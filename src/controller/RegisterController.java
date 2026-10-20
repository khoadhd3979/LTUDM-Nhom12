package controller;

import gui.RegisterFrame;
import gui.LoginFrame;
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

public class RegisterController {
    private RegisterFrame view;

    public RegisterController(RegisterFrame view) {
        this.view = view;
        initController();
    }

    private void initController() {
        view.setBackListener(e -> {
            LoginFrame loginView = new LoginFrame();
            new LoginController(loginView);
            loginView.setVisible(true);
            view.dispose();
        });

        view.setRegisterListener(e -> {
            String email = view.getEmail();
            String fullName = view.getFullName();
            String dob = view.getDateOfBirth();
            String gender = view.getGender();
            char[] passChars = view.getPassword();

            if (email.isEmpty() || fullName.isEmpty() || dob.isEmpty() || passChars.length == 0) {
                JOptionPane.showMessageDialog(view, "Vui lòng nhập đầy đủ thông tin!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!ValidateUtils.isValidEmail(email)) {
                JOptionPane.showMessageDialog(view, "Định dạng Email không hợp lệ!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!ValidateUtils.isValidPassword(passChars)) {
                JOptionPane.showMessageDialog(view, "Mật khẩu phải có ít nhất 6 ký tự!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!ValidateUtils.isValidDate(dob)) {
                JOptionPane.showMessageDialog(view, "Ngày sinh không hợp lệ (Chuẩn: YYYY-MM-DD)!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (ClientSocketManager.connect()) {
                try {
                    String pass = new String(passChars).trim();
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("email", email);
                    payload.put("password", pass);
                    payload.put("fullName", fullName);
                    payload.put("dateOfBirth", dob);
                    payload.put("gender", gender);

                    String currentRequestId = UUID.randomUUID().toString();
                    Packet packet = Packet.request(MessageType.REGISTER, currentRequestId, payload);
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

                            JOptionPane.showMessageDialog(view, "Đăng ký thành công! Hãy đợi OTP xác thực.", "Thành công", JOptionPane.INFORMATION_MESSAGE);

                            LoginFrame loginView = new LoginFrame();
                            new LoginController(loginView);
                            loginView.setVisible(true);
                            view.dispose();
                        } else {
                            JOptionPane.showMessageDialog(view, "Đăng ký thất bại!\nLỗi: " + response.getError(), "Lỗi", JOptionPane.ERROR_MESSAGE);
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