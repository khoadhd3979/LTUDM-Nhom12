package com.securechat.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/** RFC 5869 HKDF-SHA256. */
public final class HKDFUtil {
    private static final String HMAC = "HmacSHA256";
    private HKDFUtil() {}

    public static byte[] extract(byte[] salt, byte[] ikm) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC);
        mac.init(new SecretKeySpec(
                (salt == null || salt.length == 0) ? new byte[32] : salt, HMAC));
        return mac.doFinal(ikm);
    }

    public static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC);
        mac.init(new SecretKeySpec(prk, HMAC));
        byte[] result = new byte[length];
        byte[] prev = new byte[0];
        int offset = 0;
        for (int i = 1; offset < length; i++) {
            mac.reset();
            mac.update(prev);
            mac.update(info);
            mac.update((byte) i);
            prev = mac.doFinal();
            int copyLen = Math.min(prev.length, length - offset);
            System.arraycopy(prev, 0, result, offset, copyLen);
            offset += copyLen;
        }
        return result;
    }

    public static byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length)
            throws GeneralSecurityException {
        return expand(extract(salt, ikm), info, length);
    }
}