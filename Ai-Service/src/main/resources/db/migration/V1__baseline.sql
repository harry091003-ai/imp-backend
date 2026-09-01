-- =============================================================================
-- V1__baseline.sql  -  Ai-Service   (PostgreSQL, not MySQL)
--
-- WHAT THIS IS
-- A photo of Ai-Service's database exactly as it looked on 2026-09-01, taken with
-- pg_dump --schema-only from the real running container, not generated from the
-- Java code.
--
-- WHEN IT RUNS
--   Existing database (yours)  -> does NOT run. Flyway marks "already at V1".
--   Empty database (fresh PC)  -> DOES run, and builds everything below.
--
-- GOLDEN RULE: once this file has actually executed anywhere, never edit it.
-- Make a new file (V2, V3...) instead.
--
-- WHY BOTH TABLES ARE IN THIS ONE FILE
-- Earlier the plan was to split the vector table into its own migration. On
-- reflection that was wrong, for the same reason ReportingService's `shedlock`
-- table belongs in ITS baseline: a baseline's job is to describe the whole
-- database as it exists. Splitting would also have meant V2 running against your
-- existing database, where both tables already exist - needing
-- CREATE TABLE IF NOT EXISTS to avoid failing. Keeping it in V1 means nothing
-- executes here at all, which is simpler and safer.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- EXTENSIONS  -  these must exist BEFORE the tables below
--
-- An extension is an add-on that teaches PostgreSQL new abilities. MySQL has no
-- direct equivalent, which is why nothing like this appeared in the other five
-- baselines.
--
--   vector      REQUIRED. Adds the `vector` data type and the similarity
--               operators the RAG search depends on. Without it the
--               resolved_incidents_vectors table below cannot even be created.
--               This is the whole reason the container runs the pgvector image
--               instead of plain postgres.
--
--   uuid-ossp   Functions for generating UUIDs inside the database.
--   hstore       for storing  key and value in a single column .
--
-- The last two are present in the real database, so they are reproduced here -
-- that is this file's job. Both may well be unused by our code (application ids
-- are generated in Java, and metadata below uses jsonb rather than hstore). They
-- likely arrived with an image template or were enabled speculatively. Left in
-- place on purpose: this file copies reality, and removing an extension is a
-- deliberate change for its own migration, not a guess made in a baseline.
--
-- "IF NOT EXISTS" makes these safe to run against a database that already has
-- them - which matters on a fresh machine where the pgvector image may pre-enable
-- some of them.
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS vector      WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS hstore      WITH SCHEMA public;


-- -----------------------------------------------------------------------------
-- ai_insight  -  the ONLY table here with a Java @Entity class
--
-- Holds what the AI concluded about one incident: its guessed category, severity,
-- priority and service, how confident it was, and whether that guess disagreed
-- with what the human who raised the incident chose (`conflict`).
--
-- Note `incident_id uuid` - PostgreSQL has a real built-in uuid type, so ids are
-- readable directly in a SQL client. The MySQL services store the same kind of id
-- as binary(16), which needs BIN_TO_UUID() to read. Same Java UUID, two very
-- different storage choices made by the two databases.
--
-- Because this table has an @Entity, ddl-auto=validate WILL check it on startup.
-- -----------------------------------------------------------------------------
CREATE TABLE public.ai_insight (
    incident_id     uuid NOT NULL,
    ai_category     character varying(255),
    ai_severity     character varying(255),
    ai_priority     character varying(255),
    ai_service_name character varying(255),
    confidence      double precision,
    conflict        boolean NOT NULL,
    summary         character varying(2000)
);

ALTER TABLE ONLY public.ai_insight
    ADD CONSTRAINT ai_insight_pkey PRIMARY KEY (incident_id);


-- -----------------------------------------------------------------------------
-- resolved_incidents_vectors   *** THE IMPORTANT ONE ***
--
-- This is the RAG memory: every resolved incident, stored as a mathematical
-- "meaning" vector, so a NEW incident can be matched against similar past ones
-- and their resolutions offered as suggestions.
--
-- IT HAS NO @Entity CLASS. Spring AI's PgVectorStore reads and writes it directly,
-- and AiConfig deliberately says .initializeSchema(false) - meaning "do not create
-- this table for me, it already exists". So before today:
--
--     * Hibernate did not know about it  (no entity)
--     * Spring AI would not create it    (initializeSchema false)
--     * Nothing in the project described it
--
-- It existed only because somebody typed CREATE TABLE by hand once, on one
-- machine. Exactly the same situation as ReportingService's `shedlock` table.
-- A fresh laptop would have started Ai-Service perfectly happily, and then failed
-- the moment anyone asked for a resolution suggestion.
--
-- Writing it down here is the single most valuable part of this baseline.
--
-- THE COLUMNS - these names are NOT free choices. Spring AI's PgVectorStore
-- expects exactly this shape, so renaming any of them breaks RAG at runtime:
--   id         the vector's own identifier
--   content    the original text that was turned into the vector
--   metadata   jsonb - extra fields (incident id, category...) kept alongside
--   embedding  the vector itself: 3072 numbers describing the text's meaning
--
-- 3072 matches the output size of the gemini-embedding-001 model configured in
-- application.properties, and the .dimensions(3072) setting in AiConfig. All
-- three must agree - a mismatch fails at runtime, not at startup.
-- -----------------------------------------------------------------------------
CREATE TABLE public.resolved_incidents_vectors (
    id        uuid NOT NULL,
    content   text,
    metadata  jsonb,
    embedding public.vector(3072)
);

ALTER TABLE ONLY public.resolved_incidents_vectors
    ADD CONSTRAINT resolved_incidents_vectors_pkey PRIMARY KEY (id);


-- =============================================================================
-- WORTH KNOWING: there is no index on the `embedding` column
--
-- Every similarity search therefore scans the whole table and computes the
-- distance to each row one by one. With a small number of resolved incidents this
-- is genuinely fine - and it is also exact, where the index types below are
-- approximate.
--
-- But it cannot simply be "fixed" by adding an index, and the reason is worth
-- knowing before anyone tries: pgvector's hnsw and ivfflat indexes support up to
-- about 2000 dimensions. This column has 3072. So the usual advice does not apply
-- here - an attempt to add one is expected to be rejected outright.
--
-- Options if this ever becomes slow: store as `halfvec` (which supports more
-- dimensions for indexing), or configure gemini-embedding-001 to emit fewer
-- dimensions (it supports smaller sizes) and rebuild the vectors.
--
-- Both are real changes needing their own migration AND regenerating every
-- embedding through the Gemini API. Recorded in BUGS-TO-FIX-LATER.md so the
-- trade-off is written down rather than rediscovered under pressure.
-- =============================================================================
