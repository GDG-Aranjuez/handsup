<script lang="ts">
  import { base } from '#lib/paths';
  import { page } from '$app/state';
  import { onMount } from 'svelte';
  import { BackendError, backend } from '#lib/backend';
  import Chip from '#lib/components/Chip.svelte';
  import Icon from '#lib/components/Icon.svelte';
  import QuestionCard from '#lib/components/QuestionCard.svelte';
  import { buildQueue, sortQuestions } from '#lib/domain/ordering';
  import {
    MAX_QUESTION_LENGTH,
    type Attendee,
    type Delivery,
    type Question,
    type SortMode,
  } from '#lib/domain/types';
  import { getEventContext, windowOpen } from '#lib/eventContext.svelte';
  import { formatTime } from '#lib/format';
  import { t } from '#lib/i18n/index.svelte';
  import { requireUser, session } from '#lib/state.svelte';
  import { toast } from '#lib/toast.svelte';

  const ctx = getEventContext();
  const eventId = $derived(page.params.event ?? '');
  const code = $derived((page.params.code ?? '').toUpperCase());
  const talk = $derived(ctx.talk(code));
  const settings = $derived(ctx.event?.settings);

  let me = $state<Attendee | null>(null);
  let joinError = $state(false);
  let publicQs = $state<Question[]>([]);
  let mineQs = $state<Question[]>([]);
  let myVotes = $state<Set<string>>(new Set());
  let listError = $state(false);
  let now = $state(Date.now());
  let tab = $state<'all' | 'mine'>('all');
  let sortChoice = $state<SortMode | null>(null);
  let draft = $state('');
  let delivery = $state<Delivery>('read');
  let cooldownUntil = $state(0);
  let sending = $state(0);
  let busy = $state<Set<string>>(new Set());
  let editingName = $state(false);
  let nameDraft = $state('');

  const sortMode = $derived(sortChoice ?? settings?.defaultSort ?? 'votes');
  const open = $derived(talk && settings ? windowOpen(talk, settings.graceMin, now) : false);
  const cooldown = $derived(Math.max(0, Math.ceil((cooldownUntil - now) / 1000)));
  const queue = $derived(buildQueue(publicQs, sortMode));
  const mineSorted = $derived(sortQuestions(mineQs, 'time').reverse());
  const mineIds = $derived(new Set(mineQs.map((q) => q.id)));
  const canSend = $derived(open && !!me && draft.trim().length > 0 && draft.length <= MAX_QUESTION_LENGTH && cooldown === 0);

  onMount(() => {
    const tick = setInterval(() => (now = Date.now()), 1000);
    return () => clearInterval(tick);
  });

  $effect(() => {
    const ev = eventId;
    const c = code;
    let cancelled = false;
    let unsubs: Array<() => void> = [];
    me = null;
    joinError = false;
    publicQs = [];
    mineQs = [];
    myVotes = new Set();
    (async () => {
      try {
        const user = await requireUser();
        if (cancelled) return;
        const b = backend();
        me = await b.joinEvent(ev, user.uid);
        if (cancelled) return;
        unsubs = [
          b.watchQuestions(ev, c, 'public', user.uid, (q) => ((publicQs = q), (listError = false)), () => (listError = true)),
          b.watchQuestions(ev, c, 'mine', user.uid, (q) => (mineQs = q)),
          b.watchMyVotes(ev, c, user.uid, (ids) => (myVotes = ids)),
        ];
      } catch (e) {
        console.error('handsup: join failed', (e as { code?: string }).code, (e as Error).message);
        joinError = true;
      }
    })();
    return () => {
      cancelled = true;
      unsubs.forEach((u) => u());
    };
  });

  function errorText(e: unknown): string {
    if (e instanceof BackendError && e.code === 'denied') return t('err.denied');
    return t('err.generic');
  }

  async function send(e: Event) {
    e.preventDefault();
    if (!canSend || !me || !settings) return;
    const text = draft.trim();
    const d = delivery;
    cooldownUntil = Date.now() + 31_000; // server rule is 30 s; the extra second absorbs clock skew
    draft = '';
    sending++;
    try {
      await backend().ask({
        eventId,
        talkId: code,
        uid: me.uid,
        pseudonym: me.pseudonym,
        text,
        delivery: d,
        preApproval: settings.preApproval,
      });
      toast(settings.preApproval ? t('talk.sentPending') : t('talk.sent'));
    } catch (err) {
      cooldownUntil = 0;
      draft = text;
      toast(errorText(err), 'error');
    } finally {
      sending--;
    }
  }

  async function guarded(id: string, fn: () => Promise<void>) {
    if (busy.has(id)) return;
    busy = new Set(busy).add(id);
    try {
      await fn();
    } catch (err) {
      toast(errorText(err), 'error');
    } finally {
      const next = new Set(busy);
      next.delete(id);
      busy = next;
    }
  }

  const toggleVote = (q: Question) =>
    me &&
    guarded(q.id, () =>
      myVotes.has(q.id) ? backend().unvote(eventId, code, q.id, me!.uid) : backend().vote(eventId, code, q.id, me!.uid),
    );
  const withdraw = (q: Question) => guarded(q.id, () => backend().withdrawQuestion(eventId, code, q.id));
  const edit = (q: Question, patch: { text?: string; delivery?: Delivery }) =>
    guarded(q.id, () => backend().editQuestion(eventId, code, q.id, patch));

  async function reroll() {
    if (!me) return;
    try {
      me = { ...me, pseudonym: await backend().changePseudonym(eventId, me.uid) };
    } catch (err) {
      toast(errorText(err), 'error');
    }
  }
  async function saveName(e: Event) {
    e.preventDefault();
    if (!me || nameDraft.trim().length < 2) return;
    try {
      me = { ...me, pseudonym: await backend().changePseudonym(eventId, me.uid, nameDraft) };
      editingName = false;
    } catch (err) {
      toast(err instanceof BackendError && err.code === 'denied' ? t('talk.nameTaken') : errorText(err), 'error');
    }
  }
  async function linkGoogle() {
    try {
      await backend().linkGoogle();
      toast(t('talk.linked'));
    } catch (err) {
      toast(errorText(err), 'error');
    }
  }

  const canVote = (q: Question) => open && settings?.voting === true && (q.status === 'open' || q.status === 'onstage');
  const lastAnswered = $derived([...queue.followup, ...queue.answered]);
  const roomName = $derived(talk ? (ctx.room(talk.roomId)?.name ?? '') : '');
