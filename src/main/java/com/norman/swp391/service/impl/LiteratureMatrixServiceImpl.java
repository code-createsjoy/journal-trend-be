package com.norman.swp391.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.request.ai.LiteratureMatrixRequest;
import com.norman.swp391.dto.response.ai.LiteratureMatrixResponse;
import com.norman.swp391.entity.CollectionPaper;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperCollection;
import com.norman.swp391.entity.UserDailyFeatureUsage;
import com.norman.swp391.entity.enums.FeatureType;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.exception.AiQuotaExhaustedException;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.exception.UnauthorizedException;
import com.norman.swp391.repository.CollectionPaperRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.repository.PaperCollectionRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.UserDailyFeatureUsageRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.LiteratureMatrixService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class LiteratureMatrixServiceImpl implements LiteratureMatrixService {

    /** BR spec: USER tối đa 10 lượt/ngày, ADMIN/SUPER_ADMIN không giới hạn. */
    private static final int DAILY_LIMIT = 10;
    /** BR spec: tối đa 20 paper/lượt gọi. */
    private static final int MAX_PAPERS = 20;
    /** Sentinel trả về ở quotaRemainingToday khi role không bị giới hạn quota. */
    private static final int UNLIMITED_QUOTA = -1;

    private static final Map<String, String> CUSTOM_COLUMN_ALIASES = Map.of(
            "objective", "Objective",
            "methodology", "Methodology",
            "dataset", "Dataset",
            "performance", "Key Results",
            "keyresults", "Key Results",
            "limitations", "Limitations");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final PaperRepository paperRepository;
    private final PaperCollectionRepository paperCollectionRepository;
    private final CollectionPaperRepository collectionPaperRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final UserDailyFeatureUsageRepository userDailyFeatureUsageRepository;
    private final UserRepository userRepository;
    @Qualifier("groqRestClient")
    private final RestClient groqRestClient;

    @Override
    @Transactional
    public LiteratureMatrixResponse generate(LiteratureMatrixRequest request) {
        String apiKey = appProperties.getGroq().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }

        Long userId = SecurityUtils.getCurrentUserId();
        var currentUser = SecurityUtils.getCurrentUser();
        if (userId == null || currentUser == null) {
            throw new UnauthorizedException("Not authenticated");
        }

        UserRole role = currentUser.getRole();
        List<Paper> papers = resolvePapers(request);
        // Chỉ kiểm tra (không tăng đếm) ở đây — quota chỉ thực sự bị trừ sau khi Groq trả lời thành
        // công (xem incrementQuota bên dưới), tránh trừ quota oan cho các lượt gọi Groq bị lỗi.
        assertQuotaAvailable(userId, role);

        List<Long> paperIds = papers.stream().map(Paper::getId).toList();
        Map<Long, List<String>> authorsByPaperId = paperAuthorRepository.findByPaperIdInWithAuthor(paperIds).stream()
                .collect(Collectors.groupingBy(pa -> pa.getPaper().getId(), LinkedHashMap::new,
                        Collectors.mapping(pa -> pa.getAuthor().getName(), Collectors.toList())));

        String prompt = buildPrompt(papers, authorsByPaperId);
        String aiResponseText = callGroq(prompt);
        List<LiteratureMatrixResponse.MatrixRow> rows = parseAiResponse(aiResponseText, papers, authorsByPaperId);

        int quotaRemaining = incrementQuota(userId, role);

        return LiteratureMatrixResponse.builder()
                .totalPapers(papers.size())
                .quotaRemainingToday(quotaRemaining)
                .matrixRows(rows)
                .markdownTable(buildMarkdownTable(rows, request.getCustomColumns()))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Nếu collectionId được truyền: lấy paper ACTIVE trong collection đó (phải thuộc user hiện
     * tại); nếu paperIds cũng được truyền, thu hẹp về đúng các paper đó trong collection. Nếu chỉ
     * paperIds được truyền (không có collectionId): lấy trực tiếp theo id, không cần thuộc
     * collection nào (paper là dữ liệu công khai, không gắn quyền sở hữu).
     */
    private List<Paper> resolvePapers(LiteratureMatrixRequest request) {
        Long collectionId = request.getCollectionId();
        List<Long> requestedIds = request.getPaperIds();
        List<Paper> papers;

        if (requestedIds != null && requestedIds.size() > MAX_PAPERS) {
            throw new BadRequestException(
                    "You provided " + requestedIds.size() + " papers, but at most " + MAX_PAPERS + " are allowed per request");
        }

        if (collectionId != null) {
            PaperCollection collection = getOwnedCollection(collectionId);
            List<Paper> collectionPapers = collectionPaperRepository
                    .findByCollectionIdOrderBySavedAtDesc(collection.getId()).stream()
                    .map(CollectionPaper::getPaper)
                    .filter(p -> p.getStatus() == PaperStatus.ACTIVE)
                    .toList();
            if (requestedIds != null && !requestedIds.isEmpty()) {
                Set<Long> requestedSet = new LinkedHashSet<>(requestedIds);
                papers = collectionPapers.stream().filter(p -> requestedSet.contains(p.getId())).toList();
            } else {
                papers = collectionPapers;
            }
        } else if (requestedIds != null && !requestedIds.isEmpty()) {
            // Giữ đúng thứ tự paperIds người dùng truyền (findAllById không đảm bảo thứ tự này),
            // đồng thời loại trùng lặp và paper không ACTIVE/không tồn tại.
            Map<Long, Paper> byId = paperRepository.findAllById(requestedIds).stream()
                    .filter(p -> p.getStatus() == PaperStatus.ACTIVE)
                    .collect(Collectors.toMap(Paper::getId, p -> p, (a, b) -> a, LinkedHashMap::new));
            papers = requestedIds.stream()
                    .distinct()
                    .map(byId::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } else {
            throw new BadRequestException("Either collectionId or paperIds must be provided");
        }

        if (papers.isEmpty()) {
            throw new BadRequestException("No papers found for the given request");
        }
        if (papers.size() > MAX_PAPERS) {
            throw new BadRequestException(
                    "You provided " + papers.size() + " papers, but at most " + MAX_PAPERS + " are allowed per request");
        }
        return papers;
    }

    private PaperCollection getOwnedCollection(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return paperCollectionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", id));
    }

    /** ADMIN/SUPER_ADMIN bỏ qua quota. USER: chặn nếu đã dùng đủ 10 lần hôm nay. Không có side effect. */
    private void assertQuotaAvailable(Long userId, UserRole role) {
        if (role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN) {
            return;
        }
        if (currentUsageCount(userId) >= DAILY_LIMIT) {
            throw new AiQuotaExhaustedException(
                    "Quota daily limit reached (%d/%d). Please try again tomorrow.".formatted(DAILY_LIMIT, DAILY_LIMIT));
        }
    }

    /**
     * Tăng bộ đếm ngày hiện tại trong user_daily_feature_usages — chỉ được gọi SAU khi Groq đã trả
     * lời thành công, để không trừ quota cho các lượt gọi bị lỗi. Re-check limit tại đây (không chỉ
     * dựa vào assertQuotaAvailable trước đó) để giữ bất biến "không vượt quá DAILY_LIMIT" kể cả khi
     * có request khác chen giữa hai bước.
     */
    private int incrementQuota(Long userId, UserRole role) {
        if (role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN) {
            return UNLIMITED_QUOTA;
        }

        LocalDate today = LocalDate.now();
        UserDailyFeatureUsage usage = userDailyFeatureUsageRepository
                .findByUserIdAndFeatureTypeAndUsageDate(userId, FeatureType.LITERATURE_MATRIX, today)
                .orElse(null);
        int currentCount = usage != null ? usage.getUsageCount() : 0;

        if (currentCount >= DAILY_LIMIT) {
            throw new AiQuotaExhaustedException(
                    "Quota daily limit reached (%d/%d). Please try again tomorrow.".formatted(DAILY_LIMIT, DAILY_LIMIT));
        }

        if (usage == null) {
            usage = UserDailyFeatureUsage.builder()
                    .user(userRepository.getReferenceById(userId))
                    .featureType(FeatureType.LITERATURE_MATRIX)
                    .usageDate(today)
                    .usageCount(1)
                    .build();
        } else {
            usage.setUsageCount(currentCount + 1);
        }
        userDailyFeatureUsageRepository.save(usage);

        return DAILY_LIMIT - (currentCount + 1);
    }

    private int currentUsageCount(Long userId) {
        return userDailyFeatureUsageRepository
                .findByUserIdAndFeatureTypeAndUsageDate(userId, FeatureType.LITERATURE_MATRIX, LocalDate.now())
                .map(UserDailyFeatureUsage::getUsageCount)
                .orElse(0);
    }

    private String buildPrompt(List<Paper> papers, Map<Long, List<String>> authorsByPaperId) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert academic reviewer. Analyze the provided papers using ONLY the metadata ");
        sb.append("below (title, abstract, year, journal, authors) and output a structured literature matrix. ");
        sb.append("Do not invent details that are not supported by the abstract.\n\n");

        for (Paper p : papers) {
            sb.append(String.format("- [paperId=%d] \"%s\" (%s, %s)%n",
                    p.getId(),
                    p.getTitle(),
                    p.getPublicationDate() != null ? p.getPublicationDate().getYear() : "n/a",
                    p.getJournal() != null ? p.getJournal() : "n/a"));
            List<String> authors = authorsByPaperId.getOrDefault(p.getId(), List.of());
            if (!authors.isEmpty()) {
                sb.append("  authors: ").append(String.join(", ", authors)).append("\n");
            }
            String abstractText = p.getAbstractText();
            if (abstractText != null && !abstractText.isBlank()) {
                sb.append("  abstract: ")
                        .append(abstractText.length() > 800 ? abstractText.substring(0, 800) + "..." : abstractText)
                        .append("\n");
            }
        }

        sb.append(
                """

                        Respond EXACTLY in the following JSON format (no markdown, no text outside the JSON). \
                        paperId values MUST come from the [paperId=...] values above:
                        {
                          "papers": [
                            {
                              "paperId": <id>,
                              "objective": "<the paper's stated objective/goal, written in English>",
                              "methodology": "<research method/approach used, written in English>",
                              "dataset": "<dataset(s) used, or \\"n/a\\" if not mentioned, written in English>",
                              "keyResults": "<main findings/results, written in English>",
                              "limitations": "<stated or inferred limitations, written in English>"
                            }
                          ]
                        }
                        """);

        return sb.toString();
    }

    private String callGroq(String prompt) {
        AppProperties.Groq cfg = appProperties.getGroq();
        String url = cfg.getBaseUrl() + "/chat/completions";

        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> body = Map.of(
                "model", cfg.getModel(),
                "messages", List.of(message),
                "temperature", 0.3,
                "max_tokens", cfg.getMaxOutputTokens(),
                "response_format", Map.of("type", "json_object"));

        try {
            String raw = groqRestClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Groq API quota exceeded: {}", e.getMessage());
            throw new AiQuotaExhaustedException(
                    "The AI system has temporarily reached its usage quota, please try again in a few minutes.", e);
        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage());
            throw new RuntimeException("Literature matrix generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JSON AI trả về thành 1 row/paper theo ĐÚNG thứ tự papers truyền vào — không theo thứ
     * tự AI trả lời. Nếu parse lỗi hoặc AI bỏ sót 1 paperId, row của paper đó vẫn được tạo với các
     * field còn trống thay vì làm hỏng cả response (nhất quán với cách các luồng AI khác trong
     * AiAnalysisServiceImpl xử lý lỗi parse).
     */
    private List<LiteratureMatrixResponse.MatrixRow> parseAiResponse(String json, List<Paper> papers,
            Map<Long, List<String>> authorsByPaperId) {
        Map<Long, JsonNode> byPaperId = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arrayNode = root.isArray() ? root : root.path("papers");
            if (arrayNode.isArray()) {
                arrayNode.forEach(n -> {
                    long id = n.path("paperId").asLong(-1);
                    if (id != -1) {
                        byPaperId.put(id, n);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Failed to parse AI literature-matrix response: {}", json);
        }

        List<LiteratureMatrixResponse.MatrixRow> rows = new ArrayList<>();
        for (Paper p : papers) {
            JsonNode n = byPaperId.get(p.getId());
            String authors = String.join(", ", authorsByPaperId.getOrDefault(p.getId(), List.of()));
            rows.add(LiteratureMatrixResponse.MatrixRow.builder()
                    .paperId(p.getId())
                    .title(p.getTitle())
                    .authors(authors)
                    .year(p.getPublicationDate() != null ? p.getPublicationDate().getYear() : null)
                    .objective(n != null ? n.path("objective").asText("") : "")
                    .methodology(n != null ? n.path("methodology").asText("") : "")
                    .dataset(n != null ? n.path("dataset").asText("") : "")
                    .keyResults(n != null ? n.path("keyResults").asText("") : "")
                    .limitations(n != null ? n.path("limitations").asText("") : "")
                    .build());
        }
        return rows;
    }

    /**
     * Title/Authors/Year luôn cố định ở đầu bảng. customColumns (nếu có) chọn thêm cột nào trong 5
     * cột nội dung AI sinh ra sẽ được hiển thị, theo đúng thứ tự người dùng truyền; để trống = hiển
     * thị đủ cả 5 cột theo thứ tự mặc định.
     */
    private String buildMarkdownTable(List<LiteratureMatrixResponse.MatrixRow> rows, List<String> customColumns) {
        LinkedHashMap<String, Function<LiteratureMatrixResponse.MatrixRow, String>> columns = new LinkedHashMap<>();
        columns.put("Title", LiteratureMatrixResponse.MatrixRow::getTitle);
        columns.put("Authors", LiteratureMatrixResponse.MatrixRow::getAuthors);
        columns.put("Year", r -> r.getYear() != null ? String.valueOf(r.getYear()) : "");

        LinkedHashMap<String, Function<LiteratureMatrixResponse.MatrixRow, String>> contentColumns = new LinkedHashMap<>();
        contentColumns.put("Objective", LiteratureMatrixResponse.MatrixRow::getObjective);
        contentColumns.put("Methodology", LiteratureMatrixResponse.MatrixRow::getMethodology);
        contentColumns.put("Dataset", LiteratureMatrixResponse.MatrixRow::getDataset);
        contentColumns.put("Key Results", LiteratureMatrixResponse.MatrixRow::getKeyResults);
        contentColumns.put("Limitations", LiteratureMatrixResponse.MatrixRow::getLimitations);

        if (customColumns != null && !customColumns.isEmpty()) {
            for (String requested : customColumns) {
                String canonical = CUSTOM_COLUMN_ALIASES.get(
                        requested.toLowerCase(Locale.ROOT).replace(" ", ""));
                if (canonical != null && !columns.containsKey(canonical)) {
                    columns.put(canonical, contentColumns.get(canonical));
                }
            }
        } else {
            columns.putAll(contentColumns);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", columns.keySet())).append(" |\n");
        sb.append("|").append("---|".repeat(columns.size())).append("\n");
        for (LiteratureMatrixResponse.MatrixRow row : rows) {
            sb.append("| ");
            sb.append(columns.values().stream()
                    .map(fn -> escapeMarkdownCell(fn.apply(row)))
                    .collect(Collectors.joining(" | ")));
            sb.append(" |\n");
        }
        return sb.toString();
    }

    private String escapeMarkdownCell(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }
}
