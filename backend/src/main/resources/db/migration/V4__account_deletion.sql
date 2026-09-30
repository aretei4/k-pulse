-- Account deletion requests: an agent asks from the public page or the app, an
-- admin approves, and approval erases the account together with everything that
-- agent recorded.
CREATE TABLE account_deletion_request (
    id             uuid PRIMARY KEY,
    -- Cleared when the account goes, so the row survives as the record of what
    -- was approved. The agent's details below are copied for the same reason.
    user_id        uuid REFERENCES app_user (id) ON DELETE SET NULL,
    agent_name     varchar(120) NOT NULL,
    agent_phone    varchar(20) NOT NULL,
    agent_email    varchar(160),
    reason         varchar(500),
    status         varchar(20) NOT NULL,
    requested_at   timestamp with time zone NOT NULL,
    reviewed_at    timestamp with time zone,
    reviewed_by_id uuid REFERENCES app_user (id),
    reviewer_note  varchar(500),
    -- What the approval actually erased, kept for the audit trail.
    deleted_entries integer
);

CREATE INDEX ix_deletion_status ON account_deletion_request (status);
CREATE INDEX ix_deletion_phone ON account_deletion_request (agent_phone);
