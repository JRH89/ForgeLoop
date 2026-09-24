import { expect, test } from '@playwright/test';

/** Smoke test the operator control plane against the Docker-delivered web application. */
test('renders the persisted delivery-run console', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Runs', exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'ForgeLoop navigation' })).toBeVisible();
  await expect(page.getByText('Intake queue')).toBeVisible();
  await expect(page.getByRole('button', { name: '+ New run' })).toBeVisible();
  if (await page.getByRole('heading', { name: 'No run selected' }).count()) {
    await expect(page.getByText(/GitHub issues carrying/)).toBeVisible();
  } else {
    await expect(page.getByRole('heading', { name: 'Task graph & attempts' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Evidence browser & redacted logs' })).toBeVisible();
  }

  await page.getByRole('button', { name: /Harness & policy/ }).click();
  await expect(page.getByRole('heading', { name: 'Harness & policy' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Execution policy' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Harness definitions' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'MCP routes' })).toBeVisible();
});
