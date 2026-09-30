package com.example.UserService.config;
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
                        // These used to be permitAll, which meant ANYONE could call them with no
                        // login at all. They hand out user names, email addresses and whole team
                        // rosters - /internal/UserSpecificDetails/batch takes a list of user ids
                        // and returns everyone's email in one go.
                        //
                        // They are meant only for our other services (IncidentService and
                        // NotificationService) to call. So now they require the role that only a
                        // service can get: a caller must send the shared secret AND the
                        // X-Internal-Service header. See HeaderAuthFilter in common-security.
                        //
                        // A normal person can never reach these, because the gateway wipes the
                        // X-Internal-Service header off every request that comes in from outside.
                        .requestMatchers("/internal/**").hasRole("INTERNAL_SERVICE")

                        .requestMatchers("/users/**").hasAnyRole("ENGINEER", "SUPERVISOR","ADMIN") // Only users with ROLE_ENGINEER or ROLE_SUPERVISOR can access /users/**

                        .anyRequest().authenticated()//ye line se authentication use karne ya na karne ka koi link nahi hai it check check karte hai ki SecurityContextHolder.getContext().getAuthentication() != null to nahi hai
                );


        return http.build();
    }
}