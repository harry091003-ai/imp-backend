# PROGRESS.md — What we are fixing in this project

**Read this file first when you come back to this project.**

It says three things:
1. What we already fixed.
2. What we have not fixed yet.
3. Why we made each choice.

Other files that go with this one:
- `CLAUDE.md` — what the code looks like today, and a list of known problems.
- `BUGS-TO-FIX-LATER.md` — problems we found but chose not to fix yet.
- `FLYWAY-NOTES.md` — how database migrations work here.
- `ERROR_HANDLING_FLOW.md` — how errors work here.

Last updated: 2026-09-01

---

## Where we are right now

| Task | What it is | Status |
|---|---|---|
| A1 | Error handling | ✅ Done — 2026-08-28 |
| A2 | Database migrations (Flyway) | ✅ Done — 2026-09-01 |
| A3 | Config and profiles | ⏸️ Skipped on purpose |
| A4 | Fix the build | ✅ Done — 2026-09-01 |
| Track B | Not decided yet | ❓ Need to discuss |
| Track C | Not decided yet | ❓ Need to discuss |
| Track D | Not decided yet | ❓ Need to discuss |

**Track A is finished.**

---

## Rules we agreed

1. **Ask before changing code.** Plan first. Write the plan here. Then change code.
2. **Explain every file we change.** Say which file, what changed, and why. This is how you
   review the work.
3. **One task at a time.** Do not jump ahead to a later task because it looks easy.
4. **Keep this file up to date.** When a task finishes, update it here and in `CLAUDE.md` too.
   The two files must not disagree.
5. **This git repo stays on this laptop.** No remote. No GitHub. No pull requests.
6. **Write simply.** Short sentences. Plain words. This applies to code comments too.

---

# A1 — Error handling ✅ Done 2026-08-28

## What was wrong

When something failed, every service did this:

```java
throw new RuntimeException("Incident not found");
```

Spring turned that into an HTML error page with status **500**.

## Why that was bad

The client could not tell these two things apart:

- "You asked for an incident that does not exist" — your mistake
- "Our database is down" — our mistake

Both looked the same. Both said 500.

Also, if you add monitoring later, it would wake you at 3am for someone typing a bad ID.

## What we built

A new shared module called `common-web`. All services use it. Nobody copies code.

It has three parts.

**1. Five error types.** The name of the error says what went wrong:

| Error type | HTTP code | Use when |
|---|---|---|
| `NotFoundException` | 404 | The thing does not exist |
| `ValidationException` | 400 | The request itself is wrong |
| `ConflictException` | 409 | Request is fine, but clashes with what is already saved |
| `ForbiddenException` | 403 | We know who you are. You are not allowed |
| `UnauthorizedException` | 401 | We do not know who you are |

**2. One class that catches everything.** You never call it. Spring sends every error to it
automatically. It picks the right HTTP code and builds the reply.

**3. One reply shape.** Every service, every error, the same shape:

```json
{
  "timestamp": "2026-08-28T10:15:00Z",
  "status": 404,
  "error": "INCIDENT_NOT_FOUND",
  "message": "Incident not found: 3f2a...",
  "path": "/incidents/3f2a...",
  "traceId": "a1b2c3d4"
}
```

The frontend writes its error code once. It then works for all services.

## The safety rule

If an error is one we expected, the client sees our message.

If an error is a bug we did not expect, the client sees **only** a generic sentence and a
traceId. The real error goes to the server log.

Why: a raw error message can leak table names, file paths and library versions to whoever is
calling the API.

## Two services are different

- **APIGateway** does not use `common-web`. It runs on WebFlux, where this trick does not work.
  We copied the same JSON shape by hand instead.
- **NotificationService** does not need it. It has no HTTP endpoints. It only reads Kafka.

## How we know it works

12 tests in `common-web`. They run with no database, no Kafka, no Eureka. About one second.

They check the status codes, the JSON field names, and that a surprise error does **not** leak
its message.

If someone changes a status code later, these tests fail and tell them.

## Still not done

We never tested this against a running system. All 11 modules compile and the tests pass. But
nobody has called a real endpoint and looked at the reply.

---

# A2 — Database migrations ✅ Done 2026-09-01

## What was wrong

All 6 services with a database used this setting:

```
spring.jpa.hibernate.ddl-auto=update
```

That means: *"Hibernate, look at my Java classes and change the database to match."*

There were no SQL files anywhere. The database shape was not written down.

## Why that was bad

**Problem 1 — silent data loss on rename.**

Say you rename a field in Java from `affectedSource` to `affectedService`.

Hibernate does not understand this as a rename. It sees:
1. An old field disappeared.
2. A new field appeared.

