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
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
        // để hệ thống quay lại ưu tiên lấy các bài báo mới nhất (đề phòng OpenAlex bị quá tải).
        // builder.queryParam("sort", "publication_date:desc");
        String filterStr = "has_doi:true,has_abstract:true";
        if (StringUtils.hasText(fromPublicationDate)) {
            filterStr += ",from_publication_date:" + fromPublicationDate;
        }
        builder.queryParam("filter", filterStr);
        appendMailto(builder);
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
    public Optional<ExternalAuthorProfile> fetchAuthorProfile(String authorId) {
        String normalized = normalizeAuthorId(authorId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                appProperties.getOpenalex().getBaseUrl() + "/authors/" + normalized);
        appendMailto(builder);
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
        Integer citationCount =
                work.path("cited_by_count").isInt() ? work.path("cited_by_count").asInt() : null;
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
                authorDetails);
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
        String primaryDomain = "General";
        
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
                    if ("General".equals(primaryDomain) && !"General".equals(domain)) {
                        primaryDomain = domain;
                    }
                    keywords.add(new ExternalKeywordInfo(term.trim(), domain.trim()));
                }
            }
        }

        String fallbackDomain = null;
        double maxConceptScore = -1.0;
        if (conceptsNode != null && conceptsNode.isArray()) {
            for (JsonNode node : conceptsNode) {
                int level = node.path("level").asInt(-1);
                if (level == 0) {
                    double score = node.path("score").asDouble(0.0);
                    if (score > maxConceptScore) {
                        maxConceptScore = score;
                        fallbackDomain = textOrNull(node.path("display_name"));
                    }
                }
            }
        }
        if ("General".equals(primaryDomain) && StringUtils.hasText(fallbackDomain)) {
            primaryDomain = fallbackDomain;
        }
        
        if (conceptsNode != null && conceptsNode.isArray()) {
            List<JsonNode> conceptList = StreamSupport.stream(conceptsNode.spliterator(), false)
                    .map(JsonNode.class::cast)
                    .sorted(Comparator.comparingDouble((JsonNode node) -> node.path("score").asDouble(0)).reversed())
                    .toList();
            for (JsonNode node : conceptList) {
                String term = textOrNull(node.path("display_name"));
                if (StringUtils.hasText(term) && seen.add(term.toLowerCase().trim())) {
                    int level = node.path("level").asInt(-1);
                    String domain = (level == 0) ? term : primaryDomain;
                    keywords.add(new ExternalKeywordInfo(term.trim(), domain.trim()));
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
            authors.add(new ExternalAuthorInfo(name, "OPENALEX", openAlexId, affiliation != null ? affiliation : ""));
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
                return externalApiRestClient.get().uri(url).retrieve().body(JsonNode.class);
            } catch (Exception ex) {
                if (attempt >= attempts) {
                    log.warn("OpenAlex request failed after {} attempts: {}", attempts, url, ex);
                    return null;
                }
                log.debug("OpenAlex retry {}/{} for {}", attempt, attempts, url);
                sleepQuietly(250L * attempt);
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

/**
 * Xử lý nghiệp vụ: normalizeWorkId.
 */
    private String normalizeWorkId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BadRequestException("OpenAlex work id is required");
        }
        return toOpenAlexWorkId(id);
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
}
