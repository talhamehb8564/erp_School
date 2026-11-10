ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS notice_attachments_json TEXT;

ALTER TABLE announcements
    ADD COLUMN IF NOT EXISTS attachments_json TEXT;

ALTER TABLE stored_files
    ADD COLUMN IF NOT EXISTS file_data BYTEA;
