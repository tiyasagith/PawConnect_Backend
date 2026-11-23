-- V5: Add explicit configuration flag to service provider availability
ALTER TABLE IF EXISTS service_provider_availability
    ADD COLUMN IF NOT EXISTS is_configured BOOLEAN DEFAULT FALSE;
