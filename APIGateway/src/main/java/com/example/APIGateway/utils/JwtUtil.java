package com.example.APIGateway.utils;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.util.List;


// we have only 1 JwtUtil Files in this project now
//  this file  is used by the apigateway (JWTGatewayfilter ) for authetication only
//
// Auth v2 step 2 (2026-10-23): the gateway only CHECKS tokens, with AuthService's PUBLIC RSA key.
// A public key cannot sign, so the gateway can no longer create a token (the old shared secret could).
// The old generateToken, the secret and jwt.expirationMs are gone.

@Component
public class JwtUtil {

    private final PublicKey publicKey;

    public JwtUtil(@Value("${jwt.public-key}") String publicKey) {
        // Read once, at start-up: a missing or broken key stops the gateway right away.
        this.publicKey = RsaKeys.publicKeyFrom(publicKey);
    }

    // Checks the stamp with the public key, then reads the body. Throws for anything not genuine:
    // a wrong or edited stamp, an expired token, an unsigned token, or garbage.
    private Claims claims(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
        // Only RS256. Stops the "algorithm trick": an HS256 token signed with our public key used as a secret.
        if (!"RS256".equals(jws.getHeader().getAlgorithm())) {
            throw new JwtException("Only RS256 tokens are accepted");
        }
        return jws.getPayload();
    }

// userid
    public String getSubjectFromToken(String token) {
        return claims(token).getSubject();
    }

    public List<String> getRolesFromToken(String token) {
        return claims(token).get("roles", List.class);
    }

    public String getEmailFromToken(String token) {
        return claims(token).get("email", String.class);
    }

    public Integer getTokenVersion(String token) {
        return claims(token).get("tokenVersion", Integer.class);
    }

    // true only for a genuine RS256 token from AuthService that has not expired (the parser checks expiry).
    public boolean validateToken(String token) {
        try {
            claims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
