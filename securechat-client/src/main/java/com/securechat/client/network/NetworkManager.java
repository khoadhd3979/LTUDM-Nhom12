package com.securechat.client.network;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * NDJSON qua TCP (Protocol v0.2).
 * - Đọc bằng BufferedReader.readLine() ở luồng nền (không bao giờ chặn EDT).
 * - Ghi ở một luồng riêng, mỗi gói là 1 dòng kết thúc bằng '\n' (cố định, không phụ thuộc OS).
 * - Mọi callback tới listener đều chạy trên EDT.
 */
public class NetworkManager {

    public interface MessageListener {
        void onMessageReceived(String json);
        void onConnectionLost();
    }

    private static final int CONNECT_TIMEOUT_MS = 5000;

    // Tất cả các field dưới đây chỉ được đụng tới trên EDT.
    private MessageListener listener;
    private Socket socket;
    private BufferedWriter out;
    private ExecutorService writer;
    private boolean closing = true;
    private int generation = 0;   // bỏ qua sự kiện của kết nối cũ

    public void setListener(MessageListener l) { this.listener = l; }

    public void connect(String host, int port, Runnable onConnected, Consumer<IOException> onError) {
        doDisconnect();
        final int gen = ++generation;
        Thread t = new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                s.setTcpNoDelay(true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) { closeQuietly(s); return; }
                    socket = s; out = w; closing = false;
                    writer = Executors.newSingleThreadExecutor(r -> {
                        Thread th = new Thread(r, "net-writer"); th.setDaemon(true); return th;
                    });
                    startReader(gen, s, in);
                    onConnected.run();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> { if (gen == generation) onError.accept(e); });
            }
        }, "net-connect");
        t.setDaemon(true);
        t.start();
    }

    private void startReader(int gen, Socket s, BufferedReader in) {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String l = line;
                    SwingUtilities.invokeLater(() -> {
                        if (gen == generation && listener != null) listener.onMessageReceived(l);
                    });
                }
            } catch (IOException ignored) { /* socket đóng */ }
            SwingUtilities.invokeLater(() -> notifyLost(gen));
        }, "net-reader");
        t.setDaemon(true);
        t.start();
    }

    private void notifyLost(int gen) {
        if (gen != generation || closing) return;   // do chính client đóng thì không báo
        doDisconnect();
        if (listener != null) listener.onConnectionLost();
    }

    /** Gửi 1 gói (1 dòng JSON). Không chặn EDT. */
    public void sendMessage(String json) {
        if (writer == null || closing) throw new IllegalStateException("Chưa kết nối tới Server");
        if (json.indexOf('\n') >= 0 || json.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Gói NDJSON không được chứa ký tự xuống dòng");
        final BufferedWriter w = out;
        final int gen = generation;
        writer.execute(() -> {
            try {
                w.write(json);
                w.write('\n');
                w.flush();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> notifyLost(gen));
            }
        });
    }

    /** Đóng kết nối sau khi các gói đang chờ gửi đã được ghi xong. */
    public void disconnect() { doDisconnect(); }

    /** Như disconnect() nhưng đợi tối đa 1 giây để gói LOGOUT kịp ra khỏi máy (dùng khi thoát app). */
    public void disconnectAndWait() {
        ExecutorService ex = doDisconnect();
        if (ex != null) {
            try { ex.awaitTermination(1, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
    }

    private ExecutorService doDisconnect() {
        closing = true;
        generation++;
        ExecutorService ex = writer;
        final Socket s = socket;
        if (ex != null) {
            ex.execute(() -> closeQuietly(s));   // chạy sau các lệnh ghi đã xếp hàng
            ex.shutdown();
        }
        writer = null; socket = null; out = null;
        return ex;
    }

    private static void closeQuietly(Socket s) {
        try { if (s != null) s.close(); } catch (IOException ignored) {}
    }
}