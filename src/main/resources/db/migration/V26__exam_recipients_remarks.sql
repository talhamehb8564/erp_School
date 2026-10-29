-- Per-student online-exam eligibility (include / exclude + reason)
-- and staff remarks on students. Additive only.

CREATE TABLE online_exam_recipients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    exam_id         UUID         NOT NULL REFERENCES online_exams (id) ON DELETE CASCADE,
    student_id      UUID         NOT NULL,
    excluded        BOOLEAN      NOT NULL DEFAULT FALSE,
    exclude_reason  VARCHAR(40),
    exclude_note    VARCHAR(300),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_online_exam_recipient UNIQUE (tenant_id, exam_id, student_id),
    CONSTRAINT chk_online_exam_exclude_reason CHECK (
        exclude_reason IS NULL OR exclude_reason IN (
            'ABSENT', 'UNPAID_FEES', 'INACTIVE', 'MEDICAL', 'DISCIPLINARY', 'OTHER'
        )
    )
);

CREATE INDEX idx_online_exam_recipients_exam ON online_exam_recipients (tenant_id, exam_id);
CREATE INDEX idx_online_exam_recipients_student ON online_exam_recipients (tenant_id, student_id);

CREATE TABLE student_remarks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    campus_id    UUID         REFERENCES campuses (id) ON DELETE SET NULL,
    student_id   UUID         NOT NULL,
    class_id     UUID,
    section_id   UUID,
    category     VARCHAR(40)  NOT NULL,
    body         TEXT         NOT NULL,
    remark_date  DATE         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT chk_student_remark_category CHECK (category IN (
        'APPRECIATION', 'COMPLAINT', 'ACADEMIC', 'BEHAVIOUR',
        'PARTICIPATION', 'HOMEWORK', 'GENERAL'
    ))
);

CREATE INDEX idx_student_remarks_student ON student_remarks (tenant_id, student_id, remark_date DESC);
CREATE INDEX idx_student_remarks_class ON student_remarks (tenant_id, class_id, section_id, remark_date DESC);
