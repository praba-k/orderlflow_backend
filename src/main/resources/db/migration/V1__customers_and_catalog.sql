CREATE TABLE customers (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 name VARCHAR(120) NOT NULL, active BOOLEAN NOT NULL DEFAULT true,
 role VARCHAR(30) NOT NULL CHECK (role IN ('CUSTOMER','ADMIN','INVENTORY_MANAGER')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CHECK (email=lower(email))
);
CREATE TABLE addresses (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customers(id),
 recipient VARCHAR(120) NOT NULL, line1 VARCHAR(200) NOT NULL, line2 VARCHAR(200),
 city VARCHAR(100) NOT NULL, postal_code VARCHAR(20) NOT NULL, country VARCHAR(2) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX addresses_customer_idx ON addresses(customer_id);
CREATE TABLE categories (
 id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE products (
 id UUID PRIMARY KEY, name VARCHAR(200) NOT NULL, description VARCHAR(4000),
 category_id UUID NOT NULL REFERENCES categories(id), price NUMERIC(19,2) NOT NULL CHECK(price>=0),
 sku VARCHAR(64) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL CHECK(status IN ('ACTIVE','INACTIVE')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX products_category_status_idx ON products(category_id,status);

