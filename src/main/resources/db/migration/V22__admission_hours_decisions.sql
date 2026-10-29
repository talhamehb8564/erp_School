-- Default hours + closed days, and appointment accept/reject without dropping history.

ALTER TABLE admission_settings
    ADD COLUMN IF NOT EXISTS closed_days VARCHAR(20) NOT NULL DEFAULT '7',
    ADD COLUMN IF NOT EXISTS open_start TIME NOT NULL DEFAULT TIME '09:00',
    ADD COLUMN IF NOT EXISTS open_end TIME NOT NULL DEFAULT TIME '13:00',
    ADD COLUMN IF NOT EXISTS slot_minutes INTEGER NOT NULL DEFAULT 30;

ALTER TABLE admission_settings
    DROP CONSTRAINT IF EXISTS chk_admission_slot_minutes;
ALTER TABLE admission_settings
    ADD CONSTRAINT chk_admission_slot_minutes CHECK (slot_minutes IN (15, 20, 30, 45, 60));

ALTER TABLE admission_appointments
    DROP CONSTRAINT IF EXISTS chk_admission_appt_status;
ALTER TABLE admission_appointments
    ADD CONSTRAINT chk_admission_appt_status CHECK (status IN (
        'BOOKED', 'CANCELLED', 'COMPLETED', 'ACCEPTED', 'REJECTED'
    ));

DROP INDEX IF EXISTS uq_admission_appt_slot;
CREATE UNIQUE INDEX uq_admission_appt_slot
    ON admission_appointments (tenant_id, COALESCE(campus_id, '00000000-0000-0000-0000-000000000000'), slot_start)
    WHERE status IN ('BOOKED', 'ACCEPTED');
