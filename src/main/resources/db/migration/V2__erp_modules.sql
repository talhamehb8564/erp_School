-- =============================================================================
-- Phase 2–17 — School ERP modules (tenant-isolated)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Document sequences (challans, admission numbers, etc.)
-- -----------------------------------------------------------------------------
CREATE TABLE document_sequences (
    tenant_id   UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    doc_type    VARCHAR(40) NOT NULL,
    next_value  INTEGER     NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, doc_type)
);

-- -----------------------------------------------------------------------------
-- School settings / external payment instructions
-- -----------------------------------------------------------------------------
CREATE TABLE school_settings (
    tenant_id              UUID PRIMARY KEY REFERENCES tenants (id) ON DELETE CASCADE,
    payment_instructions   TEXT,
    bank_name              VARCHAR(150),
    account_title          VARCHAR(150),
    account_number         VARCHAR(80),
    jazzcash               VARCHAR(40),
    easypaisa              VARCHAR(40),
    other_payment_methods  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID
);

CREATE TRIGGER trg_school_settings_updated_at
    BEFORE UPDATE ON school_settings
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- -----------------------------------------------------------------------------
-- SaaS subscriptions (manual payment verification)
-- -----------------------------------------------------------------------------
CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    amount          NUMERIC(12,2),
    currency        VARCHAR(10)  NOT NULL DEFAULT 'PKR',
    period_start    DATE,
    period_end      DATE,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT chk_subscription_status CHECK (status IN (
        'PENDING', 'PAYMENT_SUBMITTED', 'PAID', 'REJECTED', 'EXPIRED', 'SUSPENDED'
    ))
);

CREATE INDEX idx_subscriptions_tenant ON subscriptions (tenant_id, created_at DESC);
CREATE INDEX idx_subscriptions_status ON subscriptions (status);

CREATE TRIGGER trg_subscriptions_updated_at
    BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE subscription_payments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    subscription_id    UUID         NOT NULL REFERENCES subscriptions (id) ON DELETE CASCADE,
    slip_url           VARCHAR(500) NOT NULL,
    transaction_ref    VARCHAR(100),
    status             VARCHAR(30)  NOT NULL DEFAULT 'PAYMENT_SUBMITTED',
    submitted_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_by        UUID,
    reviewed_at        TIMESTAMPTZ,
    rejection_reason   TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT chk_sub_pay_status CHECK (status IN ('PAYMENT_SUBMITTED', 'PAID', 'REJECTED'))
);

CREATE INDEX idx_sub_pay_tenant ON subscription_payments (tenant_id, submitted_at DESC);

CREATE TRIGGER trg_subscription_payments_updated_at
    BEFORE UPDATE ON subscription_payments
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- -----------------------------------------------------------------------------
-- Campuses
-- -----------------------------------------------------------------------------
CREATE TABLE campuses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name         VARCHAR(150) NOT NULL,
    code         VARCHAR(20)  NOT NULL,
    address      VARCHAR(300),
    city         VARCHAR(100),
    phone        VARCHAR(30),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT uq_campuses_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_campuses_tenant ON campuses (tenant_id);

CREATE TRIGGER trg_campuses_updated_at
    BEFORE UPDATE ON campuses
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- -----------------------------------------------------------------------------
-- Academic structure
-- -----------------------------------------------------------------------------
CREATE TABLE school_classes (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    campus_id          UUID         REFERENCES campuses (id) ON DELETE SET NULL,
    name               VARCHAR(80)  NOT NULL,
    grade              VARCHAR(40),
    academic_session   VARCHAR(50),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_classes_tenant_name_session UNIQUE (tenant_id, name, academic_session)
);

CREATE INDEX idx_classes_tenant ON school_classes (tenant_id);

CREATE TRIGGER trg_school_classes_updated_at
    BEFORE UPDATE ON school_classes
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE sections (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    class_id     UUID        NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    name         VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT uq_sections_class_name UNIQUE (tenant_id, class_id, name)
);

CREATE INDEX idx_sections_tenant_class ON sections (tenant_id, class_id);

CREATE TRIGGER trg_sections_updated_at
    BEFORE UPDATE ON sections
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE subjects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name         VARCHAR(120) NOT NULL,
    code         VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT uq_subjects_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_subjects_tenant ON subjects (tenant_id);

CREATE TRIGGER trg_subjects_updated_at
    BEFORE UPDATE ON subjects
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE class_subjects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    class_id     UUID        NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    subject_id   UUID        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT uq_class_subjects UNIQUE (tenant_id, class_id, subject_id)
);

