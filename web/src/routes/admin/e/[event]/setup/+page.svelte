<script lang="ts">
  import { page } from '$app/state';
  import BrandingEditor from '#lib/components/admin/BrandingEditor.svelte';
  import DataTools from '#lib/components/admin/DataTools.svelte';
  import RoomsEditor from '#lib/components/admin/RoomsEditor.svelte';
  import SettingsEditor from '#lib/components/admin/SettingsEditor.svelte';
  import TalksEditor from '#lib/components/admin/TalksEditor.svelte';
  import TeamEditor from '#lib/components/admin/TeamEditor.svelte';
  import { t } from '#lib/i18n/index.svelte';

  const eventId = $derived(page.params.event ?? '');
  type Tab = 'talks' | 'rooms' | 'branding' | 'settings' | 'team' | 'data';
  const tabs: Tab[] = ['rooms', 'talks', 'branding', 'settings', 'team', 'data'];
  let tab = $state<Tab>('rooms');
</script>

<main class="mx-auto flex max-w-3xl flex-col gap-4 px-4 py-4">
  <div class="flex flex-wrap gap-1" role="tablist">
    {#each tabs as k}
      <button role="tab" aria-selected={tab === k} class="chip {tab === k ? '!bg-ink !text-surface' : ''}" onclick={() => (tab = k)} data-testid="setup-tab-{k}">
        {t(`setup.tab.${k}`)}
      </button>
    {/each}
  </div>

  {#if tab === 'talks'}<TalksEditor {eventId} />
  {:else if tab === 'rooms'}<RoomsEditor {eventId} />
  {:else if tab === 'branding'}<BrandingEditor {eventId} />
  {:else if tab === 'settings'}<SettingsEditor />
  {:else if tab === 'team'}<TeamEditor {eventId} />
  {:else}<DataTools {eventId} />{/if}
</main>
