package com.securechat.security;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.Base64;

public final class SecurityUtils {
    private static final int BCRYPT_COST = 12;
    private static final SecureRandom RNG = new SecureRandom();
    private static final int OTP_BOUND = 1_000_000;

    private SecurityUtils() {}

    /** Hash mật khẩu bằng BCrypt (cost 12). */
    public static String hashPassword(String rawPassword) {
        if (rawPassword == null) throw new IllegalArgumentException("password is null");
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    /** So khớp mật khẩu với hash lưu DB. Trả false nếu hash hỏng. */
    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) return false;
        try { return BCrypt.checkpw(rawPassword, storedHash); }
        catch (IllegalArgumentException e) { return false; }
    }

    /** OTP 6 chữ số, giữ nguyên hành vi cũ. */
    public static String generateOtp() {
        int v = RNG.nextInt(OTP_BOUND);
        return String.format("%06d", v);
    }

    /** Random base64 — dùng cho IV/nonce ở Phase 3. */
    public static String randomBase64(int byteLen) {
        byte[] b = new byte[byteLen];
        RNG.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    // =============================================================
    // RSA/AES skeleton — GIỮ NGUYÊN KHUNG, triển khai ở Phase 3
    // =============================================================
    // public static KeyPair generateRsaKeyPair() { ... }
    // public static byte[] encryptAesGcm(SecretKey key, byte[] iv, byte[] plain) { ... }
    // public static byte[] decryptAesGcm(SecretKey key, byte[] iv, byte[] cipher) { ... }
    // public static byte[] wrapAesKeyWithRsa(PublicKey pub, SecretKey aes) { ... }
    // public static SecretKey unwrapAesKeyWithRsa(PrivateKey priv, byte[] wrapped) { ... }
}