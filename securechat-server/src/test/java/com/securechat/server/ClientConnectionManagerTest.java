package com.securechat.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class ClientConnectionManagerTest {

    private ServerSocket ss;
    private Socket serverSide;
    private Socket clientSide;

    private void setupSocketPair() throws IOException {
        ss = new ServerSocket(0);
        clientSide = new Socket("127.0.0.1", ss.getLocalPort());
        serverSide = ss.accept();
    }

    @AfterEach
    void cleanup() throws IOException {
        if (serverSide != null) serverSide.close();
        if (clientSide != null) clientSide.close();
        if (ss != null) ss.close();
    }

    @Test
    void registerAndUnregisterTracksConnectionCount() throws Exception {
        setupSocketPair();
        ClientHandler handler = new ClientHandler(serverSide);
        ClientConnectionManager.Connection c = ClientConnectionManager.registerConnection(handler, serverSide);

        assertNotNull(c);
        assertTrue(c.id > 0);
        assertEquals(1, ClientConnectionManager.connectionCount());

        ClientConnectionManager.unregisterConnection(c);
        assertEquals(0, ClientConnectionManager.connectionCount());
    }

    @Test
    void registerUserRejectsDuplicate() throws Exception {
        setupSocketPair();
        ClientHandler h1 = new ClientHandler(serverSide);
        ClientConnectionManager.Connection c1 = ClientConnectionManager.registerConnection(h1, serverSide);

        assertTrue(ClientConnectionManager.register(1L, h1));
        assertFalse(ClientConnectionManager.register(1L, h1)); // duplicate

        ClientConnectionManager.unregisterConnection(c1);
    }

    @Test
    void unregisterConnectionAlsoRemovesOnlineEntry() throws Exception {
        setupSocketPair();
        ClientHandler handler = new ClientHandler(serverSide);
        ClientConnectionManager.Connection c = ClientConnectionManager.registerConnection(handler, serverSide);
        assertTrue(ClientConnectionManager.register(1L, handler));
        assertEquals(1, ClientConnectionManager.onlineCount());

        ClientConnectionManager.unregisterConnection(c);
        assertEquals(0, ClientConnectionManager.onlineCount());
        assertNull(ClientConnectionManager.findByUserId(1L));
    }
}