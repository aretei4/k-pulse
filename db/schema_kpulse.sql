--
-- PostgreSQL database dump
--

\restrict BQgBkqgAgkmwLEQLT8706LP8dNcLT2t7oR1CiO4oVLvgrDWeNmIJuXvJHCKrBpM

-- Dumped from database version 15.14 (Debian 15.14-1.pgdg13+1)
-- Dumped by pg_dump version 18.0

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
-- Name: uat; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA uat;


ALTER SCHEMA uat OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: access_request; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.access_request (
    id uuid NOT NULL,
    agent_id uuid NOT NULL,
    unit_id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    decided_at timestamp with time zone,
    expires_at timestamp with time zone,
    reviewer_note character varying(500)
);


ALTER TABLE public.access_request OWNER TO postgres;

--
-- Name: account_deletion_request; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.account_deletion_request (
    id uuid NOT NULL,
    user_id uuid,
    agent_name character varying(120) NOT NULL,
    agent_phone character varying(20) NOT NULL,
    agent_email character varying(160),
    reason character varying(500),
    status character varying(20) NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    reviewed_at timestamp with time zone,
    reviewed_by_id uuid,
    reviewer_note character varying(500),
    deleted_entries integer
);


ALTER TABLE public.account_deletion_request OWNER TO postgres;

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.app_user (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    email character varying(160),
    phone character varying(20),
    address character varying(255),
    password_hash character varying(255),
    role character varying(20) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    scope_level character varying(20),
    scope_unit_id uuid
);


ALTER TABLE public.app_user OWNER TO postgres;

--
-- Name: candidate; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.candidate (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    party character varying(120),
    unit_id uuid
);


ALTER TABLE public.candidate OWNER TO postgres;

--
-- Name: election_cycle; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.election_cycle (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    election_type character varying(20) NOT NULL,
    election_year integer NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone
);


ALTER TABLE public.election_cycle OWNER TO postgres;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO postgres;

--
-- Name: house_sentiment_entry; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.house_sentiment_entry (
    id uuid NOT NULL,
    booth_id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    house_no character varying(40) NOT NULL,
    house_name character varying(160) NOT NULL,
    ward_no integer,
    headcount integer NOT NULL,
    residential_count integer NOT NULL,
    positive_count integer NOT NULL,
    neutral_count integer NOT NULL,
    negative_count integer NOT NULL,
    confidence character varying(20) NOT NULL,
    recorded_by_id uuid NOT NULL,
    recorded_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    election_cycle_id uuid NOT NULL
);


ALTER TABLE public.house_sentiment_entry OWNER TO postgres;

--
-- Name: otp_token; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.otp_token (
    id uuid NOT NULL,
    phone character varying(20) NOT NULL,
    code character varying(10) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone NOT NULL
);


ALTER TABLE public.otp_token OWNER TO postgres;

--
-- Name: sentiment_entry; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.sentiment_entry (
    id uuid NOT NULL,
    voter_id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    sentiment character varying(20) NOT NULL,
    confidence character varying(20) NOT NULL,
    resident boolean DEFAULT true NOT NULL,
    ward_no integer,
    recorded_by_id uuid NOT NULL,
    recorded_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    election_cycle_id uuid NOT NULL
);


ALTER TABLE public.sentiment_entry OWNER TO postgres;

--
-- Name: unit; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.unit (
    id uuid NOT NULL,
    level character varying(20) NOT NULL,
    name character varying(120) NOT NULL,
    path character varying(500) NOT NULL,
    parent_id uuid
);


ALTER TABLE public.unit OWNER TO postgres;

--
-- Name: voter; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.voter (
    id uuid NOT NULL,
    epic_no character varying(40) NOT NULL,
    name character varying(160) NOT NULL,
    relation character varying(160),
    house_no character varying(40),
    age integer NOT NULL,
    gender character varying(10) NOT NULL,
    booth_id uuid NOT NULL,
    ward_no integer,
    created_at timestamp with time zone NOT NULL
);


