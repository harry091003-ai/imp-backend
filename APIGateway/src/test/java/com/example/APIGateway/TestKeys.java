package com.example.APIGateway;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Throwaway RSA key pairs for tests (Auth v2). Made in memory while the tests run:
 * nothing is written to disk, nothing is a real key.
 */
public final class TestKeys {

    /** One pair for the whole test run: the private half plays "AuthService", the public half the gateway's. */
    public static final KeyPair PAIR = newPair(2048);

    private TestKeys() {
    }

    public static KeyPair newPair(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The public key as one line of base64 (X.509) - the shape the env var jwtpublickey holds. */
    public static String publicKeyText() {
        return Base64.getEncoder().encodeToString(PAIR.getPublic().getEncoded());
    }

    /** The private key as one line of base64 - only used to prove the gateway refuses it. */
    public static String privateKeyText() {
        return Base64.getEncoder().encodeToString(PAIR.getPrivate().getEncoded());
    }
}
