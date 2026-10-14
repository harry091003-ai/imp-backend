-- TESTS ONLY (OutboxWiringTest). The same shedlock table as in V1__baseline.sql, so the 3 SLA timers can
-- take their lock and really run inside the test, instead of skipping with "bad SQL grammar".
-- Never used by the real service (Flyway makes the real table from V1).
CREATE TABLE IF NOT EXISTS shedlock (
  name       varchar(64)  NOT NULL,
  lock_until timestamp    NULL DEFAULT NULL,
  locked_at  timestamp    NULL DEFAULT NULL,
  locked_by  varchar(255) DEFAULT NULL,
  PRIMARY KEY (name)
);