ALTER TABLE public.voter OWNER TO postgres;

--
-- Name: voter_change_request; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.voter_change_request (
    id uuid NOT NULL,
    change_type character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    agent_id uuid NOT NULL,
    voter_id uuid,
    voter_name character varying(160),
    booth_id uuid NOT NULL,
    epic_no character varying(40),
    name character varying(160),
    relation character varying(160),
    house_no character varying(40),
    age integer,
    gender character varying(10),
    ward_no integer,
    reason character varying(500),
    proposed_at timestamp with time zone NOT NULL,
    decided_at timestamp with time zone,
    reviewer_note character varying(500)
);


ALTER TABLE public.voter_change_request OWNER TO postgres;

--
-- Name: voter_upload; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.voter_upload (
    id uuid NOT NULL,
    file_name character varying(255) NOT NULL,
    unit_id uuid NOT NULL,
    row_count integer NOT NULL,
    status character varying(20) NOT NULL,
    message character varying(500),
    uploaded_by_id uuid NOT NULL,
    uploaded_at timestamp with time zone NOT NULL
);


ALTER TABLE public.voter_upload OWNER TO postgres;

--
-- Name: access_request; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.access_request (
    id uuid NOT NULL,
    agent_id uuid NOT NULL,
    unit_id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    decided_at timestamp with time zone,
    expires_at timestamp with time zone,
    reviewer_note character varying(500)
);


ALTER TABLE uat.access_request OWNER TO postgres;

--
-- Name: account_deletion_request; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.account_deletion_request (
    id uuid NOT NULL,
    user_id uuid,
    agent_name character varying(120) NOT NULL,
    agent_phone character varying(20) NOT NULL,
    agent_email character varying(160),
    reason character varying(500),
    status character varying(20) NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    reviewed_at timestamp with time zone,
    reviewed_by_id uuid,
    reviewer_note character varying(500),
    deleted_entries integer
);


ALTER TABLE uat.account_deletion_request OWNER TO postgres;

--
-- Name: app_user; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.app_user (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    email character varying(160),
    phone character varying(20),
    address character varying(255),
    password_hash character varying(255),
    role character varying(20) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    scope_level character varying(20),
    scope_unit_id uuid
);


ALTER TABLE uat.app_user OWNER TO postgres;

--
-- Name: candidate; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.candidate (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    party character varying(120),
    unit_id uuid
);


ALTER TABLE uat.candidate OWNER TO postgres;

--
-- Name: election_cycle; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.election_cycle (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    election_type character varying(20) NOT NULL,
    election_year integer NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone
);


ALTER TABLE uat.election_cycle OWNER TO postgres;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE uat.flyway_schema_history OWNER TO postgres;

--
-- Name: house_sentiment_entry; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.house_sentiment_entry (
    id uuid NOT NULL,
    booth_id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    house_no character varying(40) NOT NULL,
    house_name character varying(160) NOT NULL,
    ward_no integer,
    headcount integer NOT NULL,
    residential_count integer NOT NULL,
    positive_count integer NOT NULL,
    neutral_count integer NOT NULL,
    negative_count integer NOT NULL,
    confidence character varying(20) NOT NULL,
    recorded_by_id uuid NOT NULL,
    recorded_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    election_cycle_id uuid NOT NULL
);


ALTER TABLE uat.house_sentiment_entry OWNER TO postgres;

--
-- Name: otp_token; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.otp_token (
    id uuid NOT NULL,
    phone character varying(20) NOT NULL,
    code character varying(10) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone NOT NULL
);


ALTER TABLE uat.otp_token OWNER TO postgres;

--
-- Name: sentiment_entry; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.sentiment_entry (
    id uuid NOT NULL,
    voter_id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    sentiment character varying(20) NOT NULL,
    confidence character varying(20) NOT NULL,
    resident boolean DEFAULT true NOT NULL,
    ward_no integer,
    recorded_by_id uuid NOT NULL,
    recorded_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    election_cycle_id uuid NOT NULL
);


