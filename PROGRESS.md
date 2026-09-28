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

Last updated: 2026-09-22

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
| Track C | Not decided yet | ❓ Need to discuss |
| Track D | Not decided yet | ❓ Need to discuss |

**Track A is finished. Track B is almost finished — only B5 is left.**

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

# Track C and D — not decided yet

You mentioned these but we never discussed what is in them. **Do not guess.** Ask first.

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
