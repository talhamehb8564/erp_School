-- Public website: principal profile, map, featured notice, contact inquiries.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS principal_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS principal_title VARCHAR(120),
    ADD COLUMN IF NOT EXISTS principal_photo_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS principal_message TEXT,
    ADD COLUMN IF NOT EXISTS principal_published BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS map_lat NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS map_lng NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS map_place VARCHAR(300),
    ADD COLUMN IF NOT EXISTS notice_title VARCHAR(200),
    ADD COLUMN IF NOT EXISTS notice_body TEXT,
    ADD COLUMN IF NOT EXISTS notice_video_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS notice_poster_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS notice_published BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS website_inquiries (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name       VARCHAR(150) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    phone      VARCHAR(40),
    message    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_website_inquiries_tenant
    ON website_inquiries (tenant_id, created_at DESC);
