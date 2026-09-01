# Flyway – Easy Notes

Simple notes to understand **why** we are using Flyway and **how** it works in this project.

> **Safety note:** this file contains no passwords, API keys, or connection credentials.
> Wherever a command needs them, it shows a placeholder like `<your-mysql-user>`.
> Keep it that way when adding to this file.
>
> Related docs: `ERROR_HANDLING_FLOW.md` (how errors work), `PROGRESS.md` (what's done/pending),
> `CLAUDE.md` (whole-codebase reference).

---

## 1. First, know the difference: Entity vs Schema

- **Java Entity** → this is just Java code (a class with fields).
- **Database Schema** → this is the real structure inside MySQL/PostgreSQL (tables, columns).

They look connected, but they are **two different things**.

```java
@Entity
public class User {
    @Id
    private UUID id;
    private String name;
    private String email;
    private String password;
    private LocalDateTime createdAt;
}
```

The above is just Java. The actual `users` table in the database is a separate thing that Hibernate keeps in sync (or not).

---

## 2. What Hibernate does today (`ddl-auto=update`)

Right now our config has:

```
spring.jpa.hibernate.ddl-auto=update
```

This means:

> "Hibernate, look at my `@Entity` classes and change the database automatically so it matches."

This works fine... until we rename or restructure something.

---

## 3. The real problem: Silent Rename

Say yesterday we had:

```java
private String affectedSource;
```

Today we rename it:

```java
private String affectedService;
```

Hibernate does **not** understand this as a rename. It only sees:

- an old field disappeared
- a new field appeared

So it creates a **brand new column**:

```
incidents
---------------------------------
affected_source     <-- old column, still has data
affected_service    <-- new column, all NULL
```

Our old data (`Solar Plant`, `Wind Farm`, etc.) stays in the old column, and the new column is empty.

**This is called a "silent rename" — Hibernate quietly does the wrong thing, and it's easy to miss.**

---

## 4. This is where Flyway comes in

Flyway's whole idea:

> "Don't let Hibernate *guess* how the database should change. YOU explicitly write what change should happen, in SQL."

We write plain SQL files, and Flyway runs them in order:

```
db/migration/
    V1__baseline.sql
    V2__add_incident_environment.sql
    V3__rename_affected_source_to_affected_service.sql
```

These files become the **official history** of our database.

---

## 5. Easiest way to think about it: Flyway = Git, but for the database

| Git | Flyway |
|---|---|
| commit 1, commit 2, commit 3 | V1, V2, V3 |
| tracks code changes | tracks database changes |
| `git log` shows history | `flyway_schema_history` table shows history |

Example:

```
V1 → Created initial tables
V2 → Added environment column
V3 → Renamed affected_source
V4 → Added index
```

Now our database changes live in Git, next to our code. 🎉

---

## 6. Rename example, done the Flyway way

Instead of letting Hibernate guess, we write it ourselves:

```sql
ALTER TABLE incidents
RENAME COLUMN affected_source TO affected_service;
```

Now there's zero confusion. We are literally telling the database:

> "Rename this column. Keep the data."

Before:
```
affected_source
----------------
Solar
Wind
Hydro
```

After:
```
affected_service
----------------
Solar
Wind
Hydro
```

Data is safe. No guessing involved.

> **Version note:** `RENAME COLUMN` needs **MySQL 8.0 or newer**. We're on MySQL 8.4, so this
> works. On older MySQL you'd have to write the clunkier
> `ALTER TABLE incidents CHANGE COLUMN affected_source affected_service VARCHAR(255);`
> — which forces you to repeat the column type, and silently changes the type if you get it wrong.

---

## 7. Where migration files live, and how to name them

Flyway looks in this exact folder inside each service:

```
src/main/resources/db/migration/
```

The filename has a strict format. Getting it wrong means Flyway silently ignores the file:

```
V1__baseline.sql
│ │  │
│ │  └── description (underscores become spaces in the history table)
│ └───── TWO underscores  ← the #1 beginner mistake, one underscore will NOT work
└─────── prefix: V = versioned migration (runs once, in order)
```

Other prefixes exist (`R__` for repeatable, `U__` for undo) but **we only use `V`**.

---

## 8. What do V1, V2, V3 mean?

Just version numbers, in order:

- `V1__baseline.sql` → Version 1 (starting point)
- `V2__add_environment.sql` → Version 2
- `V3__rename_affected_source.sql` → Version 3

Flyway always runs them **in numeric order**, one after another.

---

## 9. How does Flyway know what it already ran?

Flyway keeps a special table in the database called:

```
flyway_schema_history
```

Example content:

| version | description | success |
|---|---|---|
| 1 | baseline | true |
| 2 | add environment | true |
| 3 | rename affected source | true |

So next time the app starts, Flyway checks:

- Files available: V1, V2, V3, V4
- Already run (from the table): V1, V2, V3

Conclusion: **"I only need to run V4."**

---

## 10. ⚠️ The rule that surprises everyone: never edit an applied migration

Flyway stores a **checksum** (a fingerprint) of every migration file it runs, in that same
history table.

If you go back and edit `V2__add_environment.sql` after it has already run — even just fixing a
typo in a comment — the fingerprint no longer matches what's recorded, and Flyway refuses to
start:

```
Migration checksum mismatch for migration version 2
```

**Why Flyway is this strict:** your database was built by the *old* version of that file. If the
file changed, then the file in Git no longer describes the database you actually have — which
destroys the one guarantee Flyway exists to provide.

**The rule:**

> Once a migration has run **anywhere** (even just your local machine), it is frozen forever.
> To change something, write a **new** migration.

Wrong ❌
```
edit V2__add_environment.sql
```

Right ✅
```
add V5__fix_environment_column.sql
```

---

## 11. What happens on a teammate's fresh laptop?

This is where Flyway really shines.

- Teammate's database = **completely empty**
- Git has: V1, V2, V3, V4 migration files

When they start the app, Flyway runs:

```
V1 → V2 → V3 → V4
```

...automatically, in order. Now their database matches everyone else's. No manual setup needed.

```
Git migrations
      ↓
   Flyway
      ↓
Database schema
```

This is why people say Flyway lets you **"recreate any environment's schema from Git."**

---

## 12. What is "Baseline"?

This part matters most for **our project**, because our databases already exist and already
have real data in them.

We already have a database with:

```
users
roles
user_roles
```

...and it already has real data in it.

If we now add Flyway with a `V1__baseline.sql` that contains:

```sql
CREATE TABLE users (...);
CREATE TABLE roles (...);
CREATE TABLE user_roles (...);
```

We do **NOT** want Flyway to actually run those `CREATE TABLE` statements — the tables already exist!

Instead, we tell Flyway:

> "This database is already at version 1. Just mark V1 as done, don't re-run it."

This process is called **baselining**.

```
Existing database (already has data)
      ↓
"You're already at V1"
      ↓
Flyway writes a row in flyway_schema_history saying V1 is applied
      ↓  (it never executes V1's SQL)
Future migrations start from V2
```

**Why we still write `V1__baseline.sql` even though it never runs on our machine:** it's for the
*empty* database case. On a fresh laptop (or if you ever drop and recreate your local DB), there's
nothing to baseline, so Flyway runs V1 for real and builds the tables from scratch. The same file
serves both situations.

---

## 13. The `baseline-on-migrate` setting

```
spring.flyway.baseline-on-migrate=true
```

This tells Flyway: "It's okay, this database already has stuff in it — treat it as baseline
instead of throwing an error."

**What actually happens without this setting** (this is worth knowing precisely): Flyway does
**not** blindly try to run V1 and crash halfway. It checks first, finds a non-empty database with
no history table, and refuses to do anything at all:

```
Found non-empty schema(s) but no schema history table.
Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

That's Flyway being careful — it stops *before* touching anything, rather than making a mess.
Good to know: the failure mode here is a clear error message, not damage.

⚠️ **Important:** Don't just turn this on everywhere blindly. Make sure the baseline SQL file
actually matches what's really in the database, otherwise a fresh laptop will build a *different*
schema than the one you have — and you won't find out for weeks.

---

## 14. ⚠️ MySQL gotcha: a failed migration does NOT roll back

This one matters a lot for us, because 5 of our 6 databases are MySQL.

**PostgreSQL** can run schema changes inside a transaction. If a migration has 3 statements and
statement 2 fails, all 3 are undone. Clean.

**MySQL cannot.** Every `CREATE TABLE` / `ALTER TABLE` commits immediately and permanently.

So if `V7__three_changes.sql` contains:
```sql
ALTER TABLE users ADD COLUMN a VARCHAR(50);   -- succeeds, permanent
ALTER TABLE users ADD COLUMN b INVALID_TYPE;  -- fails 💥
ALTER TABLE users ADD COLUMN c VARCHAR(50);   -- never runs
```

...you're left with column `a` added, `b` and `c` missing, and Flyway marking V7 as **failed**.
The app won't start until you fix it by hand.

**How we avoid this:**
- Keep migrations **small** — ideally one logical change per file.
- Test every migration on a scratch copy of the database before running it for real.
- Take a backup before running migrations against a database whose data you care about.

---

## 15. What changes for Hibernate after adding Flyway?

We change this:

```
spring.jpa.hibernate.ddl-auto=update
```

to this:

```
spring.jpa.hibernate.ddl-auto=validate
```

Now Hibernate's job changes:

- **Before:** "Let me modify your DB automatically."
- **After:** "I will NOT touch your DB. I will only check if my entities match the DB."

**Order of events on startup:** Spring Boot runs **Flyway first**, then hands over to Hibernate.
So the database is already migrated by the time validation happens. We don't configure this —
Spring Boot wires the ordering for us.

```
App starts
   ↓
Flyway migrates the DB (runs any new V files)
   ↓
Hibernate validates entities against the now-migrated DB
   ↓
App is ready (or fails loudly)
```

---

## 16. Example of `validate` in action

Database has:
```
users: id, name, email
```

Entity has:
```java
id, name, email
```

✅ Match → App starts fine.

Now a developer adds a new field but **forgets** to write a migration:

```java
private String phone;
```

Database still only has: `id, name, email`
Entity now has: `id, name, email, phone`

Hibernate with `validate` says:

```
❌ Schema-validation: missing column [phone] in table [users]
```

App **refuses to start**.

This is actually a **good thing** — it catches the mistake immediately instead of causing weird bugs later.

**Be honest about its limits though.** `validate` checks that tables and columns exist with
broadly compatible types. It does **not** reliably check:
- indexes
- foreign keys
- default values
- `NOT NULL` constraints in every case

So `validate` passing does not prove the schema is perfect — it proves the obvious things line
up. The migration files remain the real source of truth.

---

## 17. Why is `validate` + Flyway better than `update`?

**With `update` (old way):**
```
Developer changes Entity
       ↓
Hibernate auto-changes DB
       ↓
Maybe correct, maybe wrong, maybe dangerous 😬
```

**With Flyway + validate (new way):**
```
Developer changes Entity
       ↓
Developer writes migration SQL
       ↓
Migration gets reviewed in Git (like any code change)
       ↓
Flyway applies the migration to DB
       ↓
Hibernate validates entity vs DB
```

Now every database change is:
- explicit
- reviewable (in Git)
- versioned
- reproducible on any machine
- part of the normal startup process

---

## 18. Does Flyway let you "undo" changes?

Careful with this — Flyway migrations are **not automatically reversible** like Git commits.

Example: `V3__rename_column.sql` does not know how to undo itself.

> Flyway *does* have an `U__` "undo" feature, but it is a **paid (Teams/Enterprise) feature**.
> We are using **Flyway Community**, the free version, so undo is not available to us at all.

If we need to reverse something, we write a **new** migration:

```
V4__rename_column_back.sql
```

So "undo" with Flyway doesn't mean "automatically roll back." It means:

> The full history of changes is explicit and reproducible, so we can always write a new migration to fix or reverse something.

---

## 19. ⚠️ The one command to never run: `flyway clean`

Flyway has a `clean` command that **drops every table in the database**. It exists for throwaway
test databases.

Modern Spring Boot ships with `spring.flyway.clean-disabled=true` by default, so it's blocked
unless someone deliberately turns it back on.

**Rule: never set `clean-disabled=false` in this project.** There is no situation in our local
setup where dropping every table is the right answer — and it would destroy exactly the data we
decided to keep.

---

## 20. Our project's migration flow (big picture)

```
Java Entity
     │  developer changes entity
     ↓
Write Flyway migration (SQL file)
     │
     ↓
SQL file committed to Git
     │
     ↓
Flyway executes migration on app startup
     │
     ↓
Database gets updated
     │
     ↓
Hibernate validates entity ↔ DB
     │
     ↓
Application starts (or fails if mismatch)
```

Example file sequence:

```
V1__baseline.sql              → Create users, roles, user_roles
V2__add_user_status.sql       → ALTER TABLE users ADD COLUMN status ...
V3__rename_email_column.sql   → ALTER TABLE users RENAME COLUMN ...
```

---

## 21. Why start with AuthService?

AuthService has a simple schema:

```
users
roles
user_roles
```

Good candidate to learn and test Flyway safely before rolling it out everywhere else. It's also
the service everything else depends on, so it's worth getting right early.

**Plan:**

```
AuthService
   ↓
Back up the database first (we have real data)
   ↓
Capture current DB schema exactly as it is
   ↓
Create V1__baseline.sql from that capture
   ↓
Tell Flyway "this DB is already at V1" (baseline-on-migrate) using that command 
   ↓
Disable Hibernate auto-update
   ↓
 and Set Hibernate to "validate"
   ↓
Test it works, and test the fresh-database case too
```

Once AuthService works fine, repeat the same pattern for:

```
UserService
IncidentService
NotificationService
ReportingService
Ai-Service
```

**Important detail for our situation:** we capture the baseline by dumping the **real, running
database** — not by generating SQL from the entity classes. Those two could already have drifted
apart, because `ddl-auto=update` never removes anything. If an old column was left behind by a
rename months ago, it's in the database but not in any entity. Dumping the real database captures
the truth; generating from entities would capture a fiction.

---

## 22. Special case: Ai-Service (uses pgvector)

Ai-Service uses PostgreSQL + `pgvector`. It has two kinds of tables:

- `AiInsight` → a normal Hibernate table → **Flyway manages this normally** ✅
- `resolved_incidents_vectors` → a special vector table, created by hand, with
  `initializeSchema(false)` telling Spring AI not to touch it → **leave it alone, keep it outside
  Flyway** ✅

It already works and is already deliberately unmanaged. Bringing a working hands-off table under
new management gains nothing and risks something.

---

## The whole idea in one picture

**Without Flyway:**
```
Java Entity
     ↓
Hibernate
     ↓
"Let me figure out what changed" (guessing)
     ↓
Database
```

**With Flyway:**
```
Java Entity + Migration SQL
     ↓
   Flyway
     ↓
Database
     ↓
Hibernate (validate mode)
     ↓
"Does entity match DB?"
```

---

## The one concept to remember

> **Entity ≠ Database schema.**
>
> - The Java entity describes what the **application expects**.
> - Flyway migrations describe **how the database actually changes**, step by step.
> - Hibernate (`validate` mode) just **checks** that the two agree — it never changes the DB itself anymore.

This separation — **application model → explicit migration → database → validation** — is the whole reason we're introducing Flyway.

---

## Quick Cheat Sheet

| Term | Meaning |
|---|---|
| Migration file | A SQL file describing one database change (e.g. `V2__add_status.sql`) |
| `V1`, `V2`, `V3`... | Version numbers, run in order |
| `__` (two underscores) | Separates version from description in the filename. One underscore = file ignored |
| `db/migration/` | The folder Flyway looks in, under `src/main/resources/` |
| `flyway_schema_history` | Table Flyway uses to track which migrations already ran |
| Checksum | Fingerprint of each migration file. Editing an applied file breaks it |
| Baseline | Telling Flyway "treat this existing DB as version X, don't rerun old scripts" |
| `baseline-on-migrate=true` | Setting that allows Flyway to baseline an already-existing DB |
| `ddl-auto=validate` | Hibernate only checks entity vs DB, never changes DB itself |
| `ddl-auto=update` (old way) | Hibernate auto-changes DB based on entities (risky, silent renames) |
| `flyway clean` | Drops every table. Disabled by default. Never enable it here |

---
---

# PART 2 — Project Migration Log

> Everything above is **how Flyway works in general**.
> Everything below is **what we actually did in this project**, in order, with the reasoning.
> Add a new entry here every time a migration is written or a service is converted.

## Status by service

| Service | Database | Flyway added? | `ddl-auto` | Latest migration |
|---|---|---|---|---|
| AuthService | MySQL | ✅ done, verified running | `validate` | V2__seed_roles.sql |
| UserService | MySQL | ✅ done, verified running | `validate` | V2__seed_roles.sql |
| IncidentService | MySQL | ✅ done, verified running | `validate` | V1__baseline.sql |
| NotificationService | MySQL | ✅ done, verified running | `validate` | V1__baseline.sql |
| ReportingService | MySQL | ✅ done, verified running | `validate` | V1__baseline.sql |
| Ai-Service | PostgreSQL | ✅ done, verified running | `validate` | V1__baseline.sql |

_(Update this table as each service is converted.)_

---

## Migration history

### AuthService — V1__baseline.sql
**Date:** 2026-08-29

**What it does (plain English):**
Records the shape of AuthService's database as it already existed - three tables: `roles`,
`users`, `user_roles`. On our machine it does **not** actually run; Flyway just notes "this
database is already at version 1". On an empty database it *does* run and builds the three
tables from scratch.

**Why we needed it:**
Every Flyway history needs a starting point. Without V1, a fresh machine would have no way to
build the database, and Flyway would have nothing to anchor its version numbering to.

**Where the content came from:**
`mysqldump --no-data --skip-add-drop-table --skip-comments` against the real running database -
NOT generated from the `@Entity` classes. Those two can disagree, because `ddl-auto=update` only
ever adds columns and never removes them. Dumping the live database captures what is actually
there. (Good news: for AuthService they matched - no orphan columns were found.)

**Three changes we made to the raw dump, and why:**

1. **Reordered the tables.** mysqldump wrote them alphabetically: `roles`, `user_roles`, `users`.
   But `user_roles` has foreign keys pointing at *both* other tables, so on an empty database it
   would be created before `users` exists and fail. The dump only survives this because it sets
   `FOREIGN_KEY_CHECKS=0` at the top. We reordered to `roles` -> `users` -> `user_roles`, which
   works without needing that trick and reads in dependency order.

2. **Removed `AUTO_INCREMENT=4`** from the `roles` table. That number is where the counter
   happens to be right now because there are 3 rows - it is a fact about *data*, not about
   *structure*. A fresh database should start counting at 1.

3. **Removed the mysqldump preamble** (`SET @OLD_CHARACTER_SET_CLIENT`, `SET FOREIGN_KEY_CHECKS=0`,
   and friends). Those exist so a dump can be replayed through the `mysql` command-line client.
   Flyway runs statements over a plain JDBC connection and does not need them. Each table still
   pins its own `CHARSET` and `COLLATE` explicitly, so nothing was lost.

**Two things we deliberately kept exactly as-is:**

- **The ugly Hibernate constraint names** (`UKjdhyvh8di85ai37phukfemdnx` etc). Renaming them to
  something readable was tempting, but the existing database already has these names. If a fresh
  database built different names, the two would diverge - which is exactly the problem Flyway
  exists to prevent.
- **`enabled bit(1)` next to `account_locked tinyint(1)`** - two different types for the same
  true/false idea. This is an accident (see `BUGS-TO-FIX-LATER.md` E7), but the baseline must
  describe reality or `validate` fails. Fixing it is a real schema change and gets its own
  migration later.

**Anything to watch out for:**
`userid` is `binary(16)`, not a readable string - Hibernate 6 stores `UUID` as 16 raw bytes on
MySQL. To read ids in a SQL client use `SELECT BIN_TO_UUID(userid) FROM users;` and to search by
one use `WHERE userid = UUID_TO_BIN('...')`.

**Still to come:** `V2__seed_roles.sql`. The `roles` table must contain ROLE_ADMIN,
ROLE_ENGINEER and ROLE_SUPERVISOR or signup fails outright - `RegisteringUserService` does
`findByRolename(...).orElseThrow()`. Right now those rows exist only because they were inserted
by hand, which means a fresh machine would have a working schema and a broken signup. Seeding
belongs in its own migration, separate from schema.

### UserService — V1__baseline.sql
**Date:** 2026-08-31
**What it does:** Baselines 3 tables — `roles`, `users`, `user_roles`.
**Same fix as AuthService:** mysqldump listed the tables alphabetically, so `user_roles` came
before `users` even though it has a foreign key pointing at it. Reordered to
roles → users → user_roles.
**Interesting bit:** this `users` table has no `password` column — deliberately. AuthService owns
credentials; this is only a profile mirror fed by Kafka. It does have two things AuthService's
does not: a `status` column (ACTIVE/INACTIVE/SUSPENDED) and `supervisor_id`, a foreign key from
the table back to *itself* — that is how the engineer/supervisor hierarchy is stored.
**Also removed:** `AUTO_INCREMENT=4` on `roles` (a fact about today's 3 rows, not about structure).

---

### IncidentService — V1__baseline.sql
**Date:** 2026-08-31
**What it does:** Baselines the single `incidents` table.
**Interesting bit:** `created_by`, `assigned_to` and `assigned_by` hold user ids but have **no
foreign keys**, and that is correct — those users live in a different database owned by a
different service, and a foreign key cannot reach across that boundary. IncidentService checks
users by calling UserService over HTTP instead. That is the trade microservices make: the
database stops enforcing integrity for you, and your code has to.
**Found while doing this:** the table has *no indexes at all* beyond the primary key, yet the
repository queries by three different columns. Recorded as E9 in BUGS-TO-FIX-LATER.md — not fixed
here, because a baseline must copy reality exactly or `validate` fails.

---

### NotificationService — V1__baseline.sql
**Date:** 2026-08-31
**What it does:** Baselines the single `notification_log` table.
**Interesting bit:** the most important line is `UNIQUE KEY (event_id, recipient_email)`. That is
not tidiness — it is the only thing stopping a person being emailed twice for one event. Kafka
delivers "at least once", so duplicates genuinely happen. The service does not check-then-insert
(two consumers could both pass the check simultaneously); it just inserts and lets the database
reject the duplicate. The database is the referee.
**Found while doing this:** a Hibernate-generated `CHECK (event_type between 0 and 7)` on an enum
with 8 values, where MySQL counts enum positions from 1. Possibly rejects the last value.
Recorded as E10 — needs testing before anyone changes it.

---

### ReportingService — V1__baseline.sql
**Date:** 2026-08-31
**What it does:** Baselines 7 tables — the biggest in the project. Grouped in the file by purpose:
the read model (`incident_report`, `incident_audit_log`), pre-computed stats (`daily_metrics`,
`engineer_performance_stats`, `engineer_workload`), and plumbing (`processed_event`, `shedlock`).
**No reordering needed:** unlike Auth/User, none of these tables reference each other.
**The most valuable line in this whole baseline:** the `shedlock` table. It is **not** a Hibernate
entity — no `@Entity` class exists for it anywhere. It belongs to the ShedLock library and had
been created by hand at some point. Which means that before today it existed *only on this
machine*, written down nowhere. A fresh laptop would have started up fine and then the SLA
schedulers would have failed at runtime with "table doesn't exist". It is now captured and
reproducible.
**Found while doing this:** the typo `USER_LOOGED_OUT` in the audit log enum (recorded as E11).
Reproduced as-is, because the baseline copies reality — fixing it needs the Java enum changed
*and* existing rows rewritten, together.

---

### AuthService + UserService — V2__seed_roles.sql
**Date:** 2026-08-31

**What it does (plain English):**
Puts the three role rows (ROLE_ADMIN, ROLE_ENGINEER, ROLE_SUPERVISOR) into the `roles` table of
both services.

**Why we needed it:**
These rows were never optional — they were just undocumented. They existed only because somebody
typed them in by hand once, on one machine.

- **AuthService:** `RegisteringUserService` does `findByRolename(ROLE_ENGINEER).orElseThrow()`.
  Empty roles table = every signup fails, loudly.
- **UserService:** `UserEventConsumer` does the same lookup when a `user.registered` Kafka event
  arrives. Empty roles table = signup appears to SUCCEED (AuthService saved the user and published
  the event), but the consumer throws and the user never appears in UserService. Nobody is told.
  The two databases silently drift apart. This one is nastier precisely because it is quiet.

**THE KEY LESSON IN THIS MIGRATION — why `INSERT IGNORE` and not plain `INSERT`:**

This is the first migration that actually EXECUTES on our existing databases. V1 was skipped
(it is the baseline), but V2 sits above the baseline, so Flyway runs it for real — against tables
that already contain these exact three rows.

A plain `INSERT` would hit the UNIQUE constraint on `rolename`, throw a duplicate-key error, mark
the migration FAILED, and stop the service from starting until somebody repaired it by hand.

`INSERT IGNORE` says "skip rows that would violate a unique constraint":

```
Existing database (rows already there) -> all three skipped, nothing changes
Fresh database    (empty table)        -> all three inserted
```

One file, correct in both situations. **This is the normal shape of a data migration** — it has to
be safe to run against a database that already has the data.

**Anything to watch out for:**
`INSERT IGNORE` downgrades *other* errors to warnings too, not only duplicates. That is fine here
because these are three fixed, hard-coded rows with no variable input — there is no other
realistic way for it to fail. Do not reach for `INSERT IGNORE` on inserts containing user data.

The `id` values may differ between the two services (AUTO_INCREMENT assigns them in insert order).
That is harmless: nothing compares role ids across services — only role *names* travel between
them in Kafka events.

---

### Ai-Service — V1__baseline.sql  (the PostgreSQL one)
**Date:** 2026-09-01

**What it does:** Baselines 3 extensions and 2 tables. This is the only service on PostgreSQL, so
it differs from the other five in real ways.

**Different from the MySQL baselines:**
- **Extensions.** PostgreSQL has add-ons that must be enabled before use. `vector` is the reason
  the container runs the pgvector image rather than plain postgres — without it the vector column
  type does not exist. MySQL has no equivalent, so nothing like this appeared in the other five.
- **Real `uuid` type.** Ids are stored as `uuid`, readable directly in a SQL client. The MySQL
  services store the same Java UUID as `binary(16)`, needing `BIN_TO_UUID()` to read. Same Java
  type, two very different storage decisions made by the two databases.
- **Different Flyway module.** `flyway-database-postgresql` instead of `flyway-mysql`. Flyway 10+
  splits database support per module; using the wrong one fails with "Unsupported Database".

**The big catch — `resolved_incidents_vectors`:**
This table has **no `@Entity` class**. Spring AI's `PgVectorStore` reads and writes it directly,
and `AiConfig` explicitly says `.initializeSchema(false)` — "do not create this, it already
exists". So before this baseline:

- Hibernate did not know about it (no entity)
- Spring AI would not create it (initializeSchema false)
- Nothing in the project described it

It existed only because someone typed `CREATE TABLE` by hand once, on one machine. **Exactly the
same situation as ReportingService's `shedlock` table** — a fresh laptop would have started
Ai-Service happily and then failed the moment anyone requested a resolution suggestion.

**A plan change worth recording:** the original intent was to split the vector table into its own
`V2`. That was reconsidered for two reasons. First, consistency — `shedlock` went into
ReportingService's V1, and this is the same kind of table. Second, practicality — a V2 would
*execute* against the existing database, where the table already exists, so it would have needed
`CREATE TABLE IF NOT EXISTS` to avoid failing. Everything in V1 means nothing executes at all.

**Found while doing this:** no index on the `embedding` column, and pgvector's index types cap out
around 2000 dimensions while this column is 3072 — so it cannot simply be added. Recorded as E12.
Also two probably-unused extensions (E13).

**Watch out:** `ddl-auto=validate` only checks tables that have an `@Entity`. It validates
`ai_insight` and ignores `resolved_incidents_vectors` entirely — which is precisely why that table
had to go into Flyway. Nothing else was watching it.

---

**Template for each entry — copy this shape:**

```
### <Service> — V<n>__<name>.sql
**Date:**
**What it does (plain English):**
**Why we needed it:**
**The SQL, explained line by line:**
**Anything to watch out for:**
```

---

## Decisions we made and why

### Why Flyway, not Liquibase — 2026-08-29
Flyway migrations are plain `.sql` files. Liquibase uses XML/YAML changelogs that then generate
SQL. For a project where the whole point is *being able to read and review what changes*, plain
SQL is easier to understand and there's no translation layer to get wrong. Liquibase's extra
features (database-agnostic changelogs, built-in rollback) don't buy us anything here — we're not
switching database vendors, and rollback is a paid feature in Flyway anyway.

### Why we keep the existing local data — 2026-08-29
We deliberately chose **not** to wipe and recreate the local databases, even though that would be
the easier path. Learning how to baseline a database that already contains data is the whole
point — that's the situation any real project is in, and it's the part that goes wrong if you've
only ever practised on empty databases.
