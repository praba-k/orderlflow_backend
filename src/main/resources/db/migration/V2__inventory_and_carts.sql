CREATE TABLE inventory (
 product_id UUID PRIMARY KEY REFERENCES products(id),
 available INTEGER NOT NULL DEFAULT 0 CHECK(available>=0), reserved INTEGER NOT NULL DEFAULT 0 CHECK(reserved>=0),
 version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE inventory_transactions (
 id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES products(id), order_id UUID,
 actor_id UUID REFERENCES customers(id), operation VARCHAR(30) NOT NULL, quantity INTEGER NOT NULL,
 available_after INTEGER NOT NULL CHECK(available_after>=0), reserved_after INTEGER NOT NULL CHECK(reserved_after>=0),
 reason VARCHAR(250) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX inventory_history_idx ON inventory_transactions(product_id,created_at DESC);
CREATE TABLE carts (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL UNIQUE REFERENCES customers(id),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE cart_items (
 id UUID PRIMARY KEY, cart_id UUID NOT NULL REFERENCES carts(id), product_id UUID NOT NULL REFERENCES products(id),
 quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 1000), UNIQUE(cart_id,product_id)
);