So it adds a **new empty column** and leaves the old one sitting there, still full of your data.
No error. No warning. You find out weeks later when reports look wrong.

**Problem 2 — the app just refuses to start.**

`ddl-auto=update` cannot shrink a column, change a type, or add a NOT NULL rule to a table that
already has rows. It just refuses. Your service will not start, and there is no history telling
you what changed.

## What we did

We added **Flyway** to all 6 services.

Flyway is like git, but for your database. You write SQL files. They run in order. Flyway
remembers which ones already ran.

Steps we followed for each service:

1. Asked the real database for its shape (`mysqldump --no-data` / `pg_dump --schema-only`).
   We did **not** build it from the Java classes. Those two can drift apart.
2. Saved that as `V1__baseline.sql`.
3. Told Flyway "this database already exists, mark V1 as done, do not run it"
   (`baseline-on-migrate=true`).
4. Changed `ddl-auto=update` to `ddl-auto=validate`. Now Hibernate never touches the database.
   It only checks that Java and the database agree, and refuses to start if they do not.

That last part is a good thing. It means "you changed a Java class and forgot the SQL file" gets
caught straight away.

## The 8 SQL files we wrote

| Service | Files |
|---|---|
| AuthService | `V1__baseline.sql`, `V2__seed_roles.sql` |
| UserService | `V1__baseline.sql`, `V2__seed_roles.sql` |
| IncidentService | `V1__baseline.sql` |
| NotificationService | `V1__baseline.sql` |
| ReportingService | `V1__baseline.sql` |
| Ai-Service | `V1__baseline.sql` |

## The lesson that caught us twice

**V1 does not run on your database. V2 does.**

V1 is the baseline, so Flyway skips it. But V2 is above the baseline, so Flyway runs it for real
— on a database that already has the data.

This caught us twice:

1. `V2__seed_roles.sql` adds 3 role rows. Those rows already existed. A normal `INSERT` would
   have failed with a duplicate error. We used `INSERT IGNORE` instead. It skips rows that
   already exist.
2. Ai-Service's vector table already existed too. So we put it in V1 instead of V2. Nothing
   runs, nothing fails.

## Two tables we nearly lost

This is the most valuable thing A2 found.

**ReportingService — the `shedlock` table.** It has no Java class. The ShedLock library uses it
so that only one copy of the service runs each scheduled job. Somebody created it by hand once.
It was written down nowhere.

**Ai-Service — the `resolved_incidents_vectors` table.** Same story. Spring AI reads and writes
it directly. The config says `initializeSchema(false)`, which means "do not create this, it
already exists". So:

- Hibernate did not know about it — no Java class
- Spring AI would not create it
- Nothing in the project described it

For both tables, a new laptop would have started every service perfectly fine. Then failed later
— the SLA schedulers for one, the AI suggestions for the other.

Both are now in the SQL files.

## How we know it works

We tested the empty-database case for real, not just in theory.

We made a throwaway database, pointed AuthService at it, and started it. Flyway built the tables
from V1 and added the roles from V2.

That was the one thing baselining alone could never prove.

## Choices we made

**Flyway, not Liquibase.** Flyway uses plain SQL files. Liquibase uses XML that turns into SQL.
Plain SQL is easier to read and review. We are not switching database brands, so Liquibase's
extra features buy us nothing.

**We kept the existing data.** Wiping the databases would have been easier. But learning how to
handle a database that already has data is the whole point. That is the real-world case.

## Problems we found but did not fix

A baseline file must copy the database exactly, or `validate` fails. So we wrote these down
instead of fixing them. All are in `BUGS-TO-FIX-LATER.md`:

- **E7** — `users.enabled` is `bit(1)` but `users.account_locked` is `tinyint(1)`. Same idea,
  two different types.
- **E9** — The `incidents` table has no indexes at all. But the code searches it by three
  different columns. Every search reads the whole table.
- **E10** — A CHECK rule on `notification_log` may reject the last enum value. Needs testing.
- **E11** — `USER_LOOGED_OUT` typo, spelled that way in Java and in the database.
- **E12** — The AI vector column has no index, and cannot easily get one. The index type only
  supports about 2000 dimensions. Ours is 3072.
- **E13** — Two PostgreSQL extensions look unused.

## Extra work we did along the way

Not strictly A2, but found while working on it.

**Kafka was open to the whole network.** Port 9092 was open to every network interface, with no
password. Anyone who could reach your laptop could read every topic, send fake events, or delete
topics. ZooKeeper was open too, on port 2181.

Both are now fixed. Kafka is locked to this machine only. ZooKeeper is not published at all —
Kafka reaches it inside Docker, so it never needed to be.

