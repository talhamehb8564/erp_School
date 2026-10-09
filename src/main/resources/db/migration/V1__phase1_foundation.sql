-- =============================================================================
-- Phase 1 — Foundation schema
-- Multi-tenant School ERP SaaS
-- Shared database, discriminator isolation (tenant_id on every school row)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- Tenants (each school is a tenant)
-- -----------------------------------------------------------------------------
CREATE TABLE tenants (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code               VARCHAR(20)  NOT NULL,
    name               VARCHAR(200) NOT NULL,
    legal_name         VARCHAR(200),
    email              VARCHAR(150),
    phone              VARCHAR(30),
    address_line       VARCHAR(300),
    city               VARCHAR(100),
    state              VARCHAR(100),
    country            VARCHAR(100) NOT NULL DEFAULT 'Pakistan',
    postal_code        VARCHAR(20),
    logo_url           VARCHAR(500),
    website            VARCHAR(200),
    status             VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    academic_year      VARCHAR(20),
    academic_session   VARCHAR(50),
    timezone           VARCHAR(50)  NOT NULL DEFAULT 'Asia/Karachi',
    notes              TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uq_tenants_code UNIQUE (code),
    CONSTRAINT chk_tenants_code CHECK (code ~ '^[A-Z0-9]{2,20}$'),
    CONSTRAINT chk_tenants_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'DISABLED', 'EXPIRED'))
);

CREATE INDEX idx_tenants_status ON tenants (status);
CREATE INDEX idx_tenants_name   ON tenants (name);

COMMENT ON TABLE tenants IS 'A school on the SaaS platform. All school data is isolated by tenant_id.';

-- -----------------------------------------------------------------------------
-- Users (platform ERP owner has tenant_id NULL; everyone else belongs to a school)
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID REFERENCES tenants (id) ON DELETE RESTRICT,
    username               VARCHAR(80)  NOT NULL,
    email                  VARCHAR(150),
    password_hash          VARCHAR(255) NOT NULL,
    first_name             VARCHAR(100) NOT NULL,
    last_name              VARCHAR(100) NOT NULL,
    phone                  VARCHAR(30),
    role                   VARCHAR(30)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password   BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at          TIMESTAMPTZ,
    password_changed_at    TIMESTAMPTZ,
    failed_login_attempts  INTEGER      NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN (
        'ERP_OWNER', 'SCHOOL_ADMIN', 'PRINCIPAL', 'TEACHER',
        'ACCOUNT_OFFICER', 'PARENT', 'STUDENT'
    )),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    CONSTRAINT chk_users_erp_owner_tenant CHECK (
        (role = 'ERP_OWNER' AND tenant_id IS NULL)
        OR (role <> 'ERP_OWNER' AND tenant_id IS NOT NULL)
    )
);

CREATE INDEX idx_users_tenant          ON users (tenant_id);
CREATE INDEX idx_users_tenant_role     ON users (tenant_id, role);
CREATE INDEX idx_users_tenant_status   ON users (tenant_id, status);
CREATE INDEX idx_users_role            ON users (role);

COMMENT ON TABLE users IS 'Login accounts. ERP_OWNER is platform-scoped; all other roles are tenant-scoped.';

-- -----------------------------------------------------------------------------
-- Username sequence per tenant + role (GVS-TCH-0001, GVS-STU-0001, ...)
-- -----------------------------------------------------------------------------
CREATE TABLE username_sequences (
    tenant_id   UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    role        VARCHAR(30) NOT NULL,
    next_value  INTEGER     NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, role),
    CONSTRAINT chk_username_seq_role CHECK (role IN (
        'SCHOOL_ADMIN', 'PRINCIPAL', 'TEACHER',
        'ACCOUNT_OFFICER', 'PARENT', 'STUDENT'
    )),
    CONSTRAINT chk_username_seq_next CHECK (next_value >= 1)
);

-- -----------------------------------------------------------------------------
-- Refresh tokens (opaque, stored hashed). Logout / password change revokes them.
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tenant_id   UUID         REFERENCES tenants (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(300),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_tenant  ON refresh_tokens (tenant_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- -----------------------------------------------------------------------------
-- Audit log (foundation for later phases)
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID,
    user_id       UUID,
    username      VARCHAR(80),
    role          VARCHAR(30),
    action        VARCHAR(80)  NOT NULL,
    entity_type   VARCHAR(80),
    entity_id     VARCHAR(80),
    details       JSONB,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant_created ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX idx_audit_user_created   ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_audit_action         ON audit_logs (action);
CREATE INDEX idx_audit_entity         ON audit_logs (entity_type, entity_id);

COMMENT ON TABLE audit_logs IS 'Append-only audit trail. tenant_id is NULL for platform-level actions.';

-- -----------------------------------------------------------------------------
-- updated_at trigger
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
