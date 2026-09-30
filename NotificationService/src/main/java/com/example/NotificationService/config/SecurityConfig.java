package com.example.NotificationService.config;

import com.project.security.filter.HeaderAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final HeaderAuthFilter headerAuthFilter;

    // ---------------------------------------------------------------------------------
    // REMOVED 2026-09-09 (Track B3, step 2): the @LoadBalanced WebClient.Builder bean
    // that used to sit here.
    //
    // It did two jobs, and both now live somewhere better:
    //
    //   1. Attaching X-Internal-Secret and X-Internal-Service to every outgoing call
    //         -> config/FeignInternalAuthConfig.java
    //   2. Turning a Eureka name like "USERSERVICE" into a real address
    //         -> Feign does this by itself, no bean needed
    //
    // Two things worth noticing about this removal:
    //
    //   - Feign does the load-balancing wiring on its own, so this whole bean simply has
    //     no replacement. One fewer thing to declare, and one fewer thing to get wrong.
    //
    //   - It should never have been in SecurityConfig in the first place. An HTTP client
    //     is not a security setting. It ended up here in three other services too, and is
    //     recorded as known issue #35 in CLAUDE.md. This service is now clear of it.
    //
    // This file is back to doing only what its name says: security configuration.
    // ---------------------------------------------------------------------------------

    public SecurityConfig(HeaderAuthFilter headerAuthFilter) {
        this.headerAuthFilter = headerAuthFilter;
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //spring has all the filters
        // below are not the filter ,they are just the configuration of the filters ...
        // when a particular  filter run then it will takes  its configuration from here

        // C1 step 1: was System.out.println of the filter object (it only printed a memory address).
        // A startup detail, so debug - hidden unless you turn debug logging on.
        log.debug("HeaderAuthFilter added to the security chain");


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
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()//ye line se authentication use karne ya na karne ka koi link nahi hai it check check karte hai ki SecurityContextHolder.getContext().getAuthentication() != null to nahi hai
                );


        return http.build();
    }
}

