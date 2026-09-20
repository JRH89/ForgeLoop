# ForgeLoop Runner

The runner executes inside customer-controlled infrastructure. It registers with the ForgeLoop control plane using a one-time token, then claims only capability-compatible work under a short-lived, nonce-bound lease.

## Current capabilities

* Validates local runner configuration.
* Registers through the control-plane GraphQL API.
* Sends an authenticated runner heartbeat through the control-plane GraphQL API.
* Acknowledges a leased task with both the local runner credential and the one-time lease nonce.
* Completes an acknowledged lease with a verified pass/fail result.
* Retrieves structured, authenticated pending-task context (repository, policy base branch, source reference, and capability) before a runner attempts a lease claim.
* Claims a task and keeps the lease nonce in a local state file rather than printing it.
* Provides a guarded Git worktree manager and an atomic claim-and-prepare flow for task-scoped repository isolation from pre-cloned local checkouts.
* Runs policy-selected verification commands directly (never through a shell) with a one-hour maximum timeout and bounded output.
* Runs disposable Docker verification containers with a read-only task mount, a read-only root filesystem, capped temporary storage, and deny-by-default networking.
* Can submit bounded, lease-bound verification evidence to the control plane; the control plane calculates its integrity digest.
* Runs as a non-root container image.

## Container build

```sh
docker build -t forgeloop-runner:local runner
```

Registration tokens and runner credentials are secrets. Provide registration tokens through a secure local secret mechanism; do not put them in source control, logs, or command history. Enrollment writes a runner credential to `FORGELOOP_RUNNER_STATE_FILE` (or `/state/runner` in the container image); mount `/state` as a durable, permission-restricted volume and do not commit its contents.

The runner does not yet clone repositories, choose policy checks, execute coding providers, upload evidence, or create pull requests. It can claim a server-authorized task, create a guarded detached worktree from a locally available checkout, and execute operator-selected verification commands.

## Provider boundary

The runner includes a tested provider-neutral contract plus OpenAI Responses and Anthropic Messages API adapters. Each reads its API key only from runner-local configuration; ForgeLoop's control plane never stores, logs, or receives that key. Both classify `429` and `5xx` responses as retryable and treat returned text as untrusted until a task-specific schema validates it. The adapters are intentionally not wired into task execution until the task-output schema, path policy, and repair flow are complete.

After building the runner image, validate an Anthropic key from the same PowerShell window that contains `ANTHROPIC_API_KEY`:

```powershell
docker build -t forgeloop-runner:local runner
docker run --rm -e ANTHROPIC_API_KEY forgeloop-runner:local provider-health anthropic <your-Claude-model-id>
```

The command performs one minimal API request, prints only request/usage metadata, and never prints the key or prompt response.

## Claim and prepare a dispatched task

Keep trusted pre-cloned repositories below a runner-owned root using their GitHub `owner/repository` path. The runner resolves only that exact path and rejects traversal, missing checkouts, and arbitrary filesystem paths.

```sh
claim-and-prepare-task http://control-plane:8090 /state/runner <task-id> /state/lease /repositories /worktrees
```

This claims only a task returned by the authenticated control plane, prepares a detached worktree from its policy-pinned base branch, persists the nonce locally, and acknowledges the lease. If preparation fails, no nonce is persisted and normal lease expiry recovery handles the unacknowledged lease.

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

To attach the result to an already acknowledged lease before explicit completion, use `verify-container-and-record`. The runner sends bounded output and a control-plane-calculated integrity digest; it does not mark the task complete by itself.

```sh
verify-container-and-record http://control-plane:8090 /state/runner /state/lease /worktrees/task-123 900 none node:22-alpine node --version
```
