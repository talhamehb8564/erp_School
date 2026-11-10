-- One active admission per school campus + CNIC. Rejected/cancelled may re-apply.
-- Do not make CNIC unique across tenants.

ALTER TABLE admission_applications
    DROP CONSTRAINT IF EXISTS chk_admission_app_status;
ALTER TABLE admission_applications
    ADD CONSTRAINT chk_admission_app_status CHECK (status IN (
        'SUBMITTED', 'REVIEWED', 'ACCEPTED', 'REJECTED', 'CANCELLED'
    ));

DROP INDEX IF EXISTS uq_admission_app_active_cnic;
CREATE UNIQUE INDEX uq_admission_app_active_cnic
    ON admission_applications (
        tenant_id,
        COALESCE(campus_id, '00000000-0000-0000-0000-000000000000'),
        cnic
    )
    WHERE status IN ('SUBMITTED', 'REVIEWED', 'ACCEPTED');
