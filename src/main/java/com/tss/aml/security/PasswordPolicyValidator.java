package com.tss.aml.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*");

    public void validate(String newPassword, String currentPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password cannot be empty");
        }

        if (newPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters long");
        }

        if (!UPPERCASE_PATTERN.matcher(newPassword).matches()) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter (A-Z)");
        }

        if (!LOWERCASE_PATTERN.matcher(newPassword).matches()) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter (a-z)");
        }

        if (!DIGIT_PATTERN.matcher(newPassword).matches()) {
            throw new IllegalArgumentException("Password must contain at least one numerical digit (0-9)");
        }

        if (!SPECIAL_CHAR_PATTERN.matcher(newPassword).matches()) {
            throw new IllegalArgumentException("Password must contain at least one special character");
        }

        if (currentPassword != null && newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("New password must be different from current password");
        }
    }
}
