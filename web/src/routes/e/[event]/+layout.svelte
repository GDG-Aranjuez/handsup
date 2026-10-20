<script lang="ts">
  import { page } from '$app/state';
  import { base } from '#lib/paths';
  import LangSwitch from '#lib/components/LangSwitch.svelte';
  import { EventContext, setEventContext } from '#lib/eventContext.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { applyBranding, requireUser } from '#lib/state.svelte';
  import { DEFAULT_BRANDING } from '#lib/domain/types';

  let { children } = $props();

  const ctx = new EventContext(page.params.event ?? '');
  setEventContext(ctx);

  $effect(() => {
    const id = page.params.event ?? '';
    ctx.reset(id);
    let cancelled = false;
    requireUser()
      .then(() => {
        if (!cancelled) ctx.start();
      })
      .catch((e) => {
        ctx.error = String(e?.message ?? e);
        ctx.loaded = true;
      });
    return () => {
      cancelled = true;
      ctx.stop();
    };
  });

  $effect(() => {
    applyBranding(ctx.event?.branding ?? DEFAULT_BRANDING);
    return () => applyBranding(DEFAULT_BRANDING);
  });
</script>

<svelte:head>
  <title>{ctx.event?.name ?? 'handsup'}</title>
</svelte:head>

<header class="sticky top-0 z-20 border-b border-line bg-surface/90 backdrop-blur">
  <div class="mx-auto flex max-w-2xl items-center gap-3 px-4 py-3">
    <a href="{base}/e/{page.params.event}" class="flex min-w-0 items-center gap-3" aria-label={ctx.event?.name ?? 'handsup'}>
      {#if ctx.event?.branding.logo}
        <img src={ctx.event.branding.logo} alt="" class="h-9 w-9 rounded-lg object-contain" />
      {:else}
        <span class="grid h-9 w-9 place-items-center rounded-lg bg-brand text-lg font-bold text-on-brand">✋</span>
      {/if}
      <span class="truncate text-lg font-bold">{ctx.event?.name ?? '…'}</span>
    </a>
    <div class="ml-auto"><LangSwitch /></div>
  </div>
</header>

{#if ctx.loaded && !ctx.event}
  <main class="mx-auto max-w-2xl px-4 py-16 text-center" data-testid="event-not-found">
    <p class="text-lg font-semibold">{t('event.notFound')}</p>
    <a class="btn-ghost mt-6" href="{base}/">{t('common.back')}</a>
  </main>
{:else}
  {@render children()}
{/if}
