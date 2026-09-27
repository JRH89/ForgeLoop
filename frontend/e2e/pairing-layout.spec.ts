import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

for (const width of [320, 1440]) {
  test(`pairing layout and approval states at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.route('**/graphql', route => route.fulfill({ json: { data: {
      currentOperator: { subject: 'admin', organizationId: 'org', role: 'ADMIN' },
      approveRunnerPairing: true,
    } } }));
    await page.goto('/app/runner-connect#challenge=' + 'a'.repeat(64) + '&name=Windows%20desktop%20test');
    await expect(page.getByText('aaaaaaaaaaaa', { exact: true })).toBeVisible();
    const approve = page.getByRole('button', { name: 'Approve runner' });
    await expect(approve).toBeDisabled();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const card = await page.locator('.pairing-card').boundingBox();
    expect(card!.width).toBeLessThanOrEqual(580);
    expect(new Set((await new AxeBuilder({ page }).analyze()).violations.map(v => v.id))).toEqual(new Set());
    await page.screenshot({ path: `../evidence/forgeloop-ui/pairing-${width}.png`, fullPage: true });
    await page.getByRole('checkbox').check();
    await approve.click();
    await expect(page.getByRole('status')).toContainText('No paid work has started');
    await page.goto('/app/runner-connect');
    await expect(page.getByRole('alert')).toContainText('Invalid pairing request');
    await expect(page.getByRole('button', { name: 'Approve runner' })).toHaveCount(0);
  });
}
