-- AI plan phase 3 (2026-11-01). What the AI suggested for a text, until the incident is created (or 24 hours pass).
CREATE TABLE public.classification_suggestion (
    text_hash       character varying(64) NOT NULL,
    ai_category     character varying(255),
    ai_severity     character varying(255),
    ai_priority     character varying(255),
    ai_service_name character varying(255),
    confidence      double precision,
    summary         character varying(2000),
    created_at      timestamp(6) NOT NULL,
    CONSTRAINT classification_suggestion_pkey PRIMARY KEY (text_hash)
);