**pgvector had no compose file.** Its settings lived only inside Docker. Delete the container and
you would have to remember the port, the volume name and the database name. Now written down in
`pgvector-docker/docker-compose.yml`.

That is the same problem as the two lost tables above. Something important that existed only on
one machine.

---

# A3 — Config and profiles ⏸️ Skipped on purpose

You only run this project on your own laptop. There is no staging server. There is no
production.

So Spring profiles, config servers and per-environment settings are **not needed yet**.

Do not spend time here unless you decide to deploy this somewhere.

---

# A4 — Fix the build ✅ Done 2026-09-01

## What was wrong

This project has **11 buildable pieces**.

The main project file at the root, `pom.xml`, is supposed to list all of them. It listed **6**.

Missing:
- IncidentService
- NotificationService
- ReportingService
- Ai-Service
- common-web

## Why that was dangerous

1. You change some shared code — say `shared-events`, used by several services.
2. You run `mvn clean install` at the root.
3. It says **BUILD SUCCESS**.
4. **But that is a lie.** Five modules were never checked. They were silently skipped.
5. Those services are still built against the old code.
6. You find out much later, while the app is running. Usually as a Kafka message that a service
   suddenly cannot read.

**In one line:** the build said "everything is fine" when it was not. That is worse than a build
that honestly fails, because a failing build warns you straight away.

## Why it blocked other work

Track C is about automated testing. There was no point starting that first. If the build itself
lies about what it checked, then automatic tests sitting on top of it would lie too.

## What we did

**1. Listed all 11 modules** in the root `pom.xml`.

Maven works out the correct build order by itself, from the dependencies between modules. So the
shared libraries always get built before the services that use them. The order you write them in
does not matter.

**2. Marked the root file's settings as doing nothing.**

This was a surprise. The root file declares things like `java.version` and `spring-boot.version`.
**No module reads them.**

In Maven, settings only flow downward from a parent to its children. No module names the root
file as its parent. They all point at `spring-boot-starter-parent` instead, to get Spring Boot's
version management.

So those settings were doing nothing. We kept them but added a clear warning comment, so nobody
edits them expecting a result.

**3. The dead `AuthService` dependency was already gone.**

Four services used to list `AuthService` as a dependency while importing nothing from it. That
had already been removed before this task started. We checked twice: no pom declares it, and no
Java file imports it. Everything still builds.

## How we proved it works

Two checks, not just "it compiled".

**Check 1 — one command builds everything.** `mvn clean install` at the root now builds all 11
modules in about 2 minutes 15 seconds.

**Check 2 — the build now catches a real break.**

We renamed a field in `shared-events` on purpose (`title` → `titleRENAMED`) and ran the build
again:

```
shared-events        SUCCESS
common-security      SUCCESS
common-web           SUCCESS
Eureka               SUCCESS
APIGateway           SUCCESS
AuthService          SUCCESS
UserService          SUCCESS
IncidentService      FAILURE   <-- caught it
NotificationService  SKIPPED
ReportingService     SKIPPED
AiService            SKIPPED
BUILD FAILURE
```

IncidentService is one of the four services the old build used to skip. Before the fix, those
seven modules above it would all have passed, and the build would have printed BUILD SUCCESS.

We put the rename back straight away and checked the build was green again.

## What we did NOT do

**Make every module point at the root file as its parent.**

That would make the shared settings real. But it is not a simple change:

- Modules get Spring Boot's version management **from** `spring-boot-starter-parent`. If we
  repoint them, the root file has to import the Spring Boot BOM instead, or every version breaks
  at once.
- `common-security` is on Spring Boot **3.3.2** on purpose, not 3.5.11.
- `Ai-Service` is on Java **17** on purpose, not 21.

So it forces decisions about two deliberate exceptions. That is its own task, not a side effect
of fixing the build.

## One thing to know

The root build needs `-DskipTests` to pass.

Eight services have auto-generated `contextLoads()` tests. Those need MySQL, Kafka, Eureka and
the environment variables all running. Only `common-web`'s 12 tests run on their own.

That is a Track C problem, not something this change broke.

---

# Track B, C and D — not decided yet

You mentioned these tracks but we never discussed what is in them.

**Do not guess.** Ask before starting any of them.

---

# Change log — every file we changed

This is your review record. Each entry says the file and what changed.

---

## 2026-08-28 — First look at the project

Read the whole codebase. Wrote `CLAUDE.md` — what the code looks like today.
Wrote this file. Agreed the order: A1 → A2 → A4. A3 skipped.

**No code changed.**

---

