//package com.example.AuthService.util;
//
//
//import io.jsonwebtoken.Jwts;
//
//import io.jsonwebtoken.io.Decoders;
//import io.jsonwebtoken.security.Keys;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.stereotype.Component;
//
//import javax.crypto.SecretKey;
//import java.util.Date;
//import java.util.List;
//
//@Component
//public class JwtUtil {
//
//    @Value("${jwt.secret}")
//    private String jwtSecret;
//
//    @Value("${jwt.expirationMs}")
//    private long jwtExpirationMs;
////    this method convert a raw byte array into a secure SecretKey object
//    private SecretKey getSigningKey() {
//        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
//        return Keys.hmacShaKeyFor(keyBytes);
//    }
//
//
//    // initially for making the token weare passing just the email as parameter like public String generateToken(String email) {}
//    // But the good approach is to pass the entire  UserDetails Object
//    public String generateToken(UserDetails userDetails) {
//          List<String> roles = userDetails.getAuthorities()
//                .stream()
//                .map(GrantedAuthority::getAuthority)
//                .toList();
//        Date now= new Date();
//        Date ExpiryDate=new Date(now.getTime()+jwtExpirationMs);
//        return Jwts.builder().
//                subject(userDetails.getUsername()).
//                claim("roles",roles).//we also add role in our token so that we can use it when we want
//                issuedAt(now).
//                expiration(ExpiryDate).
//                signWith(getSigningKey()).compact();
//    }
//
//
//
//}
