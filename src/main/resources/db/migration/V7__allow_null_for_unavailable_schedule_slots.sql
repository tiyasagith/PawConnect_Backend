-- V7: Allow NULL start_time, end_time, and slot_duration_minutes for inactive availability days
ALTER TABLE IF EXISTS veterinarian_availability
    ALTER COLUMN start_time DROP NOT NULL;

ALTER TABLE IF EXISTS veterinarian_availability
    ALTER COLUMN end_time DROP NOT NULL;

ALTER TABLE IF EXISTS veterinarian_availability
    ALTER COLUMN slot_duration_minutes DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_vet_avail_vet_day
    ON veterinarian_availability (veterinarian_id, day_of_week);

ALTER TABLE IF EXISTS service_provider_availability
    ALTER COLUMN start_time DROP NOT NULL;

ALTER TABLE IF EXISTS service_provider_availability
    ALTER COLUMN end_time DROP NOT NULL;

ALTER TABLE IF EXISTS service_provider_availability
    ALTER COLUMN slot_duration_minutes DROP NOT NULL;

