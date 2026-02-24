-- liquibase formatted sql
-- changeset liquibase:001

CREATE SCHEMA IF NOT EXISTS public;

CREATE SEQUENCE pipelines_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.pipelines
(
    id         BIGINT,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(40)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_pipelines_id PRIMARY KEY (id),
    CONSTRAINT uq_pipelines_name UNIQUE (name)
);

CREATE SEQUENCE topics_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.topics
(
    id               BIGINT,
    key              VARCHAR(255) NOT NULL,
    pipeline_id      BIGINT       NOT NULL,
    title            VARCHAR(255) NOT NULL,
    description      TEXT         NOT NULL,
    target_audience  VARCHAR(255) NOT NULL,
    experience_level VARCHAR(255) NOT NULL,
    question_count   INT          NOT NULL,
    CONSTRAINT fk_pipeline_id FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT pk_topics_id PRIMARY KEY (id),
    CONSTRAINT uq_topics_key UNIQUE (key)
);

CREATE TABLE public.exclusions
(
    topic_id  BIGINT       NOT NULL,
    exclusion VARCHAR(255) NOT NULL,
    CONSTRAINT fk_topic_id FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE
);

CREATE TABLE public.intended_usages
(
    topic_id       BIGINT       NOT NULL,
    intended_usage VARCHAR(255) NOT NULL,
    CONSTRAINT fk_topic_id FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE
);

