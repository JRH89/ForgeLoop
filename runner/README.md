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
* Runs disposable Docker verification containers from a read-only task mount copied into capped ephemeral storage, with a read-only root filesystem and deny-by-default networking.
* Executes server-snapshotted verification policy (digest-pinned image, argv, network decision, timeout, and gate) without consulting provider policy.
* Redacts common credential forms and submits bounded, lease-bound evidence whose output and bundle checksums are independently recomputed by the control plane.
* Executes a strict non-writing planner contract and submits the validated task graph through its active lease.
* Enforces control-plane-owned path prefixes for writing tasks and dispatches repair attempts with only bounded failure context.
* Integrates only the commit SHAs declared by an eligible integration task; conflicts fail the lease and enter the bounded repair policy.
* Pushes an integrated commit directly with a lease-bound, short-lived GitHub installation token and exact `--force-with-lease` protection; source content does not transit the control plane.
* Runs a read-only independent reviewer against the bounded integrated diff and records criterion-level, checksummed review evidence before verification begins.
* Polls continuously with bounded parallelism, heartbeats, task-scoped lease files, and worktree cleanup.
* Runs as a non-root container image.

## Container build

```sh
docker build -t forgeloop-runner:local runner
```

Registration tokens and runner credentials are secrets. Provide registration tokens through a secure local secret mechanism; do not put them in source control, logs, or command history. Enrollment writes a runner credential to `FORGELOOP_RUNNER_STATE_FILE` (or `/state/runner` in the container image); mount `/state` as a durable, permission-restricted volume and do not commit its contents.

The runner does not yet clone repositories or upload evidence to an object store. It can claim server-authorized work, create guarded detached worktrees from locally available checkouts, execute policy-selected planner/coding/review providers, integrate declared task commits, push the integrated branch with a scoped installation token, persist redacted evidence, and execute repository-policy-selected verification tasks. After human approval, the control plane creates the check run and draft pull request for that already-pushed branch.

Planner and writing prompts include a bounded runner-local repository manifest and text-only source context. Authorized paths are prioritized, Git metadata and binary contents are excluded, individual files and total context are capped, and the server independently enforces the fixed capability for every role. The model cannot invent a new runner capability or expand its write scope.

## Provider boundary

The runner includes tested Anthropic Messages, OpenAI Responses, Gemini generateContent, and local OpenAI-compatible adapters behind one contract. Each reads its API key only from runner-local configuration; ForgeLoop's control plane never stores, logs, or receives that key. All adapters classify `429` and `5xx` responses as retryable and treat returned text as untrusted until a strict task schema validates it.

`execute-policy-task` is the production provider-worker entry point. It resolves provider, model, and a one-to-three-attempt retry ceiling from a reviewed runner-local JSON policy, then claims and acknowledges the task lease, creates an isolated worktree, invokes the provider, validates every output field and path before writing, commits atomically written files, submits redacted usage/failure evidence, and completes the lease. Unsupported roles are rejected before a lease is claimed.

All delivery roles have explicit least-privilege manifests. Implementation, backend, frontend, independent-test, and repair roles may use the schema-guarded patch worker. Planner, integration, verification, and review have dedicated paths; review remains read-only. A successful coding worker transitions only to `CHANGE_READY`, never `VERIFIED`. Integration consumes only dependency SHAs from the validated graph and advances only those declared dependencies.

```text
execute-policy-task <control-plane-url> <identity-file> <task-id> <repositories-root> <workspace-root> <provider-policy-file> <allowed-prefixes> <lease-file>
```

For graph-planned writing tasks, the control-plane-owned `ownedPaths` list overrides the legacy `allowed-prefixes` CLI value. Planner and integration tasks use the same command but are routed to their dedicated execution paths.

For a long-running worker, use `serve`; for bounded CI or proving-ground work, use `work-until-idle`. Both accept the control-plane URL, persisted identity, pre-cloned repository root, worktree root, reviewed provider-policy file, fallback allowed path, lease-state root, and parallelism:

```text
serve <control-plane-url> <identity-file> <repositories-root> <workspace-root> <provider-policy-file> <allowed-prefixes> <state-root> <parallelism>
work-until-idle <control-plane-url> <identity-file> <repositories-root> <workspace-root> <provider-policy-file> <allowed-prefixes> <state-root> <parallelism>
```

Copy `provider-policy.example.json` outside the repository and choose only models enabled for that runner. Provider credentials use `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, or `GEMINI_API_KEY`. A local adapter uses `FORGELOOP_LOCAL_PROVIDER_URL` and optional `FORGELOOP_LOCAL_PROVIDER_API_KEY`; non-loopback endpoints require `FORGELOOP_LOCAL_PROVIDER_ALLOW_REMOTE=true`.

Inject only the selected provider credential into the runner. Do not pass the
control plane's `.env` wholesale: GitHub App private keys, webhook secrets,
OIDC configuration, and storage credentials are outside the runner trust
boundary.

Cost estimates are enabled per provider/model with environment variables derived from upper-cased names, for example `FORGELOOP_ANTHROPIC_CLAUDE_SONNET_5_INPUT_MICROS_PER_MILLION` and the matching `_OUTPUT_MICROS_PER_MILLION`. Values are micro-dollars per million tokens. When either rate is absent, usage is persisted with `costKnown=false` instead of a fabricated estimate.

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

Host commands are executed directly, never through a shell. The worktree must have been prepared by Git and the timeout cannot exceed one hour.

```sh
verify /worktrees/task-123 900 npm test
```

### Container verification

Container verification accepts a Docker image plus command arguments. A fixed bootstrap shell copies the read-only source mount into an ephemeral workspace and forwards policy argv as positional arguments without interpolation; a reviewed policy may itself explicitly select a shell command when chaining package-manager steps. By default, the child container has no network access.

```sh
verify-container /worktrees/task-123 900 none node:22-alpine node --version
```

The runner needs access to a Docker daemon for this command. Giving a runner a Docker socket is equivalent to giving it privileged access to that daemon, so enable it only for a dedicated runner host and apply the socket's own access controls. When the runner itself is a container, mount the daemon socket explicitly and configure both workspace roots so that the daemon can resolve the task path:

```sh
docker run --rm \
  --group-add "$(stat -c '%g' /var/run/docker.sock)" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /srv/forgeloop/worktrees:/worktrees \
  -e FORGELOOP_RUNNER_WORKSPACE_ROOT=/worktrees \
  -e FORGELOOP_DOCKER_HOST_WORKSPACE_ROOT=/srv/forgeloop/worktrees \
  forgeloop-runner:local verify-container /worktrees/task-123 900 none node:22-alpine node --version
```

The supplemental group is required when the non-root runner user is not already
a member of the socket's host group. Never switch the runner image to root just
to bypass this permission boundary.

`FORGELOOP_DOCKER_HOST_WORKSPACE_ROOT` must name the same location as seen by the Docker daemon, not the runner container. The runner rejects partial mapping configuration and worktrees outside the configured runner workspace root.

To attach the result to an already acknowledged lease before explicit completion, use `verify-container-and-record`. The runner sends bounded output and a control-plane-calculated integrity digest; it does not mark the task complete by itself.

```sh
verify-container-and-record http://control-plane:8090 /state/runner /state/lease /worktrees/task-123 900 none node:22-alpine node --version
```
