-- =============================================================================
-- V2__seed_roles.sql  -  UserService
--
-- WHY THIS FILE EXISTS
-- Same three rows as AuthService's V2, but needed for a different reason.
--
-- UserService does not handle signup. It listens to Kafka. When AuthService
-- publishes a "user.registered" event, UserEventConsumer turns the role NAMES in
-- that event back into Role rows from this database:
--
--     roleRepository.findByRolename(RoleName.valueOf(role))
--                   .orElseThrow(() -> new RuntimeException("Role Not Found:" + role))
--
-- RoleEventConsumer does the same thing when roles are updated.
--
-- So on a fresh machine with an empty roles table, the failure is quieter and
-- nastier than AuthService's. Signup would appear to SUCCEED - AuthService saves
-- the user and publishes the event happily - but this consumer would throw while
-- processing it, and the user would simply never appear in UserService. No error
-- reaches whoever signed up. The two databases silently drift apart.
--
-- That is exactly the kind of setup step that lives only in somebody's memory
-- until it is written into a migration.
--
-- WHY IT IS A SEPARATE FILE FROM V1
-- V1 answers "what shape is the database". This answers "what must be in it".
-- =============================================================================


-- -----------------------------------------------------------------------------
-- INSERT IGNORE, not plain INSERT - for the same reason as AuthService's V2.
--
-- V1 was skipped on this database (it is the baseline), but V2 is above the
-- baseline, so Flyway runs it here for real - against a roles table that already
-- has these three rows. A plain INSERT would hit the UNIQUE constraint on
-- `rolename`, fail the migration, and stop the service from starting.
--
--   Existing database -> all three skipped, nothing changes
--   Fresh database    -> all three inserted
--
-- Note: these rows must match AuthService's exactly. The two services keep
-- separate databases on purpose, but they are describing the same three roles,
-- and role NAMES are what travel between them in Kafka events. If the two lists
-- ever disagree, events start failing to process.
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `roles` (`rolename`) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_ENGINEER'),
    ('ROLE_SUPERVISOR');


-- =============================================================================
-- A NOTE ON THE id VALUES
--
-- The `id` numbers here do NOT need to match AuthService's. Nothing compares ids
-- across services - only role names travel between them, inside Kafka events.
-- Within this database the ids just need to be self-consistent, which
-- AUTO_INCREMENT guarantees.
-- =============================================================================
