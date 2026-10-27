package com.securechat.client.gui;

import com.securechat.client.controller.ClientController;
import com.securechat.client.validation.InputValidator;

import javax.swing.*;
import java.awt.*;

public class LoginPanel extends JPanel {
    private final JTextField txtIp = new JTextField("127.0.0.1", 20);
    private final JTextField txtPort = new JTextField("8080", 20);
    private final JTextField txtEmail = new JTextField(20);
    private final JPasswordField txtPassword = new JPasswordField(20);

    public LoginPanel(ClientController controller) {
        setLayout(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(5, 2, 10, 10));
        form.add(new JLabel("IP Server:"));   form.add(txtIp);
        form.add(new JLabel("Port:"));        form.add(txtPort);
        form.add(new JLabel("Email:"));       form.add(txtEmail);
        form.add(new JLabel("Mật khẩu:"));    form.add(txtPassword);

        JButton btnRegister = new JButton("Đăng ký tài khoản");
        btnRegister.addActionListener(e -> controller.showRegisterScreen());
        JButton btnLogin = new JButton("Đăng nhập");
        btnLogin.addActionListener(e -> submit(controller));
        txtPassword.addActionListener(e -> submit(controller));   // Enter để đăng nhập
        form.add(btnRegister);
        form.add(btnLogin);
        add(form);
    }

    private void submit(ClientController controller) {
        String ip = txtIp.getText().trim();
        String port = txtPort.getText().trim();
        String email = txtEmail.getText().trim();
        char[] pw = txtPassword.getPassword();

        String err = InputValidator.firstError(
                InputValidator.host(ip), InputValidator.port(port),
                InputValidator.email(email), InputValidator.passwordRequired(pw));
        if (err != null) {
            java.util.Arrays.fill(pw, '\0');
            JOptionPane.showMessageDialog(this, err, "Dữ liệu chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        txtPassword.setText("");                                   // xóa mật khẩu khỏi ô nhập
        controller.login(ip, Integer.parseInt(port), email, pw);   // controller sẽ xóa mảng pw sau khi dùng
    }
}