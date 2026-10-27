package com.example.AuthService.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

// Auth v2 step 1: turns the env var jwtprivatekey into a real RSA private key.
// Accepts the text of a PEM file ("-----BEGIN PRIVATE KEY-----" ...) or the same thing as one line of base64.
// Anything wrong stops AuthService at start-up with a clear message - and never prints the key itself.
public final class RsaKeys {

    private static final int MIN_BITS = 2048;

    private RsaKeys() {
    }

    public static PrivateKey privateKeyFrom(String text) {
        RSAPrivateKey key;
        try {
            byte[] der = Base64.getDecoder().decode(onlyTheBase64(text));
            key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            // e is not attached: its message could repeat part of the key text.
            throw new IllegalStateException("jwt.private-key (env var jwtprivatekey) is not a readable RSA private key."
                    + " Expected PKCS#8: a PEM starting with -----BEGIN PRIVATE KEY-----, or the same as one line of base64.");
        }
        if (key.getModulus().bitLength() < MIN_BITS) {
            throw new IllegalStateException("jwt.private-key (env var jwtprivatekey) is shorter than " + MIN_BITS + " bits.");
        }
        return key;
    }

    // Drops the BEGIN/END lines and every space or line break, leaving only the base64 body.
    private static String onlyTheBase64(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty");
        }
        return text.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
    }
}
