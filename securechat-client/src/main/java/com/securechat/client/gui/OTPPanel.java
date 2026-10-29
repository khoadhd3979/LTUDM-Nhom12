package com.securechat.client.gui;

import com.securechat.client.controller.ClientController;
import com.securechat.client.validation.InputValidator;

import javax.swing.*;
import java.awt.*;

public class OTPPanel extends JPanel {
    private final JLabel lblInfo = new JLabel("", SwingConstants.CENTER);
    private final JTextField txtOTP = new JTextField(10);

    public OTPPanel(ClientController controller) {
        setLayout(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(4, 1, 10, 10));

        txtOTP.setHorizontalAlignment(JTextField.CENTER);
        txtOTP.setFont(new Font("Arial", Font.BOLD, 16));
        setEmail("");

        JButton btnVerify = new JButton("Xác thực OTP");
        Runnable submit = () -> {
            String otp = txtOTP.getText().trim();
            String err = InputValidator.otp(otp);
            if (err != null) {
                JOptionPane.showMessageDialog(this, err, "Dữ liệu chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
                return;
            }
            controller.verifyOtp(otp);
        };
        btnVerify.addActionListener(e -> submit.run());
        txtOTP.addActionListener(e -> submit.run());

        JButton btnBack = new JButton("Quay lại đăng nhập");
        btnBack.addActionListener(e -> controller.showLoginScreen());

        form.add(lblInfo); form.add(txtOTP); form.add(btnVerify); form.add(btnBack);
        add(form);
    }

    public void setEmail(String email) {
        lblInfo.setText("<html><center>Nhập mã OTP (6 số) đã gửi đến<br><b>" + escape(email) + "</b></center></html>");
        txtOTP.setText("");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}