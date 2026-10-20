package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class LoginFrame extends JFrame {
    private JTextField txtEmail;
    private JPasswordField txtPassword;
    private JButton btnLogin, btnRegister;

    public LoginFrame() {
        setTitle("SecureChat - Đăng nhập");
        setSize(400, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(4, 1, 10, 10));

        JPanel panelEmail = new JPanel(new FlowLayout());
        panelEmail.add(new JLabel("Email:"));
        txtEmail = new JTextField(20);
        panelEmail.add(txtEmail);
        add(panelEmail);

        JPanel panelPass = new JPanel(new FlowLayout());
        panelPass.add(new JLabel("Mật khẩu:"));
        txtPassword = new JPasswordField(20);
        panelPass.add(txtPassword);
        add(panelPass);

        JPanel panelBtn = new JPanel(new FlowLayout());
        btnLogin = new JButton("Đăng nhập");
        btnRegister = new JButton("Đăng ký");
        panelBtn.add(btnLogin);
        panelBtn.add(btnRegister);
        add(panelBtn);
    }

    public String getEmail() {
        return txtEmail.getText().trim();
    }

    public char[] getPassword() {
        return txtPassword.getPassword();
    }

    public void setLoginButtonListener(ActionListener listener) {
        btnLogin.addActionListener(listener);
    }

    public void setRegisterButtonListener(ActionListener listener) {
        btnRegister.addActionListener(listener);
    }
}