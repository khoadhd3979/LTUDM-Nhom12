package com.securechat.server;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke test: một packet ERROR/BAD_JSON không được đóng socket.
 * Test này KHÔNG chạy handshake (cần key + crypto) — chỉ test handler lifecycle
 * trên trường hợp pre-handshake error.
 */
class RegisterFailureKeepsConnectionTest {

    @Test
    void badJsonResponseDoesNotCloseSocket() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            int port = ss.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try (Socket s = ss.accept()) {
                    new ClientHandler(s).run();
                } catch (Exception ignored) {}
            });
            serverThread.setDaemon(true);
            serverThread.start();

            try (Socket client = new Socket("127.0.0.1", port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

                // Gửi JSON hỏng → server trả BAD_JSON nhưng KHÔNG đóng socket
                out.println("this is not json");
                String resp = in.readLine();
                assertNotNull(resp);
                assertTrue(resp.contains("BAD_JSON"), "Expect BAD_JSON, got: " + resp);

                // Gửi tiếp PING → vẫn nhận PONG, chứng minh socket còn sống
                out.println("{\"type\":\"PING\",\"requestId\":\"r2\",\"payload\":{}}");
                String pong = in.readLine();
                assertNotNull(pong);
                assertTrue(pong.contains("PONG"), "Expect PONG, got: " + pong);
            }
        }
    }
}