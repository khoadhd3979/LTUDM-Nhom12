package com.securechat.common.protocol;

public final class MessageType {
    private MessageType() {}

    public static final String PING  = "PING";
    public static final String PONG  = "PONG";
    public static final String OK    = "OK";
    public static final String ERROR = "ERROR";

    // Authentication
    public static final String REGISTER   = "REGISTER";
    public static final String VERIFY_OTP = "VERIFY_OTP";
    public static final String LOGIN      = "LOGIN";
    public static final String LOGOUT     = "LOGOUT";

    // Secure Transport
    public static final String CLIENT_HELLO      = "CLIENT_HELLO";
    public static final String SERVER_HELLO      = "SERVER_HELLO";
    public static final String KEY_EXCHANGE      = "KEY_EXCHANGE";
    public static final String HANDSHAKE_CONFIRM = "HANDSHAKE_CONFIRM";
    public static final String SECURE_MESSAGE    = "SECURE_MESSAGE";

    // Chat & Groups (client → server)
    public static final String SEND_DIRECT_MESSAGE  = "SEND_DIRECT_MESSAGE";
    public static final String SEND_GROUP_MESSAGE   = "SEND_GROUP_MESSAGE";
    public static final String FETCH_DIRECT_HISTORY = "FETCH_DIRECT_HISTORY";
    public static final String FETCH_GROUP_HISTORY  = "FETCH_GROUP_HISTORY";
    public static final String CREATE_GROUP         = "CREATE_GROUP";
    public static final String ADD_GROUP_MEMBER     = "ADD_GROUP_MEMBER";
    public static final String FETCH_MY_GROUPS      = "FETCH_MY_GROUPS";

    // Push (server → client, inside SECURE_MESSAGE)
    public static final String PUSH_DIRECT_MESSAGE = "PUSH_DIRECT_MESSAGE";
    public static final String PUSH_GROUP_MESSAGE  = "PUSH_GROUP_MESSAGE";

    // User lookup
    public static final String SEARCH_USER = "SEARCH_USER";

    // ============================================================
    // Core Backend additions (NO PUSH_SYSTEM_BROADCAST, NO SYSTEM_FORCE_LOGOUT)
    // ============================================================
    public static final String UPDATE_PROFILE       = "UPDATE_PROFILE";
    public static final String REMOVE_GROUP_MEMBER  = "REMOVE_GROUP_MEMBER";
    public static final String SET_GROUP_ROLE       = "SET_GROUP_ROLE";
    public static final String MARK_READ            = "MARK_READ";
    public static final String MESSAGE_STATUS       = "MESSAGE_STATUS";       // C↔S, legacy conversation-level + push S→C {status:"SEEN"} for direct
    public static final String SYSTEM_BROADCAST     = "SYSTEM_BROADCAST";     // C↔S, E-1 canonical
    public static final String LOCK_USER            = "LOCK_USER";
    public static final String UNLOCK_USER          = "UNLOCK_USER";
    public static final String USER_STATS           = "USER_STATS";

    // ============================================================
    // File transfer (M4-B) — stateless per-chunk relay
    // Client C→S: {fileId, fileName, chunkIndex, totalChunks, data,
    //              receiver | groupName}
    // Server S→C: same 5 fields + {senderId, senderEmail}
    //              (+ {groupId, groupName} for group)
    // ============================================================
    public static final String FILE_CHUNK = "FILE_CHUNK";
}
