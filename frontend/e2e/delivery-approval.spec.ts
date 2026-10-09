import { expect, test } from '@playwright/test';

for (const requiresHumanApproval of [true, false]) {
  test(`verified run approval controls honor captured policy: ${requiresHumanApproval}`, async ({ page }) => {
    const run = { id: 'approval-run', title: 'Approval policy test', repository: 'acme/app', sourceRef: 'issue-1',
      specification: 'Preserve behavior', state: 'READY_FOR_REVIEW', archived: false, createdAt: new Date().toISOString(),
      budgetUsd: 5, spentCostMicros: 0, approved: false, requiresHumanApproval, policyRevision: 1,
      harnessProfile: 'GENERIC', baseBranch: 'main', tasks: [], gates: [], criteria: [] };
    await page.route('**/graphql', route => route.fulfill({ json: { data: {
      currentOperator: { subject: 'admin', organizationId: 'org', role: 'ADMIN' },
      repositoryConnections: [], featureRuns: [run], featureRun: run, runners: [], myOrganizationMemberships: [],
      runAnalytics: { totalRuns: 1, activeRuns: 1, deliveredRuns: 0, providerRequests: 0, inputTokens: 0,
        outputTokens: 0, knownCostMicros: 0, costCoverage: 1, modelComparisons: [], harnessComparisons: [] },
    } } }));
    await page.goto('/app');
    await page.getByRole('row', { name: 'Open run Approval policy test' }).click();
    if (requiresHumanApproval) {
      await expect(page.getByRole('button', { name: 'Approve release' })).toBeVisible();
    } else {
      await expect(page.getByText('Verification passed. Publishing automatically; no human approval is required.')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Approve release' })).toHaveCount(0);
    }
  });
}
