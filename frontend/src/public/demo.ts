/**
 * Recorded facts from the unattended project-hub validation run (issue #10 → PR #11).
 * Source of truth: docs/second-repository-validation-2026-10-09.md. Keep every value
 * here traceable to that record or to the public GitHub repository; never embellish.
 */
export const DEMO_REPOSITORY = 'https://github.com/JRH89/project-hub';
export const DEMO_RUN = {
  issue: { number: 10, url: `${DEMO_REPOSITORY}/issues/10`, title: 'Cover non-string file search queries with regression tests' },
  pullRequest: { number: 11, url: `${DEMO_REPOSITORY}/pull/11`, author: 'forgeloop-ai[bot]' },
  mergeCommit: 'fbae689ee2dab57a0118acdb89cadaa936b235af',
  mergedAt: '2026-10-09T19:09:22Z',
  budgetUsd: 5,
  knownCostUsd: 0.218838,
  gateCommand: 'npm ci && node --test tests/search.test.js && npm run build',
} as const;

export type DemoStage = { stage: string; title: string; detail: string; evidence: string };

/** Ordered stages of the replay; each one renders as a step in the timeline. */
export const demoStages: DemoStage[] = [
  { stage: 'Intake', title: 'A labeled GitHub issue opens a run',
    detail: 'Issue #10 asked for regression tests proving that null, undefined, numeric, and object queries return an empty array. It restricted changes to one test file and named the exact commands that must pass.',
    evidence: 'webhook accepted · repository policy revision captured · budget USD 5' },
  { stage: 'Plan', title: 'The planner produces a bounded task graph',
    detail: 'The request becomes validated tasks with owned paths and acceptance criteria. Only tests/search.test.js is writable; runtime code, dependencies, and workflows are out of scope.',
    evidence: 'owned path: tests/search.test.js · criteria stored server-side' },
  { stage: 'Execute', title: 'A runner claims the lease and edits an isolated worktree',
    detail: 'The enrolled runner checks out the repository on its own machine, calls the model with its locally stored provider key, and commits the change in a dedicated Git worktree.',
    evidence: 'lease acknowledged · heartbeat streaming · commit recorded' },
  { stage: 'Verify', title: 'The repository gate runs in a pinned container',
    detail: 'The required unit gate ran Node tests and the production build in a digest-pinned Node 22 image. An agent saying “done” does not count; only the gate result does.',
    evidence: 'unit gate PASSED · every acceptance criterion covered' },
  { stage: 'Review', title: 'An independent review checks the integrated diff',
    detail: 'A separate review stage receives the bounded, redacted diff against the same remote-tracking base used for execution. It assesses each stored criterion exactly once.',
    evidence: 'review approved · criteria identities match the server list' },
  { stage: 'Deliver', title: 'Policy allowed auto-merge, so the verified commit merged',
    detail: 'Human approval was disabled for this repository and auto-merge was enabled. ForgeLoop opened PR #11 as its GitHub App, merged the verified commit, and closed the source issue.',
    evidence: 'PR #11 merged · approved_by: null · issue #10 closed' },
];
