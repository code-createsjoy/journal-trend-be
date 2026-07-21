package com.norman.swp391.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.AiAnalysisType;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.CustomUserDetails;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression test cho UnexpectedRollbackException: khi saveHistory() thất bại ở tầng DB (VD vi
 * phạm giới hạn độ dài cột), nó không được phép làm transaction của caller (VD
 * analyzeCollection) bị đánh dấu rollback-only và ném UnexpectedRollbackException ra ngoài dù
 * exception gốc đã bị bắt và log non-fatal.
 *
 * Không dùng @Transactional cấp class/method — Spring Test luôn ROLLBACK ở cuối test (thay vì
 * COMMIT), nên sẽ che giấu hoàn toàn bug này (UnexpectedRollbackException chỉ ném ra khi
 * transaction manager cố COMMIT một transaction rollback-only, không phải khi rollback chủ động).
 * Dùng TransactionTemplate thủ công để tái hiện đúng luồng commit thật như production.
 */
@SpringBootTest
class AiHistoryRollbackBugTest {

    @Autowired
    private AiAnalysisHistoryService aiAnalysisHistoryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void nestedSaveHistoryFailure_doesNotPoisonOuterTransaction() {
        User user = userRepository.save(User.builder()
                .email("rollback-bug-test@example.com")
                .password("password123")
                .fullName("Rollback Bug Test User")
                .role(UserRole.RESEARCHER)
                .status(UserStatus.ACTIVE)
                .enabled(true)
                .verified(true)
                .build());
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of()));

            // overallVerdict cột chỉ dài 50 ký tự -> chuỗi 200 ký tự này làm SQL Server ném lỗi
            // truncation ngay khi flush (IDENTITY strategy flush ngay lập tức).
            String tooLong = "X".repeat(200);

            TransactionTemplate tpl = new TransactionTemplate(transactionManager);
            tpl.setReadOnly(true); // mô phỏng đúng @Transactional(readOnly = true) của analyzeCollection

            // Mô phỏng đúng luồng thật (AiAnalysisServiceImpl.safeSaveHistory): outer
            // @Transactional(readOnly=true) gọi saveHistory() bên trong try-catch của chính nó.
            // REQUIRES_NEW cô lập transaction, nhưng saveHistory() vẫn có thể tự ném
            // UnexpectedRollbackException ra khỏi lời gọi khi commit transaction MỚI của chính nó
            // thất bại — nên caller bắt buộc phải tự bắt lỗi ở đây nữa.
            assertDoesNotThrow(() -> tpl.execute(status -> {
                try {
                    aiAnalysisHistoryService.saveHistory(AiAnalysisType.COLLECTION_ANALYSIS,
                            List.of("test"), tooLong, "raw response");
                } catch (Exception ex) {
                    // mô phỏng safeSaveHistory() trong AiAnalysisServiceImpl
                }
                return null;
            }), "saveHistory() thất bại không được phép làm transaction của caller bị rollback-only");
        } finally {
            SecurityContextHolder.clearContext();
            userRepository.delete(user);
        }
    }
}
