CREATE TABLE product_deletions (
    product_id UUID PRIMARY KEY,
    deleted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_product_deletions_deleted_at ON product_deletions(deleted_at);
