package com.securechat.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoTest {

    @Test
    public void testAesGcmEncryptDecrypt() throws Exception {
        byte[] keyBytes = AESUtil.randomBytes(32);
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        byte[] nonce = AESUtil.deriveNonce(1);
        byte[] aad = "test-aad".getBytes(StandardCharsets.UTF_8);
        byte[] plain = "Hello, SecureChat!".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = AESUtil.gcmEncrypt(key, nonce, aad, plain);
        assertNotNull(cipher);

        byte[] decrypted = AESUtil.gcmDecrypt(key, nonce, aad, cipher);
        assertArrayEquals(plain, decrypted);
    }

    @Test
    public void testHkdfDerivation() throws Exception {
        byte[] ikm = AESUtil.randomBytes(32);
        byte[] salt = AESUtil.randomBytes(32);
        byte[] info = "test-info".getBytes(StandardCharsets.UTF_8);

        byte[] okm = HKDFUtil.derive(ikm, salt, info, 64);
        assertNotNull(okm);
        assertEquals(64, okm.length);
    }

    @Test
    public void testRsaOaepAndPss() throws Exception {
        KeyPair kp = RSAUtil.generateEphemeralKeyPair();
        byte[] secret = AESUtil.randomBytes(32);

        byte[] enc = RSAUtil.oaepEncrypt(kp.getPublic(), secret);
        byte[] dec = RSAUtil.oaepDecrypt(kp.getPrivate(), enc);
        assertArrayEquals(secret, dec);

        byte[] tbs = "to-be-signed-data".getBytes(StandardCharsets.UTF_8);
        byte[] sig = RSAUtil.pssSign(kp.getPrivate(), tbs);
        assertTrue(RSAUtil.pssVerify(kp.getPublic(), tbs, sig));
    }
}

