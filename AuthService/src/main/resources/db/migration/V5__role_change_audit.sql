-- Issue 67 (2026-10-21): a record of every role change.
--
-- One row per change: whose roles changed, who did it, the roles before and after, and when.
--   changed_by = NULL means the system did it (the first-admin bootstrap at start-up).
--   source     = 'ADMIN_REQUEST' (PUT /roles/{username}/update) or 'BOOTSTRAP'.
-- The row is saved in the same transaction as the role change: both or neither.
--
-- Never edit this file after it has run (Flyway checks it). Change = a new V-file.

CREATE TABLE `role_change_audit` (
  `id`             bigint       NOT NULL AUTO_INCREMENT,
  `target_user_id` binary(16)   NOT NULL,
  `changed_by`     binary(16)   DEFAULT NULL,
  `old_roles`      varchar(255) NOT NULL,
  `new_roles`      varchar(255) NOT NULL,
  `source`         varchar(20)  NOT NULL,
  `changed_at`     datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_role_change_target_time` (`target_user_id`, `changed_at`)
);
