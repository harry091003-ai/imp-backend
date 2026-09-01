-- =============================================================================
-- V1__baseline.sql  -  ReportingService
--
-- WHAT THIS IS
-- A photo of ReportingService's database exactly as it looked on 2026-08-31,
-- taken with mysqldump --no-data from the real running database.
--
-- This is the biggest baseline in the project: 7 tables. Six are normal Hibernate
-- entities. The seventh (`shedlock`) is NOT - read its note, it is the one most
-- likely to be lost otherwise.
--
-- WHEN IT RUNS
--   Existing database (yours)  -> does NOT run. Flyway marks "already at V1".
--   Empty database (fresh PC)  -> DOES run, and builds all 7 tables.
--
-- GOLDEN RULE: once this file has actually executed anywhere, never edit it.
-- Make a new file (V2, V3...) instead.
--
-- NOTE ON ORDERING: unlike AuthService and UserService, none of these tables have
-- foreign keys to each other, so creation order does not technically matter. They
-- are grouped below by what they are for, to make the file readable.
-- =============================================================================


-- =============================================================================
-- GROUP 1: the read model  -  rebuilt from Kafka events, not written to directly
-- =============================================================================

-- -----------------------------------------------------------------------------
-- incident_report
-- One row per incident, assembled from the events IncidentService publishes.
-- This service never talks to IncidentService's database - it listens to events
-- and keeps its own copy, shaped for reporting instead of for editing.
--
-- Notice `incident_id` is the PRIMARY KEY, not a generated id. The incident's own
-- id is reused, which makes re-processing the same event safely overwrite the same
-- row rather than creating a second one.
--
-- The 8 indexes at the bottom are not decoration - each one matches a real query
-- pattern (the SLA schedulers scan for breached-but-unpublished rows every 70
-- seconds, and the dashboards filter by status/severity/date).
-- -----------------------------------------------------------------------------
CREATE TABLE `incident_report` (
  `incident_id`                    binary(16)   NOT NULL,
  `title`                          varchar(255) DEFAULT NULL,
  `description`                    varchar(255) DEFAULT NULL,

  `severity`                       varchar(255) DEFAULT NULL,
  `priority`                       varchar(255) DEFAULT NULL,
  `status`                         varchar(255) DEFAULT NULL,
  `category`                       varchar(255) DEFAULT NULL,
  `service_name`                   varchar(255) DEFAULT NULL,

  `created_by`                     binary(16)   DEFAULT NULL,
  `assigned_to`                    binary(16)   DEFAULT NULL,
  `assigned_by`                    binary(16)   DEFAULT NULL,
  `resolved_by`                    binary(16)   DEFAULT NULL,

  `created_at`                     datetime(6)  DEFAULT NULL,
  `assigned_at`                    datetime(6)  DEFAULT NULL,
  `resolved_at`                    datetime(6)  DEFAULT NULL,
  `updated_at`                     datetime(6)  DEFAULT NULL,

  -- SLA tracking: deadline, whether it was missed, and whether we already told
  -- anyone. The "event_published" flags exist so a breach is announced once, not
  -- once per scheduler run.
  `assignment_deadline`            datetime(6)  DEFAULT NULL,
  `assignment_sla_breached`        bit(1)       NOT NULL,
  `assignment_sla_event_published` bit(1)       NOT NULL,
  `assignment_risk_level`          enum('CRITICAL','HIGH','NONE') DEFAULT NULL,
  `assignment_risk_score`          double       DEFAULT NULL,

  `resolution_deadline`            datetime(6)  DEFAULT NULL,
  `resolution_sla_breached`        bit(1)       NOT NULL,
  `resolution_sla_event_published` bit(1)       NOT NULL,
  `resolution_risk_level`          enum('CRITICAL','HIGH','NONE') DEFAULT NULL,
  `resolution_risk_score`          double       DEFAULT NULL,

  -- pre-calculated durations, so reports do not recompute them every time
  `time_to_assign_seconds`         bigint       DEFAULT NULL,
  `resolution_time_seconds`        bigint       DEFAULT NULL,

  PRIMARY KEY (`incident_id`),
  KEY `idx_status_severity_createdAt` (`status`,`severity`,`created_at`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_service_name` (`service_name`),
  KEY `idx_status` (`status`),
  KEY `idx_assignmentSlaBreached_assignmentSlaEventPublished` (`assignment_sla_breached`,`assignment_sla_event_published`),
  KEY `idx_resolutionSlaBreached_resolutionSlaEventPublished` (`resolution_sla_breached`,`resolution_sla_event_published`),
  KEY `idx_assignedAt_assignmentSlaBreached_assignmentDeadline` (`assigned_at`,`assignment_sla_breached`,`assignment_deadline`),
  KEY `idx_resolvedAt_resolutionSlaBreached_status_resolutionDeadline` (`resolved_at`,`resolution_sla_breached`,`status`,`resolution_deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- incident_audit_log
-- Append-only history: every event that ever touched an incident, kept forever.
-- incident_report answers "what does this incident look like now";
-- this table answers "how did it get that way".
-- -----------------------------------------------------------------------------
CREATE TABLE `incident_audit_log` (
  `id`           binary(16)   NOT NULL,
  `incident_id`  binary(16)   DEFAULT NULL,
  `event_id`     binary(16)   DEFAULT NULL,
  `event_type`   enum('INCIDENT_ASSIGNED','INCIDENT_CREATED','INCIDENT_METADATA_UPDATED','INCIDENT_REOPENED','INCIDENT_RESOLVED','INCIDENT_STATUS_UPDATED','USER_LOGGED_IN','USER_LOOGED_OUT','USER_REGISTERED','USER_UPDATED') DEFAULT NULL,
  `old_value`    varchar(255) DEFAULT NULL,
  `new_value`    varchar(255) DEFAULT NULL,
  `performed_by` binary(16)   DEFAULT NULL,
  `timestamp`    datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Note the typo USER_LOOGED_OUT (should be USER_LOGGED_OUT). It is spelled that
-- way in the Java enum and therefore in the database. Reproduced exactly, because
-- this file copies reality. Fixing it means changing the enum AND migrating any
-- existing rows - a real change for its own migration, not a quiet edit here.


-- =============================================================================
-- GROUP 2: pre-computed statistics  -  updated as events arrive
-- =============================================================================

-- -----------------------------------------------------------------------------
-- daily_metrics
-- One row per calendar day. The date itself is the primary key, which naturally
-- prevents two rows ever existing for the same day.
-- -----------------------------------------------------------------------------
CREATE TABLE `daily_metrics` (
  `date`                         date   NOT NULL,
  `total_incidents`              bigint NOT NULL,
  `open_incidents`               bigint NOT NULL,
  `resolved_incidents`           bigint NOT NULL,
  `high_severity_count`          bigint NOT NULL,
  `assignmentsla_breached_count` bigint NOT NULL,
  `resolutionsla_breached_count` bigint NOT NULL,
  `avg_resolution_time`          double NOT NULL,
  PRIMARY KEY (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- engineer_performance_stats
-- How good is this engineer, at this category, in this service. Ai-Service reads
-- this over HTTP when recommending who to assign an incident to.
--
-- The UNIQUE KEY on (engineer_id, category, service_name) is what makes
-- "one row per engineer per category per service" true at the database level,
-- rather than merely intended.
-- -----------------------------------------------------------------------------
CREATE TABLE `engineer_performance_stats` (
  `id`                          binary(16)   NOT NULL,
  `engineer_id`                 binary(16)   DEFAULT NULL,
  `category`                    varchar(255) DEFAULT NULL,
  `service_name`                varchar(255) DEFAULT NULL,
  `total_incidents_resolved`    bigint       NOT NULL,
  `avg_resolution_time_seconds` double       NOT NULL,
  `sla_success_rate`            double       NOT NULL,
  `avg_sla_utilization`         double       DEFAULT NULL,
  `expertise_score`             double       NOT NULL,
  `last_updated`                datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uc_engineer_category_service` (`engineer_id`,`category`,`service_name`),
  KEY `idx_engineer_category_service` (`engineer_id`,`category`,`service_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- engineer_workload
-- How busy is this engineer right now. One row per engineer, enforced by the
-- unique key on engineer_id.
-- -----------------------------------------------------------------------------
CREATE TABLE `engineer_workload` (
  `id`                    binary(16)   NOT NULL,
  `engineer_id`           binary(16)   NOT NULL,
  `active_incident_count` int          NOT NULL,
  `available`             bit(1)       NOT NULL,
  `shift_name`            varchar(255) DEFAULT NULL,
  `last_assigned_at`      datetime(6)  DEFAULT NULL,
  `updated_at`            datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt0kd6ixx231ux96p1hddjsnry` (`engineer_id`),
  KEY `idx_engineer_id` (`engineer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =============================================================================
-- GROUP 3: plumbing  -  not business data, but the service breaks without it
-- =============================================================================

-- -----------------------------------------------------------------------------
-- processed_event
-- The duplicate-event guard. Before handling a Kafka event, the service inserts
-- its event_id here. If the insert fails because the id is already present, this
-- event has been seen before and is skipped.
--
-- Same trick as NotificationService: let the database's primary key be the
-- referee, instead of checking first and inserting second (which two consumers
-- could both pass at the same instant).
-- -----------------------------------------------------------------------------
CREATE TABLE `processed_event` (
  `event_id`     binary(16)  NOT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- shedlock   *** READ THIS ONE ***
--
-- This table is NOT a Hibernate entity. There is no @Entity class for it anywhere
-- in the codebase. It belongs to the ShedLock library, which uses it to make sure
-- that when several copies of ReportingService run at once, only ONE of them runs
-- each scheduled job (otherwise every instance would publish the same SLA breach
-- events).
--
-- WHY IT MATTERS THAT IT IS IN THIS FILE:
-- Because no entity exists for it, ddl-auto=validate does not check it, and
-- Hibernate never created it - it was made by hand at some point. Which means that
-- before today it existed ONLY on this machine, recorded nowhere. A fresh laptop
-- would have started up perfectly happily, and then the SLA schedulers would have
-- failed at runtime with "table doesn't exist".
--
-- Putting it here is the single most valuable line of this whole baseline: an
-- undocumented, hand-made piece of setup is now written down and reproducible.
-- -----------------------------------------------------------------------------
CREATE TABLE `shedlock` (
  `name`       varchar(64)  NOT NULL,   -- the job name, e.g. "slaScheduler"
  `lock_until` timestamp    NULL DEFAULT NULL,
  `locked_at`  timestamp    NULL DEFAULT NULL,
  `locked_by`  varchar(255) DEFAULT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =============================================================================
-- DELIBERATELY NOT IN THIS FILE
--   The mysqldump preamble (SET @OLD_CHARACTER_SET_CLIENT, FOREIGN_KEY_CHECKS=0
--   and friends). Those exist so a dump can be replayed through the mysql command
--   line tool. Flyway uses a plain JDBC connection and does not need them. Every
--   table pins its own charset and collation explicitly.
-- =============================================================================
