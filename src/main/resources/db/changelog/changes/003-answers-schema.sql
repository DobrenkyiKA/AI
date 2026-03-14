-- liquibase formatted sql
-- changeset liquibase:answers-schema

CREATE TABLE public.answers_artifacts
(
    id BIGINT NOT NULL,
    CONSTRAINT pk_answers_artifacts_id PRIMARY KEY (id),
    CONSTRAINT fk_answers_artifact_id_base FOREIGN KEY (id) REFERENCES pipeline_artifacts (id) ON DELETE CASCADE
);

CREATE SEQUENCE topic_qa_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.topic_qa
(
    id                  BIGINT,
    key                 VARCHAR(255) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    answers_artifact_id BIGINT       NOT NULL,
    CONSTRAINT pk_topic_qa_id PRIMARY KEY (id),
    CONSTRAINT fk_topic_qa_answers_artifact_id FOREIGN KEY (answers_artifact_id) REFERENCES answers_artifacts (id) ON DELETE CASCADE,
    CONSTRAINT uq_topic_qa_artifact_key UNIQUE (answers_artifact_id, key)
);

CREATE SEQUENCE qa_entries_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.qa_entries
(
    id            BIGINT,
    question_text TEXT         NOT NULL,
    level         VARCHAR(50)  NOT NULL,
    answer        TEXT,
    short_answer  TEXT,
    topic_qa_id   BIGINT       NOT NULL,
    CONSTRAINT pk_qa_entries_id PRIMARY KEY (id),
    CONSTRAINT fk_qa_entries_topic_qa_id FOREIGN KEY (topic_qa_id) REFERENCES topic_qa (id) ON DELETE CASCADE
);
