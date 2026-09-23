# Runner events and human escalation

ForgeLoop runners emit only bounded lifecycle metadata through `POST /api/runner/events`. Every event is authenticated by the runner credential and active acknowledged lease, has a per-lease monotonic sequence number, and is idempotent on `(lease_id, sequence_number)`. The control plane rejects unknown event types, future timestamps, oversized messages, and common credential patterns. Source files, prompts, provider output, commands, and local paths do not belong in this stream.

The operator console refreshes the append-only event timeline every five seconds alongside run state. This is near-real-time durable streaming: reconnecting the browser or control plane does not lose already accepted progress. Verification output remains in the separately checksummed evidence system.

## Escalation policy

Automation creates a durable escalation and leaves the run blocked when:

- a task or quality repair exhausts its autonomous attempt budget; or
- known provider spend reaches a task or run budget.

Duplicate reports for the same run, task, and reason resolve to one escalation. Operators may acknowledge or resolve an escalation from the console; both actions are tenant-authorized and appended to the immutable audit ledger. Acknowledgement records ownership of the incident but does not bypass a gate, add budget, retry work, approve a run, or deliver a pull request. Those remain separate explicit actions.

Operationally, investigate the redacted event timeline, provider attempt category, repair package, and verification evidence before retrying. Resolve the escalation only when the underlying condition has been handled or the run is intentionally cancelled.
