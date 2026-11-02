# PROGRESS.md — What we are fixing in this project

**Read this file first when you come back to this project.**

It says three things:
1. What we already fixed.
2. What we have not fixed yet.
3. Why we made each choice.

Other files that go with this one:
- `CLAUDE.md` — what the code looks like today, and a list of known problems.
- `BUGS-TO-FIX-LATER.md` — problems we found but chose not to fix yet.
- `notes/` — personal learning notes (not tracked by git - see below). Includes
  `FLYWAY-NOTES.md`, `ERROR_HANDLING_FLOW.md`, and topic notes on things learned along the way.

Last updated: 2026-10-24

---

## Where we are right now

| Task | What it is | Status |
|---|---|---|
| A1 | Error handling | ✅ Done — 2026-08-28 |
| A2 | Database migrations (Flyway) | ✅ Done — 2026-09-01 |
| A3 | Config and profiles | ⏸️ Skipped on purpose |
| A4 | Fix the build | ✅ Done — 2026-09-01 |
| B1 | Stop people faking an Admin | ✅ Done — all 3 steps, 2026-09-02 |
| B2 | Kafka dead letter topics | ✅ Done — 2026-09-08 |
| B3 | Timeouts, resilience, the gateway hot path | ✅ Done — all 5 steps, 2026-09-16 |
| B4 | Input checks | ✅ Done — all 7 steps, 2026-09-22 |
| B5 | Rate limiting | ✅ Done — all 4 steps + 1 bug found and fixed, 2026-09-28 |
| C1 | Logging and trace ids | ✅ Done — steps 1, 1b, 2, 2b, 3 on 2026-09-30 (step 4, Zipkin/Prometheus, left for later) |
| LLD re-check | Every LLD file read against the code, 3 doubtful claims tested | ✅ Done — 2026-10-01 (10 new issues, 5 wrong claims corrected) |
| Outbox (Phase 2) | Events can no longer be lost between "saved" and "sent" | ✅ Done — all 6 steps (module 2026-10-12; 3 services 2026-10-13; proved on the real system and documented 2026-10-14) |
| SLA notifications (Phase 3, log only) | SLA alerts are saved per person and visible with a GET call - no email | ✅ Done — all 7 steps 2026-10-17 to 2026-10-19 (the step 4 SQL runs at your next start) |
| Testing guide (2026-10-19) | `notes/testing-in-our-project.md`: why / what / how / is it safe, with the proof re-run on every module | ✅ Written |
| Option B (2026-10-19) | The 8 auto-generated `contextLoads()` tests no longer touch the real system | ✅ Done - "run all tests" is safe on every service (proof below) |
| Small bugs (2026-10-19) | Issues 4, 3, 56, 64, 1 - one at a time, test first | ✅ All 5 done (bug 5 on 2026-10-22) |
| Auth v2 - RSA keys (2026-10-23) | Only AuthService can create tokens; the gateway can only check them | ✅ Done - all 6 steps. Step 5 (real keys + hand test through the gateway) passed 2026-10-26 |
| BCR-02 - refresh token in an HttpOnly cookie (2026-11-02) | For the browser frontend: the token never appears in JSON | ✅ Code + tests done on branch `auth-refresh-cookie` (AuthService 94, gateway 78, plus the gateway log leak fix), **not committed or merged**, not tried by hand - see "BCR-02" below |
| Auth v2 part B - refresh tokens (2026-10-27) | Access token 15 min + refresh token 7 days, used once, cancelled on logout | ✅ Code + tests done (AuthService 65, gateway 69). **Not yet tried by hand on the real system** (Flyway `V6` runs at the next AuthService start) - see "Auth v2 part B" below |
| Issue 68 + security batch (2026-10-28) | Wrong old password counts in the lockout; issues 49, 11, 10, 5, 7, 2, 51 | ✅ Code + tests done, all test-first (AuthService 80, gateway 76, all 6 services green). **Not yet tried by hand** - see "Issue 68 and the security clean-up batch" below |
| AI service plan (2026-11-01) | Improve suggestions, storage and engineer ranking, in 4 phases | ✅ **Phases 1, 2 and 3 done** (Ai-Service 92 tests, no Gemini needed). **Not tried by hand, Gemini never really called. Flyway `V2` clears the AI memory and `V3` adds a table at the next Ai-Service start.** Phase 4 to do - see "AI service improvement plan" below |
| Correctness batch (2026-11-01) | Issues 62, 61, 63, 57, 58, 38 | ✅ Code + tests done, test first where the old code could fail (Reporting 49, Incident 35, User 9). **Not yet tried by hand** - see "Correctness batch" below |
| Role management (2026-10-21) | Issue 67: first admin from a setting, last-admin guard, audit row | ✅ Done (Flyway `V5` not yet run on your DB) |
| SLA values (Phase 1) | Real SLA times for all 4 priorities, CRITICAL included (issues 19 + 59) | ✅ Done — 2026-10-17, test first. Old rows are NOT recalculated (issue 66) |
| Lockout fix (Phase 1) | 5 wrong passwords really lock the account, and the lock ends after 15 minutes (issues 55 + 6) | ✅ Done — 2026-10-14, test first (not yet tried by hand on the real system) |
| Track C (rest) | Not decided yet | ❓ Need to discuss |
| Track D | Not decided yet | ❓ Need to discuss |

**Track A and Track B are finished. Track C has started: C1 is done. The rest of Track C and Track D are not decided yet.**

---

## What is left (pending tasks) - written 2026-10-24

Tick a line and move it to the table above when it is done. Issue numbers are the ones in `CLAUDE.md` section 9.

### 1. Finish what is already built (owner's side)

| # | Task | Status |
|---|---|---|
| 1.1 | **Auth v2 step 5**: make the real RSA key pair, set `jwtprivatekey` (AuthService) and `jwtpublickey` (gateway), restart, test by hand | ✅ Done 2026-10-26 - keys made outside the project, set in IntelliJ; signup, login, token use (200), edited token (401), logout, token after logout (401) all passed through the gateway |
| 1.2 | **Commit** the Auth v2 (RSA) work | ✅ Done 2026-10-28 - commit `7219fc7` (RSA + refresh tokens together, plus the pgvector schema backup) |
| 1.4 | **Hand test of refresh tokens** (owner) | ⏳ owner will run it; steps in section "Auth v2 part B" below |
| 1.3 | **Real-system check of earlier work** (do it together with 1.1, the services restart anyway). Back up the databases first | ⏳ never run on the real system: migrations Auth `V4` + `V5`, Incident `V4`, Notification `V2`; the lockout; first admin + role guards; assign rules; the 409 when two people save at once; the password change; SLA timers (test D); `GET /notifications/me`. Guide: `notes/sla-notifications.md` section 8 |

### 2. ~~Next big piece: Auth v2 part B - refresh tokens~~ ✅ built 2026-10-27 (hand test still to do - section "Auth v2 part B" below)

- A short login token (about 15 minutes) + a long refresh token kept **hashed** in the database. RSA only controls who can
  CREATE tokens; refresh tokens are what limit a STOLEN token.
- Needs: a new table (Auth Flyway `V6`), `RefreshTokenService`, `POST /auth/refresh`, login returns two tokens, logout and
  password change cancel refresh tokens, a gateway rate-limit rule for `/auth/refresh`, a lock against two refreshes with the
  same token at once. Each refresh token is used ONCE (rotation); reuse of a used one cancels the whole chain.
- Whatever calls the API must handle "401 -> refresh -> retry".
- Size: about 3-4 days with tests. Plan first, then your "go". Decisions to make: access life (suggest 15 min), refresh life
  (suggest 7 days), how the client receives it (JSON body now; `HttpOnly` cookie for a browser front end later).

### 3. Small decisions waiting for the owner

| Issue | Question |
|---|---|
| ~~68~~ | ✅ Decided 2026-10-28: the SAME lockout. Done the same day |
| 66 | Old `incident_report` rows keep the old test-value SLA deadlines. Recalculate them, or leave them? |

### 4. Known gaps (not started), roughly by importance

**Correctness**

| Issue | What is wrong |
|---|---|
| ~~62, 61, 63, 57, 58, 38~~ | ✅ All done 2026-11-01 (hand test still to do) |

**Security and safety**

| Issue | What is wrong |
|---|---|
| ~~49~~ | ✅ Done 2026-10-28: 10 s for every route, 60 s for AI |
| 14 | Signup activates the account at once, no email check |
| ~~11, 10~~ | ✅ Done 2026-10-28 |
| ~~5, 7, 2~~ | ✅ Done 2026-10-28 (logout and the 2 AI endpoints are POST now) |
| - | HTTPS between the client and the gateway is not covered by anything yet |
| ~~51~~ | ✅ Done 2026-10-28 |

**Features not built**

| Item | Note |
|---|---|
| Real email sending (issue 16) | deliberately later; for now logs + database rows |
| ~~AI classification (issue 17)~~ | ✅ Done 2026-11-01: `POST /ai/classify`. Issue 50 is moot |
| History of every root cause | `notes/LLD/LLD-improvements.md` E7 |
| An API to READ the role audit rows | today SQL only (issue 67) |
| The client sends back the incident `version` (`If-Match`) | covers a screen left open for minutes (issue 64) |
| Key rotation / a JWKS endpoint | only if keys must change without logging everyone out |

**Ops and tidy-up**

| Item | Note |
|---|---|
| No CI, no Dockerfiles, no profiles (issue 42) | |
| Micrometer tracing / Zipkin (issue 53) | deliberately left for later |
| The trace-id rules exist twice (issue 54) | gateway has its own copy |
| Gateway log lines write the trace id by hand (issue 52) | |
| Dead commented-out JWT files (issues 20, 28) | `AuthService/util/JwtUtil.java`, common-security `JwtUtil`/`JwtFilter` - harmless |
| Shorten the long comments in older work (outbox, earlier tracks) | offered, not started |
| Track C (rest) and Track D | not decided yet |

### Suggested order

Updated 2026-10-28 (1.1, 1.2 and refresh tokens are done):
1. Owner: hand test of refresh tokens (1.4) together with the real-system check (1.3). Back up the databases first.
2. Decide issue 66 (68 is done).
3. ~~Security clean-up batch: 49, 11 + 10, 5 / 7 / 2, 51~~ ✅ done 2026-10-28 (hand test still to do).
4. ~~Correctness: 62, 61, 63, 57, 58, 38~~ ✅ done 2026-11-01 (hand test still to do).
5. Getting ready to deploy: issue 42 (Dockerfiles, profiles, CI) and HTTPS.
6. Signup email check (14) together with real email sending (16).
7. Later: Zipkin (53), trace-id tidy-up (52, 54), dead JWT files (20, 28), AI (17, 50).

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

# Track B — Security and reliability

We agreed this list on 2026-09-01. There are 5 items. B1 is by far the most important.

| Item | What it is | Status |
|---|---|---|
| B1 | Anyone could pretend to be an Admin | ✅ Done |
| B2 | Kafka messages can get stuck forever | ✅ Done |
| B3 | One slow service can freeze everything | ✅ Done |
| B4 | Missing input checks | ✅ Done |
| B5 | Nothing stops someone spamming the app | ✅ Done |

---

## B1 — Anyone could pretend to be an Admin 🔴

### How your system works today

1. The gateway checks your login token.
2. If it is good, the gateway adds three headers: `X-User-Id`, `X-Roles`, `X-User-Email`.
3. The gateway passes the request on to a service.
4. The service trusts those headers. It never checks the token itself.

This is a normal design. It is fine **as long as nobody can reach a service directly**.

### The real problem

Every one of your services listens on `0.0.0.0`. That means "any network can reach me".

So somebody who can reach your laptop can skip the gateway completely:

```
curl -H "X-User-Id: 000..." -H "X-Roles: ROLE_ADMIN" http://your-host:8085/incidents
```

No password. No token. The service believes it, because that is exactly what the gateway would
have sent.

This is the same problem Kafka had, and the same fix works.

### Two things we checked, and they are NOT problems

We looked at both before planning. Both turned out fine:

**1. Fake headers cannot sneak through the gateway.** The gateway uses `builder.header(...)`, and
that method *replaces* a header instead of adding a second one. So a fake `X-Roles` sent by a
client gets overwritten by the real one before the request goes onward.

**2. `/auth/**` skipping the token check is safe today.** Those routes only reach login, signup
and token-version. We read all three. Login and signup read the request body. Token-version reads
the URL. **None of them read a header.** So a fake header there does nothing.

It is still worth stripping headers on `/auth/**` later, but only to stop a future mistake. Not
because anything is broken now.

### What we decided NOT to do, and why

The first idea was: make every service decode the login token itself.

**We rejected it.** It copies the gateway's job into 6 places. Checking the token is the gateway's
whole purpose. Doing it 7 times is redundant. It would also mean giving the signing secret to 4
more services that do not have it today.

The lighter plan below gives almost the same protection, for much less work and much less risk.

### The plan — 3 steps

**Step 1 — Lock the doors.** Make every service listen on `127.0.0.1` instead of `0.0.0.0`.
Now only your own laptop can reach port 8085. Nobody else on the network can even try.

This is what real companies do, just with bigger tools: a private network, a firewall, or a
Kubernetes rule. On one laptop, `127.0.0.1` does the same job.

⚠️ **This step has a trap.** Every service has `eureka.instance.prefer-ip-address=true`, so it
tells Eureka "reach me at 192.168.x.x". If we bind to `127.0.0.1` but keep advertising the LAN
address, the gateway would call an address nothing is listening on. **Every service call would
fail.**

So each service needs **two** lines, not one:

```
server.address=127.0.0.1
eureka.instance.ip-address=127.0.0.1
```

### Step 1 result — ✅ done and proven 2026-09-02

We did not just assume it worked. We ran the real attack from both sides:

```
From the network  (192.168.x.x:8085)  ->  CONNECTION REFUSED
From this laptop  (127.0.0.1:8085)     ->  200 OK
```

The first line is the win. That is the exact fake-header request from the plan above. Before this
change it would have worked. Now the attacker cannot even open a connection.

All 7 ports moved from `0.0.0.0` to `127.0.0.1`. Eureka confirms every service now advertises
`hostName=127.0.0.1`.

**Two things worth knowing:**

**1. The Eureka dashboard does not show this.** It shows the `instanceId`, which is just a name
tag shaped like `machine-name:SERVICE:port`. It never changes. The field that matters is
`hostName`, which you can see at `http://localhost:8081/eureka/apps`.

**2. The fake header STILL works from this laptop** (that 200 above). That is not a bug. Step 1
only means "you cannot reach the door". It does not yet mean "the door checks who you are".
That is exactly what step 2 adds.

---

### Step 2 result — ✅ done 2026-09-02

**What we built.** The gateway now sends a secret word with every request it passes on. Each
service checks that word before it believes the `X-User-Id` and `X-Roles` headers.

The service does **not** decode the login token. It only checks that one word matches. About ten
lines of real code.

**What happens when the word is wrong or missing.** The service does not reject the request
itself. It simply does not log anybody in. The request carries on with no user attached, and each
service's own `SecurityConfig` decides what to do with it. Protected pages then reject it. Open
pages like `/actuator/health` still work.

We chose this over "reject straight away" so that all the "who is allowed where" rules stay in one
place, instead of being split between the filter and `SecurityConfig`.

**Two small extras we did at the same time:**

1. **The gateway now wipes any identity headers the caller sent**, before writing its own. It
   already overwrote them, so this fixes nothing today. It stops a future bug: if someone later
   writes a path that sets only two of the three headers, the third can no longer survive from the
   caller.
2. **`/auth/**` now gets the same treatment.** We still skip the token check there, because login
   and signup have no token yet. But the headers get wiped and the secret gets added, so every
   request the gateway sends now looks the same. One rule is easier to reason about than two.

**A detail worth knowing: `MessageDigest.isEqual`, not `equals`.**

A normal string comparison stops at the first character that differs. So a wrong guess starting
with the right letter takes very slightly longer to reject than one starting with a wrong letter.
Somebody measuring those tiny differences over thousands of tries could work the secret out one
letter at a time. That is called a timing attack.

`MessageDigest.isEqual` always checks every byte, so every wrong answer takes the same time and
there is nothing to measure. It costs nothing, so we used it.

**No default value, on purpose.** If the `internalsecret` variable is missing, the service refuses
to start with a clear message. For a security setting a loud failure is much better than a service
that quietly starts and lets everyone in.

**Files changed:**

| File | What changed |
|---|---|
| `common-security/.../filter/HeaderAuthFilter.java` | Checks the secret before trusting any headers |
| `common-security/.../config/SecurityAutoConfig.java` | Passes the secret into the filter |
| `APIGateway/.../filter/JwtGatewayFilter.java` | Sends the secret. Wipes caller headers. Covers `/auth/**` too |
| 7 x `application.properties` | Added `internal.secret=${internalsecret}` (gateway + 6 services) |

Eureka needed nothing. It has no `HeaderAuthFilter`, and the gateway never routes to it.

**New environment variable:** `internalsecret`. Must be the **same value everywhere**, or every
request gets rejected.

---

**Step 2 — Add a secret handshake.** The gateway sends a secret word with every request. Services
check the word before trusting the headers.

The service does **not** decode any token. It just checks that one header equals one value. About
ten lines of code.

Why bother, when step 1 already blocks outsiders? Because step 1 protects you from the network,
and step 2 protects you from a mistake. Someone opens a firewall later, or another program runs
on the same machine. Two layers. If one fails, the other still holds.

### Step 3 result — ✅ done 2026-09-02

We split this into two parts on purpose, so the system worked after each one.

**Part A — teach every caller to identify itself.** Nothing was enforced yet, so nothing could
break. We only added information to requests.

`HeaderAuthFilter` learned there are two kinds of caller, not one:

| Caller | Sends | Gets |
|---|---|---|
| A person, through the gateway | `X-User-Id` + `X-Roles` | their own roles |
| One of our services, calling directly | `X-Internal-Service` | `ROLE_INTERNAL_SERVICE` |

Both must also send the secret word from step 2. The second header is what separates them.

**Why a second header at all?** The gateway sends the secret too. If the secret alone granted
the service role, then every ordinary request through the gateway — even a plain login — would
count as a service.

**Part B — lock the doors.** Changed the rules in two `SecurityConfig` files.

**The order mattered.** Our services called each other with **no identification at all** — every
`.header(...)` line in the clients was commented out. Locking first would have locked our own
services out. Part A first meant we could confirm the headers were arriving (a
`Internal service call from INCIDENTSERVICE to /internal/validation/...` line in the UserService
log) before anything started being enforced.

**Where the headers get added:**

| Service | How | Why different |
|---|---|---|
| IncidentService, NotificationService | `defaultHeader(...)` on the `WebClient.Builder` bean | Set once, cannot be forgotten on a new call later |
| Ai-Service | A Feign `RequestInterceptor` (new file) | Feign has no builder to attach to — you describe the call as a Java interface and Feign writes the code |

**Is it safe to put a secret on every call that builder makes?** Yes, because the builders are
`@LoadBalanced`. That means they can only reach services registered in Eureka — they resolve
names like `USERSERVICE`, not real web addresses. They physically cannot call an outside website,
so the secret cannot leak to a third party. If anyone ever removes `@LoadBalanced`, this needs
rethinking.

**⚠️ One behaviour change for real users:** `/reports/**` now needs ADMIN or SUPERVISOR. It used
to be open to everyone with no login at all. **Engineers will now get 403 from report pages.**
That was a deliberate choice — those endpoints show every engineer's performance numbers, and one
engineer should not browse another's.

That choice also closed a side door: `ReportingController` repeats two of the internal endpoints
at `/reports/engineer-performance/...`. Locking `/internal/**` alone would have left the same
data reachable through that second path (CLAUDE.md issue 30).

**Files changed:**

| File | What changed |
|---|---|
| `common-security/.../HeaderAuthFilter.java` | Knows two kinds of caller. New constants `INTERNAL_SERVICE_HEADER`, `INTERNAL_SERVICE_ROLE` |
| `APIGateway/.../JwtGatewayFilter.java` | Also wipes `X-Internal-Service` from incoming requests |
| `IncidentService/config/SecurityConfig.java` | `WebClient.Builder` now sends both headers |
| `NotificationService/config/SecurityConfig.java` | Same |
| `Ai-Service/config/FeignInternalAuthConfig.java` | **New file.** Feign interceptor, same job |
| `UserService/config/SecurityConfig.java` | `/internal/**` → `hasRole("INTERNAL_SERVICE")` |
| `ReportingService/config/SecurityConfig.java` | `/internal/**` → `hasRole("INTERNAL_SERVICE")`, `/reports/**` → `hasAnyRole("ADMIN","SUPERVISOR")` |

No new environment variable — `internalsecret` from step 2 is reused.

---

## B1 is complete — what it adds up to

Three locks, each covering a different failure:

| Lock | Protects against | Fails if |
|---|---|---|
| 1. Services listen on `127.0.0.1` only | Anyone on the network reaching a service | This ever moves to a real server, or a firewall is opened |
| 2. Shared secret on every request | A program on this machine faking a user | The secret leaks |
| 3. `/internal/**` needs a service role | Even a real logged-in person reading everyone's emails | — |

Any one of them can fail and the others still hold.

**The weakness we are still accepting:** the JWT uses one shared secret, so any service that can
*check* a token could also *create* one. The proper fix is public/private key signing. That is a
bigger job — key files, key rotation, probably a JWKS endpoint — and is deliberately left for
later.

---

**Step 3 — Close the internal doors.** The `/internal/**` endpoints exist so services can talk to
each other. Today they are open to anyone. They leak user emails, team lists and performance data.

⚠️ **This step has a trap too.** Your services currently call each other with **no login info at
all**. Look at `IncidentService/client/UserClient.java`. So if we lock the doors first, we break
our own services. Caller and receiver must change together, in one commit.

### Files this touches

**Step 1** — 8 files, all `application.properties`:

```
Eureka, APIGateway, AuthService, UserService,
IncidentService, NotificationService, ReportingService, Ai-Service
```

**Step 2**:

```
APIGateway/.../filter/JwtGatewayFilter.java       <- send the secret
common-security/.../filter/HeaderAuthFilter.java  <- check it before trusting headers
8 x application.properties                        <- read the secret from an env variable
```

**Step 3**:

```
IncidentService/client/UserClient.java            <- send the secret
NotificationService/client/UserClient.java        <- send the secret
Ai-Service/feignClients/...                       <- send the secret
UserService/config/SecurityConfig.java            <- stop allowing /internal/** for everyone
ReportingService/config/SecurityConfig.java       <- same, plus /reports/**
```

### What does NOT change

Every controller stays exactly the same. `auth.getName()` still returns the user UUID, just as
today. We are not replacing `HeaderAuthFilter`. We are only adding one check before it trusts
what it was given.

### The weakness we are accepting for now

Your tokens use one shared secret. Any service that can *check* a token could also *create* one.
So if a service is ever hacked, the attacker could make an admin token.

The proper fix is public/private key signing. AuthService signs with a private key, services check
with a public key. Then a hacked service can only read tokens, not make them.

That is a much bigger job: key files, key rotation, probably a JWKS endpoint. **Not now.**
Written down so it stays a choice, not an accident.

---

## B2 to B5 — where each one stands

B2 and B3 are done. B4 and B5 are still short summaries - full detail when we
get to them.

**B2 — Kafka messages can get stuck.** ✅ **DONE 2026-09-08.**

### What was wrong

If a service could not process a message, Kafka handed it back forever. And a Kafka partition is
a strict queue, so that one message blocked every message behind it. Notifications would stop
completely, with no error and no alert.

### What we built

A new shared module, **`common-kafka`** (our 12th module). One file. Every consumer service gets
it by adding the dependency - nothing to configure per service.

