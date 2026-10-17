-- Additive indexes for dashboard, parent multi-child progress, and catalogue reads.

CREATE INDEX IF NOT EXISTS idx_stu_att_student_date
    ON student_attendance (tenant_id, student_id, attendance_date);

CREATE INDEX IF NOT EXISTS idx_tch_att_teacher_date
    ON teacher_attendance (tenant_id, teacher_user_id, attendance_date);

CREATE INDEX IF NOT EXISTS idx_exam_sessions_published
    ON exam_sessions (tenant_id, published, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_exam_results_student_session
    ON exam_results (tenant_id, student_id, exam_session_id);

CREATE INDEX IF NOT EXISTS idx_homework_teacher
    ON homework (tenant_id, teacher_user_id, due_date DESC);

CREATE INDEX IF NOT EXISTS idx_users_campus_role
    ON users (tenant_id, campus_id, role);

CREATE INDEX IF NOT EXISTS idx_challan_charges_challan
    ON challan_charges (challan_id);

CREATE INDEX IF NOT EXISTS idx_notifications_unread
    ON notifications (tenant_id, user_id)
    WHERE read_at IS NULL;
