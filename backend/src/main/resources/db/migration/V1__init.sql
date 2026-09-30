-- K-Pulse initial schema.
-- Written in portable SQL so the same migration runs on PostgreSQL (production)
-- and on H2 in PostgreSQL mode (the zero-setup "h2" profile).

CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    name          varchar(120) NOT NULL,
    email         varchar(160),
    phone         varchar(20),
    address       varchar(255),
    password_hash varchar(255),
    role          varchar(20) NOT NULL,
    active        boolean NOT NULL DEFAULT true,
    created_at    timestamp with time zone NOT NULL
);

CREATE UNIQUE INDEX ux_app_user_email ON app_user (email);
CREATE UNIQUE INDEX ux_app_user_phone ON app_user (phone);

CREATE TABLE candidate (
    id    uuid PRIMARY KEY,
    name  varchar(120) NOT NULL,
    party varchar(120)
);

-- District > Block > Panchayat > Booth in one self-referencing table: an access
-- grant can sit at any level, and "everything under it" is a single walk down.
CREATE TABLE unit (
    id        uuid PRIMARY KEY,
    level     varchar(20) NOT NULL,
    name      varchar(120) NOT NULL,
    path      varchar(500) NOT NULL,
    parent_id uuid REFERENCES unit (id)
);

CREATE INDEX ix_unit_parent ON unit (parent_id);
CREATE INDEX ix_unit_level ON unit (level);

CREATE TABLE voter (
    id         uuid PRIMARY KEY,
    epic_no    varchar(40) NOT NULL,
    name       varchar(160) NOT NULL,
    relation   varchar(160),
    house_no   varchar(40),
    age        integer NOT NULL,
    gender     varchar(10) NOT NULL,
    booth_id   uuid NOT NULL REFERENCES unit (id),
    ward_no    integer,
    created_at timestamp with time zone NOT NULL
);

CREATE UNIQUE INDEX ux_voter_epic ON voter (epic_no);
CREATE INDEX ix_voter_booth ON voter (booth_id);
CREATE INDEX ix_voter_name ON voter (name);

CREATE TABLE access_request (
    id            uuid PRIMARY KEY,
    agent_id      uuid NOT NULL REFERENCES app_user (id),
    unit_id       uuid NOT NULL REFERENCES unit (id),
    candidate_id  uuid NOT NULL REFERENCES candidate (id),
    status        varchar(20) NOT NULL,
    requested_at  timestamp with time zone NOT NULL,
    decided_at    timestamp with time zone,
    expires_at    timestamp with time zone,
    reviewer_note varchar(500)
);

CREATE INDEX ix_access_agent ON access_request (agent_id);
CREATE INDEX ix_access_status ON access_request (status);

CREATE TABLE sentiment_entry (
    id             uuid PRIMARY KEY,
    voter_id       uuid NOT NULL REFERENCES voter (id) ON DELETE CASCADE,
    candidate_id   uuid NOT NULL REFERENCES candidate (id),
    sentiment      varchar(20) NOT NULL,
    confidence     varchar(20) NOT NULL,
    resident       boolean NOT NULL DEFAULT true,
    ward_no        integer,
    recorded_by_id uuid NOT NULL REFERENCES app_user (id),
    recorded_at    timestamp with time zone NOT NULL,
    updated_at     timestamp with time zone NOT NULL
);

-- One entry per voter per candidate; re-recording overwrites it (FR-U10).
CREATE UNIQUE INDEX ux_sentiment_voter_candidate ON sentiment_entry (voter_id, candidate_id);
CREATE INDEX ix_sentiment_recorded_at ON sentiment_entry (recorded_at);

CREATE TABLE voter_change_request (
    id            uuid PRIMARY KEY,
    change_type   varchar(20) NOT NULL,
    status        varchar(20) NOT NULL,
    agent_id      uuid NOT NULL REFERENCES app_user (id),
    voter_id      uuid REFERENCES voter (id) ON DELETE SET NULL,
    voter_name    varchar(160),
    booth_id      uuid NOT NULL REFERENCES unit (id),
    epic_no       varchar(40),
    name          varchar(160),
    relation      varchar(160),
    house_no      varchar(40),
    age           integer,
    gender        varchar(10),
    ward_no       integer,
    reason        varchar(500),
    proposed_at   timestamp with time zone NOT NULL,
    decided_at    timestamp with time zone,
    reviewer_note varchar(500)
);

CREATE INDEX ix_change_status ON voter_change_request (status);
CREATE INDEX ix_change_agent ON voter_change_request (agent_id);

CREATE TABLE voter_upload (
    id             uuid PRIMARY KEY,
    file_name      varchar(255) NOT NULL,
    unit_id        uuid NOT NULL REFERENCES unit (id),
    row_count      integer NOT NULL,
    status         varchar(20) NOT NULL,
    message        varchar(500),
    uploaded_by_id uuid NOT NULL REFERENCES app_user (id),
    uploaded_at    timestamp with time zone NOT NULL
);

CREATE TABLE otp_token (
    id         uuid PRIMARY KEY,
    phone      varchar(20) NOT NULL,
    code       varchar(10) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed   boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX ix_otp_phone ON otp_token (phone);
