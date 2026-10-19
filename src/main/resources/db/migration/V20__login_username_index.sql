-- Login uses lower(username) / lower(email). Unique username is case-sensitive.
CREATE INDEX IF NOT EXISTS idx_users_username_lower ON users (lower(username));
CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users (lower(email));
