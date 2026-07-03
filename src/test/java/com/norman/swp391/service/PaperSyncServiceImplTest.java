package com.norman.swp391.service;

import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.SyncLog;
import com.norman.swp391.entity.enums.SyncStatus;
import com.norman.swp391.integration.model.ExternalAuthorInfo;
import com.norman.swp391.integration.model.ExternalAuthorProfile;
import com.norman.swp391.integration.model.ExternalKeywordInfo;
import com.norman.swp391.integration.model.ExternalPaperMetadata;
import com.norman.swp391.integration.openalex.OpenAlexClient;
import com.norman.swp391.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test cho luồng sync paper từ OpenAlex vào DB.
 *
 * Chiến lược: mock OpenAlexClient + ApiSourceService để kiểm soát dữ liệu đầu vào,
 * dùng DB thật để verify kết quả lưu trữ.
 *
 * Mỗi test dùng DOI prefix "10.test.sync/" riêng biệt để tránh xung đột.
 */
@SpringBootTest
class PaperSyncServiceImplTest {

    @Autowired private PaperSyncService paperSyncService;
    @Autowired private SyncLogRepository syncLogRepository;
    @Autowired private PaperRepository paperRepository;
    @Autowired private AuthorRepository authorRepository;
    @Autowired private PaperKeywordRepository paperKeywordRepository;
    @Autowired private PaperAuthorRepository paperAuthorRepository;
    @Autowired private KeywordSyncStateRepository keywordSyncStateRepository;

    @MockitoBean private OpenAlexClient openAlexClient;
    @MockitoBean private ApiSourceService apiSourceService;
    @MockitoBean private KeywordTrendService keywordTrendService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private PaperReviewService paperReviewService;
    @MockitoBean private JournalService journalService;

    private static final String TEST_DOI_PREFIX = "10.test.sync/";
    // Query đầu tiên trong application.yml — chỉ mock 1 query để tránh buffer dư
    private static final String FIRST_QUERY = "artificial intelligence";

