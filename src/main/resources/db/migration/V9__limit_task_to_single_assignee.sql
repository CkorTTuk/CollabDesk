DELETE duplicate_assignment
FROM task_assignees duplicate_assignment
JOIN task_assignees kept_assignment
  ON kept_assignment.task_id = duplicate_assignment.task_id
 AND (
      kept_assignment.assigned_at < duplicate_assignment.assigned_at
      OR (
          kept_assignment.assigned_at = duplicate_assignment.assigned_at
          AND kept_assignment.id < duplicate_assignment.id
      )
 );

ALTER TABLE task_assignees
    DROP INDEX task_assignees_task_project_member_uk,
    ADD CONSTRAINT task_assignees_task_uk UNIQUE (task_id);
