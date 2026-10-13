package com.securechat.common.protocol;

public final class MessageType {
    private MessageType() {}

    public static final String PING  = "PING";
    public static final String PONG  = "PONG";
    public static final String OK    = "OK";
    public static final String ERROR = "ERROR";

    // Phase 2+: REGISTER, VERIFY_OTP, LOGIN, LOGOUT, SEND_MESSAGE, ...
}