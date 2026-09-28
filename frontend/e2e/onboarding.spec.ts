import { expect, test } from '@playwright/test';

/** Deterministic browser coverage: fixtures never contact GitHub or a model provider. */
for (const width of [390, 1440]) {
  test(`setup checklist and issue diagnosis at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const queries: string[] = [];
    await page.route('**/graphql', async route => {
      const { query } = route.request().postDataJSON();
      queries.push(query);
      expect(query).not.toContain('mutation');
      const data = query.includes('currentOperator') ? { currentOperator: { subject: 'admin', organizationId: 'org', role: 'ADMIN' } }
        : query.includes('repositoryConnections') ? { repositoryConnections: [{ id: 'repo', repository: 'acme/project', installationId: 1, enabled: true, defaultBranch: 'main', issueLabel: 'forgeloop', requiredAssignee: 'owner', harnessProfile: 'GENERIC', requiredGates: ['unit'], maxBudgetUsd: 5, policyRevision: 1 }] }
        : query.includes('organizationPolicy') ? { organizationPolicy: { organizationId: 'org', maxRunBudgetUsd: 10, maxParallelTasks: 2, allowedProviders: ['ANTHROPIC'], requireHumanApproval: true, autoMergeEnabled: false, revision: 1 }, harnessDefinitions: [{ name: 'GENERIC', enabled: true }], localMcpConfigurations: [] }
        : query.includes('runners(') ? { runners: [] }
        : query.includes('issueIntakeCheck') ? { issueIntakeCheck: { eligible: false, reasons: ['Add the required label: forgeloop', 'Assign the issue to: owner'], checkedAt: new Date().toISOString() } }
        : query.includes('runAnalytics') ? { runAnalytics: { totalRuns: 0, activeRuns: 0, deliveredRuns: 0, providerRequests: 0, inputTokens: 0, outputTokens: 0, knownCostMicros: 0, costCoverage: 0, modelComparisons: [], harnessComparisons: [] } }
        : { featureRuns: [] };
      await route.fulfill({ json: { data } });
    });
    await page.goto('/app');
    if (width === 390) {
      await expect(page.getByRole('navigation', { name: 'ForgeLoop navigation' })).not.toBeVisible();
      await page.getByRole('button', { name: 'Open navigation' }).click();
      await expect(page.getByRole('button', { name: 'Account', exact: true })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Support', exact: true })).toHaveCount(0);
      await expect(page.getByRole('link', { name: 'Support tickets', exact: true })).toHaveCount(0);
      await expect(page.getByRole('navigation').getByRole('link', { name: 'Sign out' })).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(page.getByRole('button', { name: 'Open navigation' })).toBeFocused();
      await expect(page.getByRole('navigation', { name: 'ForgeLoop navigation' })).not.toBeVisible();
      await page.getByRole('button', { name: 'Open navigation' }).click();
      await page.getByRole('button', { name: 'Usage & costs', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Usage & costs', exact: true })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'ForgeLoop navigation' })).not.toBeVisible();
      await page.getByRole('button', { name: 'Open navigation' }).click();
      await page.getByRole('button', { name: 'Runs', exact: true }).click();
    } else {
      await expect(page.getByRole('link', { name: 'Sign out' })).toHaveCSS('border-top-style', 'solid');
    }
    await page.getByRole('button', { name: 'Open setup checklist' }).click();
    await expect(page.getByRole('heading', { name: 'Getting started', exact: true })).toBeVisible();
    await expect(page.getByText('No enabled runner is enrolled.', { exact: true })).toBeVisible();
    const navigation = page.getByRole('navigation', { name: 'ForgeLoop navigation' });
    if (width === 390) await page.getByRole('button', { name: 'Open navigation' }).click();
    await expect(navigation.getByRole('button', { name: 'Issue chat', exact: true })).toBeVisible();
    await expect(navigation.getByRole('button', { name: 'User guide', exact: true })).toBeVisible();
    if (width === 390) await page.keyboard.press('Escape');
    const draftUrl = new URL((await page.getByRole('link', { name: 'Draft a safe issue on GitHub' }).getAttribute('href')) ?? '');
    expect(draftUrl.searchParams.get('title')).toBe('Describe the change');
    expect(draftUrl.searchParams.get('body')).toContain('Required label: `forgeloop`');
    expect(draftUrl.searchParams.get('body')).toContain('Required assignee: `owner`');
    expect(draftUrl.searchParams.has('labels')).toBe(false);
    expect(draftUrl.searchParams.has('assignees')).toBe(false);
    await expect(page.getByText(/A running worker can make paid provider calls/)).toBeVisible();
    await page.getByLabel('GitHub issue number or URL').fill('https://github.com/acme/project/issues/7');
    await page.getByRole('button', { name: 'Check intake — read only', exact: true }).click();
    await expect(page.getByText('Add the required label: forgeloop', { exact: true })).toBeVisible();
    await expect(page.getByText('Assign the issue to: owner', { exact: true })).toBeVisible();
    expect(queries.some(query => query.includes('issueIntakeCheck'))).toBe(true);
    expect(queries.every(query => !query.includes('mutation'))).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `../evidence/forgeloop-ui/onboarding-${width}.png`, fullPage: true });
    await page.getByRole('button', { name: 'Read the complete user guide' }).click();
    await expect(page.getByRole('heading', { name: 'Start with the setup checklist' })).toBeVisible();
  });
}
