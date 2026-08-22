import AxeBuilder from '@axe-core/playwright';
import { chromium } from 'playwright-core';

const baseUrl = process.env.SAFA_BROWSER_BASE_URL || 'http://127.0.0.1:8000';
const executablePath = process.env.CHROME_BIN;
if (!executablePath) throw new Error('CHROME_BIN is required');

const browser = await chromium.launch({
  executablePath,
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});

const failures = [];

async function audit(page, label) {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
    .analyze();
  const severe = result.violations.filter(violation => ['serious', 'critical'].includes(violation.impact || ''));
  for (const violation of severe) {
    for (const node of violation.nodes.slice(0, 5)) {
      failures.push(`${label}: ${violation.id} [${violation.impact}] ${node.target.join(' ')}`);
    }
  }
}

async function assertTouchTargets(page, label) {
  const undersized = await page.locator('.icon-button:visible').evaluateAll(elements => elements
    .map(element => {
      const rect = element.getBoundingClientRect();
      return { width: Math.round(rect.width), height: Math.round(rect.height), label: element.getAttribute('aria-label') || element.id || element.className };
    })
    .filter(item => item.width < 44 || item.height < 44));
  for (const item of undersized) failures.push(`${label}: undersized icon control ${item.label} (${item.width}x${item.height})`);
}

async function runLocale(locale) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: locale === 'bn' ? 'bn-BD' : 'en-US' });
  const page = await context.newPage();

  await page.goto(`${baseUrl}/login?lang=${locale}`, { waitUntil: 'networkidle' });
  await audit(page, `${locale}:login`);

  await page.locator('input[name="identity"]').fill('0500000000');
  await page.locator('input[name="credential"]').fill('123456');
  await Promise.all([
    page.waitForURL(url => url.pathname === '/app'),
    page.locator('button[type="submit"]').click(),
  ]);
  await page.waitForLoadState('networkidle');
  await audit(page, `${locale}:dashboard`);
  await assertTouchTargets(page, `${locale}:dashboard`);

  for (const screen of ['customers', 'suppliers', 'wallet', 'expenses']) {
    const nav = page.locator(`[data-nav="${screen}"]:visible`).first();
    if (await nav.count()) {
      await nav.click();
      await page.locator(`.screen.active[data-screen="${screen}"]`).waitFor();
      await audit(page, `${locale}:${screen}`);
      await assertTouchTargets(page, `${locale}:${screen}`);
    }
  }

  const settings = page.locator('[data-open-settings]:visible').first();
  await settings.click();
  await page.locator('.screen.active[data-screen="settings"]').waitFor();
  await audit(page, `${locale}:settings`);
  await assertTouchTargets(page, `${locale}:settings`);

  const customersNav = page.locator('[data-nav="customers"]:visible').first();
  if (await customersNav.count()) {
    await customersNav.click();
    const trigger = page.locator('[data-open-create="customer"]:visible').first();
    if (await trigger.count()) {
      await trigger.focus();
      const triggerIdentity = await trigger.evaluate(element => element.outerHTML);
      await trigger.click();
      await page.locator('#modal:not(.hidden)').waitFor();
      await audit(page, `${locale}:customer-modal`);
      const focusInside = await page.evaluate(() => document.getElementById('modal')?.contains(document.activeElement) === true);
      if (!focusInside) failures.push(`${locale}:customer-modal focus did not enter dialog`);
      await page.keyboard.press('Escape');
      await page.locator('#modal.hidden').waitFor();
      const focusReturned = await trigger.evaluate(element => document.activeElement === element);
      if (!focusReturned) failures.push(`${locale}:customer-modal focus did not return to trigger ${triggerIdentity}`);
    }
  }

  await context.close();
}

try {
  await runLocale('en');
  await runLocale('bn');
} finally {
  await browser.close();
}

if (failures.length) {
  console.error(`Accessibility gate found ${failures.length} blocking finding(s):`);
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log('WCAG 2.2 AA serious/critical accessibility gate passed for English and Bangla critical states.');