```
message fails -> retry 3 times, 1 second apart -> still failing?
              -> copy it to <topic>-dlt -> mark the original done, queue moves on
```

### The part that is easy to miss

A message can fail in two different places, and they need different handling:

| Where | Example | Handled by |
|---|---|---|
| Inside the listener | database down | `DefaultErrorHandler` alone |
| Before the listener runs | bad JSON, unknown class | needs `ErrorHandlingDeserializer` too |

The second one is the actual poison-pill case. Without `ErrorHandlingDeserializer` there is no
message object to hand over - it never got built - so it keeps spinning forever. That needed one
extra line in each service's properties.

### Why two producers for the dead letter topic

A serializer turns a value into bytes, and only one runs per message - they do not chain. The
failed message is in one of two shapes:

| Failure | We hold | Producer | What it does |
|---|---|---|---|
| Inside the listener | a Java object | `dltJsonTemplate` | object → JSON → bytes |
| During deserializing | raw bytes | `dltRawBytesTemplate` | nothing - sends bytes as they are |

The second is deliberate: the dead letter topic then holds exactly what arrived, so you can read
the broken JSON yourself.

We build both producers inside `common-kafka` rather than reusing each service's, because a
consumer-only service (NotificationService) has no producer configured at all - Spring's default
would be `StringSerializer`, which cannot send a Java object.

### Also fixed: `trusted.packages=*`

Every message carries a header saying which Java class it is. `*` meant "build any class named",
which is a way in for anyone who can write to our topics. Narrowed to `com.project.events` in all
four consumers.

**Worth knowing:** this only affects Kafka. HTTP `@RequestBody` DTOs use a completely separate
Jackson converter - look at the property prefix, `spring.kafka.consumer.*`. Narrowing it cannot
break any REST endpoint.

### Proved it works

Added a deliberate `throw` in NotificationService, created an incident, and watched it: 3 retries
exactly 1 second apart, then the event landed in `incident.created-dlt`, and the offset moved on.

Two things learned while testing:
- The topic is `<topic>-dlt`, not `.DLT` as first assumed. Always check the real topic list.
- Duplicate-key errors appearing during retries are **normal** - the first attempt saved a row,
  so later retries hit the unique constraint and the idempotency guard catches them. Two safety
  mechanisms working together, not a failure.

**Files:** new `common-kafka/` module (pom, `KafkaErrorHandlingAutoConfig`, autoconfig imports);
root `pom.xml` (module list); 4 x `pom.xml` and 4 x `application.properties` for
NotificationService, UserService, ReportingService, Ai-Service.

### Also fixed: the RAG duplicate bug (2026-09-08)

Ai-Service stored each resolved incident with `new Document(cleanedText)`, which gives the
document a **random id every time**.

Kafka delivers "at least once", so the same event can arrive twice. With a random id, the same
incident became two separate documents. A search would find both, count the same advice twice,
and the AI's suggestions would slowly get worse - with no error and nothing in the logs.

**Fix:** use the incident id as the document id.

```java
new Document(event.getIncidentId().toString(), cleaned, metadata)
```

This works because the vector store writes with
`INSERT ... ON CONFLICT (id) DO UPDATE SET ...` (verified by reading the SQL out of the
spring-ai-pgvector-store jar, not assumed). Same id arriving again overwrites the row instead of
adding a second one.

**Why overwrite rather than skip early?** Skipping would save the paid Gemini embedding call on a
repeat. But an incident can genuinely be resolved twice - reopened, then resolved differently -
and skipping would leave the old wrong answer in the index forever. Overwriting always keeps the
latest. Redeliveries are rare, so the occasional extra call is worth it.

**File:** `Ai-Service/service/RagIntegrationService.java`.

This closes the last open item in B2.

## B3 — One slow service could freeze everything ✅ Done 2026-09-16

### What was wrong

Not a single network call in this project had a time limit. Not one.

The gateway asks AuthService "is this token still valid?" on **every single request**. If
AuthService went quiet, we waited forever.

### Why that was worse than "slow"

Most people picture threads piling up. In the gateway that is **not** what happens — it is
reactive, so a thread waiting for a reply is released, not held. The real damage is different:

```
AuthService goes quiet
   -> each call borrows a connection and never gives it back
   -> the pool (about 16 by default) empties permanently
   -> new requests queue, and the default wait is 45 SECONDS, then fail
   -> the gateway cannot serve ANY request, to ANY service
   -> and it does NOT recover when AuthService gets better,
      because nothing ever gave up
```

**A ten-second hiccup became an outage lasting until someone restarted it by hand.**

The other six services break the ordinary way instead: they are blocking, so a hung call holds a
Tomcat thread. 200 of those and the service is dead — while still reporting itself healthy,
because nothing crashed.

### The plan — 5 steps

| Step | What | Status |
|---|---|---|
| 1 | Timeouts + a bounded connection pool on the gateway | ✅ Done 2026-09-09 |
| 2 | Timeouts on the other 9 calls, and fix the wrong HTTP client | ✅ Done 2026-09-09 |
| 3 | Cache the token version (stop calling AuthService every request) | ⬜ To do |
| 4 | Circuit breaker + fallback | ⬜ To do |
| 5 | Turn on metrics so we can see steps 1–4 working | ⬜ To do |

The order matters. A cache on top of a hang does not fix the hang — it just makes it rarer, and
therefore harder to find. Timeouts had to be first.

---

### Step 1 result — ✅ done 2026-09-09

Two timeouts and a bounded pool on the gateway's call to AuthService.

| Setting | Value | What it means |
|---|---|---|
| `connect-timeout-ms` | 2000 | "Nobody answered the door" |
| `response-timeout-ms` | 2000 | "They answered but never spoke" — **this is the one that was missing** |
| `max-connections` | 50 | The default is about 16, which surprises people |
| `pending-acquire-max-count` | 100 | 50 working + 100 waiting = 150; number 151 is refused straight away |
| `pending-acquire-timeout-ms` | 2000 | The built-in default is **45 seconds** |
| `max-idle-time-ms` | 15000 | Close before Tomcat does at ~20s, or you get random "connection prematurely closed" errors |

Worst case a user now waits **6 seconds** and gets a clear answer, instead of waiting forever.

**No change was needed in `JwtGatewayFilter`.** It already had error handling that returns a 503.
It simply never fired, because the call never *failed* — it only hung. Now it fails, and code
that was already written starts doing its job.

**A follow-up we only found by testing.** The timeout fired correctly, but the log said:

```
Error fetching token version: null
```

Netty builds its timeout exception with **no message and no stack trace on purpose** — timeouts
are common under load and stack traces are expensive, so it reuses one shared instance. Our
logging called `getMessage()`, which threw away the only information that existed: the class
name.

New `FailureDescription` helper prints the exception type, the whole cause chain, **and which
of the three timeout settings is responsible**. The lesson: *"log the message" is a habit, not a
rule* — when the message is empty, the type and cause chain are the real information.

---

### Step 2 result — ✅ done 2026-09-09

This turned out to be two problems, not one.

**Problem A: no timeouts.** Nine more calls with no limit — 6 in IncidentService, 2 in
NotificationService, 2 in Ai-Service.

**Problem B: the wrong HTTP client.** IncidentService and NotificationService used WebClient and
then called `.block()` on every call:

```java
webClientBuilder.build().get().uri(...).retrieve().bodyToMono(...).block();
                                                                  ^^^^^^^
```

WebClient is built for reactive apps whose whole design is "never sit and wait". `.block()` told
it to sit and wait anyway — all the machinery, none of the benefit. This was already catalogued
as known issue #37 in `CLAUDE.md`.

**The rule we settled on:** the client follows the app's execution model, not personal taste.

| App | How it works | Client |
|---|---|---|
| APIGateway | Reactive (Netty) | **WebClient** — correct already |
| The other 6 | Blocking (Tomcat, one thread per request) | **Feign** |

**Why Feign and not RestClient.** I first recommended RestClient — a smaller change, and it
matches the note already in `CLAUDE.md` issue #37. You pushed back: Ai-Service already uses
Feign, and consistency across services is worth more than saving a few lines. That was the right
call, and it is what we did. All three blocking services now use the same tool.

**What changed per service:**

| Service | Change |
|---|---|
| IncidentService | `UserClient` class → Feign interface; new `FeignInternalAuthConfig` + `FeignErrorDecoderConfig`; `WebClient.Builder` bean removed from `SecurityConfig`; timeouts added |
| NotificationService | Same, **minus** the error decoder (see below) |
| Ai-Service | Timeouts only — no code change; it already used Feign |

**Zero caller changes.** The Feign interfaces kept identical method names and signatures, so all
9 call sites were untouched.

**Why NotificationService got no error decoder.** I wrote one, and the build rejected it —
`common-web` is not a dependency there, because that service has no HTTP endpoints. Rather than
add a dependency to make my file work, I checked what the old code actually did:

```java
new RuntimeException("UserService returned error: " + response.statusCode())
```

No 404 rule at all. Nothing to preserve — and Feign's built-in decoder is better anyway, giving
the status, method, URL **and** response body. Deleting the file was the right answer.

IncidentService *does* have one, because its old code had a real rule worth keeping: 404 means
"no such user", which must reach the caller as a 404 and not a 500. Without that file the
migration would have silently downgraded a 404 into a 500.

**Three facts we verified in the jars rather than assuming:**

- Feign's default timeouts are **10s connect / 60s read** — so Ai-Service was never fully
  exposed the way WebClient's *no* limit left the others. But 60s is still far too long.
- Spring Cloud sets Feign to **`NEVER_RETRY`** — so no accidental retry stacking. This matters:
  3 gateway retries x 3 service retries = 9 real calls to a service that is already struggling.
  **Retry belongs at exactly one layer.** We have not chosen that layer yet (see "Not done in B3").
- The property prefix is **`spring.cloud.openfeign.client.config`** — a wrong prefix would have
  silently applied nothing, which is the worst kind of failure.

**A mistake worth recording.** The first version named the config class `FeignErrorDecoder`, and
the service refused to start:

```
The bean 'feignErrorDecoder' could not be registered.
A bean with that name has already been defined and overriding is disabled.
```

Spring names a scanned `@Configuration` class after the class itself, first letter lowercased.
So the class and its own `@Bean` method both claimed `feignErrorDecoder`. **A config class must
never share its name with a `@Bean` method inside it** — name it after its *job*, not after what
it produces. Fixed by renaming to `FeignErrorDecoderConfig`.

---

### What steps 1–2 add up to

```
BEFORE                                 AFTER
no time limit anywhere        ->       every call has one
one slow service = total,              one slow service = a fast, clear 503
  permanent outage                       and the system heals itself
WebClient bent into blocking  ->       the client matches the app
a timeout logged as "null"    ->       names the exact setting to change
```

### Known gap, still open after B3

A timeout currently surfaces to the caller as a **500**, not a 503. Feign's `ErrorDecoder` only
sees real HTTP replies — a timeout has no reply, so it bypasses it and lands on common-web's
catch-all. Honest, but not as clear as it should be. This was planned for step 4 but step 4 only
covered the gateway, so it is still open (see "Not done in B3").

### Files changed in B3 steps 1–2

**APIGateway (4):** `config/WebClientConfig.java` (timeouts + pool),
`resources/application.properties` (6 tunable values), `utils/FailureDescription.java` (new),
`services/TokenVersionService.java` + `filter/JwtGatewayFilter.java` (logging).

**IncidentService (7):** `pom.xml` (+openfeign), `IncidentServiceApplication.java`
(+`@EnableFeignClients`), `client/UserClient.java` (→ interface),
`config/FeignInternalAuthConfig.java` (new), `config/FeignErrorDecoderConfig.java` (new),
`config/SecurityConfig.java` (WebClient bean removed), `application.properties` (timeouts).

**NotificationService (6):** same list minus the error decoder.

**Ai-Service (1):** `application.properties` (timeouts only).

**Notes (3):** `gateway-timeouts-and-connection-pools.md` (new),
`choosing-between-webclient-and-feign.md` (new), `README.md` (index).

### Step 3 result — ✅ done 2026-09-11

**What it does.** The gateway used to call AuthService on every request to check if a token was
revoked. Now it remembers the answer, using 3 tiers:

| Tier | When | What happens |
|---|---|---|
| 1 — fresh cache | value under 30s old | use it, no call to AuthService |
| live | cache empty/old | call AuthService, save the answer |
| 2 — stale cache | AuthService didn't reply, value under 5min old | use it anyway, log a warning |
| 3 — unavailable | AuthService didn't reply, nothing usable cached | skip the check, log it loudly |

Signature and expiry are still checked before any of this, so even Tier 3 rejects a fake or
expired token — only the extra "was this revoked recently" check is skipped.

**Why an in-memory cache (Caffeine), not Redis.** Redis is itself a network call — we'd be
swapping "a call that can hang" for "another call that can hang". Caffeine is a lookup in our own
memory, so it can never hang. Redis becomes the right choice once we run more than one gateway,
so every instance shares the same cache — not before. When that day comes it goes **behind**
Caffeine, not instead of it.

**A bug we caught while writing this.** If Tier 3 gives back "I don't know" and the code compared
it carelessly, every user would look revoked and get logged out — for as long as AuthService
stayed down. Fixed with `canCheckRevocation()`, which the filter must check before comparing.

**A second, separate case we had to add.** A 404 from AuthService means the user was deleted —
not that AuthService is down. Without treating that separately, a deleted user's old token would
fall into Tier 3 and get waved straight through. New `AuthUserNotFoundException` keeps the two
apart.

**Files:** `pom.xml` (+Caffeine), `services/TokenVersionService.java` (the 3 tiers),
`services/TokenVersionResult.java` (new), `services/AuthUserNotFoundException.java` (new),
`filter/JwtGatewayFilter.java` (uses the new result type), `application.properties`
(3 new settings: fresh-ms, stale-ceiling-ms, max-cached-users).

### Step 4 result — ✅ done 2026-09-16

**Goal:** when AuthService is down, reach the fallback **instantly** instead of waiting 2
seconds on every request.

**What we did:** put a circuit breaker (Resilience4j) around the one HTTP call to AuthService.
The cache and the 3 tiers are untouched.

```
Cache missed → we need a live answer from AuthService
        │
        ▼
Is the breaker OPEN?
   ├─ NO  → make the real call (gives up after 2s if it hangs)
   └─ YES → refuse instantly. Never touch the network.
        │
        ▼
No answer? → same fallback as step 3 (Tier 2 or Tier 3)
```

The breaker's 3 states:

```
CLOSED (normal)
   │  half of the last 10 calls failed
   ▼
OPEN (refuse everything for 10 seconds)
   │  10 seconds pass
   ▼
HALF_OPEN (let 3 test calls through)
   ├─ they work → back to CLOSED
   └─ they fail → back to OPEN
```

**Remember:**
- The breaker only changes how **fast** we reach the fallback. It never changes **what** the
  fallback does.
- The window is 10, not 20+. Step 3's cache made calls to AuthService rare, so a big window
  would take minutes to fill with failures.
- A 404 ("no such user") is **not** a failure for the breaker. AuthService answered correctly.
  `ignoreExceptions(AuthUserNotFoundException.class)`.
- Every state change is logged (`CLOSED_TO_OPEN` etc.).

**Files:** `pom.xml` (+Resilience4j), `TokenVersionService.java`, `FailureDescription.java`,
`application.properties` (`gateway.circuitbreaker.*`).

### Step 5 result — ✅ done 2026-09-16

**Goal:** make steps 1–4 **visible**. They all work silently. A breaker you cannot see is a
breaker you cannot trust or tune.

```
STARTUP   make 4 Counters + 1 Gauge, pin them on the MeterRegistry (the shared noticeboard)
             │
RUNNING   a request is answered by a tier → click that tier's Counter (+1)
             │
ANYTIME   open a /actuator URL → registry reads the counters,
                                 gauge reads the cache size right then
```

**Open these in a browser:**

| URL (on port 8082) | Shows |
|---|---|
| `/actuator/metrics/gateway.tokenversion.lookups` | requests answered per tier (tag `tier`) |
| `/actuator/metrics/gateway.tokenversion.cache.size` | users cached right now |
| `/actuator/circuitbreakers` | breaker state and failure rate |

Healthy system: `stale_cache` and `unavailable` stay at **0**.

**Remember:**
- **Counter** = a tally we click; the number lives inside it; only goes up. **Gauge** = stores
  nothing; reads the cache size live; goes up and down. Counters replaced the old `AtomicLong`s,
  which were private and invisible.
- **Bug we caught:** `CircuitBreaker.of(...)` builds a breaker that works but never shows up in
  `/actuator`. We use `registry.circuitBreaker(...)` instead.
- **Safety:** the `circuitbreakers` page also accepts a POST that can force the breaker open.
  The gateway has no login on `/actuator`. We tested it: without
  `management.endpoints.access.max-permitted=read-only` the POST returned 200 and the breaker
  became `FORCED_OPEN`. With it: 404, breaker stays `CLOSED`.
- The `circuitbreakerevents` page is left off. It shows raw error text that can hold user ids.
- Never use a user id as a metric label. Every new value makes a new metric.
- Only the **gateway** has these pages. The other 6 services still expose only `health`, `info`.

**Files:** `TokenVersionService.java` (counters, gauge, registry; `statsSummary()` removed),
`application.properties` (`management.*`).

### B3 is complete — what it adds up to

```
Step 1  timeouts        → nothing waits forever
Step 2  Feign           → each app uses the right HTTP client
Step 3  cache + tiers   → AuthService is not called every request, and can be down
Step 4  circuit breaker → the fallback is instant
Step 5  metrics         → we can see all of it
```

### Not done in B3 (still open)

- **A Feign timeout shows up as a 500, not a 503.** A timeout has no reply, so Feign's
  `ErrorDecoder` never sees it. Step 4 was meant to fix this and did not.
- **No retries anywhere.** We never chose which layer should retry. Feign is still
  `NEVER_RETRY`. Retry at **one** layer only, or retries multiply (3 × 3 = 9 calls).
- **No circuit breaker on the Feign calls** (Incident, Notification, Ai-Service). Only the
  gateway has one.
- **Ai-Service has no timeout on the Gemini call.**
- **Metrics only exist on the gateway.**

## B4 — Missing input checks ✅ Done 2026-09-22

### What was wrong

Most request bodies had **no rules at all**. ReportingService had none anywhere. A field with no
limit could be:

- **Missing**, and crash something later, far from where the request came in.
- **Too long**, and get rejected by the database as a confusing 500, instead of a clean "too long"
  message.
- **The wrong type**, like `days=abc`, and crash with a raw exception.

The goal of all 7 steps: **catch a bad request at the door, and say exactly what is wrong** —
instead of letting it travel deep into the code and fail somewhere confusing.

### Why order mattered

```
Step 1  teach the error handler to answer these checks with a clean 400
           │  (without this, every later step below still gives a 500)
           ▼
Step 2  add the checks to the biggest, riskiest forms (create/update incident, auth)
           ▼
Step 3  prove the checks actually stop a real crash (not just "looks right")
           ▼
Step 4  same checks for Ai-Service — its bugs cost real money, not just a bad reply
           ▼
Step 5  the same idea for values in the URL, not just the request body
           ▼
Step 6  a size limit for the whole request, at the one door everyone walks through
           ▼
Step 7  write it all down (this step)
```

Step 1 had to come first. `@Min`, `@Max` and `@Size` on a parameter do nothing useful until
something turns their failure into a clean reply — otherwise they just change a 500 into a
*different* 500.

### The plan — 7 steps

| Step | What | Status |
|---|---|---|
| 1 | Teach the shared error handler to answer parameter checks with 400 | ✅ Done |
| 2 | Add length limits to the biggest request bodies (Incident, Auth) | ✅ Done |
| 3 | Prove a missing required field really did crash something | ✅ Done |
| 4 | Add the same checks to Ai-Service (its bugs cost real money) | ✅ Done |
| 5 | Add limits to values in the URL (path and query) | ✅ Done |
| 6 | Cap the whole request body size, at the gateway | ✅ Done |
| 7 | Write it all down | ✅ Done |

---

### Step 1 result — ✅ done 2026-09-19

**Goal:** before this step, a bad `@Min`/`@Max`/`@Size` on a URL value crashed with a raw 500.
Spring's own check (`HandlerMethodValidationException`) was never being caught.

**What we built:** one new handler in the shared `common-web` module, plus a second fix for a
related gap — an unknown URL, a wrong HTTP method, or a wrong `Content-Type` were **also**
landing as unhandled 500s, because they arrive as Spring's own exception types, and our catch-all
did not recognise them.

```
Bad request arrives
   │
   ▼
Is it a @Valid body failure?          → 400, existing handler (unchanged)
Is it a bad @Min/@Max/@Size param?    → 400, NEW handler
Is it a 4xx Spring already knows      → 400/404/405/415, NEW check in the catch-all
   about (bad URL, wrong method...)?
Anything else                         → 500, hidden message (unchanged, the safety rule)
```

**Proved with 6 new tests**, each hitting a real endpoint: a bad query param, a body list that
was too long, the wrong HTTP method, the wrong content type, an unknown URL, and a 200-character
value that should be cut down to a short preview in the error. **Before: 18 tests, 5 failing, all
500. After: 18 of 18 pass**, and the 5 error-log lines for "this is a framework problem, not our
bug" dropped to 0.

**Files:** `common-web/handler/GlobalExceptionHandler.java`,
`common-web/handler/GlobalExceptionHandlerTest.java` (+6 tests).

---

### Step 2 result — ✅ done 2026-09-19

**Goal:** stop obviously-bad create/update requests from ever reaching a service or a database.

**What we found first.** Reading `IncidentService`'s `SLAConfiguration` class, a missing
`priority` on a newly created incident would later crash `ReportingService` — a completely
different service, minutes later, with no obvious link back to the request that caused it. That
became the reason for Step 3.

**What we added:**

| DTO | Rule added | Why |
|---|---|---|
| `CreateIncidentRequestDTO` | `@NotNull` on severity, priority, environment, serviceName, category | Each one is read by another service later (see Step 3) |
| `CreateIncidentRequestDTO`, `UpdateIncidentMetadate_RequestDTO` | `@Size(max=255)` on affectedSource, location | Matches the database column width |
| `SignupFormDTO`, `LoginFromDTO` | username/email/password length caps | Matches the database columns, and BCrypt (see below) |
| `ChangePasswordDTO`, `UpdateCredentialsRequestDTO` | password caps, `@NotBlank` where it was missing | Same reasons |

**One database mismatch found and fixed with a migration.** `root_cause` and
`resolution_notes` were **`varchar(500)`** in the database, but the DTO that updates them allowed
**2000** characters. A value over 500 would have passed our own check and then been rejected by
MySQL — the exact confusing 500 this whole track exists to prevent. Fixed with a new migration,
**`V2__widen_root_cause_and_resolution_notes.sql`**, widening both columns to 2000 to match the
DTO. **You still need to run this migration yourself** — it has not touched the live database.

**Why 72 for every password field.** BCrypt (the password hashing algorithm) silently reads only
the first 72 **bytes** of any password and ignores the rest. A field with no limit could let
someone set a 500-character password, believe all 500 characters matter, and be wrong. Capping at
72 makes the truth match what the user believes.

**Files:** `IncidentService/dto/CreateIncidentRequestDTO.java`,
`IncidentService/dto/UpdateIncidentMetadate_RequestDTO.java`,
`IncidentService/db/migration/V2__widen_root_cause_and_resolution_notes.sql` (new, **not yet
run**), `AuthService/dto/SignupFormDTO.java`, `LoginFromDTO.java`, `ChangePasswordDTO.java`,
`UpdateCredentialsRequestDTO.java`.

---

### Step 3 result — ✅ done 2026-09-20

