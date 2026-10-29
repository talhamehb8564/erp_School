-- Range history on student portals: /attendance/me and /attendance/students/{id}?from&to
CREATE INDEX IF NOT EXISTS idx_stu_att_student_date
    ON student_attendance (tenant_id, student_id, attendance_date DESC);
