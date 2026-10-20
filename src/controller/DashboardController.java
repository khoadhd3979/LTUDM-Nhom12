package controller;

import com.securechat.common.protocol.MessageType;
import gui.DashboardFrame;
import network.ClientSocketManager;
import network.ClientReceiver; // Đã thêm import luồng nhận tin nhắn
import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DashboardController {
    private DashboardFrame view;
    private ClientReceiver receiver;

    public DashboardController(DashboardFrame view) {
        this.view = view;
        initController();

        receiver = new ClientReceiver(ClientSocketManager.getSocket(), view);
        receiver.start();
    }

    private void initController() {
        view.addSendListener(e -> {
            String msg = view.getMessageInput();
            if (msg != null && !msg.trim().isEmpty()) {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("msg", msg);

                    Packet packet = Packet.request(MessageType.SEND_MESSAGE, UUID.randomUUID().toString(), payload);
                    String jsonStr = ProtocolSerializer.toJson(packet);

                    ClientSocketManager.sendPacket(jsonStr);

                    view.appendMessage("[Tôi]: " + msg);
                    view.clearMessageInput();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        view.setLogoutListener(e -> {
            try {
                Packet logoutPacket = Packet.request("LOGOUT", UUID.randomUUID().toString(), new HashMap<>());
                ClientSocketManager.sendPacket(ProtocolSerializer.toJson(logoutPacket));

                if (receiver != null) {
                    receiver.stopReceiver();
                }

                gui.LoginFrame loginView = new gui.LoginFrame();
                new LoginController(loginView);
                loginView.setVisible(true);
                view.dispose();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

}