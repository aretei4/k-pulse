-- FR-A20: the admin's Field agents page.
--
-- Payment is one status per agent, holding the last payment's details, because
-- the question the page answers is "has this agent been paid". A payment
-- history, or a status per month, would be a table of its own and is
-- deliberately not here.
--
-- agent_no is the readable agent ID the page shows, searches on and exports
-- (rendered A-00272). Agents already in the table are numbered oldest first.

ALTER TABLE app_user ADD COLUMN agent_no integer;

ALTER TABLE app_user ADD COLUMN paid boolean NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN paid_amount numeric(10,2);
ALTER TABLE app_user ADD COLUMN paid_on date;
ALTER TABLE app_user ADD COLUMN paid_mode varchar(20);
ALTER TABLE app_user ADD COLUMN paid_ref varchar(120);
ALTER TABLE app_user ADD COLUMN paid_updated_by_id uuid REFERENCES app_user (id);
ALTER TABLE app_user ADD COLUMN paid_updated_at timestamp with time zone;

-- Oldest first, with the id breaking a tie, so the numbering is stable and
-- unique however many accounts happen to share a creation timestamp. Counting
-- rather than a window function keeps the same statement working on PostgreSQL
-- and on the H2 the tests run against.
UPDATE app_user u
   SET agent_no = (SELECT COUNT(*)
                     FROM app_user e
                    WHERE e.role = 'FIELD_AGENT'
                      AND (e.created_at < u.created_at
                           OR (e.created_at = u.created_at AND e.id <= u.id)))
 WHERE u.role = 'FIELD_AGENT';

-- Admins keep a null agent_no; both databases allow repeated nulls here.
CREATE UNIQUE INDEX ux_app_user_agent_no ON app_user (agent_no);
CREATE INDEX ix_app_user_role_paid ON app_user (role, paid);

-- The page counts and sorts agents by what each has recorded.
CREATE INDEX ix_sentiment_recorded_by ON sentiment_entry (recorded_by_id);
