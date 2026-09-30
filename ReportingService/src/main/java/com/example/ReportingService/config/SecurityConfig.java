package com.example.ReportingService.config;

import com.project.security.filter.HeaderAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final HeaderAuthFilter headerAuthFilter;
    @Bean
    @LoadBalanced
    public WebClient.Builder LoaBalancedWebClientBuilder() {
        return WebClient.builder();
    }
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
                        // Health checks stay open. They give away nothing.
                        .requestMatchers("/actuator/**").permitAll()

                        // CHANGED 2026-09-02 (Track B1, step 3).
                        // Service-to-service only. Ai-Service calls these to decide which
                        // engineer to suggest for an incident. Used to be permitAll.
                        //
                        // NOTE: this rule must stay ABOVE the /reports/** rule below. Spring
                        // checks these in order and the first match wins. (These two do not
                        // actually overlap - "/internal/reports/..." does not match "/reports/**"
                        // - but keeping the more specific rule first is the habit that stops a
                        // real mistake later.)
                        .requestMatchers("/internal/**").hasRole("INTERNAL_SERVICE")

                        // CHANGED 2026-09-02 (Track B1, step 3).
                        // This was permitAll, which meant this service's ENTIRE public API was
                        // open with no login: every incident report, all the metrics, the full
                        // audit log, and every engineer's performance numbers.
                        //
                        // Now only admins and supervisors. Engineers are deliberately left out -
                        // these endpoints show everyone's performance statistics, and one
                        // engineer should not be browsing another engineer's numbers.
                        //
                        // This also quietly closes a side door: ReportingController repeats two
                        // of the internal endpoints at /reports/engineer-performance/... and
                        // /reports/engineer-workload/... . Locking /internal/** alone would have
                        // left the same data reachable through this second path.
                        .requestMatchers("/reports/**").hasAnyRole("ADMIN", "SUPERVISOR")
                        .anyRequest().authenticated()//ye line se authentication use karne ya na karne ka koi link nahi hai it check check karte hai ki SecurityContextHolder.getContext().getAuthentication() != null to nahi hai
                );


        return http.build();
    }
}

