package com.securechat.client.validation;

import java.io.File;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;

/** Kiểm tra đầu vào GUI. Mỗi hàm trả về thông báo lỗi, hoặc null nếu hợp lệ. */
public final class InputValidator {
    private InputValidator() {}

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 72;          // giới hạn 72 byte của BCrypt
    public static final int MAX_FRAME_BYTES = 1_000_000; // Protocol v0.2, mục 3.1
    public static final int MAX_MESSAGE_CHARS = 2000;
    public static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L} .'-]*$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern OTP = Pattern.compile("^\\d{6}$");

    public static String firstError(String... errors) {
        for (String e : errors) if (e != null) return e;
        return null;
    }

    public static String host(String s) {
        if (s == null || s.isBlank()) return "Vui lòng nhập IP/Host của Server.";
        return HOST.matcher(s).matches() ? null : "IP/Host không hợp lệ.";
    }

    public static String port(String s) {
        try {
            int p = Integer.parseInt(s == null ? "" : s.trim());
            return (p >= 1 && p <= 65535) ? null : "Port phải nằm trong khoảng 1 - 65535.";
        } catch (NumberFormatException e) {
            return "Port phải là một số nguyên.";
        }
    }

    public static String email(String s) {
        if (s == null || s.isBlank()) return "Vui lòng nhập email.";
        if (s.length() > 254 || !EMAIL.matcher(s).matches()) return "Email không đúng định dạng.";
        return null;
    }

    /** Dùng khi đăng nhập: chỉ yêu cầu không rỗng (Server tự kiểm tra). */
    public static String passwordRequired(char[] pw) {
        return (pw == null || pw.length == 0) ? "Vui lòng nhập mật khẩu." : null;
    }

    /** Dùng khi đăng ký: 8 ký tự trở lên và tối đa 72 byte UTF-8. */
    public static String passwordPolicy(char[] pw) {
        if (pw == null || pw.length < PASSWORD_MIN) return "Mật khẩu phải có ít nhất " + PASSWORD_MIN + " ký tự.";
        int bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(pw)).remaining();
        if (bytes > PASSWORD_MAX) return "Mật khẩu quá dài (tối đa " + PASSWORD_MAX + " byte).";
        return null;
    }

    public static String fullName(String s) {
        if (s == null || s.isBlank()) return "Vui lòng nhập họ và tên.";
        if (s.length() < 2 || s.length() > 100) return "Họ và tên phải từ 2 đến 100 ký tự.";
        return NAME.matcher(s).matches() ? null : "Họ và tên chỉ gồm chữ cái, khoảng trắng, dấu . ' -";
    }

    public static String gender(String s) {
        return GENDERS.contains(s) ? null : "Giới tính phải là MALE, FEMALE hoặc OTHER.";
    }

    public static String dateOfBirth(String s) {
        if (s == null || !DATE.matcher(s).matches()) return "Ngày sinh phải theo định dạng yyyy-MM-dd (VD: 2000-01-15).";
        try {
            LocalDate d = LocalDate.parse(s);
            if (!d.isBefore(LocalDate.now())) return "Ngày sinh phải ở trong quá khứ.";
            if (d.isBefore(LocalDate.of(1900, 1, 1))) return "Ngày sinh không hợp lệ.";
            return null;
        } catch (DateTimeParseException e) {
            return "Ngày sinh không tồn tại trong lịch.";
        }
    }

    public static String otp(String s) {
        return (s != null && OTP.matcher(s).matches()) ? null : "Mã OTP gồm đúng 6 chữ số.";
    }

    public static String chatMessage(String s) {
        if (s == null || s.isBlank()) return "Tin nhắn không được để trống.";
        return s.length() > MAX_MESSAGE_CHARS ? "Tin nhắn tối đa " + MAX_MESSAGE_CHARS + " ký tự." : null;
    }

    public static String attachment(File f) {
        if (f == null || !f.isFile()) return "File không tồn tại.";
        if (f.length() >= 1_000_000) return "File phải nhỏ hơn 1MB.";
        return null;
    }
}