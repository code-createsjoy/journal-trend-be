package com.norman.swp391.security;

import com.norman.swp391.entity.User;
import com.norman.swp391.entity.enums.UserRole;
import com.norman.swp391.entity.enums.UserStatus;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Bọc entity User cho Spring Security.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final UserRole role;
    private final UserStatus status;
    private final boolean enabled;
    private final boolean verified;

/**
 * Xử lý nghiệp vụ: CustomUserDetails.
 */
    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.enabled = user.isEnabled() || user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN;
        this.verified = user.isVerified() || user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN;
    }

    @Override
/**
 * Lấy dữ liệu: getAuthorities.
 */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
/**
 * Lấy dữ liệu: getUsername.
 */
    public String getUsername() {
        return email;
    }

    @Override
/**
 * Xử lý nghiệp vụ: isAccountNonExpired.
 */
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
/**
 * Xử lý nghiệp vụ: isAccountNonLocked.
 */
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
/**
 * Xử lý nghiệp vụ: isCredentialsNonExpired.
 */
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
/**
 * Xử lý nghiệp vụ: isEnabled.
 */
    public boolean isEnabled() {
        return enabled;
    }
}