ALTER TABLE uat.sentiment_entry OWNER TO postgres;

--
-- Name: unit; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.unit (
    id uuid NOT NULL,
    level character varying(20) NOT NULL,
    name character varying(120) NOT NULL,
    path character varying(500) NOT NULL,
    parent_id uuid
);


ALTER TABLE uat.unit OWNER TO postgres;

--
-- Name: voter; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.voter (
    id uuid NOT NULL,
    epic_no character varying(40) NOT NULL,
    name character varying(160) NOT NULL,
    relation character varying(160),
    house_no character varying(40),
    age integer NOT NULL,
    gender character varying(10) NOT NULL,
    booth_id uuid NOT NULL,
    ward_no integer,
    created_at timestamp with time zone NOT NULL
);


ALTER TABLE uat.voter OWNER TO postgres;

--
-- Name: voter_change_request; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.voter_change_request (
    id uuid NOT NULL,
    change_type character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    agent_id uuid NOT NULL,
    voter_id uuid,
    voter_name character varying(160),
    booth_id uuid NOT NULL,
    epic_no character varying(40),
    name character varying(160),
    relation character varying(160),
    house_no character varying(40),
    age integer,
    gender character varying(10),
    ward_no integer,
    reason character varying(500),
    proposed_at timestamp with time zone NOT NULL,
    decided_at timestamp with time zone,
    reviewer_note character varying(500)
);


ALTER TABLE uat.voter_change_request OWNER TO postgres;

--
-- Name: voter_upload; Type: TABLE; Schema: uat; Owner: postgres
--

CREATE TABLE uat.voter_upload (
    id uuid NOT NULL,
    file_name character varying(255) NOT NULL,
    unit_id uuid NOT NULL,
    row_count integer NOT NULL,
    status character varying(20) NOT NULL,
    message character varying(500),
    uploaded_by_id uuid NOT NULL,
    uploaded_at timestamp with time zone NOT NULL
);


ALTER TABLE uat.voter_upload OWNER TO postgres;

--
-- Name: access_request access_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.access_request
    ADD CONSTRAINT access_request_pkey PRIMARY KEY (id);


--
-- Name: account_deletion_request account_deletion_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account_deletion_request
    ADD CONSTRAINT account_deletion_request_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: candidate candidate_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.candidate
    ADD CONSTRAINT candidate_pkey PRIMARY KEY (id);


--
-- Name: election_cycle election_cycle_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.election_cycle
    ADD CONSTRAINT election_cycle_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: house_sentiment_entry house_sentiment_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_pkey PRIMARY KEY (id);


--
-- Name: otp_token otp_token_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.otp_token
    ADD CONSTRAINT otp_token_pkey PRIMARY KEY (id);


--
-- Name: sentiment_entry sentiment_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sentiment_entry
    ADD CONSTRAINT sentiment_entry_pkey PRIMARY KEY (id);


--
-- Name: unit unit_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.unit
    ADD CONSTRAINT unit_pkey PRIMARY KEY (id);


--
-- Name: voter_change_request voter_change_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter_change_request
    ADD CONSTRAINT voter_change_request_pkey PRIMARY KEY (id);


--
-- Name: voter voter_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter
    ADD CONSTRAINT voter_pkey PRIMARY KEY (id);


--
-- Name: voter_upload voter_upload_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter_upload
    ADD CONSTRAINT voter_upload_pkey PRIMARY KEY (id);


--
-- Name: access_request access_request_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.access_request
    ADD CONSTRAINT access_request_pkey PRIMARY KEY (id);


--
-- Name: account_deletion_request account_deletion_request_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.account_deletion_request
    ADD CONSTRAINT account_deletion_request_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: candidate candidate_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.candidate
    ADD CONSTRAINT candidate_pkey PRIMARY KEY (id);