</script>

<svelte:head>
  <title>{talk ? `${talk.title} · ${ctx.event?.name ?? ''}` : (ctx.event?.name ?? 'handsup')}</title>
</svelte:head>

<main class="mx-auto flex max-w-2xl flex-col gap-4 px-4 py-4">
  <a href="{base}/e/{eventId}?browse=1" class="inline-flex w-fit items-center gap-1 text-sm text-muted hover:text-ink">
    <Icon name="back" size={16} />{t('talk.allTalks')}
  </a>

  {#if ctx.loaded && ctx.event && !talk}
    <div class="card text-center" data-testid="talk-not-found">
      <p class="font-semibold">{t('talk.notFound')}</p>
      <a class="btn-ghost mt-4" href="{base}/e/{eventId}">{t('common.back')}</a>
    </div>
  {:else if talk}
    <section class="flex flex-col gap-1">
      <div class="flex items-center gap-2">
        {#if talk.state === 'live'}<Chip tone="live">● {t('talk.state.live')}</Chip>
        {:else if talk.state === 'ended'}<Chip>{t('talk.state.ended')}</Chip>
        {:else}<Chip>{t('talk.state.scheduled')} · {formatTime(talk.start)}</Chip>{/if}
        {#if ctx.rooms.length > 1 && roomName}<span class="text-sm text-muted">{roomName}</span>{/if}
      </div>
      <h1 class="text-2xl font-bold leading-tight" data-testid="talk-title">{talk.title}</h1>
      {#if talk.speakers}<p class="text-muted">{talk.speakers}</p>{/if}
    </section>

    {#if joinError}
      <div class="card text-center" role="alert">
        <p>{t('err.join')}</p>
        <button class="btn-ghost mt-3" onclick={() => location.reload()}>{t('common.retry')}</button>
      </div>
    {/if}

    {#if me}
      <div class="flex flex-wrap items-center gap-2 rounded-xl bg-surface-2 px-3 py-2 text-sm" data-testid="pseudonym-bar">
        {#if editingName}
          <form class="flex flex-1 gap-2" onsubmit={saveName}>
            <input class="input !min-h-9" bind:value={nameDraft} maxlength="30" aria-label={t('talk.customName')} />
            <button class="btn-primary !min-h-9" type="submit">{t('common.save')}</button>
            <button class="btn-ghost !min-h-9" type="button" onclick={() => (editingName = false)} aria-label={t('common.cancel')}>
              <Icon name="x" size={16} />
            </button>
          </form>
        {:else}
          <span class="text-muted">{t('talk.askingAs')}</span>
          <strong data-testid="my-pseudonym">{me.pseudonym}</strong>
          <button class="chip ml-auto hover:bg-line" onclick={reroll} data-testid="reroll"><Icon name="dice" size={14} />{t('talk.reroll')}</button>
          <button
            class="chip hover:bg-line"
            onclick={() => ((nameDraft = me!.pseudonym), (editingName = true))}
            aria-label={t('talk.customName')}
          >
            <Icon name="pencil" size={14} />
          </button>
        {/if}
      </div>
    {/if}

    <form class="card flex flex-col gap-3" onsubmit={send} data-testid="composer">
      {#if talk.state === 'scheduled'}
        <p class="text-muted" data-testid="composer-closed">{t('talk.notStarted')}</p>
      {:else if !open}
        <p class="text-muted" data-testid="composer-closed">{t('talk.closed')}</p>
      {:else}
        <label class="sr-only" for="q-text">{t('talk.placeholder')}</label>
        <textarea
          id="q-text"
          class="input !min-h-24 py-2 text-[17px]"
          bind:value={draft}
          placeholder={t('talk.placeholder')}
          maxlength={MAX_QUESTION_LENGTH}
          disabled={!me}
        ></textarea>
        <div class="flex items-center justify-between text-xs text-muted">
          <span id="delivery-label">{t('delivery.label')}</span>
          <span class={draft.length > MAX_QUESTION_LENGTH - 20 ? 'text-warn' : ''} data-testid="counter">
            {draft.length}/{MAX_QUESTION_LENGTH}
          </span>
        </div>
        <div class="grid grid-cols-2 gap-2" role="radiogroup" aria-labelledby="delivery-label">
          {#each ['read', 'speak'] as const as d}
            <button
              type="button"
              role="radio"
              aria-checked={delivery === d}
              class="flex min-h-14 flex-col items-center justify-center gap-0.5 rounded-xl border-2 px-2 py-2 text-sm font-semibold transition-colors {delivery === d
                ? 'border-brand bg-brand/10 text-ink'
                : 'border-line text-muted hover:bg-surface-2'}"
              onclick={() => (delivery = d)}
              data-testid="delivery-choice-{d}"
            >
              <span class="flex items-center gap-1.5"><Icon name={d === 'speak' ? 'mic' : 'volume'} size={18} />{t(`delivery.${d}.short`)}</span>
              <span class="text-xs font-normal text-muted">{t(`delivery.${d}.hint`)}</span>
            </button>
          {/each}
        </div>
        <button class="btn-primary w-full" type="submit" disabled={!canSend} data-testid="send">
          {#if sending > 0}{t('talk.sending')}
          {:else if cooldown > 0}{t('talk.wait', { n: cooldown })}
          {:else}{t('talk.send')}{/if}
        </button>
      {/if}
    </form>

    <div class="flex flex-col gap-2">
      <div class="flex items-center gap-2" role="tablist">
        <button role="tab" aria-selected={tab === 'all'} class="chip {tab === 'all' ? '!bg-ink !text-surface' : ''}" onclick={() => (tab = 'all')} data-testid="tab-all">
          {t('talk.tabAll')} · {publicQs.length}
        </button>
        <button role="tab" aria-selected={tab === 'mine'} class="chip {tab === 'mine' ? '!bg-ink !text-surface' : ''}" onclick={() => (tab = 'mine')} data-testid="tab-mine">
          {t('talk.tabMine')} · {mineQs.length}
        </button>
      </div>
      {#if tab === 'all'}
        <div class="flex gap-1" role="group" aria-label={t('talk.sort')}>
          <button class="chip {sortMode === 'votes' ? '!bg-brand !text-on-brand' : ''}" onclick={() => (sortChoice = 'votes')}>{t('talk.sortVotes')}</button>
          <button class="chip {sortMode === 'time' ? '!bg-brand !text-on-brand' : ''}" onclick={() => (sortChoice = 'time')}>{t('talk.sortTime')}</button>
        </div>
      {/if}
    </div>

    {#if listError}<p class="text-sm text-[#c0262d]" role="alert">{t('err.list')}</p>{/if}

    {#if tab === 'all'}
      <section class="flex flex-col gap-3" data-testid="question-list">
        {#each queue.onStage as q (q.id)}
          <QuestionCard {q} mine={mineIds.has(q.id)} voted={myVotes.has(q.id)} canVote={canVote(q)} onvote={() => toggleVote(q)} />
        {/each}
        {#each queue.open as q (q.id)}
          <QuestionCard
            {q}
            mine={mineIds.has(q.id)}
            voted={myVotes.has(q.id)}
            canVote={canVote(q)}
            onvote={() => toggleVote(q)}
            onwithdraw={() => withdraw(q)}
            onedit={(p) => edit(q, p)}
          />
        {/each}
        {#if publicQs.length === 0}
          <p class="py-8 text-center text-muted" data-testid="empty">{t('talk.empty')}</p>
        {/if}
        {#if lastAnswered.length}
          <details class="mt-2">
            <summary class="cursor-pointer py-2 text-sm font-semibold text-muted">{t('talk.answeredSection', { n: lastAnswered.length })}</summary>
            <div class="mt-2 flex flex-col gap-3">
              {#each lastAnswered as q (q.id)}
                <QuestionCard {q} mine={mineIds.has(q.id)} voted={myVotes.has(q.id)} canVote={canVote(q)} onvote={() => toggleVote(q)} />
              {/each}
            </div>
          </details>
        {/if}
      </section>
    {:else}
      <section class="flex flex-col gap-3" data-testid="my-list">
        {#each mineSorted as q (q.id)}
          <QuestionCard
            {q}
            mine
            showStatus
            voted={myVotes.has(q.id)}
            canVote={canVote(q)}
            onvote={() => toggleVote(q)}
            onwithdraw={() => withdraw(q)}
            onedit={(p) => edit(q, p)}
          />
        {/each}
        {#if mineQs.length === 0}<p class="py-8 text-center text-muted">{t('talk.emptyMine')}</p>{/if}
      </section>
    {/if}

    <footer class="mt-4 flex flex-col gap-3 border-t border-line pt-4 text-xs text-muted">
      <p>{t('privacy.note')}</p>
      {#if session.user?.isAnonymous}
        <button class="btn-ghost w-fit !min-h-9 text-sm" onclick={linkGoogle}>{t('talk.linkGoogle')}</button>
      {/if}
    </footer>
  {:else}
    <div class="card animate-pulse py-10 text-center text-muted">{t('common.loading')}</div>
  {/if}
</main>
