package com.norman.swp391.entity;

import com.norman.swp391.entity.enums.FeatureType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Đếm số lần user dùng 1 tính năng AI trong ngày, phục vụ quota hàng ngày (VD literature matrix: 10 lần/ngày). */
@Entity
@Table(
        name = "user_daily_feature_usages",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "feature_type", "usage_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDailyFeatureUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 50)
    private FeatureType featureType;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private int usageCount = 1;
}
