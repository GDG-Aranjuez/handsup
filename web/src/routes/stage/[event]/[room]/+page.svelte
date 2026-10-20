<script lang="ts">
  import { page } from '$app/state';
  import { backend } from '#lib/backend';
  import DeliveryBadge from '#lib/components/DeliveryBadge.svelte';
  import Qr from '#lib/components/Qr.svelte';
  import { DEFAULT_BRANDING, type Question } from '#lib/domain/types';
  import { EventContext } from '#lib/eventContext.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { talkUrl } from '#lib/links';
  import { applyBranding, requireUser } from '#lib/state.svelte';

  const ctx = new EventContext(page.params.event ?? '');
  let qs = $state<Question[]>([]);

  const room = $derived(ctx.rooms.find((r) => r.id === page.params.room) ?? (ctx.rooms.length === 1 ? ctx.rooms[0] : undefined));
  const talk = $derived(room?.liveTalkId ? ctx.talk(room.liveTalkId) : undefined);
  const onStage = $derived(talk ? qs.find((q) => q.status === 'onstage') : undefined);

  $effect(() => {
    ctx.reset(page.params.event ?? '');
    let cancelled = false;
    requireUser().then(() => !cancelled && ctx.start());
    return () => {
      cancelled = true;
      ctx.stop();
    };
  });

  $effect(() => {
    const id = talk?.id;
    qs = [];
    if (!id) return;
    return backend().watchQuestions(ctx.id, id, 'public', null, (q) => (qs = q));
  });

  $effect(() => {
    applyBranding(ctx.event?.branding ?? DEFAULT_BRANDING);
    return () => applyBranding(DEFAULT_BRANDING);
  });
</script>

<svelte:head><title>{ctx.event?.name ?? 'handsup'} · stage</title></svelte:head>

<main class="grid min-h-dvh grid-rows-[auto_1fr] bg-surface p-6 sm:p-10" data-testid="stage">
  <header class="flex items-center gap-4">
    {#if ctx.event?.branding.logo}<img src={ctx.event.branding.logo} alt="" class="h-14 w-14 object-contain" />{/if}
    <h1 class="text-2xl font-bold sm:text-3xl">{ctx.event?.name ?? ''}</h1>
  </header>

  {#if !talk}
    <div class="grid place-items-center text-center">
      <p class="text-3xl font-semibold text-muted sm:text-5xl" data-testid="stage-idle">{t('stage.idle')}</p>
    </div>
  {:else}
    <div class="grid items-center gap-8 lg:grid-cols-[1fr_auto]">
      <div class="flex flex-col gap-6">
        <p class="text-xl text-muted sm:text-2xl" data-testid="stage-title">{talk.title}</p>
        {#if onStage}
          <div class="flex flex-col gap-4" data-testid="stage-question">
            <div class="flex items-center gap-3 text-xl sm:text-2xl">
              <strong class="text-brand-text">{onStage.pseudonym}</strong>
              <DeliveryBadge delivery={onStage.delivery} />
            </div>
            <p class="text-4xl font-bold leading-tight break-words sm:text-6xl">{onStage.text}</p>
          </div>
        {:else}
          <p class="text-4xl font-bold leading-tight sm:text-6xl" data-testid="stage-ask">{t('stage.ask')}</p>
        {/if}
      </div>
      <div class="flex flex-col items-center gap-3 rounded-3xl border border-line p-6">
        <Qr value={talkUrl(ctx.id, talk.id)} label="QR" size={220} />
        <p class="text-sm text-muted">{t('stage.code')}</p>
        <p class="font-mono text-5xl font-bold tracking-[0.2em]" data-testid="stage-code">{talk.id}</p>
      </div>
    </div>
  {/if}
</main>
