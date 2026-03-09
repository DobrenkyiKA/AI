-- liquibase formatted sql
-- changeset liquibase:001-v4

CREATE SCHEMA IF NOT EXISTS public;

DROP TABLE IF EXISTS public.intended_usages CASCADE;
DROP TABLE IF EXISTS public.exclusions CASCADE;
DROP TABLE IF EXISTS public.topics CASCADE;
DROP SEQUENCE IF EXISTS public.topics_id_sequence CASCADE;
DROP TABLE IF EXISTS public.artifacts_step_0 CASCADE;
DROP SEQUENCE IF EXISTS public.artifacts_step_0_id_sequence CASCADE;
DROP TABLE IF EXISTS public.step_1_questions CASCADE;
DROP TABLE IF EXISTS public.step_1_topics_with_questions CASCADE;
DROP SEQUENCE IF EXISTS public.step_1_topics_with_questions_id_sequence CASCADE;
DROP TABLE IF EXISTS public.artifacts_step_1 CASCADE;
DROP SEQUENCE IF EXISTS public.artifacts_step_1_id_sequence CASCADE;
DROP TABLE IF EXISTS public.pipelines CASCADE;
DROP SEQUENCE IF EXISTS public.pipelines_id_sequence CASCADE;

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

CREATE SEQUENCE artifacts_step_0_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.artifacts_step_0
(
    id          BIGINT,
    pipeline_id BIGINT      NOT NULL,
    status      VARCHAR(40) NOT NULL,
    CONSTRAINT pk_artifacts_step_0_id PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_id FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT uq_artifacts_step_0_pipeline_id UNIQUE (pipeline_id)
);

CREATE SEQUENCE artifacts_step_1_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.artifacts_step_1
(
    id          BIGINT,
    pipeline_id BIGINT      NOT NULL,
    status      VARCHAR(40) NOT NULL,
    CONSTRAINT pk_artifacts_step_1_id PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_id_step_1 FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT uq_artifacts_step_1_pipeline_id UNIQUE (pipeline_id)
);

CREATE SEQUENCE topics_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.topics
(
    id                 BIGINT,
    key                VARCHAR(255) NOT NULL,
    artifact_step_0_id BIGINT       NOT NULL,
    title              VARCHAR(255) NOT NULL,
    coverage_area      TEXT         NOT NULL,
    target_audience    VARCHAR(255) NOT NULL,
    experience_level   VARCHAR(255) NOT NULL,
    question_count     INT          NOT NULL,
    CONSTRAINT fk_artifact_step_0_id FOREIGN KEY (artifact_step_0_id) REFERENCES artifacts_step_0 (id) ON DELETE CASCADE,
    CONSTRAINT pk_topics_id PRIMARY KEY (id),
    CONSTRAINT uq_topics_key UNIQUE (key)
);

CREATE SEQUENCE step_1_topics_with_questions_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.step_1_topics_with_questions
(
    id                 BIGINT,
    key                VARCHAR(255) NOT NULL,
    artifact_step_1_id BIGINT       NOT NULL,
    title              VARCHAR(255) NOT NULL,
    CONSTRAINT fk_artifact_step_1_id FOREIGN KEY (artifact_step_1_id) REFERENCES artifacts_step_1 (id) ON DELETE CASCADE,
    CONSTRAINT pk_step_1_topics_with_questions_id PRIMARY KEY (id),
    CONSTRAINT uq_step_1_topics_with_questions_key UNIQUE (key)
);

CREATE TABLE public.step_1_questions
(
    step_1_topic_id BIGINT       NOT NULL,
    question        VARCHAR(255) NOT NULL,
    CONSTRAINT fk_step_1_topic_id FOREIGN KEY (step_1_topic_id) REFERENCES step_1_topics_with_questions (id) ON DELETE CASCADE
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

