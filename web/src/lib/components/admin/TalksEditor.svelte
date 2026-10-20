<script lang="ts">
  import { backend } from '#lib/backend';
  import Icon from '#lib/components/Icon.svelte';
  import { isValidTalkCode, normalizeTalkCode, uniqueTalkCode } from '#lib/domain/talkCode';
  import type { Talk } from '#lib/domain/types';
  import { getEventContext } from '#lib/eventContext.svelte';
  import { formatTime } from '#lib/format';
  import { t } from '#lib/i18n/index.svelte';
  import { toast } from '#lib/toast.svelte';

  let { eventId }: { eventId: string } = $props();
  const ctx = getEventContext();

  const pad = (n: number) => String(n).padStart(2, '0');
  const toInput = (d: Date) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;

  let editing = $state<string | null>(null); // talk code, or 'new'
  let code = $state('');
  let title = $state('');
  let speakers = $state('');
  let roomId = $state('');
  let start = $state('');
  let end = $state('');
  let confirmDelete = $state<string | null>(null);
  let saving = $state(false);

  function startNew() {
    const last = ctx.talks.at(-1);
    const base = last ? new Date(last.end.getTime() + 10 * 60_000) : new Date();
    base.setSeconds(0, 0);
    editing = 'new';
    code = uniqueTalkCode(new Set(ctx.talks.map((x) => x.id)));
    title = '';
    speakers = '';
    roomId = ctx.rooms[0]?.id ?? '';
    start = toInput(base);
    end = toInput(new Date(base.getTime() + 45 * 60_000));
  }
  function startEdit(tk: Talk) {
    editing = tk.id;
    code = tk.id;
    title = tk.title;
    speakers = tk.speakers;
    roomId = tk.roomId;
    start = toInput(tk.start);
    end = toInput(tk.end);
  }

  const valid = $derived(
    isValidTalkCode(code) &&
      title.trim().length > 0 &&
      !!roomId &&
      !!start &&
      !!end &&
      new Date(end) > new Date(start) &&
      (editing !== 'new' || !ctx.talk(code)),
  );

  async function save(e: Event) {
    e.preventDefault();
    if (!valid) return;
    saving = true;
    const existing = ctx.talk(code);
    try {
      await backend().saveTalk(eventId, {
        id: code,
        title: title.trim(),
        speakers: speakers.trim(),
        roomId,
        start: new Date(start),
        end: new Date(end),
        state: existing?.state ?? 'scheduled',
        questionsClosed: existing?.questionsClosed ?? false,
        endedAt: existing?.endedAt,
      });
      editing = null;
    } catch {
      toast(t('err.denied'), 'error');
    } finally {
      saving = false;
    }
  }
  async function remove(id: string) {
    if (confirmDelete !== id) {
      confirmDelete = id;
      return;
    }
    confirmDelete = null;
    try {
      await backend().deleteTalk(eventId, id);
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
</script>

<section class="flex flex-col gap-3" data-testid="talks-editor">
  <div class="flex items-center">
    <h2 class="text-lg font-bold">{t('setup.talks')}</h2>
    <button class="btn-primary ml-auto !min-h-10" onclick={startNew} disabled={ctx.rooms.length === 0} data-testid="new-talk"><Icon name="plus" size={16} />{t('setup.addTalk')}</button>
  </div>
  {#if ctx.rooms.length === 0}<p class="text-sm text-warn">{t('setup.needRoom')}</p>{/if}

  {#if editing}
    <form class="card flex flex-col gap-3" onsubmit={save} data-testid="talk-form">
      <div class="grid gap-3 sm:grid-cols-2">
        <div class="sm:col-span-2">
          <label class="label" for="tk-title">{t('setup.talkTitle')}</label>
          <input id="tk-title" class="input" bind:value={title} maxlength="160" />
        </div>
        <div>
          <label class="label" for="tk-speakers">{t('setup.speakers')}</label>
          <input id="tk-speakers" class="input" bind:value={speakers} maxlength="200" />
        </div>
        <div>
          <label class="label" for="tk-room">{t('setup.room')}</label>
          <select id="tk-room" class="input" bind:value={roomId}>
            {#each ctx.rooms as r}<option value={r.id}>{r.name}</option>{/each}
          </select>
        </div>
        <div>
          <label class="label" for="tk-start">{t('setup.start')}</label>
          <input id="tk-start" class="input" type="datetime-local" bind:value={start} />
        </div>
        <div>
          <label class="label" for="tk-end">{t('setup.end')}</label>
          <input id="tk-end" class="input" type="datetime-local" bind:value={end} />
        </div>
        <div>
          <label class="label" for="tk-code">{t('admin.talkCode')}</label>
          <input
            id="tk-code"
            class="input font-mono uppercase tracking-widest"
            bind:value={code}
            oninput={() => (code = normalizeTalkCode(code))}
            readonly={editing !== 'new'}
            maxlength="5"
          />
        </div>
      </div>
      <div class="flex gap-2">
        <button class="btn-primary" type="submit" disabled={!valid || saving} data-testid="save-talk">{t('common.save')}</button>
        <button class="btn-ghost" type="button" onclick={() => (editing = null)}>{t('common.cancel')}</button>
      </div>
    </form>
  {/if}

  <ul class="flex flex-col gap-2">
    {#each ctx.talks as tk (tk.id)}
      <li class="card flex items-center gap-3 !p-3" data-testid="setup-talk">
        <span class="w-12 shrink-0 font-mono text-sm text-muted">{formatTime(tk.start)}</span>
        <span class="min-w-0 flex-1">
          <span class="block truncate font-medium">{tk.title}</span>
          <span class="block truncate text-sm text-muted">{tk.speakers} · {ctx.room(tk.roomId)?.name ?? '—'}</span>
        </span>
        <span class="font-mono text-sm tracking-widest text-muted">{tk.id}</span>
        <button class="btn-ghost !min-h-9 !px-3" onclick={() => startEdit(tk)} aria-label={t('common.edit')}><Icon name="pencil" size={16} /></button>
        <button class="btn-danger !min-h-9 !px-3" onclick={() => remove(tk.id)} aria-label={t('common.delete')}>
          {#if confirmDelete === tk.id}{t('common.sure')}{:else}<Icon name="trash" size={16} />{/if}
        </button>
      </li>
    {/each}
  </ul>
</section>
