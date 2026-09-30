-- Pre-election, house-level sentiment (FR-U12). Deliberately its own table with
-- no voter_id: a household tally is never an individual's record, and the two
-- datasets must not be joinable by accident.
CREATE TABLE house_sentiment_entry (
    id                uuid PRIMARY KEY,
    booth_id          uuid NOT NULL REFERENCES unit (id),
    candidate_id      uuid NOT NULL REFERENCES candidate (id),
    house_no          varchar(40) NOT NULL,
    house_name        varchar(160) NOT NULL,
    ward_no           integer,
    headcount         integer NOT NULL,
    residential_count integer NOT NULL,
    positive_count    integer NOT NULL,
    neutral_count     integer NOT NULL,
    negative_count    integer NOT NULL,
    confidence        varchar(20) NOT NULL,
    recorded_by_id    uuid NOT NULL REFERENCES app_user (id),
    recorded_at       timestamp with time zone NOT NULL,
    updated_at        timestamp with time zone NOT NULL
);

-- One tally per house per candidate within a booth; recording it again
-- overwrites that row, the same rule the named-voter entry follows. House
-- numbers are trimmed and upper-cased by the service so "4/a" and "4/A" are
-- the same house here.
CREATE UNIQUE INDEX ux_house_sentiment_booth_candidate_house
    ON house_sentiment_entry (booth_id, candidate_id, house_no);
CREATE INDEX ix_house_sentiment_booth ON house_sentiment_entry (booth_id);
