package com.example.NotificationService.config;

import com.project.security.filter.HeaderAuthFilter;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds our two internal headers to every Feign call this service makes.
 *
 * MOVED HERE 2026-09-09 (Track B3, step 2).
 *
 * These headers are not new - they were added in Track B1 step 3. What changed is only WHERE
 * they are attached. They used to sit on the WebClient.Builder bean inside SecurityConfig:
 *
 *     WebClient.builder()
 *         .defaultHeader(INTERNAL_SECRET_HEADER, internalSecret)
 *         .defaultHeader(INTERNAL_SERVICE_HEADER, serviceName);
 *
 * We do not build the request ourselves any more - Feign does (see client/UserClient). So
 * there is no builder left to attach anything to. Feign's equivalent hook is a
 * "RequestInterceptor": a small piece of code it runs on every outgoing request, just before
 * sending it.
 *
 * Same idea, different hook. This is a copy of the one Ai-Service has already had since
 * Track B1 - which is a fair point against this switch: we now have this file three times
 * instead of two. That is a real cost, accepted because the alternative (two different HTTP
 * clients to understand) was judged worse.
 *
 * WHAT THE TWO HEADERS MEAN
 *   X-Internal-Secret   the shared secret word - "I am allowed to talk to you"
 *   X-Internal-Service  our own service name   - "and I am a service, not a person"
 *
 * UserService needs BOTH before it will let us reach /internal/**. Without them we get a 403.
 *
 * WORTH KNOWING: this service's calls come from a KAFKA LISTENER, not from a web request. So
 * there is no logged-in person behind them at all - this service is acting entirely as itself.
 * That is exactly what X-Internal-Service is for, and it is why these calls could never have
 * worked by forwarding a user's identity instead.
 *
 * IS IT SAFE TO PUT A SECRET ON EVERY CALL?
 * Yes. Our @FeignClient uses a Eureka NAME ("USERSERVICE"), not a web address, so it
 * physically cannot call an outside website - there is nowhere for the secret to leak to. If
 * a Feign client is ever added here with a real url = "http://..." instead of a name, this
 * reasoning stops holding and must be revisited.
 *
 * WHY A PLAIN @Bean AND NOT A PER-CLIENT SETTING
 * A plain @Bean applies to EVERY Feign client in this service, including ones added later.
 * Attaching it to one client instead would mean remembering to do it again next time - and
 * forgetting would be a silent 403 that only appears at runtime.
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
