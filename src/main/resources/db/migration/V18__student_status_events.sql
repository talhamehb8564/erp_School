-- Per-student status history. Does not delete or rewrite past reasons.

ALTER TABLE students
    ADD COLUMN IF NOT EXISTS inactive_note VARCHAR(300);

CREATE TABLE student_status_events (
    id              UUID         PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    campus_id       UUID,
    student_id      UUID         NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
    from_status     VARCHAR(20)  NOT NULL,
    to_status       VARCHAR(20)  NOT NULL,
    reason_code     VARCHAR(40)  NOT NULL,
    reason_note     VARCHAR(300),
    actor_user_id   UUID,
    actor_username  VARCHAR(80),
    bulk            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_student_status_events_student
    ON student_status_events (tenant_id, student_id, created_at DESC);
