package com.example.AuthService.config;

import com.example.AuthService.service.CustomUserDetailsService;
//import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import com.example.AuthService.security.RsaKeys;
import com.project.security.filter.HeaderAuthFilter;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

import java.security.PrivateKey;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;
//import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity // enables Spring Security for the entire application.
public class SecurityConfig {
    // Auth v2 step 1: tokens are signed with AuthService's PRIVATE RSA key (RS256), no longer with the
    // shared secret. Only AuthService has this key, so only AuthService can create a token. The gateway
    // checks tokens with the matching PUBLIC key, which cannot create any.
    private final PrivateKey signingKey;
    private final String keyId;              // written into each token's header as "kid"
    private final long jwtExpirationMs;

    private final CustomUserDetailsService userDetailsService;
    private final HeaderAuthFilter headerAuthFilter;

    public SecurityConfig(CustomUserDetailsService userDetailsService,HeaderAuthFilter headerAuthFilter,
                          @Value("${jwt.private-key}") String privateKey,
                          @Value("${jwt.key-id}") String keyId,
                          @Value("${jwt.expirationMs}") long jwtExpirationMs) {
        this.userDetailsService = userDetailsService;
        this.headerAuthFilter=headerAuthFilter;
        // Read once, at start-up: a missing or broken key stops the service right away, not at the first login.
        this.signingKey = RsaKeys.privateKeyFrom(privateKey);
        this.keyId = keyId;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    // for generating token
    public String generateToken(String userid , Map<String,Object> claims) {
        Date now= new Date();
        Date ExpiryDate=new Date(now.getTime()+jwtExpirationMs);
        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(userid)//username is primary subject
                .claims(claims)//here claims is a map which contains the user roles and token version information
                .issuedAt(now)
                .expiration(ExpiryDate)
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // it tells how to authenticate the user as
    // 1.Your CustomUserDetailsService
    //   Loads user from DB
    //   Returns username, password, roles
    // 2.Your BCryptPasswordEncoder
    //      Validates password
    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider( userDetailsService);
//        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

//    This manager uses the DaoAuthenticationProvider you configured to:
//    1.Load user from DB
//    2.Check password
//    3.Throw exception if invalid
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //spring has all the filters
        // below are not the filter ,they are just the configuration of the filters ...
        // when a particular  filter run then it will takes  its configuration from here

        Logger.getLogger("Now our we are in Security filter chain of Authservice");
        http
                // JWT → stateless so CSRF not needed
                .csrf(AbstractHttpConfigurer::disable)

                // No HTTP sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)//Spring Security by default uses HTTP Sessions.But JWT works differently.so we do Not store session  in server.so we tell spring to Do NOT create sessions.
                )
                // Run JWT filter BEFORE authorization check (CRITICAL)
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/debug/**","/actuator/**").permitAll()
                        .requestMatchers("/roles/**").hasAuthority("ROLE_ADMIN") // ✅ ADD THIS
                        .anyRequest().authenticated()//ye line se authentication use karne ya na karne ka koi link nahi hai it check check karte hai ki SecurityContextHolder.getContext().getAuthentication() != null to nahi hai
                );
        return http.build();
    }
}
