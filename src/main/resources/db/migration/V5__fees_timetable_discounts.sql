-- Custom fee charge catalog, student discounts, timetable settings, IBAN.

ALTER TABLE school_settings
    ADD COLUMN IF NOT EXISTS iban VARCHAR(34);

CREATE TABLE fee_charge_types (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name          VARCHAR(120)  NOT NULL,
    default_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT uq_fee_charge_types_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_fee_charge_types_tenant ON fee_charge_types (tenant_id, active);

CREATE TRIGGER trg_fee_charge_types_updated_at
    BEFORE UPDATE ON fee_charge_types
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE student_fee_discounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    student_id       UUID          NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    percent          NUMERIC(6,2),
    amount           NUMERIC(12,2),
    reason           VARCHAR(300),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID
);

CREATE INDEX idx_student_fee_discounts_student ON student_fee_discounts (tenant_id, student_id, active);

CREATE TRIGGER trg_student_fee_discounts_updated_at
    BEFORE UPDATE ON student_fee_discounts
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE timetable_settings (
    tenant_id         UUID PRIMARY KEY REFERENCES tenants (id) ON DELETE CASCADE,
    start_time        TIME         NOT NULL DEFAULT TIME '08:00',
    end_time          TIME         NOT NULL DEFAULT TIME '13:30',
    lecture_minutes   INTEGER      NOT NULL DEFAULT 45,
    break_minutes     INTEGER      NOT NULL DEFAULT 15,
    lectures_per_day  INTEGER      NOT NULL DEFAULT 7,
    working_days      VARCHAR(40)  NOT NULL DEFAULT '1,2,3,4,5',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID
);

CREATE TRIGGER trg_timetable_settings_updated_at
    BEFORE UPDATE ON timetable_settings
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE UNIQUE INDEX IF NOT EXISTS uq_salary_deductions_attendance
    ON salary_deductions (tenant_id, teacher_attendance_id)
    WHERE teacher_attendance_id IS NOT NULL;
