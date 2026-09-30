-- FR-A9 / FR-A13: admins are scoped to a District, Block or Panchayat, and a
-- Super Admin sits above the scoping with no row here at all.
ALTER TABLE app_user ADD COLUMN scope_level varchar(20);
ALTER TABLE app_user ADD COLUMN scope_unit_id uuid REFERENCES unit (id);

CREATE INDEX ix_app_user_scope ON app_user (scope_unit_id);

-- Every admin that exists today has unrestricted access, because scoping did not
-- exist when their account was made. Promoting them keeps what they can see
-- exactly as it was; narrowing them silently would take access away from someone
-- mid-campaign. It also guarantees FR-A13's "at least one Super Admin".
UPDATE app_user SET role = 'SUPER_ADMIN' WHERE role = 'ADMIN';
