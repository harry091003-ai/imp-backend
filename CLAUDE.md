# CLAUDE.md — Incident Management Platform

Spring Boot **microservices** platform for IT/operational incident management: users register,
raise incidents, supervisors assign them to engineers, engineers resolve them, a reporting
service tracks SLAs, a notification service logs (should email) stakeholders, and an AI service
does classification + RAG-based resolution suggestions.

Root: `E:\dev-spring-boot\IMP` — **not a git repository**, no root `.gitignore`, no README.

---

## 1. Modules

| Module | Port | Role | Runnable |
|---|---|---|---|
| `Eureka` | 8081 | Netflix Eureka service registry | yes |
| `APIGateway` | 8082 | Spring Cloud Gateway (WebFlux), JWT validation, header injection | yes |
| `AuthService` | 8083 | signup/login/logout, password, roles, JWT minting, token-version | yes |
| `UserService` | 8084 | user profiles, engineer/supervisor hierarchy, search | yes |
| `IncidentService` | 8085 | incident CRUD, assignment, status transitions | yes |
| `NotificationService` | 8086 | consumes incident events, writes `NotificationLog` | yes |
| `ReportingService` | 8087 | incident reports, metrics, audit log, SLA schedulers | yes |
| `Ai-Service` | 8088 | Gemini classification + pgvector RAG, engineer recommendation | yes |
| `shared-events` | — | plain JAR: Kafka event DTOs (`com.project.events`) | no |
| `common-security` | — | plain JAR: `HeaderAuthFilter` + auto-config | no |
| `common-web` | — | plain JAR: exception family, `ErrorResponse`, global `@RestControllerAdvice`, `TraceIdFilter` + auto-config | no |
| `common-kafka` | — | plain JAR: retry-then-dead-letter error handler + 2 DLT producers, auto-config | no |

Root `pom.xml` (`com.example:incident-management-platform:1.0-SNAPSHOT`, `packaging=pom`)
lists **all 12 modules** as of 2026-09-01 (Track A4), so `mvn clean install` at the root builds
everything in one command. It previously listed only 6, and the other 6 built individually - see
issue 24.

**Still true:** no service declares the root pom as its `<parent>`; every service parents directly
to `spring-boot-starter-parent`, so the root pom's `<properties>` are decorative and inert.

---

## 2. Tech stack

- **Java 21** everywhere **except `Ai-Service`, which is Java 17** (`<java.version>17</java.version>`).
- **Spring Boot 3.5.11** everywhere **except `common-security`, which is 3.3.2**.
- `common-web` targets **Java 17 bytecode on purpose** (so Ai-Service, still on 17, can use it).
- **Spring Cloud 2025.0.1** (Eureka client/server, Gateway, LoadBalancer, OpenFeign).
- **Maven**, per-module `mvnw`/`mvnw.cmd` wrappers. No root wrapper.
- **MySQL 8** (`com.mysql:mysql-connector-j`) — one schema per service:
  `inicident-management-{auth,user,incident,notification,reporting}servicedb` (the typo
  `inicident` is in the real DB names — do not "fix" without a migration).
- **PostgreSQL + pgvector** for `Ai-Service` only: `localhost:5433/vector_db`,
  table `resolved_incidents_vectors`, 3072 dims, cosine distance, `initializeSchema(false)`
  (schema created by hand).
- **Kafka** (Confluent 7.4.0 + Zookeeper) via `kafka-docker/docker-compose.yml`, `localhost:9092`.
- **JJWT 0.13.0** (`jjwt-api/impl/jackson`), HMAC-SHA, base64 secret.
- **Spring AI 1.1.0** (`spring-ai-starter-model-google-genai`, embedding, `pgvector-store`, `rag`,
  `pdf-document-reader`). Chat model `gemini-2.5-flash-lite`, embeddings `gemini-embedding-001`.
  Prompt templates live in `Ai-Service/src/main/resources/prompts/**/*.st`.
- **ShedLock 5.10.0** (JDBC) for `ReportingService` schedulers.
- **Lombok** everywhere (`@Getter/@Setter/@Builder/@RequiredArgsConstructor`).

---

