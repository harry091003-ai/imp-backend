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
| `common-kafka` | — | plain JAR: retry-then-dead-letter error handler + 2 DLT producers, **trace-id producer/consumer interceptors** (Track C), auto-config | no |
| `common-outbox` | — | plain JAR (Phase 2, 2026-10-12): the outbox pattern — `OutboxWriter` (save an event in the business transaction), `OutboxRelay` + `OutboxScheduler` (send it to Kafka), auto-config. **Used by IncidentService** (step 2), **AuthService** (step 3) and **ReportingService** (step 4), all 2026-10-13; **proved on the real system 2026-10-14** (Kafka down, service restart). Full notes: `notes/outbox-pattern.md` | no |
| `common-trace` | — | plain JAR (Track C, 2026-09-30): `TraceId` (header name, MDC key, safety rule, id maker) + `TraceIdLoggingDefaults` (fills Boot's log-pattern slot). Used by `common-web` and `common-kafka`. Java 17 bytecode | no |

Root `pom.xml` (`com.example:incident-management-platform:1.0-SNAPSHOT`, `packaging=pom`)
lists **all 14 modules** (all 12 as of 2026-09-01 in Track A4; `common-trace` added 2026-09-30 in
Track C; `common-outbox` added 2026-10-12), so `mvn clean install` at the root builds everything in one command. `common-trace` is
listed before `common-web` and `common-kafka` because both depend on it. It previously listed only
6, and the other 6 built individually - see issue 24.

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
- **Caffeine** (in-memory cache, APIGateway only, Track B3 step 3) — caches the AuthService
  token-version answer so it isn't called on every request.
- **Resilience4j** (`spring-cloud-starter-circuitbreaker-reactor-resilience4j`, APIGateway only,
  Track B3 step 4) — circuit breaker around the AuthService call. Reactor variant, not the plain
  one — the gateway is WebFlux.
- **Micrometer** (comes with actuator; used in APIGateway, Track B3 step 5) — `Counter` per tier and
  a `Gauge` for cache size, registered on the `MeterRegistry` so they show under `/actuator`.
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
SLA deadline / breach / event-published flags), `IncidentAuditLog`, `DailyMetrics` (**never written** - issue 61),
`EngineerPerformanceStats`, `EngineerWorkload`, `ProcessedEvent` (idempotency keys - stop a repeat since 2026-10-13, issue 60 fixed).

**NotificationService** — `NotificationLog`, unique constraint on `(eventId, recipientEmail)`.

**Ai-Service** — `AiInsight` (per-incident classification + confidence + conflict flag).

---

## 4. Auth & security architecture

1. `POST /auth/login` → AuthService mints a JWT with claims `roles`, `tokenVersion`, `email`;
   subject = **userid (UUID string)**.
2. Every request hits **APIGateway** → `JwtGatewayFilter` (`GlobalFilter`, order `-1`):
   skips paths starting with `/auth`, validates signature + expiry, then checks the token version
   against AuthService and rejects on mismatch. This is the logout / password-change revocation
   mechanism. **As of 2026-09-11 (Track B3 step 3) this check goes through a Caffeine cache with
   3 tiers** (fresh cache → live call → stale-cache fallback → skip-the-check fallback) instead of
   calling AuthService on every request. See `services/TokenVersionService.java` and §6.
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
7. **As of 2026-09-23 to 09-24 (Track B5) the gateway also rate-limits**, in
   `filter/RateLimitFilter.java` (`GlobalFilter`, order `1` — runs *after* `JwtGatewayFilter`,
   because it needs the `X-User-Id` header that filter writes). 4 rules, checked in order, first
   match wins: `/ai/**` per user (5/min, 100/day — the paid Gemini calls), `/auth/login` per IP
   (10/min, 100/hour — catches credential stuffing, which per-account lockout cannot), `/auth/signup`
   per IP (5/hour, 20/day), everything else per user (20/sec, 300/min — a safety floor, must stay
   **last** in the rule list or it would swallow every request before the tighter rules ran). Login
   and signup deliberately ignore `X-Forwarded-For` (a caller-supplied header, easy to fake) and use
   the real connection address instead. Buckets are Bucket4j, held per caller in a bounded Caffeine
   cache (`services/KeyedRateLimiter.java`, reused across all 4 rules). Over a limit → 429 +
   `Retry-After`, in the same JSON shape as every other gateway error. See §6 and
   `notes/rate-limiting-on-ai-endpoints.md`.
8. **As of 2026-09-25 (Track B5) `JwtGatewayFilter`'s own error handling covers *only* the
   AuthService token-version lookup**, via `.materialize()`. Errors from forwarding the request
   (the target service is down, unregistered, or too slow) are **not** caught there — they reach
   `handler/GatewayExceptionHandler.java`, a `@Order(-2)` `ErrorWebExceptionHandler`, the gateway's
   WebFlux equivalent of `common-web`'s `GlobalExceptionHandler`. **Before this split (found while
   testing B5 step 3), any downstream failure was wrongly reported as `AUTH_SERVICE_UNAVAILABLE`**
   — a down IncidentService made the caller think their session was the problem, and the log
   blamed AuthService for someone else's outage. See §9 issue 48 and
   `notes/jwt-gateway-error-handling.md`.

