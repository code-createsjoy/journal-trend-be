package com.norman.swp391.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.norman.swp391.dto.request.ai.AiCollectionAnalysisRequest;
import com.norman.swp391.dto.response.ai.AiCollectionAnalysisResponse;
import com.norman.swp391.entity.CollectionPaper;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.PaperCollection;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.repository.CollectionPaperRepository;
import com.norman.swp391.repository.PaperCollectionRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.CustomUserDetails;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@SpringBootTest
@Transactional
public class AiAnalysisServiceCollectionTest {

    @Autowired
    private AiAnalysisService aiAnalysisService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaperCollectionRepository paperCollectionRepository;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private CollectionPaperRepository collectionPaperRepository;

    @MockitoBean(name = "groqRestClient")
    private RestClient groqRestClient;

    private User owner;
    private PaperCollection collection;
    private Paper paperA;
    private Paper paperB;
    private Paper paperC;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email("ai-collection-test@example.com")
                .password("password123")
                .fullName("AI Collection Test User")
                .role(UserRole.RESEARCHER)
                .status(UserStatus.ACTIVE)
                .enabled(true)
                .verified(true)
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(owner), null,
                        List.of()));

        collection = paperCollectionRepository.save(PaperCollection.builder()
                .user(owner)
                .name("Test Collection")
                .createdAt(LocalDateTime.now())
                .build());

        paperA = savePaper("Paper A", 10);
        paperB = savePaper("Paper B", 20);
        paperC = savePaper("Paper C", 30);

        link(paperA, LocalDateTime.now().minusDays(3));
        link(paperB, LocalDateTime.now().minusDays(2));
        link(paperC, LocalDateTime.now().minusDays(1));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Paper savePaper(String title, int citations) {
        return paperRepository.save(Paper.builder()
                .title(title)
                .abstractText(title + " abstract")
                .publicationDate(LocalDate.of(2024, 1, 1))
                .citationCount(citations)
                .status(PaperStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void link(Paper paper, LocalDateTime savedAt) {
        collectionPaperRepository.save(CollectionPaper.builder()
                .collection(collection)
                .paper(paper)
                .savedAt(savedAt)
                .build());
    }

    @SuppressWarnings("unchecked")
    private void stubGroqResponse(String aiJsonContent) {
        String escaped = aiJsonContent.replace("\"", "\\\"").replace("\n", " ");
        String rawGroqResponse = "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";

        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(groqRestClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(any(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(rawGroqResponse);
    }

    @Test
    void autoSelect_picksMostRecentlySavedPapers() {
        stubGroqResponse("{\"overallSummary\":\"summary\",\"topicClusters\":[],\"outliers\":[],\"corePapers\":[],\"researchGaps\":[]}");

        AiCollectionAnalysisResponse response = aiAnalysisService.analyzeCollection(collection.getId(),
                new AiCollectionAnalysisRequest());

        assertEquals(3, response.getPaperCount());
        assertEquals(3, response.getAnalyzedPaperCount());
    }

    @Test
    void manualSelect_onlyAnalyzesRequestedPapers() {
        stubGroqResponse("{\"overallSummary\":\"summary\",\"topicClusters\":[],\"outliers\":[],\"corePapers\":[],\"researchGaps\":[]}");

        AiCollectionAnalysisRequest request = new AiCollectionAnalysisRequest();
        request.setPaperIds(List.of(paperA.getId(), paperB.getId()));

        AiCollectionAnalysisResponse response = aiAnalysisService.analyzeCollection(collection.getId(), request);

        assertEquals(3, response.getPaperCount());
        assertEquals(2, response.getAnalyzedPaperCount());
    }

    @Test
    void manualSelect_rejectsPapersNotInCollection() {
        stubGroqResponse("{}");

        AiCollectionAnalysisRequest request = new AiCollectionAnalysisRequest();
        Paper foreignPaper = savePaper("Foreign paper", 5);
        request.setPaperIds(List.of(foreignPaper.getId()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> aiAnalysisService.analyzeCollection(collection.getId(), request));
        assertTrue(ex.getMessage().contains("None of the selected papers"));
    }

    @Test
    void manualSelect_rejectsWhenExceedingCap() {
        AiCollectionAnalysisRequest request = new AiCollectionAnalysisRequest();
        request.setPaperIds(java.util.stream.LongStream.range(1, 32).boxed().toList());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> aiAnalysisService.analyzeCollection(collection.getId(), request));
        assertTrue(ex.getMessage().contains("at most 30"));
    }

    @Test
    void hallucinatedPaperIds_areFilteredOutOfResponse() {
        long fakeId = 999_999L;
        String aiJson = String.format(
                "{\"overallSummary\":\"summary\","
                        + "\"topicClusters\":[{\"name\":\"cluster\",\"description\":\"d\",\"paperIds\":[%d,%d]}],"
                        + "\"outliers\":[{\"paperId\":%d,\"title\":\"Ghost\",\"reason\":\"r\"}],"
                        + "\"corePapers\":[{\"paperId\":%d,\"title\":\"Real\",\"citationCount\":10,\"reason\":\"r\"}],"
                        + "\"researchGaps\":[]}",
                paperA.getId(), fakeId, fakeId, paperB.getId());
        stubGroqResponse(aiJson);

        AiCollectionAnalysisResponse response = aiAnalysisService.analyzeCollection(collection.getId(),
                new AiCollectionAnalysisRequest());

        assertEquals(1, response.getTopicClusters().get(0).getPapers().size());
        assertEquals(paperA.getId(), response.getTopicClusters().get(0).getPapers().get(0).getPaperId());
        assertEquals(paperA.getTitle(), response.getTopicClusters().get(0).getPapers().get(0).getTitle());
        assertTrue(response.getOutliers().isEmpty(), "hallucinated outlier paperId must be dropped");
        assertFalse(response.getCorePapers().isEmpty(), "real core paper must be kept");
        assertEquals(paperB.getId(), response.getCorePapers().get(0).getPaperId());
    }

    @Test
    void fullyHallucinatedCluster_isDroppedEntirely() {
        long fakeId1 = 999_991L;
        long fakeId2 = 999_992L;
        String aiJson = String.format(
                "{\"overallSummary\":\"summary\","
                        + "\"topicClusters\":["
                        + "{\"name\":\"real cluster\",\"description\":\"d\",\"paperIds\":[%d]},"
                        + "{\"name\":\"ghost cluster\",\"description\":\"d\",\"paperIds\":[%d,%d]}"
                        + "],"
                        + "\"outliers\":[],\"corePapers\":[],\"researchGaps\":[]}",
                paperA.getId(), fakeId1, fakeId2);
        stubGroqResponse(aiJson);

        AiCollectionAnalysisResponse response = aiAnalysisService.analyzeCollection(collection.getId(),
                new AiCollectionAnalysisRequest());

        assertEquals(1, response.getTopicClusters().size(),
                "cluster with 100% hallucinated paperIds must be dropped, not kept with an empty paperIds list");
        assertEquals("real cluster", response.getTopicClusters().get(0).getName());
    }
}
