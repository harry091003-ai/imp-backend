package com.project.security.config;
import com.project.security.filter.HeaderAuthFilter;
//import com.project.security.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityAutoConfig {

//    @Bean
//    public JwtUtil jwtUtil() {
//        return new JwtUtil();
//    }
    @Bean
    public HeaderAuthFilter headerAuthFilter(){
        return new HeaderAuthFilter();
    }
}
