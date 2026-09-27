import { expect, test, type Page } from '@playwright/test';

async function fits(page: Page) {
  const layout = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
    outside: [...document.querySelectorAll('.content *')].filter(element => element.getBoundingClientRect().right > innerWidth + 1).slice(0, 12).map(element => ({ tag: element.tagName, className: element.className, width: element.getBoundingClientRect().width })) }));
  expect(layout.scroll, JSON.stringify(layout)).toBeLessThanOrEqual(layout.width);
  const clipped = await page.locator('.setup-entry, .runs-dashboard, .detail-stack, .content .panel').evaluateAll(elements => elements.filter(element => {
    const box = element.getBoundingClientRect();
    return box.left < -1 || box.right > innerWidth + 1;
  }).map(element => element.className));
  expect(clipped).toEqual([]);
}

for (const width of [320, 390, 430, 768, 1440]) {
  test(`populated run queue, details, and forms fit ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 850 });
    const long = 'long-unbroken-repository-or-path-segment-'.repeat(4);
    const repository = `acme/${long}`;
    const title = 'Add a recoverable retry flow for failed ticket assignment and preserve all existing user data';
    const run = { id: 'r', repository, sourceRef: 'issue-7', title, specification: `Acceptance criteria: preserve data.\n\n\`\`\`text\n${long}\n\`\`\``, archived: false, state: 'COMPLETE', createdAt: new Date().toISOString(), budgetUsd: 25, spentCostMicros: 0, approved: false, policyRevision: 1, harnessProfile: 'GENERIC', baseBranch: 'main',
      tasks: [{ id: 't', planKey: long, role: 'BACKEND', executionRole: 'BACKEND', title: 'Implement and verify retry handling', state: 'VERIFIED', attemptBudget: 2, attempts: 1, ownedPaths: [long], dependencyKeys: [], repairPackages: [], providerAttempts: [{ id: 'a', provider: 'anthropic', model: long, inputTokens: 1234, outputTokens: 567, estimatedCostMicros: 0, costKnown: false, outcome: 'SUCCEEDED', recordedAt: new Date().toISOString() }] }],
      gates: [{ id: 'g', name: 'acceptance tests', state: 'PASSED', required: true, kind: 'TEST', networkPolicy: 'NONE', timeoutSeconds: 120, command: ['npm', 'test'] }], criteria: [{ id: 'c', statement: long, coverageState: 'COVERED' }] };
    await page.route('**/graphql', route => {
      expect(route.request().postDataJSON().query).not.toContain('mutation');
      return route.fulfill({ json: { data: {
        currentOperator: { subject: 'admin', organizationId: 'org', role: 'ADMIN' },
        repositoryConnections: [{ id: 'repo', repository, installationId: 1, enabled: true, defaultBranch: 'main', issueLabel: 'forgeloop', harnessProfile: 'GENERIC', requiredGates: ['unit'], maxBudgetUsd: 25, policyRevision: 1 }],
        featureRuns: [run], featureRun: run, runAnalytics: { totalRuns: 1, activeRuns: 0, deliveredRuns: 1, providerRequests: 1 },
        organizationPolicy: { organizationId: 'org', maxRunBudgetUsd: 25, maxParallelTasks: 2, allowedProviders: ['anthropic'], requireHumanApproval: true, autoMergeEnabled: false, revision: 1 }, harnessDefinitions: [], localMcpConfigurations: [], runners: [],
      } } });
    });
    await page.goto('/app');
    const row = page.getByRole('row', { name: `Open run ${title}` });
    await expect(row).toBeVisible();
    await fits(page);
    if (width <= 760) {
      for (const label of ['Repository', 'Status', 'Progress', 'Cost', 'Started', 'Queue']) {
        const cell = row.locator(`[data-label="${label}"]`);
        await expect(cell).toBeVisible();
        const box = await cell.boundingBox();
        expect(box!.x + box!.width).toBeLessThanOrEqual(width);
      }
      expect(await page.locator('.runs-table-wrap').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
      await page.screenshot({ path: `../evidence/forgeloop-ui/run-cards-${width}.png`, fullPage: true });
    }
    page.once('dialog', dialog => dialog.dismiss());
    await row.getByRole('button', { name: 'Archive', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Task graph & attempts' })).toHaveCount(0);
    await row.click();
    await expect(page.getByRole('heading', { name: 'Task graph & attempts' })).toBeVisible();
    await fits(page);
    await page.screenshot({ path: `../evidence/forgeloop-ui/run-details-${width}.png`, fullPage: true });
    await page.getByRole('button', { name: '+ New Run' }).click();
    await expect(page.getByLabel('Specification', { exact: true })).toBeVisible();
    await fits(page);
    if (width <= 760) await page.getByRole('button', { name: 'Open navigation' }).click();
    await page.getByRole('button', { name: 'Repositories', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Connected repositories', exact: true })).toBeVisible();
    await fits(page);
    if (width <= 760) await page.getByRole('button', { name: 'Open navigation' }).click();
    await page.getByRole('button', { name: 'Harness & policy', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Execution policy', exact: true })).toBeVisible();
    await fits(page);
  });
}
