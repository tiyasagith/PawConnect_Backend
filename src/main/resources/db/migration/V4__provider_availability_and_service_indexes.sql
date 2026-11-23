-- V4: Add slot duration to service provider availability and enforce uniqueness
ALTER TABLE IF EXISTS service_provider_availability
    ADD COLUMN IF NOT EXISTS slot_duration_minutes INT DEFAULT 30;

ALTER TABLE IF EXISTS services
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sp_avail_provider_day
    ON service_provider_availability (provider_id, day_of_week);

CREATE INDEX IF NOT EXISTS idx_services_provider_active
    ON services (provider_id, is_active);
