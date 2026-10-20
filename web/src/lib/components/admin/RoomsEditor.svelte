<script lang="ts">
  import { backend } from '#lib/backend';
  import Icon from '#lib/components/Icon.svelte';
  import { getEventContext } from '#lib/eventContext.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { toast } from '#lib/toast.svelte';

  let { eventId }: { eventId: string } = $props();
  const ctx = getEventContext();

  let newName = $state('');
  let confirmDelete = $state<string | null>(null);
  const slug = (s: string) =>
    s
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .slice(0, 30);

  async function add(e: Event) {
    e.preventDefault();
    const id = slug(newName);
    if (!id || ctx.room(id)) return;
    try {
      await backend().saveRoom(eventId, { id, name: newName.trim(), liveTalkId: null, order: ctx.rooms.length });
      newName = '';
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
  async function rename(id: string, name: string) {
    const room = ctx.room(id);
    if (!room || !name.trim() || name.trim() === room.name) return;
    try {
      await backend().saveRoom(eventId, { ...room, name: name.trim() });
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
  async function remove(id: string) {
    if (ctx.talks.some((tk) => tk.roomId === id)) {
      toast(t('setup.roomInUse'), 'error');
      return;
    }
    if (confirmDelete !== id) {
      confirmDelete = id;
      return;
    }
    confirmDelete = null;
    try {
      await backend().deleteRoom(eventId, id);
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
</script>

<section class="flex flex-col gap-3" data-testid="rooms-editor">
  <h2 class="text-lg font-bold">{t('setup.rooms')}</h2>
  <ul class="flex flex-col gap-2">
    {#each ctx.rooms as r (r.id)}
      <li class="card flex items-center gap-3 !p-3">
        <input class="input" value={r.name} maxlength="60" onchange={(e) => rename(r.id, e.currentTarget.value)} aria-label={t('setup.roomName')} />
        <code class="text-xs text-muted">{r.id}</code>
        <button class="btn-danger !min-h-9 !px-3" onclick={() => remove(r.id)} aria-label={t('common.delete')}>
          {#if confirmDelete === r.id}{t('common.sure')}{:else}<Icon name="trash" size={16} />{/if}
        </button>
      </li>
    {/each}
  </ul>
  <form class="flex gap-2" onsubmit={add}>
    <input class="input" bind:value={newName} placeholder={t('setup.roomName')} maxlength="60" data-testid="new-room-name" />
    <button class="btn-primary" type="submit" disabled={!slug(newName) || !!ctx.room(slug(newName))} data-testid="add-room"><Icon name="plus" size={16} />{t('common.add')}</button>
  </form>
</section>
