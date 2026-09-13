import { test, expect } from '@playwright/test';

test.describe('phase5 prediction ticket', () => {
  test('public market surface does not expose private betting data', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('body')).not.toContainText(/matricula|hmac|authorization|token/i);
  });
});
