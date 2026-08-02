package com.norman.swp391.repository;

import com.norman.swp391.entity.UserDailyFeatureUsage;
import com.norman.swp391.entity.enums.FeatureType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Kho truy cập bộ đếm quota AI hàng ngày theo user + feature + ngày. */
public interface UserDailyFeatureUsageRepository extends JpaRepository<UserDailyFeatureUsage, Long> {

    Optional<UserDailyFeatureUsage> findByUserIdAndFeatureTypeAndUsageDate(
            Long userId, FeatureType featureType, LocalDate usageDate);
}
