-- Additive indexes for active/inactive student lists and class/section bulk updates.

CREATE INDEX IF NOT EXISTS idx_students_tenant_status
    ON students (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_students_tenant_class_status
    ON students (tenant_id, class_id, section_id, status);

CREATE INDEX IF NOT EXISTS idx_students_tenant_campus_status
    ON students (tenant_id, campus_id, status);
