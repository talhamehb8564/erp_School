-- Per-school feature flags (missing row = enabled) and public admissions.

CREATE TABLE tenant_features (
    tenant_id  UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    feature    VARCHAR(40) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    PRIMARY KEY (tenant_id, feature)
);

CREATE TABLE admission_settings (
    tenant_id             UUID PRIMARY KEY REFERENCES tenants (id) ON DELETE CASCADE,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    appointment_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    appointment_required  BOOLEAN      NOT NULL DEFAULT FALSE,
    cta_label             VARCHAR(80)  NOT NULL DEFAULT 'Apply for Admission',
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by            UUID
);

CREATE TABLE admission_fields (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    field_key   VARCHAR(60)  NOT NULL,
    label       VARCHAR(120) NOT NULL,
    field_type  VARCHAR(20)  NOT NULL,
    required    BOOLEAN      NOT NULL DEFAULT FALSE,
    locked      BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    options     TEXT,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT uq_admission_fields_key UNIQUE (tenant_id, field_key),
    CONSTRAINT chk_admission_field_type CHECK (field_type IN (
        'TEXT', 'TEXTAREA', 'TEL', 'EMAIL', 'DATE', 'NUMBER', 'SELECT'
    ))
);

CREATE INDEX idx_admission_fields_tenant ON admission_fields (tenant_id, sort_order);

CREATE TABLE school_open_periods (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    campus_id     UUID        REFERENCES campuses (id) ON DELETE CASCADE,
    day_of_week   INTEGER     NOT NULL,
    start_time    TIME        NOT NULL,
    end_time      TIME        NOT NULL,
    slot_minutes  INTEGER     NOT NULL DEFAULT 30,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT chk_open_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_open_span CHECK (end_time > start_time),
    CONSTRAINT chk_slot_minutes CHECK (slot_minutes IN (15, 20, 30, 45, 60))
);

CREATE INDEX idx_open_periods_tenant ON school_open_periods (tenant_id, day_of_week);

CREATE TABLE admission_applications (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    campus_id      UUID         REFERENCES campuses (id) ON DELETE SET NULL,
    student_name   VARCHAR(150) NOT NULL,
    guardian_name  VARCHAR(150) NOT NULL,
    cnic           VARCHAR(20)  NOT NULL,
    phone          VARCHAR(30)  NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT chk_admission_app_status CHECK (status IN ('SUBMITTED', 'REVIEWED', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX idx_admission_apps_tenant ON admission_applications (tenant_id, created_at DESC);

CREATE TABLE admission_appointments (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    application_id  UUID         NOT NULL REFERENCES admission_applications (id) ON DELETE CASCADE,
    campus_id       UUID         REFERENCES campuses (id) ON DELETE SET NULL,
    slot_start      TIMESTAMPTZ  NOT NULL,
    slot_end        TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'BOOKED',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT chk_admission_appt_status CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT chk_admission_appt_span CHECK (slot_end > slot_start)
);

CREATE UNIQUE INDEX uq_admission_appt_slot
    ON admission_appointments (tenant_id, COALESCE(campus_id, '00000000-0000-0000-0000-000000000000'), slot_start)
    WHERE status = 'BOOKED';

CREATE INDEX idx_admission_appt_tenant ON admission_appointments (tenant_id, slot_start);
