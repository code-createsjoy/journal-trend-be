package com.norman.swp391.integration.openalex;

import com.fasterxml.jackson.databind.JsonNode;
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.integration.model.ExternalAuthorInfo;
import com.norman.swp391.integration.model.ExternalKeywordInfo;
import com.norman.swp391.integration.model.ExternalAuthorProfile;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client HTTP gọi API OpenAlex để lấy bài báo và tác giả.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAlexClient {

    private final AppProperties appProperties;
    private final RestClient externalApiRestClient;

    /**
     * Tìm danh sách bài báo theo từ khóa, trang và ngày xuất bản tối thiểu.
     */
    public List<ExternalPaperMetadata> fetchWorks(String search, int page, String fromPublicationDate) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                appProperties.getOpenalex().getBaseUrl() + "/works")
                .queryParam("search", search)
                .queryParam("page", page)
                .queryParam("per_page", appProperties.getOpenalex().getPerPage());

        // TODO: Bỏ comment dòng bên dưới sau khi đã lấy đủ dữ liệu các tháng cũ
        // để hệ thống quay lại ưu tiên lấy các bài báo mới nhất (đề phòng OpenAlex bị
        // quá tải).
        // builder.queryParam("sort", "publication_date:asc");
        String filterStr = "has_doi:true,has_abstract:true";
        if (StringUtils.hasText(fromPublicationDate)) {
            filterStr += ",from_publication_date:" + fromPublicationDate;
        }
        builder.queryParam("filter", filterStr);
        appendMailto(builder);
        appendApiKey(builder);
        JsonNode root = getJson(builder.toUriString());
        List<ExternalPaperMetadata> results = new ArrayList<>();
        for (JsonNode work : root.path("results")) {
            results.add(mapWork(work));
        }
        return results;
    }

    /**
     * Lấy metadata một bài theo ID OpenAlex hoặc DOI.
     */
    public Optional<ExternalPaperMetadata> fetchWorkById(String idOrDoi) {
        if (!StringUtils.hasText(idOrDoi)) {
            return Optional.empty();
        }
        String url = buildWorkLookupUrl(idOrDoi.trim());
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        appendMailto(builder);
        appendApiKey(builder);
        JsonNode node = fetchJsonSafe(builder.toUriString());
        if (node == null || node.isMissingNode()) {
            return Optional.empty();
        }
        return Optional.of(mapWork(node));
    }

    /** Builds OpenAlex /works URL from OpenAlex id (W…), full URL, or DOI. */
    private String buildWorkLookupUrl(String idOrDoi) {
        String base = appProperties.getOpenalex().getBaseUrl() + "/works/";
        if (idOrDoi.startsWith("https://openalex.org/") || idOrDoi.matches("^W\\d+$")) {
            return base + toOpenAlexWorkId(idOrDoi);
        }
        String doi = normalizeDoi(idOrDoi);
        if (StringUtils.hasText(doi) && doi.startsWith("10.")) {
            return base + "https://doi.org/" + doi;
        }
        if (idOrDoi.startsWith("W")) {
            return base + toOpenAlexWorkId(idOrDoi);
        }
        if (StringUtils.hasText(doi)) {
            return base + "https://doi.org/" + doi;
        }
        return base + idOrDoi;
    }

    /**
     * Lấy hồ sơ tác giả từ OpenAlex.
     */
    /**
     * Batch fetch citedByCount + hIndex cho nhiều authors theo OpenAlex IDs.
     * Dùng pipe filter giống fetchWorksByIds — một call cho tối đa 50 IDs.
     */
    public List<ExternalAuthorProfile> fetchAuthorsByIds(List<String> openAlexIds) {
        if (openAlexIds == null || openAlexIds.isEmpty()) {
            return List.of();
        }
        List<ExternalAuthorProfile> results = new ArrayList<>();
        int batchSize = 50;
        for (int i = 0; i < openAlexIds.size(); i += batchSize) {
            List<String> batch = openAlexIds.subList(i, Math.min(i + batchSize, openAlexIds.size()));
            String pipeFilter = batch.stream()
                    .map(this::toOpenAlexAuthorId)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("|"));
            if (!StringUtils.hasText(pipeFilter)) continue;
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                    appProperties.getOpenalex().getBaseUrl() + "/authors")
                    .queryParam("filter", "openalex_id:" + pipeFilter)
                    .queryParam("per_page", batch.size())
                    .queryParam("select", "id,cited_by_count,works_count,summary_stats");
            appendMailto(builder);
            appendApiKey(builder);
            JsonNode root = fetchJsonSafe(builder.toUriString());
            if (root != null && root.has("results") && root.path("results").isArray()) {
                for (JsonNode author : root.path("results")) {
                    ExternalAuthorProfile profile = mapAuthorProfile(author);
                    if (StringUtils.hasText(profile.openAlexId())) {
                        results.add(profile);
                    }
                }
            }
        }
        return results;
    }

    public Optional<ExternalAuthorProfile> fetchAuthorProfile(String authorId) {
        String normalized = normalizeAuthorId(authorId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                appProperties.getOpenalex().getBaseUrl() + "/authors/" + normalized);
        appendMailto(builder);
        appendApiKey(builder);
        JsonNode node = fetchJsonSafe(builder.toUriString());
        if (node == null || node.isMissingNode()) {
            return Optional.empty();
        }
        return Optional.of(mapAuthorProfile(node));
    }

    /**
     * Chuyển đổi entity sang DTO: mapWork.
     */
    private ExternalPaperMetadata mapWork(JsonNode work) {
        String openAlexId = toOpenAlexWorkId(textOrNull(work.path("id")));
        String title = textOrNull(work.path("title"));
        String abstractText = null;
        if (work.has("abstract_inverted_index") && !work.path("abstract_inverted_index").isNull()) {
            abstractText = reconstructAbstract(work.path("abstract_inverted_index"));
        }
        if (!StringUtils.hasText(abstractText)) {
            abstractText = stripHtml(textOrNull(work.path("abstract")));
        }
        String doi = normalizeDoi(textOrNull(work.path("doi")));
        LocalDate publicationDate = resolvePublicationDate(work);
        Integer citationCount = work.path("cited_by_count").isInt() ? work.path("cited_by_count").asInt() : null;
        List<ExternalKeywordInfo> keywords = mapOpenAlexKeywords(
                work.path("topics"),
                work.path("concepts"));
        List<String> authors = new ArrayList<>();
        List<ExternalAuthorInfo> authorDetails = mapAuthorDetails(work.path("authorships"));
        for (ExternalAuthorInfo info : authorDetails) {
            authors.add(info.name());
        }
        String pdfUrl = textOrNull(work.path("open_access").path("oa_url"));
        String landingPageUrl = textOrNull(work.path("primary_location").path("landing_page_url"));
        Boolean openAccess = work.path("open_access").path("is_oa").isBoolean()
                ? work.path("open_access").path("is_oa").asBoolean()
                : null;
        String journal = textOrNull(work.path("primary_location").path("source").path("display_name"));

        // Parse referenced_works (list of OpenAlex work URLs/IDs)
        List<String> referencedWorkIds = new ArrayList<>();
        JsonNode refsNode = work.path("referenced_works");
        if (refsNode.isArray()) {
            for (JsonNode ref : refsNode) {
                String refId = toOpenAlexWorkId(ref.asText(null));
                if (StringUtils.hasText(refId)) {
                    referencedWorkIds.add(refId);
                }
            }
        }

        return new ExternalPaperMetadata(
                title,
                abstractText,
                doi,
                publicationDate,
                citationCount,
                keywords,
                authors,
                pdfUrl,
                landingPageUrl,
                openAccess,
                journal,
                "OPENALEX",
                openAlexId,
                authorDetails,
                referencedWorkIds);
    }

    /**
     * Xử lý nghiệp vụ: reconstructAbstract.
     */
    private String reconstructAbstract(JsonNode invertedIndex) {
        if (!invertedIndex.isObject()) {
            return null;
        }
        TreeMap<Integer, String> wordsByPosition = new TreeMap<>();
        invertedIndex.fields().forEachRemaining(entry -> {
            String word = entry.getKey();
            for (JsonNode pos : entry.getValue()) {
                if (pos.isInt()) {
                    wordsByPosition.put(pos.asInt(), word);
                }
            }
        });
        if (wordsByPosition.isEmpty()) {
            return null;
        }
        return String.join(" ", wordsByPosition.values());
    }

    /**
     * Chuyển đổi entity sang DTO: mapAuthorProfile.
     */
    private ExternalAuthorProfile mapAuthorProfile(JsonNode author) {
        String openAlexId = toOpenAlexAuthorId(textOrNull(author.path("id")));
        String name = textOrNull(author.path("display_name"));
        String affiliation = null;
        JsonNode affiliations = author.path("affiliations");
        if (affiliations.isArray() && !affiliations.isEmpty()) {
            affiliation = textOrNull(affiliations.get(0).path("institution").path("display_name"));
        }
        Integer citedByCount = author.path("cited_by_count").isInt() ? author.path("cited_by_count").asInt() : null;
        Integer worksCount = author.path("works_count").isInt() ? author.path("works_count").asInt() : null;
        Integer hIndex = author.path("summary_stats").path("h_index").isInt()
                ? author.path("summary_stats").path("h_index").asInt()
                : null;
        return new ExternalAuthorProfile(openAlexId, name, affiliation, citedByCount, worksCount, hIndex);
    }

    /**
     * Xử lý nghiệp vụ: resolvePublicationDate.
     */
    private LocalDate resolvePublicationDate(JsonNode work) {
        LocalDate publicationDate = parseDate(textOrNull(work.path("publication_date")));
        if (publicationDate != null) {
            return publicationDate;
        }
        if (work.path("publication_year").isInt()) {
            int year = work.path("publication_year").asInt();
            if (year > 0) {
                return LocalDate.of(year, 6, 1);
            }
        }
        return null;
    }

    private List<ExternalKeywordInfo> mapOpenAlexKeywords(JsonNode topicsNode, JsonNode conceptsNode) {
        List<ExternalKeywordInfo> keywords = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (topicsNode != null && topicsNode.isArray()) {
            List<JsonNode> topicList = StreamSupport.stream(topicsNode.spliterator(), false)
                    .map(JsonNode.class::cast)
                    .sorted(Comparator.comparingDouble((JsonNode node) -> node.path("score").asDouble(0)).reversed())
                    .toList();
            for (JsonNode node : topicList) {
                String term = textOrNull(node.path("display_name"));
                if (StringUtils.hasText(term) && seen.add(term.toLowerCase().trim())) {
                    String domain = "General";
                    if (node.has("subfield") && !node.path("subfield").isNull()) {
                        domain = textOrNull(node.path("subfield").path("display_name"));
                    } else if (node.has("field") && !node.path("field").isNull()) {
                        domain = textOrNull(node.path("field").path("display_name"));
                    } else if (node.has("domain") && !node.path("domain").isNull()) {
                        domain = textOrNull(node.path("domain").path("display_name"));
                    }
                    if (!StringUtils.hasText(domain)) {
                        domain = "General";
                    }
                    keywords.add(new ExternalKeywordInfo(term.trim(), domain.trim()));
                }
            }
        }

        // Concepts level > 0 không chứa domain thực trong OpenAlex Works API response —
        // chúng chỉ có display_name/level/score, không có parent hierarchy.
        // Dùng primaryDomain cho chúng là sai vì paper CS về healthcare sẽ khiến
        // "Pharmacology", "Surgery" (level 1, Medicine) được gán domain "Computer Science".
        // → Chỉ giữ level 0 concepts (domain gốc như "Computer Science", "Mathematics").
        //   Specific keywords đã được lấy từ topics phía trên với subfield đúng.
        if (conceptsNode != null && conceptsNode.isArray()) {
            for (JsonNode node : conceptsNode) {
                int level = node.path("level").asInt(-1);
                if (level != 0) continue;
                String term = textOrNull(node.path("display_name"));
                if (StringUtils.hasText(term) && seen.add(term.toLowerCase().trim())) {
                    keywords.add(new ExternalKeywordInfo(term.trim(), term.trim()));
                }
            }
        }
        return keywords;
    }

    /**
     * Chuyển đổi entity sang DTO: mapAuthorDetails.
     */
    private List<ExternalAuthorInfo> mapAuthorDetails(JsonNode authorships) {
        if (!authorships.isArray()) {
            return List.of();
        }
        List<ExternalAuthorInfo> authors = new ArrayList<>();
        authorships.forEach(authorship -> {
            JsonNode authorNode = authorship.path("author");
            String name = textOrNull(authorNode.path("display_name"));
            if (!StringUtils.hasText(name)) {
                return;
            }
            String openAlexId = toOpenAlexAuthorId(textOrNull(authorNode.path("id")));
            String affiliation = "";
            JsonNode institutions = authorship.path("institutions");
            if (institutions.isArray() && !institutions.isEmpty()) {
                affiliation = textOrNull(institutions.get(0).path("display_name"));
            }
            String position = textOrNull(authorship.path("author_position")); // "first", "middle", "last"
            authors.add(new ExternalAuthorInfo(name, "OPENALEX", openAlexId, affiliation != null ? affiliation : "", position));
        });
        return authors;
    }

    /**
     * Lấy dữ liệu: getJson.
     */
    private JsonNode getJson(String url) {
        JsonNode node = fetchJsonSafe(url);
        if (node == null || node.isMissingNode()) {
            throw new ResourceNotFoundException("OpenAlex request failed for URL: " + url);
        }
        return node;
    }

    /**
     * Xử lý nghiệp vụ: fetchJsonSafe.
     */
    private JsonNode fetchJsonSafe(String url) {
        int attempts = Math.max(1, appProperties.getSync().getOpenAlexRetryAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JsonNode response = externalApiRestClient.get().uri(url != null ? url : "").retrieve().body(JsonNode.class);
                // Giãn cách 150ms sau mỗi request thành công để tránh chạm hạn mức rate limit
                // (10 req/s) của OpenAlex
                sleepQuietly(150L);
                return response;
            } catch (HttpClientErrorException.TooManyRequests ex) {
                String responseBody = ex.getResponseBodyAsString();
                if (responseBody != null && responseBody.contains("Insufficient budget")) {
                    throw new com.norman.swp391.exception.OpenAlexQuotaExhaustedException(
                            "OpenAlex quota exhausted. Synchronization stopped. Retry after quota reset.", ex);
                }
                long sleepMs = 2000L * attempt; // Back off: 2s, 4s, 6s...
                var headers = ex.getResponseHeaders();
                if (headers != null) {
                    String retryAfter = headers.getFirst("Retry-After");
                    if (retryAfter != null && !retryAfter.isBlank()) {
                        try {
                            sleepMs = Math.max(1, Long.parseLong(retryAfter.strip())) * 1000L;
                        } catch (NumberFormatException nfe) {
                            // ignore, fallback
                        }
                    }
                }
                if (attempt >= attempts) {
                    log.warn("OpenAlex rate limit hit, exhausted all {} attempts for URL: {}", attempts, url, ex);
                    return null;
                }
                log.warn("OpenAlex rate limit hit (429), retrying attempt {}/{} after sleeping {} ms...", attempt + 1,
                        attempts, sleepMs);
                sleepQuietly(sleepMs);
            } catch (Exception ex) {
                if (attempt >= attempts) {
                    log.warn("OpenAlex request failed after {} attempts: {}", attempts, url, ex);
                    return null;
                }
                log.debug("OpenAlex retry {}/{} for {}", attempt, attempts, url);
                sleepQuietly(500L * attempt);
            }
        }
        return null;
    }

    /**
     * Xử lý nghiệp vụ: sleepQuietly.
     */
    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Xử lý nghiệp vụ: appendMailto.
     */
    private void appendMailto(UriComponentsBuilder builder) {
        if (StringUtils.hasText(appProperties.getOpenalex().getMailto())) {
            builder.queryParam("mailto", appProperties.getOpenalex().getMailto());
        }
    }

    private void appendApiKey(UriComponentsBuilder builder) {
        if (StringUtils.hasText(appProperties.getOpenalex().getApiKey())) {
            builder.queryParam("api_key", appProperties.getOpenalex().getApiKey());
        }
    }

    /**
     * Ánh xạ sang DTO/phản hồi: toOpenAlexWorkId.
     */
    private String toOpenAlexWorkId(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        if (id.startsWith("https://openalex.org/")) {
            id = id.substring("https://openalex.org/".length());
        }
        return id.startsWith("W") ? id : "W" + id;
    }

    /**
     * Xử lý nghiệp vụ: normalizeAuthorId.
     */
    private String normalizeAuthorId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BadRequestException("OpenAlex author id is required");
        }
        return toOpenAlexAuthorId(id);
    }

    /**
     * Ánh xạ sang DTO/phản hồi: toOpenAlexAuthorId.
     */
    private String toOpenAlexAuthorId(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        if (id.startsWith("https://openalex.org/")) {
            id = id.substring("https://openalex.org/".length());
        }
        return id.startsWith("A") ? id : "A" + id;
    }

    /**
     * Xử lý nghiệp vụ: normalizeDoi.
     */
    private String normalizeDoi(String doi) {
        if (!StringUtils.hasText(doi)) {
            return null;
        }
        return doi.replace("https://doi.org/", "").replace("http://doi.org/", "");
    }

    /**
     * Xử lý nghiệp vụ: parseDate.
     */
    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Xử lý nghiệp vụ: textOrNull.
     */
    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return StringUtils.hasText(text) ? text : null;
    }

    /**
     * Xử lý nghiệp vụ: stripHtml.
     */
    private String stripHtml(String value) {
        return value == null ? null : value.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Batch fetch metadata cho nhiều works theo OpenAlex IDs.
     * Sử dụng filter pipe: openalex_id:W1|W2|W3 (tối đa ~50 IDs/request).
     * IDs không có trong kết quả batch (do OpenAlex merge/alias) sẽ được fetch đơn lẻ qua
     * /works/{id} — endpoint này hỗ trợ redirect nên lấy được metadata dù ID đã bị gộp.
     */
    public List<ExternalPaperMetadata> fetchWorksByIds(List<String> openAlexIds) {
        if (openAlexIds == null || openAlexIds.isEmpty()) {
            return List.of();
        }
        List<ExternalPaperMetadata> allResults = new ArrayList<>();
        int batchSize = 50;
        for (int i = 0; i < openAlexIds.size(); i += batchSize) {
            List<String> batch = openAlexIds.subList(i, Math.min(i + batchSize, openAlexIds.size()));
            String pipeFilter = String.join("|", batch);
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                    appProperties.getOpenalex().getBaseUrl() + "/works")
                    .queryParam("filter", "openalex_id:" + pipeFilter)
                    .queryParam("per_page", batch.size())
                    .queryParam("select", "id,title,publication_year,publication_date,doi,cited_by_count");
            appendMailto(builder);
            appendApiKey(builder);
            JsonNode root = fetchJsonSafe(builder.toUriString());
            if (root != null && root.has("results") && root.path("results").isArray()) {
                for (JsonNode work : root.path("results")) {
                    String id = toOpenAlexWorkId(textOrNull(work.path("id")));
                    String title = textOrNull(work.path("title"));
                    String doi = normalizeDoi(textOrNull(work.path("doi")));
                    LocalDate pubDate = resolvePublicationDate(work);
                    Integer citations = work.path("cited_by_count").isInt()
                            ? work.path("cited_by_count").asInt() : null;
                    allResults.add(new ExternalPaperMetadata(
                            title, null, doi, pubDate, citations,
                            List.of(), List.of(), null, null, null, null,
                            "OPENALEX", id));
                }
            }
        }

        // Fallback: IDs không có trong kết quả batch → fetch đơn lẻ.
        // /works/{id} theo redirect nên xử lý được cả trường hợp OpenAlex đã merge ID.
        // sourceIdentifier giữ nguyên ID gốc để mapping trong reference_metadata đúng key.
        Set<String> returnedIds = allResults.stream()
                .map(ExternalPaperMetadata::sourceIdentifier)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<String> stillMissing = openAlexIds.stream()
                .filter(id -> !returnedIds.contains(id))
                .toList();
        if (!stillMissing.isEmpty()) {
            log.debug("[OpenAlex] {} IDs missing from batch, trying individual lookup (possible merged IDs)",
                    stillMissing.size());
            for (String originalId : stillMissing) {
                ExternalPaperMetadata meta = fetchSingleWorkById(originalId);
                if (meta != null) {
                    allResults.add(meta);
                }
            }
        }

        return allResults;
    }

    /**
     * Fetch metadata cho một work đơn lẻ qua /works/{id}.
     * Endpoint này hỗ trợ HTTP redirect — dùng để fallback khi batch pipe filter bỏ qua ID
     * (thường do OpenAlex đã merge work vào ID khác).
     * sourceIdentifier trả về là originalId (không phải ID sau redirect) để mapping đúng key.
     */
    private ExternalPaperMetadata fetchSingleWorkById(String originalId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                appProperties.getOpenalex().getBaseUrl() + "/works/" + originalId)
                .queryParam("select", "id,title,publication_year,publication_date,doi,cited_by_count");
        appendMailto(builder);
        appendApiKey(builder);
        JsonNode work = fetchJsonSafe(builder.toUriString());
        if (work == null || work.isMissingNode() || work.isNull()) {
            return null;
        }
        String title = textOrNull(work.path("title"));
        String doi = normalizeDoi(textOrNull(work.path("doi")));
        LocalDate pubDate = resolvePublicationDate(work);
        Integer citations = work.path("cited_by_count").isInt()
                ? work.path("cited_by_count").asInt() : null;
        if (title == null && pubDate == null && doi == null) {
            return null;
        }
        return new ExternalPaperMetadata(
                title, null, doi, pubDate, citations,
                List.of(), List.of(), null, null, null, null,
                "OPENALEX", originalId);
    }

    /**
     * Lấy danh sách referenced_works IDs cho một work cụ thể.
     * Trả về list OpenAlex IDs (e.g. ["W123", "W456"]).
     */
    public List<String> extractReferencedWorkIds(String openAlexId) {
        if (!StringUtils.hasText(openAlexId)) {
            return List.of();
        }
        String url = appProperties.getOpenalex().getBaseUrl() + "/works/" + openAlexId;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url)
                .queryParam("select", "id,referenced_works");
        appendMailto(builder);
        appendApiKey(builder);
        JsonNode node = fetchJsonSafe(builder.toUriString());
        if (node == null || node.isMissingNode()) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        JsonNode refsNode = node.path("referenced_works");
        if (refsNode.isArray()) {
            for (JsonNode ref : refsNode) {
                String refId = toOpenAlexWorkId(ref.asText(null));
                if (StringUtils.hasText(refId)) {
                    refs.add(refId);
                }
            }
        }
        return refs;
    }

    /**
     * Lấy danh sách papers trích dẫn (citing) một work cụ thể.
     * Sử dụng OpenAlex filter: cites:W...
     *
     * @param openAlexId   OpenAlex ID của paper gốc (e.g. "W2741809807")
     * @param sort         "cited_by_count:desc" hoặc "publication_date:desc"
     * @param yearFrom     Năm bắt đầu (nullable)
     * @param yearTo       Năm kết thúc (nullable)
     * @param perPage      Số lượng kết quả tối đa (default 20)
     * @return Danh sách ExternalPaperMetadata nhẹ (chỉ title, year, doi, citationCount)
     */
    public List<ExternalPaperMetadata> fetchCitingWorks(String openAlexId, String sort,
                                                        Integer yearFrom, Integer yearTo, int perPage) {
        if (!StringUtils.hasText(openAlexId)) {
            return List.of();
        }
        StringBuilder filterStr = new StringBuilder("cites:" + openAlexId);
        if (yearFrom != null) {
            filterStr.append(",from_publication_date:").append(yearFrom).append("-01-01");
        }
        if (yearTo != null) {
            filterStr.append(",to_publication_date:").append(yearTo).append("-12-31");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                appProperties.getOpenalex().getBaseUrl() + "/works")
                .queryParam("filter", filterStr.toString())
                .queryParam("per_page", Math.min(perPage, 200))
                .queryParam("select", "id,title,publication_year,publication_date,doi,cited_by_count");

        if (StringUtils.hasText(sort)) {
            builder.queryParam("sort", sort);
        } else {
            builder.queryParam("sort", "cited_by_count:desc");
        }

        appendMailto(builder);
        appendApiKey(builder);

        JsonNode root = fetchJsonSafe(builder.toUriString());
        if (root == null || !root.has("results") || !root.path("results").isArray()) {
            return List.of();
        }

        List<ExternalPaperMetadata> results = new ArrayList<>();
        for (JsonNode work : root.path("results")) {
            String id = toOpenAlexWorkId(textOrNull(work.path("id")));
            String title = textOrNull(work.path("title"));
            String doi = normalizeDoi(textOrNull(work.path("doi")));
            LocalDate pubDate = resolvePublicationDate(work);
            Integer citations = work.path("cited_by_count").isInt()
                    ? work.path("cited_by_count").asInt() : null;
            results.add(new ExternalPaperMetadata(
                    title, null, doi, pubDate, citations,
                    List.of(), List.of(), null, null, null, null,
                    "OPENALEX", id));
        }
        return results;
    }
}
