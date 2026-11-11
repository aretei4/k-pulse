-- The referral an agent names when they sign up: who sent them. Free text on
-- purpose -- it arrives as a mobile number, a party worker's code or nothing at
-- all, and tying it to another account would turn a note into a claim the app
-- cannot check.
ALTER TABLE app_user ADD COLUMN referral_no varchar(20);
