# BUGS-TO-FIX-LATER.md

Things found while working on this project that are **real problems but were not fixed at the
time**, either because they were outside the current task's scope or because they are your call
to make. Nothing here is blocking right now — this is the "review in free time" list.

Companion files: [CLAUDE.md](CLAUDE.md) = what the codebase is · [PROGRESS.md](PROGRESS.md) = the
modernization plan and what is done.

**How to use this file:** pick an item, fix it, then move it to the "Fixed" section at the bottom
with the date. Items are grouped by how much they can hurt you.

---

## 🔴 Environment — blocks Maven entirely

### E1. `JAVA_HOME` points at a JDK that is not installed

**What's wrong:** your `JAVA_HOME` is set to `C:\Program Files\Java\jdk-24`, but that folder does
not exist. The only JDK actually installed is `C:\Program Files\Java\jdk-21`.

**What it causes:** every Maven command fails immediately with:

```
The JAVA_HOME environment variable is not defined correctly,
this environment variable is needed to run this program.
```

**Why it matters / how it hid:** this is why there was not a single `.jar` anywhere in the
project and why `out/production/` exists at the root — everything has only ever been compiled and
run **from inside IntelliJ**, which uses its own configured JDK and ignores `JAVA_HOME`. Maven has
effectively never run on this machine. That also means the "it builds" signal you have today comes
only from the IDE, not from a real build.

**How to fix:** set the environment variable to the JDK you actually have. In an admin PowerShell,
permanently for your user:

```bash
setx JAVA_HOME "C:\Program Files\Java\jdk-21"
```

Then close and reopen every terminal and IntelliJ, and confirm with `mvn -v`.

**Current workaround:** I export `JAVA_HOME` inline on each Maven command I run. That works but
only for my commands — your own terminal is still broken until you set it properly.

---

## 🟠 Build / dependency hygiene

### E2. Nothing had ever been installed into the local Maven repository

**What's wrong:** the shared library modules were not present in `~/.m2/repository`:

- `com.example:shared-events:1.0-SNAPSHOT` — **completely absent**
- `com.project.security:common-security:1.0.0` — only `.lastUpdated` marker files were there,
  which are the traces of a **failed** download attempt, not an installed artifact
- `com.example:AuthService:0.0.1-SNAPSHOT` — absent

**What it causes:** any service that depends on these cannot be built by Maven at all. It fails
with `Could not find artifact ... in central`, because Maven goes looking on the internet for a
module that only exists on your own disk and was never `install`ed.

**Why it matters:** it is the same root cause as E1 showing up in a second place. A new machine —
or a CI server later — cannot build this project from a clean checkout without someone knowing the
exact install order by heart.

**What I did about it:** installed them in dependency order so the build chain works:
`shared-events` → `common-security` → `AuthService`. This is a one-time local fix, not a code
change. It will need doing again on any other machine.

**Proper fix (this is Track A4 in PROGRESS.md):** add the missing modules to the root `pom.xml`
reactor so a single `mvn clean install` at the root builds everything in the right order, and
nobody has to remember the sequence.

---

### E3. Java version drift: Ai-Service is on 17, everything else on 21

**Status: VERIFIED — this is NOT currently broken.** I originally flagged this as "probably
broken"; that was wrong, and here is the corrected finding.

**What's actually true:** `Ai-Service` compiles with `<java.version>17</java.version>` while every
other module uses 21. Measured bytecode versions:

| Module | Bytecode | Notes |
|---|---|---|
| `Ai-Service` (own classes) | Java 17 (major 61) | |
| `common-web` (new) | Java 17 (major 61) | deliberately targeted at 17 |
| `common-security` | Java 21 (major 65) | and Ai-Service **does** import from it |
| `shared-events` | Java 21 (major 65) | |

**Why it works anyway:** `-source/-target/--release 17` controls which language features are
allowed and what bytecode javac *emits*, and restricts the *platform API* to Java 17. It does not
stop javac from *reading* newer class files that are on the classpath — JDK 21's javac happily
reads version-65 files. And at runtime everything runs on a Java 21 JVM, which reads both.
`Ai-Service` builds clean: **verified with a real Maven build.**

**So why is this still on the list:** it is a latent inconsistency, not a live bug.

- It would become a real failure the moment anyone tries to run `Ai-Service` on an actual
  Java 17 JVM — `common-security` and `shared-events` would fail with
  `UnsupportedClassVersionError` at startup.
- `Ai-Service` is the only module held back to 17, and there is no reason for it. It cannot use
  any language feature from 18–21.

**Fix when convenient:** bump `Ai-Service` to `<java.version>21</java.version>` to match every
other module. Low risk. (Also logged as item 26 in CLAUDE.md §9.)

