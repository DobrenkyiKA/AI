-- liquibase formatted sql
-- changeset liquibase:topic-tree-schema

CREATE TABLE public.topic_tree_artifacts
(
    id        BIGINT NOT NULL,
    max_depth INT    NOT NULL DEFAULT 3,
    CONSTRAINT pk_topic_tree_artifacts_id PRIMARY KEY (id),
    CONSTRAINT fk_topic_tree_artifact_id_base FOREIGN KEY (id) REFERENCES pipeline_artifacts (id) ON DELETE CASCADE
);

CREATE SEQUENCE topic_tree_nodes_id_sequence START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE TABLE public.topic_tree_nodes
(
    id                     BIGINT,
    key                    VARCHAR(255) NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    parent_topic_key       VARCHAR(255),
    coverage_area          TEXT         NOT NULL,
    depth                  INT          NOT NULL DEFAULT 0,
    leaf                   BOOLEAN      NOT NULL DEFAULT TRUE,
    topic_tree_artifact_id BIGINT       NOT NULL,
    CONSTRAINT pk_topic_tree_nodes_id PRIMARY KEY (id),
    CONSTRAINT fk_topic_tree_artifact_id FOREIGN KEY (topic_tree_artifact_id) REFERENCES topic_tree_artifacts (id) ON DELETE CASCADE,
    CONSTRAINT uq_topic_tree_nodes_artifact_key UNIQUE (topic_tree_artifact_id, key)
);
