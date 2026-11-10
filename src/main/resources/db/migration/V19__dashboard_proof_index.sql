-- Account/principal dashboards filter pending proofs by tenant + status.

CREATE INDEX IF NOT EXISTS idx_fee_proofs_status
    ON fee_payment_proofs (tenant_id, status, created_at DESC);