--
-- Name: election_cycle election_cycle_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.election_cycle
    ADD CONSTRAINT election_cycle_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: house_sentiment_entry house_sentiment_entry_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_pkey PRIMARY KEY (id);


--
-- Name: otp_token otp_token_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.otp_token
    ADD CONSTRAINT otp_token_pkey PRIMARY KEY (id);


--
-- Name: sentiment_entry sentiment_entry_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.sentiment_entry
    ADD CONSTRAINT sentiment_entry_pkey PRIMARY KEY (id);


--
-- Name: unit unit_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.unit
    ADD CONSTRAINT unit_pkey PRIMARY KEY (id);


--
-- Name: voter_change_request voter_change_request_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter_change_request
    ADD CONSTRAINT voter_change_request_pkey PRIMARY KEY (id);


--
-- Name: voter voter_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter
    ADD CONSTRAINT voter_pkey PRIMARY KEY (id);


--
-- Name: voter_upload voter_upload_pkey; Type: CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter_upload
    ADD CONSTRAINT voter_upload_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: ix_access_agent; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_access_agent ON public.access_request USING btree (agent_id);


--
-- Name: ix_access_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_access_status ON public.access_request USING btree (status);


--
-- Name: ix_app_user_scope; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_app_user_scope ON public.app_user USING btree (scope_unit_id);


--
-- Name: ix_candidate_unit; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_candidate_unit ON public.candidate USING btree (unit_id);


--
-- Name: ix_change_agent; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_change_agent ON public.voter_change_request USING btree (agent_id);


--
-- Name: ix_change_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_change_status ON public.voter_change_request USING btree (status);


--
-- Name: ix_cycle_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_cycle_status ON public.election_cycle USING btree (status);


--
-- Name: ix_deletion_phone; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_deletion_phone ON public.account_deletion_request USING btree (agent_phone);


--
-- Name: ix_deletion_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_deletion_status ON public.account_deletion_request USING btree (status);


--
-- Name: ix_house_sentiment_booth; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_house_sentiment_booth ON public.house_sentiment_entry USING btree (booth_id);


--
-- Name: ix_house_sentiment_cycle; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_house_sentiment_cycle ON public.house_sentiment_entry USING btree (election_cycle_id);


--
-- Name: ix_otp_phone; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_otp_phone ON public.otp_token USING btree (phone);


--
-- Name: ix_sentiment_cycle; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_sentiment_cycle ON public.sentiment_entry USING btree (election_cycle_id);


--
-- Name: ix_sentiment_recorded_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_sentiment_recorded_at ON public.sentiment_entry USING btree (recorded_at);


--
-- Name: ix_unit_level; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_unit_level ON public.unit USING btree (level);


--
-- Name: ix_unit_parent; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_unit_parent ON public.unit USING btree (parent_id);


--
-- Name: ix_voter_booth; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_voter_booth ON public.voter USING btree (booth_id);


--
-- Name: ix_voter_name; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_voter_name ON public.voter USING btree (name);


--
-- Name: ux_app_user_email; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_app_user_email ON public.app_user USING btree (email);


--
-- Name: ux_app_user_phone; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_app_user_phone ON public.app_user USING btree (phone);


--
-- Name: ux_house_sentiment_booth_candidate_house_cycle; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_house_sentiment_booth_candidate_house_cycle ON public.house_sentiment_entry USING btree (booth_id, candidate_id, house_no, election_cycle_id);


--
-- Name: ux_sentiment_voter_candidate_cycle; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_sentiment_voter_candidate_cycle ON public.sentiment_entry USING btree (voter_id, candidate_id, election_cycle_id);


--
-- Name: ux_voter_epic; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_voter_epic ON public.voter USING btree (epic_no);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX flyway_schema_history_s_idx ON uat.flyway_schema_history USING btree (success);


--
-- Name: ix_access_agent; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_access_agent ON uat.access_request USING btree (agent_id);


--
-- Name: ix_access_status; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_access_status ON uat.access_request USING btree (status);


