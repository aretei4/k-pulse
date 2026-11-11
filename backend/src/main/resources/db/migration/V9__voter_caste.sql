-- The caste an agent records at the door. The electoral roll does not carry it,
-- so it starts null on every voter already loaded and is filled in as booths
-- are worked through. Held on the voter, not on the sentiment entry: it does not
-- change between candidates or between election cycles.
ALTER TABLE voter ADD COLUMN caste varchar(20);
