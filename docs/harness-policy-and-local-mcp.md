# Harness, organization policy, and local MCP operations

ForgeLoop stores reusable execution configuration in the control plane while retaining the repository and process trust boundary on the self-hosted runner.

## Organization policy

An administrator configures the maximum run budget, maximum parallel task count, permitted model providers, and whether human approval is required. Submission rejects a run above the organization budget before it creates work. Repository budgets remain an additional, narrower boundary.

The `organizationPolicy` query and `configureOrganizationPolicy` mutation are tenant-scoped and administrator-controlled. Every change increments the policy revision and creates a digest-only audit entry.

## Harness definitions

Harness definitions are reusable, organization-owned profiles with a stable uppercase name, description, allowed task roles, default repair-attempt budget, enabled state, and revision. A repository connection names its harness profile; run intake rejects missing or disabled definitions. Existing repository profiles are seeded during migration so upgrading does not invalidate connected repositories.

Use `harnessDefinitions` to list profiles and `createHarnessDefinition` to add one. Definitions are append-only in the current API: modifying or disabling a production definition requires a future versioned mutation rather than silently changing an active profile.

## Runner-local MCP routing

`createLocalMcpConfiguration` stores a process command, argv, allowed task roles, context tool name, JSON tool arguments, and revision. The control plane sends only enabled configurations matching a task's effective role.

The runner applies a second, local authorization boundary. Set a comma-separated exact command allowlist in the runner environment:

```powershell
$env:FORGELOOP_MCP_ALLOWED_COMMANDS = 'node,C:\Program Files\Python312\python.exe'
```

For each routed configuration, the runner:

1. verifies the exact executable against the local allowlist;
2. launches it with the task worktree as its working directory;
3. performs MCP `initialize` and `notifications/initialized` over JSON-lines stdio;
4. calls the configured context tool with validated JSON arguments;
5. accepts text content only, with a 15-second response timeout and 32 KiB bound;
6. terminates the child process after the call; and
7. supplies the result only to the assigned planner, implementation, or review provider context.

`${WORKTREE}` may be used in process arguments. It is expanded by the runner, never by the control plane. A missing allowlist entry, protocol failure, tool error, timeout, or oversized response fails the lease; ForgeLoop does not silently continue with incomplete context.

Do not place credentials in command arguments or tool arguments. Configure secrets directly in the runner host's process environment or a runner-local secret manager. The control plane never executes the configured command.
