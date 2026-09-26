import { expect, test } from '@playwright/test';

/** Smoke test the operator control plane against the Docker-delivered web application. */
test('renders the persisted delivery-run console', async ({ page }) => {
  await page.goto('/app');

  await expect(page.getByRole('heading', { name: 'Runs', exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'ForgeLoop navigation' })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Filter runs' })).toBeVisible();
  await expect(page.getByRole('button', { name: '+ New Run' })).toBeVisible();
  if (await page.getByText(/GitHub issues carrying/).count()) {
    await expect(page.getByText(/GitHub issues carrying/)).toBeVisible();
  } else {
    await page.getByRole('row', { name: /Open run/ }).first().click();
    await expect(page.getByRole('heading', { name: 'Task graph & attempts' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Evidence browser & redacted logs' })).toBeVisible();
  }

  await page.getByRole('button', { name: /Harness & policy/ }).click();
  await expect(page.getByRole('heading', { name: 'Harness & policy' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Execution policy' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Harness definitions' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'MCP routes' })).toBeVisible();
});
