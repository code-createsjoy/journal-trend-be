package com.norman.swp391.integration.semanticscholar;

import com.fasterxml.jackson.databind.JsonNode;
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.integration.model.ExternalAuthorInfo;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import com.norman.swp391.integration.model.ExternalKeywordInfo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client HTTP gọi Semantic Scholar Graph API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticScholarClient {

    private static final String FIELDS = "title,abstract,externalIds,year,publicationDate,citationCount,"
            + "authors,url,openAccessPdf,journal,fieldsOfStudy";

    private final AppProperties appProperties;
    private final RestClient externalApiRestClient;

    /**
     * Lấy metadata bài báo theo DOI để làm giàu dữ liệu.
     */
    public Optional<ExternalPaperMetadata> enrichByDoi(String doi) {
        String normalizedDoi = normalizeDoi(doi);
        if (!StringUtils.hasText(normalizedDoi)) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromUriString(appProperties.getSemanticScholar().getBaseUrl())
                .path("/paper/DOI:{doi}")
                .queryParam("fields", FIELDS)
                .buildAndExpand(normalizedDoi)
                .toUriString();

        try {
            JsonNode paper = fetchJsonWithRetry(url);
            if (paper == null || paper.isMissingNode()) {
                return Optional.empty();
            }
            return Optional.of(mapToMetadata(paper, normalizedDoi));
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Semantic Scholar: DOI not found: {}", normalizedDoi);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Semantic Scholar error for DOI {}: {}", normalizedDoi, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Tìm danh sách bài báo theo từ khóa và trang từ Semantic Scholar.
     */
    public List<ExternalPaperMetadata> fetchWorks(String query, int page) {
        int limit = Math.min(100, appProperties.getOpenalex().getPerPage());
        if (limit <= 0) {
            limit = 50;
        }
        int offset = (page - 1) * limit;

        String url = UriComponentsBuilder.fromUriString(appProperties.getSemanticScholar().getBaseUrl())
                .path("/paper/search")
                .queryParam("query", query)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .queryParam("fields", FIELDS)
                .toUriString();

        try {
            // Introduce a sleep before every search call if no api key is configured,
            // to avoid hitting the 1 request/second rate limit immediately.
            if (!StringUtils.hasText(appProperties.getSemanticScholar().getApiKey())) {
                sleepQuietly(2000L);
            }

            JsonNode root = fetchJsonWithRetry(url);
            List<ExternalPaperMetadata> results = new ArrayList<>();
            if (root != null && root.has("data") && root.path("data").isArray()) {
                for (JsonNode paper : root.path("data")) {
                    String doi = textOrNull(paper.path("externalIds").path("DOI"));
                    results.add(mapToMetadata(paper, doi));
                }
            }
            return results;
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Semantic Scholar search error for query '{}' page {}: {}", query, page, ex.getMessage());
            return List.of();
        }
    }

    private JsonNode fetchJsonWithRetry(String url) {
        int attempts = 5;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return externalApiRestClient
                        .get()
                        .uri(url)
                        .headers(headers -> {
                            if (StringUtils.hasText(appProperties.getSemanticScholar().getApiKey())) {
                                headers.set("x-api-key", appProperties.getSemanticScholar().getApiKey());
                            }
                        })
                        .retrieve()
                        .body(JsonNode.class);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                long sleepMs = 4000L * attempt; // 4s, 8s, 12s, 16s...
                if (ex.getResponseHeaders() != null) {
                    String retryAfter = ex.getResponseHeaders().getFirst("Retry-After");
                    if (StringUtils.hasText(retryAfter)) {
                        try {
                            sleepMs = Math.max(1, Long.parseLong(retryAfter.trim())) * 1000L;
                        } catch (NumberFormatException nfe) {
                            // ignore, fallback
                        }
                    }
                }
                if (attempt >= attempts) {
                    log.warn("Semantic Scholar rate limit hit, exhausted all {} attempts for URL: {}", attempts, url);
                    throw ex;
                }
                log.warn("Semantic Scholar rate limit hit, retrying attempt {}/{} after sleeping {} ms...", attempt + 1, attempts, sleepMs);
                sleepQuietly(sleepMs);
            } catch (Exception ex) {
                if (attempt >= attempts) {
                    throw new RuntimeException(ex);
                }
                sleepQuietly(1000L);
            }
        }
        return null;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

/**
 * Chuyển đổi entity sang DTO: mapToMetadata.
 */
    private ExternalPaperMetadata mapToMetadata(JsonNode paper, String doi) {
        String title = textOrNull(paper.path("title"));
        String abstractText = textOrNull(paper.path("abstract"));
        LocalDate publicationDate = parseDate(textOrNull(paper.path("publicationDate")));
        if (publicationDate == null && paper.path("year").isInt()) {
            publicationDate = LocalDate.of(paper.path("year").asInt(), 1, 1);
        }
        Integer citationCount = paper.path("citationCount").isInt() ? paper.path("citationCount").asInt() : null;
        List<ExternalKeywordInfo> keywords = mapFieldsOfStudy(paper.path("fieldsOfStudy"));
        List<String> authors = new ArrayList<>();
        List<ExternalAuthorInfo> authorDetails = new ArrayList<>();
        JsonNode authorNodes = paper.path("authors");
        if (authorNodes.isArray()) {
            authorNodes.forEach(node -> {
                String name = textOrNull(node.path("name"));
                String s2AuthorId = textOrNull(node.path("authorId"));
                if (StringUtils.hasText(name)) {
                    authors.add(name);
                    authorDetails.add(new ExternalAuthorInfo(name, "SEMANTIC_SCHOLAR", s2AuthorId, ""));
                }
            });
        }
        String pdfUrl = textOrNull(paper.path("openAccessPdf").path("url"));
        String landingPageUrl = textOrNull(paper.path("url"));
        Boolean openAccess = pdfUrl != null;
        String journal = textOrNull(paper.path("journal").path("name"));
        String paperId = textOrNull(paper.path("paperId"));
        String resolvedDoi = StringUtils.hasText(doi) ? doi : textOrNull(paper.path("externalIds").path("DOI"));
        resolvedDoi = normalizeDoi(resolvedDoi);

        return new ExternalPaperMetadata(
                title,
                abstractText,
                resolvedDoi,
                publicationDate,
                citationCount,
                keywords,
                authors,
                pdfUrl,
                landingPageUrl,
                openAccess,
                journal,
                "SEMANTIC_SCHOLAR",
                paperId != null ? paperId : resolvedDoi,
                authorDetails);
    }

/**
 * Chuyển đổi entity sang DTO: mapFieldsOfStudy.
 */
    private List<ExternalKeywordInfo> mapFieldsOfStudy(JsonNode fields) {
        if (!fields.isArray()) {
            return List.of();
        }
        List<ExternalKeywordInfo> keywords = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        fields.forEach(field -> {
            String value = textOrNull(field);
            if (StringUtils.hasText(value) && seen.add(value.toLowerCase().trim())) {
                keywords.add(new ExternalKeywordInfo(value.trim(), value.trim()));
            }
        });
        return keywords;
    }

/**
 * Xử lý nghiệp vụ: normalizeDoi.
 */
    private String normalizeDoi(String doi) {
        if (!StringUtils.hasText(doi)) {
            return null;
        }
        return doi.replace("https://doi.org/", "")
                .replace("http://doi.org/", "")
                .trim();
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
}
