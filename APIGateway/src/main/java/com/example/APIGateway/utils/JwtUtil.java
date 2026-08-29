package com.example.APIGateway.utils;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;


// we have only 1 JwtUtil Files in this project now
//  this file  is used by the apigateway (JWTGatewayfilter ) for authetication only

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.expirationMs}")
    private long jwtExpirationMs;


//------below is used for token generation in our Authservice----


    //    this method convert a raw byte array into a secure SecretKey object
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // the good approach is to pass the entire  UserDetails Object instead of user email
    public String generateToken(String userid , Map<String,Object> claims) {
        Date now= new Date();
        Date ExpiryDate=new Date(now.getTime()+jwtExpirationMs);
        return Jwts.builder()
                .subject(userid)//username is basically an email in our case
                .claims(claims)//here claims is a map which contains the user roles and token version information
                .issuedAt(now)
                .expiration(ExpiryDate)
                .signWith(getSigningKey())
                .compact();
    }

// ---below part is used for Authentication in all the services---------
// userid
    public String getSubjectFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public List<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("roles", List.class);
    }
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("email", String.class);
    }


    public Integer getTokenVersion(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("tokenVersion", Integer.class);
    }
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);

            return !isTokenExpired(token);

        } catch (Exception e) {
            return false;
        }
    }
//    public boolean validateTokenWithVersion(String token) {
//
//    }

    public Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}