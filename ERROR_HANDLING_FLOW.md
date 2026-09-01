# How Error Handling Works — Plain English Walkthrough

> This explains what happens, step by step, when something goes wrong in any of the 8
> services — from the moment a bad request arrives to the JSON the client sees. It uses one
> running example throughout. No credentials, ports beyond the public list in CLAUDE.md, or
> other sensitive detail appear here — this is safe to read, share, or paste anywhere.
>
> Companion reading: `CLAUDE.md` §4 (auth flow) and §7 (conventions) for the wider picture.
> `PROGRESS.md` for what was built and when.

---

## The one idea to hold onto

**Before this existed:** a service method just did `throw new RuntimeException("Incident not
found")`. Spring turned that into a blank HTML page with status 500. The client had no way to
tell "you made a mistake" apart from "the server is broken".

**Now:** every failure is one of a handful of *named* exception types. The name itself carries
the meaning, so one shared piece of code can turn any of them into the right HTTP status and the
right JSON — automatically, without any service writing its own error-formatting code.

Three layers, each with exactly one job:

```
Layer 1            Layer 2                      Layer 3
Your service code   One shared class             The client
throws a named   →  (GlobalExceptionHandler)  →  gets a JSON body,
exception            catches it, decides           same shape,
                      status + JSON shape           every time
```

---

## Walkthrough: someone asks for an incident that doesn't exist

**The request:** `GET /incidents/3f2a1c00-...` where that id was deleted yesterday.

### Step 1 — the request arrives, gets a travel tag

Before anything else runs, `TraceIdFilter` (part of the shared `common-web` module) stamps this
request with a short id — say `a1b2c3d4`. Think of it like a claim-check ticket. Every log line
written while handling this request will carry that same ticket number, and it goes back to the
client too. If a user ever says "I got an error", you ask for this id and jump straight to the
right log lines instead of hunting through logs from all 8 services by timestamp.

### Step 2 — the controller calls the service, the service asks the database

`IncidentController.getIncidentById(...)` calls into `IncidentService.getIncidentByIdBasedOnRole(...)`,
which asks the database for that id. The database finds nothing.

### Step 3 — the service throws a *named* exception, not a generic one

```java
Incident incident = incidentRepository.findById(incidentId)
        .orElseThrow(() -> new NotFoundException("INCIDENT_NOT_FOUND",
                "Incident not found: " + incidentId));
```

`NotFoundException` is one of five exception types every service can throw
(`NotFoundException`, `ValidationException`, `ConflictException`, `ForbiddenException`,
`UnauthorizedException` — full table below). Each one already knows its own HTTP status.
`NotFoundException` always means 404 — the service code never has to decide that itself.

The two pieces handed in are:
- `"INCIDENT_NOT_FOUND"` — a short code the *type* of problem, chosen by whoever wrote this
  line, because they're the only one who knows exactly what failed.
- `"Incident not found: 3f2a1c00-..."` — a human-readable sentence, safe to show a user.

### Step 4 — the exception "escapes" the controller, and Spring catches it automatically

Nobody writes a `try/catch` around the controller method. Spring itself watches every
controller in the service. The moment an exception comes out of one, Spring looks for a
matching `@ExceptionHandler` and routes the exception there. This routing class is
`GlobalExceptionHandler`, and it lives once, in the shared `common-web` module — not copied
into all 8 services.

### Step 5 — the handler decides what to log and what to send back

For a `NotFoundException` (and its four siblings), the handler:
1. Logs a short line, e.g. `[a1b2c3d4] GET /incidents/3f2a1c00 -> 404 INCIDENT_NOT_FOUND:
   Incident not found: 3f2a1c00` — note the traceId from Step 1 is right there in the log.
2. Builds the response body from the exception's own status, code and message.

### Step 6 — the client receives this, every time, from every service

```json
{
  "timestamp": "2026-08-29T10:15:00Z",
  "status": 404,
  "error": "INCIDENT_NOT_FOUND",
  "message": "Incident not found: 3f2a1c00-...",
  "path": "/incidents/3f2a1c00-...",
  "traceId": "a1b2c3d4"
}
```

