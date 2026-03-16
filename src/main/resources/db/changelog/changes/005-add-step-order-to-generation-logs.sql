-- liquibase formatted sql

-- changeset kdob:5
ALTER TABLE generation_logs ADD COLUMN step_order INTEGER;

-- Optional: index to speed up filtering by step within a pipeline
CREATE INDEX idx_generation_logs_pipeline_step ON generation_logs (pipeline_id, step_order);
