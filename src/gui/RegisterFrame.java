package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class RegisterFrame extends JFrame {
    private JTextField txtEmail, txtFullName, txtDob;
    private JPasswordField txtPassword;
    private JComboBox<String> cbGender;
    private JButton btnRegister, btnBack;

    public RegisterFrame() {
        setTitle("SecureChat - Đăng ký tài khoản");
        setSize(450, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JLabel lblTitle = new JLabel("ĐĂNG KÝ TÀI KHOẢN MỚI", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 18));
        lblTitle.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));
        add(lblTitle, BorderLayout.NORTH);

        JPanel pnlForm = new JPanel(new GridLayout(5, 2, 10, 12));
        pnlForm.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));

        pnlForm.add(new JLabel("Email (Username):"));
        txtEmail = new JTextField();
        pnlForm.add(txtEmail);

        pnlForm.add(new JLabel("Mật khẩu:"));
        txtPassword = new JPasswordField();
        pnlForm.add(txtPassword);

        pnlForm.add(new JLabel("Họ và tên:"));
        txtFullName = new JTextField();
        pnlForm.add(txtFullName);

        pnlForm.add(new JLabel("Giới tính:"));
        cbGender = new JComboBox<>(new String[]{"Nam", "Nữ", "Khác"});
        pnlForm.add(cbGender);

        pnlForm.add(new JLabel("Ngày sinh (YYYY-MM-DD):"));
        txtDob = new JTextField("2004-01-01");
        pnlForm.add(txtDob);

        add(pnlForm, BorderLayout.CENTER);

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnRegister = new JButton("Đăng ký");
        btnBack = new JButton("Quay lại Đăng nhập");

        pnlButtons.add(btnRegister);
        pnlButtons.add(btnBack);
        pnlButtons.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        add(pnlButtons, BorderLayout.SOUTH);
    }

    public String getGender() {
        if (cbGender.getSelectedItem() != null) {
            String selected = cbGender.getSelectedItem().toString();
            if ("Nam".equals(selected)) return "MALE";
            if ("Nữ".equals(selected)) return "FEMALE";
        }
        return "OTHER";
    }

    public String getDateOfBirth() {
        return txtDob.getText().trim();
    }

    public String getEmail() {
        return txtEmail.getText().trim();
    }

    public char[] getPassword() {
        return txtPassword.getPassword();
    }

    public String getFullName() {
        return txtFullName.getText().trim();
    }

    public JFrame getFrame() {
        return this;
    }

    public void setRegisterListener(ActionListener listener) {
        btnRegister.addActionListener(listener);
    }

    public void setBackListener(ActionListener listener) {
        btnBack.addActionListener(listener);
    }
}