UPDATE feature_run run
SET base_branch = connection.default_branch
FROM repository_connection connection
WHERE run.repository = connection.repository
  AND run.base_branch <> connection.default_branch;
