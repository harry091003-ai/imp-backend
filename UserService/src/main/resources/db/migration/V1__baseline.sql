-- =============================================================================
-- V1__baseline.sql  -  UserService
--
-- WHAT THIS IS
-- A photo of UserService's database exactly as it looked on 2026-08-31, taken
-- by asking MySQL directly (mysqldump --no-data), not by reading the Java code.
-- Java entities and the real database can quietly drift apart, so we copy the
-- real thing.
--
-- WHEN IT RUNS
--   Existing database (yours)  -> does NOT run. Flyway just marks "already at V1".
--   Empty database (fresh PC)  -> DOES run, and builds all 3 tables.
--
-- GOLDEN RULE: once this file has actually executed anywhere, never edit it.
-- Make a new file (V2, V3...) instead.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- roles
-- Identical to AuthService's roles table, on purpose. Each service owns its own
-- copy of the data it needs - that is the point of microservices. This copy is
-- filled from the Kafka events AuthService publishes, not by sharing a database.
-- -----------------------------------------------------------------------------
CREATE TABLE `roles` (
  `id`       int NOT NULL AUTO_INCREMENT,
  `rolename` enum('ROLE_ADMIN','ROLE_ENGINEER','ROLE_SUPERVISOR') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjdhyvh8di85ai37phukfemdnx` (`rolename`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- users  -  the profile mirror
--
-- Notice what is MISSING: there is no `password` column, and that is deliberate.
-- AuthService owns credentials; this service only mirrors profile information it
-- receives over Kafka. If you ever find yourself wanting to add a password here,
-- something has gone wrong in the design.
--
-- Two things this table has that AuthService's does not:
--
--   `status`         - ACTIVE / INACTIVE / SUSPENDED. IncidentService checks this
--                      before letting somebody raise or be assigned an incident.
--
--   `supervisor_id`  - the engineer/supervisor hierarchy. It is a foreign key from
--                      this table back to ITSELF: one supervisor has many engineers,
--                      each engineer points at their one supervisor. That is why the
--                      constraint below references `users` while still inside the
--                      CREATE TABLE for `users` - perfectly legal, MySQL creates the
--                      table first and the constraint after.
-- -----------------------------------------------------------------------------
CREATE TABLE `users` (
  `userid`                binary(16)   NOT NULL,
  `username`              varchar(25)  NOT NULL,
  `email`                 varchar(100) NOT NULL,
  `status`                enum('ACTIVE','INACTIVE','SUSPENDED') DEFAULT NULL,
  `enabled`               bit(1)       NOT NULL,
  `account_locked`        tinyint(1)   NOT NULL,
  `failed_login_attempts` int          NOT NULL,
  `created_at`            datetime(6)  NOT NULL,
  `updated_at`            datetime(6)  NOT NULL,
  `last_login_at`         datetime(6)  DEFAULT NULL,
  `last_logout_at`        datetime(6)  DEFAULT NULL,
  `password_updated_at`   datetime(6)  DEFAULT NULL,
  `supervisor_id`         binary(16)   DEFAULT NULL,
  PRIMARY KEY (`userid`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  KEY `FKpgopks9vl2c3kw6rb5ivil2vo` (`supervisor_id`),
  CONSTRAINT `FKpgopks9vl2c3kw6rb5ivil2vo` FOREIGN KEY (`supervisor_id`) REFERENCES `users` (`userid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- user_roles  -  join table linking users to their roles
--
-- ORDER MATTERS: created LAST because its foreign keys need both `roles` and
-- `users` to already exist. mysqldump listed the tables alphabetically
-- (roles, user_roles, users), which would fail on an empty database - it only
-- worked in the dump because mysqldump switches foreign key checks off at the top.
-- -----------------------------------------------------------------------------
CREATE TABLE `user_roles` (
  `user_id` binary(16) NOT NULL,
  `role_id` int        NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FKh8ciramu9cc9q3qcqiv4ue8a6` (`role_id`),
  CONSTRAINT `FKh8ciramu9cc9q3qcqiv4ue8a6` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`userid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =============================================================================
-- DELIBERATELY NOT IN THIS FILE
--
-- 1. AUTO_INCREMENT=4 on `roles` - that is a fact about the 3 rows currently in
--    the table, not about its structure. A fresh database starts counting at 1.
--
-- 2. The mysqldump preamble (SET @OLD_CHARACTER_SET_CLIENT, FOREIGN_KEY_CHECKS=0
--    and friends). Those help a dump replay through the mysql command line tool.
--    Flyway uses a plain JDBC connection and does not need them. Each table still
--    pins its own charset and collation explicitly.
--
-- 3. Seeding the role rows. Data belongs in its own migration, kept separate from
--    schema. Same reasoning as AuthService.
-- =============================================================================
