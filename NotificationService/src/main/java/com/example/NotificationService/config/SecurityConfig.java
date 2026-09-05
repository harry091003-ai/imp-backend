package com.example.NotificationService.config;

import com.project.security.filter.HeaderAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    private final HeaderAuthFilter headerAuthFilter;
    // ---------------------------------------------------------------------------------
    // CHANGED 2026-09-02 (Track B1, step 3): this builder now sends two extra headers on
    // every call it makes.
    //
    //   X-Internal-Secret   the shared secret word - "I am allowed to talk to you"
    //   X-Internal-Service  our own service name   - "and I am a service, not a person"
    //
    // The service being called needs both before it will let us reach its /internal/**
    // endpoints. Without them we get a 403.
    //
    // WHY SET IT HERE, ON THE BUILDER, INSTEAD OF AT EACH CALL?
    // Because there is more than one call site today, and there will be more tomorrow. Set
    // it once here and it is impossible to forget on a new call. Set it at each call site
    // and forgetting one is a silent 403 that only shows up at runtime.
    //
    // IS IT SAFE TO PUT A SECRET ON EVERY CALL THIS BUILDER MAKES?
    // Yes, because of @LoadBalanced. That annotation means this client can only reach
    // services registered in Eureka - it resolves names like "USERSERVICE", not real web
    // addresses. So it physically cannot call an outside website, and the secret cannot
    // leak to a third party. If this builder ever loses @LoadBalanced, revisit this.
    //
    // The service name comes from spring.application.name, so it stays correct by itself
    // if the service is ever renamed.
    // ---------------------------------------------------------------------------------
    @Bean
    @LoadBalanced
    public WebClient.Builder LoaBalancedWebClientBuilder(
            @Value("${internal.secret}") String internalSecret,
            @Value("${spring.application.name}") String serviceName) {
        return WebClient.builder()
                .defaultHeader(HeaderAuthFilter.INTERNAL_SECRET_HEADER, internalSecret)
                .defaultHeader(HeaderAuthFilter.INTERNAL_SERVICE_HEADER, serviceName);
    }
    public SecurityConfig(HeaderAuthFilter headerAuthFilter) {
        this.headerAuthFilter = headerAuthFilter;
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //spring has all the filters
        // below are not the filter ,they are just the configuration of the filters ...
        // when a particular  filter run then it will takes  its configuration from here

        System.out.println("headerAuthFilter is:"+ headerAuthFilter);


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

