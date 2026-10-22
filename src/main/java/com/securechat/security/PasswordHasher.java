package com.securechat.security;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordHasher {
    private static final int COST = 12;

    private PasswordHasher() {}

    public static String hash(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt(COST));
    }
    public static boolean verify(String raw, String hash) {
        if (raw == null || hash == null) return false;
        try { return BCrypt.checkpw(raw, hash); }
        catch (IllegalArgumentException e) { return false; }
    }
}