package network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientSocketManager {
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    public static boolean connect() {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void sendPacket(String jsonPayload) {
        try { if (out != null) out.println(jsonPayload); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public static String receivePacket() {
        try { if (in != null) return in.readLine(); }
        catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public static Socket getSocket() { return socket; }

    public static BufferedReader getInputStream() { return in; }
}