    @BeforeEach
    void setUp() {
        when(apiSourceService.isEnabled("OpenAlex")).thenReturn(true);
        doNothing().when(apiSourceService).recordSyncResult(any(), anyBoolean());

        // Mặc định: tất cả OpenAlex calls trả về rỗng
        when(openAlexClient.fetchWorks(anyString(), anyInt(), any())).thenReturn(List.of());
        when(openAlexClient.fetchAuthorsByIds(any())).thenReturn(List.of());

        // Post-sync tasks không làm gì (isolated)
        doNothing().when(paperReviewService).expireStalePendingReviews();
        doNothing().when(keywordTrendService).recalculateAll();
        doNothing().when(keywordTrendService).backfillHistoricalMonths(anyInt());
        doNothing().when(notificationService).notifyTrendingForFollowedKeywords(any());
        doNothing().when(notificationService).notifyNewPapersForSubscriptions(any());
        doNothing().when(journalService).calculateAndUpdateJournalImpactFactors();

        // Xóa crawler state để mỗi test bắt đầu từ page 1
        keywordSyncStateRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Xóa theo thứ tự: child tables trước, paper sau (tránh FK violation)
        List<Paper> testPapers = paperRepository.findAll().stream()
                .filter(p -> p.getDoi() != null && p.getDoi().startsWith(TEST_DOI_PREFIX))
                .toList();

        if (!testPapers.isEmpty()) {
            List<Long> paperIds = testPapers.stream().map(Paper::getId).toList();
            paperKeywordRepository.deleteAll(paperKeywordRepository.findByPaperIdInWithKeyword(paperIds));
            paperAuthorRepository.deleteAll(paperAuthorRepository.findByPaperIdInWithAuthor(paperIds));
            paperRepository.deleteAll(testPapers);
        }

        // Xóa author test (sourceIdentifier bắt đầu bằng "ATEST")
        authorRepository.findAll().stream()
                .filter(a -> a.getSourceIdentifier() != null && a.getSourceIdentifier().startsWith("ATEST"))
                .forEach(authorRepository::delete);

        keywordSyncStateRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: chờ virtual thread sync hoàn thành (tối đa 6 giây)
    // ─────────────────────────────────────────────────────────────────────────
    private SyncLog waitForSync(Long syncId) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            Thread.sleep(100);
            SyncLog log = syncLogRepository.findById(syncId).orElse(null);
            if (log != null && log.getStatus() != SyncStatus.RUNNING) {
                return log;
            }
        }
        return syncLogRepository.findById(syncId).orElse(null);
    }

    /**
     * Chạy sync với 1 trang kết quả rồi dừng.
     * Chỉ mock query FIRST_QUERY để tránh cùng DOI xuất hiện nhiều lần trong buffer.
     */
    private SyncLog runSyncWith(List<ExternalPaperMetadata> papers) throws InterruptedException {
        when(openAlexClient.fetchWorks(eq(FIRST_QUERY), eq(1), any())).thenReturn(papers);
        SyncLogResponse response = paperSyncService.startSync(null);
        return waitForSync(response.getId());
    }

    // Helper: tạo paper metadata hợp lệ với keyword trong domain được phép
    private ExternalPaperMetadata validPaper(String doi, String openAlexId) {
        return new ExternalPaperMetadata(
                "Test Paper: " + doi, "Abstract",
                doi,
                LocalDate.of(2024, 6, 1),
                100,
                List.of(new ExternalKeywordInfo("Machine Learning", "Computer Science")),
                null, null, null, false,
                "Test Journal", "OPENALEX", openAlexId,
                List.of(), List.of()
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 1: Paper hợp lệ với keyword được phép → lưu vào DB
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    void sync_validPaperWithAllowedKeyword_savedToDb() throws InterruptedException {
        String doi = TEST_DOI_PREFIX + "valid-001";

        SyncLog log = runSyncWith(List.of(validPaper(doi, "W111111111")));

        assertEquals(SyncStatus.SUCCESS, log.getStatus(), "Sync phải kết thúc SUCCESS");
        assertTrue(paperRepository.findAll().stream().anyMatch(p -> doi.equals(p.getDoi())),
                "Paper phải tồn tại trong DB với DOI đúng");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 2: Paper thiếu DOI → không lưu (validation filter)
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    void sync_paperWithoutDoi_notSaved() throws InterruptedException {
        ExternalPaperMetadata noDoi = new ExternalPaperMetadata(
                "Paper Without DOI", "Abstract",
                null,                                // ← DOI null
                LocalDate.of(2024, 6, 1), 10,
                List.of(new ExternalKeywordInfo("Machine Learning", "Computer Science")),
                null, null, null, false, "Journal", "OPENALEX", "W222222222",
                List.of(), List.of()
        );

        SyncLog log = runSyncWith(List.of(noDoi));

        assertEquals(SyncStatus.SUCCESS, log.getStatus());
        assertEquals(0, log.getPapersInserted(), "Paper không có DOI không được lưu");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 3: Paper thiếu publicationDate → không lưu (validation filter)
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    void sync_paperWithoutPublicationDate_notSaved() throws InterruptedException {
        String doi = TEST_DOI_PREFIX + "nodate-003";
        ExternalPaperMetadata paper = new ExternalPaperMetadata(
                "Paper Without Date", "Abstract",
                doi,
                null,                               // ← publicationDate null
                10,
                List.of(new ExternalKeywordInfo("AI", "Computer Science")),
                null, null, null, false, "Journal", "OPENALEX", "W333333333",
                List.of(), List.of()
        );

        SyncLog log = runSyncWith(List.of(paper));

        assertEquals(0, log.getPapersInserted(), "Paper không có ngày xuất bản không được lưu");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 4: Paper có keyword nhưng domain bị chặn → không lưu (domain filter)
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    void sync_paperWithBlockedDomainKeyword_notSaved() throws InterruptedException {
        String doi = TEST_DOI_PREFIX + "blocked-004";
        ExternalPaperMetadata paper = new ExternalPaperMetadata(
                "Paper on Biology", "Abstract",
                doi,
                LocalDate.of(2024, 6, 1), 50,
                List.of(new ExternalKeywordInfo("Cell Biology", "Life Sciences")), // domain không được phép
                null, null, null, false, "Bio Journal", "OPENALEX", "W444444444",
                List.of(), List.of()
        );

        SyncLog log = runSyncWith(List.of(paper));

        assertEquals(SyncStatus.SUCCESS, log.getStatus());
        assertEquals(0, log.getPapersInserted(),
                "Paper có toàn bộ keyword thuộc domain không được phép không được lưu");
        assertFalse(paperRepository.findAll().stream().anyMatch(p -> doi.equals(p.getDoi())),
                "Paper không được tồn tại trong DB");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 5: Paper trùng DOI → không insert thêm lần 2
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    void sync_duplicateDoi_notInsertedTwice() throws InterruptedException {
        String doi = TEST_DOI_PREFIX + "dup-005";
        ExternalPaperMetadata paper = validPaper(doi, "W555555555");

        // Lần sync đầu
        runSyncWith(List.of(paper));
        long countAfterFirst = paperRepository.findAll().stream()
                .filter(p -> doi.equals(p.getDoi())).count();
        assertEquals(1, countAfterFirst, "Lần đầu phải có đúng 1 paper");

        // Reset crawler state, sync lần 2 với cùng paper
        keywordSyncStateRepository.deleteAll();
        runSyncWith(List.of(paper));

        long countAfterSecond = paperRepository.findAll().stream()
                .filter(p -> doi.equals(p.getDoi())).count();
        assertEquals(1, countAfterSecond, "Paper trùng DOI không được tạo thêm bản ghi");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 6: Author có OpenAlex ID → hIndex và citationCount được lưu vào DB
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    void sync_newAuthorWithOpenAlexId_hIndexSaved() throws InterruptedException {
        String doi = TEST_DOI_PREFIX + "hindex-006";
        String authorOpenAlexId = "ATEST0000001";

        ExternalPaperMetadata paper = new ExternalPaperMetadata(
                "Paper with Author", "Abstract",
                doi,
                LocalDate.of(2024, 6, 1), 200,
                List.of(new ExternalKeywordInfo("Deep Learning", "Computer Science")),
                null, null, null, false, "Journal", "OPENALEX", "W666666666",
                List.of(new ExternalAuthorInfo("Dr. Test Author", "OPENALEX", authorOpenAlexId, "Test University")),
                List.of()
        );

        // Override mock để trả về profile với hIndex=42
        when(openAlexClient.fetchAuthorsByIds(any()))
                .thenReturn(List.of(new ExternalAuthorProfile(
                        authorOpenAlexId, "Dr. Test Author", "Test University",
                        1500, 50, 42)));

        runSyncWith(List.of(paper));

        Optional<Author> savedAuthor = authorRepository
                .findBySourceTypeAndSourceIdentifier("OPENALEX", authorOpenAlexId);
        assertTrue(savedAuthor.isPresent(), "Author phải được lưu vào DB");
        assertEquals(42, savedAuthor.get().getHIndex(), "hIndex phải được set từ OpenAlex API");
        assertEquals(1500, savedAuthor.get().getCitationCount(), "citationCount phải được set từ OpenAlex API");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 7: enrichAuthorStats() — cập nhật hIndex cho author cũ chưa có stats
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    void enrichAuthorStats_updatesUnenrichedAuthors() {
        String authorSourceId = "ATEST9999999";
        Author unenriched = authorRepository.save(Author.builder()
                .name("Unenriched Author")
                .affiliation("Some University")
                .citationCount(0)
                .sourceType("OPENALEX")
                .sourceIdentifier(authorSourceId)
                .build());

        assertNull(unenriched.getHIndex(), "hIndex ban đầu phải là null");

        // Mock trả về profile cho bất kỳ ID nào — chỉ ATEST9999999 mới khớp trong profileMap
        when(openAlexClient.fetchAuthorsByIds(any()))
                .thenReturn(List.of(new ExternalAuthorProfile(
                        authorSourceId, "Unenriched Author", "Some University", 800, 40, 25)));

        // Dùng limit lớn để đảm bảo ATEST9999999 (ID mới, lớn) được đưa vào batch
        // DB thật có thể có nhiều author unenriched cũ với ID nhỏ hơn
        paperSyncService.enrichAuthorStats(100_000);

        Author updated = authorRepository.findById(unenriched.getId()).orElseThrow();
        assertEquals(25, updated.getHIndex(), "hIndex phải được cập nhật thành 25");
        assertEquals(800, updated.getCitationCount(), "citationCount phải được cập nhật thành 800");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEST 8: Sync thất bại khi OpenAlex bị tắt → status FAILED
    // ═════════════════════════════════════════════════════════════════════════
    @Test
    void sync_openAlexDisabled_syncFailed() throws InterruptedException {
        when(apiSourceService.isEnabled("OpenAlex")).thenReturn(false);

        SyncLogResponse response = paperSyncService.startSync(null);
        SyncLog log = waitForSync(response.getId());

        assertEquals(SyncStatus.FAILED, log.getStatus(), "Sync phải FAILED khi OpenAlex bị tắt");
        assertNotNull(log.getErrorMessage(), "Phải có error message mô tả nguyên nhân");
    }
}
