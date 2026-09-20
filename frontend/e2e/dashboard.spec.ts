import { expect, test } from '@playwright/test';

/** Smoke test the operator control plane against the Docker-delivered web application. */
test('renders the persisted delivery-run console', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Verified changes, not agent claims.' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'ForgeLoop navigation' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create delivery run' })).toBeVisible();
});
