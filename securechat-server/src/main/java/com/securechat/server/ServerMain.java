package com.securechat.server;

import com.securechat.config.AppConfig;
import com.securechat.security.ServerIdentityKeyManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerMain {

    private static final int CORE_POOL_SIZE          = 10;
    private static final int MAX_POOL_SIZE           = 50;
    private static final int QUEUE_CAPACITY          = 100;
    private static final long KEEP_ALIVE_SECONDS     = 60L;
    private static final long SHUTDOWN_WAIT_SECONDS  = 10L;

    public static void main(String[] args) throws IOException {
        int port = AppConfig.port();
        ServerIdentityKeyManager.getInstance();
        ThreadPoolExecutor pool = buildPool();

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Server đang chạy ở port " + port + "...");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server, pool)));

            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    ClientHandler handler = new ClientHandler(client);
                    try {
                        pool.submit(handler);
                    } catch (RejectedExecutionException ree) {
                        System.err.println("[WARN] Server overload — từ chối "
                                + client.getRemoteSocketAddress());
                        closeQuietly(client);
                    }
                } catch (SocketException se) {
                    break;
                } catch (IOException ioe) {
                    System.err.println("[WARN] accept lỗi, tiếp tục: " + ioe.getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    private static ThreadPoolExecutor buildPool() {
        AtomicInteger seq = new AtomicInteger(0);
        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "client-handler-" + seq.incrementAndGet());
                    t.setDaemon(false);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static void shutdown(ServerSocket server, ThreadPoolExecutor pool) {
        System.out.println("[SHUTDOWN] Bắt đầu shutdown server...");
        try { server.close(); } catch (IOException ignored) {}

        ClientConnectionManager.closeAll();
        pool.shutdown();

        long deadline = System.currentTimeMillis() + (SHUTDOWN_WAIT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            try {
                if (pool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    System.out.println("[SHUTDOWN] ThreadPool terminated cleanly.");
                    System.out.println("[SHUTDOWN] ALL_CONNECTIONS="
                            + ClientConnectionManager.connectionCount()
                            + " ONLINE=" + ClientConnectionManager.onlineCount());
                    System.out.println("Server shutdown hoàn tất.");
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            ClientConnectionManager.closeAll();
        }

        System.err.println("[SHUTDOWN] Timeout — force shutdownNow()");
        pool.shutdownNow();
        System.out.println("[SHUTDOWN] ALL_CONNECTIONS="
                + ClientConnectionManager.connectionCount()
                + " ONLINE=" + ClientConnectionManager.onlineCount());
        System.out.println("Server shutdown hoàn tất.");
    }

    private static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) {}
    }
}