**Goal:** don't just assume the missing-priority crash from Step 2 is real — prove it.

**What we did.** Ran the real, compiled `SLAConfiguration` class directly (with `jshell`, not a
guess from reading the code):

```
SLAConfiguration.getResolutionSLA_INSeconds("HIGH")  →  864000   (works)
SLAConfiguration.getResolutionSLA_INSeconds(null)     →  NullPointerException
```

Before Step 2's fix, an incident with no priority saved fine, and then silently crashed
ReportingService's SLA calculation in the background — no report, no SLA deadline, and nobody
told.

**Then checked the update path could not bring the bug back.** `applyMetadataUpdatesToIncident`
only sets priority when a value was actually sent:
```java
if (request.getPriority() != null) incident.setPriority(request.getPriority());
```
So an existing priority can never be cleared through an update.

**No code changed in this step.** The fix was already in place from Step 2 — this step only
proved it really works, and checked the one other door (update) that could have undone it.

---

### Step 4 result — ✅ done 2026-09-20

**Goal:** the same idea as Step 2, but for Ai-Service — where a bad request doesn't just give a
bad reply, it can **spend real money** on a paid Gemini API call.

**Two real bugs found while adding the checks, not assumed:**

| Bug | What happened |
|---|---|
| Missing title/description crashed | `Map.of("title", request.getTitle(), ...)` throws if any value is null — proved with `NullPointerException` in `jshell` |
| Missing fields still got sent to Gemini | `request.getTitle() + " " + request.getDescription()` turns a null into the literal text `"null"` — so we would **pay** to search for `"null null"` |

**What we added:** `title` and `description` are now required and capped (255 / 2000, matching
the incidents table); `category`, `serviceName`, `severity` stay optional but capped at 50. Added
`@Valid` to both `RagController` endpoints — **without it, the DTO rules never run at all.**

**Checked with the real Hibernate Validator**, not just by reading the annotations: a request
missing both fields gave 2 errors, a 5000-character description gave 1 error, and two normal
requests (with and without the optional fields) both passed cleanly.

**Left alone on purpose:** both endpoints are still `@GetMapping` with `@RequestBody`
(known issue 7 — GET-with-a-body). Fixing that changes the HTTP method callers must use, which is
a bigger, separate decision.

**Files:** `Ai-Service/dto/IncidentSuggestionNeededDTO.java`,
`Ai-Service/dto/EngineerRecommendationRequestDTO.java`,
`Ai-Service/controller/RagController.java`.

---

### Step 5 result — ✅ done 2026-09-22

**Goal:** Steps 2 and 4 checked the request **body**. This step checks values in the **URL** —
the path (`/token-version/abc`) and the query string (`?days=7`) — which had almost no checks at
all.

```
request comes in with a bad URL value
   │
   ▼
@Min / @Max / @Size on the parameter checks it
   │
   ▼
fails?  →  Step 1's handler turns it into a clean 400 with a message
passes? →  the method runs normally
```

| # | Where | Rule | Before |
|---|---|---|---|
| 1 | `ReportingController` trend `days` | `@Min(1) @Max(365)` | `days=-5` silently returned nothing; `days=100000` scanned the whole table |
| 2 | `UserController` search `query` | `@Size(max=100)` | No limit — matches the email column, so longer text could never match anyway |
| 3 | `InternalUserController` batch of ids | `@Size(max=50)` list, `@NotNull` on each item | One call could ask for the whole users table |
| 4 | `TokenVersionController` `userid` | `String` → `UUID` type | `/token-version/abc` crashed with a 500; now Spring rejects it with a clean 400 before our code even runs |
| 5 | 3 services (Incident, Reporting, User) | `spring.data.web.pageable.max-page-size=100` | Default is 2000 — `?size=2000` loaded 2000 rows in one request |

**Checked with throwaway tests** calling the real controllers (then deleted — they weren't meant
to stay): normal values passed, bad values got 400, and `?size=5000` came back capped at exactly
100. Empty search and a missing `days` both still work as before — proving the new limits don't
break normal use is as important as proving they block bad use.

**Files:** `ReportingService/controller/ReportingController.java`,
`UserService/controller/UserController.java`, `InternalUserController.java`,
`AuthService/controller/TokenVersionController.java`, and the `application.properties` of
IncidentService, ReportingService, UserService.

---

### Step 6 result — ✅ done 2026-09-22

**Goal:** stop a huge request (10MB+) before it reaches any service at all — refuse it at the
gateway, the one door every request walks through.

```
request arrives at the gateway
   │
   ▼
RequestSizeFilter runs FIRST (before the JWT check)
   │
   ├─ Content-Length over 256KB?     → 413, refused instantly
   ├─ no Content-Length, "chunked"?  → 411, we can't measure it so we refuse it
   ├─ Content-Length not a number?   → 400
   └─ anything else (normal request) → let it through
```

**Why not Spring's own built-in size filter?** We read its code first. It only reads the
`Content-Length` header — a request sent in pieces ("chunked") has none, and would walk straight
past it. Its error also has an empty body, not our normal JSON shape. So we wrote a small filter
of our own.

**256KB, not something smaller or bigger.** The biggest real request (an incident with the full
2000-character description) is about 10KB. 256KB is 25× that — comfortable room, still nowhere
near "somebody is trying to flood us".

**A small tidy-up alongside it.** The JSON-error-writing code that already existed inside
`JwtGatewayFilter` moved into a new shared helper, `GatewayErrorWriter`, so the new filter and the
old one share one copy instead of two slowly drifting apart.

**Checked two ways.** First, 9 permanent unit tests (exactly-at-the-limit passes, one byte over
fails, a chunked request gets 411, a normal GET passes, and so on). Second, started the real
gateway and sent real requests: a 300KB POST got **413** with our JSON error, a 300KB POST to the
**public** `/auth/signup` path also got 413 (the filter runs before the "skip auth for /auth/**"
rule), and a small request correctly passed the size check and moved on to the next check (401,
no token).

**Issue 36 checked while here.** The gateway's old-style route property names
(`spring.cloud.gateway.routes`) still work — Spring Cloud Gateway auto-translates them to the new
names at startup (confirmed in the real startup log). Routing was never broken; only the property
names are due a tidy-up later.

**Files:** `APIGateway/filter/RequestSizeFilter.java` (new),
`APIGateway/utils/GatewayErrorWriter.java` (new),
`APIGateway/filter/JwtGatewayFilter.java` (now uses the shared helper),
`APIGateway/src/test/.../RequestSizeFilterTest.java` (new, 9 tests),
`APIGateway/application.properties` (`gateway.request.max-body-size=256KB`).

---

### B4 is complete — what it adds up to

```
Step 1  teach the error handler   → a bad parameter now gives a clean 400, not a 500
Step 2  body limits (Incident,    → a DTO's rules can't be skipped, and can't disagree
        Auth)                       with the database column they save to
Step 3  proved the crash is real  → not "looks right" — actually run and watched crash
Step 4  Ai-Service checks         → stopped paying Gemini to search for "null null"
Step 5  URL value limits          → the same idea, for path and query values
Step 6  gateway size cap          → a huge request never reaches any service at all
```

### Not done in B4 (still open)

- **The IncidentService V2 migration has not been run yet.** You are running it yourself.
- **Issue 7 (GET-with-a-body on `/ai/rag/**`) is still open.** Its requests are now validated,
  but the HTTP method itself is unchanged.
- **No rate limiting.** A request can still be perfectly valid and sent 10,000 times a second.
  That is B5, not B4.
- **The gateway's 256KB cap is the only place body size is checked.** Individual services do not
  re-check it — they trust the gateway is the only door in (true today, because they bind to
  `127.0.0.1`, Track B1).

---

## B5 — Nothing stops spamming ✅ Done 2026-09-28

### What was wrong

Two different problems, both about "how often", neither about "is the request valid" (that was
B4):

1. **Money.** `/ai/rag/suggestion` calls a **paid** Gemini API on every request. Nothing stopped
   one user calling it 1000 times a minute.
2. **Credential stuffing.** The account lockout counts wrong passwords **per account**. An
   attacker guessing one password against 10,000 different accounts never fails 5 times on any
   single one, so the lock never trips.

### The plan — 4 steps

| Step | What | Status |
|---|---|---|
| 1 | Limit on `/ai/**`, per logged-in user | ✅ Done |
| 2 | Limit on login and signup, per IP address | ✅ Done |
| 3 | A generous default limit on everything else, per user | ✅ Done |
| 4 | Write it all down (PROGRESS.md, CLAUDE.md, LLD) | ✅ Done (this section) |

### How a token bucket works (the idea behind every step)

```
Each caller gets a bucket holding a few coins.
Every request takes 1 coin. No coins left -> refused (429).
Coins refill slowly over time (not all at once) - so a person gets a
small burst, then a steady trickle. A loop runs dry in seconds.
```

Every bucket actually carries **two** limits stacked together — a short window that stops a fast
burst, and a long window that stops a slow, patient drain that stays inside the short one:

| Rule | Short window | Long window |
|---|---|---|
| `/ai/**`, per user | 5 per minute | 100 per day |
| login, per IP | 10 per minute | 100 per hour |
| signup, per IP | 5 per hour | 20 per day |
| everything else, per user | 20 per second | 300 per minute |

Over the limit → **429**, our normal JSON error, plus a standard `Retry-After` header telling the
caller how long to wait.

---

### Step 1 result — ✅ done 2026-09-23

Rate limit on `/ai/**`, counted per logged-in user (the verified `X-User-Id` the gateway itself
writes — not something a caller can fake).

New reusable class, `KeyedRateLimiter`, keeps one Bucket4j "bucket" per caller, held in a
Caffeine cache (same library as the B3 token cache) so memory stays bounded. A new gateway
filter, `RateLimitFilter`, runs right after the JWT check (it needs the user id that check
writes) and refuses over-the-limit requests with 429 before they ever reach Ai-Service.

**Checked with 9 permanent tests** (a fake clock, so "wait 12 seconds" runs instantly) and then
with the real gateway: 5 requests passed, the 6th and 7th got 429 with `Retry-After: 10`, a
second user was unaffected, and after really waiting 13 seconds exactly one more request fit.

### Step 2 result — ✅ done 2026-09-23

Same idea, for `/auth/login` and `/auth/signup` — but counted by **IP address**, because there is
no token yet at login to get a user id from.

**Why IP catches something account lockout can't:** lockout counts failures per account; an IP
limit counts requests per caller regardless of which account each one targets. Different attack,
different net.

**Three tricks were checked and blocked, each with a test:**
- Faking the `X-Forwarded-For` header does nothing — we use the real connection address, which a
  caller cannot lie about, not a header they can type anything into.
- A trailing slash (`/auth/login/`) is treated as the same path as `/auth/login` — not a second,
  fresh bucket.
- A made-up `X-User-Id` on a login request is ignored — login/signup are always keyed by IP only.

**Worth remembering:** one IP is not always one person. Office networks share one public address
(NAT), so the limits are set generously enough that normal shared-office traffic should never
notice them — they exist to catch a *script*, not a handful of humans. Also written down for
later: if a load balancer is ever added in front of the gateway, every caller would appear to
share its one address, and `X-Forwarded-For` would then need to be trusted — but **only** from
that one known, fixed source, never from the public internet in general.

**Checked with 10 more permanent tests plus the real gateway:** 6 signups from one IP (6th
blocked), 11 logins **each for a different account** from one IP (11th blocked — the actual
credential-stuffing attack, stopped), a faked forwarded-for header still blocked, a trailing-slash
login still blocked, and per-rule counts correct on `/actuator/metrics/gateway.ratelimit.rejected`.

### Step 3 result — ✅ done 2026-09-24

**Goal:** steps 1 and 2 protected the money endpoint and the password endpoints. Every *other*
endpoint (incidents, users, reports) still had no limit — a runaway script could hammer the
database and there was nothing to stop it.

**What it is:** one more rule, `default`, that covers **every** path and is counted per user, with
a limit generous enough that a real person should never notice it: 20 requests a second, 300 a
minute. It exists only to catch a script, not to police normal use.

**Two design choices worth remembering:**
- **It must be the LAST rule checked.** The filter uses the *first* rule that matches a path. If
  `default` were checked first, it would swallow every request before the tighter `ai`/`login`/
  `signup` rules ever got a turn.
- **One default bucket covers every service.** Using 10 requests on `/incidents` and then 10 on
  `/users` shares the *same* 20-a-second allowance — hopping between services buys nothing extra.

**Checked with 5 more permanent tests, then the real gateway with genuinely parallel requests**
(30 requests fired at the exact same instant): about 21 passed and 9 got 429 — close to the 20
limit, with a small, expected spread from real network timing. A second user, a request to
`/ai/...`, and `/actuator/health` were all unaffected — proving the default bucket doesn't leak
into the other rules, and `/actuator` isn't rate-limited at all (gateway filters only run on
*routed* requests).

**Files:** `APIGateway/filter/RateLimitFilter.java` (now holds 4 rules, not 3),
`APIGateway/src/test/.../RateLimitFilterTest.java` (24 tests total),
`APIGateway/application.properties` (2 more settings).

---

### A real bug found while testing step 3 — ✅ fixed 2026-09-25

**Not caused by rate limiting.** It was already there; step 3's parallel-request test just
happened to be the first time a service was down *while* a valid token was used, which is exactly
the situation that exposed it.

**What was wrong, in one picture:**

```
JwtGatewayFilter does 2 jobs, one after another:
  A) ask AuthService "is this token still valid?"
  B) forward the request to the real service (Incident, User, ...)

The "if anything fails" catch was wrapped around BOTH jobs, not just A.
So when B failed (the service was down), it was reported as if A had
failed: 503 "AUTH_SERVICE_UNAVAILABLE - Unable to verify the session".
```

**Real proof:** with IncidentService not running, a perfectly valid token got back
*"Unable to verify the session, please try again"* — telling the user their **login** was the
problem, when the real issue was that **IncidentService** was down. The log agreed with the wrong
story: `Auth service call failed`, 35 times in one test run.

**Why this matters:** the status code and message decide what a frontend *does*. 401-shaped
answers mean "send the user to log in again". A perfectly good login being told to re-authenticate
because an unrelated service crashed is exactly the kind of bug nobody would think to blame on the
gateway.

**The fix, in one sentence:** catch job A's errors only around job A, and give job B's errors —
routing failures, a service being down — an honest home of their own.

```
Job A's errors  → still exactly the same as before: deleted user → 401,
                   unexpected lookup failure → 503 (unchanged behaviour)
Job B's errors  → NEW: GatewayExceptionHandler, the gateway's own
                   last-resort handler, gives an HONEST status:
                     service not registered / down → 503
                     service too slow               → 504
                     no route matches this path      → 404
                     our own unexpected bug           → 500 (message hidden)
```

**How it was proven, test-first.** A test was written that captures the bug and confirmed it
**failed** against the old code, then the fix was made and the same test **passed** — not "looks
right", an actual before/after. 13 new tests total (6 for the filter's own error handling, 7 for
the new handler), all 46 gateway tests green, then the exact real-world situation was reproduced
on the running gateway: the log now correctly names `IncidentService`, not AuthService, and the
count of wrongly-blamed lines dropped from 35 to **0**.

**Files:** `APIGateway/filter/JwtGatewayFilter.java` (catches only the token-version lookup's own
errors now, using `.materialize()`), `APIGateway/handler/GatewayExceptionHandler.java` (new — the
gateway's equivalent of `common-web`'s `GlobalExceptionHandler`, since WebFlux cannot use that
module), `APIGateway/src/test/.../JwtGatewayFilterTest.java` (new, 6 tests),
`APIGateway/src/test/.../GatewayExceptionHandlerTest.java` (new, 7 tests).

Full explanation, with a 3-step picture of what each part of the filter is actually for:
`notes/jwt-gateway-error-handling.md`.

---

## B5 is complete — what it adds up to

```
Step 1  /ai/** limited, per user        → the paid API can't be looped into a large bill
Step 2  login + signup limited, per IP  → credential stuffing is caught, not just single-account guessing
Step 3  everything else limited         → a runaway script can't load the database for everyone
Bug fix service failures told honestly  → a down service is no longer blamed on AuthService
```

### Not done in B5 (still open)

- **Counts live in memory only.** A gateway restart resets every caller back to a full bucket.
  With two gateways running, each counts separately. Same trade-off already accepted for the B3
  token cache — Redis is only worth it once there is more than one gateway instance.
- **An attacker spread across many IP addresses (a botnet) is not stopped.** Each IP just gets its
  own fresh bucket. That needs protection in front of the whole system (a firewall service), out
  of scope here.
- **`X-Forwarded-For` is ignored everywhere, on purpose.** If a load balancer is ever put in front
  of the gateway, every caller would appear to share the load balancer's one address. The fix at
  that point is to trust the header **only** from that one known, fixed source — never from the
  public internet in general.
- **504 GATEWAY_TIMEOUT is wired up but never actually fires today.** Only the gateway's own call
  to AuthService has a response timeout configured. Nothing limits how long a *routed* call to
  Incident, User or Reporting is allowed to take, so a slow (not down) service just makes the
  caller wait, rather than getting a clean 504. Adding route-level timeouts is a possible future
  item.
- **The default limit's numbers (20/sec, 300/min) are a starting point, not measured.** Watch
  `/actuator/metrics/gateway.ratelimit.rejected?tag=rule:default` once this runs for real — if it
  climbs during ordinary use, the numbers are too tight.

### Files changed in B5, all together

**APIGateway (9 files):** `services/KeyedRateLimiter.java` (new), `filter/RateLimitFilter.java`
(new), `handler/GatewayExceptionHandler.java` (new), `filter/JwtGatewayFilter.java` (error
handling narrowed to the token-version lookup only), `application.properties` (8 rate-limit
settings), `pom.xml` (+Bucket4j), `src/test/.../RateLimitFilterTest.java` (new, 24 tests),
`src/test/.../JwtGatewayFilterTest.java` (new, 6 tests),
`src/test/.../GatewayExceptionHandlerTest.java` (new, 7 tests).

**Notes (2 new):** `rate-limiting-on-ai-endpoints.md`, `jwt-gateway-error-handling.md`.

---

# Track C — Logging, tracing, and more

## C1 — Logs were messy and one request could not be followed ✅ Done 2026-09-30

*(Built over three days: steps 1 to 3 between 09-28 and 09-29, flow charts and wrap-up on 09-30.)*

### What was wrong

1. About 38 `System.out.println` / `printStackTrace` calls. Some printed emails, roles, whole `User`
   objects and token versions. Nobody could turn them off or filter them.
2. Only the MVC services had a trace id, and log lines only showed it if someone typed it by hand.
3. The gateway had no trace id of its own for its own errors. A request that crossed the gateway
   and a Kafka topic could not be followed from start to end.
4. Kafka messages carried no trace id at all.

### What we did — 5 steps

```
Step 1   println → log.debug            → off by default, only user ids inside
Step 1b  3 more places cleaned          → emails gone from Notification logs, noisy INFO → debug
Step 2   log pattern gets an [id] slot  → every log line shows the trace id by itself
Step 2b  TraceIdGatewayFilter (gateway) → the id is made ONCE, first, and every gateway filter reuses it
Step 3   new module common-trace        → the same id travels through Kafka messages too
```

**Step 1 + 1b.** 38 calls in 18 files became `log.debug`. Rule used: log **user ids only** — never
emails, roles, passwords, tokens or whole objects. Extra: `UpdateRoleService` had a `Logger` that did
nothing (a Java-util-logging call with no handler) — it now writes a real line.

**Step 2.** Spring Boot has a slot in its log format for a "correlation id"
(`logging.pattern.correlation`). `common-trace` fills it with `[%X{traceId:--}] ` using an
`EnvironmentPostProcessor` registered in `META-INF/spring.factories`. It must run **before** logging
starts, which is why it is a `spring.factories` entry and not a normal bean. After this, the 13 hand-written
`"[{}] " + traceId(request)` bits in `GlobalExceptionHandler` were removed — the slot does it.

**Step 2b.** `filter/TraceIdGatewayFilter.java` (order **-3**, runs before every other gateway filter):
takes a safe `X-Trace-Id` from the caller or makes one, saves it as an exchange **attribute**
(`gateway.traceId`), puts it on the response, and forwards it to the service. Other filters call
`TraceIdGatewayFilter.currentTraceId(exchange)`. Why an attribute and not just the header: a filter that
uses `exchange.mutate()` makes a *copy* of the request. Headers on the copy are not seen by the original,
but attributes are shared by both.

**Step 3.** New plain-JAR module **`common-trace`** (`com.project.trace:common-trace:1.0.0`, Java 17
bytecode). It holds `TraceId` (the header name, the MDC key, the safety rule, the id maker) and the
`TraceIdLoggingDefaults` from step 2. Both `common-web` and `common-kafka` depend on it, so they agree.

`common-kafka` gained:
- `TraceIdProducerInterceptor` — when a service **sends** a message, copies the id from the MDC onto the
  message header.
- `TraceIdRecordInterceptor` — when a service **receives** a message, reads the header, puts it back in
  the MDC, and clears it after the message is done.
- `KafkaTraceIdAutoConfig` — wires both into Spring Boot's producers and listener containers by itself.

AuthService and IncidentService got `common-kafka` added to their `pom.xml` (they were the two
producers that did not have it yet).

### A real bug found while testing step 3 — fixed

`KafkaErrorHandlingAutoConfig` (from B2) defines two `KafkaTemplate` beans for the dead-letter topics.
Spring Boot's default `kafkaTemplate` is `@ConditionalOnMissingBean(KafkaTemplate.class)` — so those
two beans **hid** it. In a service that only *sends* messages (ReportingService, Ai-Service) no
`KafkaTemplate<String, SomeEvent>` existed. Fix: `@AutoConfiguration(after = KafkaAutoConfiguration.class)`
so Boot's default template is created first. Proven with a test on the real auto-config
(`bootsDefaultTemplateIsNotSuppressedByTheDeadLetterTemplates`).

**Not yet seen in the real running system.** It was reproduced in an isolated test. Whether
ReportingService and Ai-Service really failed to start between B2 and now has not been checked.

### How we know it works

- **108 tests pass** in the four modules Track C touched: common-trace 8, common-web 19,
  common-kafka 28, APIGateway 53.
- `TraceIdOverKafkaTest` uses a **real embedded Kafka broker**: a trace id is sent, received, forwarded
  to a second topic, and parked in a dead-letter topic — and it is the same id every time.
- Full 13-module build passes. All 6 service jars contain `common-trace` and `common-kafka`
  (NotificationService has no `common-web`).

### Not done in C1 (still open)

- **Step 4: Micrometer Tracing / Zipkin / Prometheus.** Deliberately left. It is the "real" tracing
  system with spans and a UI. Our trace id is a simple string in logs; it does not measure time.
- **Nothing seen with real services yet.** The user should start Kafka and all services, send one request,
  and check that the same `[id]` appears in the gateway, IncidentService, ReportingService and
  NotificationService logs.
- **Services' `TraceIdFilter` still reuses any incoming `X-Trace-Id` without checking it** (the gateway
  does check). Safe today because services only accept calls from the gateway, but not defence in depth.
- **The trace id rules exist twice (CLAUDE.md issue 54).** The gateway has no `common-trace` dependency, so `TraceIdGatewayFilter` keeps its own copy of the header name, safety pattern and id maker. Found on 2026-10-01 while re-reading the LLD notes against the code.
- **Gateway log lines still write the `[id]` by hand.** The gateway is reactive; the MDC does not follow
  a request across threads there without extra work.
- **Reload Maven projects in IntelliJ** — there is a new module.

### Files changed in C1, all together

**New module `common-trace` (6 files):** `pom.xml`, `TraceId.java`, `TraceIdLoggingDefaults.java`,
`META-INF/spring.factories`, `TraceIdTest`, `TraceIdLoggingDefaultsTest`.

