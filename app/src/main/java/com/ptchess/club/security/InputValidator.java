package com.ptchess.club.security;

import android.util.Patterns;

import java.util.regex.Pattern;

/**
 * Client-side validation and sanitization. This is a defence-in-depth layer:
 * all database access uses parameterized queries (see {@code ClubRepository}),
 * so SQL injection is structurally impossible, but we still reject malformed or
 * oversized input early and strip control characters from free text.
 */
public final class InputValidator {

    public static final int MAX_EMAIL = 254;
    public static final int MAX_NAME = 80;
    public static final int MAX_PASSWORD = 128;
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_TEXT = 4000;

    // At least one letter and one digit, min 8 chars.
    private static final Pattern STRONG_PASSWORD =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    private InputValidator() { }

    public static boolean isValidEmail(String email) {
        return email != null
                && email.length() <= MAX_EMAIL
                && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isStrongPassword(String password) {
        return password != null
                && password.length() >= MIN_PASSWORD
                && password.length() <= MAX_PASSWORD
                && STRONG_PASSWORD.matcher(password).matches();
    }

    public static boolean isValidName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return trimmed.length() >= 2 && trimmed.length() <= MAX_NAME;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** Removes control characters and clamps length for stored free text. */
    public static String sanitizeText(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "").trim();
        if (cleaned.length() > MAX_TEXT) {
            cleaned = cleaned.substring(0, MAX_TEXT);
        }
        return cleaned;
    }

    /** Removes control characters and clamps to a single line. */
    public static String sanitizeLine(String input, int maxLen) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (cleaned.length() > maxLen) {
            cleaned = cleaned.substring(0, maxLen);
        }
        return cleaned;
    }
}
