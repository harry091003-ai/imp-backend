-- Auth v2 part B (2026-10-27): refresh tokens.
--
-- Login now gives TWO tokens: a short access token (15 min, the JWT) and a long refresh token (7 days).
-- One row here = one refresh token. Only its SHA-256 hash is stored, never the token itself:
-- someone who reads this table still cannot use a single row of it.
--
--   family_id   every token made from the same login shares it (login -> refresh -> refresh ...).
--               Reusing an already-used token cancels the whole family (someone copied it).
--   used_at     set when the token was swapped for a new one. Each token works ONCE.
--   revoked_at  set by logout, password change, role change, or when reuse is detected.
--
-- Never edit this file after it has run (Flyway checks it). Change = a new V-file.

CREATE TABLE `refresh_token` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `user_id`     binary(16)   NOT NULL,
  `family_id`   binary(16)   NOT NULL,
  `token_hash`  varchar(64)  NOT NULL,
  `created_at`  datetime(6)  NOT NULL,
  `expires_at`  datetime(6)  NOT NULL,
  `used_at`     datetime(6)  DEFAULT NULL,
  `revoked_at`  datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_token_user` (`user_id`),
  KEY `idx_refresh_token_family` (`family_id`)
);