9. **Gateway filter order (Track C, 2026-09-30):** `TraceIdGatewayFilter` **-3** (makes/accepts the
   trace id, first, so every other filter and every error carries it) → `RequestSizeFilter` **-2** →
   `JwtGatewayFilter` **-1** → `RateLimitFilter` **1**. Later filters get the id with
   `TraceIdGatewayFilter.currentTraceId(exchange)`, an exchange **attribute** — not from the request
   header, because `exchange.mutate()` makes a copy and a header set on one copy is invisible to the
   others. See section 6 and `notes/trace-id-and-logging.md`.

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
Consumers use `spring.json.trusted.packages=com.project.events` (narrowed from `*` in B2). **This is an exact
package match, not "starts with"** (issue 65): `com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent` is
rejected by that setting, even though the comment in each `application.properties` says otherwise.
**As of Track C (2026-09-30) every message also carries an `X-Trace-Id` header**, added by
`common-kafka`'s `TraceIdProducerInterceptor` on send and restored into the MDC by
`TraceIdRecordInterceptor` on receive. A message parked in a `-dlt` topic keeps the same header. Any
service with `common-kafka` on its classpath gets both automatically.

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

**Outbox (Phase 2):** IncidentService's three `incident.*` events no longer use `kafkaTemplate.send`. They are
saved in an `outbox_event` row in the same transaction as the incident (Flyway `V3`), and `common-outbox`'s
relay sends them about a second later - same topics, keys and message bytes, so consumers are unchanged.
AuthService's five events (`user.*`, `roles.update`) went through the outbox in step 3 (Flyway `V3`; `user.registered`
and `roles.update` now have a Kafka key = the user id). ReportingService's five events (resolved report + 4 SLA
topics) went through the outbox in step 4 (Flyway `V2`): the listener's event joins its `@Transactional`, and each
SLA scheduler page saves its event rows and its flags in one `TransactionTemplate` transaction (issue 22 closed).
**No service calls `kafkaTemplate.send` for a business event any more** (Ai-Service's `ai.classification.result`
producer is switched off with the classifier, issue 17).
**Proved on the real system (step 5, 2026-10-14, run by hand):** normal case, Kafka stopped (the request still
succeeds, the row waits as `NEW`, and is sent after `docker start kafka`), and a service restart while Kafka is
down (the waiting row survives). **Not run by hand:** the SLA timers (test D) and the inbox on the real system.
A row stuck in `FAILED` needs a person - checklist and SQL in `notes/outbox-pattern.md` section 25.

Consumer groups: `user-service-group`, `notification-service-group`, `Reporting-service-group`,
`Ai-service-group`.

**Idempotency**: ReportingService `IdempotencyService.isDuplicate(eventId)` checks `existsById` first and throws
`DataIntegrityViolationException` for a repeat, which the consumer's catch block turns into "skip". **Fixed
2026-10-13 (issue 60):** before, it only did `save()`, which is a merge for an id the code assigns, so a repeat
just updated the old row and the work ran again. NotificationService relies on the `(eventId, recipientEmail)` unique constraint
instead, and that one does work (its row id is generated, so every save is a real INSERT).

---

## 6. Synchronous inter-service calls

**As of 2026-09-09 (Track B3 step 2) the rule is: the HTTP client follows the app's execution
model.** APIGateway is reactive (WebFlux/Netty) so it uses `WebClient`. Every other service is
blocking (Spring MVC/Tomcat) so it uses **OpenFeign**. Do not mix them — `WebClient` + `.block()`
in a blocking service was exactly the bug B3 removed.

- Gateway → AuthService: `WebClient` `http://AUTHSERVICE/auth/token-version/{userid}`.
  Timeouts and a bounded connection pool live in `config/WebClientConfig` +
  `gateway.httpclient.*` in `application.properties`. **As of Track B3 step 3, this is no longer
  called on every request** — `services/TokenVersionService.java` caches the answer (Caffeine,
  30s fresh) and falls back to a stale cached value (up to 5 min) or skips the check entirely if
  AuthService won't answer at all. `gateway.tokenversion.*` in `application.properties`.
  **As of Track B3 step 4, the call is also wrapped in a Resilience4j circuit breaker** —
  `gateway.circuitbreaker.*` in `application.properties`. When AuthService is failing, the breaker
  opens and skips the call entirely (no more waiting out the 2s timeout on every request); a 404
  ("no such user") is explicitly excluded from failure counting, same reasoning as the tiers —
  AuthService answering 404 means it's healthy. **As of step 5 the numbers are visible** (gateway
  only, port 8082): `/actuator/metrics/gateway.tokenversion.lookups` (per tier),
  `/actuator/metrics/gateway.tokenversion.cache.size`, `/actuator/circuitbreakers`. Healthy =
  `stale_cache` and `unavailable` stay at 0.
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
belongs at exactly one layer. **That layer has not been chosen yet** - B3 added no retries.

