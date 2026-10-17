-- Hibernate maps String length=1 to VARCHAR, not CHAR (bpchar).
-- V12 already applied CHAR(1) for MCQ keys; align without rewriting V12.

ALTER TABLE online_exam_answers
    ALTER COLUMN option_choice TYPE VARCHAR(1);

ALTER TABLE online_exam_questions
    ALTER COLUMN correct_option TYPE VARCHAR(1);
