-- Test only: the ai_insight table as in V1__baseline.sql. V1 itself cannot run on H2, because it creates Postgres extensions.
CREATE TABLE public.ai_insight (
    incident_id     uuid NOT NULL,
    ai_category     character varying(255),
    ai_severity     character varying(255),
    ai_priority     character varying(255),
    ai_service_name character varying(255),
    confidence      double precision,
    conflict        boolean NOT NULL,
    summary         character varying(2000),
    CONSTRAINT ai_insight_pkey PRIMARY KEY (incident_id)
);
