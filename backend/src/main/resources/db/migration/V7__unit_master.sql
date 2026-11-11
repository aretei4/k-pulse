-- The location master is uploaded from the state's own sheets, which carry LGD
-- codes and, for a booth, the polling station it sits in.
--
-- The code is what makes a re-upload safe: names get respelled between roll
-- revisions, so matching on them would either duplicate a unit or rename the
-- wrong one. Codes come from separate LGD namespaces per level and are only
-- promised to be unique within one, hence the unique index on the pair.
ALTER TABLE unit ADD COLUMN code varchar(32);
ALTER TABLE unit ADD COLUMN premises varchar(250);

-- Units that predate the upload have no code. A unique index counts each NULL
-- as distinct, so they coexist until an upload adopts them by name.
CREATE UNIQUE INDEX ux_unit_level_code ON unit (level, code);
