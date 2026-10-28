package com.securechat.security;

import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SecureTransport {

    private final SecureSession session;
    private final Object outboundLock = new Object();

    public SecureTransport(SecureSession session) { this.session = session; }

    /** Encrypt inner Packet into a SECURE_MESSAGE envelope. Thread-safe. */
    public String encodeOutbound(Packet inner) throws Exception {
        synchronized (outboundLock) {
            long seq = session.nextOutboundSeq();
            byte[] nonce = AESUtil.deriveNonce(seq);
            byte[] aad = aad("S2C", seq);
            byte[] plain = ProtocolSerializer.toJson(inner).getBytes(StandardCharsets.UTF_8);
            byte[] cipher = AESUtil.gcmEncrypt(session.getS2cKey(), nonce, aad, plain);

            Map<String, Object> env = new LinkedHashMap<>();
            env.put("sequence", seq);
            env.put("nonce", Base64.getEncoder().encodeToString(nonce));
            env.put("ciphertext", Base64.getEncoder().encodeToString(cipher));

            Packet outer = Packet.ok("SECURE_MESSAGE", inner.getRequestId(), env);
            return ProtocolSerializer.toJson(outer);
        }
    }

    /** Decrypt a SECURE_MESSAGE envelope back to inner Packet. Verifies seq + tag. */
    public Packet decodeInbound(Packet envelope) throws Exception {
        Map<String, Object> p = envelope.getPayload();
        if (p == null) throw new SecurityException("Empty SECURE_MESSAGE payload");
        Object seqObj = p.get("sequence");
        Object nonceObj = p.get("nonce");
        Object ctObj = p.get("ciphertext");
        if (!(seqObj instanceof Number) || !(nonceObj instanceof String) || !(ctObj instanceof String))
            throw new SecurityException("Malformed SECURE_MESSAGE");

        long seq = ((Number) seqObj).longValue();
        byte[] wireNonce = Base64.getDecoder().decode((String) nonceObj);
        byte[] cipher = Base64.getDecoder().decode((String) ctObj);

        long expected = session.expectedInboundSeq();
        if (seq != expected)
            throw new SecurityException("Replay/ordering: expected " + expected + " got " + seq);

        byte[] expectedNonce = AESUtil.deriveNonce(seq);
        if (!Arrays.equals(wireNonce, expectedNonce))
            throw new SecurityException("Nonce mismatch");

        byte[] aad = aad("C2S", seq);
        byte[] plain = AESUtil.gcmDecrypt(session.getC2sKey(), wireNonce, aad, cipher);
        session.advanceInboundSeq(seq);
        return ProtocolSerializer.fromJson(new String(plain, StandardCharsets.UTF_8));
    }

    private static byte[] aad(String direction, long seq) {
        return ("SecureChat-SecureEnvelope-v1|" + direction + "|" + seq)
                .getBytes(StandardCharsets.UTF_8);
    }
}