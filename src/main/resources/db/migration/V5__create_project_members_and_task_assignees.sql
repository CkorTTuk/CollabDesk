CREATE TABLE project_members (
         id BIGINT AUTO_INCREMENT,
         project_id BIGINT NOT NULL,
         workspace_member_id BIGINT NOT NULL,
         joined_at DATETIME(6) NOT NULL,

         PRIMARY KEY (id),

         CONSTRAINT project_members_project_fk
             FOREIGN KEY (project_id)
                 REFERENCES projects(id)
                 ON DELETE CASCADE,

         CONSTRAINT project_members_workspace_member_fk
             FOREIGN KEY (workspace_member_id)
                 REFERENCES workspace_members(id)
                 ON DELETE CASCADE,

         CONSTRAINT project_members_project_workspace_member_uk
             UNIQUE (project_id, workspace_member_id),

         INDEX project_members_project_joined_at_idx (
                  project_id,
                  joined_at
             )
);
INSERT INTO project_members (
    project_id,
    workspace_member_id,
    joined_at
)
SELECT
    p.id,
    wm.id,
    p.created_at
FROM projects p
         JOIN workspace_members wm
              ON wm.workspace_id = p.workspace_id
                  AND wm.user_id = p.created_by;

INSERT INTO project_members (
    project_id,
    workspace_member_id,
    joined_at
)
SELECT
    p.id,
    wm.id,
    p.created_at
FROM projects p
         JOIN workspace_members wm
              ON wm.workspace_id = p.workspace_id
                  AND wm.role = 'OWNER'
WHERE NOT EXISTS (
    SELECT 1
    FROM project_members pm
    WHERE pm.project_id = p.id
);

CREATE TABLE task_assignees (
                                id BIGINT AUTO_INCREMENT,
                                task_id BIGINT NOT NULL,
                                project_member_id BIGINT NOT NULL,
                                assigned_at DATETIME(6) NOT NULL,

                                PRIMARY KEY (id),

                                CONSTRAINT task_assignees_task_fk
                                    FOREIGN KEY (task_id)
                                        REFERENCES tasks(id)
                                        ON DELETE CASCADE,

                                CONSTRAINT task_assignees_project_member_fk
                                    FOREIGN KEY (project_member_id)
                                        REFERENCES project_members(id)
                                        ON DELETE CASCADE,

                                CONSTRAINT task_assignees_task_project_member_uk
                                    UNIQUE (task_id, project_member_id),

                                INDEX task_assignees_task_idx (task_id),
                                INDEX task_assignees_project_member_idx (project_member_id)
);
