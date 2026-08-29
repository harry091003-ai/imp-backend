package com.project.security.filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

// this filter is used by authservice,userservice and later many more(from this centeral security-module)
// used for getting the userid,email and roles from header comming from ApiGateFilter (present in ApiGAteway Service) and then setting in to SecurityContextHolder..such that we can use the roles and users when every needed
@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = Logger.getLogger(HeaderAuthFilter.class.getName());

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        logger.info("🔍 HeaderAuthFilter executing for path: " + request.getRequestURI());

        String userId = request.getHeader("X-User-Id");
        String rolesHeader = request.getHeader("X-Roles");
        String email = request.getHeader("X-User-Email");
        logger.info("📋 X-Userid header: " + userId);
        logger.info("📋 X-Roles header: " + rolesHeader);
        logger.info("📋 X-User-Email: " + email);

        if (userId != null && rolesHeader != null) {
            List<SimpleGrantedAuthority> authorities =
                    Arrays.stream(rolesHeader.split(","))
                            .map(SimpleGrantedAuthority::new)
                            .toList();

            logger.info("✅ Parsed authorities: " + authorities);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);
            logger.info("✅ Authentication set in SecurityContext for user: " + userId + " with authorities: " + authorities);
        } else {
            logger.warning("⚠️ Missing X-User or X-Roles header - Authentication not set");
        }

        filterChain.doFilter(request, response);
    }
}