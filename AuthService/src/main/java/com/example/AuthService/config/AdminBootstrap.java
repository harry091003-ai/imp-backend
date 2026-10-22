package com.example.AuthService.config;

import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.service.UpdateRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Issue 67: makes the FIRST admin at start-up, instead of a hand edit in MySQL.
//
// How to use: sign up normally, set the env var `adminemail` to that account's email, start AuthService.
//   - no admin yet      -> that account becomes ADMIN (through UpdateRoleService: roles.update is sent, audit row saved)
//   - admin exists      -> nothing happens (so a setting added later cannot create a new admin)
//   - setting empty     -> nothing happens (the normal case)
//   - email not found   -> a warning in the log, the service starts anyway
// Remove the setting afterwards. It is an email, never a password.
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final UpdateRoleService updateRoleService;
    private final String adminEmail;

    public AdminBootstrap(UserRepository userRepository, UpdateRoleService updateRoleService,
                          @Value("${auth.bootstrap.admin-email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.updateRoleService = updateRoleService;
        this.adminEmail = adminEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        makeFirstAdminIfNeeded();
    }

    public void makeFirstAdminIfNeeded() {
        if (!StringUtils.hasText(adminEmail)) {
            return;
        }
        if (userRepository.countUsersWithRole(RoleName.ROLE_ADMIN) > 0) {
            log.info("Admin bootstrap: an admin already exists, so auth.bootstrap.admin-email is ignored. You can remove it.");
            return;
        }
        User user = userRepository.findByEmail(adminEmail.trim());
        if (user == null) {
            // No email in the log (personal data). The person who set it knows which one it is.
            log.warn("Admin bootstrap: no account has the email in auth.bootstrap.admin-email. Sign up first, then restart.");
            return;
        }
        try {
            updateRoleService.MakeFirstAdmin(user.getUsername());
            log.info("Admin bootstrap: user {} is now ADMIN. Remove auth.bootstrap.admin-email now.", user.getUserid());
        } catch (RuntimeException e) {
            // Do not stop the whole service for this; the log says what went wrong.
            log.error("Admin bootstrap failed for user {}", user.getUserid(), e);
        }
    }
}
