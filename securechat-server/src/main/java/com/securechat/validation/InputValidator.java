package com.securechat.validation;

import com.securechat.exception.BusinessException;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern OTP_6 = Pattern.compile("^\\d{6}$");
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 72;
    private static final int FULLNAME_MIN = 1;
    private static final int FULLNAME_MAX = 120;
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");
    private static final Set<String> MESSAGE_TYPES_M1 = Set.of("TEXT", "STICKER");
    private static final int BROADCAST_MAX = 1000;

    // [M4-B] FILE_CHUNK limits per M4-B §3
    private static final int FILE_CHUNK_MAX_TOTAL = 128;              // 128 × 8 KiB ≈ 1 MiB
    private static final int FILE_CHUNK_MAX_DECODED_BYTES = 262_144;  // 256 KiB protocol §2

    private InputValidator() {}

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

    public static String validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank())
            throw new BusinessException("FULLNAME_REQUIRED", "Họ tên bắt buộc");
        String t = fullName.trim();
        if (t.length() < FULLNAME_MIN || t.length() > FULLNAME_MAX)
            throw new BusinessException("FULLNAME_INVALID", "Họ tên không hợp lệ");
        return t;
    }

    public static String validateGender(String gender) {
        if (gender == null || gender.isBlank()) return null;
        String g = gender.trim().toUpperCase(Locale.ROOT);
        if (!GENDERS.contains(g))
            throw new BusinessException("INVALID_GENDER", "Giới tính phải là MALE/FEMALE/OTHER");
        return g;
    }

    public static LocalDate validateDateOfBirth(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(dateOfBirth.trim());
            if (d.isAfter(LocalDate.now()))
                throw new BusinessException("INVALID_DATE_OF_BIRTH", "Ngày sinh không hợp lệ");
            return d;
        } catch (DateTimeParseException e) {
            throw new BusinessException("INVALID_DATE_OF_BIRTH", "Ngày sinh phải có định dạng yyyy-MM-dd");
        }
    }

    public static void validateOtp(String otp) {
        if (otp == null || !OTP_6.matcher(otp).matches())
            throw new BusinessException("OTP_INVALID_FORMAT", "OTP phải là 6 chữ số");
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String validateMessageContent(String content) {
        if (content == null || content.isBlank())
            throw new BusinessException("CONTENT_INVALID", "Nội dung tin nhắn không được để trống");
        String trimmed = content.trim();
        if (trimmed.length() > 4000)
            throw new BusinessException("CONTENT_INVALID", "Tin nhắn tối đa 4000 ký tự");
        return trimmed;
    }

    public static String validateGroupName(String raw) {
        if (raw == null || raw.isBlank())
            throw new BusinessException("GROUP_NAME_INVALID", "Tên nhóm không được để trống");
        String trimmed = raw.trim();
        if (trimmed.length() > 100)
            throw new BusinessException("GROUP_NAME_INVALID", "Tên nhóm tối đa 100 ký tự");
        return trimmed;
    }

    public static int normalizeHistoryLimit(int limit) {
        if (limit <= 0) return 30;
        return Math.min(limit, 100);
    }

    public static String validateMsgType(String raw) {
        if (raw == null || raw.isBlank()) return "TEXT";
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (!MESSAGE_TYPES_M1.contains(upper)) {
            throw new BusinessException("INVALID_MESSAGE_TYPE",
                    "msgType phải là TEXT hoặc STICKER (FILE chưa hỗ trợ)");
        }
        return upper;
    }

    public static void validateProfileAllowedFields(Map<String, Object> payload) {
        if (payload == null) return;
        String[] allowed = {"fullName", "gender", "dateOfBirth"};
        for (String k : payload.keySet()) {
            boolean ok = false;
            for (String a : allowed) if (a.equals(k)) { ok = true; break; }
            if (!ok) throw new BusinessException("INVALID_FIELD",
                    "Trường không được phép cập nhật: " + k);
        }
    }

    public static String validateGroupRole(String raw) {
        if (raw == null || raw.isBlank())
            throw new BusinessException("INVALID_ROLE", "Role bắt buộc");
        String r = raw.trim().toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(r) && !"MEMBER".equals(r))
            throw new BusinessException("INVALID_ROLE", "Role phải là ADMIN hoặc MEMBER");
        return r;
    }

    public static String validateMarkReadType(String raw) {
        if (raw == null || raw.isBlank())
            throw new BusinessException("INVALID_MESSAGE", "messageType bắt buộc");
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (!"DIRECT".equals(t) && !"GROUP".equals(t))
            throw new BusinessException("INVALID_MESSAGE", "messageType phải DIRECT/GROUP");
        return t;
    }

    public static String validateBroadcastMessage(String raw) {
        if (raw == null || raw.isBlank())
            throw new BusinessException("INVALID_MESSAGE", "Nội dung broadcast bắt buộc");
        String t = raw.trim();
        if (t.length() > BROADCAST_MAX)
            throw new BusinessException("INVALID_MESSAGE",
                    "Broadcast tối đa " + BROADCAST_MAX + " ký tự");
        return t;
    }

    /**
     * [M4-B] Validate một FILE_CHUNK packet (stateless per-chunk, KD-3).
     * Trả Map chuẩn hóa chứa đúng 5 field nghiệp vụ + routing key (receiver HOẶC groupName).
     * Không đọc field định danh người gửi (senderId/senderEmail) từ payload — đúng nguyên tắc
     * "danh tính lấy từ session".
     *
     * @throws BusinessException code=INVALID_MESSAGE khi bất kỳ điều kiện nào sai.
     */
    public static Map<String, Object> validateFileChunk(Map<String, Object> payload) {
        if (payload == null) {
            throw new BusinessException("INVALID_MESSAGE", "payload rỗng");
        }

        Object fileIdO = payload.get("fileId");
        if (!(fileIdO instanceof String fileId) || fileId.isBlank()) {
            throw new BusinessException("INVALID_MESSAGE", "fileId bắt buộc và không rỗng");
        }

        Object fileNameO = payload.get("fileName");
        if (!(fileNameO instanceof String fileName) || fileName.isBlank()) {
            throw new BusinessException("INVALID_MESSAGE", "fileName bắt buộc và không rỗng");
        }

        int totalChunks = parseIntField(payload, "totalChunks");
        if (totalChunks < 1 || totalChunks > FILE_CHUNK_MAX_TOTAL) {
            throw new BusinessException("INVALID_MESSAGE",
                    "totalChunks phải trong [1, " + FILE_CHUNK_MAX_TOTAL + "]");
        }

        int chunkIndex = parseIntField(payload, "chunkIndex");
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new BusinessException("INVALID_MESSAGE",
                    "chunkIndex phải trong [0, totalChunks)");
        }

        Object dataO = payload.get("data");
        if (!(dataO instanceof String dataB64) || dataB64.isEmpty()) {
            throw new BusinessException("INVALID_MESSAGE", "data bắt buộc và không rỗng");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(dataB64);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_MESSAGE", "data không phải Base64 hợp lệ");
        }
        if (decoded.length < 1 || decoded.length > FILE_CHUNK_MAX_DECODED_BYTES) {
            throw new BusinessException("INVALID_MESSAGE",
                    "data phải giải mã ra 1.." + FILE_CHUNK_MAX_DECODED_BYTES + " byte");
        }

        Object receiverO = payload.get("receiver");
        Object groupNameO = payload.get("groupName");
        boolean hasReceiver = (receiverO instanceof String r) && !r.isBlank();
        boolean hasGroup    = (groupNameO instanceof String g) && !g.isBlank();
        if (hasReceiver == hasGroup) {
            throw new BusinessException("INVALID_MESSAGE",
                    "Phải có đúng một trong hai field receiver hoặc groupName");
        }

        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("fileId", fileId);
        chunk.put("fileName", fileName);
        chunk.put("chunkIndex", chunkIndex);
        chunk.put("totalChunks", totalChunks);
        chunk.put("data", dataB64);   // nguyên văn, không decode/re-encode ở chiều relay
        if (hasReceiver) {
            chunk.put("receiver", ((String) receiverO).trim());
        } else {
            chunk.put("groupName", ((String) groupNameO).trim());
        }
        return chunk;
    }

    private static int parseIntField(Map<String, Object> p, String key) {
        Object o = p.get(key);
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) {
                throw new BusinessException("INVALID_MESSAGE", key + " phải là số nguyên");
            }
        }
        throw new BusinessException("INVALID_MESSAGE", key + " bắt buộc");
    }
}