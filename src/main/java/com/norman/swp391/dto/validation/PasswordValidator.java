package com.norman.swp391.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Kiểm tra mật khẩu: tối thiểu 8 ký tự, một chữ hoa và một chữ số.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*\\d).{8,}$");

    @Override
/**
 * Kiểm tra mật khẩu có đủ độ mạnh theo quy tắc.
 */
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(value).matches();
    }
}
