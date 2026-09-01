# PROGRESS.md — Modernization Tracker

> This project has **no git**. This file is the source of truth for what has been decided,
> what is done, and what is pending. Read this file first in any new session before touching
> code. Update it — Status + Change Log — every time work actually lands.
>
> Companion file: [CLAUDE.md](CLAUDE.md) describes the codebase as it currently is (stack,
> conventions, domain model, full catalogue of known issues). This file tracks the **plan to
> fix things**, in order, with status.

Last updated: 2026-08-28 · 

---

## Ground rules for this engagement

1. **No code changes without an explicit go-ahead on the item being worked.** Understanding
   and planning happens first; this file gets updated with the plan before any file is touched.
2. **No git/GitHub.** Every session, for every file changed, I must explicitly tell the user:
   which file, what changed, and why — with enough detail that they could review it like a
   diff, since there is no diff tool doing that for them. This is not optional politeness,
   it's the only review mechanism this project has.
3. Work proceeds **one numbered track item at a time**, in the order agreed below. Don't jump
   ahead to a later item because it looks easy or related — surface it and ask instead.
4. This file's **Status table** and **Change Log** are the durable record. CLAUDE.md's
   "Known issues" section (§9) should get its matching item marked resolved/superseded when a
   track item ships — don't let the two documents contradict each other.

---

## Track A — in progress now

Agreed order: **A1 → A2 → A4**. A3 is explicitly deferred (see below).

### A1. Error handling & API response contract — 🔴 top priority — STATUS: ✅ DONE (2026-08-28)

**Why first (agreed rationale):** every endpoint written before this exists gets touched again
when it lands; every client integrating against today's ad hoc error shapes breaks when it's
fixed. Highest rework multiplier in the codebase, cost doesn't decrease by waiting.

**Problem today:** zero `@ControllerAdvice` across all 8 services. Every failure path is
`throw new RuntimeException("...")` (~26 sites, see CLAUDE.md §9 item 40). Spring turns that
into a generic 500 + HTML Whitelabel page. Client can't distinguish "not found" from "DB is on
fire"; can't tell if retrying helps.

**Target shape (agreed direction, not yet built):**
- Small exception hierarchy: `NotFoundException`, `ConflictException`, `ForbiddenException`,
  `ValidationException` (exact set/names TBD when we scope this).
- One shared `@ControllerAdvice` mapping each exception type → correct HTTP status.
- One consistent error response body used everywhere, roughly:
  `{ timestamp, status, error, message, path, traceId }` (exact fields TBD).
- Lives in a **shared module** so all 8 services get it for free rather than 8 copies —
  candidate location TBD: either inside `common-security` or a new `shared-core`/`shared-web`
  module. This decision affects A4 (build/reactor) too, so make it together with that track.

**Open questions to resolve before writing code:**
- New shared module vs. extending `common-security`?
- Exact exception → status mapping table, and which of the ~26 `RuntimeException` call sites
  map to which exception type (this is effectively a per-service audit).
- Whether `traceId` needs real correlation-id propagation across Kafka/WebClient calls, or is a
  per-request UUID for now (local-machine scope suggests the latter is enough for now).

**DELIVERED.** New `common-web` module (9 main classes + 1 test class), rolled out to all 5
Spring MVC services, plus a hand-matched error shape in the reactive APIGateway.
NotificationService correctly skipped (no controllers). See the Change Log at the bottom of this
file for the exact per-file list. All 11 modules build clean; 12/12 contract tests pass.

---

### A2. Database migrations — 🔴 — STATUS: ✅ DONE (2026-09-01)

