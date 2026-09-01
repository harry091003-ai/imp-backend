-- =============================================================================
-- V1__baseline.sql  -  IncidentService
--
-- WHAT THIS IS
-- A photo of IncidentService's database exactly as it looked on 2026-08-31,
-- taken with mysqldump --no-data from the real running database.
--
-- WHEN IT RUNS
--   Existing database (yours)  -> does NOT run. Flyway marks "already at V1".
--   Empty database (fresh PC)  -> DOES run, and builds the table.
--
-- GOLDEN RULE: once this file has actually executed anywhere, never edit it.
-- Make a new file (V2, V3...) instead.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- incidents  -  the only table in this service
--
-- The three "who" columns (created_by, assigned_to, assigned_by) hold user ids,
-- but there is NO foreign key pointing at a users table - and that is correct,
-- not an oversight. Users live in a completely different database owned by
-- UserService. A database-level foreign key cannot cross that boundary. Instead
-- IncidentService calls UserService over HTTP to check a user is real and active.
-- That is the trade microservices make: you give up the database enforcing
-- referential integrity, and take on doing it yourself in code.
--
-- Every enum below is stored as MySQL's native enum type, holding the NAME
-- ('HIGH', 'OPEN'...) rather than a number. That matters: if the values were
-- stored as numbers, reordering the Java enum would silently change the meaning
-- of every existing row.
-- -----------------------------------------------------------------------------
CREATE TABLE `incidents` (
  `id`               binary(16)    NOT NULL,
  `title`            varchar(255)  NOT NULL,
  `description`      varchar(2000) NOT NULL,

  -- who is involved (ids only - see the note above about no foreign keys)
  `created_by`       binary(16)    NOT NULL,
  `assigned_to`      binary(16)    DEFAULT NULL,  -- null until a supervisor assigns it
  `assigned_by`      binary(16)    DEFAULT NULL,  -- null until a supervisor assigns it

  -- classification
  `severity`         enum('CRITICAL','HIGH','LOW','MEDIUM') DEFAULT NULL,
  `priority`         enum('CRITICAL','HIGH','LOW','MEDIUM') DEFAULT NULL,
  `incident_status`  enum('ASSIGNED','CLOSED','IN_PROGRESS','OPEN','REOPENED','RESOLVED') DEFAULT NULL,
  `category`         enum('API','CUSTOMER','DATABASE','HARDWARE','INFRASTRUCTURE','NETWORK','OTHER','SAFETY') DEFAULT NULL,
  `service_name`     enum('APIGATEWAY_SERVICE','AUTH_SERVICE','EMAIL_SERVICE','NOTIFICATION_SERVICE','OTHERS','PAYMENT_SERVICE','REPORTING_SERVICE','USER_SERVICE') DEFAULT NULL,
  `environment`      enum('DEVELOPMENT','LOCAL','OTHERS','PRODUCTION','STAGING') DEFAULT NULL,

  -- free text location fields, used for non-technical incidents
  `affected_source`  varchar(255)  DEFAULT NULL,  -- e.g. "DB Cluster A", "Wind Turbine #12"
  `location`         varchar(255)  DEFAULT NULL,  -- e.g. "Plant A", "Room 101"

  -- filled in when the incident is resolved
  `root_cause`       varchar(255)  DEFAULT NULL,
  `resolution_notes` varchar(255)  DEFAULT NULL,

  `created_at`       datetime(6)   NOT NULL,
  `updated_at`       datetime(6)   NOT NULL,

  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =============================================================================
-- SOMETHING WORTH KNOWING ABOUT THIS TABLE
--
-- It has NO indexes at all beyond the primary key. But IncidentRepository queries
-- by created_by, assigned_to and assigned_by on every "list my incidents" request,
-- so each of those makes MySQL read the entire table row by row.
--
-- Nobody notices with a few dozen test rows. It becomes the slowest thing in the
-- platform once the table is large.
--
-- NOT fixed here on purpose: this file must describe the database exactly as it
-- is today, or ddl-auto=validate will fail. Adding indexes is a real schema
-- change and deserves its own migration, written deliberately.
-- Logged in BUGS-TO-FIX-LATER.md.
--
-- DELIBERATELY NOT IN THIS FILE
--   The mysqldump preamble (SET @OLD_CHARACTER_SET_CLIENT etc). Those exist so a
--   dump can be replayed through the mysql command line tool. Flyway uses a plain
--   JDBC connection and does not need them. The table pins its own charset and
--   collation explicitly above.
-- =============================================================================