**common-kafka (9):** `pom.xml`, `TraceIdProducerInterceptor`, `TraceIdRecordInterceptor`,
`KafkaTraceIdAutoConfig`, `KafkaErrorHandlingAutoConfig` (bug fix), `AutoConfiguration.imports`, and 4 test classes.

**common-web (4):** `pom.xml`, `TraceIdFilter`, `GlobalExceptionHandler`, `TraceIdLoggingDefaultsTest`.

**APIGateway (7):** `TraceIdGatewayFilter` (new), `TraceIdGatewayFilterTest` (new),
`JwtGatewayFilter`, `RateLimitFilter`, `RequestSizeFilter`, `GatewayExceptionHandler`, `GatewayErrorWriter`.

**println clean-up (about 22 files across AuthService, UserService, NotificationService, Ai-Service).**
**Root `pom.xml`** — now 13 modules. **AuthService / IncidentService `pom.xml`** — +`common-kafka`.

**Notes:** `trace-id-and-logging.md` (new, with 5 "who calls whom" flow charts), plus updates to
`kafka-dead-letter-topics.md`, the B4/B5 notes, `system-design-HLD.md` and the LLD files.

---

## LLD re-check (2026-10-01) — reading the notes against the code

**Why:** the LLD notes had been updated by searching for keywords, not by reading each file against the
code. The user asked for a full read. Every service file was read end to end, every claim compared with the
real source, and three claims that could not be decided by reading were **run** in a small throwaway project
(`notes/experiments/transaction-rollback-and-osiv/`).

### Old claims that turned out to be WRONG

| Old claim | The truth | How we know |
|---|---|---|
| Password change **never saves** the new password (issue 1) | It **is saved today — by luck.** It depends on Spring's open-session-in-view, which is on by default. If someone turns that off, it breaks silently | experiment |
| Account lockout works after 5 wrong passwords, but cannot be undone (issue 6) | The lockout **never triggers**: `LoginUserService` saves the counter then throws, and the throw rolls the save back. Per-account brute-force protection is off since Track A1 (new issue 55) | experiment |
| ReportingService has "the best idempotency in the project" | Its ledger **does not catch a repeated event** — the id is assigned by the code, so `save()` merges instead of failing (new issue 60) | experiment |
| Changing your email does not cancel old tokens | It does: `UpdateCredentialsService` calls `LogoutUser()`, which bumps the token version. The commented-out line is a harmless leftover | read |
| `daily_metrics` is upserted by the consumer | **Nothing writes it** (new issue 61) | read + search |
| The recipient of an assigned incident is "the assignee" | Three people: creator, assigner, assignee | read |
| Ai-Service "N+1: ten candidates, 100 seconds" | The search keeps 4 incidents, so at most 4 candidates and 8 calls (about 40 s) | read |
| `trusted.packages=*`, "no real tests", "6 of 10 modules" in CLAUDE.md | Already fixed in B2 / A4 / Track B+C; the text had not been updated | read |

### New issues found (all in CLAUDE.md section 9; none fixed)

54 the trace-id rules exist twice (gateway has no `common-trace`) · 55 lockout never triggers · 56 assign has
no ownership check · 57 supervisor visibility rules disagree, and an empty team hides your own incidents ·
58 an incident read returns only 8 fields · 59 `CRITICAL` priority gets a 0-second SLA · 60 idempotency ledger
does not work · 61 `daily_metrics` never written · 62 active-incident counts drift / `resolvedBy` is whoever
pressed RESOLVED · 63 UserService returns `enabled` / `accountLocked` / `failedLoginAttempts` that are never
updated · 64 no `@Version` anywhere.

### What changed for the plan

The first thing to fix is no longer the password change. In order of value for the effort: **(1)** lockout
(issue 55) and **(2)** the idempotency check (issue 60) — both small, both security or data-integrity, both
found by experiments that become the first real tests; **(3)** the assign ownership check (56); **(4)** the
`CRITICAL` SLA (59) with the other SLA values; **(5)** `@Version` (64). The password change should be made
*explicit* (`@Transactional` + `save`) so it stops depending on a hidden setting. The proposed phases are in
`notes/LLD/LLD-improvements.md` section F (still a proposal).

### Files changed in this pass

`CLAUDE.md` (issues 1, 6, 28 corrected; 54–64 added; section 5 idempotency paragraph; one working agreement
reworded) · `PROGRESS.md` (this section) · all 7 `notes/LLD/LLD-<service>.md` files · `LLD-overview.md`,
`LLD-QA.md` (Q3, Q7, Q8), `LLD-improvements.md` (A4 rewritten, C8, C9, E6, section F),
`system-design-HLD.md`, `rate-limiting-on-ai-endpoints.md`, `kafka-dead-letter-topics.md`, `FLYWAY-NOTES.md`,
`ERROR_HANDLING_FLOW.md`, `README.md` · **new:** `notes/experiments/transaction-rollback-and-osiv/`.

---

# Outbox — events can no longer be lost (Phase 2, started 2026-10-12)

## What is wrong today

A service saves data, then sends a Kafka event as a second, separate step. The two can split:

| Service | Today | What can go wrong |
|---|---|---|
| IncidentService | `save()` (committed at once, no `@Transactional`), then `send()` | crash or Kafka down between them → the event is **lost**; Reporting, Notification and Ai never hear |
| AuthService | `send()` **inside** a `@Transactional` method, before the commit | send ok + commit fails → a **ghost** event; commit ok + send fails → a **lost** `user.registered`, and that user can never create an incident |
| ReportingService | resolved-report `send()` inside the listener transaction; SLA scheduler sends, then marks | ghosts and duplicates (issue 22) |

`kafkaTemplate.send()` is asynchronous, and nobody checks its result, so a failed send is silent.

## The agreed design (chosen 2026-10-12)

```
WRITE (inside the business transaction)        RELAY (separate thread, every ~1 s)
  BEGIN                                           1. claim up to 50 NEW rows, oldest first   (short transaction)
    save business data                            2. send each to Kafka, WAIT for the ack     (NO transaction open)
    insert row in outbox_event (status NEW)       3. ok -> SENT;  Kafka down -> back to NEW, stop the batch;
  COMMIT  -> both saved, or neither                  broken message -> FAILED, carry on
```

- **Polling relay**, not Debezium (Debezium needs Kafka Connect and MySQL binlog — too much for one laptop).
- **One table per service**, in that service's own MySQL schema, created by a Flyway file.
- **New shared module `common-outbox`** (like `common-kafka`), plain `JdbcTemplate` — no JPA entity, so no
  entity-scan setup in every service, and the insert joins the business transaction automatically.
- **The relay sends exactly what the service sends today** (it rebuilds the event and uses the service's
  own Kafka settings), so **consumers do not change**.
- **Two headers on every message:** the saved trace id (`X-Trace-Id` — the relay thread has no notepad) and
  the outbox row id (`X-Event-Id` — 7 of our 13 event classes have no `eventId`).
- **No database transaction is held open while talking to Kafka or to another service.**
- **Inbox (duplicates):** the outbox delivers *at least once*. Notification, Ai-Service and UserService are
  already safe; ReportingService's ledger is broken (issue 60) and is fixed in step 4.

## Steps

| Step | What | Status |
|---|---|---|
| 1 | `common-outbox` module + tests (H2 + embedded Kafka) | ✅ done 2026-10-12 — 24 tests pass |
| 2 | IncidentService uses it (3 events) + Flyway `V3` | ✅ done 2026-10-13 — 9 tests pass (3 added during step 3) |
| 3 | AuthService uses it (5 events) + Flyway `V3` | ✅ done 2026-10-13 — 16 tests pass |
| 4 | ReportingService (resolved report + SLA events) + Flyway `V2` + **fix the inbox (issue 60)** | ✅ done 2026-10-13 — 15 tests pass |
| 5 | Prove it on the real system (stop Kafka, create an incident, start Kafka, see it arrive) | ✅ done 2026-10-14 — tests A, B and C passed by hand (you ran them); D not run, see below |
| 6 | Docs: CLAUDE.md, LLD notes, a notes page | ✅ done 2026-10-14 |

**No new Docker image and no new third-party library** — everything needed is already in the services.

### Step 1 result — ✅ done 2026-10-12

A new shared module, **`common-outbox`** (`com.project.outbox:common-outbox:1.0.0`, Java 17 bytecode), listed in
the root `pom.xml` (14 modules now). **No service uses it yet** — that is steps 2 to 4.

| File | What it does |
|---|---|
| `OutboxWriter` | What a service will call instead of `kafkaTemplate.send(...)`. Saves the event as one row with `JdbcTemplate`, in the transaction that is already open. **Refuses to run outside a transaction.** Saves the request's trace id too |
| `OutboxRelay` | One round: **claim** up to 50 `NEW` rows (short transaction) → **send** each and wait for Kafka's ack (**no transaction open**) → `SENT`. Kafka down → back to `NEW` and stop the round (keeps the order). Broken message → `FAILED`, carry on. Also: puts back rows left half-sent by a crash, deletes `SENT` rows after 7 days, warns if events pile up |
| `OutboxScheduler` | Runs a round every second on its **own thread** (`outbox-relay`), so it never delays a service's other scheduled jobs. A round can never throw (one throw would stop it for good) |
| `OutboxAutoConfig` | Switches all of this on by itself in a service that has a database, transactions and Kafka. Runs after Spring Boot's own Kafka/JDBC setup. Does **not** add an `ObjectMapper` bean (that would replace the service's own) |
| `OutboxProperties` | Settings under `outbox.*`; every one has a default |
| `OutboxStatus`, `OutboxHeaders` | The four states (`NEW`, `SENDING`, `SENT`, `FAILED`) and the `X-Event-Id` header name |
| `outbox/outbox_event_mysql.sql` | The table. Each service copies it into its own Flyway file; the tests run this exact file |

**Tests (24):** writer 6, relay 10 (real embedded Kafka), auto-config 5, scheduler 1, and **2 that use real JPA +
`jakarta.transaction.Transactional`**, like our services: commit → both rows saved; exception → **neither** saved.
One relay test proves the message is **byte-for-byte what a direct send produces today** (same JSON, same
`__TypeId__` header), so consumers will not need to change.

**Checked that the tests can fail:** the "stop the round when Kafka is down" rule was broken on purpose; the test
caught it (`expected 0 attempts on the 2nd event, was 1`), then the code was restored and rebuilt.

**Dependencies:** nothing new for the services. The module's own test-only extras: `spring-kafka-test`,
`spring-boot-starter-data-jpa`, `spring-boot-starter-json`, and `h2` (pinned to 2.2.224, already in the local
Maven cache).

### Step 2 result — ✅ done 2026-10-13

IncidentService's three events (`incident.created`, `incident.assigned`, `incident.status.updated`) now go
through the outbox. **No direct `kafkaTemplate.send` is left in IncidentService.**

```
BEFORE:  check user (Feign) -> save incident (committed at once) -> kafkaTemplate.send   <- lost if crash/Kafka down
AFTER:   check user (Feign) -> [ BEGIN  save incident + save outbox row  COMMIT ] -> relay sends ~1 s later
```

| File | What changed |
|---|---|
| `pom.xml` | + `common-outbox`; + `h2` (tests only) |
| `db/migration/V3__outbox_event.sql` | **new** — the outbox table, an exact copy of the module's SQL (checked with `diff`) |
| `kafka/producer/IncidentEventProducer.java` | same 3 methods, same topics and keys, but they call `outboxWriter.save(...)` instead of `kafkaTemplate.send(...)` |
| `service/IncidentService.java` | `CreateIncident`, `AssignIncident`, `updateIncidentStatusBasedOnRole`: the save + the event are wrapped in **one `transactionTemplate.execute(...)`**. Every UserService call stays **before** it, outside any transaction |
| `src/test/.../service/IncidentServiceOutboxTest.java` | **new**, 5 tests on the real service code (H2 + fake UserService, no Kafka) |
| `src/test/.../OutboxWiringTest.java` | **new**, 1 test: the whole service starts with the outbox on (relay running) |

**Tests (6):** create → incident + one `NEW` row (topic, key = incident id, type, payload); **the event insert
fails → the incident is not saved either**; assign → `incident.assigned` row; resolve → `incident.status.updated`
row carrying the root cause; an inactive user → nothing saved. Every test also checks that **UserService was
called with no transaction open**.

**Checked that the tests can fail:** the incident save was moved outside the transaction on purpose (the old
behaviour). The test caught it: *"both or neither: no incident without its event — expected 0 but was 1"*. Code
restored and re-tested.

**Not changed on purpose (separate known issues):** the root cause wiped on every status change (issue 3), the
double UserService call in assign (issue 38), the missing ownership check on assign (issue 56).

**You need to do:** nothing by hand for the table — Flyway creates it at the next start (your still-pending `V2`
runs first, then `V3`). Reload Maven in IntelliJ (new dependency).

### Step 2, one more check added during step 3

`IncidentEventsOverKafkaTest` (3 tests, new): each real IncidentService event goes into the outbox, through the
real relay, into a real embedded Kafka, and is read back the way Reporting / Notification / Ai read it. All 3
arrive as the same object (including `IncidentStatusUpdatedEvent`, which has capital-letter fields). Step 2's
own tests only looked at the saved rows; this one proves the relay can **rebuild** the real classes.

### Step 3 result — ✅ done 2026-10-13

AuthService's five events (`user.registered`, `user.Updated`, `user.logged-in`, `user.logged-out`,
`roles.update`) now go through the outbox. **No direct `kafkaTemplate.send` is left in AuthService.**

```
BEFORE:  save user -> kafkaTemplate.send  (inside @Transactional, BEFORE the commit)   <- ghost event OR lost event
AFTER:   [ BEGIN  save user change + save outbox row  COMMIT ] -> relay sends ~1 s later
```

| File | What changed |
|---|---|
| `pom.xml` | + `common-outbox`; test only: + `h2`, + `spring-kafka-test` |
| `db/migration/V3__outbox_event.sql` | **new** — an exact copy of the module's table SQL (checked with `diff`) |
| `UserRegisteredEventProducer`, `UserUpdatedEventProducer` | `outboxWriter.save(...)` instead of `kafkaTemplate.send(...)` |
| `UserLoggedInEventProducer`, `UserLoggedOutEventProducer` | the same, **and the class-level `@Transactional` is removed** (see below) |
| `RoleUpdatedEventProducer` | the same, plus a new `userId` parameter, used as the Kafka key |
| `LogoutUserService` | both methods run in a `transactionTemplate` (token version + event row together). It **joins** the caller's transaction when there is one |
| `UpdateRoleService` | the roles change, the forced logout and both events are now **one** transaction. The user is loaded **inside** it |

**Kafka keys:** `user.registered` and `roles.update` used to be sent with **no key**. Both are keyed by the user
id now, so all of one user's events stay in order (this closes the ordering gap noted in the LLD).

**Why the two `@Transactional` annotations on the producers had to go:** with the annotation on the producer
class, a caller **without** a transaction would get a **separate** one started just around the outbox insert.
`OutboxWriter` sees "a transaction is open" and does not complain, but the user change and its event are no
longer saved together. Without the annotation, `OutboxWriter` refuses loudly instead.

**Found while doing this (reported, and fixed because it sits inside the new transaction):**
`UpdateRoleService` loaded the user **before** its work, then saved that same object **after** the forced logout
had raised the token version — which writes the **old** token version back. It only worked because Spring's
open-session-in-view hands back the same object (the same hidden dependency as known issue 1). Now the user is
loaded inside the transaction, so it is correct with or without that setting. Proved: undoing the fix makes a
test fail with "tokens are revoked, and the bump was not overwritten — expected 1 but was 0".

**Tests (16):**

| Class | Count | What it proves |
|---|---|---|
| `AuthServiceOutboxTest` | 10 | each of the 5 events: the change + one row saved together, topic and key right; **if the event row cannot be saved, the change is not saved either** (signup, logout, roles); wrong password and a taken username save no event; the roles change is all-or-nothing, including the forced logout |
| `AuthEventsOverKafkaTest` | 5 | each real event, through the real relay and a real embedded Kafka, is read back (like UserService) as the **same object** |
| `OutboxWiringTest` | 1 | the whole AuthService starts with the outbox on and the relay running |

**Checked that the tests can fail:** (1) the logged-out event was moved outside the transaction → "both or
neither: the token version did not move, expected 0 but was 1". (2) The load-inside fix was undone → the roles
test failed as above. Both files restored.

**Not changed on purpose (separate known issues):** the account lockout that never triggers (issue 55 — note the
login still *rolls back* on a wrong password, which is why no event is saved then), the password change that works
only by luck (issue 1, `ChangePasswordService` is untouched), `@PreAuthorize` inert (issue 10).

**You need to do:** nothing by hand for the table — Flyway creates it at the next start. Reload Maven in IntelliJ.

### Step 4 result — ✅ done 2026-10-13

ReportingService's five events (`Incident-Resolved-Report-Topic` and the 4 SLA topics) now go through the
outbox. **No `KafkaTemplate` is left in ReportingService's code.** And the inbox (known issue 60) is fixed.

```
LISTENER  BEFORE: [ BEGIN save report ... kafkaTemplate.send ... COMMIT ]  <- sent even if the commit then failed
          AFTER:  [ BEGIN save report + save outbox row COMMIT ] -> relay sends ~1 s later

SCHEDULER BEFORE: send event -> mark "published" (no transaction)          <- known issue 22
          AFTER:  per page of 100: [ BEGIN save outbox rows + mark "published" COMMIT ]
```

| File | What changed |
|---|---|
| `pom.xml` | + `common-outbox`; test only: + `h2`, + `spring-kafka-test` |
| `db/migration/V2__outbox_event.sql` | **new** — an exact copy of the module's table SQL (checked with `diff`). V2 because this service only had V1 |
| `IncidentResolvedReportEventProducer`, `SLA_BreachedEventProducer`, `SLA_BreachingRiskEventProducer` | `outboxWriter.save(...)` instead of `kafkaTemplate.send(...)`. Same methods, topics and keys |
| `IncidentEventConsumer` | no code change, one comment. It already had `@Transactional`, so the event row now joins that transaction |
| `SLA_BreachedSchedular` | each page of 100 is **one** `transactionTemplate` transaction: event rows + "published" flags, together |
| `SLA_BreachingRiskSchedular` | each page of 50 is one transaction: new risk level + its event row, together |
| `IdempotencyService` | **fix for issue 60:** `existsById` first. A repeat now really is skipped |

**Issue 60, test first.** The new tests were run on the OLD `IdempotencyService` first. 3 failed, showing the bug
on the real code: the same `assigned` event twice → workload +2; the same `RESOLVED` event twice → Ai-Service told
twice; a late repeat of `created` → the report reset to OPEN. After the fix, all 3 pass.

**Issue 22 is closed:** the flag and the event can no longer disagree. If the event row cannot be saved, the flag
stays "not published" and the next run tries again (tested).

**Tests (15):**

| Class | Count | What it proves |
|---|---|---|
| `service/ReportingOutboxTest` | 9 | the real listener and both real schedulers: RESOLVED → report + 1 row; the row cannot be saved → nothing saved and the event is **not** marked handled, so Kafka's retry works; issue 60 (3 tests); breach flag + event together, no repeat on the next run; risk level + event together, no repeat |
| `ReportingEventsOverKafkaTest` | 4 | each of the 3 event classes, through the real relay and a real embedded Kafka, arrives as the **same object**; plus the finding below |
| `OutboxWiringTest` | 2 | the whole service starts with the outbox on; the 3 real SLA timers run and take their ShedLock lock |

**Checked that the tests can fail:** (1) issue 60 fix undone → 3 tests fail; (2) breach page run without a
transaction → 2 tests fail; (3) risk page without a transaction → 1 test fails. All files restored and re-tested.

**Found while doing this (reported, NOT fixed — new known issue 65):** every consumer's `application.properties`
says the trusted-packages check is "starts with", so `com.project.events` "also covers"
`com.project.events.enumsUsedByEvents`. **It does not** — Spring Kafka matches the package exactly. A reader with
today's setting rejects `SlaBreachingRiskEvent`. No harm today (nobody reads the SLA topics, issue 18), but the first
SLA-risk consumer will need its package added. `todaysTrustedSettingRejectsTheRiskEvent` proves it.

**Test-only file:** `src/test/resources/shedlock_for_tests.sql` (the `shedlock` table, for the wiring test). It is
not in the jar (checked).

**Not changed on purpose (separate known issues):** workload counting the wrong engineer (62), `daily_metrics`
never written (61), CRITICAL with no SLA (59), test SLA values (19), the hardcoded ShedLock name (34).

**You need to do:** nothing by hand for the table — Flyway creates it at the next start. Reload Maven in IntelliJ.

### Step 5 result — ✅ done 2026-10-14 (you ran it by hand on the real system)

The tests so far used a fake database and a small Kafka made inside the test. Step 5 used the **real** setup:
real MySQL, the real Kafka in Docker, the real services and the gateway. I gave a plan; **you** ran it and
told me the result. I did not see the tables myself (I have no database login), so these results are your report.

| Test | What you did | Result |
|---|---|---|
| **A. Normal case** | login, create an incident through the gateway, look at `outbox_event` and the report | ✅ worked: row `NEW` then `SENT`, and the report appeared in ReportingService |
| **B. Kafka down** | `docker stop kafka`, create an incident, look at the row, `docker start kafka` | ✅ worked: the request still succeeded, the row waited, and after Kafka came back it was sent and arrived |
| **C. Service restart while Kafka is down** | same, and restart IncidentService before starting Kafka | ✅ worked: the waiting row survived the restart and was sent |
| **D. SLA timers** (optional) | create a HIGH incident, leave it unassigned, wait ~2 minutes | ⬜ **not run by hand.** Covered by the automated tests only (`ReportingOutboxTest`, `OutboxWiringTest`) |

**What this proves that the tests could not:** the real Flyway migrations create the table; the real relay thread
starts inside the real services; a real Kafka outage loses nothing; and a service restart loses nothing. This is
the main promise of the outbox, and it holds.

**What it does not prove yet:**
- **Test D** (the SLA timers on the real system), and the **inbox on the real system** (issue 60 was proved by
  tests only; a real repeat is hard to cause by hand).
