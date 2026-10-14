-- Outbox step 2 (2026-10-13): the outbox table for IncidentService.
-- An EXACT copy of common-outbox/src/main/resources/outbox/outbox_event_mysql.sql.
-- Never edit this file after it has run (Flyway checks it). Change = a new V-file.

-- =====================================================================================
-- The outbox table. ONE per service, in that service's own database.
--
-- This file is NOT run by itself. Each service copies it into its own Flyway migration
-- (for example V3__outbox_event.sql), because only Flyway may change a service's schema.
-- The tests in this module run this exact file, so the copy and the original stay the same.
--
-- status:  NEW      saved with the business data, waiting to be sent
--          SENDING  the relay has picked it up and is sending it right now
--          SENT     Kafka confirmed it (deleted after a few days)
--          FAILED   the message itself is broken; a person must look at it
-- =====================================================================================
CREATE TABLE outbox_event (
    seq         BIGINT        NOT NULL AUTO_INCREMENT,  -- the ORDER events are sent in
    id          CHAR(36)      NOT NULL,                 -- unique event id, sent as the X-Event-Id header
    topic       VARCHAR(255)  NOT NULL,
    msg_key     VARCHAR(255)  NULL,                     -- Kafka key: incident id, user id ...
    event_type  VARCHAR(255)  NOT NULL,                 -- Java class of the event, to rebuild it
    payload     TEXT          NOT NULL,                 -- the event as JSON
    trace_id    VARCHAR(64)   NULL,                     -- the request's trace id, saved for the relay
    status      VARCHAR(10)   NOT NULL,
    attempts    INT           NOT NULL DEFAULT 0,
    last_error  VARCHAR(1000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    claimed_at  DATETIME(6)   NULL,
    sent_at     DATETIME(6)   NULL,
    PRIMARY KEY (seq),
    UNIQUE KEY uk_outbox_event_id (id),
    KEY idx_outbox_event_status_seq (status, seq)
);
