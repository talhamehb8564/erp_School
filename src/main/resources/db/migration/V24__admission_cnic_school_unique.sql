-- School-wide unique CNIC for active admissions.
-- V24 previously failed on existing duplicates such as
-- tenant d403df14-c4f8-47fd-a1c0-193df7c1aec3 / CNIC 3340105819827
-- (same school, more than one SUBMITTED/REVIEWED/ACCEPTED row — typically
-- a second campus). Do not DELETE. Keep the strongest original request and
-- archive extras as CANCELLED so admin history remains.

ALTER TABLE admission_applications
    DROP CONSTRAINT IF EXISTS chk_admission_app_status;
ALTER TABLE admission_applications
    ADD CONSTRAINT chk_admission_app_status CHECK (status IN (
        'SUBMITTED', 'REVIEWED', 'ACCEPTED', 'REJECTED', 'CANCELLED'
    ));

-- Strip spaces/dashes so 33401-0581982-7 and 3340105819827 are the same key.
UPDATE admission_applications
SET cnic = regexp_replace(cnic, '[^0-9]', '', 'g'),
    updated_at = NOW()
WHERE cnic IS NOT NULL
  AND cnic ~ '[^0-9]';

-- Keep one active row per tenant + CNIC:
-- ACCEPTED, then REVIEWED, then SUBMITTED; earliest created_at wins.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id, cnic
               ORDER BY
                   CASE status
                       WHEN 'ACCEPTED' THEN 0
                       WHEN 'REVIEWED' THEN 1
                       WHEN 'SUBMITTED' THEN 2
                       ELSE 3
                   END,
                   created_at ASC NULLS LAST,
                   id ASC
           ) AS rn
    FROM admission_applications
    WHERE status IN ('SUBMITTED', 'REVIEWED', 'ACCEPTED')
)
UPDATE admission_applications a
SET status = 'CANCELLED',
    updated_at = NOW()
FROM ranked r
WHERE a.id = r.id
  AND r.rn > 1;

DROP INDEX IF EXISTS uq_admission_app_active_cnic;
CREATE UNIQUE INDEX uq_admission_app_active_cnic
    ON admission_applications (tenant_id, cnic)
    WHERE status IN ('SUBMITTED', 'REVIEWED', 'ACCEPTED');
