package com.securechat.client.gui;

import com.securechat.client.controller.ClientController;
import com.securechat.client.validation.InputValidator;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class RegisterPanel extends JPanel {
    public RegisterPanel(ClientController controller) {
        setLayout(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(9, 2, 10, 10));

        JTextField txtIp = new JTextField(controller.getHost(), 20);
        JTextField txtPort = new JTextField(String.valueOf(controller.getPort()), 20);
        JTextField txtEmail = new JTextField(20);
        JPasswordField txtPassword = new JPasswordField(20);
        JPasswordField txtConfirm = new JPasswordField(20);
        JTextField txtFullName = new JTextField(20);
        JComboBox<String> cbGender = new JComboBox<>(new String[]{"MALE", "FEMALE", "OTHER"});
        JTextField txtDob = new JTextField("2000-01-01", 20);

        form.add(new JLabel("IP Server:"));                form.add(txtIp);
        form.add(new JLabel("Port:"));                     form.add(txtPort);
        form.add(new JLabel("Email (*):"));                form.add(txtEmail);
        form.add(new JLabel("Mật khẩu (*) (8-72 ký tự):")); form.add(txtPassword);
        form.add(new JLabel("Nhập lại mật khẩu (*):"));    form.add(txtConfirm);
        form.add(new JLabel("Họ và tên (*):"));            form.add(txtFullName);
        form.add(new JLabel("Giới tính:"));                form.add(cbGender);
        form.add(new JLabel("Ngày sinh (yyyy-MM-dd):"));   form.add(txtDob);

        JButton btnBack = new JButton("Quay lại");
        btnBack.addActionListener(e -> controller.showLoginScreen());
        JButton btnRegister = new JButton("Đăng ký");
        btnRegister.addActionListener(e -> {
            String ip = txtIp.getText().trim();
            String port = txtPort.getText().trim();
            String email = txtEmail.getText().trim();
            char[] pw = txtPassword.getPassword();
            char[] pw2 = txtConfirm.getPassword();
            String name = txtFullName.getText().trim();
            String gender = (String) cbGender.getSelectedItem();
            String dob = txtDob.getText().trim();

            String err = InputValidator.firstError(
                    InputValidator.host(ip), InputValidator.port(port),
                    InputValidator.email(email), InputValidator.passwordPolicy(pw),
                    Arrays.equals(pw, pw2) ? null : "Hai mật khẩu không khớp.",
                    InputValidator.fullName(name), InputValidator.gender(gender),
                    InputValidator.dateOfBirth(dob));
            Arrays.fill(pw2, '\0');
            if (err != null) {
                Arrays.fill(pw, '\0');
                JOptionPane.showMessageDialog(this, err, "Dữ liệu chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
                return;
            }
            txtPassword.setText(""); txtConfirm.setText("");
            controller.register(ip, Integer.parseInt(port), email, pw, name, gender, dob);
        });
        form.add(btnBack);
        form.add(btnRegister);
        add(form);
    }
}