package com.securechat.tools;

import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;
import com.securechat.security.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * Reference secure client. Reads one JSON line from stdin, sends encrypted,
 * reads encrypted response. Use for handshake + auth smoke test.
 */
public class Phase3TestClient {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: Phase3TestClient <host> <port> <identityPubKeyBase64File>");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        PublicKey identityPub = loadPub(Files.readString(Path.of(args[2])).trim());

        try (Socket s = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in))) {

            // 1. CLIENT_HELLO
            byte[] clientNonce = AESUtil.randomBytes(32);
            sendPlain(out, MessageType.CLIENT_HELLO, "hello-1",
                    Map.of("version", "v1", "clientNonce", b64(clientNonce)));
            Packet hello = readPacket(in);
            requireStatus(hello, "SERVER_HELLO");

            String keyId = (String) hello.getPayload().get("keyId");
            byte[] serverNonce = Base64.getDecoder().decode((String) hello.getPayload().get("serverNonce"));
            byte[] ephPubDer = Base64.getDecoder().decode((String) hello.getPayload().get("ephemeralPublicKey"));
            byte[] signature = Base64.getDecoder().decode((String) hello.getPayload().get("signature"));

            // 2. Verify signature
            byte[] tbs = SecurityUtils.concatLenPrefixed(
                    "SecureChat-Handshake-v1".getBytes(StandardCharsets.UTF_8),
                    keyId.getBytes(StandardCharsets.UTF_8),
                    clientNonce, serverNonce, ephPubDer);
            if (!RSAUtil.pssVerify(identityPub, tbs, signature))
                throw new SecurityException("Server signature invalid");

            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey ephPub = kf.generatePublic(new X509EncodedKeySpec(ephPubDer));

            // 3. KEY_EXCHANGE
            byte[] secret = AESUtil.randomBytes(32);
            byte[] wrapped = RSAUtil.oaepEncrypt(ephPub, secret);
            sendPlain(out, MessageType.KEY_EXCHANGE, "kx-1",
                    Map.of("encryptedSecret", b64(wrapped)));

            // 4. Derive keys
            byte[] salt = SecurityUtils.concat(clientNonce, serverNonce);
            byte[] okm = HKDFUtil.derive(secret, salt,
                    "SecureChat-Handshake-v1-HKDF".getBytes(StandardCharsets.UTF_8), 64);
            AESUtil.wipe(secret);
            SecretKey c2sKey = new SecretKeySpec(Arrays.copyOfRange(okm, 0, 32), "AES");
            SecretKey s2cKey = new SecretKeySpec(Arrays.copyOfRange(okm, 32, 64), "AES");

            // 5. Receive SERVER_CONFIRMED (SECURE_MESSAGE seq=1)
            Packet env1 = readPacket(in);
            requireStatus(env1, "SECURE_MESSAGE");
            Packet serverConfirm = decrypt(env1, s2cKey, 1, "C2S".equals("no") ? "S2C" : "S2C");
            // fix: build AAD as S2C since it's server->client
            if (!"HANDSHAKE_CONFIRM".equals(serverConfirm.getType())
                    || !"SERVER_CONFIRMED".equals(serverConfirm.getPayload().get("confirm")))
                throw new SecurityException("Bad server confirm");

            // 6. Send CLIENT_CONFIRMED (SECURE_MESSAGE seq=1)
            Packet clientConfirm = Packet.ok(MessageType.HANDSHAKE_CONFIRM, "kx-2",
                    Map.of("confirm", "CLIENT_CONFIRMED"));
            out.println(encrypt(clientConfirm, c2sKey, 1, "C2S"));

            System.out.println(">>> Handshake OK. Type JSON line by line, empty line to quit.");
            long outSeq = 1, inSeq = 1;
            String line;
            while ((line = stdin.readLine()) != null && !line.isBlank()) {
                Packet req = ProtocolSerializer.fromJson(line);
                outSeq++;
                out.println(encrypt(req, c2sKey, outSeq, "C2S"));
                Packet envResp = readPacket(in);
                inSeq++;
                Packet resp = decrypt(envResp, s2cKey, inSeq, "S2C");
                System.out.println("<<< " + ProtocolSerializer.toJson(resp));
            }
        }
    }

    private static PublicKey loadPub(String b64) throws Exception {
        byte[] der = Base64.getDecoder().decode(b64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static void sendPlain(PrintWriter out, String type, String reqId, Map<String, Object> payload) throws Exception {
        out.println(ProtocolSerializer.toJson(Packet.request(type, reqId, payload)));
    }

    private static Packet readPacket(BufferedReader in) throws Exception {
        String line = in.readLine();
        if (line == null) throw new EOFException();
        return ProtocolSerializer.fromJson(line);
    }

    private static void requireStatus(Packet p, String expectedType) {
        if (p == null || !expectedType.equals(p.getType()))
            throw new IllegalStateException("Expected type=" + expectedType + ", got " + ProtocolSerializer.toJsonSafe(p));
    }

    private static String encrypt(Packet inner, SecretKey key, long seq, String dir) throws Exception {
        byte[] nonce = AESUtil.deriveNonce(seq);
        byte[] aad = aad(dir, seq);
        byte[] plain = ProtocolSerializer.toJson(inner).getBytes(StandardCharsets.UTF_8);
        byte[] ct = AESUtil.gcmEncrypt(key, nonce, aad, plain);
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("sequence", seq);
        env.put("nonce", b64(nonce));
        env.put("ciphertext", b64(ct));
        return ProtocolSerializer.toJson(Packet.ok("SECURE_MESSAGE", inner.getRequestId(), env));
    }

    private static Packet decrypt(Packet env, SecretKey key, long seq, String dir) throws Exception {
        Map<String, Object> p = env.getPayload();
        long gotSeq = ((Number) p.get("sequence")).longValue();
        if (gotSeq != seq) throw new SecurityException("Seq mismatch: expected " + seq + " got " + gotSeq);
        byte[] nonce = Base64.getDecoder().decode((String) p.get("nonce"));
        byte[] ct = Base64.getDecoder().decode((String) p.get("ciphertext"));
        byte[] plain = AESUtil.gcmDecrypt(key, nonce, aad(dir, gotSeq), ct);
        return ProtocolSerializer.fromJson(new String(plain, StandardCharsets.UTF_8));
    }

    private static byte[] aad(String dir, long seq) {
        return ("SecureChat-SecureEnvelope-v1|" + dir + "|" + seq).getBytes(StandardCharsets.UTF_8);
    }

    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }

    private static class MessageType {
        static final String CLIENT_HELLO = "CLIENT_HELLO";
        static final String SERVER_HELLO = "SERVER_HELLO";
        static final String KEY_EXCHANGE = "KEY_EXCHANGE";
        static final String HANDSHAKE_CONFIRM = "HANDSHAKE_CONFIRM";
    }

}