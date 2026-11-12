package com.securechat.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    // ============================================================
    // Password API
    // ============================================================

    @Test
    void hashPasswordProducesValidFormat() {
        String hash = PasswordHasher.hash("CorrectHorseBattery");
        assertNotNull(hash);
        String[] parts = hash.split("\\$");
        assertEquals(4, parts.length);
        assertEquals("pbkdf2_sha256", parts[0]);
        assertEquals("600000", parts[1]);
        assertTrue(Base64.getDecoder().decode(parts[2]).length >= 16);
        assertEquals(32, Base64.getDecoder().decode(parts[3]).length);
    }

    @Test
    void hashPasswordUsesUniqueSalt() {
        String h1 = PasswordHasher.hash("SamePassword");
        String h2 = PasswordHasher.hash("SamePassword");
        assertNotEquals(h1, h2, "Salt must be random per hash");

        String[] p1 = h1.split("\\$");
        String[] p2 = h2.split("\\$");
        assertNotEquals(p1[2], p2[2], "Salt base64 must differ");
    }

    @Test
    void verifyPasswordSuccess() {
        String hash = PasswordHasher.hash("CorrectHorseBattery");
        assertTrue(PasswordHasher.verify("CorrectHorseBattery", hash));
    }

    @Test
    void verifyPasswordWrongFails() {
        String hash = PasswordHasher.hash("CorrectHorseBattery");
        assertFalse(PasswordHasher.verify("wrongPassword", hash));
    }

    @Test
    void verifyPasswordNullSafe() {
        assertFalse(PasswordHasher.verify(null, "any"));
        assertFalse(PasswordHasher.verify("any", null));
    }

    @Test
    void verifyPasswordRejectsMalformedHash() {
        assertFalse(PasswordHasher.verify("any", ""));
        assertFalse(PasswordHasher.verify("any", "notahash"));
        assertFalse(PasswordHasher.verify("any", "pbkdf2_sha256$600000$onlythree"));
        assertFalse(PasswordHasher.verify("any", "pbkdf2_sha256$600000$!!!$???"));
    }

    @Test
    void verifyPasswordRejectsWrongIterations() {
        // OTP hash (100K) must NOT verify as password
        String otpHash = PasswordHasher.hashOtp("123456");
        assertFalse(PasswordHasher.verify("123456", otpHash));
    }

    @Test
    void verifyPasswordRejectsLowerIterationsBeforeRunningPbkdf2() {
        // Craft a hash with 1000 iterations — should be rejected by iteration check
        String crafted = "pbkdf2_sha256$1000$AAAA$BBBB";
        long start = System.currentTimeMillis();
        boolean result = PasswordHasher.verify("any", crafted);
        long elapsed = System.currentTimeMillis() - start;
        assertFalse(result);
        assertTrue(elapsed < 50, "Rejection should be fast (skip PBKDF2), took " + elapsed + "ms");
    }

    // ============================================================
    // OTP API
    // ============================================================

    @Test
    void hashOtpProducesValidFormat() {
        String hash = PasswordHasher.hashOtp("123456");
        assertNotNull(hash);
        String[] parts = hash.split("\\$");
        assertEquals(4, parts.length);
        assertEquals("pbkdf2_sha256", parts[0]);
        assertEquals("100000", parts[1]);
        assertEquals(32, Base64.getDecoder().decode(parts[3]).length);
    }

    @Test
    void hashOtpUsesUniqueSalt() {
        String h1 = PasswordHasher.hashOtp("123456");
        String h2 = PasswordHasher.hashOtp("123456");
        assertNotEquals(h1, h2);
    }

    @Test
    void verifyOtpSuccess() {
        String hash = PasswordHasher.hashOtp("654321");
        assertTrue(PasswordHasher.verifyOtp("654321", hash));
    }

    @Test
    void verifyOtpWrongFails() {
        String hash = PasswordHasher.hashOtp("654321");
        assertFalse(PasswordHasher.verifyOtp("000000", hash));
    }

    @Test
    void verifyOtpRejectsWrongIterations() {
        // Password hash (600K) must NOT verify as OTP
        String passwordHash = PasswordHasher.hash("123456");
        assertFalse(PasswordHasher.verifyOtp("123456", passwordHash));
    }

    @Test
    void verifyOtpRejectsMalformedHash() {
        assertFalse(PasswordHasher.verifyOtp(null, "any"));
        assertFalse(PasswordHasher.verifyOtp("any", null));
        assertFalse(PasswordHasher.verifyOtp("any", "garbage"));
        assertFalse(PasswordHasher.verifyOtp("any", "pbkdf2_sha256$100000$!!!$???"));
    }

    @Test
    void verifyOtpRejectsHighIterationsBeforeRunningPbkdf2() {
        // Craft hash with 600K iterations — OTP API must reject without running PBKDF2
        String crafted = "pbkdf2_sha256$600000$AAAA$BBBB";
        long start = System.currentTimeMillis();
        boolean result = PasswordHasher.verifyOtp("any", crafted);
        long elapsed = System.currentTimeMillis() - start;
        assertFalse(result);
        assertTrue(elapsed < 50, "Rejection should be fast (skip PBKDF2), took " + elapsed + "ms");
    }

    @Test
    void otpAndPasswordUseDifferentIterations() {
        String pwdHash = PasswordHasher.hash("same");
        String otpHash = PasswordHasher.hashOtp("same");
        assertNotEquals(pwdHash.split("\\$")[1], otpHash.split("\\$")[1]);
        assertEquals("600000", pwdHash.split("\\$")[1]);
        assertEquals("100000", otpHash.split("\\$")[1]);
    }
}