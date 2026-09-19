ALTER TABLE addresses ADD COLUMN default_address BOOLEAN NOT NULL DEFAULT false;
CREATE UNIQUE INDEX one_default_address_per_customer ON addresses(customer_id) WHERE default_address;
