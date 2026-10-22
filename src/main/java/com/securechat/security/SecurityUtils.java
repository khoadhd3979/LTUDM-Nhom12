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
    public static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] r = new byte[total];
        int off = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, r, off, a.length); off += a.length; }
        return r;
    }

    /** Unambiguous concatenation: uint32(length) || bytes per field. */
    public static byte[] concatLenPrefixed(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += 4 + a.length;
        byte[] r = new byte[total];
        int off = 0;
        for (byte[] a : arrays) {
            r[off++] = (byte)(a.length >>> 24);
            r[off++] = (byte)(a.length >>> 16);
            r[off++] = (byte)(a.length >>> 8);
            r[off++] = (byte)(a.length);
            System.arraycopy(a, 0, r, off, a.length);
            off += a.length;
        }
        return r;
    }
}