package com.securechat.server;

import com.securechat.config.AppConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        int port = AppConfig.port();
        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Server dang chay port " + port + "...");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { server.close(); } catch (IOException ignored) {}
                pool.shutdownNow();
                System.out.println("Server shutdown hoàn tat.");
            }));

            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    pool.submit(new ClientHandler(client));
                } catch (SocketException se) {
                    break; // server bị đóng
                } catch (IOException ioe) {
                    System.err.println("[WARN] accept lỗi, tiếp tục: " + ioe.getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }
    }
}