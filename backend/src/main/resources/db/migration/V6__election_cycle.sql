-- FR-A11: sentiment is kept per election cycle instead of being overwritten, so
-- the same booth can be compared against the last time it was canvassed.
CREATE TABLE election_cycle (
    id            uuid PRIMARY KEY,
    name          varchar(120) NOT NULL,
    election_type varchar(20) NOT NULL,
    -- "year" is a reserved word in H2, so the column carries the prefix.
    election_year integer NOT NULL,
    status        varchar(20) NOT NULL,
    created_at    timestamp with time zone NOT NULL,
    closed_at     timestamp with time zone
);

CREATE INDEX ix_cycle_status ON election_cycle (status);

-- Everything recorded so far belongs to one unnamed cycle. Naming it here means
-- no entry is left without one, and the column can be NOT NULL from the start.
INSERT INTO election_cycle (id, name, election_type, election_year, status, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Cycle 1', 'PANCHAYAT', 2026, 'OPEN', CURRENT_TIMESTAMP);

ALTER TABLE sentiment_entry ADD COLUMN election_cycle_id uuid REFERENCES election_cycle (id);
ALTER TABLE house_sentiment_entry ADD COLUMN election_cycle_id uuid REFERENCES election_cycle (id);

UPDATE sentiment_entry SET election_cycle_id = '00000000-0000-0000-0000-000000000001';
UPDATE house_sentiment_entry SET election_cycle_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE sentiment_entry ALTER COLUMN election_cycle_id SET NOT NULL;
ALTER TABLE house_sentiment_entry ALTER COLUMN election_cycle_id SET NOT NULL;

CREATE INDEX ix_sentiment_cycle ON sentiment_entry (election_cycle_id);
CREATE INDEX ix_house_sentiment_cycle ON house_sentiment_entry (election_cycle_id);

-- One voter has one entry per candidate *per cycle* now: the next cycle starts a
-- fresh row rather than overwriting what the last one recorded.
DROP INDEX ux_sentiment_voter_candidate;
CREATE UNIQUE INDEX ux_sentiment_voter_candidate_cycle
    ON sentiment_entry (voter_id, candidate_id, election_cycle_id);

DROP INDEX ux_house_sentiment_booth_candidate_house;
CREATE UNIQUE INDEX ux_house_sentiment_booth_candidate_house_cycle
    ON house_sentiment_entry (booth_id, candidate_id, house_no, election_cycle_id);
