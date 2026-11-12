package com.securechat.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2-only password + OTP hashing (M2-B).
 *
 * - Password: PBKDF2WithHmacSHA256, 600,000 iterations, salt 16B, DK 256 bits.
 * - OTP:      PBKDF2WithHmacSHA256, 100,000 iterations, salt 16B, DK 256 bits.
 * - Format:   pbkdf2_sha256$<iter>$<salt_b64>$<dk_b64>
 * - Verify chỉ chấp nhận iterations chính xác của từng API (600K / 100K).
 * - So sánh constant-time bằng MessageDigest.isEqual.
 * - Không có BCrypt fallback.
 */
public final class PasswordHasher {

    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2_sha256";
    private static final int PASSWORD_ITER = 600_000;
    private static final int OTP_ITER = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int DK_BITS = 256;
    private static final int DK_BYTES = DK_BITS / 8;

    private static final SecureRandom RNG = new SecureRandom();

    private PasswordHasher() {}

    // ============================================================
    // Password (public API kept: hash/verify)
    // ============================================================

    public static String hash(String rawPassword) {
        if (rawPassword == null) throw new IllegalArgumentException("password is null");
        return doHash(rawPassword, PASSWORD_ITER);
    }

    public static boolean verify(String rawPassword, String storedHash) {
        return doVerify(rawPassword, storedHash, PASSWORD_ITER);
    }

    // ============================================================
    // OTP (new API)
    // ============================================================

    public static String hashOtp(String rawOtp) {
        if (rawOtp == null) throw new IllegalArgumentException("otp is null");
        return doHash(rawOtp, OTP_ITER);
    }

    public static boolean verifyOtp(String rawOtp, String storedOtpHash) {
        return doVerify(rawOtp, storedOtpHash, OTP_ITER);
    }

    // ============================================================
    // Internals
    // ============================================================

    private static String doHash(String raw, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, DK_BITS);
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance(ALGO);
            byte[] dk = f.generateSecret(spec).getEncoded();
            return PREFIX + "$" + iterations + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(dk);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 hash failed: " + e.getMessage(), e);
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean doVerify(String raw, String stored, int expectedIter) {
        if (raw == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4) return false;
        if (!PREFIX.equals(parts[0])) return false;

        int iter;
        try {
            iter = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (iter != expectedIter) return false;   // reject before running PBKDF2

        byte[] salt, storedDk;
        try {
            salt = Base64.getDecoder().decode(parts[2]);
            storedDk = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (salt.length != SALT_BYTES || storedDk.length != DK_BYTES) return false;

        PBEKeySpec spec = null;
        try {
            spec = new PBEKeySpec(raw.toCharArray(), salt, iter, DK_BITS);
            SecretKeyFactory f = SecretKeyFactory.getInstance(ALGO);
            byte[] derived = f.generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(derived, storedDk);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return false;
        } finally {
            if (spec != null) spec.clearPassword();
        }
    }
}