**Error mapping.** IncidentService has `config/FeignErrorDecoderConfig` mapping 404 →
`NotFoundException` so "no such user" stays a 404 instead of becoming a 500. NotificationService
deliberately has none — its old code had no 404 rule to preserve, and Feign's default decoder
(status + method + URL + body) beats what was there. **Caveat:** a *timeout* never reaches an
`ErrorDecoder` (there is no HTTP reply to decode), so it currently surfaces as a 500 rather than
a 503. **Still open** - B3 step 4 only covered the gateway.

Service ids used in `lb://` / URIs are inconsistently cased vs `spring.application.name`
(e.g. `lb://IncidentService` vs `INCIDENTSERVICE`) — verify before touching discovery.
`@FeignClient(name = ...)` must match `spring.application.name` exactly.

**Request size (Track B4 step 6).** The gateway's `filter/RequestSizeFilter.java` (a
`GlobalFilter`, order `-2` — runs before `JwtGatewayFilter`'s `-1`) refuses any request body over
`gateway.request.max-body-size` (256KB) with a 413, and refuses a chunked body with no
`Content-Length` at all with a 411 (Spring's own `RequestSizeGatewayFilterFactory` only reads
`Content-Length`, so a chunked request slips past it — that is why this is a hand-written filter,
not the built-in one). It applies to every route, including `/auth/**`. Downstream services do
**not** re-check body size — they trust the gateway is the only door in, which holds only because
they bind to `127.0.0.1` (Track B1). The JSON-error-writing code every gateway filter/handler uses
lives in `utils/GatewayErrorWriter.java` (new, Track B4 step 6) — write to that one place, not to
each filter separately, or the error shapes will drift apart.

**Rate limiting (Track B5, steps 1-3, 2026-09-23 to 09-24).** `filter/RateLimitFilter.java`
(`GlobalFilter`, order `1`) sits after `JwtGatewayFilter` (`-1`) and `RequestSizeFilter` (`-2`).
4 rules, first path match wins — see §4 point 7 for the full list and numbers. Each rule owns its
own `KeyedRateLimiter` (one Bucket4j bucket per caller, held in a bounded Caffeine cache) and its
own Micrometer `Counter`, visible per rule at
`/actuator/metrics/gateway.ratelimit.rejected?tag=rule:<name>`. **The `default` rule must stay
last** in the rule list — the filter checks rules top to bottom and uses the first one that
matches the path, so a rule placed before `default` keeps its own tighter bucket, and `default`
placed anywhere else would swallow every request first. Settings: `gateway.ratelimit.*` in
`application.properties`, one `{short-window, long-window}` pair per rule.

