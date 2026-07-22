package com.norman.swp391.service.impl;

import com.norman.swp391.config.AppProperties;
import com.norman.swp391.entity.AiCollectionAnalysisSetting;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.repository.AiCollectionAnalysisSettingRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.AiCollectionAnalysisSettingService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cho phép admin chỉnh cap "số paper/lượt phân tích AI collection" lúc chạy, không cần đổi
 * application.yml + redeploy. Bảng chỉ có 1 dòng duy nhất (id=1L), tự seed từ
 * app.sync.max-papers-for-collection-analysis nếu chưa từng bị đổi.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiCollectionAnalysisSettingServiceImpl implements AiCollectionAnalysisSettingService {

    private static final Long SETTING_ID = 1L;
    /** Trần cứng để tránh admin đặt quá cao: response AI có thể bị cắt cụt (maxOutputTokens) và chất lượng cluster suy giảm khi list quá dài. */
    private static final int HARD_MAX = 100;

    private final AiCollectionAnalysisSettingRepository settingRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public int getMaxPapers() {
        return settingRepository.findById(SETTING_ID)
                .map(AiCollectionAnalysisSetting::getMaxPapers)
                .orElseGet(this::seedDefault);
    }

    @Override
    @Transactional
    public int updateMaxPapers(int newMaxPapers) {
        if (newMaxPapers < 1 || newMaxPapers > HARD_MAX) {
            throw new BadRequestException("maxPapers must be between 1 and " + HARD_MAX);
        }
        AiCollectionAnalysisSetting setting = settingRepository.findById(SETTING_ID)
                .orElseGet(() -> AiCollectionAnalysisSetting.builder().id(SETTING_ID).build());
        setting.setMaxPapers(newMaxPapers);
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(SecurityUtils.getCurrentUserId());
        settingRepository.save(setting);
        log.info("[AI_SETTINGS] maxPapersForCollectionAnalysis updated to {} by userId={}", newMaxPapers,
                setting.getUpdatedBy());
        return newMaxPapers;
    }

    private int seedDefault() {
        int defaultValue = appProperties.getSync().getMaxPapersForCollectionAnalysis();
        settingRepository.save(AiCollectionAnalysisSetting.builder()
                .id(SETTING_ID)
                .maxPapers(defaultValue)
                .updatedAt(LocalDateTime.now())
                .updatedBy(null)
                .build());
        return defaultValue;
    }
}
