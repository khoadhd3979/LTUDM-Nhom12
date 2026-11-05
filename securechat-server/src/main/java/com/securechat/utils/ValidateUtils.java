package com.securechat.utils;

import java.util.regex.Pattern;


/**
 * @deprecated Not referenced anywhere. Superseded by
 *             {@link com.securechat.validation.InputValidator}.
 *             Slated for removal after audit. Do not add new callers.
 */
@Deprecated(forRemoval = true)


public class ValidateUtils {
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";

    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        return Pattern.matches(EMAIL_REGEX, email);
    }

    public static boolean isValidPassword(char[] password) {
        return password != null && password.length >= 6;
    }

    public static boolean isValidDate(String date) {
        if (date == null || date.isEmpty()) return false;
        return Pattern.matches(DATE_REGEX, date);
    }
}