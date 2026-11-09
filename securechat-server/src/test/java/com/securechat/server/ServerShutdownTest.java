package com.securechat.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class ServerShutdownTest {

    @Test
    void closeAllClosesRegisteredSockets() throws Exception {
        // Tạo 2 socket pairs
        ServerSocket ss = new ServerSocket(0);
        Socket client1 = new Socket("127.0.0.1", ss.getLocalPort());
        Socket server1 = ss.accept();
        Socket client2 = new Socket("127.0.0.1", ss.getLocalPort());
        Socket server2 = ss.accept();

        ClientHandler h1 = new ClientHandler(server1);
        ClientHandler h2 = new ClientHandler(server2);
        ClientConnectionManager.Connection c1 = ClientConnectionManager.registerConnection(h1, server1);
        ClientConnectionManager.Connection c2 = ClientConnectionManager.registerConnection(h2, server2);

        assertEquals(2, ClientConnectionManager.connectionCount());

        ClientConnectionManager.closeAll();

        // Sau closeAll, socket đã đóng — client side sẽ thấy EOF
        assertTrue(server1.isClosed());
        assertTrue(server2.isClosed());

        ClientConnectionManager.unregisterConnection(c1);
        ClientConnectionManager.unregisterConnection(c2);
        client1.close();
        client2.close();
        ss.close();
    }
}