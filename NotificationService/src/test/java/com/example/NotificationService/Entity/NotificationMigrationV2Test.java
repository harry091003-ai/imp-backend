package com.example.NotificationService.Entity;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 4: checks the RULES of the Flyway file V2 (the tests have no real MySQL to run it on).
 * - The type and status lists hold exactly the values the Java code can save.
 * - New values only at the END (MySQL stores an enum as its position in the list).
 * - The old CHECK is dropped, and it is ONE statement (all or nothing).
 * The SQL itself is proved only by the first real start. Written first: all 6 failed.
 */
class NotificationMigrationV2Test {

    private static final String V1 = "db/migration/V1__baseline.sql";
    private static final String V2 = "db/migration/V2__sla_notifications.sql";

    private static String read(String path) throws Exception {
        ClassPathResource file = new ClassPathResource(path);
        assertTrue(file.exists(), path + " must exist");
        return new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** The SQL without comment lines (comments may mention words like CHECK on purpose). */
    private static String withoutComments(String sql) {
        return Arrays.stream(sql.split("\\R")).filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    /** The values of `column` enum('A','B',...) in the given SQL, in their order. */
    private static List<String> enumValues(String sql, String column) {
        Matcher m = Pattern.compile("`" + column + "`\\s+enum\\(([^)]*)\\)").matcher(withoutComments(sql));
        assertTrue(m.find(), "no enum(...) for column " + column);
        return Arrays.stream(m.group(1).split(",")).map(v -> v.trim().replace("'", "")).toList();
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }

    @Test
    void theEventTypeListHoldsExactlyWhatTheJavaCodeCanSave() throws Exception {
        assertEquals(names(EventType.values()), Set.copyOf(enumValues(read(V2), "event_type")));
    }

    @Test
    void theStatusListHoldsExactlyWhatTheJavaCodeCanSave() throws Exception {
        assertEquals(names(NotificationStatus.values()), Set.copyOf(enumValues(read(V2), "status")));
    }

    @Test
    void newValuesAreOnlyAddedAtTheEndSoOldRowsKeepTheirMeaning() throws Exception {
        List<String> oldTypes = enumValues(read(V1), "event_type");
        List<String> newTypes = enumValues(read(V2), "event_type");
        assertEquals(oldTypes, newTypes.subList(0, oldTypes.size()), "the old types keep their exact order");

        List<String> oldStatus = enumValues(read(V1), "status");
        List<String> newStatus = enumValues(read(V2), "status");
        assertEquals(oldStatus, newStatus.subList(0, oldStatus.size()), "the old statuses keep their exact order");
    }

    @Test
    void theOldCheckRuleIsRemoved() throws Exception {
        String sql = withoutComments(read(V2));
        assertTrue(sql.contains("DROP CHECK `notification_log_chk_1`"), "the old CHECK (event_type between 0 and 7) must go");
    }

    @Test
    void theRecipientIdColumnIsAddedLikeTheOtherIdColumns() throws Exception {
        // UUIDs are binary(16) everywhere in this table (id, event_id, incident_id ...): Hibernate expects that.
        assertTrue(withoutComments(read(V2)).contains("ADD COLUMN `recipient_id` binary(16) DEFAULT NULL"));
    }

    @Test
    void itIsOneSingleStatementSoItIsAllOrNothing() throws Exception {
        long statements = withoutComments(read(V2)).chars().filter(c -> c == ';').count();
        assertEquals(1, statements, "one ALTER TABLE statement: MySQL then applies all of it or none of it");
    }
}
