package com.project.security.filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

// This filter is used by authservice, userservice and later many more (from this central security-module)
// It reads the userid, email and roles from headers coming from ApiGatewayFilter (in the APIGateway
// service) and puts them into the SecurityContextHolder, so we can use the user and roles wherever needed.
//
// ---------------------------------------------------------------------------------------------
// ADDED 2026-09-02 (Track B1, step 2): the secret handshake
//
// The problem we are fixing:
// This filter used to believe the X-User-Id and X-Roles headers no matter who sent them. That is
// fine while only the gateway can reach us. But if anyone else ever reaches this service, they can
// simply type "X-Roles: ROLE_ADMIN" in a request and we would believe it. No password needed.
//
// The fix:
// The gateway now sends a secret word with every request. We check that word first. No word, or the
// wrong word, means the request did not come from our gateway, so we ignore its headers completely.
//
// What "ignore" means here:
// We do NOT reject the request ourselves. We just do not log anybody in. The request carries on with
// no user attached, and each service's own SecurityConfig decides what to do. Protected endpoints
// then reject it, and open ones (like /actuator/health) still work. This keeps all the "who is
// allowed where" rules in one place, instead of splitting them between here and SecurityConfig.
//
// This is our second lock, not our first. The first is that services only listen on 127.0.0.1
// (step 1). If that one is ever undone by mistake, this one still holds.
// ---------------------------------------------------------------------------------------------
public class HeaderAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = Logger.getLogger(HeaderAuthFilter.class.getName());

    /** The header the gateway puts the secret word in. */
    public static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    /**
     * ADDED 2026-09-02 (Track B1, step 3).
     *
     * The header a SERVICE uses to say "I am a service, not a person".
     * For example: X-Internal-Service: INCIDENTSERVICE
     *
     * Why a second header, when the secret alone proves the caller is one of us?
     * Because the gateway sends the secret too. If the secret alone granted the service role,
     * then every ordinary request coming through the gateway - even a plain login - would count
     * as a service. This header is what separates "a person came through the front door" from
     * "another one of our services is calling me directly".
     *
     * The gateway never sends this header, and it wipes it off any incoming request, so a
     * caller from outside cannot pretend to be a service.
     */
    public static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service";

    /**
     * The role we give a service caller. Used in SecurityConfig like this:
     *     .requestMatchers("/internal/**").hasRole("INTERNAL_SERVICE")
     *
     * Spring adds the "ROLE_" prefix itself when you write hasRole("INTERNAL_SERVICE"),
     * which is why the text here has the prefix but the rule does not.
     */
    public static final String INTERNAL_SERVICE_ROLE = "ROLE_INTERNAL_SERVICE";

    /** The secret word this service expects. Comes from the internal.secret setting. */
    private final String expectedSecret;

    public HeaderAuthFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: did this request come from our gateway?
        if (!hasValidSecret(request)) {
            // Deliberately vague log. We do not print what was sent, or what we expected.
            // Anything we print here could end up in a log file somebody else can read.
            logger.warning("Request to " + request.getRequestURI()
                    + " did not have a valid " + INTERNAL_SECRET_HEADER
                    + " header. Ignoring its identity headers.");
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2: the secret was good. Now work out WHO is calling.
        //
        // There are two kinds of caller, and they look different:
        //
        //   A PERSON  - came through the gateway. Carries X-User-Id and X-Roles.
        //   A SERVICE - one of our own services calling us directly, with no person involved.
        //               Carries X-Internal-Service instead.
        //
        // Neither one sends the other's headers, so in practice they never overlap. We check for
        // a service first because it is the more specific claim - only our own services send it.
        String serviceName = request.getHeader(INTERNAL_SERVICE_HEADER);
        String userId = request.getHeader("X-User-Id");
        String rolesHeader = request.getHeader("X-Roles");

        if (serviceName != null && !serviceName.isBlank()) {
            // A service is calling. There is no person here, so there are no user roles - just
            // the one role that says "I am an internal service". SecurityConfig decides what
            // that role is allowed to reach (today: the /internal/** endpoints).
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            serviceName, null,
                            List.of(new SimpleGrantedAuthority(INTERNAL_SERVICE_ROLE)));

            SecurityContextHolder.getContext().setAuthentication(auth);
            logger.info("Internal service call from " + serviceName
                    + " to " + request.getRequestURI());

        } else if (userId != null && rolesHeader != null) {
            // A person is calling, through the gateway.
            List<SimpleGrantedAuthority> authorities =
                    Arrays.stream(rolesHeader.split(","))
                            .map(SimpleGrantedAuthority::new)
                            .toList();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(auth);
            logger.info("Logged in user " + userId + " with roles " + authorities);

        } else {
            // Nobody is logged in. Normal for open pages like login, signup and
            // /actuator/health. Protected pages will reject this request.
            logger.fine("No identity headers on " + request.getRequestURI()
                    + " - nobody logged in for this request.");
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Compares the secret sent with the one we expect.
     *
     * Why MessageDigest.isEqual instead of a plain equals()?
     * A normal String comparison stops as soon as it finds the first character that differs. So a
     * word starting with the right letter takes very slightly longer to reject than one starting
     * with a wrong letter. Someone measuring those tiny differences over many tries could work out
     * the secret one letter at a time. This is called a timing attack.
     *
     * MessageDigest.isEqual always looks at every byte, so every wrong answer takes the same time
     * and there is nothing to measure. It costs us nothing to be careful here.
     */
    private boolean hasValidSecret(HttpServletRequest request) {
        String sent = request.getHeader(INTERNAL_SECRET_HEADER);
        if (sent == null || expectedSecret == null || expectedSecret.isBlank()) {
            return false;
        }
//   I used MessageDigest.isEqual, not equals. A normal string comparison stops at the first wrong character.
//   So a guess starting with the right letter takes a tiny bit longer to reject. Measured over thousands of tries,
//   that leaks the secret one letter at a time — a timing attack. MessageDigest.isEqual always checks every byte, so there's nothing to measure. Free to do, so I did it.
        return MessageDigest.isEqual(
                sent.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
