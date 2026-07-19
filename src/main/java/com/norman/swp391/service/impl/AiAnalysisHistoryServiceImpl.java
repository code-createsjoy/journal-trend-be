package com.norman.swp391.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.norman.swp391.dto.response.ai.AiAnalysisHistoryDetailResponse;
import com.norman.swp391.dto.response.ai.AiAnalysisHistorySummaryResponse;
import com.norman.swp391.dto.response.common.PageResponse;
import com.norman.swp391.entity.AiAnalysisHistory;
import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.AiAnalysisType;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.exception.UnauthorizedException;
import com.norman.swp391.mapper.AiAnalysisHistoryMapper;
import com.norman.swp391.repository.AiAnalysisHistoryRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.AiAnalysisHistoryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý lịch sử phân tích xu hướng bằng AI của user. */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiAnalysisHistoryServiceImpl implements AiAnalysisHistoryService {

    private final AiAnalysisHistoryRepository aiAnalysisHistoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void saveHistory(AiAnalysisType type, List<String> targetKeywords, String overallVerdict,
            Object rawResponse) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            if (userId == null) {
                return;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }

            aiAnalysisHistoryRepository.save(AiAnalysisHistory.builder()
                    .user(user)
                    .analysisType(type)
                    .targetKeywords(objectMapper.writeValueAsString(targetKeywords != null ? targetKeywords : List.of()))
                    .overallVerdict(overallVerdict)
                    .responseJson(objectMapper.writeValueAsString(rawResponse))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("[AI_HISTORY] Failed to save AI analysis history (non-fatal): {}", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AiAnalysisHistorySummaryResponse> listHistory(Pageable pageable) {
        Long userId = requireUserId();
        Page<AiAnalysisHistory> page = aiAnalysisHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, AiAnalysisHistoryMapper.toSummaryList(page.getContent(), objectMapper));
    }

    @Override
    @Transactional(readOnly = true)
    public AiAnalysisHistoryDetailResponse getHistoryDetail(Long id) {
        Long userId = requireUserId();
        AiAnalysisHistory history = aiAnalysisHistoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AiAnalysisHistory", id));
        return AiAnalysisHistoryMapper.toDetail(history, objectMapper);
    }

    @Override
    @Transactional
    public void deleteHistory(Long id) {
        Long userId = requireUserId();
        AiAnalysisHistory history = aiAnalysisHistoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AiAnalysisHistory", id));
        aiAnalysisHistoryRepository.delete(history);
    }

    @Override
    @Transactional
    public void deleteAllHistory() {
        Long userId = requireUserId();
        aiAnalysisHistoryRepository.deleteByUserId(userId);
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userId;
    }
}