--
-- Name: ix_app_user_scope; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_app_user_scope ON uat.app_user USING btree (scope_unit_id);


--
-- Name: ix_candidate_unit; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_candidate_unit ON uat.candidate USING btree (unit_id);


--
-- Name: ix_change_agent; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_change_agent ON uat.voter_change_request USING btree (agent_id);


--
-- Name: ix_change_status; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_change_status ON uat.voter_change_request USING btree (status);


--
-- Name: ix_cycle_status; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_cycle_status ON uat.election_cycle USING btree (status);


--
-- Name: ix_deletion_phone; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_deletion_phone ON uat.account_deletion_request USING btree (agent_phone);


--
-- Name: ix_deletion_status; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_deletion_status ON uat.account_deletion_request USING btree (status);


--
-- Name: ix_house_sentiment_booth; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_house_sentiment_booth ON uat.house_sentiment_entry USING btree (booth_id);


--
-- Name: ix_house_sentiment_cycle; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_house_sentiment_cycle ON uat.house_sentiment_entry USING btree (election_cycle_id);


--
-- Name: ix_otp_phone; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_otp_phone ON uat.otp_token USING btree (phone);


--
-- Name: ix_sentiment_cycle; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_sentiment_cycle ON uat.sentiment_entry USING btree (election_cycle_id);


--
-- Name: ix_sentiment_recorded_at; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_sentiment_recorded_at ON uat.sentiment_entry USING btree (recorded_at);


--
-- Name: ix_unit_level; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_unit_level ON uat.unit USING btree (level);


--
-- Name: ix_unit_parent; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_unit_parent ON uat.unit USING btree (parent_id);


--
-- Name: ix_voter_booth; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_voter_booth ON uat.voter USING btree (booth_id);


--
-- Name: ix_voter_name; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE INDEX ix_voter_name ON uat.voter USING btree (name);


--
-- Name: ux_app_user_email; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE UNIQUE INDEX ux_app_user_email ON uat.app_user USING btree (email);


--
-- Name: ux_app_user_phone; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE UNIQUE INDEX ux_app_user_phone ON uat.app_user USING btree (phone);


--
-- Name: ux_house_sentiment_booth_candidate_house_cycle; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE UNIQUE INDEX ux_house_sentiment_booth_candidate_house_cycle ON uat.house_sentiment_entry USING btree (booth_id, candidate_id, house_no, election_cycle_id);


--
-- Name: ux_sentiment_voter_candidate_cycle; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE UNIQUE INDEX ux_sentiment_voter_candidate_cycle ON uat.sentiment_entry USING btree (voter_id, candidate_id, election_cycle_id);


--
-- Name: ux_voter_epic; Type: INDEX; Schema: uat; Owner: postgres
--

CREATE UNIQUE INDEX ux_voter_epic ON uat.voter USING btree (epic_no);


--
-- Name: access_request access_request_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.access_request
    ADD CONSTRAINT access_request_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.app_user(id);


--
-- Name: access_request access_request_candidate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.access_request
    ADD CONSTRAINT access_request_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES public.candidate(id);


--
-- Name: access_request access_request_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.access_request
    ADD CONSTRAINT access_request_unit_id_fkey FOREIGN KEY (unit_id) REFERENCES public.unit(id);


--
-- Name: account_deletion_request account_deletion_request_reviewed_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account_deletion_request
    ADD CONSTRAINT account_deletion_request_reviewed_by_id_fkey FOREIGN KEY (reviewed_by_id) REFERENCES public.app_user(id);


--
-- Name: account_deletion_request account_deletion_request_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account_deletion_request
    ADD CONSTRAINT account_deletion_request_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE SET NULL;


--
-- Name: app_user app_user_scope_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_scope_unit_id_fkey FOREIGN KEY (scope_unit_id) REFERENCES public.unit(id);


--
-- Name: candidate candidate_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.candidate
    ADD CONSTRAINT candidate_unit_id_fkey FOREIGN KEY (unit_id) REFERENCES public.unit(id);


