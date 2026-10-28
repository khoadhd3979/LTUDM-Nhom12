package controller;

import gui.LoginFrame;
import gui.RegisterFrame;
import gui.DashboardFrame;
import network.ClientSocketManager;
import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;
import utils.ValidateUtils;

import javax.swing.JOptionPane;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginController {
    private LoginFrame view;

    public LoginController(LoginFrame view) {
        this.view = view;
        initController();
    }

    private void initController() {
        view.setRegisterButtonListener(e -> {
            RegisterFrame regView = new RegisterFrame();
            new RegisterController(regView);
            regView.setVisible(true);
            view.dispose();
        });

        view.setLoginButtonListener(e -> {
            char[] passChars = view.getPassword();
            String email = view.getEmail();

            if (email.isEmpty() || passChars.length == 0) {
                JOptionPane.showMessageDialog(view, "Vui lòng nhập đầy đủ Email và Mật khẩu!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!ValidateUtils.isValidEmail(email)) {
                JOptionPane.showMessageDialog(view, "Định dạng Email không hợp lệ (ví dụ chuẩn: user@gmail.com)!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!ValidateUtils.isValidPassword(passChars)) {
                JOptionPane.showMessageDialog(view, "Mật khẩu phải có ít nhất 6 ký tự!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (ClientSocketManager.connect()) {
                try {
                    String pass = new String(passChars).trim();
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("email", email);
                    payload.put("password", pass);

                    String currentRequestId = UUID.randomUUID().toString();

                    Packet packet = Packet.request(MessageType.LOGIN, currentRequestId, payload);
                    ClientSocketManager.sendPacket(ProtocolSerializer.toJson(packet));

                    String responseLine = ClientSocketManager.receivePacket();
                    if (responseLine != null) {
                        Packet response = ProtocolSerializer.fromJson(responseLine);

                        if (response.getRequestId() == null || !response.getRequestId().equals(currentRequestId)) {
                            return;
                        }

                        if (MessageType.OK.equals(response.getType()) || "OK".equals(response.getStatus())) {
                            Arrays.fill(passChars, '\0');
                            pass = null;

                            DashboardFrame dashboardView = new DashboardFrame();
                            new DashboardController(dashboardView);
                            dashboardView.setVisible(true);
                            view.dispose();
                        } else {
                            String errCode = response.getError();
                            if ("WRONG_CREDENTIALS".equals(errCode)) {
                                JOptionPane.showMessageDialog(view, "Sai Email hoặc Mật khẩu!", "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
                            } else if ("ALREADY_LOGGED_IN".equals(errCode)) {
                                JOptionPane.showMessageDialog(view, "Tài khoản này đang được đăng nhập ở một nơi khác!", "Lỗi đăng nhập", JOptionPane.WARNING_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(view, "Đăng nhập thất bại!\nLỗi: " + errCode, "Lỗi Đăng nhập", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                JOptionPane.showMessageDialog(view, "Không thể kết nối đến Server!", "Lỗi mạng", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}