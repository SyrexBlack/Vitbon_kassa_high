CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE cashiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    pin_hash VARCHAR(256) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'SENIOR_CASHIER', 'CASHIER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE shifts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cashier_id UUID NOT NULL REFERENCES cashiers(id),
    device_id VARCHAR(100) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    total_cash BIGINT DEFAULT 0,
    total_card BIGINT DEFAULT 0
);

CREATE TABLE checks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    local_uuid VARCHAR(100) NOT NULL UNIQUE,
    shift_id UUID REFERENCES shifts(id),
    cashier_id UUID REFERENCES cashiers(id),
    device_id VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('SALE','RETURN','CORRECTION','CASH_IN','CASH_OUT')),
    fiscal_sign VARCHAR(100),
    ofd_response JSONB,
    ffd_version VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_SYNC',
    subtotal BIGINT NOT NULL DEFAULT 0,
    discount BIGINT NOT NULL DEFAULT 0,
    total BIGINT NOT NULL DEFAULT 0,
    tax_amount BIGINT NOT NULL DEFAULT 0,
    payment_type VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ
);

CREATE TABLE check_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    check_id UUID NOT NULL REFERENCES checks(id) ON DELETE CASCADE,
    product_id UUID,
    barcode VARCHAR(100),
    name VARCHAR(255) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL DEFAULT 1,
    price BIGINT NOT NULL,
    discount BIGINT NOT NULL DEFAULT 0,
    vat_rate VARCHAR(10) NOT NULL,
    total BIGINT NOT NULL
);

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    barcode VARCHAR(100) UNIQUE,
    name VARCHAR(255) NOT NULL,
    article VARCHAR(100),
    price BIGINT NOT NULL,
    vat_rate VARCHAR(10) NOT NULL DEFAULT 'VAT_22',
    category_id UUID,
    stock NUMERIC(15,3) DEFAULT 0,
    egais_flag BOOLEAN NOT NULL DEFAULT FALSE,
    chaseznak_flag BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    parent_id UUID REFERENCES categories(id)
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cashier_id UUID REFERENCES cashiers(id),
    device_id VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    details JSONB,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checks_shift ON checks(shift_id);
CREATE INDEX idx_checks_status ON checks(status);
CREATE INDEX idx_checks_created ON checks(created_at);
CREATE INDEX idx_products_barcode ON products(barcode);
CREATE INDEX idx_audit_cashier ON audit_log(cashier_id);
