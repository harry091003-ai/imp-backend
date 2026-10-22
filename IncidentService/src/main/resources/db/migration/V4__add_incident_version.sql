-- Issue 64 (bug 3, 2026-10-22): optimistic locking on incidents.
--
-- Two people could save the same incident at the same moment, and the second save silently
-- overwrote the first. Now every save says "update this row only if its version is still N"
-- and makes it N+1. If someone else saved first, the version no longer matches and the save
-- is refused (the caller gets 409 CONCURRENT_UPDATE).
--
-- DEFAULT 0: every existing incident starts at version 0, so nothing else has to change.
--
-- Never edit this file after it has run (Flyway checks it). Change = a new V-file.

ALTER TABLE `incidents` ADD COLUMN `version` bigint NOT NULL DEFAULT 0;
