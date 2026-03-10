-- liquibase formatted sql
-- changeset liquibase:001-v4

CREATE SCHEMA IF NOT EXISTS public;

DROP TABLE IF EXISTS public.pipeline_topics CASCADE;
DROP SEQUENCE IF EXISTS public.pipeline_topics_id_sequence CASCADE;
DROP TABLE IF EXISTS public.topics_artifacts CASCADE;
DROP SEQUENCE IF EXISTS public.topics_artifacts_id_sequence CASCADE;
DROP TABLE IF EXISTS public.pipeline_questions CASCADE;
DROP TABLE IF EXISTS public.pipeline_topic_questions CASCADE;
DROP SEQUENCE IF EXISTS public.pipeline_topic_questions_id_sequence CASCADE;
DROP TABLE IF EXISTS public.questions_artifacts CASCADE;
DROP SEQUENCE IF EXISTS public.questions_artifacts_id_sequence CASCADE;
DROP TABLE IF EXISTS public.pipeline_steps CASCADE;
DROP SEQUENCE IF EXISTS public.pipeline_steps_id_sequence CASCADE;
DROP TABLE IF EXISTS public.prompts CASCADE;
DROP SEQUENCE IF EXISTS public.prompts_id_sequence CASCADE;
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
    topic_key  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_pipelines_id PRIMARY KEY (id),
    CONSTRAINT uq_pipelines_name UNIQUE (name)
);

CREATE SEQUENCE prompts_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.prompts
(
    id      BIGINT,
    type    VARCHAR(10)  NOT NULL,
    name    VARCHAR(255) NOT NULL,
    content TEXT         NOT NULL,
    CONSTRAINT pk_prompts_id PRIMARY KEY (id),
    CONSTRAINT uq_prompts_name UNIQUE (name)
);

CREATE SEQUENCE pipeline_steps_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.pipeline_steps
(
    id               BIGINT,
    pipeline_id      BIGINT       NOT NULL,
    step_type        VARCHAR(255) NOT NULL,
    step_order       INT          NOT NULL,
    system_prompt_id BIGINT,
    user_prompt_id   BIGINT,
    CONSTRAINT pk_pipeline_steps_id PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_id_steps FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT fk_system_prompt_id FOREIGN KEY (system_prompt_id) REFERENCES prompts (id),
    CONSTRAINT fk_user_prompt_id FOREIGN KEY (user_prompt_id) REFERENCES prompts (id)
);

CREATE SEQUENCE topics_artifacts_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.topics_artifacts
(
    id          BIGINT,
    pipeline_id BIGINT      NOT NULL,
    status      VARCHAR(40) NOT NULL,
    CONSTRAINT pk_topics_artifacts_id PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_id FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT uq_topics_artifacts_pipeline_id UNIQUE (pipeline_id)
);

CREATE SEQUENCE questions_artifacts_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.questions_artifacts
(
    id          BIGINT,
    pipeline_id BIGINT      NOT NULL,
    status      VARCHAR(40) NOT NULL,
    CONSTRAINT pk_questions_artifacts_id PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_id_questions FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT uq_questions_artifacts_pipeline_id UNIQUE (pipeline_id)
);

CREATE SEQUENCE pipeline_topics_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.pipeline_topics
(
    id                 BIGINT,
    key                VARCHAR(255) NOT NULL,
    topics_artifact_id BIGINT       NOT NULL,
    name               VARCHAR(255) NOT NULL,
    parent_topic_key   VARCHAR(255),
    coverage_area      TEXT         NOT NULL,
    CONSTRAINT fk_topics_artifact_id FOREIGN KEY (topics_artifact_id) REFERENCES topics_artifacts (id) ON DELETE CASCADE,
    CONSTRAINT pk_pipeline_topics_id PRIMARY KEY (id),
    CONSTRAINT uq_pipeline_topics_artifact_key UNIQUE (topics_artifact_id, key)
);

CREATE SEQUENCE pipeline_topic_questions_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.pipeline_topic_questions
(
    id                     BIGINT,
    key                    VARCHAR(255) NOT NULL,
    questions_artifact_id  BIGINT       NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    CONSTRAINT fk_questions_artifact_id FOREIGN KEY (questions_artifact_id) REFERENCES questions_artifacts (id) ON DELETE CASCADE,
    CONSTRAINT pk_pipeline_topic_questions_id PRIMARY KEY (id),
    CONSTRAINT uq_pipeline_topic_questions_artifact_key UNIQUE (questions_artifact_id, key)
);

CREATE TABLE public.pipeline_questions
(
    topic_question_id BIGINT       NOT NULL,
    question          VARCHAR(255) NOT NULL,
    CONSTRAINT fk_topic_question_id FOREIGN KEY (topic_question_id) REFERENCES pipeline_topic_questions (id) ON DELETE CASCADE
);