- **Ai-Service starting up.** Issue 50 (the `KafkaTemplate` that Boot's default was hidden by) is now moot for
  ReportingService (its producers no longer inject one, and it ran in this test). Ai-Service still injects one
  in `AiResponseProducer`; if you saw it start fine, issue 50 can be closed.

### Step 6 result — ✅ done 2026-10-14

| File | What changed |
|---|---|
| `notes/outbox-pattern.md` | header and contents updated; sections 5 and 19 corrected; **new** sections 24 (the real-system proof), 25 (what to do when something looks wrong: a checklist with the SQL), 26 (how to connect a NEW service, in order), 27 (what the outbox does NOT do) |
| `CLAUDE.md` | outbox paragraph says "proved on the real system"; issue 50 note; a new working agreement with the checklist for connecting a service; module row |
| `notes/LLD/LLD-overview.md`, `LLD-improvements.md` | the "outbox is missing" text now says it is done (history kept) |
| `PROGRESS.md` | this section |

**Left for a later time (not part of the outbox):** the SLA topics still have no reader (issue 18, phase 3);
`ai.classification.result` still uses a direct send, but it is switched off with the classifier (issue 17); the
trusted-packages finding (issue 65).

**You need to do:** commit steps 4–6 (message below), and run test D if you want that last proof.

---

# Lockout fix — 5 wrong passwords really lock the account (Phase 1, 2026-10-14)

**The problem (known issue 55):** `LoginUserService` is `@Transactional`. On a wrong password it added 1 to
`failedLoginAttempts`, saved it, and then **threw** the 401. A thrown error rolls the transaction back, so the
save was undone every time. The counter stayed 0 and the account never locked.

**The second problem (known issue 6):** if an account ever did get locked, nothing unlocked it. Fixing only
issue 55 would have let anyone lock any user out forever by typing 5 wrong passwords for their email.

```
BEFORE: [ BEGIN  count +1, save  ->  throw 401  ->  ROLLBACK ]           -> counter still 0
AFTER:  [ BEGIN  login ... [ BEGIN own  count +1, save  COMMIT ]  throw 401  ->  ROLLBACK ]
                                  ^ kept: it finished before the throw
```

**Test first:** `AccountLockoutTest` was written and run on the OLD code first. It failed exactly as expected:
3 wrong passwords → "expected 3 but was 0"; 5 wrong → "expected 5 but was 0". That is issue 55, proved on the real
AuthService code for the first time (before, only in a copy).

| File | What changed |
|---|---|
| `service/LoginAttemptService.java` | **new.** `recordFailedLogin(email)` counts one wrong password in its **own** transaction (`REQUIRES_NEW`), so the login's rollback cannot undo it. `isLockedNow(user)` says if the lock is still running. It has to be a separate class: Spring only applies `@Transactional` to calls that come from outside the class |
| `service/LoginUserService.java` | the `catch` calls `recordFailedLogin` instead of saving by itself; the lock check uses `isLockedNow`; a right password also clears `lockedUntil` |
| `entity/User.java` | new field `lockedUntil` (when the lock ends) |
| `repository/UserRepository.java` | new `findByEmailForUpdate` — reads the user and locks the row, so two wrong passwords at the same moment are both counted |
| `db/migration/V4__add_locked_until.sql` | **new** — adds the `locked_until` column |
| `application.properties` | `auth.lockout.max-attempts=5`, `auth.lockout.lock-minutes=15` |

**The rules now:**
1. Each wrong password: +1 (kept, even though the login fails).
2. At 5: locked until now + 15 minutes. Even the right password gets `ACCOUNT_LOCKED` until then.
3. After the 15 minutes: the user may try again, and the counter starts from 0 (one more wrong password does not lock again at once).
4. A right password: counter 0, unlocked.
5. **An account locked before this fix** (locked, but no end time): treated as finished, so it is released. Without this, those accounts would stay locked forever.

**Tests (8, new; AuthService now has 24):** each wrong password counted; 5 lock it for about 15 minutes; locked
rejects even the right password (and never checks it); after the time the right password works and everything
resets; after the time one wrong password does not re-lock; an old lock with no end time is released; a right
password resets the counter; an unknown email gets the same answer and changes nothing.

**Checked that the tests can fail:** the "own transaction" (`REQUIRES_NEW`) was removed on purpose → 4 tests failed
with the counter back at 0. Restored and re-tested. All 24 AuthService tests pass; the jar has `V4` and no test files.

**Not changed on purpose:** UserService still shows `accountLocked`/`failedLoginAttempts` that are never updated
(issue 63); the `ACCOUNT_LOCKED` answer still tells a caller that the email exists (it did before too); there is no
admin "unlock now" button (the time-based unlock needs no person). The gateway's per-IP login limit is unchanged.

**You need to do:** nothing by hand for the column — Flyway runs `V4` at the next start. If you want, try it on the
real system: 5 wrong logins, then the right one → `ACCOUNT_LOCKED`; then
`SELECT failed_login_attempts, account_locked, locked_until FROM users WHERE email = '...'`.

---

# SLA values — real times for every priority (Phase 1, 2026-10-17)

**The problem (known issues 19 and 59):** `SLAConfiguration` still held test values from development:
MEDIUM had to be resolved in **50 seconds**, HIGH assigned in **30 seconds**, MEDIUM assigned in 40 seconds. `CRITICAL` had
no rule, so its "default" gave a resolution time of **0 seconds**: every CRITICAL incident was breached the moment it was
created. These numbers feed the breach timers, the SLA events and the engineer ranking.

**The table you approved (2026-10-17):**

| Priority | Must be assigned within | Must be resolved within |
|---|---|---|
| CRITICAL | 15 minutes | 4 hours |
| HIGH | 1 hour | 1 day |
| MEDIUM | 4 hours | 3 days |
| LOW | 1 day | 7 days |

**Test first:** `SLAConfigurationTest` (8 tests, plain Java, no database) was run on the old code first. All 8 failed, for
example "expected 900 but was 3600" and "CRITICAL resolution: expected true but was false" (the 0 seconds).

| File | What changed |
|---|---|
| `config/SLAConfiguration.java` | the agreed table, written with `Duration` (`Duration.ofHours(4).toSeconds()`) so the numbers can be read. Same method names and signatures. An unknown or missing priority now **throws** `IllegalArgumentException` instead of getting the old silent "0 seconds / 1 hour" |
| `src/test/.../config/SLAConfigurationTest.java` | **new**, 8 tests |

**The 8 tests:** the 4 assignment times; the 4 resolution times; more urgent = shorter (both); resolve is longer than assign
(each priority); nothing is breached at creation (all above 0); every window is at least 1 minute (the risk timer divides by
whole minutes, so 30 seconds gave NaN); an unknown priority fails clearly; a missing priority fails with the same error.

**Checked that the tests can fail:** HIGH assignment was put back to 30 seconds on purpose → 3 tests failed. Restored, and
all 23 ReportingService tests pass (15 outbox + 8 SLA). The jar has no test files.

**Side effect, good:** the risk timer's "0 whole minutes → NaN" problem (LLD-ReportingService) cannot happen any more.

**Decision made by me, tell me if you disagree:** an unknown priority throws. In the Kafka listener that means retry, then the
dead-letter topic, where it can be seen. The other choice (use MEDIUM silently) is exactly the kind of quiet wrong data this
fix removes. A new priority added in IncidentService must also be added to both SLA methods.

**Not changed on purpose (new known issue 66):** old `incident_report` rows keep deadlines worked out from the old values.
Old open incidents will be flagged as breached by the timer, and the engineer stats stay computed from the old numbers.
Recomputing them is a separate decision (see LLD-improvements C5, part 2). The values are still in Java, not in
`application.properties` (LLD-ReportingService gap 2).

**You need to do:** nothing for the database. Decide what to do about the old rows (issue 66) before the SLA readers are built,
because those readers would announce the old breaches.

---

# SLA notifications — saved and shown, NO email (Phase 3, started 2026-10-17)

**Goal:** when an incident breaks (or is about to break) its SLA, the right people get a notification. **No real
email for now** (your decision): each notification is a log line + a row in NotificationService's database, and a
person can see their own with a GET call.

**Who is notified (your decision, 2026-10-17):**
- always the **creator** of the incident;
- the **assigned engineer**, if there is one;
- the **supervisor** of the assigned engineer - or, if nobody is assigned yet, of the creator;
- the same person only once; anyone without a supervisor is simply skipped.

**Old rows** (old notifications without a user id, old SLA deadlines) are left as they are (your decision).

| Step | What | Status |
|---|---|---|
| 1 | NotificationService trusts the sub-folder of `SlaBreachingRiskEvent` (known issue 65) | ✅ done 2026-10-17 |
| 2 | The 2 SLA events carry `createdBy` and `assignedTo`; ReportingService fills them in | ✅ done 2026-10-17 |
| 3 | UserService also returns `supervisorId` for a user | ✅ done 2026-10-17 |
| 4 | NotificationService Flyway `V2`: `recipient_id` column, the 4 SLA types, a `RECORDED` status, remove the old `CHECK (event_type between 0 and 7)` | ✅ done 2026-10-17 (code + tests; the SQL itself runs at your next start) |
| 5 | 4 new listeners: find the people, one row + one log line each, skip a repeated message (`X-Event-Id`) | ✅ done 2026-10-17 |
| 6 | The 3 existing incident listeners also save `recipient_id` (+ honest status and message - your option A) | ✅ done 2026-10-17 |
| 7 | `GET /notifications/me?page&size` (only your own, newest first, with the total) + gateway route | ✅ done 2026-10-19 |

### Step 1 result — ✅ done 2026-10-17

**The problem (known issue 65):** every Kafka message carries its Java class name. The "trusted packages" setting lists
the packages a service may build. The comment in 4 services said the check is "starts with", so `com.project.events`
would also cover `com.project.events.enumsUsedByEvents`. It does not: Spring compares the package name **exactly**.
`SlaBreachingRiskEvent` lives in that sub-folder, so the SLA "at risk" listeners of step 5 would have rejected every
message and parked it in the dead-letter topic.

**Test first:** `TrustedPackagesTest` (NotificationService, 5 tests, plain Java - no Kafka, no database, no Spring start)
reads the **real** setting from `application.properties`, writes each event the way a producer does and reads it back
the way our listeners do. On the old setting: 4 passed, and the risk event failed with "not in the trusted packages".

| File | What changed |
|---|---|
| `NotificationService/.../application.properties` | `trusted.packages=com.project.events,com.project.events.enumsUsedByEvents`, and a correct comment |
| `UserService`, `ReportingService`, `Ai-Service` `application.properties` | **comment only**: "exact match". Their setting stays the same on purpose - they do not read that event, so they should not trust that folder |
| `NotificationService/src/test/.../kafka/TrustedPackagesTest.java` | **new**, 5 tests |
| `ReportingService/src/test/.../ReportingEventsOverKafkaTest.java` | comment only: its old line "this test fails the day that changes" was wrong (it uses its own fixed value) |

**The 5 tests:** the SLA risk event is accepted (the fix); the SLA breached event is accepted; the 3 incident events we
read today are still accepted; **a class from any other package is still rejected** (the Track B2 safety rule); the setting
is never `*`.

**Checked:** all 5 pass after the fix; the jar holds the new setting and no test classes; ReportingService's Kafka test
still passes (4).

**You need to do:** nothing.

### Step 2 result — ✅ done 2026-10-17

**The problem:** the 2 SLA event classes carried only the incident id (plus the breach type, or the risk levels). A
reader could not know **who** to tell.

```
BEFORE:  SLA_BreachedEvent { incidentId, SLA_BreachedType }
AFTER:   SLA_BreachedEvent { incidentId, SLA_BreachedType, createdBy, assignedTo }
         (the same 2 fields added to SlaBreachingRiskEvent)
```

**Test first:** 4 new tests in `ReportingOutboxTest`, one per SLA event type. Each runs the REAL timer, then reads the
saved event's JSON from the outbox table. On the old code all 4 failed ("expected <the creator> but was <>").

| File | What changed |
|---|---|
| `shared-events/.../SLA_BreachedEvent.java`, `.../enumsUsedByEvents/SlaBreachingRiskEvent.java` | + `createdBy`, `assignedTo` (UUID) |
| `ReportingService/.../SLA_BreachedEventProducer.java`, `SLA_BreachingRiskEventProducer.java` | fill both from the report (it already had them) |
| `ReportingService/src/test/.../ReportingOutboxTest.java` | + 4 tests (now 13) |
| `ReportingService/src/test/.../ReportingEventsOverKafkaTest.java` | the round-trip events carry the new fields too, so the test proves they survive Kafka |

**The 4 tests:** assignment breach → names the creator, `assignedTo` empty (nobody is assigned - that is why it
breached); resolution breach → creator + engineer; assignment risk → creator; resolution risk → creator + engineer.

**Checked that the tests can fail:** the `createdBy` line was removed from the resolution-breach producer on purpose →
exactly that test failed ("expected <the creator> but was <null>"). Restored, rebuilt. ReportingService: 27 tests pass;
NotificationService: 5 pass against the new `shared-events`.

**Safe for old messages:** fields were only ADDED, nothing renamed. An SLA message already waiting in an outbox table
(or in Kafka) is read with both new fields as null. Nobody reads these topics yet anyway (issue 18).

**You need to do:** `shared-events` was re-installed into your local Maven cache (`mvn install`). Reload Maven in IntelliJ,
so it sees the new fields.

### Step 3 result — ✅ done 2026-10-17

**The problem:** to tell the supervisor, NotificationService must know who it is. It asks UserService for people with
`POST /internal/UserSpecificDetails/batch`, but the answer had only `userid`, `username` and `email`.

```
BEFORE:  { userid, username, email }
AFTER:   { userid, username, email, supervisorId }     (supervisorId = null when the user has no supervisor)
```

**Test first:** `SupervisorInBatchTest` (UserService, 4 tests, H2) runs the REAL `GetBatchOfUsers_Credentials` and turns
the result into JSON exactly like the HTTP answer - the JSON is what the other service receives. On the old code the 2
"has a supervisor" tests failed ("expected <the supervisor id> but was <>"); the other 2 already passed (they check
what must NOT change).

| File | What changed |
|---|---|
| `UserService/.../dto/UserCredentialDTO.java` | + `supervisorId` |
| `UserService/.../service/UserService.java` | `GetBatchOfUsers_Credentials` fills it: `user.getSupervisor() == null ? null : user.getSupervisor().getUserid()` |
| `UserService/pom.xml` | + `h2` 2.2.224, **test only** (first real test in UserService) |
| `NotificationService/.../dto/UserCredentialDTO.java` | + `supervisorId` (its copy of the same answer) |
| `UserService/src/test/.../service/SupervisorInBatchTest.java` | **new**, 4 tests |
| `NotificationService/src/test/.../dto/UserCredentialDtoTest.java` | **new**, 2 tests: the field is read; an answer WITHOUT it (an older UserService) still works |

**The supervisor link is LAZY.** Asking a lazy link only for its id does not load the supervisor's row. Proved: the test
calls the method with NO transaction open, where loading the row would crash (`LazyInitializationException`). It did not.

**Checked that the tests can fail:** the line that fills `supervisorId` was removed on purpose → the 2 supervisor tests
failed ("but was <null>"). Restored (byte-for-byte), rebuilt, 4 pass. The UserService jar has no H2 and no test classes.

**Order of restarts does not matter:** NotificationService reads an answer without `supervisorId` as "no supervisor", and
nothing reads the new field yet (that is step 5).

**You need to do:** reload Maven in IntelliJ (UserService has a new test dependency).

### Step 4 result — ✅ done 2026-10-17 (the SQL runs on your real database at the next start)

**What changes in the `notification_log` table** (one new Flyway file, `V2__sla_notifications.sql`, ONE statement):

| # | Change | Why |
|---|---|---|
| 1 | + column `recipient_id` (binary(16), like the other id columns) | to answer "show MY notifications" - only the email was saved before |
| 2 | `event_type` list + `SLA_ASSIGNMENT_BREACHED`, `SLA_RESOLUTION_BREACHED`, `SLA_ASSIGNMENT_AT_RISK`, `SLA_RESOLUTION_AT_RISK` | MySQL refuses a word that is not in the list |
| 3 | `status` list + `RECORDED` | honest status: saved and visible, no email sent |
| 4 | DROP the old `CHECK (event_type between 0 and 7)` | it would refuse the new types (and maybe already `USER_UPDATED`) |
| + | index `(recipient_id, created_at)` | "my newest notifications" stays fast |

New words go **at the end** of each list (MySQL stores an enum as its position: the middle would change old rows).
**One statement** = all or nothing (MySQL cannot roll back a table change). Old rows: unchanged, `recipient_id` = NULL.

**Test first:** `NotificationMigrationV2Test` (6 tests, plain Java) reads the migration FILE and checks its rules,
because the tests have no real MySQL. Before the file existed, all 6 failed. The rules: the database lists hold
exactly the Java enum values; the old values keep their exact order (new ones only at the end); the CHECK is
dropped; `recipient_id` is `binary(16)`; it is one single statement.

**Files changed in step 4:**

| File | New / changed | What |
|---|---|---|
| `NotificationService/src/main/resources/db/migration/V2__sla_notifications.sql` | **new** | the table change above |
| `NotificationService/.../Entity/EventType.java` | changed | + the 4 SLA types |
| `NotificationService/.../Entity/NotificationStatus.java` | changed | + `RECORDED` |
| `NotificationService/.../Entity/NotificationLog.java` | changed | + `recipientId`, + the index |
| `NotificationService/pom.xml` | changed | + `h2` 2.2.224, **test only** |
| `NotificationService/src/test/.../Entity/NotificationMigrationV2Test.java` | **new** | 6 tests: the migration file's rules |
| `NotificationService/src/test/.../Entity/NotificationLogStoreTest.java` | **new** | 4 tests (H2): the new types, status and field save and load; an old row without `recipient_id` still works; the duplicate guard (same event + same email) still holds; one event for 2 people = 2 rows |
| `PROGRESS.md`, `CLAUDE.md`, `BUGS-TO-FIX-LATER.md` (E10), `notes/FLYWAY-NOTES.md`, `notes/LLD/LLD-NotificationService.md` | docs | |

**Checked that the tests can fail:** (1) a new type put in the MIDDLE of the list → the "only at the end" test failed;
(2) one new type left out of the database list → the "exactly what Java can save" test failed. Both restored. All 17
NotificationService tests pass. The jar has `V2`, no H2, no test classes.

**NOT proved by any test - only by your next start:** that MySQL accepts the SQL. The rules are checked, but the in-memory
test database cannot run these MySQL commands the same way.

**You need to do:**
1. Before you start NotificationService the next time, back up the one table:
   `mysqldump -u <your user> -p inicident-management-notificationservicedb notification_log > notification_log_backup.sql`
   (keep the file outside the project).
2. Check `SELECT VERSION();` is 8.0.16 or newer (DROP CHECK needs it).
3. Start NotificationService. Flyway runs `V2` once.
4. Check: `SHOW CREATE TABLE notification_log;` - the new column, the longer lists, `RECORDED`, and no `CHECK` line.
5. If the start fails on V2: the table is unchanged (one statement). Send me the error. Flyway will have written a
   "failed" row in `flyway_schema_history` that must be removed before the next try - I will tell you exactly how.
6. Reload Maven in IntelliJ (new test dependency).

### Step 5 result — ✅ done 2026-10-17

**The 4 SLA topics finally have a reader (known issue 18).**

```
ReportingService --(outbox)--> Kafka: Sla-...-Topic   { incidentId, createdBy, assignedTo, ... } + header X-Event-Id
      |
      v
SlaEventConsumer (4 listeners)  -> picks the type + the message text + the event id (from X-Event-Id)
      |
      v
SlaNotificationService
  1. no createdBy (an old message)?  -> warn, record nothing
  2. UserService: creator + engineer            (no database transaction open while waiting)
  3. supervisor = of the engineer, or of the creator if nobody is assigned -> UserService once more if needed
  4. each person once: already recorded for this event? skip : save a RECORDED row + one log line (user id only)
```

**Test first:** the listener class was written, with the service as an EMPTY skeleton. `SlaNotificationTest` (11 tests,
real code, H2, fake UserService) then failed 10 of 11 (the 11th - "an old message saves nothing" - correctly passed on an
empty skeleton). One failure was a mistake in the test itself (how the fake was set up), fixed before writing the code.

| File | New / changed | What |
|---|---|---|
| `NotificationService/.../kafka/consumer/SlaEventConsumer.java` | **new** | the 4 `@KafkaListener`s; event id from the `X-Event-Id` header (raw bytes), or a fixed id worked out from the message if the header is missing |
| `NotificationService/.../service/SlaNotificationService.java` | **new** | who is told, and one `RECORDED` row + log line per person |
| `NotificationService/pom.xml` | changed | + `spring-kafka-test`, **test only** |
| `NotificationService/src/test/.../kafka/consumer/SlaNotificationTest.java` | **new** | 11 tests: who is told (3 cases), each person once, no supervisor = skipped, a repeat (with and without the header), two risk levels = two alerts, UserService called with no transaction open, an old message saves nothing, UserService down = nothing saved + the error goes back to Kafka |
| `NotificationService/src/test/.../kafka/consumer/SlaListenersOverKafkaTest.java` | **new** | 2 tests: the WHOLE service + a real embedded Kafka - a message sent like the outbox sends it reaches the right listener and is recorded (also proves the issue-65 fix and the header) |

**Checked that the tests can fail:** (1) always the creator's supervisor → the "engineer's supervisor" test failed; (2) the
`X-Event-Id` header ignored → the "id from the header" test failed; (3) the issue-65 setting undone → the Kafka test for
the "at risk" message failed. All restored byte-for-byte. **All 30 NotificationService tests pass.** The jar has the 2
new classes, no H2, no test Kafka, no test classes. The SLA log lines hold user ids only (checked: 0 emails), and carry
the trace id from the Kafka message.

**Not changed on purpose:** the 3 incident listeners (that is step 6); no email (issue 16); no "read / unread".

**You need to do:** reload Maven in IntelliJ (new test dependency). To see it for real later (after step 4's migration
has run): set one open incident's `assignment_deadline` in ReportingService's `incident_report` to a past time; within
about 70 s the timer sends the breach event, and NotificationService's log shows "SLA notification ... recorded for user
..." and `notification_log` gets the rows.

### Step 6 result — ✅ done 2026-10-17 (option A)

**The problem:** the 3 old incident listeners (created, assigned, status changed) saved their rows **without the user id**,
so step 7's "show MY notifications" would not find them. And the rows were not honest - which matters now that users
will read them:

| | Before | After |
|---|---|---|
| user id (`recipient_id`) | not saved | saved |
| status | `SUCCESS` (set inside an EMPTY try block - nothing is ever sent, issue 16) | `RECORDED` (saved, no email sent) |
| `sentAt` | set to "now" | empty - nothing was sent |
| message | "Email Successfully sent to <email> for **Incident Created** Event with Incident Id: ..." - for EVERY event type | "Incident <id> was created." / "... was assigned." / "Incident <id>: status changed from ASSIGNED to RESOLVED." - no email address |
| log line | "Email Successfully sent to user ..." | "Notification ... recorded for user ... - no email sent" (user id only) |

**Test first:** `IncidentNotificationTest` (4 tests, real listener + service code, H2, fake UserService). On the old code 3
failed (no user id); the 4th - the duplicate guard - correctly passed (it checks what must NOT change).

**Files changed in step 6:**

| File | New / changed | What |
|---|---|---|
| `NotificationService/.../service/EmailNoticationService.java` | changed | `storeNotificationLog` gets a `message` parameter; saves `recipientId`, status `RECORDED`, no `sentAt`; the fake "send" try/catch removed (a comment marks where real sending will go); honest log line |
| `NotificationService/.../kafka/consumer/IncidentEventConsumer.java` | changed | each of the 3 listeners passes the right message for its event |
| `NotificationService/src/test/.../kafka/consumer/IncidentNotificationTest.java` | **new** | 4 tests: created / assigned / status change - right people, user id, `RECORDED`, no `sentAt`, correct text without an email address; the same event twice is saved once |
| `PROGRESS.md`, `CLAUDE.md` (issue 16 half resolved, tests 230), `notes/LLD/LLD-NotificationService.md` | docs | |

**Checked that the tests can fail:** (1) the user id not saved → 3 tests failed; (2) the status back to `SUCCESS` → 3
failed; (3) old and new status swapped in the message → the status test failed. (A first try of check 3 changed 0 lines
because of a wrong search pattern - noticed and redone, so its "pass" was not counted.) All restored byte-for-byte.
**All 34 NotificationService tests pass.** The jar has no test parts; the log lines hold user ids only (checked: 0 emails).

**Not changed on purpose:** who is told for incident events (the same people as before); old rows (they keep their old
text and have no user id - your decision); no real email yet.

**You need to do:** nothing extra. (The step 4 migration must have run before this code saves `RECORDED` rows - it runs
at the next start, as before.)

### Step 7 result — ✅ done 2026-10-19

**`GET /notifications/me?page=0&size=20`** - your own notifications, newest first, with the total.

```
You (logged in) -> APIGateway: token check, X-User-Id, rate limit -> NEW route /notifications/** -> NotificationService
   -> SecurityConfig: users only (ENGINEER / SUPERVISOR / ADMIN)
   -> NotificationController: page 0+, size 1-100 (else a clear 400)
   -> MyNotificationsService -> rows WHERE recipient_id = your id ORDER BY created_at DESC

answer: { total, page, size, items: [ { id, incidentId, type, status, message, createdAt } ] }
```

No user id in the URL - it comes from the login, so nobody can ask for someone else's list. No email or name in the
answer. Old rows without a user id never show (your decision).

**Test first:** `MyNotificationsApiTest` (9 tests) starts the WHOLE NotificationService and sends real HTTP requests with
the headers the gateway sends. Before the endpoint existed 7 failed (404); the 2 that passed check protections that
already existed and must stay (no login → 403; wrong secret → 403). `GatewayRoutesTest` (3 tests) reads the gateway's real
route list: before the new route, 2 failed ("no gateway route for /notifications/**").

**Files changed in step 7:**

| File | New / changed | What |
|---|---|---|
| `NotificationService/.../controller/NotificationController.java` | **new** | `GET /notifications/me`, page/size checked at the door |
| `NotificationService/.../service/MyNotificationsService.java` | **new** | reads one person's page and builds the answer |
| `NotificationService/.../dto/MyNotificationsResponseDTO.java` | **new** | `total`, `page`, `size`, `items` |
| `NotificationService/.../dto/NotificationItemDTO.java` | **new** | one notification, without email / name / event id |
| `NotificationService/.../repository/NotificationLogRepository.java` | changed | + `findByRecipientIdOrderByCreatedAtDesc` (uses the V2 index) |
| `NotificationService/.../config/SecurityConfig.java` | changed | + `/notifications/**` → users only |
| `NotificationService/pom.xml` | changed | + `common-web` (the shared error shape, the 400 for a bad page/size, the HTTP trace id) |
| `APIGateway/src/main/resources/application.properties` | changed | + route 5: `/notifications/**` → `lb://NotificationService` |
| `NotificationService/src/test/.../controller/MyNotificationsApiTest.java` | **new** | 9 tests |
| `APIGateway/src/test/.../GatewayRoutesTest.java` | **new** | 3 tests |
| `PROGRESS.md`, `CLAUDE.md`, `notes/LLD/LLD-NotificationService.md`, `LLD-APIGateway.md`, `LLD-improvements.md` | docs | |

**The 9 endpoint tests:** only my own rows, newest first, with the total (another person's and an old row without a user
id are left out); paging and total; default page 0 / size 20; an empty list; no email or name in the answer; size 0, size
101 and page -1 → 400 in the usual error shape (with `traceId`); no login → 403; identity headers with a wrong secret → 403;
a service-to-service caller → 403.

**Checked that the tests can fail:** (1) the "users only" rule removed → the service-to-service call got a **500** (it tried
to read the service name as a user id) - caught; (2) everyone's rows instead of only mine → 3 tests failed; (3) the size
limit raised from 100 to 100000 → the 400 test failed. All restored byte-for-byte. NotificationService: **43 tests pass**;
APIGateway: **56 pass**. The NotificationService jar has the controller and common-web, and no test parts.

**Not done on purpose:** read / unread, delete, an admin view of someone else's list, filters by type.

**You need to do:**
1. Reload Maven in IntelliJ (NotificationService has a new dependency).
2. Restart **APIGateway** too - it only learns the new route at start.
3. Try it: log in, then `GET http://localhost:8082/notifications/me` with `Authorization: Bearer <token>`.

---

# Option B — the 8 `contextLoads()` tests made safe (2026-10-19)

**The problem:** each service had an auto-generated `contextLoads()` that started the app with the REAL
`application.properties` - your real MySQL, Kafka, Eureka and secrets. "Run all tests" was therefore unsafe.

**The fix:** each one now starts its service in the test world:

| Service | Test settings |
|---|---|
| Eureka | peer address `localhost:1` (it has no database or Kafka anyway) |
| APIGateway | Eureka off, dummy secrets |
| Auth, User, Incident, Notification, Reporting | in-memory H2 (MySQL mode), Flyway off, Eureka off, Kafka `localhost:1`, listeners off (where there are any), dummy secrets |
| Ai-Service | the same, H2 in **Postgres** mode, dummy Gemini key. Checked first: nothing at start-up calls Gemini or Postgres (the two `@PostConstruct` only read prompt files; the vector store has its size set) |

**Files:** the 8 `*ApplicationTests.java`, + `h2` (test only) in `Ai-Service/pom.xml`.

**Proof (all with the real env vars `user`, `password`, `secretkey`, `internalsecret`, `apikey` REMOVED):**
1. All 8 `contextLoads()` pass.
2. Their logs show **0** attempts to reach `localhost:3306`, `:5433`, `:9092`, `localhost:8081`; only `jdbc:h2:mem`.
3. Break check: the dummy `internalsecret` line removed from one test → it fails with "Could not resolve placeholder
   'internalsecret'" (it does not fall back to anything real). Restored.
4. **"Run all tests" on every service:** Eureka 1, APIGateway 57, AuthService 25, UserService 5, IncidentService 10,
   NotificationService 44, ReportingService 28, Ai-Service 1 = **171 tests, 0 failures**, and no real address in any log.
5. The Ai-Service jar has no H2 and no test classes.

**Same day, comments only:** my long comments in 31 files (steps 1-7, SLA values, lockout, their tests) were made
short and plain - 138 lines fewer. Proved "comments only": with all comments removed, the code of all 31 files
is identical before and after; all tests of the 5 touched services pass (158). The 2 migration files (`V2`, `V4`)
were NOT touched: Flyway checks every byte of an applied migration, comments included.

**Notes:** `notes/sla-notifications.md` (steps 1-7: need, goal, flow charts, how to check on the real system).

---

# Small bugs — one at a time, test first (started 2026-10-19)

Order agreed: issue 4 → 3 → 56 → 64 → 1.

| # | Issue | What was wrong | Status |
|---|---|---|---|
| Bug 4 | 4 | Signup event listed every role as the one outer `role` (`role1` ignored) | ✅ Done 2026-10-19 |
| Bug 1 | 3 | Any status change overwrites `rootCause` / `resolutionNotes`, even with nothing | ✅ Done 2026-10-21 |
| Bug 2 | 56 | Assign has no ownership check; a RESOLVED incident can be re-assigned | ✅ Done 2026-10-21 |
| Bug 3 | 64 | No `@Version`: two people at once, last save wins | ✅ Done 2026-10-22 (Flyway `V4` not yet run on your DB) |
| Bug 5 | 1 | Password change saved only by luck (open-session-in-view); `passwordUpdatedAt` never set | ✅ Done 2026-10-22 (+ new rule: new ≠ old) |

## Bug 4 (issue 4) — the signup event lists the real roles ✅ 2026-10-19

**Problem:** `RegisteringUserService` built the event's role list with `map(role1 -> role.getRolename())`. It used
the outer `role` (always ROLE_ENGINEER) instead of each element. Harmless today (a new user has one role), wrong the
day a user starts with two.

**Test first:** `AuthServiceOutboxTest.theSignupEventListsEveryRoleTheUserReallyHas` gives the user ROLE_SUPERVISOR
before signup, then compares the event's roles with the database. Old code: **failed** (database
`[ROLE_ENGINEER, ROLE_SUPERVISOR]`, event `[ROLE_ENGINEER]`) - that run is also the break check.

