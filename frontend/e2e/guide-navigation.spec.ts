import { expect, test } from '@playwright/test';

for (const width of [390, 1440]) {
  test(`guide sections are in a sidebar disclosure at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 850 });
    await page.route('**/graphql', route => route.fulfill({ json: { data: {
      currentOperator: { subject: 'reader', organizationId: 'org', role: 'VIEWER' },
      repositoryConnections: [], featureRuns: [],
      runAnalytics: { totalRuns: 0, activeRuns: 0, deliveredRuns: 0, providerRequests: 0 },
    } } }));
    await page.goto('/app');
    const mobile = width < 761;
    if (mobile) await page.getByRole('button', { name: 'Open navigation' }).click();
    const guide = page.getByRole('button', { name: 'User guide', exact: true });
    await guide.click();
    await expect(guide).toHaveAttribute('aria-expanded', 'true');
    const sections = page.locator('#guide-submenu');
    await expect(sections.getByRole('button')).toHaveCount(7);
    if (mobile) await expect(page.getByRole('button', { name: 'Close navigation' })).toBeVisible();
    await sections.getByRole('button', { name: 'Runner setup & API keys' }).click();
    await expect(page.locator('#runner-setup')).toBeFocused();
    await expect(page.locator('.content .guide-toc')).toHaveCount(0);
    if (mobile) await expect(page.getByRole('button', { name: 'Open navigation' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    if (mobile) await page.getByRole('button', { name: 'Open navigation' }).click();
    await sections.getByRole('button', { name: 'Troubleshooting' }).click();
    await expect(page.locator('#troubleshooting')).toBeFocused();
    if (mobile) await page.getByRole('button', { name: 'Open navigation' }).click();
    await guide.click();
    await expect(sections).toBeHidden();
  });
}