Same six fields, same names, whether this came from AuthService, IncidentService or
ReportingService. A frontend writes its error-handling code once — "read `.status`, show
`.message`" — and it works everywhere.

---

## The five exception types, and when to use which

| Type | HTTP status | Ask yourself | Example |
|---|---|---|---|
| `NotFoundException` | 404 | Does the thing exist at all? | Incident id that was never created |
| `ValidationException` | 400 | Is the request itself wrong or unreasonable? | Assigning an incident to someone who isn't an engineer |
| `ConflictException` | 409 | Is the request reasonable, but clashes with current state? | Signing up with a username that's taken |
| `ForbiddenException` | 403 | Do we know who they are, but they're not allowed? | An engineer trying to read someone else's incident |
| `UnauthorizedException` | 401 | Do we not know who they are at all? | Wrong password at login |

The plain-English test for 403 vs 401: **401 means "who are you?", 403 means "I know exactly
who you are, and the answer is no."**

---

## Two more paths worth knowing

### Path B — a request body fails validation (`@Valid`)

Example: `POST /incidents` with an empty `title`. The DTO has:
```java
@NotBlank(message = "Title is required")
private String title;
```
Spring checks this *before* the controller method body even runs, and throws
`MethodArgumentNotValidException` on its own — nobody writes this throw. The handler catches
this type specially and adds one extra field the plain example above didn't have:

```json
{
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "One or more fields are invalid.",
  "fieldErrors": [
    { "field": "title", "message": "Title is required", "rejectedValue": null }
  ],
  "traceId": "..."
}
```

`fieldErrors` only ever appears for this kind of failure — every other error response omits it
completely, so the six-field shape from the walkthrough above stays the reliable default.

### Path C — something nobody planned for (the safety net)

Example: a bug causes a `NullPointerException` deep in some service code. This is NOT one of
the five named exceptions, so it falls through to one last catch-all handler. This one behaves
differently on purpose:

- The **real** exception, with its full detail and stack trace, is written to the **server
  log only** — never sent anywhere.
- The **client** gets a generic sentence and nothing else:

```json
{
  "status": 500,
  "error": "INTERNAL_ERROR",
  "message": "An unexpected error occurred. Please quote the traceId when reporting this.",
  "traceId": "a1b2c3d4"
}
```

**Why hide it:** a raw internal error message can accidentally contain table names, file
paths, or library versions — details a service should never hand to whoever is calling its
API. The traceId is the bridge: a user reports it, you search the server log for that id, and
you find the real detail there, in a place only you can see it.

---

## Where the shared code actually lives

All of the above (the 5 exception types, the response shape, the handler, the traceId tagging)
lives in **one module**: `common-web`. Every other service just adds it as a dependency in its
`pom.xml` — no extra setup, no wiring. It turns on automatically because of one small file
(`AutoConfiguration.imports`) that tells Spring Boot to load it even though it lives in a
different package than the rest of the service's own code.

**Two exceptions to "every service":**
- **APIGateway** doesn't use `common-web` — it runs on a different, reactive style of Spring
  (WebFlux) where this module's mechanism doesn't apply. It hand-builds a matching JSON shape
  in its own code instead, so the client-facing contract still looks identical.
- **NotificationService** doesn't need it yet — it only listens to Kafka events, it has no
  HTTP endpoints of its own to protect. It would pick this up automatically the moment it ever
  gets one.

---

## Quick reference: which service throws what

A short, representative sample — not the full list:

| Situation | Exception thrown | Where |
|---|---|---|
| Login with wrong password | `UnauthorizedException` | AuthService |
| Sign up with a taken username | `ConflictException` | AuthService |
| Look up a user id that doesn't exist | `NotFoundException` | UserService |
| Assign an incident to a non-engineer | `ValidationException` | IncidentService |
| Read an incident you have no access to | `ForbiddenException` | IncidentService |
| Ask for a report on an incident id that was never created | `NotFoundException` | ReportingService |