**Fix:** `map(userRole -> userRole.getRolename().name())`.

**Proof:** all 26 AuthService tests pass (`./mvnw.cmd -o test`), 0 real addresses in the log.

**Files:** `AuthService/.../service/RegisteringUserService.java` (1 line + 1 comment line),
`AuthService/src/test/.../service/AuthServiceOutboxTest.java` (1 new test). No database change.

## Bug 1 (issue 3) — a status change no longer erases the root cause ✅ 2026-10-21

**Problem:** `updateIncidentStatusBasedOnRole` always copied `rootCause` and `resolutionNotes` from the request. A
REOPENED with no texts erased the saved ones. RESOLVED with no texts was accepted.

**Rules agreed:**
1. A missing or blank text never erases a saved one. A real text replaces it.
2. RESOLVED needs a root cause AND resolution notes (sent now, or saved before). Else `400 ROOT_CAUSE_REQUIRED`.
3. REOPENED keeps the old texts; the next RESOLVED may replace them.

**Behaviour change to know:** a client that resolves without texts now gets a 400. On purpose: Ai-Service learns
from resolved incidents.

## Bug 2 (issue 56) — assign has an ownership check ✅ 2026-10-21

| Who | Which incident | To which engineer |
|---|---|---|
| Admin | any | any active engineer |
| Supervisor | only one `supervisorMayTouch` allows: created or assigned by them, or created by / assigned to their team | only an engineer of their team |

For everyone: RESOLVED or CLOSED → `409 INCIDENT_ALREADY_CLOSED` ("reopen it first"; before, it quietly became
ASSIGNED). The same engineer again → `409 ALREADY_ASSIGNED_TO_THIS_ENGINEER` (before, a second event, +1 workload).
New errors: `403 INCIDENT_NOT_IN_YOUR_TEAM`, `403 ENGINEER_NOT_IN_YOUR_TEAM`. The team list is fetched before the
transaction (outbox rule). Change-status now uses the same `supervisorMayTouch` (same logic, moved, not changed);
list / read-one / update-details keep their own copies (issue 57, not touched).

## Proof for bugs 1 and 2

- **Test first:** new `IncidentRulesTest` (15 tests). On the old code **11 failed**, each one a bug; the 4 that
  passed are the "allowed" cases, which guard against blocking too much.
- **After the fix:** all 24 IncidentService tests pass (`./mvnw.cmd -o test`), 0 real addresses in the log.
- **Break checks on the new code** (restored afterwards, byte-identical): the team rule without "team incidents" →
  5 tests fail; blank text erasing again → `blankTextsDoNotEraseEither` fails.
- `IncidentServiceOutboxTest`'s assign test now gives the supervisor a team with that engineer (the new rule).

**Files:** `IncidentService/.../service/IncidentService.java` (status texts, assign checks, new `supervisorMayTouch`),
`IncidentService/src/test/.../service/IncidentRulesTest.java` (new), `IncidentServiceOutboxTest.java` (1 line).
No database change.

## Bug 5 (issue 1) — the password change no longer depends on luck ✅ 2026-10-22

**Problem:** `changePassword` set the new password on the user object but never saved it and had no transaction. It
was saved only because Spring's open-session-in-view kept the object "managed" and the logout's save carried it. If
that setting were ever switched off, the user would see "Password changed successfully", keep the OLD password, and
be logged out everywhere. `passwordUpdatedAt` was never set.

**Fix (one file, `ChangePasswordService`), ONE `TransactionTemplate`:**
1. load the user; 2. old password wrong → `401 INVALID_OLD_PASSWORD`; 3. **new rule (your request):** new password
equal to the current one → `400 NEW_PASSWORD_SAME_AS_OLD` (checked only after the old password is right); 4. set the
new hash and `passwordUpdatedAt = now`; 5. `save` (explicit); 6. `LogoutUser()` - revokes every old login and saves the
logged-out event; it joins this transaction. All saved, or none.

**Proof:**
- New `ChangePasswordTest` (6). Its setup has no open-session-in-view, so the hidden luck is gone. On the old code
  **3 failed**: the new password was LOST (the old one still worked), the date was not set, same-as-old was accepted.
  (First version of the date test passed wrongly - signup had just set the date. Fixed by setting an old date first.)
- After: all 44 AuthService tests pass, 0 real addresses in the log.
- Break 1 - transaction removed, `save` kept: "both or neither" fails (new password saved although the logout failed).
- Break 2 - `save` removed, transaction kept: all pass. Inside a transaction Hibernate writes a changed (managed) object
  by itself at commit ("dirty checking") - the same reason the old code worked by luck. The line stays for readers.
- No database change.

**Found, not fixed (issue 68):** a wrong old password here is not counted by the account lockout.

**Files:** `AuthService/.../service/ChangePasswordService.java`, `AuthService/src/test/.../ChangePasswordTest.java` (new).
**Notes:** `notes/password-change-and-transactions.md`.

## Bug 3 (issue 64) — two people saving the same incident at once ✅ 2026-10-22

