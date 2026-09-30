-- A candidate can be mapped to the panchayat they contest in. Optional: a
-- candidate standing across the constituency simply has none.
ALTER TABLE candidate ADD COLUMN unit_id uuid REFERENCES unit (id);

CREATE INDEX ix_candidate_unit ON candidate (unit_id);
