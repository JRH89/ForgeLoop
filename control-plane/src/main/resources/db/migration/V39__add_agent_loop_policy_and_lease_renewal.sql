ALTER TABLE repository_connection
    ADD COLUMN agent_loop_max_tool_calls integer,
    ADD COLUMN agent_loop_max_tokens integer,
    ADD COLUMN agent_loop_max_wall_seconds integer,
    ADD COLUMN agent_loop_max_conversation_bytes integer;

ALTER TABLE repository_connection
    ADD CONSTRAINT repository_agent_loop_budget_bounds CHECK (
        (agent_loop_max_tool_calls IS NULL AND agent_loop_max_tokens IS NULL
            AND agent_loop_max_wall_seconds IS NULL AND agent_loop_max_conversation_bytes IS NULL)
        OR (agent_loop_max_tool_calls IS NOT NULL AND agent_loop_max_tokens IS NOT NULL
            AND agent_loop_max_wall_seconds IS NOT NULL AND agent_loop_max_conversation_bytes IS NOT NULL
            AND agent_loop_max_tool_calls BETWEEN 1 AND 1000
            AND agent_loop_max_tokens BETWEEN 10000 AND 100000000
            AND agent_loop_max_wall_seconds BETWEEN 60 AND 14400
            AND agent_loop_max_conversation_bytes BETWEEN 65536 AND 4194304)
    );

ALTER TABLE feature_run
    ADD COLUMN agent_loop_max_tool_calls integer,
    ADD COLUMN agent_loop_max_tokens integer,
    ADD COLUMN agent_loop_max_wall_seconds integer,
    ADD COLUMN agent_loop_max_conversation_bytes integer;

ALTER TABLE feature_run
    ADD CONSTRAINT feature_run_agent_loop_budget_bounds CHECK (
        (agent_loop_max_tool_calls IS NULL AND agent_loop_max_tokens IS NULL
            AND agent_loop_max_wall_seconds IS NULL AND agent_loop_max_conversation_bytes IS NULL)
        OR (agent_loop_max_tool_calls IS NOT NULL AND agent_loop_max_tokens IS NOT NULL
            AND agent_loop_max_wall_seconds IS NOT NULL AND agent_loop_max_conversation_bytes IS NOT NULL
            AND agent_loop_max_tool_calls BETWEEN 1 AND 1000
            AND agent_loop_max_tokens BETWEEN 10000 AND 100000000
            AND agent_loop_max_wall_seconds BETWEEN 60 AND 14400
            AND agent_loop_max_conversation_bytes BETWEEN 65536 AND 4194304)
    );

ALTER TABLE task_lease ADD COLUMN claimed_at timestamptz;
UPDATE task_lease SET claimed_at = expires_at - interval '10 minutes' WHERE claimed_at IS NULL;
ALTER TABLE task_lease ALTER COLUMN claimed_at SET NOT NULL;