**Why second:** cost of delay compounds fastest of anything on this list — `ddl-auto=update`
silently orphans data on renames (adds a new column, leaves the old one full of stale data,
no error) and flatly refuses on incompatible changes (service won't start, no rollback path).
An afternoon fix today becomes a scary weekend later.

**Problem today:** all 6 JPA-using services (`AuthService`, `UserService`, `IncidentService`,
`NotificationService`, `ReportingService`, `Ai-Service`) run
`spring.jpa.hibernate.ddl-auto=update`, no Flyway/Liquibase anywhere, no migration history.

**Target direction (agreed, not yet built):** introduce Flyway (or Liquibase — tool choice
still open) per service, baseline the current schema as `V1__baseline.sql` generated from
each service's actual current DB state, switch `ddl-auto` to `validate`. Ai-Service's Postgres
usage is a bit different — it has both a `vector_db` with plain JPA tables *and* the
hand-created `resolved_incidents_vectors` pgvector table (`initializeSchema(false)`) — migration
approach there needs to respect that the vector table is intentionally not Hibernate-managed.

**Open questions:**
- Flyway vs Liquibase (Flyway is the lighter-weight default choice for a from-scratch baseline;
  no strong reason yet to prefer Liquibase — will confirm before implementing).
- How to baseline safely per-service without wiping local dev data.
- Order of services to migrate (probably matches Track A4 build-fix order, or start with
  AuthService since it's foundational).

**DECIDED:** Flyway (not Liquibase) — plain `.sql` files are reviewable; Liquibase's changelog
layer buys us nothing here and its rollback is a paid feature in Flyway anyway.
**DECIDED:** keep the existing local data rather than wiping — the user specifically wants to
learn the "database already has rows" baselining case, which is the real-world situation.
No safety backup taken: user confirmed the data is dummy/disposable.

**AuthService pilot — files changed 2026-08-29:**
| File | Change |
|---|---|
| `AuthService/src/main/resources/db/migration/V1__baseline.sql` | **New.** Schema captured from the live DB via `mysqldump --no-data`, then reordered (roles → users → user_roles) so foreign keys resolve on an empty database, with `AUTO_INCREMENT=4` and the mysqldump preamble stripped |
| `AuthService/pom.xml` | Added `flyway-core` + `flyway-mysql` (both needed — core alone fails with "Unsupported Database: MySQL" on Flyway 10+). Resolved to 11.7.2 via the Boot parent |
| `AuthService/src/main/resources/application.properties` | `ddl-auto=update` → `validate`; added `spring.flyway.*` settings including `baseline-on-migrate=true` |
| `.gitignore` | Added `db-backups/` + `*.dump` so schema/data dumps are never committed |
| `FLYWAY-NOTES.md` | **New** (user-authored, reviewed + extended). Part 2 is the running migration log |
| `ERROR_HANDLING_FLOW.md` | **New.** Plain-English A1 walkthrough (belongs to A1, written this session) |
| `BUGS-TO-FIX-LATER.md` | Added E7 (bit(1) vs tinyint(1) boolean inconsistency) and E8 (orphan `inicident-management-aiservicedb`) |

**Build verified** (`mvn clean package`) — but **NOT yet run against the database.** The next
step is starting AuthService and confirming Flyway baselines cleanly and `validate` passes.
Cannot be done by the assistant: needs the `user`/`password`/`secretkey` env vars.

**Known risk on first run:** `ddl-auto=validate` may reject `account_locked tinyint(1)` because
the entity declares `columnDefinition = "TINYINT(1)"` while Hibernate's default for `Boolean` is
`bit(1)`. If it fails, the app simply refuses to start — nothing is modified — and the fix is
either an explicit entity mapping or a deliberate type-normalising migration (BUGS E7).

**Still pending for AuthService:** `V2__seed_roles.sql`. The three role rows currently exist only
because they were inserted by hand; a fresh machine would get a valid schema and a broken signup.

**AuthService pilot RESULT (2026-08-31): ✅ verified working.** Flyway baselined the existing
database, `ddl-auto=validate` passed, service started, new users created successfully. The
`account_locked tinyint(1)` risk flagged earlier did NOT materialise — Hibernate accepted it.

**Post-verification fix:** the user added explanatory comments to `AuthService/.../V1__baseline.sql`
using `--text` with no space. MySQL requires `-- text` (space after the dashes) — 26 lines were
invalid SQL, including one *inside* the `CREATE TABLE`. Harmless on this machine because V1 was
baselined rather than executed, but it would have broken any fresh database. Fixed by inserting a
single space per line; no wording changed. Safe to edit because baseline rows store
`checksum = NULL` — that exception does NOT extend to V2+.

**Rollout to the 4 remaining MySQL services — 2026-08-31:**
| File | Change |
|---|---|
| `UserService/pom.xml`, `IncidentService/pom.xml`, `NotificationService/pom.xml`, `ReportingService/pom.xml` | Added `flyway-core` + `flyway-mysql` |
| `.../src/main/resources/application.properties` (×4) | `ddl-auto=update` → `validate`, plus the `spring.flyway.*` block |
| `UserService/.../db/migration/V1__baseline.sql` | **New** — 3 tables, reordered for FK safety |
| `IncidentService/.../db/migration/V1__baseline.sql` | **New** — 1 table |
| `NotificationService/.../db/migration/V1__baseline.sql` | **New** — 1 table |
| `ReportingService/.../db/migration/V1__baseline.sql` | **New** — 7 tables incl. the non-entity `shedlock` |

All 4 build clean; baselines verified against the dumps programmatically — 12 tables / 106 columns,
zero drift, no columns lost or invented. **Not yet run against their databases.**

**Findings recorded in BUGS-TO-FIX-LATER.md** (not fixed — a baseline must copy reality exactly or
`validate` fails): E9 `incidents` has no indexes despite 3 query paths; E10 suspicious
`CHECK (event_type between 0 and 7)` on an 8-value enum; E11 `USER_LOOGED_OUT` typo in the audit
enum.

**Biggest catch of this pass:** ReportingService's `shedlock` table is not a JPA entity and was
created by hand — it existed only on this machine and was recorded nowhere. A fresh laptop would
have started fine, then failed at runtime when the SLA schedulers ran. It is now in V1.

**Also spotted:** the user commented out the dead `AuthService` dependency in
`IncidentService/pom.xml` (CLAUDE.md §9 item 23). Still builds — confirms it was unused. The same
removal is still pending in UserService, NotificationService and ReportingService (A4 work).

**Remaining:** Ai-Service (PostgreSQL, last — its hand-made `resolved_incidents_vectors` pgvector
table stays outside Flyway). Postgres was not running on 5433 during this session.

**COMPLETE 2026-09-01.** All 6 JPA services on Flyway with `ddl-auto=validate`. 8 migration
files total (6 baselines + 2 role seeds). All 11 modules build clean; every migration verified
packaged into its jar; all baselines verified against their dumps programmatically (14 tables,
118 columns, 3 Postgres extensions - zero drift).

**The fresh-database path was tested for real**, not just assumed: a throwaway database was
created, AuthService pointed at it, and Flyway built the schema from V1 and seeded roles from V2.
That was the one thing the baseline path could never prove.

**Two undocumented hand-created tables were recovered** - ReportingService's `shedlock` and
Ai-Service's `resolved_incidents_vectors`. Both existed only on this machine, described in no
file. Neither is a JPA entity, so `ddl-auto=validate` does not check them; without capturing them,
a fresh machine would have started fine and then failed at runtime.

**Ai-Service specifics:** `flyway-database-postgresql` (not `flyway-mysql`); 3 extensions (`vector`
essential, `uuid-ossp`/`hstore` likely unused - E13); everything in V1 rather than a split V2,
matching how `shedlock` was handled and avoiding a V2 that would execute against the existing DB.

**Lesson recorded twice:** migrations above the baseline version DO execute on existing databases.
`V2__seed_roles.sql` needed `INSERT IGNORE`; the Ai-Service vector table was folded into V1 for the
same reason.

**Findings logged, not fixed** (a baseline must copy reality or `validate` fails): E7 bit/tinyint
boolean split, E9 `incidents` has no indexes, E10 suspicious CHECK on an 8-value enum, E11
`USER_LOOGED_OUT` typo, E12 embedding column unindexable at 3072 dims, E13 unused extensions.

**Infrastructure hardened along the way** (not strictly A2): Kafka was bound to `0.0.0.0:9092`
with no authentication and ZooKeeper to `0.0.0.0:2181`; both are now localhost-only/unpublished.
pgvector gained a compose file (`pgvector-docker/docker-compose.yml`) - its config previously
existed only inside Docker's state, the same class of problem as the two lost tables.

---

### A3. Configuration & profiles — ⏸️ EXPLICITLY DEFERRED

User's call: **this project only needs to run on the local machine right now** — no staging,
no production target yet. So Spring profiles, externalized config server, per-environment
property files, etc. are **not in scope** until that changes. Do not spend effort here unless
the user reopens it. (CLAUDE.md §9 item 44/§8 still documents the current single-properties-file
+ raw-env-var state accurately — that's fine as-is for a local-only project.)

---

### A4. Fix the split build — 🟠 — STATUS: not started

**Why this matters (agreed rationale):** a build that can be green while broken is worse than
no build. Root `pom.xml`'s reactor currently only lists 6 of 10 modules — `IncidentService`,
`NotificationService`, `ReportingService`, `Ai-Service` are excluded (see CLAUDE.md §1, §9
item 24). Concrete failure mode: change `shared-events`, run `mvn clean install` at root, get a
green build — but the 4 excluded services were never recompiled against the change, and it only
surfaces later as a Kafka deserialization failure at runtime.

**Also blocks Track C (tests/CI):** CI that runs against a lying build is worse than no CI —
this needs to be fixed before that track starts.

**Target direction (agreed, not yet built):**
- Add all 4 missing services to the root `pom.xml` `<modules>` list.
- Decide whether services should actually parent to the root pom (currently none do — root
  pom's `<properties>` are inert, see CLAUDE.md §1) or just be listed as reactor modules
  without changing their `<parent>`. Changing `<parent>` is a bigger, riskier move (touches
  every service's pom) — leaning toward "just fix the reactor list" first, parenting is a
  separate later decision.
- Also relevant here: `AuthService` is currently a **declared Maven dependency** of 4 services
  with nothing actually imported from it (CLAUDE.md §9 item 23) — worth deciding whether to
  remove that dead dependency as part of this same pass, since it's the same "build hygiene"
  concern. Will confirm scope before touching poms.

**Not started. No files touched yet.**

---

## Track B / C / D — mentioned, not yet defined

User referenced additional tracks beyond A ("i also have Track B, C, D but i think first we
work on this then move to next"). **Contents of B/C/D have not been discussed or captured yet.**
Do not assume what they contain — ask when we get there. (Reasonable guesses based on
CLAUDE.md's issue catalogue — security perimeter/header-trust hardening, real tests + CI,
Kafka DLQ/retry policy, notification email actually being implemented — are guesses only,
**not confirmed scope**, and must not be started without the user defining these tracks.)

---

## Status table

| Item | Priority | Status | Notes |
|---|---|---|---|
| A1 — Error handling & response contract | 🔴 highest | ✅ **Done** 2026-08-28 | `common-web` module; 5 services + gateway |
| A2 — DB migrations (Flyway) | 🔴 | ✅ **Done 2026-09-01** | All 6 JPA services; 8 migrations; fresh-DB path tested |
| A3 — Config & profiles | ⏸️ deferred | Out of scope for now | Local-machine only, revisit later |
| A4 — Fix split build (root pom reactor) | 🟠 | Not started | Blocks Track C later |
| Track B | ❓ undefined | Not discussed | Ask user before assuming |
| Track C | ❓ undefined | Not discussed | Ask user before assuming |
| Track D | ❓ undefined | Not discussed | Ask user before assuming |

---

## Change Log

_No code changes have been made yet. Every entry below must name the exact files touched and
explain the change, added the same session the change ships._

- **2026-08-28** — Initial exploration + `CLAUDE.md` written (codebase understanding, no
  application code touched). This `PROGRESS.md` created to track the modernization plan.
  Priorities discussed and order agreed: A1 → A2 → (A3 deferred) → A4.

- **2026-08-28** — **A1 IMPLEMENTED.** Full file-by-file list below.
  `BUGS-TO-FIX-LATER.md` also created (environment/build problems found along the way).

  **NEW MODULE — `common-web/` (10 new files)**
  - `pom.xml` — Boot 3.5.11, groupId `com.project.web`, artifact `common-web:1.0.0`.
    Compiled with `release=17` on purpose so Ai-Service (still Java 17) can use it.
    All Spring deps are `provided` so the module never drags the servlet stack into the
    reactive gateway.
  - `exception/ApiException.java` — abstract base, carries `errorCode` + `HttpStatus`.
  - `exception/NotFoundException.java` (404), `ValidationException.java` (400),
    `ConflictException.java` (409), `ForbiddenException.java` (403),
    `UnauthorizedException.java` (401). Each has a 1-arg (default code) and 2-arg
    (specific code) constructor.
  - `dto/ErrorResponse.java` — the 6-field contract
    `{timestamp, status, error, message, path, traceId}`, plus an optional `fieldErrors`
    list that is OMITTED (not null) unless `@Valid` failed.
  - `handler/GlobalExceptionHandler.java` — `@RestControllerAdvice` with 10 handlers:
    ApiException, `@Valid` body failure, ConstraintViolation, type mismatch (bad UUID in
    a path), unreadable JSON body, missing request param, DataIntegrityViolation,
    AccessDenied, AuthenticationException, and a catch-all. The catch-all deliberately
    hides the real exception message and returns only a generic sentence + traceId.
  - `filter/TraceIdFilter.java` — reuses an inbound `X-Trace-Id` or mints an 8-char id;
    puts it in the SLF4J MDC, the request attributes, and the response header; clears MDC
    in a finally block (pooled threads would otherwise leak the id).
  - `config/CommonWebAutoConfig.java` + `META-INF/spring/...AutoConfiguration.imports` —
    solves the `com.project.*` vs `com.example.*` scanning problem: Spring would never
    have found the advice by component scan. Same trick common-security already uses.
  - `src/test/.../GlobalExceptionHandlerTest.java` — 12 tests, standalone MockMvc, no DB/
    Kafka/Eureka needed. Locks the status codes, the JSON field names, traceId reuse, and
    that an unexpected exception does not leak its message.

  **IncidentService (pilot) — 8 files changed**
  - `pom.xml` — added the `common-web` dependency.
  - `service/IncidentService.java` — all 13 live `RuntimeException`s replaced:
    3× incident-not-found → `NotFoundException(INCIDENT_NOT_FOUND)`;
    caller-inactive (create/assign/status) → `ForbiddenException(USER_NOT_ACTIVE)`;
    access/update/status denied → `ForbiddenException`;
    engineer-not-active / not-an-engineer → `ValidationException` (400, because the fix is
    for the client to pick a different engineer, not a permission problem);
    `assignedByMe` filter misuse → `ForbiddenException`.
  - `client/UserClient.java` — a 404 from UserService now becomes
    `NotFoundException(USER_NOT_FOUND)` instead of a blanket 500. Other statuses stay a
    500, which is correct for "a dependency failed".
  - `controller/IncidentController.java` — added `@Valid` to all 4 request bodies. They had
    NONE, so the DTO annotations were never being enforced.
  - `dto/AssignIncidentRequestDTO.java` — added `@NotNull` on `enginerId`.
  - `dto/UpdateIncidentStatus_RequestDTO.java` — replaced `@Column(nullable=false)` (a JPA
    annotation, inert on a DTO) with a real `@NotNull`; added `@Size(2000)` on rootCause and
    resolutionNotes.
  - `dto/UpdateIncidentMetadate_RequestDTO.java` — added `@Size` limits matching the entity
    columns (title 255, description 2000).

  **AuthService — 11 files changed**
  - `pom.xml` — added `common-web`.
  - `controller/TokenVersionController.java` — user-not-found → `NotFoundException`.
  - `controller/PasswordController.java` — added `@Valid` (was completely missing).
  - `service/LogoutUserService.java` — 2× user-not-found → `NotFoundException`.
  - `service/ChangePasswordService.java` — user-not-found → `NotFoundException`;
    wrong old password → `UnauthorizedException(INVALID_OLD_PASSWORD)`.
  - `service/RegisteringUserService.java` — duplicate username/email →
    `ConflictException(USERNAME_TAKEN / EMAIL_TAKEN)`. The missing-ROLE_ENGINEER throw was
    deliberately LEFT as a RuntimeException (→ 500): missing seed data is our fault, not the
    caller's, so a 4xx would blame the wrong side. Commented as such.
  - `service/LoginUserService.java` — **client-visible change.** Login failures used to
    return HTTP 401 with a `LoginResponseDTO` body (its own private error shape). They now
    throw `UnauthorizedException`, so they return the standard ErrorResponse.
    `HandleMultipleFailedLoginAttempts` changed from `ResponseEntity`-or-null to void+throw.
    Unknown-email and wrong-password now return the SAME message on purpose — the old code
    said "email not exist", which let anyone harvest valid accounts.
  - `service/UpdateCredentialsService.java` — **client-visible change.** The raw string
    bodies `"Invalid password"` (401), `"Email already exists"` (400) and
    `"Username already exists"` (400) are gone. Now `UnauthorizedException` and
    `ConflictException` (409 — a taken name is a state clash, not a malformed request).
    Return type tightened from `ResponseEntity<?>` to `ResponseEntity<UpdateCredentialResponseDTO>`.
  - `service/UpdateRoleService.java` — user-not-found → `NotFoundException`; added a
    `toRoleName` helper so an unknown role string is a 400 listing the valid roles instead
    of an `IllegalArgumentException` surfacing as a 500. Role-valid-but-missing-from-DB
    intentionally stays a 500.
  - `dto/ChangePasswordDTO.java` — had NO constraints; added `@NotBlank` on both fields and
    `@Size(min=6)` on the new password.
  - `dto/SignupFormDTO.java` — added the missing `@NotBlank` on password (CLAUDE.md §9 #15:
    `@Size` alone lets null through, so a passwordless signup reached `encode(null)`).
  - `dto/LoginFromDTO.java` — same missing `@NotBlank` on password.
  - `dto/UpdateUserRolesRequestDTO.java` — added `@NotEmpty` on the roles set.

  **UserService — 5 files changed**
  - `pom.xml` — added `common-web`.
  - `service/UserService.java` — all 18 live `RuntimeException`s replaced. Notably
    `LimitedUserInfoForValidation` now throws `NotFoundException`, which is what makes
    IncidentService's 404 mapping actually work end-to-end. Role-mismatch cases →
    `ValidationException`; "you can only update your assigned engineers" →
    `ForbiddenException`; circular hierarchy → `ConflictException`.
  - `controller/UserController.java` — added `@Valid` to 3 request bodies.
  - `dto/StatusRequestDTO.java` — added `@NotNull` on status.
  - `dto/AssignEngineerRequestDTO.java` — added `@NotNull` on both ids.

  **ReportingService — 2 files changed**
  - `pom.xml` — added `common-web`.
  - `service/ReportingService.java` — incident-report-not-found → `NotFoundException`.
    (This service had only ONE throw site.)

  **Ai-Service — 1 file changed**
  - `pom.xml` — added `common-web`. No throw sites existed to migrate; it now inherits the
    shared handler for framework-level failures (bad UUID, malformed JSON, access denied).

  **APIGateway — 1 file changed (hand-matched, does NOT use common-web)**
  - `filter/JwtGatewayFilter.java` — the gateway is WebFlux, where `@RestControllerAdvice`
    does nothing, so the shape is reproduced by hand.
    `onError` previously sent a status code and an **empty body**; it now writes the same
    6-field JSON. Error codes added: `MISSING_TOKEN`, `INVALID_TOKEN`, `TOKEN_REVOKED`,
    `AUTH_SERVICE_UNAVAILABLE`.
    **Behaviour change:** AuthService being unreachable now returns **503, not 401** — it
    used to tell users their token was bad and make them re-login, hiding a real outage.
    Also now generates an `X-Trace-Id` and forwards it downstream, so one id covers the
    whole call chain rather than each service inventing its own.

  **NOT changed, on purpose**
  - `NotificationService` — verified it has zero `@RestController`/`@RequestMapping`, so
    there is no HTTP response for an advice to intercept. It picks this up if it ever gets
    an API.
  - `Eureka` — no application endpoints of its own.

  **Verification:** all 11 modules build clean via Maven, in dependency order.
  12/12 `common-web` contract tests pass. Runtime behaviour has NOT been verified against a
  live stack (needs MySQL + Postgres + Kafka + Eureka running) — that is the remaining
  manual check for A1.
