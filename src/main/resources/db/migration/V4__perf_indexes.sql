-- Additive indexes for list/batch reads used by portals and homework attachments.

CREATE INDEX IF NOT EXISTS idx_homework_attachments_homework
    ON homework_attachments (homework_id);

CREATE INDEX IF NOT EXISTS idx_exam_results_session
    ON exam_results (tenant_id, exam_session_id);
