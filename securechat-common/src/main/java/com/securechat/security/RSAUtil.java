package com.securechat.security;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPublicKeySpec;

public final class RSAUtil {
    private RSAUtil() {}

    public static KeyPair generateEphemeralKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private static OAEPParameterSpec oaepSpec() {
        return new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }

    public static byte[] oaepEncrypt(PublicKey pub, byte[] plain) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        c.init(Cipher.ENCRYPT_MODE, pub, oaepSpec());
        return c.doFinal(plain);
    }

    public static byte[] oaepDecrypt(PrivateKey priv, byte[] cipher) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        c.init(Cipher.DECRYPT_MODE, priv, oaepSpec());
        return c.doFinal(cipher);
    }

    public static byte[] pssSign(PrivateKey priv, byte[] data) throws GeneralSecurityException {
        Signature s = Signature.getInstance("RSASSA-PSS");
        s.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        s.initSign(priv);
        s.update(data);
        return s.sign();
    }

    public static boolean pssVerify(PublicKey pub, byte[] data, byte[] sig) throws GeneralSecurityException {
        Signature s = Signature.getInstance("RSASSA-PSS");
        s.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        s.initVerify(pub);
        s.update(data);
        try { return s.verify(sig); }
        catch (SignatureException e) { return false; }
    }

    public static PublicKey derivePublicKey(PrivateKey priv) throws GeneralSecurityException {
        if (priv instanceof java.security.interfaces.RSAPrivateCrtKey crt) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        }
        throw new GeneralSecurityException("Không thể suy ra public key từ private key");
    }

    public static String fingerprint(PublicKey pub) throws GeneralSecurityException {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", h[i]));
        }
        return sb.toString();
    }
}