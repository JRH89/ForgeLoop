import { PUBLISHED } from './content';

export type Article = { slug: string; title: string; description: string; category: string; published: string; body: string };
// Publication dates describe actual publication, never a manufactured project history.
export const articles: Article[] = [
  {slug:'github-issues-to-pull-requests',title:'From GitHub issues to AI pull requests',description:'Build an issue-to-PR workflow with explicit intake rules, bounded tasks, verification evidence, and a deliberate approval point.',category:'GitHub workflows',published:PUBLISHED,body:`
An issue-to-pull-request system needs more than an agent that can edit files. It needs a reliable answer to three questions: which issue is authorized, what counts as done, and who can approve delivery? Start there before choosing a model.

## Make the issue executable, not vague

Write the expected behavior, boundaries, and acceptance criteria into the issue. “Fix assignment” is ambiguous. “If ticket assignment fails, keep the ticket unchanged, show an accessible error, and allow a retry” creates observable outcomes. Include the relevant command for running tests and identify changes that are out of scope.

A useful issue also describes the unhappy path. What happens when a request times out? What should a user see when they lack permission? Existing behavior is context, not permission to rewrite unrelated parts of the application. Small, complete changes are easier to evaluate than a broad prompt to improve everything.

## Put an authorization step before execution

Not every new issue should trigger paid work. A label or assignment rule gives a maintainer an explicit intake decision. Keep repository installation permission separate from issue eligibility. An installed GitHub App can be authorized for a repository while an individual issue remains intentionally unassigned.

## Treat a pull request as a candidate

An agent's summary is useful, but it is not a test result. A candidate PR should point to the exact tested commit, verification output, acceptance coverage, and any known limitations. If the branch changes after verification, evaluate the new state instead of inheriting confidence from an earlier diff.

## A practical first rollout

1. Choose one reversible change in a repository with working tests.
2. Enable explicit intake and leave automatic merge off.
3. Set a cost budget and a retry limit before execution.
4. Inspect the diff and evidence before approving delivery.
5. Record what needed human judgment, then improve the issue template.

ForgeLoop models these as distinct delivery stages. Its value is not hiding the stages; it is making the transition between them inspectable. See [how the delivery loop works](/how-it-works) before enabling unattended work.
`},
  {slug:'coding-agents-vs-delivery-harnesses',title:'Coding agents vs. delivery harnesses',description:'Understand the difference between an AI coding agent and the orchestration, policy, evidence, and recovery system around it.',category:'Architecture',published:PUBLISHED,body:`
A coding agent and a delivery harness solve related but different problems. The agent reasons about a change and uses tools to make it. The harness decides when that work may run, what context and authority it receives, and how the result is evaluated.

## Separate the worker from the rules

An agent can propose a patch, run a command, and explain a result. A delivery system must also handle duplicate events, unavailable machines, competing tasks, exhausted budgets, and stale verification. Asking the agent to remember every rule does not give those rules a durable enforcement point.

Consider a worker that disappears after creating a commit. Another worker needs to know whether the task is still owned, whether execution can resume, and what evidence already exists. That is a state-management problem, not simply a prompt-writing problem. Durable task records and leases make the decision explicit.

## Why the distinction matters for teams

When policy lives outside the model conversation, it can be reviewed independently. The same repository rules can apply across different providers. A change in model does not need to redefine who may merge a PR or which tests are mandatory. Likewise, provider success should not be able to overwrite a failed verification result.

The tradeoff is complexity. A harness has configuration, credentials, storage, and operational responsibilities. For a one-off local edit, that overhead may be unnecessary. It becomes more valuable when multiple repositories, repeatable verification, or auditability matter.

## What to ask when evaluating a harness

- Can you identify the exact commit that passed verification?
- Is there an explicit boundary between drafting and approving?
- Can work recover from a lost runner without losing ownership history?
- Are budgets and retries enforced outside the model response?
- Can the system admit that a result is blocked or unverified?

ForgeLoop is intended to be that surrounding system, not a replacement for every editor assistant. Its [feature overview](/features) describes the implementation boundaries, while the repository checklist records remaining release work. Evaluate both; a polished dashboard alone is not delivery evidence.
`},
  {slug:'acceptance-criteria-for-coding-agents',title:'Acceptance criteria coding agents can verify',description:'Write observable acceptance criteria for AI coding tasks, including failure paths, permissions, test evidence, and explicit scope boundaries.',category:'Verification',published:PUBLISHED,body:`
Acceptance criteria are a contract between an issue author, an implementation, and a reviewer. For coding agents, they are also a way to stop a persuasive explanation from being mistaken for completion.

## Describe behavior a test can observe

“Make errors better” leaves room for almost any change. “When saving fails, preserve the entered values, display an error associated with the form, and allow another attempt” can be checked. Good criteria specify the starting state, the action, and the expected result without unnecessarily prescribing the internal design.

Avoid treating one implementation detail as the entire requirement. A toast can exist while the form still loses data. A retry button can exist while the second request is never sent. Verify the behavior that matters to the person using the application.

## Include boundaries and negative cases

Permission behavior is often more important than the happy path. A user from another organization should not be able to read or modify the record. A repeated submission should not create duplicate work. An unavailable dependency should produce a recoverable result rather than a false success message.

Write these cases into the issue before implementation. Tests created afterward can accidentally encode the implementation's assumptions instead of challenging them. Independent verification does not require an entirely separate testing framework; it requires deriving checks from the requirement rather than accepting the author's claim.

## Map each criterion to evidence

Use a short identifier for each criterion and name the evidence expected for it. A unit test may prove a calculation, an integration test may prove persistence, and a browser test may prove focus and recovery behavior. A screenshot demonstrates a visible state, not necessarily a successful database update.

For example, assignment recovery might require a failing first response, unchanged UI data, a visible retry action, and a successful second response. Preserve the test output and the commit under test so reviewers can reproduce the result.

## Keep “not verified” available

Some criteria need credentials, real infrastructure, or a funded model account. Record that boundary instead of replacing it with a mock and calling the original requirement complete. Offline tests are valuable precisely when their scope is clear. Read [testing without API spending](/blog/test-ai-workflows-without-api-spend) for a layered approach.
`},
  {slug:'self-hosted-ai-runners',title:'Self-hosted AI runners: what stays local?',description:'Understand what a self-hosted coding runner controls, what data can reach a model provider, and why local execution still needs security boundaries.',category:'Runner operations',published:PUBLISHED,body:`
“Self-hosted” answers where a process runs. It does not automatically answer where every piece of data goes. A coding runner can execute on your machine while still sending prompts and selected repository context to an external model provider.

## Separate three locations

The control plane coordinates work and records delivery state. The runner checks out code, starts tools, and performs configured operations. The model provider handles inference for requests made by that runner. Review all three when evaluating privacy requirements.

Keeping an API key on the runner avoids sending that key through the web application. It does not mean the provider sees no source context. Similarly, a control plane that is not a repository checkout may still receive issue descriptions, artifact contents, or log excerpts. These distinctions belong in deployment documentation, not just a marketing label.

## Treat the runner as an execution host

Repository scripts and tools can exercise the permissions available to the runner process. Separate Git worktrees reduce checkout collisions, but they are not a security sandbox. Use a dedicated machine or appropriately constrained environment when the repository or its commands are not fully trusted.

Docker access also deserves a separate review. The Docker security documentation explains the importance of daemon access and host-level isolation choices. A container-related checkbox should not be interpreted as a guarantee that arbitrary code cannot affect the host. See [Docker Engine security](https://docs.docker.com/engine/security/).

## Start with explicit operation

Pair the runner, configure the provider, verify prerequisites, and leave automatic startup off during initial testing. A storage check can confirm a key is readable without calling a model. A heartbeat can confirm enrollment without claiming a task. Actual work start is a different action and can incur charges.

## A deployment checklist

- Decide which repositories and commands the host may execute.
- Understand what context each provider request can include.
- Keep credentials out of repository files and diagnostic exports.
- Review evidence retention and organization access.
- Test pause, restart, and failure recovery before unattended use.

ForgeLoop's [security overview](/security) explains these boundaries for its control plane and runner model. Local execution is a useful control, but responsible operation still depends on the environment you give it.
`},
  {slug:'safe-auto-merge-ai-pull-requests',title:'Auto-merge for AI PRs: checks before trust',description:'Design guarded auto-merge around required checks, exact commits, repository policy, and human escalation rather than model confidence.',category:'GitHub workflows',published:PUBLISHED,body:`
Automatic merge is a policy decision, not a measure of how confident an agent sounds. A useful starting question is: what must be true about this exact commit before it may become part of the protected branch?

## Define the gate outside the agent

Required checks, review requirements, and branch rules should remain enforceable even when an agent requests delivery. GitHub protected branches can require status checks and restrict changes to important branches. Repository administrators should review bypass permissions as part of that configuration. See [GitHub's protected branch documentation](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).

The orchestration layer can add delivery-specific conditions, such as acceptance coverage or independent review. Those conditions complement repository controls; they do not justify disabling them. Keep the authority to change policy separate from the authority to propose code.

## Tie evidence to the commit

A passing check on an earlier revision does not verify a later revision. Capture the expected head SHA when evaluating delivery and compare it again at the merge boundary. If the branch has moved, stop and re-evaluate. This prevents a valid review decision from silently authorizing different code.

Also distinguish check completion from check success. Cancelled, skipped, missing, or timed-out jobs should not automatically become approval. Your policy must say which outcomes are acceptable for each required gate.

## Roll out in stages

Begin with human approval and inspect several complete runs. Identify which changes are predictable, reversible, and covered by meaningful tests. Automatic merge may be reasonable for a narrow class of changes while remaining disabled for schema changes, permissions, infrastructure, or other high-impact work.

Make exceptions visible. If someone overrides a failed gate, record who made the decision and why. If the system cannot establish the expected commit or required evidence, an escalation is more useful than an optimistic merge attempt.

## What auto-merge does not promise

Passing tests cannot prove every production property. They establish the behaviors those tests actually exercise. ForgeLoop offers opt-in guarded auto-merge, not a claim that all generated code should merge unattended. Its [delivery overview](/how-it-works) keeps review and delivery distinct so teams can choose the appropriate boundary.
`},
  {slug:'ai-coding-agent-cost-budgets',title:'Budgeting AI coding agents without guesswork',description:'Track model usage, distinguish unknown costs from zero, and bound retries and task scope before starting autonomous coding work.',category:'Cost control',published:PUBLISHED,body:`
The cost of an agent run is not simply the price of its final response. Planning, implementation, retries, review, and repair can each produce provider requests. A useful budget begins with that sequence rather than an optimistic estimate for one call.

## Record usage at the request boundary

Keep provider-reported usage with the relevant attempt and task. Associate an estimate with the model and pricing policy used for that request. If a provider reports different usage categories, such as cached input, do not silently price every category as ordinary input unless the calculator explicitly supports that assumption.

An estimate is not an invoice. Account-specific agreements, taxes, provider billing rules, and unsupported usage categories can make the billed total differ. Label the number accordingly and retain the source usage information needed to investigate discrepancies.

## Unknown is not zero

A missing price should produce an unavailable estimate, not a zero-dollar success. Likewise, a failed request without usage information is not evidence that the provider charged nothing. Keep “known estimated cost” and “pricing coverage” separate so a partially priced run does not appear fully accounted for.

This matters in dashboards: a short N/A can be clearer than a long warning inside a crowded table, provided the detail view explains what is missing. Hiding uncertainty behind a reassuring total makes the interface less useful.

## Bound work before it starts

Set limits on task scope, attempts, repair cycles, and run budget. An inexpensive model can still generate a costly loop if the system repeatedly asks it to fix the same underlying environment problem. Retry network failures selectively; do not keep retrying invalid credentials or missing billing access.

Before enabling automatic startup, consider what happens after a reboot. A runner that immediately claims eligible issues can spend money even when nobody opens the dashboard. Explicit consent and visible running status are part of cost control.

## Test the accountant without paying the provider

Feed deterministic usage fixtures into the calculator. Include missing prices, zero usage, large counts, failed attempts, and multiple requests. Assert units carefully: dollars, million-token prices, and stored microdollars are different scales. Then validate provider billing separately when a funded account is available. Read the [free-testing guide](/blog/test-ai-workflows-without-api-spend) for the distinction.
`},
  {slug:'bounded-repair-loops',title:'Designing repair loops that know when to stop',description:'Make AI repair loops bounded and useful with structured failure context, retry classifications, durable attempts, and human escalation.',category:'Reliability',published:PUBLISHED,body:`
A repair loop should make new progress, not merely repeat a request. If the third attempt has the same input, environment, and failure as the first, the system may be consuming budget without learning anything useful.

## Classify the failure first

Compilation errors, failed assertions, missing tools, provider outages, and denied permissions require different responses. A model can often fix a code defect. It cannot make an unavailable Docker daemon appear or grant itself a missing organization role. Retrying every failure through the same prompt obscures the real problem.

Separate transient execution failures from permanent configuration failures and failed verification. Preserve an attempt history so a later operator can see whether the system tried a new hypothesis or repeated the same action.

## Give repair work a bounded package

A useful repair package contains the failing gate, relevant output, expected behavior, and commit context. It should not indiscriminately include every log and secret-bearing environment variable. Give the repair task a clear scope and retain the original acceptance criteria so a “fix” cannot redefine success.

For example, if a retry test fails because the button remains disabled, the repair should address that behavior and rerun the relevant checks. Deleting the test or weakening its assertion may make a gate green while violating the requirement. Independent review should consider the test changes as part of the patch.

## Limits are part of the design

Bound the number of provider attempts and repair cycles. Apply time and cost limits as well as a count. When those limits are reached, keep the failed state and evidence visible, then escalate. A clear blocked result is preferable to an endless “working” indicator.

## Recovery is not the same as repair

A runner disappearing creates an ownership problem: another process may need to resume or reclaim work. A failed acceptance test creates a correctness problem: the implementation needs a change. Mixing the two makes duplicate execution and misleading histories more likely. Use task leases and durable state for ownership recovery, and bounded repair work for code correction.

ForgeLoop exposes both concepts in its delivery model. The goal is not to avoid every failure; it is to leave the system and its operator with an honest, actionable account of what happened. Explore [verification features](/features) for the surrounding workflow.
`},
  {slug:'git-worktrees-parallel-agents',title:'Git worktrees for parallel coding agents',description:'Use separate worktrees and explicit path ownership to reduce parallel agent conflicts, while keeping integration and host security separate.',category:'Architecture',published:PUBLISHED,body:`
Parallel agents should not edit the same checkout at the same time. A worktree gives each task a separate working directory while sharing repository history. That is a useful primitive, but it does not eliminate integration conflicts or turn a host into a sandbox.

## Isolate the working directory

Give each active task a known base commit and its own worktree. Record the task identity, checkout location, and resulting change commit. Avoid depending on whichever branch happens to be open in a developer's main checkout. The runner should be able to explain where a change came from after the process exits.

Local isolation also makes cleanup more precise. A failed task should not require deleting an entire repository or resetting the developer's working directory. Cleanup decisions should target only task-owned paths and preserve evidence needed for diagnosis.

## Own paths explicitly

Two tasks can have separate directories and still modify the same shared configuration file. Planning should account for owned paths and dependencies. If both tasks need to change an API contract, a sequential dependency may be safer than treating them as independent work.

Integration is its own verification boundary. Two changes that pass tests separately may fail when combined. Run appropriate gates against the integrated result, and keep its commit identity distinct from the individual task commits.

## Do not confuse worktrees with containment

A process in a worktree still has the operating-system permissions of the runner. It may access other directories, networks, or a Docker daemon if those capabilities are available. Use host and container controls for execution security; use worktrees for checkout isolation.

Repository traversal also needs care. Context collection should prune Git metadata before descending into it. Filtering metadata paths only after traversing them can read irrelevant data or race temporary lock files. A context builder should select useful source deliberately and apply size limits.

## A good parallelism test

Create two tasks with non-overlapping paths, integrate both, and run the combined gates. Then test overlapping paths and confirm the scheduler does not assume they are independent. Include cancellation and restart cases. Parallelism is valuable only when the coordination system can still explain ownership and reproduce the result. See [the harness architecture discussion](/blog/coding-agents-vs-delivery-harnesses).
`},
  {slug:'test-ai-workflows-without-api-spend',title:'Test AI workflows without spending API credits',description:'Separate free deterministic tests from paid model validation using fake providers, local servers, persistence checks, and explicit evidence boundaries.',category:'Testing',published:PUBLISHED,body:`
An empty provider balance does not need to stop product engineering. Much of a delivery system is ordinary software: state transitions, authentication boundaries, persistence, error handling, user interfaces, and accounting. Test those parts without calling a live model.

## Use deterministic provider fixtures

A fake provider can return valid output, malformed output, a transient error, or a permanent failure on demand. Assert the exact attempt count and final state. Keep the fixture explicit so nobody mistakes the result for a demonstration of real model quality.

Local HTTP servers add another layer: they can exercise request serialization, response parsing, timeouts, and heartbeat behavior. A desktop start/pause test can use a fake control plane that reports no eligible tasks. That proves process lifecycle behavior without pretending to complete a real repository change.

## Exercise persistence with real local components

Use a disposable database to test enrollment exchange, one-time proofs, replay rejection, and migrations. Use a temporary OS key-store entry with a fake credential to test storage and rotation. Never point destructive test cleanup at a user's active state directory.

Installer tests should also preserve fake connection settings through upgrades and uninstall/reinstall. A successful package build is not proof that an installed launcher can find its runtime or native credential library. Test the installed artifact, not only the source checkout.

## Test the interface people actually use

Reopen the app after saving settings. Is a blank password field explained? Can the user tell whether a connection is restored? What happens when login returns to the wrong page? These are ordinary usability defects that do not require paid inference to find.

For ForgeLoop's desktop preview, prerequisite checks, a saved-connection heartbeat, and local key-store verification do not invoke a model. Start runner is different: it can claim eligible work and is not a free test mode.

## Keep the final boundary honest

Offline tests cannot establish that a model follows the instructions, that a provider accepts the real key, or that the account has credit. Record those as separate live validation steps. A useful release checklist says both what passed and what remains unverified. That distinction lets engineering continue without turning a simulated result into a production claim.
`},
  {slug:'github-webhooks-reliable-intake',title:'Reliable GitHub webhook intake for AI agents',description:'Design webhook intake with signature verification, idempotency, authorization, and clear issue eligibility before dispatching agent work.',category:'Integrations',published:PUBLISHED,body:`
A webhook is an event notification, not permission to execute arbitrary repository work. Reliable intake needs to verify the sender, identify duplicate deliveries, resolve repository authorization, and apply the current eligibility rules before dispatching a task.

## Validate before interpreting

GitHub documents signature validation using a webhook secret and the X-Hub-Signature-256 header. Validation must use the received payload and an appropriate constant-time comparison. Keep the secret outside source control and make signature failure an explicit rejection rather than a warning followed by processing. See [GitHub webhook validation](https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries).

Signature validity establishes a delivery boundary; it does not decide whether the issue should run. Resolve the installation and repository against your application's authorization records before treating the event as eligible work.

## Make duplicate delivery harmless

Persist a delivery identity and use it to avoid processing the same notification repeatedly. Then distinguish event deduplication from business identity. Multiple different events can refer to one issue; separate issues can share the same branch. A uniqueness rule based only on repository and branch can incorrectly collapse unrelated work.

Choose keys that match the actual operation. If one delivery creates a run, define what happens when the same issue is edited, reassigned, or deliberately retried. A database constraint should reinforce that policy rather than accidentally invent it.

## Apply policy at the right boundary

An installed app may receive events that do not satisfy your label or assignment rules. That is normal. Keep the reason for ignoring or holding work observable so operators can distinguish a healthy intake filter from a broken webhook endpoint.

A public homepage loading successfully does not prove webhook or GraphQL routing is healthy. Test the specific request path through the tunnel, proxy, application, and persistence layer. After a restart, check service readiness and the expected database rather than recreating volumes to make an error disappear.

## Start with a reversible intake test

Create a clearly scoped issue and verify that it appears once in the queue under the expected repository. Confirm ineligible issues do not start work. Only then proceed to funded execution. ForgeLoop's [setup overview](/docs) explains the separate GitHub, repository, and runner setup steps.
`}
];