---

### E4. `common-web` is deliberately pinned to Java 17, and should be raised later

**Not a bug — a decision worth revisiting.** `common-web/pom.xml` sets
`<maven.compiler.release>17</maven.compiler.release>` purely so `Ai-Service` can consume it while
that service is still on 17. Java 17 bytecode runs fine on a Java 21 JVM, so nothing is lost today.

**When E3 is fixed** (Ai-Service moved to 21), raise this to 21 so the module can use newer
language features. The pom has a comment marking this.

---

## 🟡 Smaller things noticed in passing

### E5. `common-security` uses a flat source directory

Its sources live in `src/main/java/com.project.security/` — that is **one folder literally named
`com.project.security`**, not the normal nested `com/project/security/`. Maven compiles it because
it hands javac an explicit file list, but it breaks the convention every tool assumes, and some
IDE/plugin operations will misbehave on it.

`common-web` was deliberately created with proper nested folders so the mistake is not repeated.
Fixing `common-security` is a straight folder move, but do it in its own change so it is easy to
review. (Also logged as item 27 in CLAUDE.md §9.)

---

### E6. `common-security` is a Spring Boot version behind

It parents to `spring-boot-starter-parent` **3.3.2** while every service is on **3.5.11**.
Mismatched Spring versions in the same application can produce confusing `NoSuchMethodError`-style
failures at runtime that look nothing like a version problem.

`common-web` was created on 3.5.11 to match the services. (Also logged as item 25 in CLAUDE.md §9.)

---

### E7. `users.enabled` and `users.account_locked` use two different boolean types

In `inicident-management-authservicedb`, the `users` table stores two true/false flags in two
different ways:

```
`enabled`        bit(1)      NOT NULL
`account_locked` tinyint(1)  NOT NULL
```

**Why:** the `User` entity has `@Column(columnDefinition = "TINYINT(1)")` on `accountLocked` only.
For `enabled` Hibernate used its own default for MySQL, which is `bit(1)`. Nobody chose this - it
is an accident of one field having an explicit override and the other not.

**Impact today:** none functionally, both work. It is a consistency and readability problem:
`SELECT * FROM users` shows one column as a binary blob and the other as 0/1.

**Why it was NOT fixed while adding Flyway:** `V1__baseline.sql` must describe the database
*exactly as it really is*, or `ddl-auto=validate` fails. Changing a column type is a real schema
change and deserves its own migration, written deliberately, not smuggled into a baseline.

**Fix later as:** a `V<n>__normalise_user_boolean_columns.sql` migration, applied to AuthService
and checked against UserService (which mirrors this entity and may have the same split).

---

### E8. Orphan MySQL database `inicident-management-aiservicedb`

MySQL still contains a database named `inicident-management-aiservicedb`, but `Ai-Service` no
longer points at MySQL at all - its `application.properties` targets PostgreSQL (`vector_db`), and
its own comment explains the move was made so the vector store and the regular tables could live
in one database.

So this MySQL database appears to be a leftover from before that switch and is almost certainly
unused.

**Do not just drop it.** Confirm first that nothing reads it (no service config references it),
then drop it deliberately so it stops appearing in the database list and confusing future work.

---

### E9. `incidents` table has no indexes at all

`inicident-management-incidentservicedb`.`incidents` has a primary key on `id` and **nothing
else** - confirmed from the schema dump taken 2026-08-31.

But `IncidentRepository` queries by three other columns on the main "list incidents" path:

```java
Page<Incident> findByCreatedBy(UUID userId, Pageable pageable);
Page<Incident> findByAssignedTo(UUID userId, Pageable pageable);
Page<Incident> findByAssignedBy(UUID userId, Pageable pageable);
```

plus `findIncidentsForUser(...)`, which filters on `assigned_to` and `created_by`.

With no index, every one of those makes MySQL read the whole table row by row. Invisible with a
few dozen test rows; the slowest thing in the platform once it grows.

**Not fixed while adding Flyway** because `V1__baseline.sql` must describe the database exactly
as it is, or `ddl-auto=validate` fails. Adding indexes is a real schema change.

**Fix later as:** `V2__add_incident_indexes.sql` - likely indexes on `created_by`, `assigned_to`,
`assigned_by`, and possibly a composite with `created_at` since results are sorted by it
descending. Compare against ReportingService's `incident_report`, which is already indexed
thoughtfully - IncidentService simply never got the same attention.

---

### E10. Suspicious CHECK constraint on `notification_log.event_type`

