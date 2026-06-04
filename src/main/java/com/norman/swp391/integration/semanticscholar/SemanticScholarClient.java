package com.norman.swp391.integration.semanticscholar;

import com.fasterxml.jackson.databind.JsonNode;
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.integration.model.ExternalAuthorInfo;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
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
            JsonNode paper = externalApiRestClient
                    .get()
                    .uri(url)
                    .headers(headers -> {
                        if (StringUtils.hasText(appProperties.getSemanticScholar().getApiKey())) {
                            headers.set("x-api-key", appProperties.getSemanticScholar().getApiKey());
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);
            if (paper == null || paper.isMissingNode()) {
                return Optional.empty();
            }
            return Optional.of(mapToMetadata(paper, normalizedDoi));
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Semantic Scholar: DOI not found: {}", normalizedDoi);
            return Optional.empty();
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("Semantic Scholar rate limit for DOI {}", normalizedDoi);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Semantic Scholar error for DOI {}: {}", normalizedDoi, ex.getMessage());
            return Optional.empty();
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
        List<String> topics = mapFieldsOfStudy(paper.path("fieldsOfStudy"));
        List<String> authors = new ArrayList<>();
        List<ExternalAuthorInfo> authorDetails = new ArrayList<>();
        JsonNode authorNodes = paper.path("authors");
        if (authorNodes.isArray()) {
            authorNodes.forEach(node -> {
                String name = textOrNull(node.path("name"));
                if (StringUtils.hasText(name)) {
                    authors.add(name);
                    authorDetails.add(new ExternalAuthorInfo(name, null, ""));
                }
            });
        }
        String pdfUrl = textOrNull(paper.path("openAccessPdf").path("url"));
        String landingPageUrl = textOrNull(paper.path("url"));
        Boolean openAccess = pdfUrl != null;
        String journal = textOrNull(paper.path("journal").path("name"));
        return new ExternalPaperMetadata(
                title,
                abstractText,
                doi,
                publicationDate,
                citationCount,
                topics,
                authors,
                pdfUrl,
                landingPageUrl,
                openAccess,
                journal,
                null,
                authorDetails);
    }

/**
 * Chuyển đổi entity sang DTO: mapFieldsOfStudy.
 */
    private List<String> mapFieldsOfStudy(JsonNode fields) {
        if (!fields.isArray()) {
            return List.of();
        }
        List<String> topics = new ArrayList<>();
        fields.forEach(field -> {
            String value = textOrNull(field);
            if (StringUtils.hasText(value)) {
                topics.add(value);
            }
        });
        return topics;
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
