package com.securechat.client.gui;

import com.securechat.client.controller.ClientController;
import com.securechat.client.validation.InputValidator;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ChatPanel extends JPanel {
    private final ClientController controller;
    private final JLabel lblUser = new JLabel("Chưa đăng nhập");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();

    public ChatPanel(ClientController controller) {
        this.controller = controller;
        setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnCreateGroup = new JButton("Tạo Nhóm");
        JButton btnLoadHistory = new JButton("Tải Tin Nhắn Cũ");
        JButton btnBlock = new JButton("Chặn Người Dùng");
        JButton btnLogout = new JButton("Đăng xuất");
        btnCreateGroup.addActionListener(e -> showCreateGroupDialog());
        btnLoadHistory.addActionListener(e -> controller.loadMessageHistory());
        btnBlock.addActionListener(e -> showBlockDialog());
        btnLogout.addActionListener(e -> controller.logout());
        topBar.add(lblUser);
        topBar.add(btnCreateGroup); topBar.add(btnLoadHistory); topBar.add(btnBlock); topBar.add(btnLogout);
        add(topBar, BorderLayout.NORTH);

        JScrollPane userScroll = new JScrollPane(new JList<>(new DefaultListModel<String>()));
        userScroll.setPreferredSize(new Dimension(150, 0));
        userScroll.setBorder(BorderFactory.createTitledBorder("Bạn bè"));
        add(userScroll, BorderLayout.WEST);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("Nội dung chat"));
        add(chatScroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton btnSendFile = new JButton("Gửi File");
        JButton btnSendText = new JButton("Gửi");
        btnPanel.add(btnSendFile); btnPanel.add(btnSendText);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(btnPanel, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(inputPanel, BorderLayout.SOUTH);

        btnSendText.addActionListener(e -> sendText());
        inputField.addActionListener(e -> sendText());
        btnSendFile.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                String err = InputValidator.attachment(f);
                if (err != null) { warn(err); return; }
                controller.sendFile(f);
            }
        });
    }

    public void setUser(String email) {
        lblUser.setText("Xin chào, " + email + "  ");
        chatArea.setText("");
    }

    private void showCreateGroupDialog() {
        String name = JOptionPane.showInputDialog(this, "Nhập tên nhóm mới:", "Tạo Nhóm", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty() || name.length() > 100) { warn("Tên nhóm phải từ 1 đến 100 ký tự."); return; }
        controller.createGroup(name);
    }

    private void showBlockDialog() {
        String email = JOptionPane.showInputDialog(this, "Nhập email người muốn chặn:", "Chặn Người Dùng", JOptionPane.WARNING_MESSAGE);
        if (email == null) return;
        email = email.trim();
        String err = InputValidator.email(email);
        if (err != null) { warn(err); return; }
        controller.blockUser(email);
    }

    private void sendText() {
        String text = inputField.getText().trim();
        String err = InputValidator.chatMessage(text);
        if (err != null) { warn(err); return; }
        controller.sendChatMessage(text);
        inputField.setText("");
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Dữ liệu chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
    }

    public void appendMessage(String message) {
        chatArea.append(message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
}