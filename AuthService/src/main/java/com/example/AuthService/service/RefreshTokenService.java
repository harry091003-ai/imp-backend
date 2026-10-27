package com.example.AuthService.service;

import com.example.AuthService.dto.RefreshResponseDTO;
import com.example.AuthService.entity.RefreshToken;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.RefreshTokenRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.web.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens. Auth v2 part B (2026-10-27).
 *
 * Login gives a short access token (15 min) and a long refresh token (7 days). When the access token runs
 * out, the client sends the refresh token to POST /auth/refresh and gets a NEW pair.
 *
 * Rules:
 *   - only the SHA-256 hash is stored; the token itself is 32 random bytes, known only to the client
 *   - each refresh token works ONCE (rotation): refreshing uses it up and gives a new one
 *   - an already-used token sent again = someone copied it -> the whole family (that login) is cancelled
 *   - logout, password change and role change cancel all of the user's refresh tokens (LogoutUserService)
 *   - a locked account cannot refresh
 */
@Service
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final LoginAttemptService loginAttemptService;
    private final TransactionTemplate transactionTemplate;
    private final long lifetimeDays;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                               AccessTokenIssuer accessTokenIssuer, LoginAttemptService loginAttemptService,
                               TransactionTemplate transactionTemplate,
                               @Value("${auth.refresh-token.lifetime-days:7}") long lifetimeDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.accessTokenIssuer = accessTokenIssuer;
        this.loginAttemptService = loginAttemptService;
        this.transactionTemplate = transactionTemplate;
        this.lifetimeDays = lifetimeDays;
    }

    /** Login: a new refresh token that starts a new family. Runs inside the login's transaction. */
    public String issueForLogin(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    /** POST /auth/refresh: swap a refresh token for a new access token + a new refresh token. */
    public RefreshResponseDTO refresh(String refreshToken) {
        // The answer comes back from the transaction and the 401 is thrown AFTER it has committed.
        // Why: when reuse is found, cancelling the family must be SAVED. A throw inside the transaction
        // would roll that back (the same trap as issue 55).
        Outcome outcome = transactionTemplate.execute(status -> swap(refreshToken));
        if (outcome.errorCode() != null) {
            throw new UnauthorizedException(outcome.errorCode(), outcome.message());
        }
        return outcome.answer();
    }

    private Outcome swap(String refreshToken) {
        RefreshToken row = refreshTokenRepository.findByTokenHashForUpdate(hash(refreshToken)).orElse(null);
        // Unknown and cancelled get the same answer: the caller learns nothing about which tokens exist.
        if (row == null || row.getRevokedAt() != null) {
            return Outcome.refused("INVALID_REFRESH_TOKEN", "Refresh token is not valid. Please log in again.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (row.getUsedAt() != null) {
            // Used before = two parties hold it (the real user and a copy). We cannot tell which one is
            // calling, so the whole login is cancelled and both have to log in again.
            refreshTokenRepository.revokeFamily(row.getFamilyId(), now);
            log.warn("Refresh token reused for user {} - all refresh tokens of that login cancelled", row.getUserId());
            return Outcome.refused("REFRESH_TOKEN_REUSED", "Refresh token was already used. Please log in again.");
        }
        if (!now.isBefore(row.getExpiresAt())) {
            return Outcome.refused("REFRESH_TOKEN_EXPIRED", "Refresh token has expired. Please log in again.");
        }
        User user = userRepository.findById(row.getUserId()).orElse(null);
        if (user == null) {
            return Outcome.refused("INVALID_REFRESH_TOKEN", "Refresh token is not valid. Please log in again.");
        }
        // Nothing is used up here: once the lock ends, the same refresh token works again.
        if (loginAttemptService.isLockedNow(user)) {
            return Outcome.refused("ACCOUNT_LOCKED",
                    "Account is locked due to multiple failed login attempts. Try again later.");
        }

        row.setUsedAt(now);
        refreshTokenRepository.save(row);
        String newRefreshToken = issue(user.getUserid(), row.getFamilyId());
        String accessToken = accessTokenIssuer.issueFor(user);
        log.debug("User {} refreshed their tokens", user.getUserid());
        return Outcome.ok(RefreshResponseDTO.builder()
                .userid(user.getUserid())
                .JWTtoken(accessToken)
                .refreshToken(newRefreshToken)
                .build());
    }

    private String issue(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .familyId(familyId)
                .tokenHash(hash(token))
                .createdAt(now)
                .expiresAt(now.plusDays(lifetimeDays))
                .build());
        return token;
    }

    // A plain SHA-256 is enough here (no BCrypt): the token is 32 random bytes, far too many to guess,
    // so there is no short password to protect from guessing. It also lets us find the row by its hash.
    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this Java", e);
        }
    }

    private record Outcome(RefreshResponseDTO answer, String errorCode, String message) {
        static Outcome ok(RefreshResponseDTO answer) {
            return new Outcome(answer, null, null);
        }

        static Outcome refused(String errorCode, String message) {
            return new Outcome(null, errorCode, message);
        }
    }
}
