package com.securechat.server;

import com.securechat.config.AppConfig;

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

    private static final int CORE_POOL_SIZE     = 10;
    private static final int MAX_POOL_SIZE      = 50;
    private static final int QUEUE_CAPACITY     = 100;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final long SHUTDOWN_WAIT_SECONDS = 10L;

    public static void main(String[] args) throws IOException {
        int port = AppConfig.port();
        ThreadPoolExecutor pool = buildPool();

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Server đang chạy ở port " + port + "...");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server, pool)));

            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    try {
                        pool.submit(new ClientHandler(client));
                    } catch (RejectedExecutionException ree) {
                        // Hàng đợi đầy: từ chối client mới, KHÔNG chặn accept loop
                        System.err.println("[WARN] Server overload — từ chối "
                                + client.getRemoteSocketAddress());
                        closeQuietly(client);
                    }
                } catch (SocketException se) {
                    break; // server đã đóng — thoát vòng lặp accept
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
                new ThreadPoolExecutor.AbortPolicy()  // ném RejectedExecutionException — được catch ở trên
        );
    }

    private static void shutdown(ServerSocket server, ThreadPoolExecutor pool) {
        try { server.close(); } catch (IOException ignored) {}
        pool.shutdown();
        try {
            if (!pool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Server shutdown hoàn tất.");
    }

    private static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) {}
    }
}