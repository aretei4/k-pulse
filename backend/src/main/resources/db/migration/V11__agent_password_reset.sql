-- FR-A20 FA-24/FA-25: an agent asks for a new password from inside the app, and
-- an admin sets one from the Field agents page.
--
-- One pending ask per agent rather than a request table: a second tap is the
-- same ask, and what the admin needs to see is "this agent is waiting", not a
-- history. Resetting the password, or dismissing the ask, clears it.

ALTER TABLE app_user ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN password_reset_requested_at timestamp with time zone;

-- Who last reset a password, and when. Also the clock a temporary password
-- expires against (FA-N5).
ALTER TABLE app_user ADD COLUMN login_updated_by_id uuid REFERENCES app_user (id);
ALTER TABLE app_user ADD COLUMN login_updated_at timestamp with time zone;

-- The admin's list marks the agents waiting, so they are worth finding quickly.
CREATE INDEX ix_app_user_password_requested ON app_user (password_reset_requested_at);
