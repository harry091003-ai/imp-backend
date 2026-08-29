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

## Fixed

_Move items here with the date once resolved._

- **2026-08-28** — `common-web/src/main/java/com/project/web/filter/TraceIdFilter.java` had a
  duplicated `*/` closing a javadoc block twice, which broke compilation with
  `class, interface, enum, or record expected`. Removed the stray second delimiter; all comment
  text kept as written.
- **2026-08-28** — `common-web/pom.xml` was missing the `spring-boot-starter-test` dependency while
  `GlobalExceptionHandlerTest.java` was present, so the module would not compile. Re-added the
  dependency (test scope only — it never ships in the jar).
