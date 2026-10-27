--
-- PostgreSQL database dump
--

\restrict 1HN0tZ4o4b94MigyuBG5iCuQTnFIpLMsMd1FFcIl8i0xEhX9yTrmpMW7zn7VcE5

-- Dumped from database version 17.11 (Debian 17.11-1.pgdg12+2)
-- Dumped by pg_dump version 17.11 (Debian 17.11-1.pgdg12+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: resolved_incidents_vectors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resolved_incidents_vectors (
    id uuid NOT NULL,
    content text,
    metadata jsonb,
    embedding public.vector(3072)
);


--
-- Name: resolved_incidents_vectors resolved_incidents_vectors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resolved_incidents_vectors
    ADD CONSTRAINT resolved_incidents_vectors_pkey PRIMARY KEY (id);


--
-- PostgreSQL database dump complete
--

\unrestrict 1HN0tZ4o4b94MigyuBG5iCuQTnFIpLMsMd1FFcIl8i0xEhX9yTrmpMW7zn7VcE5

