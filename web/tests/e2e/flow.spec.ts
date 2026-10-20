import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { EVENT, TALK, adminDoc, ask, login, newUser } from './helpers';

// One conversation between an organizer, two attendees, a stage screen and a speaker.
test.describe.serial('live Q&A flow', () => {
  let org: { ctx: BrowserContext; page: Page };
  let a1: { ctx: BrowserContext; page: Page };
  let a2: { ctx: BrowserContext; page: Page };
  let stage: { ctx: BrowserContext; page: Page };

  test.beforeAll(async ({ browser }) => {
    org = await newUser(browser, { desktop: true });
    a1 = await newUser(browser);
    a2 = await newUser(browser);
    stage = await newUser(browser, { desktop: true });
  });
  test.afterAll(async () => {
    await Promise.all([org, a1, a2, stage].map((u) => u.ctx.close()));
  });

  test('attendee sees a scheduled talk but cannot ask yet', async () => {
    await a1.page.goto(`/e/${EVENT}/t/${TALK}`);
    await expect(a1.page.getByTestId('talk-title')).toContainText('La primera misión');
    await expect(a1.page.getByTestId('composer-closed')).toBeVisible();
    await expect(a1.page.getByTestId('pseudonym-bar')).toBeVisible();
    await expect(a1.page.getByTestId('my-pseudonym')).toHaveText(/^\S+ \S+$/);
  });

  test('organizer signs in and takes the talk live', async () => {
    await login(org.page);
    await org.page.getByTestId('admin-event').click();
    const row = org.page.locator(`[data-testid=admin-talk][data-code=${TALK}]`);
    await row.getByTestId('go-live').click();
    await expect(row.getByTestId('end-talk')).toBeVisible();
  });

  test('home opens the live talk automatically', async () => {
    await a2.page.goto('/');
    await expect(a2.page).toHaveURL(new RegExp(`/e/${EVENT}/t/${TALK}$`));
    await expect(a2.page.getByTestId('send')).toBeVisible();
  });

  test('attendee asks, choosing to speak', async () => {
    await ask(a1.page, '¿Cómo empezaste en tecnología?', 'speak');
    const q = a1.page.getByTestId('question').first();
    await expect(q.getByTestId('q-text')).toHaveText('¿Cómo empezaste en tecnología?');
    await expect(q.getByTestId('delivery-speak')).toBeVisible();
    // rate limit kicks in
    await expect(a1.page.getByTestId('send')).toBeDisabled();
    await expect(a1.page.getByTestId('send')).toContainText(/Espera \d+ s/);
  });

  test('a second attendee sees it in real time and votes', async () => {
    const q = a2.page.getByTestId('question').first();
    await expect(q.getByTestId('q-text')).toHaveText('¿Cómo empezaste en tecnología?');
    await expect(q.getByTestId('vote-count')).toHaveText('0');
    await q.getByTestId('vote').click();
    await expect(q.getByTestId('vote-count')).toHaveText('1');
    await expect(q.getByTestId('vote')).toHaveAttribute('aria-pressed', 'true');
    // the first attendee sees the count update live
    await expect(a1.page.getByTestId('question').first().getByTestId('vote-count')).toHaveText('1');
    // voting survives a reload
    await a2.page.reload();
    await expect(a2.page.getByTestId('question').first().getByTestId('vote')).toHaveAttribute('aria-pressed', 'true');
  });

  test('voting twice is impossible; removing the vote works', async () => {
    const vote = a2.page.getByTestId('question').first().getByTestId('vote');
    await vote.click();
    await expect(vote).toHaveAttribute('aria-pressed', 'false');
    await expect(a2.page.getByTestId('question').first().getByTestId('vote-count')).toHaveText('0');
    await vote.click();
    await expect(a2.page.getByTestId('question').first().getByTestId('vote-count')).toHaveText('1');
  });

  test('second attendee asks for the host to read', async () => {
    await ask(a2.page, '¿Qué consejo darías a quien busca su primer trabajo?', 'read');
    await expect(a2.page.getByTestId('question')).toHaveCount(2);
  });

  test('organizer queue shows both, most voted first, with delivery icons', async () => {
    await org.page.goto(`/admin/e/${EVENT}/live/${TALK}`);
    const cards = org.page.getByTestId('admin-question');
    await expect(cards).toHaveCount(2);
    await expect(cards.nth(0).getByTestId('q-text')).toContainText('¿Cómo empezaste');
    await expect(cards.nth(0).getByTestId('delivery-speak')).toBeVisible();
    await expect(cards.nth(1).getByTestId('delivery-read')).toBeVisible();
    // filter by delivery
    await org.page.getByTestId('filter-read').click();
    await expect(cards).toHaveCount(1);
    await expect(cards.first().getByTestId('q-text')).toContainText('primer trabajo');
    await org.page.getByTestId('filter-all').click();
    await expect(cards).toHaveCount(2);
  });

  test('stage screen shows the join code, then the question put on stage', async () => {
    await stage.page.goto(`/stage/${EVENT}/auditorio`);
    await expect(stage.page.getByTestId('stage-code')).toHaveText(TALK);
    await expect(stage.page.getByTestId('stage-ask')).toBeVisible();

    await org.page.getByTestId('admin-question').first().getByTestId('act-stage').click();
    await expect(org.page.getByTestId('onstage-section')).toContainText('¿Cómo empezaste');
    await expect(stage.page.getByTestId('stage-question')).toContainText('¿Cómo empezaste en tecnología?');
    await expect(stage.page.getByTestId('stage-question').getByTestId('delivery-speak')).toBeVisible();
    // attendees see it highlighted too
    await expect(a2.page.locator('[data-status=onstage]')).toContainText('¿Cómo empezaste');
  });

  test('putting another question on stage marks the previous one answered', async () => {
    await org.page.getByTestId('queue-list').getByTestId('admin-question').filter({ hasText: 'primer trabajo' }).getByTestId('act-stage').click();
    await expect(stage.page.getByTestId('stage-question')).toContainText('primer trabajo');
    await org.page.getByTestId('lane-answered').click();
    await expect(org.page.getByTestId('queue-list').getByTestId('admin-question')).toContainText('¿Cómo empezaste');
  });

  test('organizer hides a question; the author still sees it as hidden', async () => {
    await org.page.getByTestId('lane-open').click();
    await org.page.getByTestId('onstage-section').getByRole('button', { name: 'Volver a la cola' }).click();
    await org.page.getByTestId('lane-open').click();
    const card = org.page.getByTestId('queue-list').getByTestId('admin-question').filter({ hasText: 'primer trabajo' });
    await card.getByTestId('act-hide').click();
    await expect(org.page.getByTestId('queue-empty')).toBeVisible();
    // a1 does not see it in the public list...
    await expect(a1.page.getByTestId('question-list').getByText('primer trabajo')).toHaveCount(0);
    // ...but a2 (the author) sees it under "mine" with its status
    await a2.page.getByTestId('tab-mine').click();
    await expect(a2.page.getByTestId('my-list')).toContainText('primer trabajo');
    await expect(a2.page.getByTestId('my-list')).toContainText('Oculta');
  });

  test('follow-up: speaker link lets the speaker answer, asker sees the answer', async () => {
    // restore the hidden one and send it to follow-up
    await org.page.getByTestId('lane-hidden').click();
    await org.page.getByRole('button', { name: 'Restaurar' }).click();
    await org.page.getByTestId('lane-open').click();
    await org.page.getByTestId('queue-list').getByTestId('admin-question').filter({ hasText: 'primer trabajo' }).getByTestId('act-followup').click();
    await expect(org.page.getByTestId('lane-followup')).toContainText('1');

    // organizer generates the speaker link
    await org.page.goto(`/admin/e/${EVENT}`);
    await org.page.locator(`[data-testid=admin-talk][data-code=${TALK}]`).getByTestId('speaker-link').click();
    await expect.poll(async () => (await adminDoc(`events/${EVENT}/talkSecrets/${TALK}`))?.speakerKey, { timeout: 8000 }).toBeTruthy();
    const key = (await adminDoc(`events/${EVENT}/talkSecrets/${TALK}`))!.speakerKey as string;

    const speaker = await newUser((org.ctx.browser())!);
    await speaker.page.goto(`/speaker/${EVENT}/${TALK}/${key}`);
    await expect(speaker.page.getByTestId('speaker-talk')).toContainText('La primera misión');
    const card = speaker.page.getByTestId('speaker-question');
    await expect(card).toHaveCount(1);
    await expect(card).toContainText('primer trabajo');
    await card.getByTestId('speaker-answer').fill('Empieza por proyectos pequeños y compártelos.');
    await card.getByTestId('speaker-save').click();

    await a2.page.getByTestId('tab-all').click();
    await a2.page.locator('summary').click();
    await expect(a2.page.getByTestId('q-answer')).toContainText('Empieza por proyectos pequeños');
    await speaker.ctx.close();
  });

  test('a wrong speaker key is rejected', async ({ browser }) => {
    const u = await newUser(browser);
    await u.page.goto(`/speaker/${EVENT}/${TALK}/${'x'.repeat(32)}`);
    await expect(u.page.getByTestId('speaker-invalid')).toBeVisible();
    await u.ctx.close();
  });

  test('author edits and withdraws their question', async () => {
    test.setTimeout(90_000); // the send button re-enables once the 30 s rate limit has passed
    await ask(a1.page, 'Pregunta para retirar', 'read');
    await a1.page.getByTestId('tab-mine').click();
    const card = a1.page.getByTestId('my-list').getByTestId('question').first(); // newest first
    await card.getByTestId('q-edit').click();
    await card.locator('textarea').fill('Pregunta editada');
    await card.getByRole('button', { name: 'Guardar' }).click();
    await expect(a1.page.getByTestId('my-list')).toContainText('Pregunta editada');
    await a1.page.getByTestId('my-list').getByTestId('question').filter({ hasText: 'Pregunta editada' }).getByTestId('q-withdraw').click();
    await expect(a1.page.getByTestId('my-list')).toContainText('Retirada');
  });

  test('organizer ends the talk; questions close after the grace period setting', async () => {
    await org.page.goto(`/admin/e/${EVENT}`);
    const row = org.page.locator(`[data-testid=admin-talk][data-code=${TALK}]`);
    await row.getByRole('button', { name: 'Cerrar preguntas' }).click();
    await expect(row.getByText('Preguntas cerradas')).toBeVisible();
    await expect(a1.page.getByTestId('composer-closed')).toBeVisible();
    await row.getByTestId('end-talk').click();
    await expect(row.getByTestId('go-live')).toBeVisible();
  });
});
