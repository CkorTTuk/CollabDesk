CREATE TABLE workspace_member_access_roles (
    workspace_member_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (workspace_member_id, role_id),

    CONSTRAINT workspace_member_access_roles_member_fk
        FOREIGN KEY (workspace_member_id)
            REFERENCES workspace_members(id) ON DELETE CASCADE,

    CONSTRAINT workspace_member_access_roles_role_fk
        FOREIGN KEY (role_id)
            REFERENCES access_roles(id) ON DELETE CASCADE
);

CREATE TABLE project_allowed_roles (
    project_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (project_id, role_id),

    CONSTRAINT project_allowed_roles_project_fk
        FOREIGN KEY (project_id)
            REFERENCES projects(id) ON DELETE CASCADE,

    CONSTRAINT project_allowed_roles_role_fk
        FOREIGN KEY (role_id)
            REFERENCES access_roles(id) ON DELETE CASCADE
);

ALTER TABLE project_members
    ADD COLUMN grants_access BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE project_members pm
JOIN projects p ON p.id = pm.project_id
JOIN workspace_members wm ON wm.id = pm.workspace_member_id
SET pm.grants_access = FALSE
WHERE wm.user_id = p.created_by;

INSERT IGNORE INTO workspace_member_access_roles (workspace_member_id, role_id)
SELECT pm.workspace_member_id, pmr.role_id
FROM project_member_roles pmr
JOIN project_members pm ON pm.id = pmr.project_member_id;

INSERT IGNORE INTO project_allowed_roles (project_id, role_id)
SELECT pm.project_id, pmr.role_id
FROM project_member_roles pmr
JOIN project_members pm ON pm.id = pmr.project_member_id;

DELETE FROM access_role_permissions
WHERE permission IN (
    'CREATE_TASK',
    'EDIT_TASK',
    'CHANGE_TASK_STATUS',
    'CHANGE_TASK_VISIBILITY'
);

ALTER TABLE access_role_permissions
    DROP CHECK access_role_permissions_value_ck,
    ADD CONSTRAINT access_role_permissions_value_ck
        CHECK (permission IN ('EDIT_PROJECT'));

ALTER TABLE projects
    DROP CHECK projects_visibility_ck,
    DROP COLUMN visibility;
