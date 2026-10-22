package com.securechat.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utilities that do NOT overlap with PasswordHasher.
 * Password hashing/verify lives in PasswordHasher (single canonical API).
 */
public final class SecurityUtils {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int OTP_BOUND = 1_000_000;

    private SecurityUtils() {}

    /** 6-digit zero-padded OTP. */
    public static String generateOtp() {
        return String.format("%06d", RNG.nextInt(OTP_BOUND));
    }

    /** Random base64 — reserved for Phase 3 (IV/nonce). */
    public static String randomBase64(int byteLen) {
        byte[] b = new byte[byteLen];
        RNG.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }
}