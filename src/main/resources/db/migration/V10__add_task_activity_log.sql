CREATE TABLE task_activities (
    id BIGINT AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    activity_type VARCHAR(30) NOT NULL,
    old_value VARCHAR(150),
    new_value VARCHAR(150),
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT task_activities_task_fk
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,

    CONSTRAINT task_activities_actor_fk
        FOREIGN KEY (actor_id) REFERENCES users(id),

    INDEX task_activities_task_created_at_idx (
        task_id,
        created_at,
        id
    )
);
-- checksum tU6bHR
