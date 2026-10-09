-- Branch (campus) branding, staff campus scope, student CNIC, exam date-sheet.

ALTER TABLE tenants
    ADD COLUMN primary_color VARCHAR(16),
    ADD COLUMN accent_color VARCHAR(16);

ALTER TABLE campuses
    ADD COLUMN logo_url VARCHAR(500),
    ADD COLUMN primary_color VARCHAR(16),
    ADD COLUMN accent_color VARCHAR(16);

ALTER TABLE users
    ADD COLUMN campus_id UUID REFERENCES campuses (id) ON DELETE SET NULL;

CREATE INDEX idx_users_campus ON users (tenant_id, campus_id);

ALTER TABLE students
    ADD COLUMN cnic VARCHAR(15),
    ADD COLUMN inactive_reason VARCHAR(40);

CREATE UNIQUE INDEX idx_students_tenant_cnic
    ON students (tenant_id, cnic)
    WHERE cnic IS NOT NULL;

CREATE INDEX idx_students_campus ON students (tenant_id, campus_id);
CREATE INDEX idx_classes_campus ON school_classes (tenant_id, campus_id);

ALTER TABLE school_settings
    ADD COLUMN logo_url VARCHAR(500),
    ADD COLUMN primary_color VARCHAR(16),
    ADD COLUMN accent_color VARCHAR(16);

ALTER TABLE exam_sessions
    ADD COLUMN announce_at TIMESTAMPTZ,
    ADD COLUMN campus_id UUID REFERENCES campuses (id) ON DELETE SET NULL;

CREATE TABLE exam_date_sheet_entries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    exam_session_id  UUID        NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    campus_id        UUID        REFERENCES campuses (id) ON DELETE SET NULL,
    class_id         UUID        NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    section_id       UUID        REFERENCES sections (id) ON DELETE SET NULL,
    subject_id       UUID        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    exam_date        DATE        NOT NULL,
    start_time       TIME,
    end_time         TIME,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID
);

CREATE INDEX idx_date_sheet_session
    ON exam_date_sheet_entries (tenant_id, exam_session_id, class_id);

CREATE TRIGGER trg_exam_date_sheet_updated_at
    BEFORE UPDATE ON exam_date_sheet_entries
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
