ALTER TABLE documents
    ADD COLUMN cashier_id UUID;

ALTER TABLE documents
    ADD COLUMN device_id VARCHAR(255);

UPDATE documents
SET cashier_id = '00000000-0000-0000-0000-000000000000',
    device_id = 'LEGACY_DEVICE';

ALTER TABLE documents
    ALTER COLUMN cashier_id SET NOT NULL;

ALTER TABLE documents
    ALTER COLUMN device_id SET NOT NULL;

CREATE INDEX idx_documents_cashier_device_timestamp
    ON documents(cashier_id, device_id, timestamp);