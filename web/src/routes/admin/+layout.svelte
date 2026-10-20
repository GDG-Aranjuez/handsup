<script lang="ts">
  import { base } from '#lib/paths';
  import { backend } from '#lib/backend';
  import { USE_EMULATOR } from '#lib/backend/firebase';
  import Icon from '#lib/components/Icon.svelte';
  import LangSwitch from '#lib/components/LangSwitch.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { session } from '#lib/state.svelte';
  import { toast } from '#lib/toast.svelte';

  let { children } = $props();

  let email = $state('organizer@handsup.test');
  let password = $state('handsup-test');
  let busy = $state(false);

  const signedIn = $derived(!!session.user && !session.user.isAnonymous);

  async function google() {
    busy = true;
    try {
      await backend().signInGoogle();
    } catch {
      toast(t('admin.signInFailed'), 'error');
    } finally {
      busy = false;
    }
  }
  async function dev(e: Event) {
    e.preventDefault();
    busy = true;
    try {
      await backend().signInEmail(email, password);
    } catch {
      toast(t('admin.signInFailed'), 'error');
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head><title>handsup · {t('admin.title')}</title></svelte:head>

<header class="sticky top-0 z-20 border-b border-line bg-surface/90 backdrop-blur">
  <div class="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
    <a href="{base}/admin" class="text-lg font-bold">handsup <span class="text-sm font-medium text-muted">{t('admin.title')}</span></a>
    <div class="ml-auto flex items-center gap-2">
      {#if signedIn}
        <span class="hidden text-sm text-muted sm:inline" data-testid="admin-email">{session.user?.email}</span>
        <button class="btn-ghost !min-h-9 !px-3" onclick={() => backend().signOut()} aria-label={t('admin.signOut')}>
          <Icon name="logout" size={16} />
        </button>
      {/if}
      <LangSwitch />
    </div>
  </div>
</header>

{#if !session.ready}
  <p class="p-10 text-center text-muted">{t('common.loading')}</p>
{:else if !signedIn}
  <main class="mx-auto flex max-w-sm flex-col gap-4 px-4 py-16">
    <h1 class="text-2xl font-bold">{t('admin.signInTitle')}</h1>
    <p class="text-muted">{t('admin.signInHelp')}</p>
    <button class="btn-primary" onclick={google} disabled={busy} data-testid="google-signin">{t('admin.google')}</button>
    {#if USE_EMULATOR}
      <form class="card flex flex-col gap-2" onsubmit={dev} data-testid="dev-login">
        <p class="text-xs font-semibold uppercase text-muted">Emulator</p>
        <input class="input" type="email" bind:value={email} aria-label="email" />
        <input class="input" type="password" bind:value={password} aria-label="password" />
        <button class="btn-ghost" type="submit" disabled={busy}>{t('admin.devSignIn')}</button>
      </form>
    {/if}
  </main>
{:else}
  {@render children()}
{/if}
