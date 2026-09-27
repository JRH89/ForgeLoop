import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

for (const width of [390, 1440]) {
  test(`cost graphs and mobile navigation at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const now = new Date().toISOString();
    const attempt = { id: 'a', provider: 'anthropic', model: 'example', inputTokens: 100, outputTokens: 25, estimatedCostMicros: 2_000_000, costKnown: true, recordedAt: now };
    const run = { id: 'r', title: 'Archived cost evidence', repository: 'acme/project', sourceRef: 'issue-3', archived: true, state: 'COMPLETE', createdAt: now, budgetUsd: 10, harnessProfile: 'GENERIC', tasks: [{ id: 't', state: 'VERIFIED', providerAttempts: [attempt, { ...attempt, id: 'b', costKnown: false }] }], gates: [], criteria: [] };
    await page.route('**/graphql', route => route.fulfill({ json: { data: {
      currentOperator: { subject: 'user', organizationId: 'org', role: 'VIEWER' }, repositoryConnections: [], featureRuns: [run],
      runAnalytics: { totalRuns: 1, activeRuns: 0, deliveredRuns: 1, providerRequests: 2, modelComparisons: [], harnessComparisons: [] },
    } } }));
    await page.goto('/app');
    if (width === 390) await page.getByRole('button', { name: 'Open navigation' }).click();
    await page.getByRole('button', { name: 'Usage & costs', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Usage & costs' })).toBeVisible();
    await expect(page.getByRole('img', { name: /Daily estimated cost/ })).toBeVisible();
    await expect(page.getByText('50%', { exact: true })).toBeVisible();
    await page.getByLabel('Period (UTC)').selectOption('7');
    await page.getByLabel('Repository', { exact: true }).selectOption('acme/project');
    await expect(page.getByText('issue-3: Archived cost evidence')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: `../evidence/forgeloop-ui/usage-${width}.png`, fullPage: true });
    if (width === 390) {
      await page.getByRole('button', { name: 'Open navigation' }).click();
      await expect(page.locator('#console-navigation')).toHaveCSS('opacity', '1');
      await page.screenshot({ path: '../evidence/forgeloop-ui/mobile-console-menu.png' });
      await page.mouse.click(380, 880);
      await expect(page.getByRole('button', { name: 'Open navigation' })).toBeVisible();
    }
  });
}
