CREATE TABLE access_roles (
    id BIGINT AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(60) NOT NULL,
    color VARCHAR(7) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT access_roles_workspace_name_uk
        UNIQUE (workspace_id, name),

    CONSTRAINT access_roles_workspace_fk
        FOREIGN KEY (workspace_id)
            REFERENCES workspaces(id)
            ON DELETE CASCADE
);

CREATE TABLE access_role_permissions (
    role_id BIGINT NOT NULL,
    permission VARCHAR(40) NOT NULL,

    PRIMARY KEY (role_id, permission),

    CONSTRAINT access_role_permissions_role_fk
        FOREIGN KEY (role_id)
            REFERENCES access_roles(id)
            ON DELETE CASCADE,

    CONSTRAINT access_role_permissions_value_ck
        CHECK (permission IN (
            'EDIT_PROJECT',
            'CREATE_TASK',
            'EDIT_TASK',
            'CHANGE_TASK_STATUS',
            'CHANGE_TASK_VISIBILITY'
        ))
);

CREATE TABLE project_member_roles (
    project_member_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (project_member_id, role_id),

    CONSTRAINT project_member_roles_member_fk
        FOREIGN KEY (project_member_id)
            REFERENCES project_members(id)
            ON DELETE CASCADE,

    CONSTRAINT project_member_roles_role_fk
        FOREIGN KEY (role_id)
            REFERENCES access_roles(id)
            ON DELETE CASCADE
);
