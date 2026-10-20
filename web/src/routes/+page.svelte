<script lang="ts">
  import { goto } from '$app/navigation';
  import { base } from '#lib/paths';
  import { onMount } from 'svelte';
  import LangSwitch from '#lib/components/LangSwitch.svelte';
  import { DEFAULT_EVENT } from '#lib/backend/firebase';
  import { t } from '#lib/i18n/index.svelte';

  let slug = $state('');
  let redirecting = $state(DEFAULT_EVENT !== '');

  onMount(() => {
    if (DEFAULT_EVENT) goto(`${base}/e/${DEFAULT_EVENT}`, { replace: true });
  });

  function go(e: Event) {
    e.preventDefault();
    const s = slug.trim().toLowerCase();
    if (s) goto(`${base}/e/${s}`);
  }
</script>

{#if !redirecting}
  <main class="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-6 px-4 py-10">
    <div class="flex items-center justify-between">
      <h1 class="text-3xl font-bold text-brand-text">handsup</h1>
      <LangSwitch />
    </div>
    <p class="text-muted">{t('home.tagline')}</p>
    <form class="card flex flex-col gap-3" onsubmit={go}>
      <label class="label" for="slug">{t('home.eventLabel')}</label>
      <input id="slug" class="input" bind:value={slug} placeholder="fresonfest-2026" autocapitalize="none" autocomplete="off" />
      <button class="btn-primary" type="submit" disabled={!slug.trim()}>{t('home.go')}</button>
    </form>
    <a class="text-center text-sm text-muted underline" href="{base}/admin">{t('home.admin')}</a>
  </main>
{/if}
