-- Known issue 55 + issue 6 (2026-10-14): the account lockout.
--
-- A locked account used to stay locked FOREVER (nothing ever unlocked it).
-- This column holds WHEN the lock ends. After that time the user may try again.
--   NULL = no end time.
--
-- Never edit this file after it has run (Flyway checks it). Change = a new V-file.

ALTER TABLE `users` ADD COLUMN `locked_until` datetime(6) DEFAULT NULL;
