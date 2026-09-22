-- =============================================================================
-- V2__widen_root_cause_and_resolution_notes.sql  -  IncidentService
--
-- WHY THIS FILE EXISTS  (Track B4, step 2)
--
-- root_cause and resolution_notes were varchar(255) in V1 - copied from whatever
-- Hibernate had created with no length set. But UpdateIncidentStatus_RequestDTO
-- already allowed up to 2000 characters (@Size(max = 2000)) on both fields.
--
-- So a 300-character root cause passed our own validation, then MySQL rejected
-- it anyway, and the client got a confusing 409 "conflicts with existing data" -
-- not because anything conflicted, but because the column was too small.
--
-- THE CHOICE: widen the column, not shrink the DTO limit
--
-- These two fields hold written explanations from an engineer - "what went
-- wrong" and "how we fixed it". 255 characters is one short sentence. Ai-Service
-- also turns resolved incidents into text for its RAG search (see
-- RagIntegrationService), so a short cap here would quietly weaken that too.
-- 2000 matches "description" on the same table, which already holds similar
-- free text.
--
-- WHY A SEPARATE FILE FROM V1
-- V1 is the baseline - what Flyway found on the live database. This is a real,
-- deliberate schema change, and it needs its own version so it is reviewed and
-- run in order, the same way any other change to this table would be.
-- =============================================================================

ALTER TABLE incidents
    MODIFY COLUMN root_cause       varchar(2000) DEFAULT NULL,
    MODIFY COLUMN resolution_notes varchar(2000) DEFAULT NULL;
