import { expect, type Browser, type BrowserContext, type Page } from '@playwright/test';

export const EVENT = 'fresonfest-2026';
export const TALK = 'A4EEQ'; // first Auditorio talk in the seed
export const TALK2 = '9G6DK';
export const SALA17_TALK = 'T33J6';

export async function newUser(browser: Browser, opts: { desktop?: boolean } = {}): Promise<{ ctx: BrowserContext; page: Page }> {
  const ctx = await browser.newContext({
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
    ...(opts.desktop ? { viewport: { width: 1280, height: 900 } } : { viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true }),
  });
  const page = await ctx.newPage();
  page.on('console', (m) => m.type() === 'error' && console.log('[browser error]', m.text().slice(0, 300)));
  page.on('pageerror', (e) => console.log('[page error]', e.message.slice(0, 300)));
  return { ctx, page };
}

export async function login(page: Page, email = 'organizer@handsup.test') {
  await page.goto('/admin');
  const form = page.getByTestId('dev-login');
  await expect(form).toBeVisible();
  await form.locator('input[type=email]').fill(email);
  await form.locator('input[type=password]').fill('handsup-test');
  await form.getByRole('button').click();
  await expect(page.getByTestId('admin-email')).toBeVisible();
}

/** Reads a document straight from the Firestore emulator, bypassing rules. */
export async function adminDoc(path: string): Promise<Record<string, any> | null> {
  const res = await fetch(`http://127.0.0.1:8080/v1/projects/handsup-demo/databases/(default)/documents/${path}`, {
    headers: { Authorization: 'Bearer owner' },
  });
  if (res.status === 404) return null;
  const json = await res.json();
  const decode = (v: any): any =>
    'stringValue' in v ? v.stringValue
    : 'integerValue' in v ? Number(v.integerValue)
    : 'booleanValue' in v ? v.booleanValue
    : 'mapValue' in v ? Object.fromEntries(Object.entries(v.mapValue.fields ?? {}).map(([k, x]) => [k, decode(x)]))
    : v.timestampValue ?? null;
  return Object.fromEntries(Object.entries(json.fields ?? {}).map(([k, v]) => [k, decode(v)]));
}

export async function ask(page: Page, text: string, delivery: 'speak' | 'read' = 'read') {
  await page.getByTestId(`delivery-choice-${delivery}`).click();
  await page.locator('#q-text').fill(text);
  await page.getByTestId('send').click();
}
