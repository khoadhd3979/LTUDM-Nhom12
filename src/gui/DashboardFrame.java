package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class DashboardFrame extends JFrame {
    private JTextArea txtChatArea;
    private JTextField txtMessageInput;
    private JButton btnSend;
    private JButton btnLogout;

    public DashboardFrame() {
        initComponents();
    }

    private void initComponents() {
        setTitle("SecureChat - Dashboard");
        setSize(600, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnLogout = new JButton("Đăng xuất");
        btnLogout.setBackground(new Color(255, 102, 102));
        btnLogout.setForeground(Color.WHITE);
        topPanel.add(btnLogout);
        add(topPanel, BorderLayout.NORTH);

        txtChatArea = new JTextArea();
        txtChatArea.setEditable(false);
        txtChatArea.setLineWrap(true);
        txtChatArea.setWrapStyleWord(true);
        txtChatArea.setMargin(new Insets(5, 5, 5, 5));
        JScrollPane scrollPane = new JScrollPane(txtChatArea);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));

        txtMessageInput = new JTextField();
        btnSend = new JButton("Gửi");
        btnSend.setBackground(new Color(51, 153, 255));
        btnSend.setForeground(Color.WHITE);

        bottomPanel.add(txtMessageInput, BorderLayout.CENTER);
        bottomPanel.add(btnSend, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public String getMessageInput() {
        return txtMessageInput.getText();
    }

    public void clearMessageInput() {
        txtMessageInput.setText("");
    }

    public void appendMessage(String msg) {
        txtChatArea.append(msg + "\n");
        txtChatArea.setCaretPosition(txtChatArea.getDocument().getLength());
    }

    public void addSendListener(ActionListener listener) {
        btnSend.addActionListener(listener);
        txtMessageInput.addActionListener(listener);
    }

    public void setLogoutListener(ActionListener listener) {
        if (btnLogout != null) {
            btnLogout.addActionListener(listener);
        }
    }
}