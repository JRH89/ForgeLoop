import type { FeatureRun, PlatformConfiguration, RepositoryConnection, Runner } from '../api';

/** A heartbeat is evidence of recent contact, not proof that a provider is funded or a worker is idle. */
export function recentRunners(runners: Runner[], now = Date.now()): Runner[] {
  return runners.filter(runner => {
    const age = now - Date.parse(runner.lastHeartbeatAt);
    return runner.enabled && Number.isFinite(age) && age >= -30_000 && age < 60_000;
  });
}

export function configurationWarnings(repository: RepositoryConnection, config: PlatformConfiguration): string[] {
  const warnings: string[] = [];
  if (!repository.enabled) warnings.push('Repository connection is disabled. Ask an administrator to reconnect it.');
  if (!config.harnesses.some(harness => harness.name === repository.harnessProfile && harness.enabled))
    warnings.push(`Enable or create the ${repository.harnessProfile} harness in Harness & policy.`);
  if (repository.maxBudgetUsd <= 0 || repository.maxBudgetUsd > config.policy.maxRunBudgetUsd)
    warnings.push('Align the repository budget with the organization run limit before submitting an issue.');
  if (!repository.issueLabel.trim()) warnings.push('Configure an intake label for this repository.');
  if (!repository.requiredGates.length) warnings.push('Configure repository verification gates before submitting work.');
  if (!config.policy.allowedProviders.length) warnings.push('Allow a provider in the organization policy.');
  return warnings;
}

export function runExplanation(run: FeatureRun, runners: Runner[], now = Date.now()): string {
  if (run.state === 'COMPLETE') return 'Completed. Open the run to review verification evidence and its pull request.';
  if (run.state === 'CANCELLED') return 'Cancelled. This run will not claim more work.';
  if (run.state === 'REJECTED') return 'Rejected. Open the run to inspect validation evidence before submitting replacement work.';
  if (run.state === 'PR_OPEN') return 'Pull request open. Review its checks and merge status in Runs and GitHub.';
  if (run.state === 'READY_FOR_REVIEW' && !run.approved) return 'Waiting for human approval. Review the evidence in Runs before approving.';
  if (['BLOCKED', 'FAILED', 'HELD'].includes(run.state) || run.tasks.some(task => ['FAILED', 'HELD'].includes(task.state)))
    return 'Needs attention. Open the run to inspect failed tasks, evidence, and escalations.';
  if (run.budgetUsd > 0 && run.spentCostMicros >= run.budgetUsd * 1_000_000)
    return 'Recorded spend has reached the run budget. Review the budget and evidence before retrying.';
  if (!runners.some(runner => runner.enabled)) return 'No enabled runner is enrolled. Connect a runner, save provider settings locally, and start it when ready.';
  const missingCapabilities = [...new Set(run.tasks
    .filter(task => ['PENDING', 'REPAIR_QUEUED'].includes(task.state) && task.requiredCapability
      && !runners.some(runner => runner.enabled && runner.capabilities.includes(task.requiredCapability!)))
    .map(task => task.requiredCapability))];
  if (missingCapabilities.length) return `Queued tasks require capabilities missing from enabled runners: ${missingCapabilities.join(', ')}. Check runner configuration before starting work.`;
  if (!recentRunners(runners, now).length) return 'No recent runner heartbeat. Check the desktop connection and pause state; active work can delay heartbeats.';
  return 'Runner contact is recent. Open Runs for live task events, dependencies, leases, and verification results; contact alone does not guarantee work is executing.';
}

export function newIssueUrl(repository: string): string | undefined {
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository)) return undefined;
  const body = '## Requested change\nDescribe one small, concrete change.\n\n## Acceptance criteria\n- [ ] Describe the observable result.\n- [ ] Describe the tests that should pass.\n\n## Constraints\nDescribe what must not change.';
  // Do not pre-apply intake labels or assignees: submitting eligible issues can start paid work.
  return `https://github.com/${repository}/issues/new?${new URLSearchParams({ body })}`;
}
