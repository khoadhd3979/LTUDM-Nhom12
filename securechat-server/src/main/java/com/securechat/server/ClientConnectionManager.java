package com.securechat.server;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory online-session registry. NOT persisted to DB.
 * users.status remains PENDING/ACTIVE/LOCKED — online-ness is ephemeral.
 */
public final class ClientConnectionManager {

    private static final ConcurrentHashMap<Long, ClientHandler> ONLINE = new ConcurrentHashMap<>();

    private ClientConnectionManager() {}

    /** @return true iff no existing session for userId. */
    public static boolean register(long userId, ClientHandler handler) {
        return ONLINE.putIfAbsent(userId, handler) == null;
    }

    /** Only removes if handler is the current session (prevents stale cleanup). */
    public static void unregister(long userId, ClientHandler handler) {
        ONLINE.remove(userId, handler);
    }

    public static int onlineCount() { return ONLINE.size(); }
}