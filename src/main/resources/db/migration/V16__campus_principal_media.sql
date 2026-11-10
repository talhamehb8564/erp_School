-- Branch-specific Principal profiles, extra photographs, campus map, WhatsApp.

ALTER TABLE campuses
    ADD COLUMN IF NOT EXISTS principal_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS principal_title VARCHAR(120),
    ADD COLUMN IF NOT EXISTS principal_photo_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS principal_message TEXT,
    ADD COLUMN IF NOT EXISTS principal_published BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS principal_photos_json TEXT,
    ADD COLUMN IF NOT EXISTS map_lat NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS map_lng NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS map_place VARCHAR(300),
    ADD COLUMN IF NOT EXISTS whatsapp_url VARCHAR(300);

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS whatsapp_url VARCHAR(300),
    ADD COLUMN IF NOT EXISTS principal_photos_json TEXT;
