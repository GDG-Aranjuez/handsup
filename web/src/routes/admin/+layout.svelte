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

  // The emulator seed users are prefilled so a dev can just press the button.
  let email = $state(USE_EMULATOR ? 'organizer@handsup.test' : '');
  let password = $state(USE_EMULATOR ? 'handsup-test' : '');
  let busy = $state(false);

  const signedIn = $derived(!!session.user && !session.user.isAnonymous);
  const needsVerification = $derived(signedIn && !session.user?.emailVerified);

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
  async function withEmail(e: Event) {
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
  async function register() {
    busy = true;
    try {
      await backend().registerEmail(email, password);
      toast(t('admin.verifySent'));
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
{:else if needsVerification}
  <main class="mx-auto max-w-sm px-4 py-16">
    <div class="card flex flex-col gap-3" data-testid="verify-notice">
      <p>{t('admin.verifyNeeded', { email: session.user?.email ?? '' })}</p>
      <button class="btn-ghost" onclick={() => backend().signOut()}>{t('admin.signOut')}</button>
    </div>
  </main>
{:else if !signedIn}
  <main class="mx-auto flex max-w-sm flex-col gap-4 px-4 py-16">
    <h1 class="text-2xl font-bold">{t('admin.signInTitle')}</h1>
    <p class="text-muted">{t('admin.signInHelp')}</p>
    <button class="btn-primary" onclick={google} disabled={busy} data-testid="google-signin">{t('admin.google')}</button>
    <form class="card flex flex-col gap-2" onsubmit={withEmail} data-testid="dev-login">
      <label class="label" for="adm-email">{t('admin.email')}</label>
      <input id="adm-email" class="input" type="email" autocomplete="username" bind:value={email} />
      <label class="label" for="adm-pass">{t('admin.password')}</label>
      <input id="adm-pass" class="input" type="password" autocomplete="current-password" bind:value={password} />
      <button class="btn-primary" type="submit" disabled={busy || !email || password.length < 6}>{t('admin.signIn')}</button>
      <button class="btn-ghost" type="button" onclick={register} disabled={busy || !email || password.length < 6}>{t('admin.register')}</button>
    </form>
  </main>
{:else}
  {@render children()}
{/if}
