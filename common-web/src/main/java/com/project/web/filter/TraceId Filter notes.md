# TraceId Filter — Easy Notes

Quick notes to read alongside `TraceIdFilter.java` so the code makes sense at a glance.

---

## 1. What problem does this solve?

When many requests hit your app at the same time, logs from different requests get mixed together. If a user says "I got an error," you have no easy way to find *their* exact logs.

**Fix:** give every request a small unique ID (`traceId`) and stamp it on:
- every log line for that request
- the HTTP response sent back to the client

Now the user can give you the ID, and you just search the logs for it.

---

## 2. What is a "Correlation ID"?

Just a fancy name for: **an ID that connects all logs/events belonging to one request.**

Same idea as a courier tracking number — one number, and every checkpoint (gateway → auth → service → database) records it.

---

## 3. Where does the traceId come from?

Two cases:

| Situation | What happens |
|---|---|
| Incoming request already has `X-Trace-Id` header | **Reuse it** (important for microservices — keeps the same ID across all services) |
| No `X-Trace-Id` header | **Generate a new one** |

In the code, this is the check:
```java
if (traceId == null || traceId.isBlank()) {
    traceId = UUID.randomUUID()...substring(0, 8);
}
```
It only makes a new ID when none was sent — otherwise the whole chain of services shares one ID.

---

## 4. What is MDC?

**MDC = Mapped Diagnostic Context.**

Think of it as a **small temporary storage box tied to the current request's thread.**

```
MDC
------------------
traceId = ABC123
```

Once you put `traceId` into MDC, your logging framework can automatically grab it for every log line — **without you manually typing the traceId in every `log.info(...)` call.**

That's the whole trick: write logs normally, MDC quietly adds the traceId.

---

## 5. How does the traceId show up in logs automatically?

Your logging pattern includes:
```
%X{traceId}
```
This literally means: *"pull whatever is in MDC under the key `traceId` and print it here."*

So:
```java
log.info("Saving incident");
```
becomes in the actual log file:
```
INFO [traceId=ABC123] Saving incident
```

---

## 6. Sending it back to the client

The filter also does:
```java
response.setHeader(TRACE_ID_HEADER, traceId);
```
So the response carries:
```
X-Trace-Id: ABC123
```

If there's an error, the JSON error body can also include it:
```json
{
  "code": "DATA_INTEGRITY_VIOLATION",
  "traceId": "ABC123"
}
```

**Why this matters:** if the traceId is in the response but never written into the logs, it's a "decorative fake" — it looks useful but you can't actually search for it. The filter is what makes it real (puts it in *both* places).

---

## 7. Why the `finally` block is critical

Servlet apps reuse threads from a thread pool. If you forget to clean up MDC:

```
Thread-1 handles Request A → MDC traceId = ABC123
Thread-1 later handles Request B → traceId ABC123 leaks into B's logs (WRONG)
```

So the filter always does:
```java
try {
    filterChain.doFilter(request, response);
} finally {
    MDC.remove(TRACE_ID_KEY);
}
```
`finally` runs no matter what (success, failure, exception) — so cleanup is guaranteed and the next request on that thread starts fresh.

---

## 8. Full flow in one picture

```
Incoming Request
      │
      ▼
Has X-Trace-Id header? ── yes ──► reuse it
      │
      no
      │
      ▼
Generate new traceId
      │
      ▼
MDC.put("traceId", id)   +   store as request attribute
      │
      ▼
Controller → Service → Repository   (all logs auto-tagged)
      │
      ▼
Response header: X-Trace-Id: ABC123
      │
      ▼
finally { MDC.remove("traceId") }   ← always runs, prevents leaking to next request
```

---

## 9. What the extra `currentTraceId()` method is for

```java
public static String currentTraceId(HttpServletRequest request)
```

Used later (e.g., by a global exception handler) to fetch the current traceId when building an error response — even outside the filter itself. It checks:
1. The request attribute first (`request.getAttribute("traceId")`)
2. Falls back to MDC
3. Falls back to `"unknown"` — so it **never returns null**

---

## 10. One thing to always remember

> `traceId` does **not fix** errors. It's just a **tracking label** — like a parcel tracking number.
> It only exists so that "user complaint" and "server logs" can be connected instantly.

---

## 11. Cheat-sheet glossary

| Term | Meaning |
|---|---|
| `traceId` / correlation ID | Unique ID for one request, shared across logs + response |
| `X-Trace-Id` header | Where the traceId travels between client ↔ server ↔ other services |
| MDC | Thread-local key-value store SLF4J logging reads from automatically |
| `%X{traceId}` | Logging pattern syntax meaning "insert MDC's traceId value here" |
| `finally { MDC.remove(...) }` | Cleanup step, required because threads are reused (thread pool) |