CREATE TABLE teacher_assignments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    teacher_user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    class_id         UUID        NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    section_id       UUID        NOT NULL REFERENCES sections (id) ON DELETE CASCADE,
    subject_id       UUID        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT uq_teacher_assignments UNIQUE (tenant_id, teacher_user_id, class_id, section_id, subject_id)
);

CREATE INDEX idx_teacher_assign_teacher ON teacher_assignments (tenant_id, teacher_user_id);

CREATE TABLE timetable_slots (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    class_id         UUID        NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    section_id       UUID        NOT NULL REFERENCES sections (id) ON DELETE CASCADE,
    subject_id       UUID        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    teacher_user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    day_of_week      INTEGER     NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT chk_timetable_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_timetable_time CHECK (end_time > start_time)
);

CREATE INDEX idx_timetable_class ON timetable_slots (tenant_id, class_id, section_id, day_of_week);
CREATE INDEX idx_timetable_teacher ON timetable_slots (tenant_id, teacher_user_id, day_of_week);

CREATE TRIGGER trg_timetable_slots_updated_at
    BEFORE UPDATE ON timetable_slots
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- -----------------------------------------------------------------------------
-- Students and parent links (one parent login, many children)
-- -----------------------------------------------------------------------------
CREATE TABLE students (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id            UUID         REFERENCES users (id) ON DELETE SET NULL,
    campus_id          UUID         REFERENCES campuses (id) ON DELETE SET NULL,
    class_id           UUID         REFERENCES school_classes (id) ON DELETE SET NULL,
    section_id         UUID         REFERENCES sections (id) ON DELETE SET NULL,
    admission_number   VARCHAR(40)  NOT NULL,
    registration_number VARCHAR(40),
    roll_number        VARCHAR(20),
    photo_url          VARCHAR(500),
    gender             VARCHAR(20),
    date_of_birth      DATE,
    admission_date     DATE,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    address            VARCHAR(300),
    guardian_name      VARCHAR(150),
    guardian_phone     VARCHAR(30),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_students_admission UNIQUE (tenant_id, admission_number),
    CONSTRAINT chk_student_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'GRADUATED', 'LEFT_SCHOOL'))
);

CREATE INDEX idx_students_tenant_class ON students (tenant_id, class_id, section_id);
CREATE INDEX idx_students_user ON students (user_id);

CREATE TRIGGER trg_students_updated_at
    BEFORE UPDATE ON students
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE parent_students (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    parent_user_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    student_id      UUID         NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    relationship    VARCHAR(40),
    is_primary      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_parent_student UNIQUE (tenant_id, parent_user_id, student_id)
);

CREATE INDEX idx_parent_students_parent ON parent_students (tenant_id, parent_user_id);
CREATE INDEX idx_parent_students_student ON parent_students (tenant_id, student_id);

-- -----------------------------------------------------------------------------
-- Attendance
-- -----------------------------------------------------------------------------
CREATE TABLE student_attendance (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    timetable_slot_id  UUID        NOT NULL REFERENCES timetable_slots (id) ON DELETE CASCADE,
    student_id         UUID        NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    teacher_user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    attendance_date    DATE        NOT NULL,
    status             VARCHAR(20) NOT NULL,
    remarks            VARCHAR(300),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT chk_stu_att_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'LEAVE')),
    CONSTRAINT uq_student_attendance UNIQUE (tenant_id, timetable_slot_id, student_id, attendance_date)
);

CREATE INDEX idx_stu_att_date ON student_attendance (tenant_id, attendance_date);
CREATE INDEX idx_stu_att_student ON student_attendance (tenant_id, student_id);

CREATE TRIGGER trg_student_attendance_updated_at
    BEFORE UPDATE ON student_attendance
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE teacher_attendance (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    teacher_user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    attendance_date  DATE        NOT NULL,
    status           VARCHAR(20) NOT NULL,
    remarks          VARCHAR(300),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT chk_tch_att_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'LEAVE')),
    CONSTRAINT uq_teacher_attendance UNIQUE (tenant_id, teacher_user_id, attendance_date)
);

CREATE INDEX idx_tch_att_date ON teacher_attendance (tenant_id, attendance_date);

CREATE TRIGGER trg_teacher_attendance_updated_at
    BEFORE UPDATE ON teacher_attendance
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE salary_deductions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    teacher_user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    teacher_attendance_id   UUID          REFERENCES teacher_attendance (id) ON DELETE SET NULL,
    deduct                  BOOLEAN       NOT NULL DEFAULT FALSE,
    amount                  NUMERIC(12,2) NOT NULL DEFAULT 0,
    reason                  VARCHAR(300),
    month                   DATE          NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID
);

