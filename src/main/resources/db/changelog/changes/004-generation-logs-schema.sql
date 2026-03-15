-- liquibase formatted sql

-- changeset kdob:4
CREATE SEQUENCE IF NOT EXISTS generation_logs_id_sequence START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS generation_logs (
    id BIGINT PRIMARY KEY,
    pipeline_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_generation_logs_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_generation_logs_pipeline ON generation_logs (pipeline_id);