**Problem:** each request reads the incident, does its checks (and slow UserService calls), then saves. Two
requests that overlap both read the same old incident; the second save silently overwrites the first ("lost
update"), and BOTH "assigned" events are sent (Reporting counts +1 workload for both engineers).

**Fix - optimistic locking:**
1. `Incident.version` with `@Version` (Flyway `V4`: `bigint NOT NULL DEFAULT 0`, so old rows start at 0).
2. Hibernate now saves with `UPDATE ... SET version = N+1 WHERE id = ? AND version = N`. If someone saved first, 0 rows
   match, nothing is written, and Spring throws `OptimisticLockingFailureException`. The transaction rolls back, so the
   loser's outbox event is not saved either.
3. `common-web`'s `GlobalExceptionHandler` maps that exception to **409 `CONCURRENT_UPDATE`** ("Someone else changed
   this at the same time. Reload it and try again."). Before, it would have been a 500. Central, so any entity that
   gets `@Version` later is covered too.

**Proof:**
- Test first. `GlobalExceptionHandlerTest.optimisticLockMapsTo409` failed (got 500). In `IncidentRulesTest`,
  `twoAssignsAtTheSameMomentTheSecondIsRefusedNotSilentlyOverwritten` forces the overlap with two latches (B reads,
  A saves, B saves its old copy): on the old code B said **OK** and overwrote A. `everySavedChangeRaisesTheVersionByOne`
  failed (no column).
- After: common-web 20/20, IncidentService 27/27 (all tests), race test 3 more runs green; the other 5 services that use
  common-web still start (`contextLoads`). 0 real addresses in any log.
- Break check: `@Version` removed → both new tests fail. Restored, byte-identical.
- **Not proved by tests:** the `V4` SQL itself (H2 tables come from Hibernate). Checked on the first real start
  (Flyway, then `ddl-auto=validate`).

**Limits (on purpose):** only `Incident` has a version. And the service re-reads the incident at the start of each
request, so this protects overlapping *requests*; a person looking at an old screen for minutes is protected only if
the client sends back the version it saw (an `If-Match`-style check) - a later idea.

**Files:** `IncidentService/.../entity/Incident.java` (`version` field), `IncidentService/.../db/migration/V4__add_incident_version.sql`
(new), `common-web/.../handler/GlobalExceptionHandler.java` (409 mapping; **re-installed** into `~/.m2`), tests:
`IncidentRulesTest.java` (2 new), `GlobalExceptionHandlerTest.java` (1 new). **Notes:** `notes/optimistic-locking.md`.

## Role management (issue 67) — first admin, guard rails, audit ✅ 2026-10-21

**Problems:** (1) the first admin was made by a hand edit in MySQL, which sends no `roles.update`, so UserService
kept saying ENGINEER; (2) nothing stopped removing the last admin; (3) no record of who changed whose roles.

**What changed:**
1. `AdminBootstrap` (new, `config/`): at start-up, if `auth.bootstrap.admin-email` (env var `adminemail`) is set AND no
   admin exists, that account becomes `ROLE_ADMIN` through `UpdateRoleService.MakeFirstAdmin` - the normal path, so
   `roles.update` is sent, the user is logged out, an audit row is saved. Admin exists / empty / unknown email → nothing
   (a warning for the unknown email; the service still starts). The email is never logged.
2. Guards in `UpdateRoleService` (only when a change takes ADMIN away): your own ADMIN role → `403
   CANNOT_REMOVE_OWN_ADMIN_ROLE`; the last admin → `409 LAST_ADMIN`. The transaction first locks the ADMIN row of the
   `roles` table (`SELECT ... FOR UPDATE`), so role changes run one after the other.
3. Audit: new table `role_change_audit` (Flyway `V5`), one row per change (whose, who, old, new, source, when), saved
   in the same transaction as the change.
4. `RoleController` passes the admin's id; `UpdatingUserRoles` and the bootstrap share one private `changeRoles`.

**Proof:**
- New `RoleManagementTest` (12). On the old code **7 failed** - including the race: two admins removing each other
  at the same moment both succeeded (`[OK, OK]`), leaving **zero** admins. The 5 that passed are the "allowed" cases.
- After the fix all 38 AuthService tests pass, 0 real addresses in the log; the race test passed 3 more runs in a row.
- Break checks (restored, byte-identical): no lock → the race test fails; no own-role guard → its test fails;
  bootstrap without "admin exists" → its test fails.
- **Not proved by tests:** the `V5` SQL itself (tests use H2 with tables made by Hibernate). It is checked on the first
  real start: Flyway runs it, then `ddl-auto=validate` compares it with `RoleChangeAudit` and stops the service if they
  differ. Column types copy ones already validated (`binary(16)` like `users.userid`, `datetime(6)` like `locked_until`).

**How to make the first admin (once):** sign up → set env var `adminemail` to that email → start AuthService → log
says "user <id> is now ADMIN" → remove `adminemail` → log in again (the old token was revoked).

**Files:** `AuthService/.../config/AdminBootstrap.java` (new), `entity/RoleChangeAudit.java` (new),
`repository/RoleChangeAuditRepository.java` (new), `db/migration/V5__role_change_audit.sql` (new),
`service/UpdateRoleService.java`, `repository/UserRepository.java` (2 count queries), `repository/RoleRepository.java`
(locking read), `controller/RoleController.java` (passes the admin's id), `application.properties` (setting),
tests: `RoleManagementTest.java` (new), `AuthServiceOutboxTest.java` (2 calls pass an admin id).
**Notes:** `notes/assign-rules-and-role-management.md`.

## Later ideas (not started, kept so they are not lost)

1. **History of every root cause** - a table with one row per resolve (who, when, root cause, notes), so a REOPENED
   incident keeps every earlier attempt, not just the last one. Bigger than the bug fix; nobody needs it yet.
   Also in `notes/LLD/LLD-improvements.md` E7.
2. ~~**Role management (issue 67)**~~ **Done 2026-10-21** (section above). Still open from it: an API to READ the audit
   rows (today SQL only). The original proposal:
   - keep signup = ENGINEER only (never let a user choose their role);
   - first admin: a one-time start-up step that promotes an EXISTING account named in a setting
     (`auth.bootstrap.admin-email`), only while no admin exists, through `UpdateRoleService` so `roles.update` is sent
     and UserService learns it. No password in any setting. Then remove the setting;
   - guard: never remove the last admin (`409`); an admin may not take away their own ADMIN role;
   - audit: one row per role change (who, whose, old roles, new roles, when).

---

# Auth v2 — RSA keys (started 2026-10-23)

**Why:** tokens were signed with ONE shared HMAC secret held by AuthService, the gateway and UserService. Whoever can
check a token with it can also CREATE one, so a leak from any of the three lets an attacker forge an ADMIN token.
**Idea:** RS256 key pair. AuthService signs with the PRIVATE key (only it has it); the gateway checks with the PUBLIC key,
which cannot sign.

| Step | What | Status |
|---|---|---|
| 1 | AuthService signs with the private key (RS256 + `kid`) | ✅ 2026-10-23 |
| 2 | Gateway checks with the public key, only RS256; its `generateToken` + expiry removed | ✅ 2026-10-23 |
| 3 | UserService drops the unused secret + jjwt; `.gitignore` gets `*.pem`/`*.key` | ✅ 2026-10-24 |
| 4 | Full proof run (all services, start-up tests without real env vars) | ✅ 2026-10-24 |
| 5 | Real-system check (you: generate keys, set env vars, restart Auth + gateway) | ✅ Done 2026-10-26 (owner ran it by hand, Postman) |
| 6 | Docs + concept note + commit message | ✅ docs + note `notes/rsa-keys-and-token-signing.md` 2026-10-24. Commit message: give it when asked. |

## Steps 1-2 (2026-10-23)

**Code:**
- AuthService `security/RsaKeys` (new): reads `jwtprivatekey` (PKCS#8, PEM or one-line base64), refuses empty, garbage,
  a public key, or < 2048 bits - at start-up, with a message naming the setting and never the key text.
- AuthService `config/SecurityConfig`: the key is read once in the constructor; `generateToken` signs with
  `Jwts.SIG.RS256` and writes `kid` (`jwt.key-id=key-1`). `jwt.secret` removed from its settings.
- Gateway `utils/RsaKeys` (new): reads `jwtpublickey` (X.509); refuses a private key, garbage, < 2048 bits.
- Gateway `utils/JwtUtil`: rewritten to check with the public key and accept only RS256; same 5 methods for
  `JwtGatewayFilter` (unchanged). Removed: `generateToken`, the secret, `jwt.expirationMs` (issue 39).
- The 3 start-up tests get a throwaway key from `TestKeys` (`@DynamicPropertySource`) instead of `secretkey=dummy`.

**Tests:** `TokenSigningTest` (AuthService, 8) and `JwtUtilTest` (gateway, 10). Before, NO test checked real signing:
the filter test mocks `JwtUtil` and the login tests mock `generateToken`. On the old code the new tests could not even
compile (no way to pass a key) - a weak "red", so the break checks carry the proof.

**Proof:** AuthService 52/52 and gateway 67/67 (all tests), 0 real addresses. Break checks (restored, byte-identical):
1. gateway checks with HMAC made from the public key, pin kept → real tokens refused (caught); the trick still refused by the pin.
2. only the RS256 pin removed → all pass: the library itself refuses HS256 with a public key. The pin is a 2nd layer.
3. AuthService signs HS256 again → caught. 4. the 2048-bit minimum removed → caught.
5. the full classic bug (HMAC check + no pin) → the attacker's ADMIN token was **accepted** → caught by
   `theAlgorithmTrickIsRefused`.
(A first break-check run reported all exit=1 but the tests had never run - `mvnw.cmd` was not found from Python.
Spotted because break 2 "failed" when it should pass; re-run with the full path.)

**Important until step 5:** AuthService needs `jwtprivatekey` and the gateway needs `jwtpublickey` to START. The old
`secretkey` is no longer enough for them (and since step 3 nothing needs it at all). Old tokens stop working after the switch: everyone logs in once.

**Files:** AuthService `config/SecurityConfig.java`, `security/RsaKeys.java` (new), `application.properties`,
tests `TestKeys.java` (new), `security/TokenSigningTest.java` (new), `AuthServiceApplicationTests.java`,
`OutboxWiringTest.java`. Gateway `utils/JwtUtil.java`, `utils/RsaKeys.java` (new), `application.properties`, tests
`TestKeys.java` (new), `utils/JwtUtilTest.java` (new), `ApiGatewayApplicationTests.java`.

## Steps 3-4 (2026-10-24)

**Step 3 - cleanup:**
- UserService: `jwt.secret` + `jwt.expirationMs` removed from `application.properties`; the 3 jjwt dependencies removed
  from `pom.xml`; the dead `util/JwtUtil.java` (69 lines, all commented out, nothing referred to it) deleted, and its
  empty folder. Its start-up test no longer carries `secretkey=dummy`.
- `.gitignore`: `*.pem` and `*.key` added (checked with `git check-ignore`).
- Result: **no service reads `secretkey` any more.** A search of code, settings and tests finds it only in comments.

**Found while checking:** `.idea/workspace.xml` (IntelliJ's run configurations) holds the REAL old `secretkey` value -
and `user`, `password`, `internalsecret` - in plain text, 7 times. Checked: `.idea/` is git-ignored, not tracked, never
committed. Not touched (your IDE's file). That is also where `jwtprivatekey` / `jwtpublickey` will go in step 5.

**Step 4 - full proof, with the real env vars REMOVED** (`user`, `password`, `secretkey`, `internalsecret`, `apikey`,
`jwtprivatekey`, `jwtpublickey`, `adminemail`):
- ALL tests: AuthService 52/52, APIGateway 67/67, UserService 5/5 (it builds and starts without jjwt and without any key).
- Start-up test of the other 5: Eureka, IncidentService, NotificationService, ReportingService, Ai-Service - all pass.
- 0 attempts to reach 3306 / 5433 / 9092 / 8081 in any log.
- Scan of all 610 pending added lines: no key text, no long base64 blob, not the old secret value, no file over 300 KB.

**Step 3 files:** `UserService/pom.xml`, `UserService/src/main/resources/application.properties`,
`UserService/src/test/.../UserServiceApplicationTests.java`, `UserService/.../util/JwtUtil.java` (deleted), `.gitignore`.

## Auth v2 - Step 5 (pending)

**Status: ✅ DONE 2026-10-26** - tests 2-6 passed by hand through the gateway (test 1 skipped: no old token; databases were empty, so a new user was signed up first). The plan below is kept for the next machine. **Before that date:** Until then **AuthService and
APIGateway do not start on the real system** (they need the two keys). Nothing else is affected: UserService and the
other services need no key. The code and every test are finished.

**What it is:** create the real RSA key pair, give each service its half, restart, and try it by hand.

```
 A. make the key pair (outside the project)
 B. PRIVATE key -> AuthService, PUBLIC key -> gateway  (IntelliJ run configurations)
 C. restart the services
 D. test by hand through the gateway (login, API call, edited token, logout)
 E. tell Claude the results -> commit message
```

### A. Make the key pair, outside the project
Pick a folder OUTSIDE `E:\dev-spring-boot\IMP` (suggestion: `~/jwt-keys` in Git Bash = a folder in your user home).
`openssl` is in Git Bash. Run, in that folder:
```bash
mkdir -p ~/jwt-keys && cd ~/jwt-keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem
head -1 private.pem public.pem     # must show BEGIN PRIVATE KEY / BEGIN PUBLIC KEY
```
Never copy these files into the project (`*.pem`/`*.key` are git-ignored, but do not rely on it). Keep a private
backup of `private.pem`; if it is lost, make a new pair and everyone logs in again.

### B. Give each service its key (IntelliJ run configurations)
Use IntelliJ, NOT Windows `setx`: `setx` silently cuts values at 1024 characters; the private key is ~1,600.
1. Copy the private key as ONE line, straight to the clipboard (nothing is shown on screen):
   `grep -v -- '-----' ~/jwt-keys/private.pem | tr -d '\r\n' | clip`
   IntelliJ -> Run -> Edit Configurations -> **AuthServiceApplication** -> Environment variables -> add `jwtprivatekey` = paste.
2. Same for the public key: `grep -v -- '-----' ~/jwt-keys/public.pem | tr -d '\r\n' | clip`
   -> **APIGateway** run configuration -> add `jwtpublickey` = paste.
3. Optional tidy-up: delete `secretkey` from all run configurations (nothing uses it any more).
Never swap them: AuthService gets the PRIVATE key only, the gateway the PUBLIC key only (a wrong one stops the service
at start-up with a clear message). No other service gets a key.

### C. Restart
1. If the services were not started since the last commit, **back up the databases first** - older migrations run on
   this start too: Auth `V4`+`V5`, Incident `V4`, Notification `V2`.
2. Start in the usual order: Kafka + MySQL -> Eureka -> AuthService -> the others -> Gateway last.
3. Good: both start. Key problem: a message naming `jwtprivatekey` / `jwtpublickey` (it never prints the key).

### D. Test by hand, through the gateway (`http://localhost:8082`, e.g. Postman)
| # | Do | Expect | Proves |
|---|---|---|---|
| 1 | `GET /notifications/me` with an OLD token (from before the switch), if you saved one | 401 | old shared-secret tokens are dead |
| 2 | `POST /auth/login` with `{"username":"...","email":"...","password":"..."}` | 200 + a new `JWTtoken` | AuthService signs with the private key |
| 3 | `GET /notifications/me` with `Authorization: Bearer <new token>` | 200 | the gateway checks with the public key |
| 4 | change ONE character in the middle part of the token (between the two dots), repeat #3 | 401 | an edited token is refused |
| 5 | `POST /logout/user` with the token | success | logout still works |
| 6 | repeat #3 with the same token, right away and again after ~30 s | may work for up to ~30 s, then 401 | the gateway's logout cache (`gateway.tokenversion.fresh-ms=30000`) |
| 7 | optional: decode ONLY the first part of the token locally: `echo '<first part>' \| base64 -d` | `"alg":"RS256"`, `"kid":"key-1"` | it really is RSA |

Do NOT paste real tokens into websites such as jwt.io - a token is a live login. A "padding" warning from `base64 -d` is harmless.

**If something goes wrong:** service will not start + message names `jwtprivatekey`/`jwtpublickey` = the variable is missing,
cut off or swapped -> redo B. Login works but EVERY call is 401 = the gateway's public key is not the pair of AuthService's
private key -> make the public key again from the SAME `private.pem`, set it, restart the gateway. Login gives 500 = look at
the AuthService log and send Claude the message only, never the key.

### E. After it works
Tell Claude the result of tests 1-7. Then: the commit message for the whole RSA work (not given yet), and tick step 5 here.

---

# Track C (rest) and Track D — not decided yet

You mentioned these but we never discussed what is in them. **Do not guess.** Ask first.


---

# Change log — every file we changed

This is your review record. Each entry says the file and what changed.

---

## AI service improvement plan (2026-11-01)

**Status: Phases 1, 2 and 3 are DONE (same day, `notes/ai-service-phase-1.md`, `-2.md`, `-3.md`). Phase 4 is not started. Not committed.**
Phase 3 fixed problem 14: `POST /ai/classify` before submit, `incident.created` measures the agreement with no Gemini call, the old classifier is deleted.
Phase 2 fixed problems 6, 7, 8 and the unused fields of 15. Problem 13 (index) was only looked at: not needed yet, measure first. **Flyway `V2` empties
`resolved_incidents_vectors` at the next Ai-Service start** (owner's decision 1).
Problems 1-5 and 9-12 below are fixed; 15 partly (the copied search code and the wrong log line are gone). `avgHistoricalResolutionTime` is removed.
**Decisions (owner, 2026-11-01):** 1 clear the old vectors (do it in phase 2), 2 ADMIN + SUPERVISOR only, 3 team members join when too few, 4 classification
before submit, 5 sending incident text to Gemini is fine for personal use, with no secrets in the text (revisit if other people use it).
**Also found while building:** the old suggestion answer class had `@Builder` and no empty constructor, so Jackson probably could not build it from (read, not run):
Gemini's JSON; Spring AI retries 10 times with waits of up to 3 minutes by default (now 2 attempts).
**Hand test still to do:** `notes/ai-service-phase-1.md` section 6. It is the first real call to Gemini with the new prompt.
The section below is the plan as written before phase 1.

**What exists today.** 2 endpoints (both POST since 2026-10-28, through the gateway at `/ai/**`, limited to 5/min and 100/day
per user) and 1 Kafka listener. Nothing inside the system calls the endpoints; a client does.
```
 Incident-Resolved-Report-Topic ─► RagIntegrationService.storingResolvedIncidentsInVectorDb
        text = title + description + category + service + ROOT CAUSE + RESOLUTION ─► Gemini embedding ─► pgvector table
        (document id = incident id, so a repeat overwrites; metadata: category, service, resolvedById, times, SLA flags)

 POST /ai/rag/suggestion              {title, description, category?, serviceName?}  -> {rootCause, resolutionNotes, anyError}
        filter category+service (similarity >= 0.6, top 4) -> if empty: no filter (>= 0.7) -> if empty: stop, no LLM call
        -> Gemini answers ONLY from those documents

 POST /ai/EngineersSuggestion-ForIncidentAssignment   {title, description, category, serviceName, severity}
        same search -> engineers who resolved those documents -> ask ReportingService for each one's expertise, SLA rate and
        active count -> score = 0.35 similarity + 0.35 expertise + 0.20 SLA + 0.10 free time -> sorted list

 incident.created listener: switched off (body commented out). Classification code (AiProcessingService) is dead code.
```

**Verdict: keep the design, fix the details.** Good already: answers only from retrieved context and refuses without it (no
invented advice); filter with a fallback; the document id makes a repeat harmless; structured output; temperature 0.2; the
stored event comes through the outbox and dead-letter handling; the ranking mixes four real signals.

**Problems found (most important first)**
| # | Problem | Why it matters |
|---|---|---|
| 1 | The engineer recommendation is open to any logged-in user, and it is not limited to the caller's team or to active engineers | An ENGINEER can read every engineer's SLA rate and workload (Reporting hides these on purpose). A supervisor can be shown engineers they are not allowed to assign (403 since issue 56) |
| 2 | Candidates are only the engineers behind the top-4 documents (at most 4, often 1-2) | A free, skilled engineer with no similar case never appears |
| 3 | The "similarity" part of the score cannot reach its weight: count is at most 4, so the factor is at most 0.35 (it needs 99 matches to reach 1.0) | The real weights are not 35/35/20/10; similarity counts about a third of what it should |
| 4 | The suggestion endpoint searches twice (by hand to choose the filter, then again inside the advisor) | 2 embedding calls and 2 searches per request, and the two can disagree |
| 5 | The fallback is "relax the filter" but also raises the threshold from 0.6 to 0.7 | The fallback finds LESS, not more. The thresholds are guesses and have never been measured on real data |
| 6 | What is embedded includes the root cause and resolution, but the search text is only title + description | The match is diluted: a problem is compared with a problem plus its solution |
| 7 | Every RESOLVED event is stored, with no quality check (root cause "asd" is accepted) | Junk in the memory gives junk advice |
| 8 | A REOPENED incident keeps its old solution in the memory; old vectors carry `resolvedBy` = whoever pressed the button (before issue 62) | Stale or wrong advice, and a supervisor can be recommended as an engineer |
| 9 | Reporting down or slow: 2 calls per candidate, one after the other, and any failure = 500 for the whole recommendation (worst case about 40 s) | One failing service breaks the answer even though the search worked |
| 10 | A Gemini error, quota or a malformed answer = a raw 500 | The user sees a crash instead of "AI is busy, try later" |
| 11 | The answer has no sources and no confidence; an empty result is a text inside `anyError` | The user cannot judge or check the advice |
| 12 | `category` / `serviceName` in the request are free text | A typo silently misses the filter |
| 13 | No vector index. With 3072 dimensions the plain `vector` type cannot be indexed (limit 2000) | Search is an exact scan: fine for thousands of rows, slow later |
| 14 | Classification is switched off, its result is read by nobody, and its LLM call sits inside a DB transaction | Dead weight. Needs a decision |
| 15 | The filter/fallback code is copied in 2 services; unused fields (`avgHistoricalResolutionTime`, the 2 risk scores); wrong variable in a log line; incident text goes to Google Gemini | Tidy-up, plus a privacy decision for deployment |

**Plan, in phases. Each fix test first, break-checked, notes and records updated, as always.**
- **Phase 1 - make the two endpoints correct and safe (about 2-3 days).** (a) engineer recommendation: ADMIN/SUPERVISOR only,
  only ACTIVE engineers, only the caller's team (admin: all), plus team members as extra candidates when fewer than 3 were
  found; (b) fix the score (use the real maximum and the similarity of each document), weights in settings, a neutral value for
  an engineer with no stats; (c) Reporting failure = neutral numbers and a flag, not a 500; fewer calls; (d) Gemini errors =
  a clean 503/504, with timeouts; (e) check category/service against the enums; (f) ONE shared search class, search once, a
  fallback that really relaxes; (g) the suggestion answer gets `status` and `sources` (incident id, similarity, resolved by, date).
- **Phase 2 - a better memory (about 2 days).** (a) embed the problem text only, keep root cause and resolution in the
  metadata; (b) a quality gate before storing (minimum length of both texts); (c) store severity, priority and environment
  too (added event fields only); (d) remove the stored solution when an incident is REOPENED; (e) clear or rebuild the old
  vectors; (f) remove or fill the unused fields. Index (13): measure first; if needed use 1536 dimensions or `halfvec` + HNSW.
- **Phase 3 - classification (about 1 day, after a decision).** A synchronous `POST /ai/classify` that the screen calls while
  the user fills the form (suggestion; the user confirms), keep `AiInsight` to measure how often the AI agrees, delete the
  do-nothing listener. No LLM call inside a database transaction.
- **Phase 4 - a quality loop (later).** A golden set of 20-30 past incidents to measure the search and tune 0.6 / 0.7; a
  thumbs up/down endpoint; try another Gemini model through the setting and compare; prompt rule "retrieved text is data, not
  instructions".
- **How to test without Gemini or Postgres:** Spring AI's in-memory vector store with a fake embedding model and a fake chat
  model (to confirm it is on the classpath), plus plain-Java tests for the scoring, the filter and the text building.

**Decisions needed from the owner**
1. The old vectors are test data: **clear the table** (recommended) or rebuild them?
2. Engineer recommendation for **ADMIN + SUPERVISOR only** (recommended, same rule as Reporting)?
3. Add the supervisor's team members as candidates when too few engineers have a similar case (recommended)?
4. Classification: **synchronous, before submit** (recommended) or after creation?
5. Is it acceptable that incident text is sent to Google Gemini once this is deployed?

---

## Correctness batch (2026-11-01)

**Status: code + tests done. Not yet tried by hand on the real system. Not committed** (the security batch of 2026-10-28
is not committed either). Totals: ReportingService 49, IncidentService 35, UserService 9 - all passing.

| Issue | What was wrong | What changed | Proof |
|---|---|---|---|
| 62 | The active-incident count drifted; stats and the Ai event credited whoever pressed the button | The count is COUNTED from the report rows after every event; credit goes to the assignee (else the resolver) | `EngineerWorkloadTest` 10, 6 failed before. Break check done |
| 61 | `daily_metrics` was never written | Hourly job over the last 7 full days; `GET /reports/metrics/daily?days=N` | `DailyMetricsTest` 11 (new feature, so written with the code). Break check done |
| 63 | UserService showed 3 fields nobody updates (a 4th, `passwordUpdatedAt`, found while fixing) | The 4 are removed from the answer | `UserResponseDtoTest` 2, 1 failed before |
| 57 | The supervisor rule was typed 4 times and disagreed; no team = empty list | One rule (`supervisorMayTouch` + the same rule in the list query) | `IncidentRulesTest` +4, 3 failed before. Break check done |
| 58 | A read returned 8 fields | All the details, on list and read-one | `IncidentRulesTest` +2, both failed before |
| 38 | An assign asked UserService twice | Once | `IncidentRulesTest` +2, 1 failed before |

### Decisions taken (say if you want any changed)
- **Who gets the credit:** the assigned engineer. If nobody was assigned, whoever resolved it. `IncidentReport.resolvedBy`
  still records who pressed the button.
- **Issue 63:** remove the fields rather than send them over Kafka: AuthService owns them, and a counter that changes on
  every wrong password is not worth an event.
- **Issue 57:** the wider rule won (what a supervisor may change, they may also read and list).
- **Daily job:** every hour rather than at midnight, because this machine is not always on at midnight.

### Files
| File | Change |
|---|---|
| `ReportingService/.../service/EngineerWorkloadService.java` | counts rows instead of +1 / -1 |
| `ReportingService/.../kafka/consumer/IncidentEventConsumer.java` | refreshes the old and new engineer on assign, the assignee on any status change |
| `ReportingService/.../entity/IncidentReport.java` | new `engineerToCredit()` |
| `ReportingService/.../service/EngineerPerformanceService.java` | stats go to `engineerToCredit()` |
| `ReportingService/.../kafka/producer/IncidentResolvedReportEventProducer.java` | event's `resolvedBy` = `engineerToCredit()` |
| `ReportingService/.../service/DailyMetricsService.java` | NEW |
| `ReportingService/.../schedular/dailyMetrics/DailyMetricsSchedular.java` | NEW |
| `ReportingService/.../dto/DailyMetricsResponseDTO.java` | NEW |
| `ReportingService/.../repository/DailtyMetricsReportRepository.java` | id type `LocalDate`; read by date range |
| `ReportingService/.../repository/IncidentReportRepository.java` | 2 new count queries |
| `ReportingService/.../controller/ReportingController.java` | `GET /reports/metrics/daily`; `getDailyMetrics` renamed `getAllTimeMetrics` |
| `UserService/.../dto/UserResponseDTO.java`, `service/UserService.java` | 4 fields removed |
| `IncidentService/.../service/IncidentService.java` | `toResponse`, `supervisorMayTouch` everywhere, no early empty page, one `getUser` |
| `IncidentService/.../repository/IncidentRepository.java` | list query follows the same rule |
| `IncidentService/.../dto/IncidentResponseDTO.java` | 10 more fields |
| Tests | NEW `EngineerWorkloadTest`, `DailyMetricsTest`, `UserResponseDtoTest`; CHANGED `IncidentRulesTest` (+8) |

### Hand test (owner, real system) - to do
1. Restart ReportingService, IncidentService, UserService. No database change is needed.
2. As admin/supervisor assign an incident to engineer A, then to engineer B. `GET /reports/engineer-workload/{A}` -> one
   less; `{B}` -> one more.
3. Let a supervisor resolve B's incident -> B's count goes down.
4. About a minute after the start, `GET /reports/metrics/daily?days=7` -> 7 or more rows (and `SELECT * FROM daily_metrics`).
5. `GET /incidents/{id}` shows the description, root cause and dates. `GET /users/...` no longer shows `enabled`.

### Known limits (not fixed, written down)
- Old `engineer_performance_stats` rows and old Ai-Service vectors keep the old credit. Not recalculated.
- A workload number that is already wrong stays wrong until that engineer's next event.
- A day older than 7 days is never recalculated.
- Issue 66 (old SLA deadlines) is still open and also affects the old rows' SLA counts.

---

## Issue 68 and the security clean-up batch (2026-10-28)

**Status: code + tests done, every fix test first. Not yet tried by hand on the real system.**
Totals: AuthService 80, APIGateway 76, UserService 7, Ai-Service 4, IncidentService 27, NotificationService 44,
ReportingService 28, common-web 25 - all passing. `common-web` was re-installed (`mvn install`) so the services use the
new `TraceIdFilter`.

| Issue | What was wrong | What changed | Test (failed before the fix) |
|---|---|---|---|
| 68 | A wrong OLD password on `POST /password/update` was never counted | It counts in the SAME lockout as login (5 -> locked 15 min). A locked account cannot change its password. The right old password resets the counter | `ChangePasswordTest` +5. Break check: counting inside the transaction -> rolled back, 3 failed |
| 49 | No time limit on routed calls; the 504 never fired | 10 s for every route, 60 s for the AI route, 2 s to connect | `GatewayRoutesTest` +3 (real values), `GatewayRouteTimeoutTest` 3 (whole gateway + a slow local server). Break check done |
| 11 | `/auth/token-version/{id}` open to anybody | Needs the internal secret + `X-Internal-Service`; the gateway sends both | `AuthEndpointSecurityTest` 6, `TokenVersionServiceHeadersTest` 1 |
| 10 | `@PreAuthorize` on `RoleController` did nothing | `@EnableMethodSecurity` on AuthService | `AuthEndpointSecurityTest` 1 |
| 5 | `/logout/user` accepted GET (and every method) | POST only; dead `LogoutAnyUser` removed | `LogoutControllerTest` 3 |
| 7 | AI endpoints were GET with a body | Both POST | `RagControllerTest` 3 |
| 2 | `/internal/UserSpecificDetails/{userId}` always 500 | `@PathVariable("userId")` | `InternalUserControllerTest` 2 |
| 51 | Services kept any incoming trace id (line breaks, huge values) | Same safety rule as the gateway; a bad id is replaced | `TraceIdFilterTest` 5 |

### Files
| File | Change |
|---|---|
| `AuthService/.../service/ChangePasswordService.java` | issue 68: lock check first, verdict out of the transaction, count after it, counter reset on success |
| `AuthService/.../config/SecurityConfig.java` | issue 10 `@EnableMethodSecurity`; issue 11 rule for `/auth/token-version/**` before `/auth/**` |
| `AuthService/.../controller/LogoutController.java` | issue 5: `@PostMapping`, dead method removed |
| `APIGateway/.../services/TokenVersionService.java` | issue 11: sends `X-Internal-Secret` + `X-Internal-Service: APIGATEWAY` |
| `APIGateway/src/main/resources/application.properties` | issue 49: connect 2000 ms, response 10 s, AI route 60000 ms |
| `Ai-Service/.../controller/RagController.java` | issue 7: both `@PostMapping` |
| `UserService/.../controller/InternalUserController.java` | issue 2: `@PathVariable("userId")` |
| `common-web/.../filter/TraceIdFilter.java` | issue 51: `TraceId.isSafe` |
| Tests | NEW `AuthEndpointSecurityTest`, `LogoutControllerTest`, `GatewayRouteTimeoutTest`, `TokenVersionServiceHeadersTest`, `RagControllerTest`, `InternalUserControllerTest`, `TraceIdFilterTest`; CHANGED `ChangePasswordTest`, `GatewayRoutesTest` |

### What changes for whoever calls the API
- **`POST /logout/user`** (a GET now gets 405).
- **`POST /ai/rag/suggestion`** and **`POST /ai/EngineersSuggestion-ForIncidentAssignment`** (same JSON body as before).
- **Restart AuthService and the gateway together.** A new AuthService refuses the old gateway's token-version call
  (403), and the old gateway would fall back to its cache / skip the check.

### Hand test (owner, real system) - to do
1. Restart AuthService, the gateway, UserService, Ai-Service (and the others, for the new `common-web`).
2. Log in, use the token on any call -> 200 (the gateway's token-version call is accepted). The gateway log must NOT say
   "AuthService returned error status: 403".
3. `GET http://localhost:8082/auth/token-version/<your user id>` from Postman -> **403**.
4. `GET /logout/user` -> 405; `POST /logout/user` -> 200.
5. `POST /password/update` with a wrong old password 5 times -> 401 x5, then login gives `ACCOUNT_LOCKED`.
   (Use a test account: it stays locked for 15 minutes.)
6. Optional: AI endpoints with POST.

---

## BCR-02 - the refresh token moves into an HttpOnly cookie (2026-11-02)

**Status: code + tests done on branch `auth-refresh-cookie`, NOT committed or merged (owner decides). Not yet tried by
hand on the real system.** Asked for by the browser frontend (`IMP Frontend/docs/bcr-02-plan.md` has the full reasoning
and flow charts). This is the "HttpOnly cookie for a browser front end later" from part B below.

### Why
A browser page had to keep the 7-day refresh token where JavaScript can read it: memory is lost on reload,
localStorage and sessionStorage can be read by an injected script, and a duplicated tab copied the token, so the next
refresh looked like reuse and logged the user out of both tabs. An HttpOnly cookie is kept by the browser, sent only to
the refresh endpoint, shared by all tabs, and no script can read or copy it.

**What it does NOT do:** a script injected into the open page can still call refresh itself (the browser adds the
cookie). It can no longer COPY the 7-day token and use it elsewhere later. Plain-text rendering and a strict page
security policy are still needed on the frontend.

### The flow now
```
 POST /auth/login               -> JSON {jwttoken, ...}  (no refreshToken)
                                   Set-Cookie: imp_refresh=<token>; Path=/api/auth/refresh; Max-Age=604800;
                                               Secure; HttpOnly; SameSite=Strict
 POST /auth/refresh (no body)   -> the browser adds the cookie
                                   200: JSON {userid, jwttoken} + Set-Cookie with the NEW token
                                   401 + cookie cleared (Max-Age=0): missing, too long, unknown, used, expired
                                   401 ACCOUNT_LOCKED: cookie KEPT (the token works again when the lock ends)
 POST /logout/user              -> tokens cancelled as before + cookie cleared
 a token in the JSON body       -> ignored (401 INVALID_REFRESH_TOKEN if there is no cookie)
```
The access token's JSON key is **`jwttoken`** (pinned by a test): the Java field is `JWTtoken`, but Jackson names it from
the getter `getJWTtoken()`.

### Files
| File | Change |
|---|---|
| `AuthService/.../controller/RefreshCookie.java` | NEW - the one place that builds and clears the cookie |
| `AuthService/.../controller/RefreshController.java` | reads the cookie (max 200 characters, as the old body), sets the new one, clears it on refusal except a lock |
| `AuthService/.../controller/LoginController.java` | sets the cookie after a successful login |
| `AuthService/.../controller/LogoutController.java` | clears the cookie |
| `AuthService/.../dto/LoginResponseDTO.java`, `RefreshResponseDTO.java` | `@JsonIgnore` on `refreshToken` (Java still sees it) |
| `AuthService/.../dto/RefreshRequestDTO.java` | DELETED - the body is no longer read |
| `AuthService/.../config/SecurityConfig.java` | comment only: why CSRF can stay off with this cookie |
| `AuthService/.../application.properties` | `auth.refresh-cookie.secure=${refreshcookiesecure:true}`, `auth.refresh-cookie.path=${refreshcookiepath:/api/auth/refresh}` |
| `AuthService/src/test/.../controller/RefreshCookieTest.java` | NEW - 12 tests, the whole service over HTTP |
| `AuthService/src/test/.../controller/RefreshCookieSettingsTest.java` | NEW - 2 tests, the settings reach the cookie |
| `AuthService/src/test/.../AuthEndpointSecurityTest.java` | refresh with no cookie: 401 now, was 400 |
| `AuthService/src/test/.../controller/LogoutControllerTest.java` | passes the new `RefreshCookie` |
| `APIGateway/src/test/.../GatewayCookieTest.java` | NEW - 1 test: `Cookie` reaches AuthService, `Set-Cookie` comes back |
| `APIGateway/src/test/.../GatewayLogSecretsTest.java` | NEW - 1 test: no cookie, access token or internal secret reaches the log |
| `APIGateway/.../application.properties` | one line: the header-printing logger set to INFO (the log leak fix) |

Not changed: `RefreshTokenService` and its 13 tests, the database (no migration), all gateway code.

### Tests
Run 2026-11-02 with `./mvnw.cmd test`: **AuthService 94 pass (was 80), APIGateway 78 pass (was 76; +1 `GatewayCookieTest`, +1 `GatewayLogSecretsTest`).**
- **Test first:** the new tests were run against an untouched copy of `master`: 11 of 13 failed (no cookie, token in
  the JSON, 400 instead of 401). The 2 that passed describe what was already true: a failed login sets no cookie, and
  the access token's key is `jwttoken`. `GatewayCookieTest` passed on `master` too: it guards existing gateway behaviour.
- **Break checks** (each protection removed, then put back): no `@JsonIgnore` -> `loginJsonNoLongerCarriesTheRefreshToken`
  fails; no 200-character limit -> `anOversizedCookieIsRefusedBeforeItIsHashed` fails (the value reached the hashing);
  clearing the cookie on a lock too -> `aLockedAccountIsRefusedButKeepsItsCookie` fails; a gateway filter that drops
  `Cookie` -> `GatewayCookieTest` fails.
- **Found while testing:** with `logging.level.org.springframework.cloud.gateway=DEBUG` the gateway's
  `ObservedRequestHttpHeadersFilter` wrote EVERY request header to the log: the refresh cookie, the `Authorization`
  header (access token) and `X-Internal-Secret`. Older than this change (the last two were always logged).
  **Fixed the same day, see "Gateway log leak" below.**

### Gateway log leak - fixed 2026-11-02 (first piece of BCR-04)
- **Test first:** `APIGateway/src/test/.../GatewayLogSecretsTest.java` (NEW). The whole gateway with the REAL logging
  settings, in front of a stand-in AuthService. It sends a cookie, a bearer token and (set by the gateway itself) the
  internal secret, then checks the console. **Before the fix it failed: "the refresh cookie is in the log".**
- **Fix:** one line in `APIGateway/.../application.properties`:
  `logging.level.org.springframework.cloud.gateway.filter.headers.observation.ObservedRequestHttpHeadersFilter=INFO`.
  A more specific logger wins over the DEBUG line above it, so only this class is silenced. Our own filters never
  logged header values (they log "present: true").
- **After:** the test passes, none of the three values appears in the log, and 11 other DEBUG lines still print.
  Gateway 78 pass (was 77). Break check = the before-run: without the line the test fails.
- **Checked:** the gateway is the only place in the project with a DEBUG/TRACE log level.
- **Still open in BCR-04:** a `prod` profile with INFO logs, and a login check on `/actuator`. Old logs that were written
  before this fix may still hold tokens and the internal secret: the access tokens have expired (15 min), but if a gateway
  log file exists, delete it, and consider changing `internalsecret` if the log was ever shared.

### Hand test (owner, real system) - to do
Restart AuthService and the gateway. With `curl` straight to the gateway (`http://localhost:8082`); `-i` shows the
headers. curl does not apply the cookie's path here, so the cookie is passed by hand with `-H "Cookie: ..."`.
1. `curl -i -X POST http://localhost:8082/auth/login -H "Content-Type: application/json" -d "{\"username\":\"x\",\"email\":\"<you>\",\"password\":\"<yours>\"}"`
   -> 200, JSON has `jwttoken` and NO `refreshToken`; a `Set-Cookie: imp_refresh=...; ... HttpOnly; SameSite=Strict` header.
   Copy the value between `imp_refresh=` and the first `;` (call it R1).
2. `curl -i -X POST http://localhost:8082/auth/refresh -H "Cookie: imp_refresh=R1"` -> 200, a new `jwttoken`, a new
   `Set-Cookie` (R2).
3. the new `jwttoken` on `GET /notifications/me` (`-H "Authorization: Bearer <it>"`) -> 200
4. refresh with R1 again -> 401 `REFRESH_TOKEN_REUSED`, and `Set-Cookie: imp_refresh=; Max-Age=0`
5. refresh with R2 -> 401 `INVALID_REFRESH_TOKEN` (step 4 cancelled the login)
6. `curl -i -X POST http://localhost:8082/auth/refresh -H "Content-Type: application/json" -d "{\"refreshToken\":\"R2\"}"`
   -> 401 `INVALID_REFRESH_TOKEN` (the body is ignored)
7. log in again, `POST /logout/user` with its `jwttoken` -> 200 and `Set-Cookie: imp_refresh=; Max-Age=0`; refresh with
   that login's cookie -> 401
8. In the browser: the frontend with `VITE_API_MOCKING=false`, sign in, then DevTools > Application > Cookies shows
   `imp_refresh` marked HttpOnly; reload the page -> still signed in; Local/Session storage hold no token.
9. Check the gateway log for the cookie value, the access token and `X-Internal-Secret`: none should appear (the log
   leak fix; the gateway still runs at DEBUG for everything else).

### Known limits (not fixed, written down)
- An answer lost after the server used the token up (network drop, F5 at that moment) leaves the browser with the used
  cookie: the next refresh counts as reuse, one extra login. Safe and rare; a grace period would weaken reuse detection.
- Behind nginx every visitor shares the refresh rate limit until BCR-01 (real client IP) is done.
- `Secure` needs HTTPS in production. Chrome and Firefox accept it on `http://localhost`; for a browser that does not,
  set `refreshcookiesecure=false` on the laptop only.
- `SameSite` treats sibling subdomains as the same site: host the app where every subdomain is yours.

---

## Auth v2 part B - refresh tokens (2026-10-27)

**Status: code + tests done. Not yet tried by hand on the real system.** Decisions taken with the suggested values:
access token 15 minutes, refresh token 7 days, sent in the JSON body (an `HttpOnly` cookie can come with a browser
front end later).

### The flow
```
 POST /auth/login            -> JWTtoken (15 min) + refreshToken (7 days)      [new family, hash saved]
 any call with JWTtoken      -> works while the 15 min last
 ... 401 (token expired)
 POST /auth/refresh {refreshToken}  -> NEW JWTtoken + NEW refreshToken         [old one marked used]
 POST /auth/refresh with an OLD (used) one -> 401 REFRESH_TOKEN_REUSED, whole family cancelled
 logout / password change / role change / credentials change -> all the user's refresh tokens cancelled
```
The client must do: "401 -> call /auth/refresh -> retry the call with the new JWTtoken; refresh refused -> log in".

### Rules (each one has a test in `RefreshTokenTest`)
| Rule | Why |
|---|---|
| Only the SHA-256 hash is stored | Reading the table gives nobody a usable token |
| Each refresh token works ONCE (rotation) | A copy is noticed the moment both copies are used |
| A used token sent again cancels the whole family (that login only, not other devices) | We cannot tell the real user from the copier, so both must log in again |
| The 401 for reuse is thrown AFTER the transaction commits | A throw inside would roll back the cancel (the issue 55 trap). Break check: thrown inside -> 2 tests fail |
| Row lock (`SELECT ... FOR UPDATE`) on the token | Two refreshes at once: one wins, the other counts as reuse. Break check: no lock -> both won (`[null, null]`) |
| Logout cancels all refresh tokens, in the logout's own transaction | `LogoutUserService` is also used by password, role and credentials changes, so they all cancel too |
| A locked account cannot refresh; its token is NOT used up | Works again when the lock ends |
| Expired (7 days) / unknown / cancelled -> 401 | Unknown and cancelled get the same code (`INVALID_REFRESH_TOKEN`), so nothing is learned about which tokens exist |

### Files
| File | Change |
|---|---|
| `AuthService/.../db/migration/V6__refresh_token.sql` | NEW - table `refresh_token` |
| `AuthService/.../entity/RefreshToken.java` | NEW |
| `AuthService/.../repository/RefreshTokenRepository.java` | NEW - locked read, cancel all of a user, cancel a family |
| `AuthService/.../service/RefreshTokenService.java` | NEW - make, swap, all the rules above |
| `AuthService/.../service/AccessTokenIssuer.java` | NEW - the JWT claims, moved out of `LoginUserService` so login and refresh share them |
| `AuthService/.../controller/RefreshController.java` | NEW - `POST /auth/refresh` |
| `AuthService/.../dto/RefreshRequestDTO.java`, `RefreshResponseDTO.java` | NEW |
| `AuthService/.../dto/LoginResponseDTO.java` | + `refreshToken` |
| `AuthService/.../service/LoginUserService.java` | uses `AccessTokenIssuer`, also returns a refresh token (no `SecurityConfig` any more) |
| `AuthService/.../service/LogoutUserService.java` | both logout methods cancel the user's refresh tokens |
| `AuthService/.../application.properties` | `jwt.expirationMs` 7200000 -> **900000** (15 min); new `auth.refresh-token.lifetime-days=7` |
| `AuthService/src/test/.../RefreshTokenTest.java` | NEW - 13 tests |
| `AuthService/src/test/.../AccountLockoutTest.java`, `AuthServiceOutboxTest.java` | `@Import` gets the 2 new services |
| `APIGateway/.../filter/RateLimitFilter.java` | new rule `refresh`: `POST /auth/refresh` per IP (before `default`) |
| `APIGateway/.../application.properties` | `gateway.ratelimit.refresh.per-minute=30`, `per-hour=300` |
| `APIGateway/src/test/.../RateLimitFilterTest.java` | + 2 tests |

Nothing else changed: the gateway already skips the token check for `/auth/**` and already routes `/auth/**` to
AuthService, so `/auth/refresh` works through it without a new route.

### Hand test (owner, real system) - to do
Restart AuthService (Flyway `V6` makes the table) and the gateway. Then, through `http://localhost:8082`:
1. `POST /auth/login` -> 200, the answer now also has `refreshToken`
2. `POST /auth/refresh` with `{"refreshToken": "<it>"}` -> 200, a new `JWTtoken` + a new `refreshToken`
3. the new `JWTtoken` on `GET /notifications/me` -> 200
4. `POST /auth/refresh` with the FIRST (now used) refresh token -> 401 `REFRESH_TOKEN_REUSED`
5. `POST /auth/refresh` with the token from step 2 -> 401 `INVALID_REFRESH_TOKEN` (step 4 cancelled the login)
6. log in again, `POST /logout/user`, then refresh with that login's token -> 401 `INVALID_REFRESH_TOKEN`
7. optional: wait 15 minutes, the old `JWTtoken` -> 401; refresh -> works

### Known limits (not fixed, written down)
- Old rows are never deleted. Fine for now; later a small scheduled clean-up of rows past `expires_at`.
- A client that sends the same refresh token twice by accident (double click, retry after a lost answer) is
  treated as reuse and must log in again. That is the strict, safe choice.
- Tokens issued before this change have no refresh token: those users simply log in again.

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
| `FLYWAY-NOTES.md` | Your notes, reviewed and extended. Later moved to `notes/` (2026-09-02) |
| `ERROR_HANDLING_FLOW.md` | **New.** How errors work, with one example followed all the way through. Later moved to `notes/` (2026-09-02) |
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

---

## 2026-09-19 to 2026-09-22 — B4 done (missing input checks)

### `common-web` — 2 files

| File | What changed |
|---|---|
| `handler/GlobalExceptionHandler.java` | New handler for `HandlerMethodValidationException` (bad `@Min`/`@Max`/`@Size` on a path or query value). Catch-all now also recognises Spring's own 4xx exceptions (bad URL, wrong method, wrong content type) instead of hiding them as 500 |
| `handler/GlobalExceptionHandlerTest.java` | +6 tests. 18/18 pass, up from 13/18 |

### IncidentService — 3 files

| File | What changed |
|---|---|
| `dto/CreateIncidentRequestDTO.java` | `@NotNull` on severity, priority, environment, serviceName, category; `@Size(max=255)` on affectedSource, location |
| `dto/UpdateIncidentMetadate_RequestDTO.java` | Same `@Size(max=255)` limits |
| `db/migration/V2__widen_root_cause_and_resolution_notes.sql` | **New.** Widens `root_cause`/`resolution_notes` from 500 to 2000 characters, matching the DTO. **Not yet run** |

### AuthService — 4 files

| File | What changed |
|---|---|
| `dto/SignupFormDTO.java` | username/email/password length caps |
| `dto/LoginFromDTO.java` | email/password length caps |
| `dto/ChangePasswordDTO.java` | Both password fields capped at 72 (BCrypt reads no more anyway) |
| `dto/UpdateCredentialsRequestDTO.java` | newEmail/newUsername/password caps; password `@NotNull` → `@NotBlank` |

### Ai-Service — 3 files

| File | What changed |
|---|---|
| `dto/IncidentSuggestionNeededDTO.java` | title/description required + capped; category/serviceName optional + capped |
| `dto/EngineerRecommendationRequestDTO.java` | Same, plus severity capped (not read by the service) |
| `controller/RagController.java` | `@Valid` added to both endpoints — without it, the rules above never ran |

### Path/query limits — 5 files

| File | What changed |
|---|---|
| `ReportingService/controller/ReportingController.java` | `days` gets `@Min(1) @Max(365)` |
| `UserService/controller/UserController.java` | search `query` gets `@Size(max=100)` |
| `UserService/controller/InternalUserController.java` | batch id list gets `@Size(max=50)`, each id gets `@NotNull` |
| `AuthService/controller/TokenVersionController.java` | `userid` path variable: `String` → `UUID` |
| `application.properties` × 3 (Incident, Reporting, User) | `spring.data.web.pageable.max-page-size=100` |

### APIGateway — 4 files

| File | What changed |
|---|---|
| `filter/RequestSizeFilter.java` | **New.** Refuses request bodies over 256KB, and chunked bodies with no declared size, before the JWT check runs |
| `utils/GatewayErrorWriter.java` | **New.** The JSON-error-writing code, moved out of `JwtGatewayFilter` so both filters share one copy |
| `filter/JwtGatewayFilter.java` | Now calls the shared helper instead of building its own JSON |
| `application.properties` | `gateway.request.max-body-size=256KB` |

**Verification, not just "it compiled":** the full 12-module build passed after every step. Step 1
was proven with 6 new permanent tests. Step 3's crash was proven by running the real compiled
class in `jshell`, not by reading the code. Step 4 was checked against the real Hibernate
Validator, also via `jshell`. Step 5 was checked with throwaway MockMvc tests (removed once they
passed — they were not meant to stay). Step 6 got 9 new permanent tests plus a real running
gateway sent real oversized/chunked/normal requests over `curl`.

---

## 2026-09-23 — B5 steps 1-2 (rate limiting on /ai/**, login, signup)

### APIGateway — 5 files

| File | What changed |
|---|---|
| `services/KeyedRateLimiter.java` | **New.** Reusable "one token bucket per key" helper — Bucket4j does the token-bucket maths, Caffeine holds one bucket per caller (bounded, same idea as the B3 token cache) |
| `filter/RateLimitFilter.java` | **New.** Runs right after `JwtGatewayFilter`. Holds a small list of `Rule`s (`ai` by user id, `login`/`signup` by IP), each with its own limiter and counter. Over the limit → 429 + `Retry-After`, via the shared `GatewayErrorWriter` from B4 |
| `application.properties` | 6 new settings: `gateway.ratelimit.ai.{per-minute,per-day}`, `.login.{per-minute,per-hour}`, `.signup.{per-hour,per-day}`, `.max-tracked-keys` |
| `pom.xml` | Added Bucket4j (`bucket4j_jdk17-core`) — not managed by any Spring BOM, version pinned by hand |
| `src/test/.../RateLimitFilterTest.java` | **New.** 19 tests (fake clock, no real waiting): both steps' limits, separate buckets per rule, the 3 login/signup tricks (faked `X-Forwarded-For`, trailing slash, faked `X-User-Id`) all confirmed blocked, per-rule metrics |

**Verification:** 19/19 tests pass. Full 12-module build passes. Real gateway started on a spare
port with throwaway secrets: hand-signed test JWTs proved the `/ai/**` limit (5 pass, then 429,
`Retry-After: 10`, a second user unaffected, one coin back after really waiting 13s); real `curl`
POSTs proved the login/signup limits (6 signups → 6th blocked; 11 logins **each for a different
account** → 11th blocked, the actual credential-stuffing attack; faked header and trailing slash
both still blocked); `/actuator/metrics/gateway.ratelimit.rejected` showed the right count per
rule.

**New note:** `notes/rate-limiting-on-ai-endpoints.md` — the token-bucket idea explained with
coins, why IP limiting catches credential stuffing when account lockout can't, why one office can
share one public IP (NAT), and why a future load balancer would need special handling.

**Not done yet:** step 3 (a default limit on everything else) and step 4 (updating CLAUDE.md and
the LLD files) are still open — see the B5 section above.

---

## 2026-09-24 to 2026-09-28 — B5 finished (steps 3-4, plus a bug found and fixed)

### APIGateway — 4 more files

| File | What changed |
|---|---|
| `filter/RateLimitFilter.java` | Added the 4th rule, `default` — covers every remaining path, per user, 20/sec + 300/min. Must stay LAST in the rule list, or it would swallow requests before the tighter rules ever ran |
| `application.properties` | 2 more settings: `gateway.ratelimit.default.{per-second,per-minute}` |
| `src/test/.../RateLimitFilterTest.java` | +5 tests for the default rule (24 total). 2 old tests that said "this path is not limited" were rewritten — that stopped being true once `default` covers everything |

**Verification:** 24/24 tests pass. Real gateway sent 30 requests at the exact same instant —
about 21 passed, 9 got 429, close to the 20-a-second limit. `/actuator/health` and a second user
were both confirmed unaffected.

### A bug found while testing step 3, fixed as its own change — 3 new files, 1 changed

| File | What changed |
|---|---|
| `filter/JwtGatewayFilter.java` | The "if anything fails" catch used to wrap BOTH the AuthService check and the act of forwarding the request. So a routing failure (a service down) was wrongly reported as `AUTH_SERVICE_UNAVAILABLE`. Now `.materialize()` catches only the token-version lookup's own errors; forwarding errors are no longer caught here at all |
| `handler/GatewayExceptionHandler.java` | **New.** The gateway's own last-resort error handler (WebFlux's equivalent of `common-web`'s `GlobalExceptionHandler`, which only works on Spring MVC). Gives forwarding failures an honest status: 503 service down, 504 too slow, 404 no route, 500 our own bug — always in the project's normal JSON shape, with the real details kept to the log only |
| `src/test/.../JwtGatewayFilterTest.java` | **New**, 6 tests. Written **before** the fix — confirmed to fail against the old code, then passed once the fix was made |
| `src/test/.../GatewayExceptionHandlerTest.java` | **New**, 7 tests |

**Verification:** the exact real-world situation that exposed the bug (a valid token, IncidentService
not running) was reproduced on the real gateway before and after. Before: 503
`AUTH_SERVICE_UNAVAILABLE`, log blamed AuthService. After: 503 `SERVICE_UNAVAILABLE`, log correctly
names `IncidentService`. 35 wrongly-blamed log lines in one run → 0.

### Documentation

`notes/rate-limiting-on-ai-endpoints.md` rewritten with a "quick revision page" at the top (the
whole gateway flow in pictures, the 4 questions the rate limiter asks, 20 worked examples, 3
worked-through stories with numbers) plus step 3 folded into the detail below it.

`notes/jwt-gateway-error-handling.md` — **new**. The 3 different jobs inside `JwtGatewayFilter`
(read the token / ask AuthService / forward the request), which of them the `try/catch` actually
covers, why `lookupFailed` sometimes answers 401 and sometimes 503, and what
`GatewayExceptionHandler` does with everything else.

`CLAUDE.md` — §4 (auth flow) and §6 (inter-service calls) updated with the rate limiter and the
error-handling split; §9 gets 2 new resolved issues (the blame bug, and the still-open 504 gap);
§10 gets 2 new working agreements.

`notes/LLD/LLD-APIGateway.md` and `LLD-overview.md` updated: the rate limiter is added to the
filter-chain diagram and the resilience table; the error-responses section is rewritten to explain
the split between the filter's own errors and `GatewayExceptionHandler`'s.

---

## 2026-09-30 — C1 done (logging and trace ids)

Full list is in the C1 section above ("Files changed in C1, all together"). Short version:

| Area | What changed |
|---|---|
| New module `common-trace` | `TraceId` (shared name + safety rule + id maker) and `TraceIdLoggingDefaults` (fills Spring Boot's log-pattern slot). Root `pom.xml` now lists 13 modules |
| `common-kafka` | Producer + consumer interceptors carry the trace id inside Kafka messages. Auto-config wires them in. **Bug fix:** `KafkaErrorHandlingAutoConfig` now runs after `KafkaAutoConfiguration`, so its dead-letter templates no longer hide Boot's default `kafkaTemplate` |
| `common-web` | `TraceIdFilter` uses `TraceId`; 13 hand-written `[id]` prefixes removed from `GlobalExceptionHandler` |
| APIGateway | `TraceIdGatewayFilter` (order -3, new). All gateway filters + `GatewayErrorWriter` read the id from it. 7 new tests (53 total) |
| 5 services | 38 `println` / `printStackTrace` calls became `log.debug`; only user ids are logged |
| AuthService, IncidentService | `pom.xml` gets `common-kafka` |

**Verification:** 108 tests pass (common-trace 8, common-web 19, common-kafka 28, gateway 53); a real
embedded Kafka broker test proves one id survives send, receive, forward and dead-letter; full
13-module build passes.

**Committed by the user as `b2c7c10`.** Not yet run against the real system (Kafka + all services).