## 2026-08-28 — A1 done (error handling)

### New module: `common-web` — 10 new files

| File | What it does |
|---|---|
| `pom.xml` | Boot 3.5.11. Built for Java **17** on purpose, so Ai-Service (still on 17) can use it. All Spring dependencies are `provided`, so this module never drags the servlet stack into the reactive gateway |
| `exception/ApiException.java` | Base class. Holds the error code and the HTTP status |
| `exception/NotFoundException.java` | 404 |
| `exception/ValidationException.java` | 400 |
| `exception/ConflictException.java` | 409 |
| `exception/ForbiddenException.java` | 403 |
| `exception/UnauthorizedException.java` | 401 |
| `dto/ErrorResponse.java` | The 6-field reply shape. Plus an optional `fieldErrors` list, left out completely unless `@Valid` failed |
| `handler/GlobalExceptionHandler.java` | Catches everything. 10 handlers. The last one hides real error messages and returns only a generic sentence plus a traceId |
| `filter/TraceIdFilter.java` | Gives every request a short id. Reuses an incoming `X-Trace-Id` if there is one. Puts it in the logs and the reply. Clears it afterwards, because threads get reused |
| `config/CommonWebAutoConfig.java` + `AutoConfiguration.imports` | Makes the module work just by adding it. Without this, Spring would never find the handler. Our services live under `com.example.*` and this module lives under `com.project.web` |
| `src/test/.../GlobalExceptionHandlerTest.java` | 12 tests. No database needed. Locks the status codes, the field names, and the no-leak rule |

### IncidentService — 7 files (this was the pilot)

| File | What changed |
|---|---|
| `pom.xml` | Added `common-web` |
| `service/IncidentService.java` | Replaced all 13 `RuntimeException`s. Not found → 404. Caller not active → 403. Bad engineer choice → 400, because the client should pick someone else, so it is a request problem not a permission one |
| `client/UserClient.java` | A 404 from UserService now becomes a 404 here, not a 500 |
| `controller/IncidentController.java` | Added `@Valid` to all 4 request bodies. There was **none** before, so the DTO rules never ran |
| `dto/AssignIncidentRequestDTO.java` | Added `@NotNull` on the engineer id |
| `dto/UpdateIncidentStatus_RequestDTO.java` | Replaced `@Column(nullable=false)` — a database annotation that does nothing on a DTO — with a real `@NotNull`. Added length limits |
| `dto/UpdateIncidentMetadate_RequestDTO.java` | Added length limits matching the database columns |

### AuthService — 13 files

| File | What changed |
|---|---|
| `pom.xml` | Added `common-web` |
| `controller/TokenVersionController.java` | User not found → 404 |
| `controller/PasswordController.java` | Added `@Valid`. It had none |
| `service/LogoutUserService.java` | 2 × user not found → 404 |
| `service/ChangePasswordService.java` | User not found → 404. Wrong old password → 401 |
| `service/RegisteringUserService.java` | Duplicate username or email → 409. **Left alone on purpose:** the missing-role error stays a 500, because missing seed data is our fault, not the caller's |
| `service/LoginUserService.java` | ⚠️ **Changes what clients see.** Login failures used to return their own private shape. Now they use the standard one. Also: unknown email and wrong password now give the **same** message. The old one said "email not exist", which let anyone check whether an account existed |
| `service/UpdateCredentialsService.java` | ⚠️ **Changes what clients see.** Plain text replies like `"Email already exists"` are gone. A taken name is now **409**, not 400 |
| `service/UpdateRoleService.java` | User not found → 404. A bad role name is now a 400 listing the valid roles, instead of a 500 |
| `dto/ChangePasswordDTO.java` | Had **no** rules at all. Added them |
| `dto/SignupFormDTO.java` | Added the missing `@NotBlank` on password. Without it, a null password passed validation and reached the encoder |
| `dto/LoginFromDTO.java` | Same missing `@NotBlank` on password |
| `dto/UpdateUserRolesRequestDTO.java` | Added `@NotEmpty` on the roles list |

### UserService — 5 files

| File | What changed |
|---|---|
| `pom.xml` | Added `common-web` |
| `service/UserService.java` | Replaced all 18 `RuntimeException`s. **The important one:** `LimitedUserInfoForValidation` now returns 404. That is what makes IncidentService's 404 work end to end. The two changes only work together |
| `controller/UserController.java` | Added `@Valid` to 3 request bodies |
| `dto/StatusRequestDTO.java` | Added `@NotNull` |
| `dto/AssignEngineerRequestDTO.java` | Added `@NotNull` on both ids |

### ReportingService — 2 files