CREATE INDEX idx_salary_deductions_teacher ON salary_deductions (tenant_id, teacher_user_id, month);

CREATE TRIGGER trg_salary_deductions_updated_at
    BEFORE UPDATE ON salary_deductions
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- -----------------------------------------------------------------------------
-- Homework (no online exams)
-- -----------------------------------------------------------------------------
CREATE TABLE homework (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    teacher_user_id  UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    class_id         UUID         NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    section_id       UUID         NOT NULL REFERENCES sections (id) ON DELETE CASCADE,
    subject_id       UUID         NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    due_date         DATE         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID
);

CREATE INDEX idx_homework_class ON homework (tenant_id, class_id, section_id, due_date DESC);

CREATE TRIGGER trg_homework_updated_at
    BEFORE UPDATE ON homework
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE homework_attachments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    homework_id  UUID         NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
    file_name    VARCHAR(200) NOT NULL,
    file_url     VARCHAR(500) NOT NULL,
    content_type VARCHAR(120),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID
);

-- -----------------------------------------------------------------------------
-- Offline examination / results
-- -----------------------------------------------------------------------------
CREATE TABLE exam_sessions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name          VARCHAR(150) NOT NULL,
    academic_session VARCHAR(50),
    start_date    DATE,
    end_date      DATE,
    published     BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID
);

CREATE INDEX idx_exam_sessions_tenant ON exam_sessions (tenant_id, created_at DESC);

CREATE TRIGGER trg_exam_sessions_updated_at
    BEFORE UPDATE ON exam_sessions
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE exam_results (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    exam_session_id  UUID          NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    student_id       UUID          NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    subject_id       UUID          NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    total_marks      NUMERIC(8,2)  NOT NULL,
    obtained_marks   NUMERIC(8,2)  NOT NULL,
    percentage       NUMERIC(6,2)  NOT NULL,
    grade            VARCHAR(5)    NOT NULL,
    pass_status      VARCHAR(10)   NOT NULL,
    remarks          VARCHAR(300),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT chk_result_pass CHECK (pass_status IN ('PASS', 'FAIL')),
    CONSTRAINT uq_exam_result UNIQUE (tenant_id, exam_session_id, student_id, subject_id)
);

CREATE INDEX idx_exam_results_student ON exam_results (tenant_id, student_id);

CREATE TRIGGER trg_exam_results_updated_at
    BEFORE UPDATE ON exam_results
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- -----------------------------------------------------------------------------
-- Fees / monthly challans / payment proofs
-- -----------------------------------------------------------------------------
CREATE TABLE fee_structures (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    class_id         UUID          REFERENCES school_classes (id) ON DELETE SET NULL,
    name             VARCHAR(120)  NOT NULL,
    academic_year    VARCHAR(20),
    tuition_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID
);

CREATE INDEX idx_fee_structures_tenant ON fee_structures (tenant_id);

CREATE TRIGGER trg_fee_structures_updated_at
    BEFORE UPDATE ON fee_structures
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE fee_challans (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    student_id         UUID          NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
    challan_number     VARCHAR(40)   NOT NULL,
    month              DATE          NOT NULL,
    issue_date         DATE          NOT NULL,
    due_date           DATE          NOT NULL,
    tuition_fee        NUMERIC(12,2) NOT NULL DEFAULT 0,
    previous_outstanding NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    additional_charges NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_payable      NUMERIC(12,2) NOT NULL DEFAULT 0,
    status             VARCHAR(40)   NOT NULL DEFAULT 'UNPAID',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_challan_number UNIQUE (tenant_id, challan_number),
    CONSTRAINT uq_challan_student_month UNIQUE (tenant_id, student_id, month),
    CONSTRAINT chk_challan_status CHECK (status IN (
        'UNPAID', 'PAYMENT_UNDER_VERIFICATION', 'PAID', 'PAYMENT_REJECTED', 'OVERDUE'
    ))
);

CREATE INDEX idx_challans_student ON fee_challans (tenant_id, student_id, month DESC);
CREATE INDEX idx_challans_status ON fee_challans (tenant_id, status);

CREATE TRIGGER trg_fee_challans_updated_at
    BEFORE UPDATE ON fee_challans
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE challan_charges (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    challan_id   UUID          NOT NULL REFERENCES fee_challans (id) ON DELETE CASCADE,
    name         VARCHAR(120)  NOT NULL,
    amount       NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID
);

