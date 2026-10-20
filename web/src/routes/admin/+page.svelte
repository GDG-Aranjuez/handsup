<script lang="ts">
  import { goto } from '$app/navigation';
  import { base } from '#lib/paths';
  import { onMount } from 'svelte';
  import { backend } from '#lib/backend';
  import { DEFAULT_BRANDING, DEFAULT_SETTINGS, type EventDoc } from '#lib/domain/types';
  import { t } from '#lib/i18n/index.svelte';
  import { toast } from '#lib/toast.svelte';

  let events = $state<(EventDoc & { manage: boolean })[]>([]);
  let superadmin = $state(false);
  let loaded = $state(false);
  let slug = $state('');
  let name = $state('');

  onMount(async () => {
    const b = backend();
    superadmin = await b.isSuperadmin();
    const all = await b.listEvents();
    const checked = await Promise.all(all.map(async (e) => ({ ...e, manage: await b.isOrganizer(e.id) })));
    events = checked.filter((e) => e.manage);
    loaded = true;
  });

  const slugOk = $derived(/^[a-z0-9-]{3,40}$/.test(slug));

  async function create(e: Event) {
    e.preventDefault();
    if (!slugOk || !name.trim()) return;
    try {
      await backend().createEvent({
        id: slug,
        name: name.trim(),
        branding: { ...DEFAULT_BRANDING },
        settings: { ...DEFAULT_SETTINGS },
      });
      goto(`${base}/admin/e/${slug}/setup`);
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
</script>

<main class="mx-auto flex max-w-3xl flex-col gap-6 px-4 py-8">
  <h1 class="text-2xl font-bold">{t('admin.events')}</h1>

  {#if loaded && events.length === 0}
    <p class="card text-muted" data-testid="no-events">{t('admin.noEvents')}</p>
  {/if}

  <ul class="flex flex-col gap-2">
    {#each events as e (e.id)}
      <li>
        <a class="card flex items-center gap-3 hover:bg-surface-2" href="{base}/admin/e/{e.id}" data-testid="admin-event">
          <span class="grid h-10 w-10 place-items-center rounded-lg text-lg font-bold" style="background:{e.branding.primary};color:#fff">✋</span>
          <span class="flex-1"><span class="block font-semibold">{e.name}</span><span class="text-sm text-muted">{e.id}</span></span>
        </a>
      </li>
    {/each}
  </ul>

  {#if superadmin}
    <form class="card flex flex-col gap-3" onsubmit={create} data-testid="create-event">
      <h2 class="font-semibold">{t('admin.createEvent')}</h2>
      <div>
        <label class="label" for="ev-name">{t('admin.eventName')}</label>
        <input id="ev-name" class="input" bind:value={name} maxlength="80" />
      </div>
      <div>
        <label class="label" for="ev-slug">{t('admin.eventSlug')}</label>
        <input id="ev-slug" class="input font-mono" bind:value={slug} placeholder="fresonfest-2027" autocapitalize="none" autocomplete="off" />
        {#if slug && !slugOk}<p class="mt-1 text-xs text-[#c0262d]">{t('admin.slugHelp')}</p>{/if}
      </div>
      <button class="btn-primary" type="submit" disabled={!slugOk || !name.trim()}>{t('admin.create')}</button>
    </form>
  {/if}
</main>
