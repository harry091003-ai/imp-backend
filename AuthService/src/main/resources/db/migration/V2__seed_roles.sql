-- =============================================================================
-- V2__seed_roles.sql  -  AuthService
--
-- WHY THIS FILE EXISTS
-- The three role rows are not optional decoration - signup breaks without them.
-- RegisteringUserService does:
--
--     roleRepository.findByRolename(RoleName.ROLE_ENGINEER).orElseThrow(...)
--
-- so if the roles table is empty, every single registration fails.
--
-- Until now those rows existed only because somebody typed them in by hand, on
-- this machine, and nothing recorded that. A fresh laptop would have got a
-- perfectly valid, perfectly empty schema - and a completely broken signup, with
-- nothing to explain why.
--
-- WHY IT IS A SEPARATE FILE FROM V1
-- V1 answers "what shape is the database". This answers "what must be in it".
-- Keeping them apart means you can read the schema history without wading through
-- data, and change one without touching the other.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- THE IMPORTANT DETAIL: why "INSERT IGNORE" and not plain "INSERT"
--
-- Unlike V1, this file DOES run on the existing database. V1 was skipped because
-- it sits at the baseline version, but V2 is above it, so Flyway executes it here
-- for real - on a database whose roles table already contains these three rows.
--
-- A plain INSERT would hit the UNIQUE constraint on `rolename`, throw a duplicate
-- key error, and mark the migration as FAILED. The service would then refuse to
-- start until somebody repaired it by hand.
--
-- INSERT IGNORE tells MySQL: "if a row would violate a unique constraint, skip it
-- quietly and carry on."  So:
--
--   Your database (rows already there) -> all three skipped, nothing changes
--   A fresh database (empty table)     -> all three inserted
--
-- One file, correct in both cases. This is the normal shape of a data migration:
-- it has to be safe to run against a database that already has the data.
--
-- The trade-off worth knowing: INSERT IGNORE downgrades OTHER errors to warnings
-- too, not just duplicates. That is acceptable here because this is three fixed,
-- hard-coded rows with no user input - there is no other realistic way for it to
-- fail. Do not reach for INSERT IGNORE on inserts with variable data.
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `roles` (`rolename`) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_ENGINEER'),
    ('ROLE_SUPERVISOR');


-- =============================================================================
-- A NOTE ON THE id VALUES
--
-- `id` is AUTO_INCREMENT, so a fresh database will number these 1, 2, 3 in the
-- order written above, while your existing database numbered them whenever they
-- happened to be typed in - possibly a different order.
--
-- That difference is harmless. Nothing in the code looks a role up by id; every
-- lookup goes through findByRolename(...). The ids only need to be consistent
-- WITHIN one database, so that user_roles.role_id points at the right row there -
-- and they always are.
-- =============================================================================
