CREATE INDEX IF NOT EXISTS idx_audit_logs_user_created
    ON audit_logs (user_id, created_at DESC);
