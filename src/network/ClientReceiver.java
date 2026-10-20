package network;

import gui.DashboardFrame;
import main.ClientMain;
import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;

import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.io.BufferedReader;

public class ClientReceiver extends Thread {
    private BufferedReader in;
    private DashboardFrame view;
    private volatile boolean isRunning = true;

    public ClientReceiver(java.net.Socket socket, DashboardFrame view) {
        this.in = ClientSocketManager.getInputStream();
        this.view = view;
    }

    @Override
    public void run() {
        try {
            String line;
            while (isRunning && (line = in.readLine()) != null) {
                Packet packet = ProtocolSerializer.fromJson(line);
                handleIncomingPacket(packet);
            }
        } catch (Exception e) {
            if (isRunning) {
                SwingUtilities.invokeLater(() -> {
                    view.appendMessage("[Hệ thống]: Mất kết nối đến Server!");
                    JOptionPane.showMessageDialog(view, "Đã mất kết nối đến Server! Vui lòng kết nối lại.", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                    view.dispose();
                    new ClientMain().setVisible(true);
                });
            }
        }
    }

    private void handleIncomingPacket(Packet packet) {
        SwingUtilities.invokeLater(() -> {
            if ("CHAT".equals(packet.getType()) || "BROADCAST".equals(packet.getType())) {
                Object msg = packet.getPayload().get("msg");
                Object sender = packet.getPayload().get("sender");
                if (msg != null) {
                    String displaySender = (sender != null) ? sender.toString() : "Người lạ";
                    view.appendMessage("[" + displaySender + "]: " + msg.toString());
                }
            } else if ("ERROR".equals(packet.getType())) {
                view.appendMessage("[Hệ thống báo lỗi]: " + packet.getError());
            }
        });
    }

    public void stopReceiver() {
        isRunning = false;
    }
}