import { expect, test } from '@playwright/test';

test('administrator can find the team invitation workflow', async ({ page }) => {
  await page.goto('/app');
  await page.getByRole('button', { name: 'Team', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Team' })).toBeVisible();
  await expect(page.getByLabel('GitHub username')).toBeVisible();
  await expect(page.getByLabel('Role')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Invite GitHub user' })).toBeDisabled();
  await page.getByLabel('GitHub username').fill('octocat');
  await expect(page.getByRole('button', { name: 'Invite GitHub user' })).toBeEnabled();
});
