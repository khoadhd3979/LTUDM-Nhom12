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
}