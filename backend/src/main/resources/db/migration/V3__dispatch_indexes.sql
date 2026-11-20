-- Composite indexes for the dispatcher and maintenance sweeps that run every tick.
CREATE INDEX IF NOT EXISTS idx_payments_status_expires ON payments (status, expires_at);
CREATE INDEX IF NOT EXISTS idx_orders_status_updated ON orders (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_job_offers_order_status_exp ON job_offers (order_id, status, expires_at);
CREATE INDEX IF NOT EXISTS idx_tech_verified_online ON technician_profiles (verification_status, is_online);