## 3. Domain model

**AuthService** (`users`, `roles`, `user_roles`) — credentials owner.
`User`: `userid` (UUID, `GenerationType.UUID`), username, email, **password (BCrypt)**,
`createdAt/updatedAt/lastLoginAt/lastLogoutAt/passwordUpdatedAt`, `failedLoginAttempts`,
`accountLocked`, `enabled`, **`TokenVersion`** (drives JWT revocation), `Set<Role>` (`@ManyToMany`).

**UserService** (`users`, `roles`, `user_roles`) — profile mirror, populated from Kafka.
Same `User` shape **minus password**, plus `status` (`ACTIVE/INACTIVE/SUSPENDED`) and a
self-referencing hierarchy: `supervisor` (`@ManyToOne`) / `engineers` (`@OneToMany(mappedBy)`).
`@Id` is **not generated** — the UUID arrives on the `user.registered` event.

`RoleName`: `ROLE_ADMIN`, `ROLE_ENGINEER`, `ROLE_SUPERVISOR` (enum duplicated in both services).

**IncidentService** (`incidents`) — `Incident`: id (UUID), title, description(2000),
`Severity`, `IncidentStatus` (OPEN/IN_PROGRESS/ASSIGNED/RESOLVED/CLOSED/REOPENED), `Priority`,
`createdBy`/`assignedTo`/`assignedBy` (UUIDs, no FK), `ServiceName`, `IncidentCategory`,
`Environment`, `affectedSource`, `location`, `rootCause`, `resolutionNotes`, timestamps.

**ReportingService** — read-model built from events: `IncidentReport` (heavily indexed, with
SLA deadline / breach / event-published flags), `IncidentAuditLog`, `DailyMetrics`,
`EngineerPerformanceStats`, `EngineerWorkload`, `ProcessedEvent` (idempotency keys).

**NotificationService** — `NotificationLog`, unique constraint on `(eventId, recipientEmail)`.

**Ai-Service** — `AiInsight` (per-incident classification + confidence + conflict flag).

---

## 4. Auth & security architecture

1. `POST /auth/login` → AuthService mints a JWT with claims `roles`, `tokenVersion`, `email`;
   subject = **userid (UUID string)**.
2. Every request hits **APIGateway** → `JwtGatewayFilter` (`GlobalFilter`, order `-1`):
   skips paths starting with `/auth`, validates signature + expiry, then calls
   `AUTHSERVICE /auth/token-version/{userid}` (reactive `WebClient`, `@LoadBalanced`) and
   rejects on version mismatch. This is the logout / password-change revocation mechanism.
3. On success the gateway **injects headers** `X-User-Id`, `X-Roles`, `X-User-Email`.
4. **As of 2026-09-02 (Track B1) the gateway also sends `X-Internal-Secret`, and wipes any
   `X-User-*` headers the caller sent before writing its own.** Services check that secret
   (constant-time compare) before trusting any identity header; a wrong or missing secret means
   no authentication is set, so `SecurityConfig` rejects protected paths. Services also now bind
   to `127.0.0.1` only. Value comes from the `internalsecret` env var - no default, so a missing
   value stops startup.
5. Downstream services trust those headers via `common-security`'s **`HeaderAuthFilter`**
   (`OncePerRequestFilter`, auto-registered through
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), which
   builds a `UsernamePasswordAuthenticationToken(userId, null, authorities)`.
6. So in every downstream controller/service: **`auth.getName()` is the user's UUID string**,
   and authorities are the raw `ROLE_*` strings.

Every downstream `SecurityConfig` is a copy-paste of the same shape: csrf disabled,
`SessionCreationPolicy.STATELESS`, `addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter)`,
`@EnableMethodSecurity` + `@EnableWebSecurity`.

Per-service permitAll rules:
- AuthService: `/auth/**`, `/debug/**`, `/actuator/**`; `/roles/**` → `hasAuthority("ROLE_ADMIN")`. **No `@EnableMethodSecurity`.**
- UserService: `/actuator/**` only; `/internal/**` → `hasRole("INTERNAL_SERVICE")`;
  `/users/**` → `hasAnyRole(ENGINEER, SUPERVISOR, ADMIN)`.
