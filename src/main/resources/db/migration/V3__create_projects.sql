CREATE TABLE projects (
      id BIGINT AUTO_INCREMENT,
      workspace_id BIGINT NOT NULL,
      name VARCHAR(100) NOT NULL,
      description VARCHAR(500),
      status VARCHAR(20) NOT NULL,
      created_by BIGINT NOT NULL,
      created_at DATETIME(6) NOT NULL,
      updated_at DATETIME(6) NOT NULL,
      version BIGINT NOT NULL,

      PRIMARY KEY (id),

      FOREIGN KEY (workspace_id)
          REFERENCES workspaces(id)
          ON DELETE CASCADE,

      FOREIGN KEY (created_by)
          REFERENCES users(id),

      CHECK (status IN ('ACTIVE', 'ARCHIVED')),

      INDEX projects_workspace_id_created_at_idx (
              workspace_id,
              created_at
          )
);