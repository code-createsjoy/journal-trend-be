# Implementation Plan — Password History (Backend)

Block reuse of the **last N passwords** during password reset (and change). All user-facing
error messages are in **English**.

- **Stack:** Spring Boot 3.5 + JPA (SQL Server, `ddl-auto` auto-manages schema)
- **Core file:** `service/impl/AuthServiceImpl.java`
- **Scope:** `resetPassword` (forgot-password flow) + `changePassword` (logged-in flow)
- **Default N:** 5 (configurable)

> The current password lives in `user.getPassword()`. The history table stores the
> **(N-1)** older hashes. Together they cover exactly the **last N** passwords, with no
> duplicate of the current one in history.

---

## Step B1 — New entity `PasswordHistory`

New file: `src/main/java/com/norman/swp391/entity/PasswordHistory.java`
Pattern copied from `PasswordResetToken.java`. Table auto-created by Hibernate.

```java
package com.norman.swp391.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "password_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

---

## Step B2 — New repository `PasswordHistoryRepository`

New file: `src/main/java/com/norman/swp391/repository/PasswordHistoryRepository.java`

```java
package com.norman.swp391.repository;

import com.norman.swp391.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    // Most-recent-first; caller slices to (N-1)
    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserId(Long userId);
}
```

---

## Step B3 — Config value `passwordHistoryCount`

In `config/AppProperties.java` add a field (default 5) and expose a getter, then bind it
in `application.yml`:

```yaml
app:
  password-history-count: 5
```

Lets you tune N without redeploying code changes.

---

## Step B4 — Logic in `AuthServiceImpl`

1. Inject the repository:

```java
private final PasswordHistoryRepository passwordHistoryRepository;
```

2. Add two private helpers:

```java
/** Reject the new password if it matches the current or any of the last N-1 passwords. */
private void enforcePasswordHistory(User user, String newRawPassword) {
    int n = appProperties.getPasswordHistoryCount();
    String errorMessage = "New password must be different from your last " + n + " passwords";

    // 1) current password
    if (passwordEncoder.matches(newRawPassword, user.getPassword())) {
        throw new BadRequestException(errorMessage);
    }
    // 2) previous (N-1) hashes
    passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
            .stream().limit(n - 1)
            .forEach(h -> {
                if (passwordEncoder.matches(newRawPassword, h.getPasswordHash())) {
                    throw new BadRequestException(errorMessage);
                }
            });
}

/** Archive the OLD (current) password into history, then prune to N-1 newest rows. */
private void archiveOldPassword(User user) {
    passwordHistoryRepository.save(PasswordHistory.builder()
            .user(user)
            .passwordHash(user.getPassword())   // old hash, before overwrite
            .createdAt(LocalDateTime.now())
            .build());

    int keep = appProperties.getPasswordHistoryCount() - 1;
    passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
            .stream().skip(keep)
            .forEach(passwordHistoryRepository::delete);
}
```

3. Wire into `resetPassword` (currently around the `user.setPassword(...)` line):

```java
User user = resetToken.getUser();
enforcePasswordHistory(user, request.getNewPassword());   // NEW
archiveOldPassword(user);                                 // NEW
user.setPassword(passwordEncoder.encode(request.getNewPassword()));
userRepository.save(user);
resetToken.setUsed(true);
passwordResetTokenRepository.save(resetToken);
refreshTokenRepository.deleteByUserId(user.getId());
```

4. Wire into `changePassword` the same way, right after the current-password check:

```java
if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
    throw new BadRequestException("Current password is incorrect");
}
enforcePasswordHistory(user, request.getNewPassword());   // NEW
archiveOldPassword(user);                                 // NEW
user.setPassword(passwordEncoder.encode(request.getNewPassword()));
userRepository.save(user);
refreshTokenRepository.deleteByUserId(userId);
```

---

## Step B5 — (Optional) Cleanup on user deletion

If users can be deleted, call `passwordHistoryRepository.deleteByUserId(id)` in that flow,
or add an `ON DELETE CASCADE` FK, to avoid orphaned rows.

---

## Error messages (English)

- `New password must be different from your last 5 passwords` (includes N — recommended)
- or: `New password must not match any of your recent passwords`

`BadRequestException` is mapped to an HTTP 400 with the message by `GlobalExceptionHandler`,
so the frontend receives the text directly.

---

## Verification checklist

1. Reset with the **current** password → rejected (400 + message).
2. Reset with a password used in the **previous** reset → rejected.
3. Reset with a brand-new password → succeeds.
4. Change more than N times, then try the oldest one again → allowed (fell out of history).
5. Confirm the `password_history` table is auto-created and rows are pruned to N-1.

---

## Files touched

| File | Change |
|---|---|
| `entity/PasswordHistory.java` | **new** entity |
| `repository/PasswordHistoryRepository.java` | **new** repository |
| `config/AppProperties.java` | add `passwordHistoryCount` |
| `application.yml` | add `app.password-history-count` |
| `service/impl/AuthServiceImpl.java` | inject repo + 2 helpers + wire `resetPassword` & `changePassword` |