--
-- Name: house_sentiment_entry house_sentiment_entry_booth_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_booth_id_fkey FOREIGN KEY (booth_id) REFERENCES public.unit(id);


--
-- Name: house_sentiment_entry house_sentiment_entry_candidate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES public.candidate(id);


--
-- Name: house_sentiment_entry house_sentiment_entry_election_cycle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_election_cycle_id_fkey FOREIGN KEY (election_cycle_id) REFERENCES public.election_cycle(id);


--
-- Name: house_sentiment_entry house_sentiment_entry_recorded_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_recorded_by_id_fkey FOREIGN KEY (recorded_by_id) REFERENCES public.app_user(id);


--
-- Name: sentiment_entry sentiment_entry_candidate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sentiment_entry
    ADD CONSTRAINT sentiment_entry_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES public.candidate(id);


--
-- Name: sentiment_entry sentiment_entry_election_cycle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sentiment_entry
    ADD CONSTRAINT sentiment_entry_election_cycle_id_fkey FOREIGN KEY (election_cycle_id) REFERENCES public.election_cycle(id);


--
-- Name: sentiment_entry sentiment_entry_recorded_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sentiment_entry
    ADD CONSTRAINT sentiment_entry_recorded_by_id_fkey FOREIGN KEY (recorded_by_id) REFERENCES public.app_user(id);


--
-- Name: sentiment_entry sentiment_entry_voter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sentiment_entry
    ADD CONSTRAINT sentiment_entry_voter_id_fkey FOREIGN KEY (voter_id) REFERENCES public.voter(id) ON DELETE CASCADE;


--
-- Name: unit unit_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.unit
    ADD CONSTRAINT unit_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.unit(id);


--
-- Name: voter voter_booth_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter
    ADD CONSTRAINT voter_booth_id_fkey FOREIGN KEY (booth_id) REFERENCES public.unit(id);


--
-- Name: voter_change_request voter_change_request_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter_change_request
    ADD CONSTRAINT voter_change_request_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.app_user(id);


--
-- Name: voter_change_request voter_change_request_booth_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter_change_request
    ADD CONSTRAINT voter_change_request_booth_id_fkey FOREIGN KEY (booth_id) REFERENCES public.unit(id);


--
-- Name: voter_change_request voter_change_request_voter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter_change_request
    ADD CONSTRAINT voter_change_request_voter_id_fkey FOREIGN KEY (voter_id) REFERENCES public.voter(id) ON DELETE SET NULL;


--
-- Name: voter_upload voter_upload_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter_upload
    ADD CONSTRAINT voter_upload_unit_id_fkey FOREIGN KEY (unit_id) REFERENCES public.unit(id);


--
-- Name: voter_upload voter_upload_uploaded_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.voter_upload
    ADD CONSTRAINT voter_upload_uploaded_by_id_fkey FOREIGN KEY (uploaded_by_id) REFERENCES public.app_user(id);


--
-- Name: access_request access_request_agent_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.access_request
    ADD CONSTRAINT access_request_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES uat.app_user(id);


--
-- Name: access_request access_request_candidate_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.access_request
    ADD CONSTRAINT access_request_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES uat.candidate(id);


--
-- Name: access_request access_request_unit_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.access_request
    ADD CONSTRAINT access_request_unit_id_fkey FOREIGN KEY (unit_id) REFERENCES uat.unit(id);


--
-- Name: account_deletion_request account_deletion_request_reviewed_by_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.account_deletion_request
    ADD CONSTRAINT account_deletion_request_reviewed_by_id_fkey FOREIGN KEY (reviewed_by_id) REFERENCES uat.app_user(id);


--
-- Name: account_deletion_request account_deletion_request_user_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.account_deletion_request
    ADD CONSTRAINT account_deletion_request_user_id_fkey FOREIGN KEY (user_id) REFERENCES uat.app_user(id) ON DELETE SET NULL;


