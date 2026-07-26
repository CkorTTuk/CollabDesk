CREATE TABLE tasks (
   id BIGINT AUTO_INCREMENT,
   project_id BIGINT NOT NULL,
   title VARCHAR(150) NOT NULL,
   description VARCHAR(1000),
   status VARCHAR(20) NOT NULL,
   created_by BIGINT NOT NULL,
   created_at DATETIME(6) NOT NULL,
   updated_at DATETIME(6) NOT NULL,
   version BIGINT NOT NULL,

   PRIMARY KEY (id),

   FOREIGN KEY (project_id)
       REFERENCES projects(id)
       ON DELETE CASCADE,

   FOREIGN KEY (created_by)
       REFERENCES users(id),

   CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),

   INDEX tasks_project_id_created_at_idx (
          project_id,
          created_at
       )
);