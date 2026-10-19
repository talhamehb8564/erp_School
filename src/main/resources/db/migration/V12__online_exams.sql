-- Online examinations (separate from offline exam_sessions / marksheets).

CREATE TABLE online_exams (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    campus_id          UUID         REFERENCES campuses (id) ON DELETE SET NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    class_id           UUID         NOT NULL,
    section_id         UUID         NOT NULL,
    subject_id         UUID         NOT NULL,
    exam_date          DATE,
    start_at           TIMESTAMPTZ  NOT NULL,
    duration_minutes   INT          NOT NULL,
    total_marks        NUMERIC(8,2) NOT NULL DEFAULT 0,
    passing_marks      NUMERIC(8,2),
    mcq_marks          NUMERIC(8,2) NOT NULL DEFAULT 1,
    short_marks        NUMERIC(8,2) NOT NULL DEFAULT 2,
    long_marks         NUMERIC(8,2) NOT NULL DEFAULT 5,
    short_required     INT,
    long_required      INT,
    status             VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    published_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT chk_online_exam_status CHECK (status IN (
        'DRAFT', 'SCHEDULED', 'LIVE', 'CLOSED', 'RESULTS_PUBLISHED'
    )),
    CONSTRAINT chk_online_exam_duration CHECK (duration_minutes > 0)
);

CREATE INDEX idx_online_exams_tenant_start ON online_exams (tenant_id, start_at DESC);
CREATE INDEX idx_online_exams_class ON online_exams (tenant_id, class_id, section_id);
CREATE INDEX idx_online_exams_creator ON online_exams (tenant_id, created_by);

CREATE TABLE online_exam_questions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    exam_id            UUID         NOT NULL REFERENCES online_exams (id) ON DELETE CASCADE,
    question_type      VARCHAR(20)  NOT NULL,
    prompt             TEXT         NOT NULL,
    marks              NUMERIC(8,2) NOT NULL,
    sort_order         INT          NOT NULL DEFAULT 0,
    option_a           TEXT,
    option_b           TEXT,
    option_c           TEXT,
    option_d           TEXT,
    correct_option     CHAR(1),
    reference_answer   TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT chk_online_q_type CHECK (question_type IN ('MCQ', 'SHORT', 'LONG'))
);

CREATE INDEX idx_online_exam_questions_exam ON online_exam_questions (tenant_id, exam_id, sort_order);

CREATE TABLE online_exam_attempts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    exam_id            UUID         NOT NULL REFERENCES online_exams (id) ON DELETE CASCADE,
    student_id         UUID         NOT NULL,
    started_at         TIMESTAMPTZ  NOT NULL,
    deadline_at        TIMESTAMPTZ  NOT NULL,
    submitted_at       TIMESTAMPTZ,
    auto_submitted     BOOLEAN      NOT NULL DEFAULT FALSE,
    status             VARCHAR(30)  NOT NULL DEFAULT 'IN_PROGRESS',
    mcq_obtained       NUMERIC(8,2) NOT NULL DEFAULT 0,
    short_obtained     NUMERIC(8,2) NOT NULL DEFAULT 0,
    long_obtained      NUMERIC(8,2) NOT NULL DEFAULT 0,
    obtained_marks     NUMERIC(8,2) NOT NULL DEFAULT 0,
    total_marks        NUMERIC(8,2) NOT NULL DEFAULT 0,
    percentage         NUMERIC(6,2) NOT NULL DEFAULT 0,
    grade              VARCHAR(5),
    pass_status        VARCHAR(10),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_online_attempt UNIQUE (tenant_id, exam_id, student_id),
    CONSTRAINT chk_online_attempt_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED'))
);

CREATE INDEX idx_online_attempts_exam ON online_exam_attempts (tenant_id, exam_id, status);
CREATE INDEX idx_online_attempts_student ON online_exam_attempts (tenant_id, student_id);

CREATE TABLE online_exam_answers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    attempt_id         UUID         NOT NULL REFERENCES online_exam_attempts (id) ON DELETE CASCADE,
    question_id        UUID         NOT NULL REFERENCES online_exam_questions (id) ON DELETE CASCADE,
    option_choice      CHAR(1),
    text_answer        TEXT,
    obtained_marks     NUMERIC(8,2),
    auto_graded        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_online_answer UNIQUE (attempt_id, question_id)
);

CREATE INDEX idx_online_answers_attempt ON online_exam_answers (tenant_id, attempt_id);
