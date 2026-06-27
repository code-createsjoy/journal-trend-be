package com.norman.swp391.config;

import com.norman.swp391.entity.ApiSourceConfig;
import com.norman.swp391.entity.Journal;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import com.norman.swp391.repository.ApiSourceConfigRepository;
import com.norman.swp391.repository.JournalRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.service.JournalService;
import com.norman.swp391.service.PaperSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Seed dữ liệu ban đầu khi khởi động ứng dụng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String SUPER_ADMIN_EMAIL = "admin@research.local";
    private static final String SUPER_ADMIN_PASSWORD = "Admin@12345";
    private static final String HELIX_ADMIN_EMAIL = "admin@helix.io";
    private static final String HELIX_ADMIN_PASSWORD = "admin12345";

    private final UserRepository userRepository;
    private final PaperRepository paperRepository;
    private final JournalRepository journalRepository;
    private final JournalService journalService;
    private final ApiSourceConfigRepository apiSourceConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final PaperSyncService paperSyncService;
    private final AppProperties appProperties;

    /**
     * Seed tài khoản, API sources và backfill journal khi khởi động.
     */
    @Override
    public void run(String... args) {
        seedSuperAdmin();
        seedHelixAdmin();
        seedApiSources();
        backfillJournalsFromPapers();
        if (appProperties.getSync().isOnStartup() && paperRepository.countByStatus(PaperStatus.ACTIVE) == 0) {
            log.info("Scheduling initial metadata sync in background (OpenAlex + enrichment)…");
            Thread.startVirtualThread(() -> {
                try {
                    var result = paperSyncService.startSync(null);
                    log.info(
                            "Startup sync finished: {} papers, status {}",
                            result.getPapersFetched(),
                            result.getStatus());
                } catch (Exception ex) {
                    log.warn("Startup sync failed (app can still run; trigger sync from Admin): {}", ex.getMessage());
                }
            });
        }
    }

/**
 * Xử lý nghiệp vụ: seedSuperAdmin.
 */
    private void seedSuperAdmin() {
        var existing = userRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isEnabled() || !user.isVerified()) {
                user.setEnabled(true);
                user.setVerified(true);
                userRepository.save(user);
                log.info("Updated existing super admin to be enabled and verified: {}", SUPER_ADMIN_EMAIL);
            }
            return;
        }
        userRepository.save(User.builder()
                .email(SUPER_ADMIN_EMAIL)
                .password(passwordEncoder.encode(SUPER_ADMIN_PASSWORD))
                .fullName("Super Admin")
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .enabled(true)
                .verified(true)
                .build());
        log.info("Seeded super admin: {}", SUPER_ADMIN_EMAIL);
    }

/**
 * Xử lý nghiệp vụ: seedHelixAdmin.
 */
    private void seedHelixAdmin() {
        var existing = userRepository.findByEmailIgnoreCase(HELIX_ADMIN_EMAIL);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isEnabled() || !user.isVerified()) {
                user.setEnabled(true);
                user.setVerified(true);
                userRepository.save(user);
                log.info("Updated existing Helix admin to be enabled and verified: {}", HELIX_ADMIN_EMAIL);
            }
            return;
        }
        userRepository.save(User.builder()
                .email(HELIX_ADMIN_EMAIL)
                .password(passwordEncoder.encode(HELIX_ADMIN_PASSWORD))
                .fullName("Helix Admin")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .enabled(true)
                .verified(true)
                .build());
        log.info("Seeded Helix admin: {}", HELIX_ADMIN_EMAIL);
    }

/**
 * Xử lý nghiệp vụ: seedApiSources.
 */
    private void seedApiSources() {
        seedSource("OpenAlex", appProperties.getOpenalex().getBaseUrl(), appProperties.getSync().getCron());
    }

/**
 * Xử lý nghiệp vụ: seedSource.
 */
    private void seedSource(String name, String baseUrl, String schedule) {
        if (apiSourceConfigRepository.findByNameIgnoreCase(name).isPresent()) {
            return;
        }
        apiSourceConfigRepository.save(ApiSourceConfig.builder()
                .name(name)
                .baseUrl(baseUrl)
                .enabled(true)
                .syncSchedule(schedule)
                .successRate(99.0)
                .updatedAt(LocalDateTime.now())
                .build());
        log.info("Seeded API source config: {}", name);
    }

/**
 * Xử lý nghiệp vụ: backfillJournalsFromPapers.
 */
    @Transactional
    protected void backfillJournalsFromPapers() {
        Map<String, Journal> journalCache = new HashMap<>();
        journalRepository.findAll().forEach(j -> journalCache.putIfAbsent(normalizeJournalKey(j.getName()), j));

        int linked = 0;
        int skipped = 0;
        for (var row : paperRepository.findAllForJournalBackfill()) {
            if (row.getJournalRefId() != null || !StringUtils.hasText(row.getJournal())) {
                continue;
            }
            String name = row.getJournal().trim();
            String key = normalizeJournalKey(name);
            try {
                Journal journal = journalCache.get(key);
                if (journal == null) {
                    journal = journalService.findOrCreate(name, null, "General");
                    if (journal != null && journal.getId() != null) {
                        journalCache.put(key, journal);
                    }
                }
                if (journal == null || journal.getId() == null) {
                    skipped++;
                    continue;
                }
                linked += paperRepository.linkJournal(row.getId(), journal.getId());
            } catch (Exception ex) {
                skipped++;
                journalRepository.findByNameIgnoreCase(name).ifPresent(j -> journalCache.put(key, j));
                log.debug("Skipped journal link for paper id={}: {}", row.getId(), ex.getMessage());
            }
        }
        if (linked > 0) {
            log.info("Backfilled journal_ref for {} paper(s)", linked);
        }
        if (skipped > 0) {
            log.warn("Skipped journal backfill for {} paper(s) due to name conflicts", skipped);
        }
    }

    private static String normalizeJournalKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
