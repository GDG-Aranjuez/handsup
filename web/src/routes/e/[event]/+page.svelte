<script lang="ts">
  import { goto } from '$app/navigation';
  import { base } from '#lib/paths';
  import { page } from '$app/state';
  import Chip from '#lib/components/Chip.svelte';
  import Icon from '#lib/components/Icon.svelte';
  import { getEventContext } from '#lib/eventContext.svelte';
  import { formatTime } from '#lib/format';
  import { t } from '#lib/i18n/index.svelte';
  import { isValidTalkCode, normalizeTalkCode } from '#lib/domain/talkCode';

  const ctx = getEventContext();
  const eventId = $derived(page.params.event ?? '');
  const roomKey = $derived(`handsup.room.${eventId}`);

  let code = $state('');
  let codeError = $state(false);

  const live = $derived(ctx.liveTalks);
  const browsing = $derived(page.url.searchParams.has('browse'));
  let redirected = false;

  // Auto-open the live talk: the only one, or the one in the room this device chose before.
  $effect(() => {
    if (redirected || browsing || !ctx.loaded || live.length === 0) return;
    let target = live.length === 1 ? live[0] : undefined;
    if (!target) {
      try {
        const room = localStorage.getItem(roomKey);
        target = live.find((l) => l.roomId === room);
      } catch {
        /* storage unavailable */
      }
    }
    if (target) {
      redirected = true;
      goto(`${base}/e/${eventId}/t/${target.id}`, { replace: true });
    }
  });

  function open(e: Event) {
    e.preventDefault();
    const c = normalizeTalkCode(code);
    if (!isValidTalkCode(c) || !ctx.talk(c)) {
      codeError = true;
      return;
    }
    goto(`${base}/e/${eventId}/t/${c}`);
  }

  function pickRoom(roomId: string) {
    try {
      localStorage.setItem(roomKey, roomId);
    } catch {
      /* storage unavailable */
    }
  }

  const roomName = (id: string) => ctx.room(id)?.name ?? '';
</script>

<main class="mx-auto flex max-w-2xl flex-col gap-6 px-4 py-6">
  {#if ctx.event?.branding.tagline}
    <p class="text-muted">{ctx.event.branding.tagline}</p>
  {/if}

  <section aria-labelledby="live-h" class="flex flex-col gap-3">
    <h2 id="live-h" class="text-sm font-semibold uppercase tracking-wide text-muted">{t('event.liveNow')}</h2>
    {#if live.length === 0}
      <div class="card text-muted" data-testid="no-live">{t('event.noLive')}</div>
    {/if}
    {#each live as talk (talk.id)}
      <a
        href="{base}/e/{eventId}/t/{talk.id}"
        onclick={() => pickRoom(talk.roomId)}
        class="card flex flex-col gap-2 border-2 border-brand transition-colors hover:bg-surface-2"
        data-testid="live-talk"
      >
        <div class="flex items-center gap-2">
          <Chip tone="live">● {t('talk.state.live')}</Chip>
          {#if ctx.rooms.length > 1}<span class="text-sm text-muted">{roomName(talk.roomId)}</span>{/if}
        </div>
        <p class="text-lg font-semibold leading-snug">{talk.title}</p>
        {#if talk.speakers}<p class="text-muted">{talk.speakers}</p>{/if}
        <span class="btn-primary mt-1 w-full sm:w-auto">{t('event.ask')}</span>
      </a>
    {/each}
  </section>

  <form class="card flex flex-col gap-2" onsubmit={open}>
    <label class="label" for="code">{t('event.codeLabel')}</label>
    <div class="flex gap-2">
      <input
        id="code"
        class="input font-mono text-lg uppercase tracking-[0.3em]"
        bind:value={code}
        oninput={() => (codeError = false)}
        placeholder="ABCDE"
        maxlength="8"
        autocomplete="off"
        autocapitalize="characters"
        inputmode="text"
        aria-invalid={codeError}
      />
      <button class="btn-primary" type="submit" disabled={!code.trim()}>{t('home.go')}</button>
    </div>
    {#if codeError}<p class="text-sm text-[#c0262d]" role="alert">{t('event.codeInvalid')}</p>{/if}
  </form>

  <section aria-labelledby="agenda-h" class="flex flex-col gap-3">
    <h2 id="agenda-h" class="text-sm font-semibold uppercase tracking-wide text-muted">{t('event.agenda')}</h2>
    {#if ctx.loaded && ctx.talks.length === 0}
      <p class="text-muted">{t('event.noTalks')}</p>
    {/if}
    <ul class="flex flex-col gap-2">
      {#each ctx.talks as talk (talk.id)}
        <li>
          <a
            href="{base}/e/{eventId}/t/{talk.id}"
            onclick={() => pickRoom(talk.roomId)}
            class="card flex items-start gap-3 !p-3 hover:bg-surface-2"
            data-testid="agenda-item"
          >
            <span class="w-12 shrink-0 pt-0.5 font-mono text-sm text-muted">{formatTime(talk.start)}</span>
            <span class="min-w-0 flex-1">
              <span class="block font-medium leading-snug">{talk.title}</span>
              <span class="block text-sm text-muted">
                {[talk.speakers, ctx.rooms.length > 1 ? roomName(talk.roomId) : ''].filter(Boolean).join(' · ')}
              </span>
            </span>
            {#if talk.state === 'live'}
              <Chip tone="live">● {t('talk.state.live')}</Chip>
            {:else if talk.state === 'ended'}
              <Chip>{t('talk.state.ended')}</Chip>
            {/if}
            <Icon name="back" size={16} class="mt-1 rotate-180 text-muted" />
          </a>
        </li>
      {/each}
    </ul>
  </section>
</main>