| File | What changed |
|---|---|
| `pom.xml` | Added `common-web` |
| `service/ReportingService.java` | Report not found → 404. This service had only one error to fix |

### Ai-Service — 1 file

| File | What changed |
|---|---|
| `pom.xml` | Added `common-web`. It had no errors to replace, but it now gets the shared handling for bad UUIDs, broken JSON and access-denied |

### APIGateway — 1 file, hand-written, does not use `common-web`

| File | What changed |
|---|---|
| `filter/JwtGatewayFilter.java` | The gateway runs on WebFlux, where the shared handler does nothing. So we copied the same JSON shape by hand. It used to send a status code with an **empty body**, so clients got nothing to show or log. ⚠️ **Behaviour change:** if AuthService is unreachable, it now returns **503, not 401**. The old code told users their token was bad and sent them to log in again, which hid a real outage. It also creates an `X-Trace-Id` and passes it on, so one id covers the whole chain |

### Not changed, on purpose

- **NotificationService** — checked: it has no HTTP endpoints at all. Only Kafka. Nothing for the
  handler to catch. It will pick this up if it ever gets an API.
- **Eureka** — no endpoints of its own.

---

## 2026-08-29 to 2026-09-01 — A2 done (database migrations)

### Every service that has a database — 3 changes each

| File | What changed |
|---|---|
| `pom.xml` | Added `flyway-core`. Plus `flyway-mysql` for the 5 MySQL services, or `flyway-database-postgresql` for Ai-Service. **Both parts are needed.** `flyway-core` alone fails with "Unsupported Database" |
| `application.properties` | `ddl-auto=update` → `ddl-auto=validate`. Added the Flyway settings, including `baseline-on-migrate=true` |
| `db/migration/V1__baseline.sql` | **New.** The database shape, copied from the real running database |

### The extra SQL files

| File | What it does |
|---|---|
| `AuthService/.../V2__seed_roles.sql` | **New.** Adds the 3 roles. Without them signup fails, because the code looks up ROLE_ENGINEER and throws if it is missing |
| `UserService/.../V2__seed_roles.sql` | **New.** Same 3 roles, different reason. When a `user.registered` event arrives, this service looks up roles too. Without them signup would look successful, but the user would never appear here. Nobody would be told |

Both use `INSERT IGNORE`, so they are safe to run on a database that already has the rows.

### Fix to an already-written file

| File | What changed |
|---|---|
| `AuthService/.../V1__baseline.sql` | You added comments using `--text` with no space. MySQL needs `-- text`. 26 lines were invalid SQL, including one **inside** the CREATE TABLE. It did not break anything here, because V1 never runs on this machine. But it would have broken any new laptop. Fixed by adding one space per line. No words changed |

### Docker and infrastructure

| File | What changed |
|---|---|
| `kafka-docker/docker-compose.yml` | Kafka was open to the whole network on port 9092, with no password. Now locked to this machine. ZooKeeper's port removed completely — Kafka reaches it inside Docker, so it never needed publishing |
| `pgvector-docker/docker-compose.yml` | **New.** The pgvector container was started by a hand-typed command. Its settings lived only inside Docker. Now written down. The password is **not** in the file. It reads an environment variable |
| `.gitignore` | Added `db-backups/` and `*.dump`, so database dumps never get committed |

### Documentation

| File | What changed |
|---|---|
| `FLYWAY-NOTES.md` | Your notes, moved into the project. Fixed 3 things that were slightly wrong. Added 6 things that catch people out later. Part 2 is a log of every migration we write |
| `ERROR_HANDLING_FLOW.md` | **New.** How errors work, with one example followed all the way through |
| `BUGS-TO-FIX-LATER.md` | Added E7, E9, E10, E11, E12, E13 |
| `CLAUDE.md` | Rewrote the schema section. It still said "No Flyway". Marked issue 41 as fixed |

---

## 2026-09-01 — A4 done (fix the build)

| File | What changed |
|---|---|
| `pom.xml` (root) | Now lists all 11 modules, not 6. Added comments explaining why the list must stay complete. Marked the settings block as doing nothing, with the reason |
| `CLAUDE.md` | Marked issues 23 and 24 as fixed |
| `PROGRESS.md` | This file. A4 section added |

**Note:** the root `pom.xml` was already fixed when we checked it. The work in this session was
proving it actually works, including deliberately breaking `shared-events` to confirm the build
now catches it.

---

## 2026-09-01 — This file rewritten

`PROGRESS.md` rewritten in simpler English. **No facts changed, only the wording.**

The old version used long sentences and hard words, and was difficult to read.
Rule 6 was added to the agreed rules so this stays that way.
