package com.securechat.client;

import com.securechat.client.controller.ClientController;
import javax.swing.SwingUtilities;

public class ClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientController controller = new ClientController();
            controller.start();
        });
    }
}