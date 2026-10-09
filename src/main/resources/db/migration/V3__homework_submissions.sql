-- =============================================================================
-- Phase: daily school operations — homework submissions
-- (fee OVERDUE and subscription EXPIRED already exist as statuses in V2)
-- =============================================================================

CREATE TABLE homework_submissions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    homework_id    UUID         NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
    student_id     UUID         NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    file_url       VARCHAR(500),
    notes          TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    submitted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_at    TIMESTAMPTZ,
    reviewed_by    UUID,
    teacher_remark VARCHAR(300),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT uq_homework_submission UNIQUE (tenant_id, homework_id, student_id),
    CONSTRAINT chk_hw_sub_status CHECK (status IN ('SUBMITTED', 'LATE', 'REVIEWED'))
);

CREATE INDEX idx_hw_sub_homework ON homework_submissions (tenant_id, homework_id);
CREATE INDEX idx_hw_sub_student ON homework_submissions (tenant_id, student_id);

CREATE TRIGGER trg_homework_submissions_updated_at
    BEFORE UPDATE ON homework_submissions
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
