<script lang="ts">
  import { page } from '$app/state';
  import { base } from '#lib/paths';
  import { onMount } from 'svelte';
  import { backend } from '#lib/backend';
  import Icon from '#lib/components/Icon.svelte';
  import { DEFAULT_BRANDING } from '#lib/domain/types';
  import { EventContext, setEventContext } from '#lib/eventContext.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { applyBranding } from '#lib/state.svelte';

  let { children } = $props();
  const eventId = $derived(page.params.event ?? '');
  const ctx = new EventContext(page.params.event ?? '');
  setEventContext(ctx);

  let allowed = $state<boolean | null>(null);

  $effect(() => {
    const id = eventId;
    ctx.reset(id);
    allowed = null;
    let cancelled = false;
    backend()
      .isOrganizer(id)
      .then((ok) => {
        if (cancelled) return;
        allowed = ok;
        if (ok) ctx.start();
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

  const tabs = $derived([
    { href: `${base}/admin/e/${eventId}`, label: t('admin.tab.live'), exact: true },
    { href: `${base}/admin/e/${eventId}/setup`, label: t('admin.tab.setup'), exact: false },
  ]);
  const active = (h: string, exact: boolean) => (exact ? page.url.pathname === h : page.url.pathname.startsWith(h));
</script>

{#if allowed === null}
  <p class="p-10 text-center text-muted">{t('common.loading')}</p>
{:else if allowed === false}
  <main class="mx-auto max-w-md px-4 py-16 text-center" data-testid="no-access">
    <p class="text-lg font-semibold">{t('admin.noAccess')}</p>
    <a class="btn-ghost mt-6" href="{base}/admin">{t('common.back')}</a>
  </main>
{:else}
  <div class="mx-auto max-w-5xl px-4 pt-4">
    <div class="flex items-center gap-3">
      <a href="{base}/admin" class="text-muted hover:text-ink" aria-label={t('common.back')}><Icon name="back" size={20} /></a>
      <h1 class="truncate text-xl font-bold" data-testid="admin-event-name">{ctx.event?.name ?? eventId}</h1>
    </div>
    <nav class="mt-3 flex gap-1 border-b border-line" aria-label="admin">
      {#each tabs as tab}
        <a
          href={tab.href}
          class="-mb-px border-b-2 px-4 py-2 text-sm font-semibold {active(tab.href, tab.exact) ? 'border-brand text-ink' : 'border-transparent text-muted hover:text-ink'}"
        >
          {tab.label}
        </a>
      {/each}
    </nav>
  </div>
  {@render children()}
{/if}
