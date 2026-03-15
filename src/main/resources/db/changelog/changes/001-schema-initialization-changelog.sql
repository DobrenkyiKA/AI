-- liquibase formatted sql
-- changeset liquibase:initial-schema-refactored

CREATE SCHEMA IF NOT EXISTS public;

DROP TABLE IF EXISTS public.pipeline_artifacts CASCADE;
DROP SEQUENCE IF EXISTS public.pipeline_artifacts_id_sequence CASCADE;
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

CREATE SEQUENCE pipeline_artifacts_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.pipeline_artifacts
(
    id          BIGINT,
    dtype       VARCHAR(50) NOT NULL,
    pipeline_id BIGINT      NOT NULL,
    status      VARCHAR(40) NOT NULL,
    CONSTRAINT pk_pipeline_artifacts_id PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_id_artifacts FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE
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
    artifact_id      BIGINT,
    CONSTRAINT pk_pipeline_steps_id PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_id_steps FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT fk_system_prompt_id FOREIGN KEY (system_prompt_id) REFERENCES prompts (id),
    CONSTRAINT fk_user_prompt_id FOREIGN KEY (user_prompt_id) REFERENCES prompts (id),
    CONSTRAINT fk_artifact_id FOREIGN KEY (artifact_id) REFERENCES pipeline_artifacts (id) ON DELETE SET NULL
);
