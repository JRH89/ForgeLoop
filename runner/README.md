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
* Runs disposable Docker verification containers with a read-only task mount, a read-only root filesystem, capped temporary storage, and deny-by-default networking.
* Runs as a non-root container image.

## Container build

```sh
docker build -t forgeloop-runner:local runner
```

Registration tokens and runner credentials are secrets. Provide registration tokens through a secure local secret mechanism; do not put them in source control, logs, or command history. Enrollment writes a runner credential to `FORGELOOP_RUNNER_STATE_FILE` (or `/state/runner` in the container image); mount `/state` as a durable, permission-restricted volume and do not commit its contents.

The runner does not yet clone repositories, choose policy checks, execute coding providers, upload evidence, or create pull requests. It can create guarded, detached task worktrees from a locally available repository, retrieve and claim dispatchable tasks, and execute operator-selected verification commands.

## Worktree preparation

The runner only accepts a local Git repository and a task ID containing letters, numbers, `_`, or `-`.

```sh
prepare-worktree /repositories/ticketly main task-123 /worktrees
```

After evidence is uploaded, remove only the matching task workspace:

```sh
remove-worktree /repositories/ticketly task-123 /worktrees
```

## Verification

Commands are executed directly, never through a shell. The worktree must have been prepared by Git and the timeout cannot exceed one hour.

```sh
verify /worktrees/task-123 900 npm test
```

### Container verification

Container verification accepts a Docker image plus direct command arguments; it never invokes a shell. By default, the child container has no network access and can only read the task worktree.

```sh
verify-container /worktrees/task-123 900 none node:22-alpine node --version
```

The runner needs access to a Docker daemon for this command. Giving a runner a Docker socket is equivalent to giving it privileged access to that daemon, so enable it only for a dedicated runner host and apply the socket's own access controls. When the runner itself is a container, mount the daemon socket explicitly and configure both workspace roots so that the daemon can resolve the task path:

```sh
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /srv/forgeloop/worktrees:/worktrees \
  -e FORGELOOP_RUNNER_WORKSPACE_ROOT=/worktrees \
  -e FORGELOOP_DOCKER_HOST_WORKSPACE_ROOT=/srv/forgeloop/worktrees \
  forgeloop-runner:local verify-container /worktrees/task-123 900 none node:22-alpine node --version
```

`FORGELOOP_DOCKER_HOST_WORKSPACE_ROOT` must name the same location as seen by the Docker daemon, not the runner container. The runner rejects partial mapping configuration and worktrees outside the configured runner workspace root.
