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
 * Known issue 55 + issue 6 (2026-10-14): the account lockout.
 *
 * WHY THIS IS A SEPARATE CLASS
 * LoginUserService is @Transactional. On a wrong password it THROWS a 401, and a thrown error rolls the
 * whole transaction back - so the "+1 failed attempt" it had just saved was undone every time. The counter
 * stayed 0 and the lock never happened (proved by AccountLockoutTest before this fix).
 *
 * recordFailedLogin() runs in its OWN transaction (REQUIRES_NEW): it is saved and finished before the login
 * throws. The login's rollback can no longer undo it. It must be in another class: Spring only applies
 * @Transactional when the call comes from OUTSIDE the class.
 *
 * THE RULES
 *   - each wrong password: +1
 *   - at maxAttempts (5): locked until now + lockMinutes (15)
 *   - after that time: the user may try again, and the counter starts from 0
 *   - a right password: everything back to 0 (done in LoginUserService)
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
