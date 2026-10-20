<script lang="ts">
  import { onMount } from 'svelte';
  import { backend } from '#lib/backend';
  import Icon from '#lib/components/Icon.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { toast } from '#lib/toast.svelte';

  let { eventId }: { eventId: string } = $props();
  let emails = $state<string[]>([]);
  let superadmin = $state(false);
  let email = $state('');

  onMount(() => {
    backend().isSuperadmin().then((v) => (superadmin = v));
  });
  $effect(() => backend().watchOrganizers(eventId, (e) => (emails = e)));

  const ok = $derived(/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim()));

  async function add(e: Event) {
    e.preventDefault();
    if (!ok) return;
    try {
      await backend().addOrganizer(eventId, email);
      email = '';
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
  async function remove(e: string) {
    try {
      await backend().removeOrganizer(eventId, e);
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
</script>

<section class="flex flex-col gap-3" data-testid="team-editor">
  <h2 class="text-lg font-bold">{t('setup.team')}</h2>
  <p class="text-sm text-muted">{t('setup.teamHelp')}</p>
  <ul class="flex flex-col gap-2">
    {#each emails as e (e)}
      <li class="card flex items-center gap-3 !p-3" data-testid="organizer">
        <span class="flex-1 truncate">{e}</span>
        {#if superadmin}
          <button class="btn-danger !min-h-9 !px-3" onclick={() => remove(e)} aria-label={t('common.remove')}><Icon name="trash" size={16} /></button>
        {/if}
      </li>
    {/each}
  </ul>
  {#if superadmin}
    <form class="flex gap-2" onsubmit={add}>
      <input class="input" type="email" bind:value={email} placeholder="organizer@gmail.com" data-testid="organizer-email" />
      <button class="btn-primary" type="submit" disabled={!ok} data-testid="add-organizer"><Icon name="plus" size={16} />{t('common.add')}</button>
    </form>
  {:else}
    <p class="text-sm text-muted">{t('setup.teamSuperadminOnly')}</p>
  {/if}
</section>