--
-- Name: app_user app_user_scope_unit_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.app_user
    ADD CONSTRAINT app_user_scope_unit_id_fkey FOREIGN KEY (scope_unit_id) REFERENCES uat.unit(id);


--
-- Name: candidate candidate_unit_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.candidate
    ADD CONSTRAINT candidate_unit_id_fkey FOREIGN KEY (unit_id) REFERENCES uat.unit(id);


--
-- Name: house_sentiment_entry house_sentiment_entry_booth_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_booth_id_fkey FOREIGN KEY (booth_id) REFERENCES uat.unit(id);


--
-- Name: house_sentiment_entry house_sentiment_entry_candidate_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES uat.candidate(id);


--
-- Name: house_sentiment_entry house_sentiment_entry_election_cycle_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_election_cycle_id_fkey FOREIGN KEY (election_cycle_id) REFERENCES uat.election_cycle(id);


--
-- Name: house_sentiment_entry house_sentiment_entry_recorded_by_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.house_sentiment_entry
    ADD CONSTRAINT house_sentiment_entry_recorded_by_id_fkey FOREIGN KEY (recorded_by_id) REFERENCES uat.app_user(id);


--
-- Name: sentiment_entry sentiment_entry_candidate_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.sentiment_entry
    ADD CONSTRAINT sentiment_entry_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES uat.candidate(id);


--
-- Name: sentiment_entry sentiment_entry_election_cycle_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.sentiment_entry
    ADD CONSTRAINT sentiment_entry_election_cycle_id_fkey FOREIGN KEY (election_cycle_id) REFERENCES uat.election_cycle(id);


--
-- Name: sentiment_entry sentiment_entry_recorded_by_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.sentiment_entry
    ADD CONSTRAINT sentiment_entry_recorded_by_id_fkey FOREIGN KEY (recorded_by_id) REFERENCES uat.app_user(id);


--
-- Name: sentiment_entry sentiment_entry_voter_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.sentiment_entry
    ADD CONSTRAINT sentiment_entry_voter_id_fkey FOREIGN KEY (voter_id) REFERENCES uat.voter(id) ON DELETE CASCADE;


--
-- Name: unit unit_parent_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.unit
    ADD CONSTRAINT unit_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES uat.unit(id);


--
-- Name: voter voter_booth_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter
    ADD CONSTRAINT voter_booth_id_fkey FOREIGN KEY (booth_id) REFERENCES uat.unit(id);


--
-- Name: voter_change_request voter_change_request_agent_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter_change_request
    ADD CONSTRAINT voter_change_request_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES uat.app_user(id);


--
-- Name: voter_change_request voter_change_request_booth_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter_change_request
    ADD CONSTRAINT voter_change_request_booth_id_fkey FOREIGN KEY (booth_id) REFERENCES uat.unit(id);


--
-- Name: voter_change_request voter_change_request_voter_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter_change_request
    ADD CONSTRAINT voter_change_request_voter_id_fkey FOREIGN KEY (voter_id) REFERENCES uat.voter(id) ON DELETE SET NULL;


--
-- Name: voter_upload voter_upload_unit_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter_upload
    ADD CONSTRAINT voter_upload_unit_id_fkey FOREIGN KEY (unit_id) REFERENCES uat.unit(id);


--
-- Name: voter_upload voter_upload_uploaded_by_id_fkey; Type: FK CONSTRAINT; Schema: uat; Owner: postgres
--

ALTER TABLE ONLY uat.voter_upload
    ADD CONSTRAINT voter_upload_uploaded_by_id_fkey FOREIGN KEY (uploaded_by_id) REFERENCES uat.app_user(id);


--
-- PostgreSQL database dump complete
--

\unrestrict BQgBkqgAgkmwLEQLT8706LP8dNcLT2t7oR1CiO4oVLvgrDWeNmIJuXvJHCKrBpM

