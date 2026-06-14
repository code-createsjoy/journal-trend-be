package com.norman.swp391.service.impl;

import com.norman.swp391.dto.response.admin.SyncLogResponse;
import com.norman.swp391.dto.response.admin.SystemStatsResponse;
import com.norman.swp391.dto.response.admin.UserAdminResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.entity.Paper;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.PaperStatus;
import com.norman.swp391.entity.enums.SyncStatus;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.mapper.SyncLogMapper;
import com.norman.swp391.mapper.UserMapper;
import com.norman.swp391.repository.AuthorRepository;
import com.norman.swp391.repository.PaperCollectionRepository;
import com.norman.swp391.repository.PaperRepository;
import com.norman.swp391.repository.SyncLogRepository;
import com.norman.swp391.repository.KeywordRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.AdminService;
import com.norman.swp391.service.PaperSyncService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai dịch vụ quản trị.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final PaperSyncService paperSyncService;
    private final SyncLogRepository syncLogRepository;
    private final UserRepository userRepository;
    private final PaperRepository paperRepository;
    private final KeywordRepository keywordRepository;
    private final AuthorRepository authorRepository;
    private final PaperCollectionRepository paperCollectionRepository;

    @Override
/**
 * Kích hoạt đồng bộ dữ liệu bài báo từ API ngoài.
 */
    public SyncLogResponse triggerSync() {
        Long adminId = SecurityUtils.getCurrentUserId();
        return paperSyncService.startSync(adminId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SyncLogResponse> listSyncLogs(
            SyncStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Page<com.norman.swp391.entity.SyncLog> page = syncLogRepository.filter(status, from, to, pageable);
        return PageResponse.from(page, SyncLogMapper.toResponseList(page.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Tìm kiếm người dùng theo email hoặc tên.
 */
    public PageResponse<UserAdminResponse> searchUsers(String q, Pageable pageable) {
        String query = (q != null && q.isBlank()) ? null : q;
        Page<User> page = userRepository.search(query, pageable);
        return PageResponse.from(page, UserMapper.toAdminResponseList(page.getContent()));
    }

    @Override
    @Transactional
/**
 * Khóa tài khoản người dùng.
 */
    public UserAdminResponse lockUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BadRequestException("Cannot lock super admin account");
        }
        user.setStatus(UserStatus.LOCKED);
        return UserMapper.toAdminResponse(userRepository.save(user));
    }

    @Override
    @Transactional
/**
 * Mở khóa tài khoản người dùng.
 */
    public UserAdminResponse unlockUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setStatus(UserStatus.ACTIVE);
        return UserMapper.toAdminResponse(userRepository.save(user));
    }

    @Override
    @Transactional
/**
 * Xóa mềm bài báo (đổi trạng thái).
 */
    public void softDeletePaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId).orElseThrow(() -> new ResourceNotFoundException("Paper", paperId));
        paper.setStatus(PaperStatus.DELETED);
        paperRepository.save(paper);
    }

    @Override
    @Transactional(readOnly = true)
/**
 * Thống kê tổng quan hệ thống cho admin.
 */
    public SystemStatsResponse getSystemStats() {
        var lastSync = syncLogRepository.findFirstByOrderByStartedAtDesc();
        return SystemStatsResponse.builder()
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countByStatus(UserStatus.ACTIVE))
                .lockedUsers(userRepository.countByStatus(UserStatus.LOCKED))
                .totalPapers(paperRepository.countByStatus(PaperStatus.ACTIVE))
                .totalKeywords(keywordRepository.count())
                .totalAuthors(authorRepository.count())
                .totalCollections(paperCollectionRepository.count())
                .lastSyncPapersFetched(lastSync != null ? lastSync.getPapersFetched() : 0)
                .build();
    }
}
