import { expect, test } from '@playwright/test';
import { adminDoc, login, newUser } from './helpers';

// Superadmin creates a brand-new event and configures it from scratch, like a partner would.
test.describe.serial('event setup', () => {
  const SLUG = 'partner-conf';

  test('superadmin creates an event', async ({ browser }) => {
    const { ctx, page } = await newUser(browser, { desktop: true });
    await login(page, 'superadmin@handsup.test');
    await expect(page.getByTestId('create-event')).toBeVisible();
    await page.locator('#ev-name').fill('Partner Conf');
    await page.locator('#ev-slug').fill(SLUG);
    await page.getByTestId('create-event').getByRole('button', { name: 'Crear' }).click();
    await expect(page).toHaveURL(new RegExp(`/admin/e/${SLUG}/setup$`));
    await expect(page.getByTestId('admin-event-name')).toHaveText('Partner Conf');

    // rooms
    await page.getByTestId('new-room-name').fill('Sala Principal');
    await page.getByTestId('add-room').click();
    await expect(page.getByTestId('rooms-editor')).toContainText('sala-principal');

    // talks need a room: add one by hand
    await page.getByTestId('setup-tab-talks').click();
    await page.getByTestId('new-talk').click();
    const form = page.getByTestId('talk-form');
    await form.locator('#tk-title').fill('Keynote de apertura');
    await form.locator('#tk-speakers').fill('Ada Lovelace');
    await form.locator('#tk-code').fill('KEYNT'); // 0, O, 1, I and L are not allowed in talk codes
    await expect(form.getByTestId('save-talk')).toBeEnabled();
    await form.getByTestId('save-talk').click();
    await expect(page.getByTestId('setup-talk')).toHaveCount(1);
    expect((await adminDoc(`events/${SLUG}/talks/KEYNT`))?.title).toBe('Keynote de apertura');

    // JSON import: invalid first, then valid
    await page.getByTestId('setup-tab-data').click();
    await page.getByTestId('import-text').fill('{ nope');
    await expect(page.getByTestId('import-error')).toBeVisible();
    await page.getByTestId('import-text').fill(
      JSON.stringify({
        rooms: [{ id: 'sala-principal', name: 'Sala Principal' }, { id: 'taller', name: 'Taller' }],
        talks: [
          { title: 'Charla A', speakers: 'X', room: 'sala-principal', start: '2027-05-01T10:00:00+02:00', end: '2027-05-01T10:45:00+02:00' },
          { title: 'Taller B', room: 'taller', start: '2027-05-01T11:00:00+02:00', end: '2027-05-01T12:00:00+02:00' },
        ],
      }),
    );
    await expect(page.getByTestId('import-preview')).toContainText('2 salas y 2 charlas');
    await page.getByTestId('run-import').click();
    await page.getByTestId('setup-tab-talks').click();
    await expect(page.getByTestId('setup-talk')).toHaveCount(3);

    // branding is validated and saved
    await page.getByTestId('setup-tab-branding').click();
    await page.getByTestId('brand-name').fill('Partner Conf 2027');
    await page.getByTestId('brand-primary').fill('#1a73e8');
    await page.getByTestId('save-branding').click();
    await expect.poll(async () => (await adminDoc(`events/${SLUG}`))?.branding?.primary).toBe('#1a73e8');
    expect((await adminDoc(`events/${SLUG}`))?.name).toBe('Partner Conf 2027');
    await page.getByTestId('brand-primary').fill('red');
    await expect(page.getByTestId('save-branding')).toBeDisabled();

    // settings
    await page.getByTestId('setup-tab-settings').click();
    await page.getByTestId('set-preapproval').check();
    await page.getByTestId('save-settings').click();
    await expect.poll(async () => (await adminDoc(`events/${SLUG}`))?.settings?.preApproval).toBe(true);

    // team
    await page.getByTestId('setup-tab-team').click();
    await page.getByTestId('organizer-email').fill('Friend@Example.com');
    await page.getByTestId('add-organizer').click();
    await expect(page.getByTestId('organizer')).toContainText('friend@example.com');
    await ctx.close();
  });

  test('attendees see the branded event and agenda without signing in', async ({ browser }) => {
    const { ctx, page } = await newUser(browser);
    await page.goto(`/e/${SLUG}`);
    await expect(page.locator('header')).toContainText('Partner Conf 2027');
    await expect(page.getByTestId('agenda-item')).toHaveCount(3);
    const brand = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--brand').trim());
    expect(brand).toBe('#1a73e8');
    await page.getByTestId('no-live').waitFor();
    // wrong code is rejected, right code opens the talk
    await page.locator('#code').fill('ZZZZZ');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByText('Ese código no corresponde')).toBeVisible();
    await page.locator('#code').fill('keynt');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByTestId('talk-title')).toHaveText('Keynote de apertura');
    await ctx.close();
  });

  test('pre-approval holds questions until an organizer approves', async ({ browser }) => {
    const org = await newUser(browser, { desktop: true });
    await login(org.page, 'superadmin@handsup.test');
    await org.page.goto(`/admin/e/${SLUG}`);
    await org.page.locator('[data-testid=admin-talk][data-code=KEYNT]').getByTestId('go-live').click();
    await expect(org.page.locator('[data-testid=admin-talk][data-code=KEYNT]').getByTestId('end-talk')).toBeVisible();

    const a = await newUser(browser);
    await a.page.goto(`/e/${SLUG}/t/KEYNT`);
    await a.page.locator('#q-text').fill('¿Se revisa antes de publicar?');
    await a.page.getByTestId('send').click();
    await a.page.getByTestId('tab-mine').click();
    await expect(a.page.getByTestId('my-list')).toContainText('Pendiente de revisión');
    await a.page.getByTestId('tab-all').click();
    await expect(a.page.getByTestId('empty')).toBeVisible();

    await org.page.goto(`/admin/e/${SLUG}/live/KEYNT`);
    await org.page.getByTestId('lane-pending').click();
    await org.page.getByTestId('act-approve').click();
    await a.page.getByTestId('tab-all').click();
    await expect(a.page.getByTestId('question').first()).toContainText('¿Se revisa antes de publicar?');
    await org.ctx.close();
    await a.ctx.close();
  });

  test('a plain organizer cannot create events or change the team', async ({ browser }) => {
    const { ctx, page } = await newUser(browser, { desktop: true });
    await login(page, 'organizer@handsup.test');
    await expect(page.getByTestId('create-event')).toHaveCount(0);
    await page.goto(`/admin/e/${SLUG}`);
    await expect(page.getByTestId('no-access')).toBeVisible();
    await ctx.close();
  });

  test('export and delete', async ({ browser }) => {
    const { ctx, page } = await newUser(browser, { desktop: true });
    await login(page, 'superadmin@handsup.test');
    await page.goto(`/admin/e/${SLUG}/setup`);
    await page.getByTestId('setup-tab-data').click();
    const download = page.waitForEvent('download');
    await page.getByTestId('export-csv').click();
    expect((await download).suggestedFilename()).toBe(`${SLUG}-questions.csv`);

    await page.getByTestId('purge').click();
    await page.getByTestId('purge').click();
    await expect(page.getByText('Identidades borradas')).toBeVisible();
    await page.getByTestId('delete-slug').fill(SLUG);
    await page.getByTestId('delete-event').click();
    await expect(page).toHaveURL(/\/admin$/);
    await expect.poll(() => adminDoc(`events/${SLUG}`)).toBeNull();
    await ctx.close();
  });
});
