CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    cashier_id UUID NOT NULL REFERENCES cashiers(id),
    device_id VARCHAR(100) NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(100)
);

CREATE INDEX idx_auth_sessions_cashier_revoked ON auth_sessions(cashier_id, revoked_at);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions(expires_at);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID,
    actor_role VARCHAR(20),
    device_id VARCHAR(100),
    session_id UUID,
    action VARCHAR(100) NOT NULL,
    target VARCHAR(200),
    result VARCHAR(20) NOT NULL,
    reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_created_at ON audit_events(created_at);
CREATE INDEX idx_audit_events_actor_id ON audit_events(actor_id);
CREATE INDEX idx_audit_events_session_id ON audit_events(session_id);
