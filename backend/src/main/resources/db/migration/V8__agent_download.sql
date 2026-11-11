-- FR-U13 / DL-13: a field agent may download a booth's voter list as a PDF only
-- once an admin switches it on. Off by default, and off for every agent already
-- on the system: a downloaded list is named voters' data leaving K-Pulse, so it
-- is granted deliberately rather than inherited.
ALTER TABLE app_user ADD COLUMN download_enabled boolean NOT NULL DEFAULT false;
