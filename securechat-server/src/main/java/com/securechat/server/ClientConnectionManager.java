package com.securechat.server;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry cho TẤT CẢ connections đã accept (chưa/đã auth), cùng session ONLINE per-user.
 *
 * - ALL_CONNECTIONS: mọi socket đã accept — kể cả chưa handshake, chưa login.
 *                    Dùng để shutdown đóng hết socket và unblock blocking read.
 * - ONLINE:          userId → ClientHandler đã LOGIN thành công. Dùng để push
 *                    realtime message. Dọn dẹp khi LOGOUT hoặc disconnect.
 *
 * Không dùng HashMap static — sử dụng ConcurrentHashMap cho an toàn đa luồng.
 */
public final class ClientConnectionManager {

    private static final AtomicLong NEXT_ID = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, Connection> ALL_CONNECTIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ClientHandler> ONLINE = new ConcurrentHashMap<>();

    private ClientConnectionManager() {}

    /** Metadata của một TCP connection. Chỉ đọc, immutable (trừ userId nếu cần). */
    public static final class Connection {
        public final long id;
        public final ClientHandler handler;
        public final Socket socket;
        public final String remote;

        Connection(long id, ClientHandler handler, Socket socket) {
            this.id = id;
            this.handler = handler;
            this.socket = socket;
            this.remote = String.valueOf(socket.getRemoteSocketAddress());
        }
    }

    /**
     * Đăng ký connection mới SAU accept() và TRƯỚC pool.submit.
     * @return metadata chứa connId dùng để log và unregister.
     */
    public static Connection registerConnection(ClientHandler handler, Socket socket) {
        long id = NEXT_ID.incrementAndGet();
        Connection c = new Connection(id, handler, socket);
        ALL_CONNECTIONS.put(id, c);
        return c;
    }

    /**
     * Gỡ connection khỏi ALL_CONNECTIONS và (nếu có) khỏi ONLINE.
     * Gọi trong finally của ClientHandler.run().
     *
     * Lưu ý: dùng removeIf với identity comparison (==) để tránh xóa nhầm
     * session mới của cùng userId được đăng ký bởi connection khác.
     */
    public static void unregisterConnection(Connection c) {
        if (c == null) return;
        ALL_CONNECTIONS.remove(c.id);
        // Chỉ xóa nếu handler đang map đúng là handler của connection này.
        ONLINE.values().removeIf(h -> h == c.handler);
    }

    /**
     * Đăng ký user session — gọi khi LOGIN thành công.
     * @return true nếu chưa có session khác cho user này.
     */
    public static boolean register(long userId, ClientHandler handler) {
        return ONLINE.putIfAbsent(userId, handler) == null;
    }

    /**
     * Gỡ user session — gọi khi LOGOUT. Chỉ xóa nếu handler là session hiện tại.
     */
    public static void unregister(long userId, ClientHandler handler) {
        ONLINE.remove(userId, handler);
    }

    /** Tra cứu handler đang online của user (nếu có) — dùng để push message. */
    public static ClientHandler findByUserId(long userId) {
        return ONLINE.get(userId);
    }

    public static boolean isOnline(long userId) {
        return ONLINE.containsKey(userId);
    }

    public static int connectionCount() { return ALL_CONNECTIONS.size(); }
    public static int onlineCount()      { return ONLINE.size(); }

    /**
     * Đóng toàn bộ client socket đang active — dùng cho shutdown.
     * Việc đóng socket sẽ unblock mọi blocking readLine() trong ClientHandler,
     * cho phép các handler thoát khỏi vòng lặp đọc.
     */
    public static void closeAll() {
        for (Connection c : ALL_CONNECTIONS.values()) {
            try {
                c.socket.close();
            } catch (IOException ignored) {
                // socket có thể đã đóng — không quan trọng khi shutdown
            }
        }
    }
}