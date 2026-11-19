-- Promo codes can be disabled/enabled by admins.
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
