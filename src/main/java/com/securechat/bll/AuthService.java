package com.securechat.bll;

import com.securechat.dao.DBConnection;
import com.securechat.dao.EmailOtpDAO;
import com.securechat.dao.EmailOtpDAO.OtpRecord;
import com.securechat.dao.UserDAO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import com.securechat.security.PasswordHasher;
import com.securechat.security.SecurityUtils;
import com.securechat.utils.MailUtils;
import com.securechat.validation.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

public class AuthService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int OTP_MAX_ATTEMPTS = 5;

    private final UserDAO userDAO = new UserDAO();
    private final EmailOtpDAO otpDAO = new EmailOtpDAO();

    // ============================================================
    // REGISTER
    // ============================================================
    public UserDTO register(String rawEmail, String password, String rawFullName,
                            String rawGender, String rawDob)
            throws SQLException {

        String email = InputValidator.validateEmail(rawEmail);
        InputValidator.validatePassword(password);
        String fullName = InputValidator.validateFullName(rawFullName);
        String gender = InputValidator.validateGender(rawGender);
        var dob = InputValidator.validateDateOfBirth(rawDob);

        if (userDAO.getUserByEmail(email).isPresent()) {
            throw new BusinessException("EMAIL_EXISTS", "Email đã được sử dụng");
        }

        String passwordHash = PasswordHasher.hash(password);
        String otpPlain = SecurityUtils.generateOtp();
        String otpHash = PasswordHasher.hash(otpPlain);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(OTP_TTL_MINUTES);

        UserDTO user = new UserDTO();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName);
        user.setGender(gender);
        user.setDateOfBirth(dob);
        user.setStatus("PENDING");

        long userId;
        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                userId = userDAO.insert(c, user);
                otpDAO.insert(c, userId, otpHash, "REGISTER", expiresAt);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }

        try {
            MailUtils.sendOtp(email, otpPlain);
        } catch (RuntimeException mailEx) {
            // Compensate: remove user + otps so a retry is possible.
            try {
                otpDAO.deleteByUser(userId);
                userDAO.deleteById(userId);
            } catch (SQLException cleanupEx) {
                System.err.println("[CRITICAL] Cleanup failed after mail error for userId="
                        + userId + ": " + cleanupEx.getMessage());
            }
            throw new BusinessException("EMAIL_SEND_FAILED",
                    "Không gửi được email OTP, vui lòng thử lại");
        }

        user.setPasswordHash(null); // do not leak
        return user;
    }

    // ============================================================
    // VERIFY OTP
    // ============================================================
    public UserDTO verifyOtp(String rawEmail, String otpPlain) throws SQLException {

        String email = InputValidator.validateEmail(rawEmail);
        InputValidator.validateOtp(otpPlain);

        UserDTO user = userDAO.getUserByEmail(email)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy tài khoản"));

        if ("ACTIVE".equals(user.getStatus()))
            throw new BusinessException("ACCOUNT_NOT_PENDING", "Tài khoản đã được kích hoạt");
        if (!"PENDING".equals(user.getStatus()))
            throw new BusinessException("ACCOUNT_NOT_PENDING", "Tài khoản không ở trạng thái chờ xác thực");

        Optional<OtpRecord> opt = otpDAO.findLatestActive(user.getId(), "REGISTER");
        OtpRecord otp = opt.orElseThrow(() ->
                new BusinessException("OTP_NOT_FOUND", "Không có OTP còn hiệu lực"));

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        if (otp.expiresAt().isBefore(nowUtc))
            throw new BusinessException("OTP_EXPIRED", "OTP đã hết hạn");
        if (otp.attemptCount() >= OTP_MAX_ATTEMPTS)
            throw new BusinessException("OTP_ATTEMPTS_EXCEEDED", "Vượt quá số lần thử OTP");

        if (!PasswordHasher.verify(otpPlain, otp.otpHash())) {
            int newCount = otpDAO.incrementAttemptIfBelowLimit(otp.id(), OTP_MAX_ATTEMPTS);
            if (newCount < 0)
                throw new BusinessException("OTP_ATTEMPTS_EXCEEDED", "Vượt quá số lần thử OTP");
            throw new BusinessException("OTP_INVALID", "OTP không đúng");
        }

        // Success — atomically mark consumed + activate user.
        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                if (!otpDAO.markConsumed(c, otp.id())) {
                    c.rollback();
                    throw new BusinessException("OTP_ALREADY_USED", "OTP đã được sử dụng");
                }
                if (!userDAO.updateStatus(c, user.getId(), "ACTIVE")) {
                    c.rollback();
                    throw new BusinessException("DB_ERROR", "Cập nhật trạng thái thất bại");
                }
                c.commit();
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }

        user.setStatus("ACTIVE");
        user.setPasswordHash(null);
        return user;
    }

    // ============================================================
    // LOGIN
    // ============================================================
    public UserDTO login(String rawEmail, String password) throws SQLException {

        String email = InputValidator.validateEmail(rawEmail);
        if (password == null || password.isEmpty())
            throw new BusinessException("AUTH_FAILED", "Email hoặc mật khẩu không đúng");

        Optional<UserDTO> opt = userDAO.getUserByEmail(email);
        if (opt.isEmpty())
            throw new BusinessException("AUTH_FAILED", "Email hoặc mật khẩu không đúng");

        UserDTO user = opt.get();
        if (!PasswordHasher.verify(password, user.getPasswordHash()))
            throw new BusinessException("AUTH_FAILED", "Email hoặc mật khẩu không đúng");

        String status = user.getStatus();
        if ("PENDING".equals(status))
            throw new BusinessException("ACCOUNT_PENDING", "Tài khoản chưa xác thực email");
        if ("LOCKED".equals(status))
            throw new BusinessException("ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
        if (!"ACTIVE".equals(status))
            throw new BusinessException("AUTH_FAILED", "Email hoặc mật khẩu không đúng");

        user.setPasswordHash(null);   // never leave handler with hash
        return user;
    }
}