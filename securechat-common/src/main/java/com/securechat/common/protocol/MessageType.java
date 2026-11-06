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

    // BỔ SUNG CÁC HẰNG SỐ PHASE 4 :
    public static final String FETCH_MY_GROUPS = "FETCH_MY_GROUPS";
    public static final String SEND_GROUP_MESSAGE = "SEND_GROUP_MESSAGE";
    public static final String SEND_DIRECT_MESSAGE = "SEND_DIRECT_MESSAGE";
    public static final String CREATE_GROUP = "CREATE_GROUP";
    public static final String ADD_GROUP_MEMBER = "ADD_GROUP_MEMBER";
    public static final String FETCH_DIRECT_HISTORY = "FETCH_DIRECT_HISTORY";
    public static final String FETCH_GROUP_HISTORY = "FETCH_GROUP_HISTORY";
    public static final String PUSH_DIRECT_MESSAGE = "PUSH_DIRECT_MESSAGE";
    public static final String PUSH_GROUP_MESSAGE = "PUSH_GROUP_MESSAGE";
}