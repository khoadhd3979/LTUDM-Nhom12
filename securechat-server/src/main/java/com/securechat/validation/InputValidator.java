package com.securechat.validation;

import com.securechat.exception.BusinessException;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern OTP_6 = Pattern.compile("^\\d{6}$");
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 72;   // BCrypt max UTF-8 bytes
    private static final int FULLNAME_MIN = 1;
    private static final int FULLNAME_MAX = 120;
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");

    private InputValidator() {}

    /** @return normalized email (trim + lowercase). */
    public static String validateEmail(String email) {
        if (email == null || email.isBlank()) throw new BusinessException("EMAIL_INVALID", "Email bắt buộc");
        String t = email.trim().toLowerCase(Locale.ROOT);
        if (t.length() > 255 || !EMAIL.matcher(t).matches())
            throw new BusinessException("EMAIL_INVALID", "Email không hợp lệ");
        return t;
    }

    public static void validatePassword(String pwd) {
        if (pwd == null || pwd.isEmpty())
            throw new BusinessException("PASSWORD_REQUIRED", "Mật khẩu bắt buộc");
        if (pwd.length() < PASSWORD_MIN)
            throw new BusinessException("PASSWORD_TOO_SHORT", "Mật khẩu phải từ " + PASSWORD_MIN + " ký tự");
        int passwordBytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(pwd)).remaining();
        if (passwordBytes > PASSWORD_MAX)
            throw new BusinessException("PASSWORD_TOO_LONG", "Mật khẩu tối đa " + PASSWORD_MAX + " byte UTF-8");
    }

    /** @return trimmed full name. */
    public static String validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank())
            throw new BusinessException("FULLNAME_REQUIRED", "Họ tên bắt buộc");
        String t = fullName.trim();
        if (t.length() < FULLNAME_MIN || t.length() > FULLNAME_MAX)
            throw new BusinessException("FULLNAME_INVALID", "Họ tên không hợp lệ");
        return t;
    }

    /** @return uppercase gender or null if input blank/null. */
    public static String validateGender(String gender) {
        if (gender == null || gender.isBlank()) return null;
        String g = gender.trim().toUpperCase(Locale.ROOT);
        if (!GENDERS.contains(g))
            throw new BusinessException("INVALID_GENDER", "Giới tính phải là MALE/FEMALE/OTHER");
        return g;
    }

    /** @return parsed date or null if blank/null. Format: ISO yyyy-MM-dd. */
    public static LocalDate validateDateOfBirth(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(dateOfBirth.trim());
            if (d.isAfter(LocalDate.now())) throw new BusinessException("INVALID_DATE_OF_BIRTH", "Ngày sinh không hợp lệ");
            return d;
        } catch (DateTimeParseException e) {
            throw new BusinessException("INVALID_DATE_OF_BIRTH", "Ngày sinh phải có định dạng yyyy-MM-dd");
        }
    }

    public static void validateOtp(String otp) {
        if (otp == null || !OTP_6.matcher(otp).matches())
            throw new BusinessException("OTP_INVALID_FORMAT", "OTP phải là 6 chữ số");
    }

    // Giữ lại hàm tiện ích từ code 1
    public static boolean isBlank(String s) { 
        return s == null || s.isBlank(); 
    }
}