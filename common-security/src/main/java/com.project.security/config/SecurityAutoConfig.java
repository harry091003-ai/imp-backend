package com.project.security.config;
import com.project.security.filter.HeaderAuthFilter;
//import com.project.security.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityAutoConfig {

//    @Bean
//    public JwtUtil jwtUtil() {
//        return new JwtUtil();
//    }

    /**
     * Builds the filter that reads the user and roles from the request headers.
     *
     * CHANGED 2026-09-02 (Track B1, step 2):
     * The filter now needs the shared secret word, so we hand it in here.
     *
     * The value comes from internal.secret in application.properties, which in turn reads the
     * internalsecret environment variable.
     *
     * There is NO default value on purpose. If the variable is missing, the service refuses to
     * start with a clear message. That is what we want for a security setting: a loud failure at
     * startup is far better than a service that quietly starts up and lets everybody in.
     */
    @Bean
    public HeaderAuthFilter headerAuthFilter(@Value("${internal.secret}") String internalSecret) {
        return new HeaderAuthFilter(internalSecret);
    }
}
