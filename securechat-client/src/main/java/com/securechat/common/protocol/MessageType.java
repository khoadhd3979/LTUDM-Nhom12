package com.securechat.common.protocol;

public final class MessageType {
    private MessageType() {}

    public static final String PING  = "PING";
    public static final String PONG  = "PONG";
    public static final String OK    = "OK";
    public static final String ERROR = "ERROR";

    // Phase 2+: Authentication
    public static final String REGISTER   = "REGISTER";
    public static final String VERIFY_OTP = "VERIFY_OTP";
    public static final String LOGIN      = "LOGIN";
    public static final String LOGOUT     = "LOGOUT";

    // Phase 3 — Secure Transport
    public static final String CLIENT_HELLO      = "CLIENT_HELLO";
    public static final String SERVER_HELLO      = "SERVER_HELLO";
    public static final String KEY_EXCHANGE      = "KEY_EXCHANGE";
    public static final String HANDSHAKE_CONFIRM = "HANDSHAKE_CONFIRM";
    public static final String SECURE_MESSAGE    = "SECURE_MESSAGE";
}