package com.securechat.server;

import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private static final int MAX_FRAME_BYTES = 1_000_000; // 1 MB

    private final Socket socket;

    public ClientHandler(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        System.out.println("[CONN] " + remote);
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                if (line.length() > MAX_FRAME_BYTES) {
                    out.println(ProtocolSerializer.toJson(
                            Packet.error(null, "FRAME_TOO_LARGE")));
                    continue;
                }
                handleLine(line, out);
            }
        } catch (IOException e) {
            // client ngắt kết nối — không log stack trace để tránh nhiễu
        } finally {
            System.out.println("[DISC] " + remote);
        }
    }

    private void handleLine(String line, PrintWriter out) {
        Packet req;
        try {
            req = ProtocolSerializer.fromJson(line);
        } catch (Exception ex) {
            try { out.println(ProtocolSerializer.toJson(Packet.error(null, "BAD_JSON"))); }
            catch (Exception ignored) {}
            return;
        }
        Packet resp = dispatch(req);
        if (resp == null) return;
        try { out.println(ProtocolSerializer.toJson(resp)); }
        catch (Exception ignored) {}
    }

    private Packet dispatch(Packet req) {
        if (req == null || req.getType() == null)
            return Packet.error(null, "MISSING_TYPE");

        switch (req.getType()) {
            case MessageType.PING: {
                Map<String, Object> p = new HashMap<>();
                p.put("ts", System.currentTimeMillis());
                return Packet.ok(MessageType.PONG, req.getRequestId(), p);
            }
            default:
                return Packet.error(req.getRequestId(), "UNKNOWN_TYPE");
        }
    }
}