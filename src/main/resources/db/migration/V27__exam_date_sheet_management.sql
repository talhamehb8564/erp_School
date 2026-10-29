-- Per-class date sheets with Theory/Online type, notes, and independent publish.

CREATE TABLE exam_date_sheets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    campus_id        UUID        REFERENCES campuses (id) ON DELETE SET NULL,
    exam_session_id  UUID        NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    class_id         UUID        NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    published        BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at     TIMESTAMPTZ,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT uq_exam_date_sheet UNIQUE (tenant_id, exam_session_id, class_id)
);

CREATE INDEX idx_exam_date_sheets_session
    ON exam_date_sheets (tenant_id, exam_session_id, published);

CREATE TRIGGER trg_exam_date_sheets_updated_at
    BEFORE UPDATE ON exam_date_sheets
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

ALTER TABLE exam_date_sheet_entries
    ADD COLUMN date_sheet_id UUID REFERENCES exam_date_sheets (id) ON DELETE CASCADE,
    ADD COLUMN exam_type VARCHAR(20) NOT NULL DEFAULT 'THEORY',
    ADD COLUMN notes TEXT,
    ADD COLUMN online_exam_id UUID REFERENCES online_exams (id) ON DELETE SET NULL,
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE exam_date_sheet_entries
    ADD CONSTRAINT chk_exam_date_sheet_type CHECK (exam_type IN ('THEORY', 'ONLINE'));

CREATE INDEX idx_date_sheet_entries_sheet
    ON exam_date_sheet_entries (tenant_id, date_sheet_id);

INSERT INTO exam_date_sheets (id, tenant_id, campus_id, exam_session_id, class_id, published, created_at, updated_at)
SELECT gen_random_uuid(), e.tenant_id, e.campus_id, e.exam_session_id, e.class_id, FALSE, NOW(), NOW()
FROM (
    SELECT DISTINCT ON (tenant_id, exam_session_id, class_id)
           tenant_id, campus_id, exam_session_id, class_id
    FROM exam_date_sheet_entries
    ORDER BY tenant_id, exam_session_id, class_id, created_at
) e;

UPDATE exam_date_sheet_entries e
SET date_sheet_id = s.id
FROM exam_date_sheets s
WHERE e.tenant_id = s.tenant_id
  AND e.exam_session_id = s.exam_session_id
  AND e.class_id = s.class_id;
