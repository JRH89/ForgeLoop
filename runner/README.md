# ForgeLoop Runner

The runner executes inside customer-controlled infrastructure. It registers with the ForgeLoop control plane using a one-time token, then will claim scoped work only after lease and capability checks are implemented.

## Current capabilities

* Validates local runner configuration.
* Registers through the control-plane GraphQL API.
* Sends an authenticated runner heartbeat through the control-plane GraphQL API.
* Acknowledges a leased task with both the local runner credential and the one-time lease nonce.
* Completes an acknowledged lease with a verified pass/fail result.
* Retrieves authenticated pending task metadata before a runner attempts a lease claim.
* Claims a task and keeps the lease nonce in a local state file rather than printing it.
* Provides a guarded Git worktree manager for task-scoped repository isolation.
* Runs policy-selected verification commands directly (never through a shell) with a one-hour maximum timeout and bounded output.
* Runs as a non-root container image.

## Container build

```sh
docker build -t forgeloop-runner:local runner
```

Registration tokens and runner credentials are secrets. Provide registration tokens through a secure local secret mechanism; do not put them in source control, logs, or command history. Enrollment writes a runner credential to `FORGELOOP_RUNNER_STATE_FILE` (or `/state/runner` in the container image); mount `/state` as a durable, permission-restricted volume and do not commit its contents.

The runner does not yet clone repositories or execute tools. It can create guarded, detached task worktrees from a locally available repository, but task discovery and execution are deliberately deferred until the scheduler and execution policies are verified.

## Worktree preparation

The runner only accepts a local Git repository and a task ID containing letters, numbers, `_`, or `-`.

```sh
prepare-worktree /repositories/ticketly main task-123 /worktrees
```

## Verification

Commands are executed directly, never through a shell. The worktree must have been prepared by Git and the timeout cannot exceed one hour.

```sh
verify /worktrees/task-123 900 npm test
```
