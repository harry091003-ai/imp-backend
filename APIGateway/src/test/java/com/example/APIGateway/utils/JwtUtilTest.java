package com.example.APIGateway.utils;

import com.example.APIGateway.TestKeys;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Key;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auth v2 step 2: the gateway CHECKS tokens with AuthService's PUBLIC key, and nothing else.
 *
 * Plain Java, no Spring. The tests make tokens the way AuthService does (RS256, same claims),
 * then try every way a fake one could get in.
 */
class JwtUtilTest {

    private static final String USER = "4d0c2f6e-1111-2222-3333-444455556666";

    private final JwtUtil jwtUtil = new JwtUtil(TestKeys.publicKeyText());

    /** A token shaped exactly like AuthService's login token. */
    private static String token(PrivateKey signer, List<String> roles, long expiresInMs) {
        Date now = new Date();
        return Jwts.builder()
                .header().keyId("key-1").and()
                .subject(USER)
                .claims(Map.of("roles", roles, "tokenVersion", 3, "email", "pat@example.com"))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInMs))
                .signWith(signer, Jwts.SIG.RS256)
                .compact();
    }

    private static String realToken() {
        return token(TestKeys.PAIR.getPrivate(), List.of("ROLE_ENGINEER"), 60_000);
    }

    // ------------------------------------------------------------------ the normal case

    @Test
    void aRealTokenIsAcceptedAndItsClaimsAreRead() {
        String token = realToken();

        assertTrue(jwtUtil.validateToken(token));
        assertEquals(USER, jwtUtil.getSubjectFromToken(token));
        assertEquals("pat@example.com", jwtUtil.getEmailFromToken(token));
        assertEquals(List.of("ROLE_ENGINEER"), jwtUtil.getRolesFromToken(token));
        assertEquals(3, jwtUtil.getTokenVersion(token));
    }

    // ------------------------------------------------------------------ every way a fake could get in

    @Test
    void aTokenWhoseRolesWereEditedIsRefused() {
        // Take the body of a token that says ADMIN, and glue it to the stamp of a real ENGINEER token.
        String[] engineer = realToken().split("\\.");
        String[] admin = token(TestKeys.newPair(2048).getPrivate(), List.of("ROLE_ADMIN"), 60_000).split("\\.");
        String edited = engineer[0] + "." + admin[1] + "." + engineer[2];

        assertFalse(jwtUtil.validateToken(edited), "the stamp no longer matches the body");
    }

    @Test
    void aTokenSignedWithAnyOtherPrivateKeyIsRefused() {
        String fake = token(TestKeys.newPair(2048).getPrivate(), List.of("ROLE_ADMIN"), 60_000);

        assertFalse(jwtUtil.validateToken(fake));
    }

    @Test
    void anExpiredTokenIsRefused() {
        String old = token(TestKeys.PAIR.getPrivate(), List.of("ROLE_ENGINEER"), -60_000);

        assertFalse(jwtUtil.validateToken(old));
    }

    @Test
    void theAlgorithmTrickIsRefused() {
        // The classic attack on RSA tokens: the public key is public, so an attacker uses its bytes as an
        // HMAC SECRET, signs a token with HS256 and hopes the gateway checks it "with the key it has".
        String trick = Jwts.builder()
                .subject(USER)
                .claims(Map.of("roles", List.of("ROLE_ADMIN"), "tokenVersion", 3, "email", "pat@example.com"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(TestKeys.PAIR.getPublic().getEncoded()), Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtUtil.validateToken(trick), "only RS256 is accepted");
    }

    @Test
    void anUnsignedTokenIsRefused() {
        String unsigned = Jwts.builder()
                .subject(USER)
                .claims(Map.of("roles", List.of("ROLE_ADMIN")))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();   // no signWith: "alg": "none"

        assertFalse(jwtUtil.validateToken(unsigned));
    }

    @Test
    void garbageIsRefused() {
        assertFalse(jwtUtil.validateToken("abc"));
        assertFalse(jwtUtil.validateToken("a.b.c"));
        assertFalse(jwtUtil.validateToken(""));
    }

    // ------------------------------------------------------------------ the gateway can only CHECK

    @Test
    void theGatewayHasNoWayToCreateATokenAnyMore() {
        for (Method method : JwtUtil.class.getDeclaredMethods()) {
            assertFalse(method.getName().toLowerCase().contains("generate"), "no token maker: " + method.getName());
        }
        for (Field field : JwtUtil.class.getDeclaredFields()) {
            boolean isAKey = Key.class.isAssignableFrom(field.getType());
            boolean isPublicKey = java.security.PublicKey.class.isAssignableFrom(field.getType());
            assertFalse(isAKey && !isPublicKey, "the only key it may hold is a PUBLIC key: " + field.getName());
        }
        assertTrue(Arrays.stream(JwtUtil.class.getDeclaredFields())
                .noneMatch(f -> f.getName().toLowerCase().contains("secret")), "no shared secret any more");
    }

    // ------------------------------------------------------------------ reading the key from the setting

    @Test
    void garbageInTheSettingStopsTheGatewayWithAClearMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JwtUtil("not-a-key"));

        assertTrue(error.getMessage().contains("jwtpublickey"), "says which setting is wrong");
        assertFalse(error.getMessage().contains("not-a-key"), "never prints the key text");
    }

    @Test
    void thePrivateKeyPutWhereThePublicKeyBelongsIsRefused() {
        // The gateway must never be given the private key, even by mistake.
        assertThrows(IllegalStateException.class, () -> new JwtUtil(TestKeys.privateKeyText()));
    }
}
