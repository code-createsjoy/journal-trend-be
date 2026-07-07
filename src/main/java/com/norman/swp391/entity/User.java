package com.norman.swp391.entity;

import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "enabled", nullable = false, columnDefinition = "bit default 0")
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "verified", nullable = false, columnDefinition = "bit default 0")
    @Builder.Default
    private boolean verified = false;

    @Column(name = "notify_keywords", nullable = false, columnDefinition = "bit default 1")
    @Builder.Default
    private boolean notifyKeywords = true;

    @Column(name = "notify_authors", nullable = false, columnDefinition = "bit default 1")
    @Builder.Default
    private boolean notifyAuthors = true;

    @Column(name = "notify_journals", nullable = false, columnDefinition = "bit default 1")
    @Builder.Default
    private boolean notifyJournals = true;

    @Column(name = "notify_email", nullable = false, columnDefinition = "bit default 1")
    @Builder.Default
    private boolean notifyEmail = true;
}

