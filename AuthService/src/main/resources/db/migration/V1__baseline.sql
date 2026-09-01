-- =============================================================================


-- V1__baseline.sql — explained in easy words
-- 🔹 Top comment block
-- This file = a photo of your database as it looks today.
-- It was made by asking MySQL directly ("show me your tables"), not by looking at your Java code.
-- Why not just use Java code? Because Java code and real database can quietly become different over time. Copying the real database avoids that risk.
-- Two different situations, one file:
-- Your database (already has tables) → this file does NOT run. Flyway just marks it as "already done."
-- A brand-new empty database → this file DOES run and builds all 3 tables.
-- Golden rule: once this file has been used even once, never edit it again. If you need a change, make a new file (V2, V3, etc.)

-- =============================================================================


-- -----------------------------------------------------------------------------
-- roles table
-- Just a small list of allowed roles: Admin, Engineer, Supervisor.
-- The app looks up a role by name during signup — if this table is empty, signup breaks.
-- So somebody must manually add these 3 rows later (that job is saved for V2, not this file).
-- -----------------------------------------------------------------------------
-- Quick note: about those auto-generated names
-- Names like 'UKjdhyvh8di85ai37phukfemdnx' were created by Hibernate itself, not typed by any person.
-- This happened earlier, back when ddl-auto=update was building your tables automatically.
-- Since we are now copying an already-existing database (via mysqldump), we have to keep those exact same ugly names — so the old database and any new database stay identical.
-- If instead we were building this table from zero ourselves (writing our own CREATE TABLE SQL, not copying from an existing DB) — then yes, we could pick nice, readable names ourselves, like:
--     sql
--  CONSTRAINT uk_roles_rolename UNIQUE (rolename)

CREATE TABLE `roles` (
  `id`       int NOT NULL AUTO_INCREMENT,
  `rolename` enum('ROLE_ADMIN','ROLE_ENGINEER','ROLE_SUPERVISOR') NOT NULL,
  PRIMARY KEY (`id`),
  -- Hibernate-generated constraint name. Kept exactly as-is on purpose: the
  -- existing database already has this name, and a fresh database must end up
  -- identical to it. Renaming it to something readable would make the two diverge.
  UNIQUE KEY `UKjdhyvh8di85ai37phukfemdnx` (`rolename`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- users table
-- userid
-- userid is stored as binary, not plain text
-- Your Java code sees it as a normal UUID (readable text) But Hibernate secretly stores it as raw bytes in MySQL (to save space).
-- So if you open the table directly, the ID looks like garbage — you need a special MySQL function to read it properly.
-- enable and account_locked
-- enabled and account_locked are two different data types, even though both mean true/false.We are not fixing it here — this file must copy reality exactly as it is.
-- It will be fixed properly later, in its own separate file.
-- -----------------------------------------------------------------------------
CREATE TABLE `users` (
  `userid`                binary(16)   NOT NULL,
  `username`              varchar(25)  NOT NULL,
  `email`                 varchar(100) NOT NULL,
  `password`              varchar(255) NOT NULL,   -- BCrypt hash, never plain text
  `enabled`               bit(1)       NOT NULL,
  `account_locked`        tinyint(1)   NOT NULL,
  `failed_login_attempts` int          NOT NULL,
  `token_version`         int          NOT NULL,   -- bumped on logout/password change to invalidate old JWTs
  `created_at`            datetime(6)  NOT NULL,
  `updated_at`            datetime(6)  NOT NULL,
  `last_login_at`         datetime(6)  DEFAULT NULL,
  `last_logout_at`        datetime(6)  DEFAULT NULL,
  `password_updated_at`   datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`userid`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- user_roles table
--   This table just connects users and roles together (one user can have many roles).
--   Why table order matters: this table depends on both other tables already existing.
-- So roles and users must be created before this one, or the database will complain.
-- -----------------------------------------------------------------------------
CREATE TABLE `user_roles` (
  `user_id` binary(16) NOT NULL,
  `role_id` int        NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FKh8ciramu9cc9q3qcqiv4ue8a6` (`role_id`),
  CONSTRAINT `FKh8ciramu9cc9q3qcqiv4ue8a6` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`), -- similarly here also
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`userid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =============================================================================
-- DELIBERATELY NOT IN THIS FILE
--
-- 1. AUTO_INCREMENT=4 on `roles`. The dump included it because the real table has
--    3 rows. That is a data fact, not a schema fact - a fresh database should
--    start counting from 1.
--
-- 2. The mysqldump session-variable preamble (SET @OLD_CHARACTER_SET_CLIENT etc).
--    Those exist so the dump can be replayed through the mysql command-line
--    client. Flyway runs statements over a normal JDBC connection and does not
--    need them. Each table pins its own charset and collation explicitly above,
--    so nothing is lost.
--
-- 3. Seeding the three role rows. Data belongs in its own migration, separate
--    from schema, so that "what shape is the database" and "what is in it" stay
--    independent. That will be V2 - and it matters, because signup fails without
--    those rows.
-- =============================================================================
