package com.example.AuthService.service;

import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Counts wrong passwords and locks the account (issues 55 and 6).
 * Why a separate class: the login throws a 401, which rolls back the login's own transaction.
 *   So the count is saved here, in its OWN transaction (REQUIRES_NEW), before that happens.
 *   Spring only applies @Transactional to calls coming from another class.
 * Rules: each wrong password +1; at 5 -> locked for 15 minutes; after that, counting starts again;
 *   a right password -> back to 0 (done in LoginUserService).
 */
@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final int maxAttempts;
    private final long lockMinutes;

    public LoginAttemptService(UserRepository userRepository,
                               @Value("${auth.lockout.max-attempts:5}") int maxAttempts,
                               @Value("${auth.lockout.lock-minutes:15}") long lockMinutes) {
        this.userRepository = userRepository;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    /** Is this account locked RIGHT NOW? A lock whose time has passed does not count. */
    public boolean isLockedNow(User user) {
        if (!Boolean.TRUE.equals(user.getAccountLocked())) {
            return false;
        }
        // An old lock with NO end time (set before this fix existed) is treated as finished. Without this
        // rule those accounts would stay locked forever - which was known issue 6.
        return user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil());
    }

    /** Count one wrong password. Saved in its OWN transaction, so the failed login cannot undo it. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailedLogin(String email) {
        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user == null) {
            return;   // unknown email: nothing to count (the caller gives the same 401 anyway)
        }
        if (Boolean.TRUE.equals(user.getAccountLocked()) && !isLockedNow(user)) {
            // The old lock has run out: start counting again from 0, otherwise ONE more wrong
            // password would lock the account again at once.
            user.setAccountLocked(false);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= maxAttempts) {
            user.setAccountLocked(true);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            log.warn("User {} locked for {} minutes after {} failed logins", user.getUserid(), lockMinutes,
                    user.getFailedLoginAttempts());
        }
        userRepository.save(user);
    }
}
