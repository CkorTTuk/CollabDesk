ALTER TABLE projects
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'WORKSPACE',
    ADD CONSTRAINT projects_visibility_ck
        CHECK (visibility IN ('WORKSPACE', 'RESTRICTED'));

ALTER TABLE tasks
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PROJECT',
    ADD CONSTRAINT tasks_visibility_ck
        CHECK (visibility IN ('PROJECT', 'ASSIGNEES'));
