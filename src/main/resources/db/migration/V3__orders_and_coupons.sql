CREATE TABLE orders (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customers(id),
 status VARCHAR(30) NOT NULL CHECK(status IN ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED')),
 subtotal NUMERIC(19,2) NOT NULL CHECK(subtotal>=0), discount NUMERIC(19,2) NOT NULL CHECK(discount>=0 AND discount<=subtotal),
 tax NUMERIC(19,2) NOT NULL CHECK(tax>=0), total NUMERIC(19,2) NOT NULL CHECK(total=subtotal-discount+tax),
 tax_rate NUMERIC(7,6) NOT NULL CHECK(tax_rate BETWEEN 0 AND 1), currency VARCHAR(3) NOT NULL,
 shipping_address VARCHAR(1200) NOT NULL, coupon_code VARCHAR(40), reservation_expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX orders_customer_created_idx ON orders(customer_id,created_at DESC);
CREATE INDEX orders_created_idx ON orders(created_at);
CREATE INDEX pending_expiry_idx ON orders(reservation_expires_at) WHERE status='PENDING';
CREATE TABLE order_items (
 id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id), product_id UUID NOT NULL REFERENCES products(id),
 product_name VARCHAR(200) NOT NULL, sku VARCHAR(64) NOT NULL, quantity INTEGER NOT NULL CHECK(quantity>0),
 price_at_purchase NUMERIC(19,2) NOT NULL CHECK(price_at_purchase>=0), UNIQUE(order_id,product_id)
);
ALTER TABLE inventory_transactions ADD CONSTRAINT inventory_order_fk FOREIGN KEY(order_id) REFERENCES orders(id);
CREATE TABLE order_status_history (
 id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id), status VARCHAR(30) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX order_history_idx ON order_status_history(order_id,created_at);
CREATE TABLE coupons (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, type VARCHAR(20) NOT NULL CHECK(type IN ('PERCENTAGE','FIXED')),
 value NUMERIC(19,2) NOT NULL CHECK(value>0 AND (type<>'PERCENTAGE' OR value<=100)),
 minimum_amount NUMERIC(19,2) NOT NULL CHECK(minimum_amount>=0), maximum_discount NUMERIC(19,2) CHECK(maximum_discount>0),
 expires_at TIMESTAMPTZ NOT NULL, usage_limit INTEGER NOT NULL CHECK(usage_limit>0),
 used_count INTEGER NOT NULL DEFAULT 0 CHECK(used_count>=0 AND used_count<=usage_limit),
 per_customer_limit INTEGER NOT NULL CHECK(per_customer_limit>0), eligible_customer_id UUID REFERENCES customers(id), active BOOLEAN NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE coupon_usage (
 id UUID PRIMARY KEY, coupon_id UUID NOT NULL REFERENCES coupons(id), customer_id UUID NOT NULL REFERENCES customers(id),
 order_id UUID NOT NULL UNIQUE REFERENCES orders(id) DEFERRABLE INITIALLY DEFERRED, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX coupon_customer_usage_idx ON coupon_usage(coupon_id,customer_id);

