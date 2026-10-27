package com.example.APIGateway.utils;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

// Auth v2 step 2: turns the env var jwtpublickey into AuthService's RSA PUBLIC key.
// Accepts the text of a PEM file ("-----BEGIN PUBLIC KEY-----" ...) or the same thing as one line of base64.
// Anything wrong - including a PRIVATE key put here by mistake - stops the gateway at start-up.
public final class RsaKeys {

    private static final int MIN_BITS = 2048;

    private RsaKeys() {
    }

    public static PublicKey publicKeyFrom(String text) {
        RSAPublicKey key;
        try {
            byte[] der = Base64.getDecoder().decode(onlyTheBase64(text));
            key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            // e is not attached: its message could repeat part of the key text.
            throw new IllegalStateException("jwt.public-key (env var jwtpublickey) is not a readable RSA public key."
                    + " Expected X.509: a PEM starting with -----BEGIN PUBLIC KEY-----, or the same as one line of base64.");
        }
        if (key.getModulus().bitLength() < MIN_BITS) {
            throw new IllegalStateException("jwt.public-key (env var jwtpublickey) is shorter than " + MIN_BITS + " bits.");
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
