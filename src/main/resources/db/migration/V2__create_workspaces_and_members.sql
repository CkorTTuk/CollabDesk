CREATE TABLE workspaces (
    id BIGINT AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) not null,
    version BIGINT NOT NULL,
    PRIMARY KEY ( id ),
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE workspace_members(
    id BIGINT AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL ,
    user_id BIGINT NOT NULL ,
    role VARCHAR(20) NOT NULL ,
    joined_at DATETIME(6)  NOT NULL ,
    PRIMARY KEY (id),
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE ( workspace_id , user_id),
    CHECK (role IN  ('OWNER' , 'ADMIN' , 'MEMBER' , 'VIEWER')),
    INDEX workspace_members_user_id_idx (user_id)
);