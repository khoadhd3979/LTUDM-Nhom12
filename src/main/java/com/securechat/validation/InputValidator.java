package com.securechat.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class InputValidator {
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 72;   // BCrypt giới hạn 72 byte

    private InputValidator() {}

    public static List<String> validateEmail(String email) {
        List<String> errs = new ArrayList<>();
        if (email == null || email.isBlank()) errs.add("EMAIL_REQUIRED");
        else if (email.length() > 255)        errs.add("EMAIL_TOO_LONG");
        else if (!EMAIL.matcher(email).matches()) errs.add("EMAIL_INVALID");
        return errs;
    }

    public static List<String> validatePassword(String pwd) {
        List<String> errs = new ArrayList<>();
        if (pwd == null || pwd.length() < PASSWORD_MIN) errs.add("PASSWORD_TOO_SHORT");
        else if (pwd.length() > PASSWORD_MAX)          errs.add("PASSWORD_TOO_LONG");
        return errs;
    }

    public static boolean isBlank(String s) { return s == null || s.isBlank(); }
}