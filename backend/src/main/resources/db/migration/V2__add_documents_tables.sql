CREATE TABLE documents (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_items (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    product_id UUID,
    barcode VARCHAR(100),
    name VARCHAR(255) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    reason TEXT
);

CREATE INDEX idx_documents_timestamp ON documents(timestamp);
CREATE INDEX idx_document_items_document_id ON document_items(document_id);
