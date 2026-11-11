-- FR-A22 / FR-U14: whether a field agent has the Reports page in the app.
--
-- Off for every agent until an admin gives it, including the ones already in
-- the table: the page shows sentiment across a whole panchayat's worth of
-- booths, which is more than an agent needed before now.
ALTER TABLE app_user ADD COLUMN reports_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN reports_updated_by_id uuid REFERENCES app_user (id);
ALTER TABLE app_user ADD COLUMN reports_updated_at timestamp with time zone;

-- The admin's list filters on it, and at ten thousand agents that is a scan.
CREATE INDEX ix_app_user_reports ON app_user (role, reports_enabled);
