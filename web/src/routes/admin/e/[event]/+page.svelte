<script lang="ts">
  import { base } from '#lib/paths';
  import { page } from '$app/state';
  import { backend } from '#lib/backend';
  import Chip from '#lib/components/Chip.svelte';
  import Icon from '#lib/components/Icon.svelte';
  import Qr from '#lib/components/Qr.svelte';
  import type { Talk } from '#lib/domain/types';
  import { getEventContext } from '#lib/eventContext.svelte';
  import { formatTime } from '#lib/format';
  import { t } from '#lib/i18n/index.svelte';
  import { copy, eventUrl, speakerUrl, stageUrl, talkUrl } from '#lib/links';
  import { toast } from '#lib/toast.svelte';

  const ctx = getEventContext();
  const eventId = $derived(page.params.event ?? '');
  let qrFor = $state<string | null>(null);
  let busy = $state<string | null>(null);

  const byRoom = $derived(
    ctx.rooms.map((r) => ({ room: r, talks: ctx.talks.filter((tk) => tk.roomId === r.id) })),
  );
  const orphans = $derived(ctx.talks.filter((tk) => !ctx.room(tk.roomId)));

  async function run(key: string, fn: () => Promise<unknown>) {
    busy = key;
    try {
      await fn();
    } catch {
      toast(t('err.denied'), 'error');
    } finally {
      busy = null;
    }
  }

  const goLive = (tk: Talk) => run(`live-${tk.id}`, () => backend().goLive(eventId, tk.id, tk.roomId));
  const end = (tk: Talk) => run(`end-${tk.id}`, () => backend().endTalk(eventId, tk.id, tk.roomId));
  const toggleClosed = (tk: Talk) =>
    run(`close-${tk.id}`, () => backend().setQuestionsClosed(eventId, tk.id, !tk.questionsClosed));

  async function copySpeaker(tk: Talk) {
    await run(`spk-${tk.id}`, async () => {
      const key = await backend().speakerKey(eventId, tk.id);
      const ok = await copy(speakerUrl(eventId, tk.id, key));
      toast(ok ? t('admin.speakerCopied') : speakerUrl(eventId, tk.id, key));
    });
  }
</script>

<main class="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-6">
  <section class="card flex flex-wrap items-center gap-4">
    <div class="flex-1">
      <h2 class="font-semibold">{t('admin.joinTitle')}</h2>
      <p class="text-sm text-muted break-all">{eventUrl(eventId)}</p>
    </div>
    <button class="btn-ghost" onclick={() => (qrFor = qrFor === 'event' ? null : 'event')} data-testid="event-qr-btn">
      <Icon name="qr" size={18} />QR
    </button>
    <button class="btn-ghost" onclick={async () => toast((await copy(eventUrl(eventId))) ? t('common.copied') : eventUrl(eventId))}>
      <Icon name="copy" size={18} />{t('common.copy')}
    </button>
    {#if qrFor === 'event'}
      <div class="w-full"><Qr value={eventUrl(eventId)} label="QR" size={224} /></div>
    {/if}
  </section>

  {#if ctx.loaded && ctx.talks.length === 0}
    <div class="card text-center" data-testid="no-talks">
      <p class="text-muted">{t('admin.noTalks')}</p>
      <a class="btn-primary mt-3" href="{base}/admin/e/{eventId}/setup">{t('admin.goSetup')}</a>
    </div>
  {/if}

  {#each byRoom as { room, talks } (room.id)}
    {#if talks.length}
      <section class="flex flex-col gap-3" data-testid="room-section">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="text-lg font-bold">{room.name}</h2>
          <a class="btn-ghost !min-h-9" href={stageUrl(eventId, room.id)} target="_blank" rel="noopener" data-testid="stage-link">
            <Icon name="monitor" size={16} />{t('admin.stage')}
          </a>
        </div>
        <ul class="flex flex-col gap-2">
          {#each talks as tk (tk.id)}
            <li class="card flex flex-col gap-3 !p-3 {tk.state === 'live' ? 'border-2 border-brand' : ''}" data-testid="admin-talk" data-code={tk.id}>
              <div class="flex flex-wrap items-start gap-3">
                <span class="w-12 shrink-0 pt-0.5 font-mono text-sm text-muted">{formatTime(tk.start)}</span>
                <div class="min-w-0 flex-1">
                  <p class="font-semibold leading-snug">{tk.title}</p>
                  <p class="text-sm text-muted">{tk.speakers}</p>
                </div>
                <button class="chip font-mono tracking-widest hover:bg-line" onclick={async () => toast((await copy(tk.id)) ? t('common.copied') : tk.id)} title={t('admin.talkCode')}>{tk.id}</button>
                {#if tk.state === 'live'}<Chip tone="live">● {t('talk.state.live')}</Chip>
                {:else if tk.state === 'ended'}<Chip>{t('talk.state.ended')}</Chip>{/if}
                {#if tk.questionsClosed}<Chip tone="warn">{t('admin.questionsClosed')}</Chip>{/if}
              </div>
              <div class="flex flex-wrap gap-2">
                {#if tk.state !== 'live'}
                  <button class="btn-primary !min-h-10" onclick={() => goLive(tk)} disabled={busy !== null} data-testid="go-live"><Icon name="play" size={16} />{t('admin.goLive')}</button>
                {:else}
                  <button class="btn-ghost !min-h-10" onclick={() => end(tk)} disabled={busy !== null} data-testid="end-talk"><Icon name="square" size={16} />{t('admin.endTalk')}</button>
                {/if}
                <a class="btn-ghost !min-h-10" href="{base}/admin/e/{eventId}/live/{tk.id}" data-testid="open-queue"><Icon name="message" size={16} />{t('admin.queue')}</a>
                {#if tk.state !== 'scheduled'}
                  <button class="btn-ghost !min-h-10" onclick={() => toggleClosed(tk)} disabled={busy !== null}>
                    <Icon name={tk.questionsClosed ? 'unlock' : 'lock'} size={16} />{tk.questionsClosed ? t('admin.reopenQuestions') : t('admin.closeQuestions')}
                  </button>
                {/if}
                <button class="btn-ghost !min-h-10" onclick={() => (qrFor = qrFor === tk.id ? null : tk.id)}><Icon name="qr" size={16} />QR</button>
                <button class="btn-ghost !min-h-10" onclick={() => copySpeaker(tk)} disabled={busy !== null} data-testid="speaker-link"><Icon name="copy" size={16} />{t('admin.speakerLink')}</button>
              </div>
              {#if qrFor === tk.id}
                <div class="flex flex-wrap items-center gap-4">
                  <Qr value={talkUrl(eventId, tk.id)} label="QR {tk.title}" size={200} />
                  <p class="break-all text-sm text-muted">{talkUrl(eventId, tk.id)}</p>
                </div>
              {/if}
            </li>
          {/each}
        </ul>
      </section>
    {/if}
  {/each}

  {#if orphans.length}
    <section class="card text-sm text-warn">{t('admin.orphans', { n: orphans.length })}</section>
  {/if}
</main>
