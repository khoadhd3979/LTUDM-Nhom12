package com.securechat.client.gui;

import com.securechat.client.controller.ClientController;
import com.securechat.client.validation.InputValidator;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class ChatPanel extends JPanel {
    private final ClientController controller;
    private final JLabel lblUser = new JLabel("Chưa đăng nhập");
    private final JLabel lblCurrentTarget = new JLabel("Chưa chọn người nhận / nhóm");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();

    private final DefaultListModel<String> friendListModel = new DefaultListModel<>();
    private final JList<String> friendList = new JList<>(friendListModel);

    private final DefaultListModel<String> groupListModel = new DefaultListModel<>();
    private final JList<String> groupList = new JList<>(groupListModel);

    private String currentTarget = null;
    private boolean isGroupChat = false;

    public ChatPanel(ClientController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(5, 5));


        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JButton btnProfile = new JButton("Hồ sơ");
        JButton btnCreateGroup = new JButton("Tạo Nhóm");
        JButton btnLoadHistory = new JButton("Tải Tin Cũ");
        JButton btnBlock = new JButton("Chặn");
        JButton btnSaveFile = new JButton("Lưu File");
        JButton btnLogout = new JButton("Đăng xuất");

        btnProfile.addActionListener(e -> showUpdateProfileDialog());
        btnCreateGroup.addActionListener(e -> showCreateGroupDialog());
        btnLoadHistory.addActionListener(e -> {
            if (currentTarget != null) controller.loadMessageHistory(currentTarget, isGroupChat);
        });
        btnBlock.addActionListener(e -> showBlockDialog());
        btnSaveFile.addActionListener(e -> controller.saveLastReceivedFile(this));
        btnLogout.addActionListener(e -> controller.logout());

        topBar.add(lblUser); topBar.add(btnProfile); topBar.add(btnCreateGroup);
        topBar.add(btnLoadHistory); topBar.add(btnBlock); topBar.add(btnSaveFile); topBar.add(btnLogout);
        add(topBar, BorderLayout.NORTH);

        // --- LEFT PANEL: Danh sách Bạn bè (STT 12) & Nhóm (STT 4, 5) ---
        JTabbedPane leftTabs = new JTabbedPane();
        leftTabs.setPreferredSize(new Dimension(220, 0));

        JPanel friendPanel = new JPanel(new BorderLayout());
        friendList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && friendList.getSelectedValue() != null) {
                groupList.clearSelection();
                selectTarget(friendList.getSelectedValue().split(" ")[0].trim(), false);
            }
        });
        JButton btnAddFriend = new JButton("+ Thêm Bạn Bè / Chat");
        btnAddFriend.addActionListener(e -> {
            String email = JOptionPane.showInputDialog(this, "Nhập email người muốn thêm bạn/nhắn tin:");
            if (email != null && !email.trim().isEmpty()) {
                email = email.trim();
                String err = InputValidator.email(email);
                if (err != null) {
                    JOptionPane.showMessageDialog(this, err, "Lỗi", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                controller.addFriend(email);
            }
        });
        friendPanel.add(new JScrollPane(friendList), BorderLayout.CENTER);
        friendPanel.add(btnAddFriend, BorderLayout.SOUTH);

        JPanel groupPanel = new JPanel(new BorderLayout());
        groupList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && groupList.getSelectedValue() != null) {
                friendList.clearSelection();
                selectTarget(groupList.getSelectedValue().trim(), true);
            }
        });
        JPanel groupActionPanel = new JPanel(new GridLayout(3, 1));
        JButton btnJoin = new JButton("Gia nhập nhóm");
        JButton btnLeave = new JButton("Rời nhóm");
        JButton btnManage = new JButton("Quản lý TV (Admin)");
        btnJoin.addActionListener(e -> showJoinGroupDialog());
        btnLeave.addActionListener(e -> { if (isGroupChat) controller.leaveGroup(currentTarget); });
        btnManage.addActionListener(e -> showManageGroupDialog());
        groupActionPanel.add(btnJoin); groupActionPanel.add(btnLeave); groupActionPanel.add(btnManage);

        groupPanel.add(new JScrollPane(groupList), BorderLayout.CENTER);
        groupPanel.add(groupActionPanel, BorderLayout.SOUTH);

        leftTabs.addTab("Bạn bè", friendPanel);
        leftTabs.addTab("Nhóm", groupPanel);
        add(leftTabs, BorderLayout.WEST);

        // --- CENTER PANEL: Nội dung Chat ---
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(lblCurrentTarget, BorderLayout.NORTH);
        chatArea.setEditable(false); chatArea.setLineWrap(true); chatArea.setWrapStyleWord(true);
        centerPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // --- BOTTOM PANEL: Gửi text (STT 6, 7), File (STT 8), Sticker (STT 9) ---
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        JButton btnSticker = new JButton("Sticker");
        JButton btnSendFile = new JButton("Gửi File");
        JButton btnSendText = new JButton("Gửi");
        btnPanel.add(btnSticker); btnPanel.add(btnSendFile); btnPanel.add(btnSendText);
        inputPanel.add(inputField, BorderLayout.CENTER); inputPanel.add(btnPanel, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        btnSendText.addActionListener(e -> sendText());
        inputField.addActionListener(e -> sendText());
        btnSticker.addActionListener(e -> showStickerDialog());
        btnSendFile.addActionListener(e -> chooseAndSendFile());
    }

    public void setUser(String email) {
        lblUser.setText("Xin chào, " + email + " | ");
        chatArea.setText(""); currentTarget = null; isGroupChat = false;
    }

    private void selectTarget(String target, boolean group) {
        this.currentTarget = target; this.isGroupChat = group;
        lblCurrentTarget.setText(group ? "Chat Nhóm: " + target : "Chat 1-1: " + target);
        controller.markConversationSeen(target, group); // Báo STT 11 (SEEN)
    }

    public void addOrUpdateFriend(String email, String status) {
        String display = email + " (" + status + ")";
        for (int i = 0; i < friendListModel.size(); i++) {
            if (friendListModel.get(i).startsWith(email + " ")) { friendListModel.set(i, display); return; }
        }
        friendListModel.addElement(display);
    }
    public void addGroupToList(String groupName) { if (!groupListModel.contains(groupName)) groupListModel.addElement(groupName); }
    public void removeGroupFromList(String groupName) { groupListModel.removeElement(groupName); }

    private void showUpdateProfileDialog() {
        JTextField txtName = new JTextField(20); JComboBox<String> cbGender = new JComboBox<>(new String[]{"MALE", "FEMALE", "OTHER"}); JTextField txtDob = new JTextField("2000-01-01", 15);
        JPanel panel = new JPanel(new GridLayout(3, 2)); panel.add(new JLabel("Họ tên:")); panel.add(txtName); panel.add(new JLabel("Giới tính:")); panel.add(cbGender); panel.add(new JLabel("Ngày sinh:")); panel.add(txtDob);
        if (JOptionPane.showConfirmDialog(this, panel, "Cập nhật hồ sơ", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            controller.updateProfile(txtName.getText().trim(), (String) cbGender.getSelectedItem(), txtDob.getText().trim());
        }
    }
    private void showCreateGroupDialog() {
        String name = JOptionPane.showInputDialog(this, "Nhập tên nhóm mới:");
        if (name != null && !name.trim().isEmpty()) controller.createGroup(name.trim());
    }
    private void showJoinGroupDialog() {
        String name = JOptionPane.showInputDialog(this, "Nhập tên nhóm muốn gia nhập:");
        if (name != null && !name.trim().isEmpty()) controller.joinGroup(name.trim());
    }
    private void showManageGroupDialog() {
        if (!isGroupChat || currentTarget == null) return;
        String[] options = {"Thêm TV", "Xóa TV", "Hủy"};
        int choice = JOptionPane.showOptionDialog(this, "Quản trị nhóm [" + currentTarget + "]:", "Quản trị", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0 || choice == 1) {
            String email = JOptionPane.showInputDialog(this, "Nhập email thành viên:");
            if (email != null) { if (choice == 0) controller.addGroupMember(currentTarget, email.trim()); else controller.removeGroupMember(currentTarget, email.trim()); }
        }
    }
    private void showBlockDialog() {
        String[] opts = {"Chặn Email", "Chặn Nhóm"};
        int ch = JOptionPane.showOptionDialog(this, "Chặn nhận tin nhắn từ:", "Chặn", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]);
        String val = JOptionPane.showInputDialog(this, "Nhập Email/Tên Nhóm:");
        if (val != null && !val.trim().isEmpty()) controller.blockTarget(val.trim(), ch == 1);
    }
    private void showStickerDialog() {
        if (currentTarget == null) return;
        String[] stickers = {"STICKER_LIKE", "STICKER_HEART", "STICKER_LAUGH", "STICKER_SAD"};
        String picked = (String) JOptionPane.showInputDialog(this, "Chọn Sticker:", "Gửi", JOptionPane.PLAIN_MESSAGE, null, stickers, stickers[0]);
        if (picked != null) controller.sendSticker(currentTarget, isGroupChat, picked);
    }
    private void chooseAndSendFile() {
        if (currentTarget == null) return;
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fc.getSelectedFile();

            String err = InputValidator.attachment(selectedFile);
            if (err != null) {
                JOptionPane.showMessageDialog(this, err, "Lỗi file", JOptionPane.WARNING_MESSAGE);
                return;
            }

            controller.sendFile(currentTarget, isGroupChat, selectedFile);
        }
    }
    private void sendText() {
        if (currentTarget == null) return;
        String text = inputField.getText().trim();
        if (!text.isEmpty()) { controller.sendChatMessage(currentTarget, isGroupChat, text); inputField.setText(""); }
    }
    public void appendMessage(String message) {
        chatArea.append(message + "\n"); chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
}