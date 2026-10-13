package com.securechat.bll;

import com.securechat.dao.UserDAO;
import com.securechat.dto.UserDTO;
import com.securechat.utils.MailUtils;
import com.securechat.security.SecurityUtils;
import com.securechat.dao.DBConnection;

import java.security.SecureRandom;
import java.util.regex.Pattern;

import java.sql.SQLException;

public class UserBLL {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserDAO userDAO;
    private final SecureRandom random = new SecureRandom();

    public UserBLL() {
        this.userDAO = new UserDAO();
    }

    /**
     * Đăng nhập: validate → lấy user → so hash → check BLOCKED → set ONLINE.
     * Trả về UserDTO (đã set status ONLINE) nếu thành công.
     */
    public UserDTO login(String email, String rawPassword) throws SQLException {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Mật khẩu không được để trống");
        }

        UserDTO user = userDAO.getUserByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            throw new SecurityException("Email hoặc mật khẩu không đúng");
        }

        if (!SecurityUtils.matches(rawPassword, user.getPasswordHash())) {
            throw new SecurityException("Email hoặc mật khẩu không đúng");
        }

        if ("BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new SecurityException("Tài khoản đã bị khoá");
        }

        userDAO.updateStatus(user.getId(), "ONLINE");
        user.setStatus("ONLINE");
        user.setPasswordHash(null); // không trả hash ra ngoài
        return user;
    }

    /**
     * Đăng ký: validate → check email tồn tại → hash password → gửi OTP → insert user.
     * OTP được sinh 6 số; nếu JavaMail chưa cấu hình thì MailUtils sẽ log ra console.
     */
    public UserDTO register(UserDTO user, String rawPassword) throws SQLException {
        validateRegisterInput(user, rawPassword);

        String email = user.getEmail().trim().toLowerCase();
        if (userDAO.getUserByEmail(email) != null) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        String otp = generateOTP();
        user.setEmail(email);
        user.setPasswordHash(SecurityUtils.hashPassword(rawPassword));
        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus("OFFLINE");
        }

        // Gửi OTP xác thực email trước khi tạo tài khoản
        MailUtils.sendOtp(email, otp);

        long newId = userDAO.insert(user);   
        if (newId <= 0) {
            throw new IllegalStateException("Đăng ký thất bại, không tạo được tài khoản");
        }

        user.setPasswordHash(null);
        return user;
    }

    private void validateRegisterInput(UserDTO user, String rawPassword) {
        if (user == null) {
            throw new IllegalArgumentException("Dữ liệu user không hợp lệ");
        }
        if (user.getEmail() == null || !EMAIL_PATTERN.matcher(user.getEmail().trim()).matches()) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
        }
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            throw new IllegalArgumentException("Họ tên không được để trống");
        }
    }

    /** Sinh OTP 6 chữ số. */
    private String generateOTP() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}