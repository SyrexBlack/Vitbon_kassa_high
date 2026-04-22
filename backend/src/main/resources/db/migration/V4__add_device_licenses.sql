CREATE TABLE device_licenses (
    device_id VARCHAR(100) PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'GRACE_PERIOD')),
    expiry_date TIMESTAMPTZ,
    grace_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (
        CASE
            WHEN status = 'ACTIVE' THEN grace_until IS NULL
            WHEN status = 'GRACE_PERIOD' THEN grace_until IS NOT NULL
            WHEN status = 'EXPIRED' THEN TRUE
            ELSE FALSE
        END
    )
);

CREATE INDEX idx_device_licenses_updated_at ON device_licenses(updated_at);