CREATE TABLE fee_payment_proofs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    challan_id         UUID         NOT NULL REFERENCES fee_challans (id) ON DELETE CASCADE,
    slip_url           VARCHAR(500) NOT NULL,
    transaction_ref    VARCHAR(100),
    status             VARCHAR(40)  NOT NULL DEFAULT 'PAYMENT_UNDER_VERIFICATION',
    remarks            TEXT,
    reviewed_by        UUID,
    reviewed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT chk_fee_proof_status CHECK (status IN (
        'PAYMENT_UNDER_VERIFICATION', 'PAID', 'PAYMENT_REJECTED'
    ))
);

CREATE INDEX idx_fee_proofs_challan ON fee_payment_proofs (tenant_id, challan_id, created_at DESC);

CREATE TRIGGER trg_fee_payment_proofs_updated_at
    BEFORE UPDATE ON fee_payment_proofs
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- -----------------------------------------------------------------------------
-- Staff salaries (teachers, principal, account officers, admin — not support staff)
-- -----------------------------------------------------------------------------
CREATE TABLE staff_salaries (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    staff_user_id           UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    month                   DATE          NOT NULL,
    base_salary             NUMERIC(12,2) NOT NULL DEFAULT 0,
    attendance_deductions   NUMERIC(12,2) NOT NULL DEFAULT 0,
    other_deductions        NUMERIC(12,2) NOT NULL DEFAULT 0,
    bonuses                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    final_salary            NUMERIC(12,2) NOT NULL DEFAULT 0,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
    payment_date            DATE,
    notes                   VARCHAR(300),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    CONSTRAINT uq_staff_salary_month UNIQUE (tenant_id, staff_user_id, month),
    CONSTRAINT chk_salary_status CHECK (status IN ('PAID', 'UNPAID', 'PENDING'))
);

CREATE INDEX idx_staff_salaries_month ON staff_salaries (tenant_id, month);

CREATE TRIGGER trg_staff_salaries_updated_at
    BEFORE UPDATE ON staff_salaries
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE staff_profiles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id       UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    base_salary   NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT uq_staff_profile_user UNIQUE (tenant_id, user_id)
);

CREATE TRIGGER trg_staff_profiles_updated_at
    BEFORE UPDATE ON staff_profiles
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- -----------------------------------------------------------------------------
-- Announcements, notifications, calendar
-- -----------------------------------------------------------------------------
CREATE TABLE announcements (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    body           TEXT         NOT NULL,
    audience       VARCHAR(30)  NOT NULL DEFAULT 'ALL',
    class_id       UUID         REFERENCES school_classes (id) ON DELETE SET NULL,
    section_id     UUID         REFERENCES sections (id) ON DELETE SET NULL,
    attachment_url VARCHAR(500),
    publish_date   DATE         NOT NULL DEFAULT CURRENT_DATE,
    expiry_date    DATE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT chk_announcement_audience CHECK (audience IN (
        'ALL', 'TEACHERS', 'PARENTS', 'STUDENTS', 'CLASS', 'SECTION'
    ))
);

CREATE INDEX idx_announcements_tenant ON announcements (tenant_id, publish_date DESC);

CREATE TRIGGER trg_announcements_updated_at
    BEFORE UPDATE ON announcements
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE notifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type          VARCHAR(40)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body          TEXT,
    entity_type   VARCHAR(80),
    entity_id     VARCHAR(80),
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID
);

CREATE INDEX idx_notifications_user ON notifications (tenant_id, user_id, created_at DESC);

CREATE TABLE school_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    event_type    VARCHAR(40)  NOT NULL DEFAULT 'EVENT',
    start_date    DATE         NOT NULL,
    end_date      DATE,
    audience      VARCHAR(30)  NOT NULL DEFAULT 'ALL',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT chk_event_type CHECK (event_type IN (
        'HOLIDAY', 'EVENT', 'PTM', 'EXAM', 'ACTIVITY', 'OTHER'
    ))
);

CREATE INDEX idx_school_events_dates ON school_events (tenant_id, start_date);

CREATE TRIGGER trg_school_events_updated_at
    BEFORE UPDATE ON school_events
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE stored_files (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    original_name VARCHAR(200) NOT NULL,
    stored_path   VARCHAR(500) NOT NULL,
    content_type  VARCHAR(120),
    size_bytes    BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID
);
