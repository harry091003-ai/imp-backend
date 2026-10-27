package com.example.AuthService.security;

import com.example.AuthService.TestKeys;
import com.example.AuthService.config.SecurityConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auth v2 step 1: AuthService signs login tokens with its PRIVATE RSA key (RS256).
 *
 * Plain Java, no Spring: we build SecurityConfig ourselves and call the real generateToken,
 * the same method LoginUserService calls at login.
 */
class TokenSigningTest {

    private static final long TWO_HOURS = 7_200_000L;

    private final SecurityConfig securityConfig =
            new SecurityConfig(null, null, TestKeys.privateKeyText(), "key-1", TWO_HOURS);

    private String loginToken() {
        return securityConfig.generateToken("4d0c2f6e-1111-2222-3333-444455556666", Map.of(
                "roles", List.of("ROLE_ENGINEER"),
                "tokenVersion", 3,
                "email", "pat@example.com"));
    }

    private Jws<Claims> verifyWith(KeyPair pair, String token) {
        return Jwts.parser().verifyWith(pair.getPublic()).build().parseSignedClaims(token);
    }

    // ------------------------------------------------------------------ signing

    @Test
    void theTokenIsSignedWithRs256AndCarriesTheKeyId() {
        Jws<Claims> jws = verifyWith(TestKeys.PAIR, loginToken());

        assertEquals("RS256", jws.getHeader().getAlgorithm());
        assertEquals("key-1", jws.getHeader().getKeyId(), "the kid lets a later key change run two keys side by side");
    }

    @Test
    void theMatchingPublicKeyReadsEveryClaimTheGatewayNeeds() {
        Claims claims = verifyWith(TestKeys.PAIR, loginToken()).getPayload();

        assertEquals("4d0c2f6e-1111-2222-3333-444455556666", claims.getSubject());
        assertEquals(List.of("ROLE_ENGINEER"), claims.get("roles", List.class));
        assertEquals(3, claims.get("tokenVersion", Integer.class));
        assertEquals("pat@example.com", claims.get("email", String.class));
        long lifetime = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(Math.abs(lifetime - TWO_HOURS) < 2_000, "expires 2 hours after it was made");
    }

    @Test
    void aDifferentPublicKeyRejectsTheToken() {
        KeyPair someoneElse = TestKeys.newPair(2048);

        assertThrows(Exception.class, () -> verifyWith(someoneElse, loginToken()),
                "only the matching public key may accept it");
    }

    // ------------------------------------------------------------------ reading the key from the setting

    @Test
    void aKeyWrittenAsAPemFileWithLineBreaksIsAcceptedToo() {
        String base64 = TestKeys.privateKeyText();
        StringBuilder pem = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        pem.append("-----END PRIVATE KEY-----\n");

        assertEquals(TestKeys.PAIR.getPrivate(), RsaKeys.privateKeyFrom(pem.toString()));
    }

    @Test
    void garbageStopsTheServiceWithAClearMessageThatDoesNotRepeatTheKey() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> RsaKeys.privateKeyFrom("not-a-key-at-all"));

        assertTrue(error.getMessage().contains("jwtprivatekey"), "says which setting is wrong");
        assertFalse(error.getMessage().contains("not-a-key-at-all"), "never prints the key text");
    }

    @Test
    void anEmptySettingIsRefused() {
        assertThrows(IllegalStateException.class, () -> RsaKeys.privateKeyFrom("   "));
    }

    @Test
    void aKeyShorterThan2048BitsIsRefused() {
        String weak = Base64.getEncoder().encodeToString(TestKeys.newPair(1024).getPrivate().getEncoded());

        assertThrows(IllegalStateException.class, () -> RsaKeys.privateKeyFrom(weak));
    }

    @Test
    void thePublicKeyPutWhereThePrivateKeyBelongsIsRefused() {
        // An easy mistake when setting the env vars: swapping the two keys.
        assertThrows(IllegalStateException.class, () -> RsaKeys.privateKeyFrom(TestKeys.publicKeyText()));
    }
}
