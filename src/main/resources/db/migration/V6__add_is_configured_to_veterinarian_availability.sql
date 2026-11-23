-- V6: Add explicit configuration flag to veterinarian availability
ALTER TABLE IF EXISTS veterinarian_availability
    ADD COLUMN IF NOT EXISTS is_configured BOOLEAN DEFAULT FALSE;
