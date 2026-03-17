-- liquibase formatted sql

-- changeset kdob:6
ALTER TABLE public.topic_qa ADD COLUMN parent_chain VARCHAR(2048);
