package com.example.AuthService.service;

import com.example.AuthService.config.AdminBootstrap;
import com.example.AuthService.dto.UpdateUserRolesRequestDTO;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleChangeAudit;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.RoleUpdatedEventProducer;
import com.example.AuthService.kafka.producer.UserLoggedOutEventProducer;
import com.example.AuthService.kafka.producer.UserRegisteredEventProducer;
import com.example.AuthService.repository.RoleChangeAuditRepository;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.outbox.OutboxWriter;
import com.project.web.exception.ApiException;
import com.project.web.exception.ConflictException;
import com.project.web.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Issue 67 - safe role management, on the REAL AuthService code (H2 in MySQL mode, no Kafka).
 *
 *   1. Audit: every role change saves one record row, in the same transaction (both or neither).
 *   2. Guards: an admin cannot remove their own ADMIN role (403); the last admin cannot be removed (409),
 *      not even by two admins removing each other at the same moment.
 *   3. Bootstrap: the first admin comes from a setting, through the normal role code (so roles.update is sent),
 *      and only while no admin exists.
 */
@DataJpaTest(properties = {
        // LOCK_TIMEOUT: a request waiting for the ADMIN-row lock may wait up to 10 s (the race test needs ~2 s).
        "spring.datasource.url=jdbc:h2:mem:auth-roles;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RegisteringUserService.class, UpdateRoleService.class, LogoutUserService.class,
        UserRegisteredEventProducer.class, UserLoggedOutEventProducer.class, RoleUpdatedEventProducer.class,
        RoleManagementTest.Setup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class RoleManagementTest {

    @TestConfiguration
    static class Setup {
        @Bean
        OutboxWriter outboxWriter(DataSource dataSource) {
            return new OutboxWriter(new JdbcTemplate(dataSource), JacksonUtils.enhancedObjectMapper());
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    RegisteringUserService registeringUserService;
    @Autowired
    UpdateRoleService updateRoleService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    RoleChangeAuditRepository auditRepository;
    @Autowired
    DataSource dataSource;

    // Real code, but the race test can make it wait at one point (see the last guard test).
    @MockitoSpyBean
    LogoutUserService logoutUserService;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM role_change_audit");
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM roles");
        for (RoleName name : RoleName.values()) {
            roleRepository.save(Role.builder().rolename(name).build());
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Sign up through the REAL code (gets ROLE_ENGINEER), then forget the signup's event row. */
    private User signedUp(String name) {
        User user = User.builder().username(name).email(name + "@example.com").password("plain-secret-1").build();
        registeringUserService.RegisteringTheUser(user);
        jdbc.update("DELETE FROM outbox_event");
        return user;
    }

    /** Set roles straight in the table: the state BEFORE the test (like today's hand edit). */
    private User withRolesInTheTable(String name, String... roles) {
        User user = signedUp(name);
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", user.getUserid());
        for (String role : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE rolename = ?",
                    user.getUserid(), role);
        }
        return user;
    }

    private void change(User admin, User target, String... newRoles) {
        updateRoleService.UpdatingUserRoles(target.getUsername(),
                UpdateUserRolesRequestDTO.builder().role(Set.of(newRoles)).build(), admin.getUserid());
    }

    private List<String> rolesOf(User user) {
        return jdbc.queryForList("SELECT r.rolename FROM user_roles ur JOIN roles r ON r.id = ur.role_id "
                + "WHERE ur.user_id = ? ORDER BY r.rolename", String.class, user.getUserid());
    }

    private int adminCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id "
                + "WHERE r.rolename = 'ROLE_ADMIN'", Integer.class);
    }

    private List<String> outboxTopics() {
        return jdbc.queryForList("SELECT topic FROM outbox_event ORDER BY seq", String.class);
    }

    private List<RoleChangeAudit> auditOf(User user) {
        return auditRepository.findByTargetUserIdOrderByChangedAtAsc(user.getUserid());
    }

    private AdminBootstrap bootstrapWith(String email) {
        return new AdminBootstrap(userRepository, updateRoleService, email);
    }

    /** The change is refused with this code, and NOTHING was saved: roles, event rows, audit rows. */
    private <T extends ApiException> void refusedAndNothingSaved(Class<T> type, String code, User target, Runnable action) {
        List<String> rolesBefore = rolesOf(target);
        T error = assertThrows(type, action::run);
        assertEquals(code, error.getErrorCode());
        assertEquals(rolesBefore, rolesOf(target), "roles not changed");
        assertEquals(List.of(), outboxTopics(), "no event, no forced logout");
        assertEquals(0, auditRepository.count(), "no audit row for a change that did not happen");
    }

    // ================================================================== 1. audit

    @Test
    void anAdminChangingRolesSavesOneAuditRow() {
        User ann = withRolesInTheTable("ann", "ROLE_ADMIN");
        User bob = signedUp("bob");

        change(ann, bob, "ROLE_ENGINEER", "ROLE_SUPERVISOR");

        List<RoleChangeAudit> rows = auditOf(bob);
        assertEquals(1, rows.size());
        RoleChangeAudit row = rows.get(0);
        assertEquals(ann.getUserid(), row.getChangedBy(), "who did it");
        assertEquals("ROLE_ENGINEER", row.getOldRoles());
        assertEquals("ROLE_ENGINEER,ROLE_SUPERVISOR", row.getNewRoles());
        assertEquals(RoleChangeAudit.SOURCE_ADMIN_REQUEST, row.getSource());
        assertNotNull(row.getChangedAt());
    }

    @Test
    void ifTheAuditRowCannotBeSavedTheRoleChangeIsNotSavedEither() {
        User ann = withRolesInTheTable("ann", "ROLE_ADMIN");
        User bob = signedUp("bob");
        jdbc.execute("ALTER TABLE role_change_audit RENAME TO role_change_audit_hidden");
        try {
            assertThrows(RuntimeException.class, () -> change(ann, bob, "ROLE_SUPERVISOR"));
        } finally {
            jdbc.execute("ALTER TABLE role_change_audit_hidden RENAME TO role_change_audit");
        }

        assertEquals(List.of("ROLE_ENGINEER"), rolesOf(bob), "both or neither: no change without its record");
        assertEquals(List.of(), outboxTopics());
    }

    // ================================================================== 2. guards

    @Test
    void anAdminCannotRemoveTheirOwnAdminRole() {
        User ann = withRolesInTheTable("ann", "ROLE_ADMIN");
        withRolesInTheTable("carl", "ROLE_ADMIN");   // another admin exists, so this is about "your own"

        refusedAndNothingSaved(ForbiddenException.class, "CANNOT_REMOVE_OWN_ADMIN_ROLE", ann,
                () -> change(ann, ann, "ROLE_ENGINEER"));
    }

    @Test
    void anAdminMayStillAddARoleToThemselves() {
        User ann = withRolesInTheTable("ann", "ROLE_ADMIN");

        change(ann, ann, "ROLE_ADMIN", "ROLE_ENGINEER");

        assertEquals(List.of("ROLE_ADMIN", "ROLE_ENGINEER"), rolesOf(ann));
    }

    @Test
    void anAdminMayRemoveAnotherAdminWhileOneStays() {
        User ann = withRolesInTheTable("ann", "ROLE_ADMIN");
        User carl = withRolesInTheTable("carl", "ROLE_ADMIN");

        change(ann, carl, "ROLE_ENGINEER");

        assertEquals(List.of("ROLE_ENGINEER"), rolesOf(carl));
        assertEquals(1, adminCount());
    }

    @Test
    void theLastAdminCannotBeRemoved() {
        // dave is no admin any more in the table, but his token still says ADMIN for a short while
        // (the gateway caches the token check). He tries to remove the only real admin.
        User dave = withRolesInTheTable("dave", "ROLE_ENGINEER");
        User carl = withRolesInTheTable("carl", "ROLE_ADMIN");

        refusedAndNothingSaved(ConflictException.class, "LAST_ADMIN", carl,
                () -> change(dave, carl, "ROLE_ENGINEER"));
        assertEquals(1, adminCount());
    }

    @Test
    void twoAdminsRemovingEachOtherAtTheSameMomentLeaveOneAdmin() throws Exception {
        User ann = withRolesInTheTable("ann", "ROLE_ADMIN");
        User carl = withRolesInTheTable("carl", "ROLE_ADMIN");

        // Hold each request inside its transaction (at the forced logout) until the other one is there too,
        // or 2 s have passed. Without a lock both get there, both have counted "2 admins", both save: 0 admins.
        CyclicBarrier bothInside = new CyclicBarrier(2);
        doAnswer(call -> {
            try {
                bothInside.await(2, TimeUnit.SECONDS);
            } catch (Exception waitedLongEnough) {
                // the other request is not coming (it is waiting for the lock): go on alone
            }
            return call.callRealMethod();
        }).when(logoutUserService).LogoutUserByAdmin(any());

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> annRemovesCarl = pool.submit(() -> { go.await(); return attempt(() -> change(ann, carl, "ROLE_ENGINEER")); });
            Future<String> carlRemovesAnn = pool.submit(() -> { go.await(); return attempt(() -> change(carl, ann, "ROLE_ENGINEER")); });
            go.countDown();
            List<String> results = new ArrayList<>(List.of(annRemovesCarl.get(30, TimeUnit.SECONDS),
                    carlRemovesAnn.get(30, TimeUnit.SECONDS)));

            results.sort(String::compareTo);
            assertEquals(List.of("LAST_ADMIN", "OK"), results, "one wins, the other is told there must be an admin");
            assertEquals(1, adminCount(), "never zero admins");
        } finally {
            pool.shutdownNow();
        }
    }

    private static String attempt(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (ApiException e) {
            return e.getErrorCode();
        }
    }

    // ================================================================== 3. bootstrap

    @Test
    void bootstrapMakesTheAccountAdminWhenThereIsNoAdminYet() {
        User eve = signedUp("eve");

        bootstrapWith("eve@example.com").makeFirstAdminIfNeeded();

        assertEquals(List.of("ROLE_ADMIN"), rolesOf(eve));
        assertTrue(outboxTopics().contains("roles.update"), "UserService is told (the hand edit never did this)");
        String event = jdbc.queryForObject("SELECT payload FROM outbox_event WHERE topic = 'roles.update'", String.class);
        assertTrue(event.contains("ROLE_ADMIN"));
        List<RoleChangeAudit> rows = auditOf(eve);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).getChangedBy(), "done by the system, not by a person");
        assertEquals(RoleChangeAudit.SOURCE_BOOTSTRAP, rows.get(0).getSource());
    }

    @Test
    void bootstrapDoesNothingWhenAnAdminAlreadyExists() {
        withRolesInTheTable("ann", "ROLE_ADMIN");
        User eve = signedUp("eve");

        bootstrapWith("eve@example.com").makeFirstAdminIfNeeded();

        assertEquals(List.of("ROLE_ENGINEER"), rolesOf(eve), "a setting added later cannot make a new admin");
        assertEquals(List.of(), outboxTopics());
        assertEquals(0, auditRepository.count());
    }

    @Test
    void bootstrapDoesNothingWhenTheSettingIsEmpty() {
        User eve = signedUp("eve");

        bootstrapWith("").makeFirstAdminIfNeeded();
        bootstrapWith("   ").makeFirstAdminIfNeeded();
        bootstrapWith(null).makeFirstAdminIfNeeded();

        assertEquals(List.of("ROLE_ENGINEER"), rolesOf(eve));
        assertEquals(0, adminCount());
    }

    @Test
    void bootstrapWithAnUnknownEmailChangesNothingAndDoesNotStopTheService() {
        signedUp("eve");

        assertDoesNotThrow(() -> bootstrapWith("nobody@example.com").makeFirstAdminIfNeeded());

        assertEquals(0, adminCount());
        assertEquals(0, auditRepository.count());
    }

    @Test
    void bootstrapIgnoresSpacesAroundTheEmail() {
        User eve = signedUp("eve");

        bootstrapWith("  eve@example.com  ").makeFirstAdminIfNeeded();

        assertEquals(List.of("ROLE_ADMIN"), rolesOf(eve));
    }
}
