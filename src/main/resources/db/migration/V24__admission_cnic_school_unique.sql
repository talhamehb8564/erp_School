-- Active admission CNIC is unique per school, not per campus.
-- Choosing another branch must not create a second request.

DROP INDEX IF EXISTS uq_admission_app_active_cnic;
CREATE UNIQUE INDEX uq_admission_app_active_cnic
    ON admission_applications (tenant_id, cnic)
    WHERE status IN ('SUBMITTED', 'REVIEWED', 'ACCEPTED');
