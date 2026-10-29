package com.securechat.client.gui;

import com.securechat.client.controller.ClientController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);

    public MainFrame(ClientController controller) {
        setTitle("SecureChat - E2EE Messaging");
        setSize(900, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { controller.shutdown(); }  // LOGOUT + đóng socket
        });
        add(mainPanel);
    }

    public void addPanel(JPanel panel, String name) { mainPanel.add(panel, name); }
    public void showPanel(String name) { cardLayout.show(mainPanel, name); }

    public void setBusy(boolean busy) {
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }
}