> **notification_log part: FIXED 2026-10-17** (SLA notifications step 4). NotificationService's Flyway
> `V2__sla_notifications.sql` drops `notification_log_chk_1` - the 4 new SLA types (positions 9-12) would surely have
> been refused. The native `enum` already limits the allowed words. Not tested whether `USER_UPDATED` really failed
> before; that no longer matters. **Still open:** the `incident_audit_log.event_type` check in ReportingService
> (last paragraph below).

The schema dump shows a Hibernate-generated constraint:

```sql
CONSTRAINT `notification_log_chk_1` CHECK ((`event_type` between 0 and 7))
```

`event_type` is a MySQL `enum` with **8** values. In MySQL, comparing an ENUM against numbers
uses the value's *position*, and positions start at **1** (0 is the reserved invalid value). So
the 8 values occupy positions 1-8, and the last one - `USER_UPDATED` - sits at position 8, which
appears to fall outside `between 0 and 7`.

**Impact:** likely none today. NotificationService only consumes incident events, so
`USER_UPDATED` is probably never written. That is exactly why this is worth recording - it would
surface much later, as a confusing constraint violation, if that event type is ever used.

**Needs verifying, not assuming.** Test it directly before changing anything:

```sql
INSERT INTO notification_log (id, event_id, event_type, recipient_email)
VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), 'USER_UPDATED', 'test@example.com');
```

If it fails, fix it in its own migration (drop and recreate the constraint with the correct
range, or drop it entirely - the native `enum` type already restricts the allowed values, which
makes the CHECK largely redundant). If it succeeds, delete this entry.

**Also check `incident_audit_log.event_type` in ReportingService**, which has 10 enum values and
may carry the same pattern.

---

### E11. Typo baked into the database: `USER_LOOGED_OUT`

`ReportingService`'s `incident_audit_log.event_type` enum contains `USER_LOOGED_OUT` - should be
`USER_LOGGED_OUT`. It is spelled that way in the Java enum, so Hibernate created the column that
way, and it is now reproduced in `V1__baseline.sql` (correctly - the baseline copies reality).

**Fixing it is a two-part change**, which is why it was not done in passing: the Java enum has to
change *and* a migration has to `ALTER` the column definition and rewrite any existing rows
holding the old spelling. Doing only one half breaks the other.

---

### E12. `resolved_incidents_vectors.embedding` has no index — and cannot easily get one

The pgvector table has a primary key on `id` and no index on the `embedding` column, so every
similarity search scans the whole table and computes a distance for each row.

With a small number of resolved incidents this is genuinely fine, and it has the advantage of
being *exact* — pgvector's index types are approximate.

**The catch worth knowing before anyone "fixes" it:** pgvector's `hnsw` and `ivfflat` indexes
support roughly **2000 dimensions**. This column is `vector(3072)`, matching
`gemini-embedding-001`'s default output. So the standard advice ("just add an HNSW index") is
expected to be rejected outright. Verify with:

```sql
CREATE INDEX ON resolved_incidents_vectors USING hnsw (embedding vector_cosine_ops);
```

If it fails on the dimension limit, the real options are:
- store as `halfvec`, which supports more dimensions for indexing, or
- configure `gemini-embedding-001` to emit fewer dimensions (it supports smaller sizes)

**Either option requires regenerating every embedding through the Gemini API** — real cost and
time, not just a schema change. Also note `AiConfig.dimensions(3072)` and the
`spring.ai.google.genai.embedding.*` settings would have to change in step, or it breaks at
runtime rather than at startup.

Not urgent while the dataset is small. Written down so the trade-off is understood in advance
rather than discovered under pressure.

---

### E13. Possibly unused PostgreSQL extensions: `hstore` and `uuid-ossp`

`vector_db` has three extensions enabled: `vector`, `uuid-ossp` and `hstore`.

`vector` is essential. The other two appear unused — application ids are generated in Java, not
by `uuid-ossp`, and the vector table's `metadata` column uses `jsonb` rather than `hstore`. They
likely came from an image template or were enabled speculatively.

Reproduced as-is in `V1__baseline.sql` because a baseline copies reality. Confirm nothing depends
on them, then drop them in their own migration if genuinely unused — fewer enabled extensions
means less attack surface.

---

## Fixed

_Move items here with the date once resolved._

- **2026-08-28** — `common-web/src/main/java/com/project/web/filter/TraceIdFilter.java` had a
  duplicated `*/` closing a javadoc block twice, which broke compilation with
  `class, interface, enum, or record expected`. Removed the stray second delimiter; all comment
  text kept as written.
- **2026-08-28** — `common-web/pom.xml` was missing the `spring-boot-starter-test` dependency while
  `GlobalExceptionHandlerTest.java` was present, so the module would not compile. Re-added the
  dependency (test scope only — it never ships in the jar).
