-- =============================================================================
-- V2__sla_notifications.sql  -  NotificationService
--
-- WHY THIS FILE EXISTS  (SLA notifications, step 4 - 2026-10-17)
-- SLA alerts are going to be saved in notification_log (no email: a log line + a row), and every person must be
-- able to ask "show MY notifications". The table needs 4 changes for that:
--
--   1. recipient_id  - the user id of the person told. Until now only the EMAIL was saved, so there was no way to
--                      ask "which rows are mine?". Old rows keep NULL here (decision: leave old rows as they are).
--   2. event_type    - MySQL only accepts the words in this list. 4 new SLA words are ADDED AT THE END.
--   3. status        - new word RECORDED = "saved and visible in the app, no email sent". (SUCCESS used to be
--                      written with the message "Email Successfully sent", which was never true - issue 16.)
--   4. the old CHECK (event_type between 0 and 7) is DROPPED. Hibernate generated it long ago, counting positions
--      0-7. MySQL counts enum positions from 1, so it could already refuse the 8th type (USER_UPDATED - see the
--      note at the bottom of V1), and it would surely refuse the 4 new ones (positions 9-12).
--   + an index on (recipient_id, created_at), so "my newest notifications" stays fast as the table grows.
--
-- WHY THE NEW WORDS GO AT THE END
-- MySQL stores an enum value as its POSITION in the list (1, 2, 3 ...). Adding at the end leaves every old row
-- meaning exactly what it meant. Inserting in the middle would silently change the meaning of old rows.
-- The old 8 types and 3 statuses below are copied from V1 in the SAME order (checked by NotificationMigrationV2Test).
--
-- WHY IT IS ONE SINGLE STATEMENT
-- MySQL cannot roll back a change to a table's shape. With two statements, a failure in the second would leave the
-- table half-changed. One ALTER TABLE is applied completely, or not at all.
--
-- Existing rows: unchanged. They get recipient_id = NULL.
-- Needs MySQL 8.0.16 or newer (DROP CHECK).
-- GOLDEN RULE: once this file has run anywhere, never edit it. Make a V3 instead.
-- =============================================================================

ALTER TABLE `notification_log`
  DROP CHECK `notification_log_chk_1`,
  ADD COLUMN `recipient_id` binary(16) DEFAULT NULL AFTER `recipient_name`,
  MODIFY COLUMN `event_type` enum('INCIDENT_ASSIGNED','INCIDENT_CREATED','INCIDENT_METADATA_UPDATED','INCIDENT_REOPENED','INCIDENT_RESOLVED','INCIDENT_STATUS_UPDATED','USER_REGISTERED','USER_UPDATED','SLA_ASSIGNMENT_BREACHED','SLA_RESOLUTION_BREACHED','SLA_ASSIGNMENT_AT_RISK','SLA_RESOLUTION_AT_RISK') DEFAULT NULL,
  MODIFY COLUMN `status` enum('FAILED','PENDING','SUCCESS','RECORDED') DEFAULT NULL,
  ADD INDEX `idx_notification_recipient_created` (`recipient_id`, `created_at`);
