-- One exam can be assigned to several sections of the same class.

CREATE TABLE online_exam_sections (
    tenant_id  UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    exam_id    UUID NOT NULL REFERENCES online_exams (id) ON DELETE CASCADE,
    section_id UUID NOT NULL,
    PRIMARY KEY (exam_id, section_id)
);

CREATE INDEX idx_online_exam_sections_lookup
    ON online_exam_sections (tenant_id, section_id);

INSERT INTO online_exam_sections (tenant_id, exam_id, section_id)
SELECT tenant_id, id, section_id
FROM online_exams
WHERE section_id IS NOT NULL
ON CONFLICT DO NOTHING;