**Error handling split (Track B5, 2026-09-25).** `JwtGatewayFilter` only catches errors from its
own token-version lookup (`.materialize()`) — see §4 point 8. Every other error the gateway
encounters (routing, a downstream service down or slow, no matching route) lands in
`handler/GatewayExceptionHandler.java`, a `@Order(-2)` `ErrorWebExceptionHandler`. It maps
`ResponseStatusException`/`ConnectException` to an honest status (503 service down, 504 too slow,
404 no route) and anything unrecognised to 500, always via `GatewayErrorWriter`, with the real
exception details kept in the log only (same no-leak rule as `common-web`'s handler).

**Tracing (Track C, 2026-09-30).** One id follows a request from the gateway through HTTP calls and
Kafka messages, and shows in every log line as `[id]`. Who does what: `TraceIdGatewayFilter`
(gateway) makes or accepts the id → `common-web`'s `TraceIdFilter` (MVC services) puts it in the MDC →
`common-kafka`'s producer interceptor copies it onto a message → the consumer interceptor puts it back
in the MDC on the other side. The log slot itself is filled by `common-trace`'s
`TraceIdLoggingDefaults` (an `EnvironmentPostProcessor` in `META-INF/spring.factories` — it has to run
before logging starts, so a normal bean would be too late). An incoming id is used only if it matches
`[A-Za-z0-9-]{1,64}` (stops log-line injection). Five flow charts: `notes/trace-id-and-logging.md`.

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
request body in AuthService, IncidentService, UserService and **Ai-Service** (Ai-Service's
`RagController` added 2026-09-20, Track B4 step 4 — it was previously wrong to say Ai-Service
has no request bodies; both its endpoints take one, just as `@GetMapping` with `@RequestBody`,
see issue 7). ReportingService genuinely has no request bodies (every endpoint is a plain
`@GetMapping`), but **as of Track B4 step 5 its `days` query parameter is validated too**
(`@Min`/`@Max`) — "no request bodies" and "no validation" are not the same thing.

**As of Track B4 (2026-09-19 to 2026-09-22), path and query parameters are validated the same
way as request bodies** — `@Min`/`@Max`/`@Size` directly on the `@PathVariable`/`@RequestParam`,
enforced by Spring's built-in method validation (`HandlerMethodValidationException`, handled in
`common-web`'s `GlobalExceptionHandler` alongside `@Valid` body failures). See ReportingService's
`days`, UserService's search `query` and batch-id list, and AuthService's `TokenVersionController`
(its `userid` path variable is now typed `UUID`, not `String`, so a non-UUID value is rejected by
Spring itself before the controller method runs). DTO length limits are chosen to **match the
database column they are saved to** — see issue 46 for the one mismatch B4 found and fixed with
a migration.

**Exception handling**: **as of 2026-08-28 this is now centralized** — see the `common-web`
module. Services throw `NotFoundException` / `ValidationException` / `ConflictException` /
`ForbiddenException` / `UnauthorizedException` (all extend `ApiException`, each carries an
`errorCode` and a fixed HTTP status). One shared `@RestControllerAdvice`
(`com.project.web.handler.GlobalExceptionHandler`) turns them into a single JSON shape,
auto-configured into every MVC service via `common-web`'s
`AutoConfiguration.imports`. APIGateway is WebFlux so it hand-matches the same shape via
`GatewayErrorWriter` — called directly from `JwtGatewayFilter.onError` for the gateway's own
checks, and from `handler/GatewayExceptionHandler` (Track B5, 2026-09-25) for everything else
(routing failures, a downstream service down). **Do not add per-service advice classes** — extend
the shared one, or for the gateway, extend `GatewayExceptionHandler`.
Remaining bare `RuntimeException`s are deliberate 500s (missing seed data) and are commented
as such.

**Response format**: successful responses have **no envelope** (deliberate decision) —
controllers return `ResponseEntity<SomeDTO>` or a bare DTO. **Errors are standardized**:
every failure returns `{timestamp, status, error, message, path, traceId}` plus an optional
`fieldErrors` array on `@Valid` failures. `LoginResponseDTO`/`SignupResponseDTO` still carry
legacy `status`/`message` fields on the SUCCESS path only.

**Logging** (cleaned up in Track C1, 2026-09-30): **no `System.out.println` or `printStackTrace`
left in live code** (38 became `log.debug`; the few hits a search still finds are comments).
Two styles remain: SLF4J (`LoggerFactory.getLogger`, `@Slf4j` in `UserEventConsumer`) in all MVC
services, and `java.util.logging.Logger` in `HeaderAuthFilter`, the gateway filters/handlers,
`TokenVersionService` and AuthService `SecurityConfig`. Rule: **log user ids only** — never emails,
roles, passwords, tokens or whole objects. Every MVC log line gets `[traceId] ` from the log pattern
automatically; do **not** write it into the message by hand. Gateway log lines still write `[id]`
by hand (issue 52).

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

The root reactor (`mvn clean install`) covers all 14 modules (fixed in A4; `common-trace` builds
first of the shared libraries).

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

**Tests**: real tests now exist in **five** modules (as of 2026-10-12): `common-trace` 8,
`common-web` 19, `common-kafka` 28 (includes a real embedded-Kafka test), `common-outbox` 24 (H2 + embedded
Kafka + real JPA), `APIGateway` 53, `IncidentService` 9, `AuthService` 16 and `ReportingService` 15 (outbox:
H2 + embedded Kafka + a full-start test each) = 172. ReportingService's wiring test uses a test-only
`src/test/resources/shedlock_for_tests.sql` so its 3 SLA timers can really run. Run a service's outbox tests
with `-Dtest=<ServiceName>OutboxTest,OutboxWiringTest,<...>EventsOverKafkaTest` - the old `contextLoads()`
still needs real MySQL/Kafka and would connect to them.
The other services (and Eureka) still have only the auto-generated
`contextLoads()` class, which fails without the env vars and running infrastructure. Run one
module's tests with `./mvnw.cmd -f <module>/pom.xml test` (shared libs have no wrapper - use any
service's). There are no `target/*.jar` artifacts — the
project has been run from IntelliJ (`out/production/` exists at root), not via Maven packaging.

---

## 9. Known issues — catalogued, NOT resolved. Do not silently "fix" while doing unrelated work.

### Correctness bugs

1. `ChangePasswordService.changePassword` — sets the new password on a `User` with no
   `@Transactional` and no `save`, then delegates to `LogoutUserService.LogoutUser()`, which re-loads
   the user and saves it. **CORRECTED 2026-10-01: the password IS saved today — but only by luck.**
   Spring Boot's default *open-session-in-view* keeps one database session for the whole request, so
   the user object is the same one `LogoutUser()` saves, and the new password rides along. Proven in a
   throwaway project that copies this code shape (`notes/experiments/transaction-rollback-and-osiv/`):
   open-session-in-view ON → password saved; OFF → password lost. AuthService does not set the option,
   so it is ON. **Still wrong:** it depends on that hidden setting (no `@Transactional`, no explicit
   `save`), and `passwordUpdatedAt` is never set. **The same hidden dependency was found in `UpdateRoleService`**
   (it saved a user loaded before the forced logout, which would overwrite the token-version bump); fixed
   2026-10-13 in outbox step 3 by loading the user inside the transaction. (This entry used to say "never persisted" — that was
   reasoned from reading the code and was not tested.)
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
   **But see issue 55: since Track A1 the failure counter is rolled back, so the lock normally never
   gets set in the first place.**
7. `RagController` uses `@GetMapping` with `@RequestBody` on both endpoints — GET-with-body is
   dropped by many clients and by gateways. **Still true as of Track B4 step 4** — both bodies are
   now validated (`@Valid`, `@NotBlank`/`@Size`), but the HTTP method itself is unchanged.

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
11. `/auth/token-version/{userid}` is `permitAll` — a public per-user counter. **As of Track B4
    step 5, `userid` is a typed `UUID` path variable**, so a non-UUID value now gets a clean 400
    from Spring itself instead of a raw crash inside `UUID.fromString(...)`. It still throws
    `NotFoundException` (a proper 404, not a raw `RuntimeException`) for a well-formed but unknown
    id — that part was already fixed under issue 40/`common-web`. The `permitAll` exposure itself
    is unchanged.
12. ~~`System.out.println` of user ids, roles, authorities and token versions across AuthService,
    UserService and the gateway.~~ **RESOLVED 2026-09-30** (Track C1 steps 1 and 1b). 38 calls in 18
    files became `log.debug`, logging user ids only. Emails no longer appear in NotificationService
    logs either. See PROGRESS.md C1.
13. ~~`spring.json.trusted.packages=*` on every consumer.~~ **RESOLVED in B2** (narrowed to
    `com.project.events` in UserService, NotificationService, ReportingService and Ai-Service; this
    entry was left stale until 2026-09-30).
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
22. ~~`SLA_BreachedSchedular` publishes then marks-as-published (at-least-once with a crash window).
    The author's own comment says an Outbox pattern is needed.~~ **RESOLVED 2026-10-13** (outbox step 4): each
    page saves its outbox rows and its "published" flags in one transaction. Same for `SLA_BreachingRiskSchedular`.

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
28. `JwtUtil` exists **four times** (APIGateway active; AuthService, UserService and common-security
    all commented out - UserService's `jwt.secret` setting and `jjwt` dependencies are therefore unused too), and `generateToken` is duplicated in `APIGateway/utils/JwtUtil` (unused there) and
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
    moved these to `spring.cloud.gateway.server.webflux.*`. **Checked 2026-09-22 (Track B4 step
    6): routing is fine.** `GatewayServerWebfluxPropertiesMigrationListener` auto-copies the old
    keys to the new ones at startup and logs a warning ("Each configuration key has been
    temporarily mapped..."), confirmed in a real startup log. Renaming the keys to silence the
    warning is still a separate, un-started tidy-up.
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
    **APIGateway is different (Track B3 step 5):** it also exposes `metrics` and `circuitbreakers`,
    read-only. Metrics exist on the gateway only.
44. Secrets come from bare env vars with no defaults and no validation.
45. No git repository at the project root.

### Found during Track B4 (2026-09-19 to 2026-09-22)

46. ~~`incidents.root_cause` and `incidents.resolution_notes` were `varchar(500)` in the database,
    but `UpdateIncidentStatus_RequestDTO` allowed up to 2000 characters.~~ **RESOLVED 2026-09-19**
    — `V2__widen_root_cause_and_resolution_notes.sql` widens both columns to 2000, matching the
    DTO. **You must run this migration yourself; it has not touched the live database yet.**
47. `AuthService/dto/LoginFromDTO.java`'s `username` field is `@NotBlank` (required) but never
    read — `LoginUserService` authenticates by email only (confirmed: no
    `request.getUsername()` anywhere in it). A caller must still send *something* there because
    of `@NotBlank`. Pre-existing behaviour, documented in the file, not changed by Track B4.

### Found during Track B5 (2026-09-23 to 09-28)

48. ~~`JwtGatewayFilter`'s `.onErrorResume(...)` was attached AFTER `chain.filter(...)`, so it
    caught errors from forwarding the request (a downstream service down, unregistered, or no
    matching route) as well as errors from its own AuthService lookup — and reported ALL of them
    as `503 AUTH_SERVICE_UNAVAILABLE`.~~ **RESOLVED 2026-09-25.** Real proof: with IncidentService
    not running, a perfectly valid token got "Unable to verify the session", and the log said
    "Auth service call failed" 35 times in one test run — both blaming AuthService for an
    IncidentService outage. Fixed with `.materialize()` (catches only the lookup's own errors) and
    a new `handler/GatewayExceptionHandler.java` for everything else. See §4 points 7-8, §6.
49. **504 GATEWAY_TIMEOUT is wired up in `GatewayExceptionHandler` but never actually fires.**
    Only the gateway's call to AuthService has a response timeout configured
    (`gateway.httpclient.response-timeout-ms`). Nothing limits how long a *routed* call to
    Incident, User, Reporting or Ai-Service is allowed to take — a slow (not down) service just
    makes the caller wait. Found while fixing issue 48; not fixed, since it needs its own decision
    about per-route timeout values.

### Found during Track C (2026-09-30)

50. ~~`KafkaErrorHandlingAutoConfig` (B2) defined two `KafkaTemplate` beans, which switched off Spring
    Boot's own default `kafkaTemplate` (it is `@ConditionalOnMissingBean`).~~ **RESOLVED 2026-09-30.**
    A service that only *sends* messages (ReportingService, Ai-Service) would have had no
    `KafkaTemplate<String, SomeEvent>` to inject. Fixed with
    `@AutoConfiguration(after = KafkaAutoConfiguration.class)`; proven by
    `KafkaTraceIdAutoConfigTest`. **Not confirmed in the real system** - whether ReportingService and
    Ai-Service actually failed to start between B2 and now has not been checked.
    **Update 2026-10-14:** moot for ReportingService - since outbox step 4 its producers no longer inject a
    `KafkaTemplate`, and the service ran in the step 5 real-system test. Still open for **Ai-Service only**
    (`AiResponseProducer` injects one). Close this when Ai-Service has been seen starting fine.
51. Services' `TraceIdFilter` (common-web) still reuses any incoming `X-Trace-Id` **without the safety
    check** the gateway and Kafka code apply. Safe today (services only accept the gateway), but not
    defence in depth. Not fixed.
52. Gateway log lines write the trace id **by hand** (`[id] ...`). The gateway is reactive, so the MDC
    does not follow a request across threads without extra work. Not fixed.
53. The trace id is a plain string in logs. It gives no timings, no spans and no UI.
    **Micrometer Tracing / Zipkin / Prometheus (C1 step 4)** was deliberately left for later.
54. **The trace id rules exist twice.** `common-trace`'s `TraceId` (used by `common-web` and
    `common-kafka`) and `APIGateway`'s `TraceIdGatewayFilter` each hold the header name `X-Trace-Id`,
    the safety pattern `[A-Za-z0-9-]{1,64}` and the 8-character id maker. The gateway's `pom.xml` has no
    `common-trace`. If someone changes one (say, the length), the gateway and the services would
    disagree silently. Found 2026-10-01 while re-reading the LLD notes against the code. Not fixed.
    Fix when wanted: add `common-trace` to the gateway and delete the gateway's copy. Check first: the
    module also fills the log-format `[id]` slot, which would then print an empty `[-]` on gateway lines
    (the gateway writes its own `[id]` by hand - issue 52).
55. **Account lockout never triggers (found 2026-10-01 while re-reading the AuthService LLD).**
    `LoginUserService` is `@Transactional` (jakarta, class level). On a wrong password it adds 1 to
    `failedLoginAttempts`, calls `save`, and then **throws** `UnauthorizedException` — a
    `RuntimeException`, so the transaction rolls back and the saved counter is undone. Reproduced in a
    throwaway project: 6 wrong passwords → six 401s, stored counter still 0, never locked
    (`notes/experiments/transaction-rollback-and-osiv/`). Before Track A1 this method *returned* a 401
    instead of throwing, so the counter was kept; A1's change to "throw" broke it quietly.
    **Effect:** per-account brute-force protection is off. What still protects `/auth/login` is the
    gateway's per-IP limit (10/min, 100/hour, Track B5) — which does not stop a slow attacker or one
    spread over many IPs. Accounts already locked in the database stay locked (issue 6). **Not fixed.**
    Proper fix: write a failing test first (5 wrong logins → `accountLocked` is true in the database),
    then keep the counter in its own transaction (`REQUIRES_NEW`) or stop throwing inside the
    transaction. Reproduced in a copy of the code shape, not yet against the real service.

### Found while re-reading the LLD notes against the code (2026-10-01) - none fixed

56. **IncidentService: the assign endpoint has no ownership check.** `AssignIncident` checks that the caller
    is ACTIVE, the incident exists, and the target is an ACTIVE engineer - never that the target is in
    this supervisor's team, or that the incident is the supervisor's. Any supervisor can assign any incident
    to any engineer, and can re-assign a `RESOLVED` incident. (Read, update-details and change-status do
    have data-level checks.)
57. **IncidentService: the "what may a supervisor touch" rule is typed four times and the copies disagree**
    (list, read-one, update, status), and a supervisor with no engineers gets an **empty list even for the
    incidents they created** (the code returns before the query runs).
58. **IncidentService: a read returns only 8 fields** (`IncidentResponseDTO`). No endpoint returns the
    description, category, service, environment, location, timestamps, root cause or resolution notes.
59. **`CRITICAL` priority has no SLA rule.** IncidentService accepts `LOW/MEDIUM/HIGH/CRITICAL`;
    `SLAConfiguration` only handles the first three. For `CRITICAL` the `switch` default gives a resolution SLA
    of **0 seconds** (and assignment 3600). Every CRITICAL incident is "breached" at creation.
60. **RESOLVED 2026-10-13 (outbox step 4)** - `IdempotencyService` now checks `existsById` first. Proved test-first
    on the real code: `ReportingOutboxTest` failed 3 times before the fix (workload +2, resolved event saved twice,
    report reset to OPEN) and passes after. The original entry, kept for the record:
    **ReportingService: the idempotency ledger does not catch a repeated event.** `ProcessedEvent` has an id
    the code assigns itself, so `repository.save()` does a `merge` - the second delivery finds the old row
    and updates it, no exception, and the listener runs the work again. Reproduced in a replica
    (`notes/experiments/transaction-rollback-and-osiv/`, `Ledger.java`): one event delivered twice ran
    the work twice, ledger rows = 1. Effects of a real redelivery: a `created` event resets the report to
    OPEN, an `assigned` event adds 1 to the workload again, a `RESOLVED` event double-counts the engineer's
    stats and republishes the resolved event. (Normal retries after a failure are fine - the transaction
    rolls back with the ledger row.) Fix: `existsById` first, or make `ProcessedEvent` implement `Persistable`.
61. **ReportingService: `daily_metrics` is never written.** The table, entity and repository exist; nothing
    inserts into it. `/reports/metrics` (its controller method is called `getDailyMetrics`) returns all-time
    totals counted from `incident_report`, not daily numbers.
62. **ReportingService: the active-incident count drifts.** On assign it adds 1 to the assignee but never
    removes 1 from the previous assignee; on resolve it removes 1 from whoever sent `RESOLVED`
    (`resolvedBy`), not from the assignee. `resolvedBy` and the engineer's expertise stats are therefore
    credited to whoever pressed the button, which may be a supervisor or admin.
63. **UserService returns fields that are never updated.** `UserResponseDTO` includes `enabled`,
    `accountLocked` and `failedLoginAttempts`, but nothing writes those columns after registration, so
    every user shows `enabled=false`, `accountLocked=false`, `failedLoginAttempts=0` - even though
    AuthService has `enabled=true`.
64. **No entity in any service has `@Version`**, so two people changing the same row at the same moment
    silently overwrite each other (last save wins). Most visible on `Incident` (two supervisors assigning
    the same incident). Fix: add a `version` column + `@Version`, map the optimistic-lock failure to a 409
    `ConflictException`, and write a test that proves the second save fails.

### Found during the outbox (2026-10-13)

65. **`spring.json.trusted.packages` is an exact match, not "starts with".** UserService, NotificationService,
    ReportingService and Ai-Service all say in `application.properties` that `com.project.events` "also covers
    `com.project.events.enumsUsedByEvents`". It does not. A reader with that setting rejects
    `SlaBreachingRiskEvent` ("not in the trusted packages"). **No harm today** - nobody reads the SLA topics
    (issue 18). The first SLA-risk consumer must add `com.project.events.enumsUsedByEvents` to its setting (or
    the class must move up a package). Proved by `ReportingEventsOverKafkaTest.todaysTrustedSettingRejectsTheRiskEvent`.
    **Not fixed** (the comments in the 4 files are still wrong).

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
  the codebase has almost no safety net (real tests exist only in `common-trace`, `common-web`, `common-kafka`, `common-outbox`, `APIGateway`, and the outbox tests of IncidentService, AuthService and ReportingService).
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
- **Actuator on the gateway must stay read-only** (`management.endpoints.access.max-permitted=read-only`).
  The `circuitbreakers` page accepts a POST that can force the breaker open, and `/actuator` has no
  login check on the gateway. Tested: without the line the POST returns 200 and the breaker becomes
  `FORCED_OPEN`.
- **Register circuit breakers through the registry** (`circuitBreakerRegistry.circuitBreaker(...)`),
  not `CircuitBreaker.of(...)`. `.of()` works but never shows up in `/actuator`.
- **Never use a user id (or any open-ended value) as a metric tag.** Every new value creates a new
  metric. Tags must have a small fixed set of values, like the 4 tiers.
- APIGateway is WebFlux and cannot use `common-web`. If the error shape changes, update
  `GatewayErrorWriter` by hand to match — both `JwtGatewayFilter` and `GatewayExceptionHandler`
  call it, so one change covers every gateway error.
- **Validate at the edge, declaratively (Track B4).** A request body gets `@Valid` + annotations
  on the DTO. A path/query value gets `@Min`/`@Max`/`@Size` directly on the parameter — Spring's
  own `HandlerMethodValidationException` handles it, already wired into `common-web`'s
  `GlobalExceptionHandler`. Don't hand-write an `if` check for something an annotation already
  does. A DTO's length limit must **match the database column it is saved to** — a mismatch means
  a value can pass our check and still be rejected by the database as a confusing 500 (see
  issue 46, the one place this was found for real).
- **`@Valid` is not optional decoration.** A DTO covered in annotations does nothing if the
  controller method is missing `@Valid` on that parameter — the rules simply never run. This is
  exactly the mistake `RagController` had (issue 7 / Track B4 step 4) until it was added.
- **In reactive code, attach an error handler around the ONE step that can fail — not around
  everything after it too (Track B5, issue 48).** `.onErrorResume(...)` (or `.materialize()`)
  placed after a `chain.filter(...)`/further chained call also catches errors from *that* later
  call, not just the step before it. If different failures need different answers, catch each
  one at its own step, or check the error's real cause before deciding what to say.
- **A new gateway rate-limit rule that should cover "everything left over" must be added LAST**,
  never first — `RateLimitFilter` uses the first rule (checked top to bottom) that matches the
  request path.
- **Rate-limit or trust decisions by IP must use the real connection address**
  (`request.getRemoteAddress()`), never a caller-supplied header like `X-Forwarded-For` — a caller
  can write anything into a header. Only trust that header once a specific, known reverse proxy or
  load balancer sits in front of the gateway, and only for that one known source.
- **No `System.out.println` / `printStackTrace` (Track C).** Use SLF4J. Per-request chatter is
  `log.debug` (off by default in production). Log **user ids only** — never emails, roles, passwords,
  tokens or whole objects.
- **Do not write the trace id into a log message by hand in an MVC service.** The `[id]` slot in the
  log pattern (filled by `common-trace`) does it. A hand-written one shows twice.
- **The trace id's name and safety rule live in `common-trace`'s `TraceId`** — use it from every
  module that can (`common-web`, `common-kafka`). Never retype `"X-Trace-Id"` or `"traceId"` there.
  **One exception, on purpose-by-necessity:** `APIGateway` does not depend on `common-trace`, so
  `TraceIdGatewayFilter` keeps its own copy of the header name, the safety pattern and the id maker
  (issue 54). If one changes, change the other by hand. In a gateway filter, read the id with
  `TraceIdGatewayFilter.currentTraceId(exchange)` (an attribute), not from the request header.
- **Any Spring Boot auto-config we write that defines a bean Boot also creates conditionally
  (`@ConditionalOnMissingBean`) must declare `after = <Boot's auto-config>`.** Otherwise ours is
  created first and silently switches Boot's off (issue 50). Test it with `ApplicationContextRunner`.
- **A new service that sends or receives Kafka messages needs `common-kafka` in its `pom.xml`.** It
  brings the retry/dead-letter handling *and* the trace-id interceptors with no extra code.
- **Outbox (Phase 2, from step 2 on): a service that uses `common-outbox` must never call
  `kafkaTemplate.send(...)` for a business event.** Call `outboxWriter.save(topic, key, event)` inside the
  transaction that saves the data. `save` refuses to run without a transaction - that refusal is the point.
  Do any call to another service (Feign) **before** that transaction starts, never inside it.
- **A Kafka producer class must NOT carry its own `@Transactional` (Outbox).** It would start a separate
  transaction just around the outbox insert and silently split the event from the data. The **caller**
  (the service method that changes the data) owns the transaction. Where a service method has none, wrap
  "save data + producer call" in a `TransactionTemplate`; it joins an existing transaction if there is one.
- **Connecting a new service to the outbox (checklist, details in `notes/outbox-pattern.md` section 26):**
  (1) `common-outbox` + `common-kafka` in the pom; test-only `h2` 2.2.224 and `spring-kafka-test`;
  (2) the table as an **exact copy** of `common-outbox/.../outbox/outbox_event_mysql.sql` in a new Flyway file;
  (3) producer class calls `OutboxWriter.save`, no `@Transactional` on it; (4) the data-changing method owns the
  transaction, Feign calls before it; (5) 3 tests: both-or-neither, round trip of every event class over a real
  embedded Kafka, whole-service wiring - and break the code once to see a test fail; (6) the **reading** side
  gets an inbox that checks `existsById` first.
- **`outbox.enabled=false` switches off the whole outbox, writer included** - a service that has producers using
  `OutboxWriter` would then fail to start. Use it only in a test that must not run the relay.
- **A `FAILED` outbox row is never retried by itself.** It means the message could not be built or sent (for
  example the event class was renamed or removed). Do not rename or move an event class while rows for it exist.
