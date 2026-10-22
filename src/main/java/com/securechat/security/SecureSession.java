package com.securechat.security;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;

public final class SecureSession {

    public enum State { INIT, HELLO_SENT, KEY_RECEIVED, ESTABLISHED, FAILED }

    private State state = State.INIT;
    private byte[] clientNonce;
    private byte[] serverNonce;
    private PrivateKey ephemeralPrivate;
    private PublicKey ephemeralPublic;
    private SecretKey c2sKey;
    private SecretKey s2cKey;

    private long outboundSeq = 0;
    private long inboundSeq = 0;

    public State getState() { return state; }
    public void setState(State s) { this.state = s; }

    public byte[] getClientNonce() { return clientNonce; }
    public void setClientNonce(byte[] n) { this.clientNonce = n; }

    public byte[] getServerNonce() { return serverNonce; }
    public void setServerNonce(byte[] n) { this.serverNonce = n; }

    public PrivateKey getEphemeralPrivate() { return ephemeralPrivate; }
    public void setEphemeralPrivate(PrivateKey k) { this.ephemeralPrivate = k; }

    public PublicKey getEphemeralPublic() { return ephemeralPublic; }
    public void setEphemeralPublic(PublicKey k) { this.ephemeralPublic = k; }

    public SecretKey getC2sKey() { return c2sKey; }
    public void setC2sKey(SecretKey k) { this.c2sKey = k; }

    public SecretKey getS2cKey() { return s2cKey; }
    public void setS2cKey(SecretKey k) { this.s2cKey = k; }

    public synchronized long nextOutboundSeq()      { return ++outboundSeq; }
    public synchronized long expectedInboundSeq()   { return inboundSeq + 1; }

    public synchronized void advanceInboundSeq(long received) {
        if (received != inboundSeq + 1)
            throw new IllegalStateException("Inbound seq mismatch: expected " + (inboundSeq + 1)
                    + " got " + received);
        inboundSeq = received;
    }
}