<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import Toasts from '#lib/components/Toasts.svelte';
  import { initLocale, t } from '#lib/i18n/index.svelte';
  import { session, startSession } from '#lib/state.svelte';

  let { children } = $props();

  initLocale();
  onMount(() => startSession());
</script>

<svelte:head>
  <title>handsup</title>
</svelte:head>

{#if !session.online}
  <div class="bg-warn/15 px-4 py-2 text-center text-sm font-medium text-warn" role="status" data-testid="offline-banner">
    {t('offline.banner')}
  </div>
{/if}

{@render children()}

<Toasts />