- ReportingService: `/actuator/**` only; `/internal/**` → `hasRole("INTERNAL_SERVICE")`;
  `/reports/**` → `hasAnyRole(ADMIN, SUPERVISOR)` (engineers deliberately excluded — these
  endpoints expose every engineer's performance numbers).
- IncidentService / NotificationService / Ai-Service: `/actuator/**` only.

---

## 5. Kafka event flow

Events live in `shared-events` (`com.project.events`), JSON-serialized, key = entity UUID.
Consumers use `spring.json.trusted.packages=*`.

| Topic | Producer | Consumers |
|---|---|---|
| `user.registered` | AuthService | UserService |
| `user.Updated` | AuthService | UserService |
| `user.logged-in` | AuthService | UserService |
| `user.logged-out` | AuthService | UserService |
| `roles.update` | AuthService | UserService |
| `incident.created` | IncidentService | Reporting, Notification, Ai (**Ai handler commented out**) |
| `incident.assigned` | IncidentService | Reporting, Notification |
| `incident.status.updated` | IncidentService | Reporting, Notification |
| `Incident-Resolved-Report-Topic` | ReportingService | Ai-Service (stores vectors) |
| `ai.classification.result` | Ai-Service | **none** |
| `Sla-Assignment-Breach-Topic` | ReportingService | **none** |
| `Sla-Resolution-Breach-Topic` | ReportingService | **none** |
| `Sla-Assignment-BreachingRisk-Topic` | ReportingService | **none** |
| `Sla-Resolution-BreachingRisk-Topic` | ReportingService | **none** |
| `incident.resolved` | declared, publisher commented out | none |

Consumer groups: `user-service-group`, `notification-service-group`, `Reporting-service-group`,
`Ai-service-group`.

**Idempotency**: ReportingService `IdempotencyService.isDuplicate(eventId)` inserts into
`ProcessedEvent` and lets the unique-constraint violation bubble into the consumer's catch block
(deliberate — see the long comment in that file). NotificationService relies on the
`(eventId, recipientEmail)` unique constraint instead.

---

## 6. Synchronous inter-service calls

**As of 2026-09-09 (Track B3 step 2) the rule is: the HTTP client follows the app's execution
model.** APIGateway is reactive (WebFlux/Netty) so it uses `WebClient`. Every other service is
blocking (Spring MVC/Tomcat) so it uses **OpenFeign**. Do not mix them — `WebClient` + `.block()`
in a blocking service was exactly the bug B3 removed.

- Gateway → AuthService: `WebClient` `http://AUTHSERVICE/auth/token-version/{userid}`.
  Called on **every request**. Timeouts and a bounded connection pool live in
  `config/WebClientConfig` + `gateway.httpclient.*` in `application.properties`.
- IncidentService → UserService (`client/UserClient`, **`@FeignClient(name = "USERSERVICE")`**):
  `/internal/validation/{userId}`, `/internal/EngineersUnderSupervisor/{SupervisorId}`,
  `/internal/UserSpecificDetails/{userId}` (this last one is **unused** and its endpoint is
  broken — see issue 2).
- NotificationService → UserService (`client/UserClient`, also a `@FeignClient`):
  `/internal/UserSpecificDetails/{userId}` (**unused**, broken endpoint),
  `POST /internal/UserSpecificDetails/batch` (the one actually used, from a Kafka listener).
- Ai-Service → ReportingService: **OpenFeign** `ReportingServiceFeignClient`
  (`/internal/reports/engineer-performance/{engineerId}/{category}/{service}`,
  `/internal/reports/engineer-workload/{engineerId}`).

**Headers.** Every internal call carries `X-Internal-Secret` + `X-Internal-Service` (Track B1
step 3). All three Feign services attach them with a `RequestInterceptor` bean in
`config/FeignInternalAuthConfig`. A new Feign client in those services inherits them
automatically — a plain `@Bean` interceptor applies to every client in the service.

**Timeouts (Track B3 step 2).** All Feign services set
`spring.cloud.openfeign.client.config.default.{connect,read}-timeout` = 2000/5000 ms. Note Feign
ships with 10s/60s defaults (unlike WebClient, which has **none**) — so Ai-Service was never
fully exposed, but 60s was still far too long. Spring Cloud's default retryer is `NEVER_RETRY`
and is deliberately left alone: retries stack across layers (3 × 3 = 9 real calls), so retry
belongs at exactly one layer, chosen in B3 step 4.

**Error mapping.** IncidentService has `config/FeignErrorDecoderConfig` mapping 404 →
`NotFoundException` so "no such user" stays a 404 instead of becoming a 500. NotificationService
deliberately has none — its old code had no 404 rule to preserve, and Feign's default decoder
(status + method + URL + body) beats what was there. **Caveat:** a *timeout* never reaches an
`ErrorDecoder` (there is no HTTP reply to decode), so it currently surfaces as a 500 rather than
a 503. Mapping that properly is B3 step 4.

Service ids used in `lb://` / URIs are inconsistently cased vs `spring.application.name`
(e.g. `lb://IncidentService` vs `INCIDENTSERVICE`) — verify before touching discovery.
`@FeignClient(name = ...)` must match `spring.application.name` exactly.

---

## 7. Conventions actually in use

**Package layout** (per service): `config`, `controller`, `dto`, `entity`, `repository`,
`service`, `kafka/{producer,consumer}`, plus `client`/`feignClients`, `util`/`utils`, `security`.
Deviations: `Ai-Service` uses `Kafka/` (capital K); `NotificationService` uses `Entity/` (capital E).

**Naming** — inconsistent by drift; match the file you are editing:
- Methods are frequently **PascalCase** (`CreateIncident`, `LoginUser`, `UpdatingUserRoles`,
  `GetOwnDetails`) alongside normal camelCase ones.
- Fields occasionally PascalCase (`User.TokenVersion`, event fields `OldStatus`, `UpdatedAt`).
- DTOs are all `*DTO`; some use snake-ish names (`UpdateIncidentMetadate_RequestDTO`,
  `UpdateIncidentStatus_ResponseDTO`) — note the "Metadate" typo.
- Kafka topics mix `dot.lower.case` (`incident.created`) with `Pascal-Hyphen-Topic`
  (`Sla-Assignment-Breach-Topic`) and `user.Updated`.

**DTO vs entity**: entities never leave a service; every endpoint returns a `*ResponseDTO`.
Mapping is inline `Builder` chaining in the service layer — **no MapStruct, no mapper classes**.

**Validation**: `jakarta.validation` annotations on DTOs, enforced by `@Valid` on every
request body in AuthService, IncidentService and UserService (added 2026-08-28 — previously
`@Valid` existed only in AuthService, so IncidentService's constraints never fired).
ReportingService and Ai-Service have no request bodies to validate.

**Exception handling**: **as of 2026-08-28 this is now centralized** — see the `common-web`
module. Services throw `NotFoundException` / `ValidationException` / `ConflictException` /
`ForbiddenException` / `UnauthorizedException` (all extend `ApiException`, each carries an
`errorCode` and a fixed HTTP status). One shared `@RestControllerAdvice`
(`com.project.web.handler.GlobalExceptionHandler`) turns them into a single JSON shape,
auto-configured into every MVC service via `common-web`'s
`AutoConfiguration.imports`. APIGateway is WebFlux so it hand-matches the same shape in
`JwtGatewayFilter.onError`. **Do not add per-service advice classes** — extend the shared one.
Remaining bare `RuntimeException`s are deliberate 500s (missing seed data) and are commented
as such.

**Response format**: successful responses have **no envelope** (deliberate decision) —
controllers return `ResponseEntity<SomeDTO>` or a bare DTO. **Errors are standardized**:
every failure returns `{timestamp, status, error, message, path, traceId}` plus an optional
`fieldErrors` array on `@Valid` failures. `LoginResponseDTO`/`SignupResponseDTO` still carry
legacy `status`/`message` fields on the SUCCESS path only.

**Logging**: three styles coexist —
SLF4J (`LoggerFactory.getLogger`, no `@Slf4j`) in Incident/Reporting/Notification/Ai,
`java.util.logging.Logger` in `HeaderAuthFilter`, `JwtGatewayFilter`, `TokenVersionService`,
and **~40 `System.out.println` calls** (24 in AuthService, 12 in UserService), several of which
print user ids, roles and token versions.

**Transactions**: `jakarta.transaction.Transactional` (not the Spring one) applied ad hoc —
present on some services/consumers/producers, absent from most read paths and from
`ChangePasswordService`.

**Comments**: heavily commented, including long teaching notes and some Hinglish
(`ye line se... koi link nahi hai`, `hamne abhi ki leye ye band kiya hai`). Large commented-out
blocks are common and are usually "parked" code, not dead-on-arrival.

---

## 8. Build / run / test

No Docker images for the services, no CI, no `.env`, no Spring profiles
(`application-*.properties` does not exist — a single `application.properties` per service).

**Required environment variables** (referenced as `${...}` with **no defaults**; every service
fails to start without them):
- `user`, `password` — MySQL credentials (`password` is also the PostgreSQL password for Ai-Service)
- `secretkey` — base64 JWT secret, shared by AuthService / UserService / APIGateway
- `apikey` — Google Gemini API key (Ai-Service)

**Build order** (`shared-events` and `common-security` must be installed first —
`shared-events` is currently **absent from the local `~/.m2`**, `common-security:1.0.0` is present):

```
mvn -f shared-events/pom.xml clean install
mvn -f common-security/pom.xml clean install
mvn -f <Service>/pom.xml clean package
```

The root reactor (`mvn clean install`) only covers 6 of the 10 modules.

**Startup order**: Kafka (`docker compose -f kafka-docker/docker-compose.yml up -d`)
→ MySQL + Postgres → `Eureka` (8081) → `AuthService` → everything else → `APIGateway` (8082).
`roles` rows (`ROLE_ADMIN/ROLE_ENGINEER/ROLE_SUPERVISOR`) must be seeded by hand — signup does
`roleRepository.findByRolename(ROLE_ENGINEER).orElseThrow()`.

Schema is managed by **Flyway** (as of 2026-09-01, Track A2). All 6 JPA services run
`spring.jpa.hibernate.ddl-auto=validate` and keep versioned SQL in
`src/main/resources/db/migration/`. Each was baselined from its live schema via
`mysqldump --no-data` / `pg_dump --schema-only`, with `spring.flyway.baseline-on-migrate=true`.
Flyway 11.7.2, managed by the Boot parent. MySQL services need `flyway-core` + `flyway-mysql`;
Ai-Service needs `flyway-core` + `flyway-database-postgresql`.
**Never edit an applied migration** - Flyway checksums them. Add a new `V<n>__*.sql` instead.
See `notes/FLYWAY-NOTES.md` for the full explanation and per-migration log (personal notes
folder, gitignored - see PROGRESS.md).

**Tests**: 8 auto-generated `contextLoads()` classes, **no real tests anywhere**. They will fail
without the env vars and running infrastructure. `spring-boot-starter-test` and
`spring-security-test` are on the classpath, unused. There are no `target/*.jar` artifacts — the
project has been run from IntelliJ (`out/production/` exists at root), not via Maven packaging.

---

## 9. Known issues — catalogued, NOT resolved. Do not silently "fix" while doing unrelated work.

### Correctness bugs

1. `ChangePasswordService.changePassword` — mutates a **detached** `User` (no `@Transactional`,
   no `save`) then delegates to `LogoutUserService.LogoutUser()`, which re-loads the user and
   saves only `tokenVersion`/`lastLogoutAt`. **The new password is never persisted**, and
   `passwordUpdatedAt` is never set. The `if (user == null)` after `orElseThrow` is dead code.
2. `InternalUserController.getOwnSpecificDetails` — `@GetMapping("/UserSpecificDetails/{userId}")`
   but the parameter is `@PathVariable UUID userid`. Name mismatch, so the mapping cannot resolve.
   Both `IncidentService` and `NotificationService` `UserClient`s call this endpoint.
3. `IncidentService.updateIncidentStatusBasedOnRole` — sets `rootCause` and `resolutionNotes`
   unconditionally from the request, so any non-RESOLVED status update **nulls out** a
   previously-recorded root cause.
4. `RegisteringUserService` — `.roles(user.getRoles().stream().map(role1 -> role.getRolename()...))`
   maps every element to the captured outer `role`, ignoring `role1`.
5. `LogoutController.LogoutOfOwn` uses a bare `@RequestMapping("/user")` (accepts **every** HTTP
   verb, including GET); `LogoutController.LogoutAnyUser(User)` has **no mapping at all** and is
   unreachable.
6. Account lockout has **no unlock path**: 5 failures set `accountLocked=true`; the pre-check
   rejects before authentication, and the reset to `false` only runs after a successful login.
7. `RagController` uses `@GetMapping` with `@RequestBody` on both endpoints — GET-with-body is
   dropped by many clients and by gateways.

### Security

8. **The header-trust model has no perimeter.** ~~Downstream services accept `X-User-Id`/`X-Roles`
   verbatim and are reachable directly on ports 8083–8088.~~ **LARGELY FIXED 2026-09-02**
   (Track B1 steps 1-2): all services now bind to `127.0.0.1` only, and `HeaderAuthFilter`
   requires a shared `X-Internal-Secret` before trusting any identity header. `/internal/**`
   was closed in step 3 (2026-09-02). Remaining: the JWT still uses one shared HMAC secret,
   so any service that can verify a token could also mint one - asymmetric keys are the proper
   fix, deliberately deferred. Anyone who can reach a service port
   can forge an admin identity. Nothing strips inbound `X-*` headers, and `/auth/**` bypasses the
   gateway filter entirely.
9. ~~`ReportingService` `permitAll`s **`/reports/**`** and `/internal/**` ... `UserService`
   `permitAll`s `/internal/**` (user emails, team rosters).~~ **RESOLVED 2026-09-02**
   (Track B1 step 3). `/internal/**` in both services now requires `ROLE_INTERNAL_SERVICE`,
   granted only to a caller sending both the shared secret and `X-Internal-Service` (which the
   gateway strips from inbound requests). `/reports/**` now requires ADMIN or SUPERVISOR.
10. `AuthService` has **no `@EnableMethodSecurity`**, so `@PreAuthorize("hasRole('ADMIN')")` on
    `RoleController` is inert (the URL rule `/roles/** -> ROLE_ADMIN` is what actually protects it).
11. `/auth/token-version/{userid}` is `permitAll` — a public per-user counter that throws a raw
    `RuntimeException` for unknown ids.
12. `System.out.println` of user ids, roles, authorities and token versions across AuthService,
    UserService and the gateway.
13. `spring.json.trusted.packages=*` on every consumer.
14. Signup hardcodes `ROLE_ENGINEER` and sets `enabled=true`, contradicting the entity comment
    ("initially when user register the account is not enabled"). There is no activation flow.
15. ~~`SignupFormDTO.password` has `@Size(min=6)` but **no `@NotBlank`**, so a null password passes
    validation and reaches `passwordEncoder.encode(null)`.~~ **RESOLVED 2026-08-28** — `@NotBlank`
    added to `SignupFormDTO`, `LoginFromDTO` and `ChangePasswordDTO`.

### Unfinished / misleading

16. **`NotificationService` never sends email.** `EmailNoticationService.storeNotificationLog`
    logs `"Email Successfully sent to ..."` and sets `NotificationStatus.SUCCESS` around an
    **empty try block**. `spring-boot-starter-mail` is a dependency; there is no `JavaMailSender`
    and no `spring.mail.*` config. The `catch` is unreachable.
17. `Ai-Service` incident classification is **switched off** — `ConsumeIncidentCreatedEvent`'s body
    is commented out, so `ai.classification.result` has no traffic and no consumer.
18. All four SLA topics are published and **consumed by nobody**.
19. `SLAConfiguration` holds test values in production code: resolution SLA for `MEDIUM` = **50
    seconds**, assignment SLA for `HIGH` = **30 seconds** and `MEDIUM` = 40 seconds
    (vs 864000 s / 86400 s for the others).
20. `common-security/filter/JwtFilter.java` and `common-security/util/JwtUtil.java` are **entirely
    commented out**; so are `AuthService/util/JwtUtil.java` and `AuthService/entity/Status.java`.
21. `IncidentService/utils/IncidentUtils.java` is an empty class.
    `shared-events/.../Main.java` is an IDE-scaffold `main` inside a library JAR.
    `AuthService/exception/` and `AuthService/dao/` are **empty directories**.
22. `SLA_BreachedSchedular` publishes then marks-as-published (at-least-once with a crash window).
    The author's own comment says an Outbox pattern is needed.

### Structural / duplication

23. ~~**`AuthService:0.0.1-SNAPSHOT` (a Spring Boot application) is declared as a dependency** of
    UserService, IncidentService, NotificationService and ReportingService — and **nothing imports
    from it** (one commented import in `UserLoggedInEventConsumer`). Dead coupling that forces an
    `AuthService install` before any of them build.~~ **RESOLVED** - removed from all four
    services; verified nothing imports from it and everything still builds.
24. ~~Root pom omits 4 of the 10 modules~~ **RESOLVED 2026-09-01** (Track A4) - all 11 modules
    are now listed and `mvn clean install` at the root builds everything in one command,
    verified by deliberately breaking `shared-events` and confirming the build caught it.
    Still true: no service uses the root pom as its `<parent>`, so its `<properties>` are
    decorative - now clearly commented as such. Making them real is a separate task, blocked
    on two deliberate exceptions (common-security on Boot 3.3.2, Ai-Service on Java 17).
25. `common-security` is on Spring Boot 3.3.2 while every consumer is on 3.5.11.
26. `Ai-Service` targets Java 17 while every other module targets 21.
27. `common-security`'s source dir is literally `src/main/java/com.project.security/` (one flat
    directory name, not `com/project/security/`). It compiles under Maven but breaks conventions
    and tooling assumptions.
28. `JwtUtil` exists **three times** (APIGateway active, AuthService commented, common-security
    commented), and `generateToken` is duplicated in `APIGateway/utils/JwtUtil` (unused there) and
    inside `AuthService/config/SecurityConfig`.
29. **`AuthService.SecurityConfig` (a `@Configuration`) contains `generateToken` and is injected
    into `LoginUserService`** as a collaborator.
30. `ReportingController` (`/reports/...`) **duplicates** the two endpoints of
    `InternalReportingContoller` (`/internal/reports/...`) verbatim. (Since 2026-09-02 this is no
    longer a data leak - `/reports/**` requires ADMIN/SUPERVISOR - but the duplication remains.) its
    `EngineerPerformanceService`/`EngineerWorkloadService` fields are declared halfway down the
    class. ("Contoller" typo is in the class name.)
31. `UserClient` is duplicated near-verbatim in IncidentService and NotificationService.
32. `User`, `Role`, `RoleName` are duplicated between AuthService and UserService (intentional
    per-service schema, but the enums can drift silently).
33. `@EnableMethodSecurity` appears **three times** in UserService (`UserServiceApplication`,
    `SecurityConfig`, and on the `UserService` **service class**, where it does nothing).
    `@EnableSchedulerLock` is declared twice in ReportingService (app class + `SchedularConfig`).
34. `SchedularConfig` hardcodes `withLockedByValue("ReportingService_Instance1")`, so every
    instance claims the same identity in the lock table.
35. ~~`IncidentService`, `NotificationService`, `ReportingService` and `Ai-Service` each declare a
    `@LoadBalanced WebClient.Builder` bean **inside their `SecurityConfig`**.~~ **HALF RESOLVED
    2026-09-09** (Track B3 step 2). IncidentService and NotificationService no longer have the bean
    at all - Feign wires load balancing itself, so there was nothing to move. Still present in
    `ReportingService` and `Ai-Service` (both declare it but neither uses WebClient for calls).
36. `spring.cloud.gateway.routes[*]` uses the pre-2025 property prefix; Spring Cloud 2025.0.x
    moved these to `spring.cloud.gateway.server.webflux.*`. Verify routing still binds.
37. ~~Blocking `.block()` on `WebClient` throughout IncidentService/NotificationService (a
    `RestClient` would be the honest choice); each call also does `webClientBuilder.build()` per
    invocation.~~ **RESOLVED 2026-09-09** (Track B3 step 2). Both now use OpenFeign, matching
    Ai-Service. Feign was chosen over RestClient deliberately: consistency with the service that
    already used it was judged worth more than the smaller diff. No `.block()` and no
    per-invocation `build()` remain in either service. See §6.
38. `IncidentService.AssignIncident` calls UserService twice per assignment — `IsUserActive`
    re-fetches what `getUser` already returned.
39. Gateway JWT expiry is configured at 3600000 ms but AuthService mints 7200000 ms tokens (the
    gateway's value is only read by its unused `generateToken`).

### Ops

40. ~~No global exception handling, no `@ControllerAdvice`, no problem-details responses.~~
    **RESOLVED 2026-08-28** by the `common-web` module (Track A1). See §7 Conventions.
41. ~~No Flyway/Liquibase; `ddl-auto=update` on every JPA service.~~ **RESOLVED 2026-09-01**
    (Track A2). Flyway on all 6 JPA services, `ddl-auto=validate` everywhere. Two tables that
    were previously undocumented and hand-created - ReportingService's `shedlock` and
    Ai-Service's `resolved_incidents_vectors` - are now captured in their baselines.
42. No tests, no CI, no Dockerfiles, no profiles, no config server.
43. Actuator exposes only `info,health`, but `show-details=always` in Auth/User/Notification/Reporting.
44. Secrets come from bare env vars with no defaults and no validation.
45. No git repository at the project root.

---

## 9b. Companion documents

- **[PROGRESS.md](PROGRESS.md)** — the modernization plan (Track A1/A2/A3/A4), current status,
  and a file-by-file change log. **Read this first in a new session.** There is no git here, so
  that change log is the only record of what was modified and why.
- **[BUGS-TO-FIX-LATER.md](BUGS-TO-FIX-LATER.md)** — environment and build problems found in
  passing that were not fixed (broken `JAVA_HOME`, nothing ever `mvn install`ed, Java version
  drift, `common-security`'s flat source directory).

---

## 10. Working agreements for future sessions

- Match the surrounding file's style (including PascalCase method names) unless the task **is** a
  rename; a blanket rename touches callers across services.
- Anything touching JWT claims, `tokenVersion`, or the `X-User-*` headers spans
  APIGateway + common-security + AuthService — change all three together.
- Adding a Kafka event means editing `shared-events` and `mvn install`ing it before the
  producer/consumer services will compile.
- `auth.getName()` is always a **UUID string**, never a username or email.
- Prefer fixing the section 9 items deliberately, one cluster at a time, with a test alongside —
  the codebase has almost no safety net (only `common-web`'s 12 contract tests).
- **Error handling is centralized now.** Throw one of `common-web`'s five exceptions; never add a
  per-service `@ControllerAdvice`, and never go back to bare `RuntimeException` for a business
  failure. A bare `RuntimeException` now means precisely "this is our bug, return 500" — a few
  are deliberate (missing seed data) and are commented as such.
- **Changing `ErrorResponse`'s fields is a breaking change for every client of all 8 services.**
  `GlobalExceptionHandlerTest` will fail if you do; that failure is the point.
- **The HTTP client follows the app's execution model, not preference.** APIGateway is reactive
  -> `WebClient`. Every other service is blocking -> `@FeignClient`. Never `WebClient` +
  `.block()` in a blocking service; that was issue 37 and it is fixed. A new Feign client needs
  `@EnableFeignClients` on the application class (already present in IncidentService,
  NotificationService, Ai-Service) and inherits the auth interceptor, error decoder and timeouts
  automatically.
- **Never name a `@Configuration` class the same as a `@Bean` method inside it.** Spring registers
  the scanned config class under its own decapitalized name, so the two collide and the service
  refuses to start. Name config classes after their job (`FeignErrorDecoderConfig`), not after
  what they produce.
- APIGateway is WebFlux and cannot use `common-web`. If the error shape changes, update
  `JwtGatewayFilter.onError` by hand to match.
