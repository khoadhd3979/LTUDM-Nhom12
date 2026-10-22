package com.securechat.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class AESUtil {
    private AESUtil() {}

    public static final int GCM_TAG_BITS = 128;
    public static final int NONCE_LEN = 12;
    public static final int KEY_LEN = 32;

    private static final SecureRandom RNG = new SecureRandom();

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    /** Nonce derived deterministically from sequence: 4B zero + 8B BE(seq). */
    public static byte[] deriveNonce(long seq) {
        byte[] nonce = new byte[NONCE_LEN];
        nonce[4]  = (byte)(seq >>> 56);
        nonce[5]  = (byte)(seq >>> 48);
        nonce[6]  = (byte)(seq >>> 40);
        nonce[7]  = (byte)(seq >>> 32);
        nonce[8]  = (byte)(seq >>> 24);
        nonce[9]  = (byte)(seq >>> 16);
        nonce[10] = (byte)(seq >>>  8);
        nonce[11] = (byte)(seq);
        return nonce;
    }

    public static byte[] gcmEncrypt(SecretKey key, byte[] nonce, byte[] aad, byte[] plain)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        c.updateAAD(aad);
        return c.doFinal(plain);
    }

    public static byte[] gcmDecrypt(SecretKey key, byte[] nonce, byte[] aad, byte[] cipher)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        c.updateAAD(aad);
        return c.doFinal(cipher);
    }

    public static void wipe(byte[] arr) { if (arr != null) Arrays.fill(arr, (byte) 0); }
}