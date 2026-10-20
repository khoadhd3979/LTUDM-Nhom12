package main;

import gui.LoginFrame;

import javax.swing.*;
import java.awt.*;

public class ClientMain extends JFrame {
    private JTextField txtIpAddress;
    private JButton btnConnect;

    public ClientMain() {
        setTitle("SecureChat - Kết nối Server");
        setSize(380, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JLabel lblTitle = new JLabel("KẾT NỐI ĐẾN SERVER CHAT", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 16));
        lblTitle.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
        add(lblTitle, BorderLayout.NORTH);

        JPanel pnlCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 20));
        pnlCenter.add(new JLabel("Server IP:"));
        txtIpAddress = new JTextField("127.0.0.1", 15);
        pnlCenter.add(txtIpAddress);
        add(pnlCenter, BorderLayout.CENTER);

        JPanel pnlSouth = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnConnect = new JButton("Kết nối");
        pnlSouth.add(btnConnect);
        pnlSouth.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        add(pnlSouth, BorderLayout.SOUTH);

        btnConnect.addActionListener(e -> {
            String ip = txtIpAddress.getText().trim();
            if(!ip.isEmpty()) {
                this.dispose();
                LoginFrame loginView = new LoginFrame();
                new controller.LoginController(loginView);
                loginView.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ IP Server!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientMain().setVisible(true);
        });
    }
}