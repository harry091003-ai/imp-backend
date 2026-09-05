package com.example.Ai_Service.config;

import com.project.security.filter.HeaderAuthFilter;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds our two internal headers to every Feign call this service makes.
 *
 * ADDED 2026-09-02 (Track B1, step 3).
 *
 * WHY THIS FILE EXISTS AT ALL
 * The other services (IncidentService, NotificationService) call each other with WebClient, and
 * there you can attach a default header straight onto the builder. Ai-Service is the only one
 * that uses Feign, and Feign works differently: you do not build the request yourself, you just
 * describe it as a Java interface (see ReportingServiceFeignClient) and Feign writes the calling
 * code for you.
 *
 * So there is no builder to attach anything to. Instead Feign gives you a "RequestInterceptor" -
 * a small piece of code it runs on every outgoing request, just before sending it. That is where
 * we add the headers.
 *
 * Same idea as the WebClient change, different hook. Both mean: set it once, and it is impossible
 * to forget when somebody adds a new call later.
 *
 * WHAT THE TWO HEADERS MEAN
 *   X-Internal-Secret   the shared secret word - "I am allowed to talk to you"
 *   X-Internal-Service  our own service name   - "and I am a service, not a person"
 *
 * ReportingService needs both before it will let us reach /internal/reports/**.
 *
 * WHY THIS IS A @Bean AND NOT A @FeignClient SETTING
 * Declaring the interceptor as a plain @Bean applies it to EVERY Feign client in this service,
 * including any added later. Attaching it to one client instead would mean remembering to do it
 * again next time - the same trap we avoided on the WebClient side.
 */
@Configuration
public class FeignInternalAuthConfig {

    @Bean
    public RequestInterceptor internalAuthInterceptor(
            @Value("${internal.secret}") String internalSecret,
            @Value("${spring.application.name}") String serviceName) {

        // requestTemplate is the outgoing request, just before it is sent.
        // We are adding to it, not replacing anything it already has.
        return requestTemplate -> {
            requestTemplate.header(HeaderAuthFilter.INTERNAL_SECRET_HEADER, internalSecret);
            requestTemplate.header(HeaderAuthFilter.INTERNAL_SERVICE_HEADER, serviceName);
        };
    }
}
