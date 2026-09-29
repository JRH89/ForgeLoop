# Runner agent loop: implementation status

The agent-loop core is being built in small, issue-linked slices. Slice 3b adds the runner-local loop, guarded tools, budgets, result shaping, and a durable step journal. **This code is not enabled in production yet.** Dispatch still follows the existing single-call path: the control-plane policy and lease lifecycle are slice 3c, and the runner wiring behind the default-off switch is slice 3d.

No live provider call is made by the tests. Running the loop against a hosted provider will use the selected provider credential and may incur provider charges.

## Loop contract

`AgentLoop` uses the provider-neutral conversation clients introduced in slice 3a. It sends one fixed, role-neutral instruction set, a task message, and only the tools granted by `WorkerRolePolicy`. Provider conversations are append-only: the adapter replays its vendor-native assistant content and receives each tool result in the vendor's required format.

The initial task message includes the title, role, owned paths, chain-changed paths, gate names, specification, and a prioritized paths-only repository manifest. It does not include source contents; the model must request relevant files through the read tools. Repository text and tool output are treated as untrusted data.

The model can finish only through `finish`. Before committing, ForgeLoop rejects an empty change, more than 20 changed paths, deletions/renames, or any changed path outside the task's owned prefixes. Commit failures are harness failures, not model-correctable tool results.

## Tool boundary

Every tool call is journaled through `ToolGateway`. The gateway checks the role grant, validates the JSON arguments, runs the interceptor seam, executes the tool, applies one retry to a transient failure, and records the full result. The loop also journals the exact shaped result returned to the model and emits metadata-only per-tool progress events. Expected validation, business-rule, permission, and transient failures are returned to the model; journal and unexpected worktree I/O failures stop the loop as harness failures.

Available tools are:

- `list_files`: sorted direct entries, up to 500; symlinks and `.git` internals are not exposed.
- `read_file`: UTF-8 text, at most 1 MiB, numbered pages of 1–400 lines.
- `search_files`: safe-bounded Java regex over up to 20,000 files, at most 100 matches; binary files are skipped and long lines are searched only at their beginning.
- `write_file`: atomic whole-file writes, at most 500,000 characters, under owned paths only.
- `edit_file`: exact text replacement in an existing owned text file; ambiguous matches require explicit `replaceAll`.
- `run_gate`: an advisory run of a task-declared gate through an injected executor. It does not submit verification evidence or change official gate state. Production Docker wiring is part of slice 3d.
- `finish`: validates the final worktree and commits the result.

There is no shell, delete, rename, dependency-install, or unrestricted process tool. Read-only roles receive only the three read tools; integration, planning, and review capabilities do not gain write tools. `run_gate` is only granted to writing roles when gates exist.

The agent-loop gateway uses the descriptor-derived enforcement chain implemented in Version 2 slices 4a–4c. The loop remains dormant until its separate, default-off runner wiring is shipped; see [Agent-loop enforcement](agent-loop-enforcement.md) for rules and current delivery boundaries.

## Budgets and stopping

Each attempt is bounded by its snapshotted policy:

| Limit | Allowed range |
|---|---:|
| Tool calls (excluding `finish`) | 1–1,000 |
| Provider tokens | 10,000–100,000,000 |
| Wall time | 60–14,400 seconds |
| Conversation items | 65,536–4,194,304 bytes |

The loop stops before another provider turn when its token, money, wall-time, or conversation estimate is exhausted. A `MAX_TOKENS` response is retried once with a larger output allowance; another truncation stops with `OUTPUT_LIMIT`. A first no-tool response gets one reminder; a second consecutive no-tool response is `DECLINED`. Refusal, provider failure, lost lease, and harness failure have distinct outcomes. An unexecuted call after a finish or budget stop is still recorded and receives a `BUSINESS_RULE` result, but it does not execute.

Tool results entering conversation are capped at 16 KiB each. Long file/search results are cut at line boundaries with a continuation hint. Gate results retain the beginning and end plus the digest; the complete result remains local in the journal. The conversation itself is never compacted.

Unknown model pricing is not guessed. Money stops are enforced only when the task has a positive spend budget and the selected provider/model has configured rates. A provider turn can exceed an estimate before usage is known; the control plane's per-turn accounting remains authoritative once slice 3d wires reporting.

## Local journal and sensitive data

The write-ahead JSONL journal is stored at:

```text
<runner-state-root>/journals/<task-id>.jsonl
```

It includes repository/task identity, provider/model and adapter/serializer version, the instructions and tool schemas, the initial task message, request hashes, full provider responses/replay data, tool arguments and results, the exact model-visible tool results, and file post-images. That material can contain proprietary source and should be treated as sensitive. On POSIX filesystems the journal directory is owner-only and files are owner-readable/writable. Windows uses the signed-in account's host ACL policy.

Each record is appended with a monotonically increasing sequence, timestamp, lease ID, and SHA-256 link to the previous complete line; writes are forced before return. A torn final fragment is ignored and removed when reopened. The chain has no external signature or remote anchor in this slice, so it is not a third-party proof against an attacker who can rewrite the whole journal. The journal is never uploaded by this slice. Automated retention cleanup and recovery/resume are not implemented yet; resume is deferred to slice 3e.

Do not share or attach the journal without reviewing it for source code, prompts, tool arguments, provider output, and secrets first.

## Verification

The local contract and security tests run with:

```powershell
mvn -B verify
```

from `runner/`. They use scripted providers and temporary Git repositories; they do not validate a live provider account, a production control plane, lease renewal, or Docker gate execution. See [Version 2 implementation progress](version-2-implementation-progress.md) for the current issue/PR boundary and evidence.
