package com.securechat.security;

import com.securechat.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public final class ServerIdentityKeyManager {
    private static final ServerIdentityKeyManager INSTANCE = new ServerIdentityKeyManager();

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;

    private ServerIdentityKeyManager() {
        try {
            Path path = Path.of(AppConfig.identityKeyPath());
            if (Files.exists(path)) {
                byte[] der = Files.readAllBytes(path);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                this.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            } else {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KeyPair kp = kpg.generateKeyPair();
                this.privateKey = kp.getPrivate();
                Files.write(path, privateKey.getEncoded());
            }
            this.publicKey = RSAUtil.derivePublicKey(privateKey);
            this.keyId = shortKeyId(publicKey);
            printStartupInfo(path);
        } catch (Exception e) {
            throw new RuntimeException("Không thể khởi tạo server identity key", e);
        }
    }

    public static ServerIdentityKeyManager getInstance() { return INSTANCE; }
    public PrivateKey privateKey() { return privateKey; }
    public PublicKey publicKey()   { return publicKey; }
    public String keyId()          { return keyId; }

    private static String shortKeyId(PublicKey pub) throws GeneralSecurityException {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(String.format("%02X", h[i]));
        return sb.toString();
    }

    private void printStartupInfo(Path path) throws GeneralSecurityException {
        System.out.println("[IDENTITY] Key file     : " + path.toAbsolutePath());
        System.out.println("[IDENTITY] Key ID       : " + keyId);
        System.out.println("[IDENTITY] Fingerprint  : " + RSAUtil.fingerprint(publicKey));
        System.out.println("[IDENTITY] Public key (Base64 SPKI) — pin this on client:");
        System.out.println(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
    }
}