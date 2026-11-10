ALTER TABLE school_settings
    ADD COLUMN absent_deduction NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN late_deduction NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN leave_deduction NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN waive_attendance_deduction BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE staff_salaries
    ADD COLUMN payment_proof_url VARCHAR(500),
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN verified_by UUID;
