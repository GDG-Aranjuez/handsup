<script lang="ts">
  import { base } from '#lib/paths';
  import { page } from '$app/state';
  import { backend } from '#lib/backend';
  import Chip from '#lib/components/Chip.svelte';
  import DeliveryBadge from '#lib/components/DeliveryBadge.svelte';
  import Icon from '#lib/components/Icon.svelte';
  import { buildQueue } from '#lib/domain/ordering';
  import type { Delivery, Question, QuestionStatus, SortMode } from '#lib/domain/types';
  import { getEventContext } from '#lib/eventContext.svelte';
  import { timeAgo } from '#lib/format';
  import { t } from '#lib/i18n/index.svelte';
  import { stageUrl } from '#lib/links';
  import { toast } from '#lib/toast.svelte';

  const ctx = getEventContext();
  const eventId = $derived(page.params.event ?? '');
  const code = $derived((page.params.code ?? '').toUpperCase());
  const talk = $derived(ctx.talk(code));

  type Lane = 'open' | 'pending' | 'followup' | 'answered' | 'hidden';
  let all = $state<Question[]>([]);
  let lane = $state<Lane>('open');
  let filter = $state<'all' | Delivery>('all');
  let sortChoice = $state<SortMode | null>(null);
  let busy = $state(false);

  const sortMode = $derived(sortChoice ?? ctx.event?.settings.defaultSort ?? 'votes');
  const filtered = $derived(all.filter((q) => filter === 'all' || q.delivery === filter));
  const queue = $derived(buildQueue(filtered, sortMode));
  const counts = $derived({
    open: queue.open.length,
    pending: queue.pending.length,
    followup: queue.followup.length,
    answered: queue.answered.length,
    hidden: queue.hidden.length,
  });
  const lanes = $derived<Lane[]>(
    queue.pending.length > 0 || ctx.event?.settings.preApproval
      ? ['open', 'pending', 'followup', 'answered', 'hidden']
      : ['open', 'followup', 'answered', 'hidden'],
  );
  const shown = $derived(queue[lane]);

  $effect(() => {
    const ev = eventId;
    const c = code;
    all = [];
    return backend().watchQuestions(ev, c, 'all', null, (q) => (all = q));
  });

  async function set(q: Question, status: QuestionStatus) {
    busy = true;
    try {
      await backend().setQuestionStatus(eventId, code, q.id, status);
    } catch {
      toast(t('err.denied'), 'error');
    } finally {
      busy = false;
    }
  }
  async function stage(q: Question) {
    busy = true;
    try {
      await backend().putOnStage(
        eventId,
        code,
        q.id,
        queue.onStage.map((s) => s.id),
      );
    } catch {
      toast(t('err.denied'), 'error');
    } finally {
      busy = false;
    }
  }
  async function talkAction(fn: () => Promise<void>) {
    busy = true;
    try {
      await fn();
    } catch {
      toast(t('err.denied'), 'error');
    } finally {
      busy = false;
    }
  }
</script>

{#snippet actions(q: Question, kind: 'onstage' | 'open' | 'pending' | 'followup' | 'answered' | 'hidden')}
  <div class="flex flex-wrap gap-2">
    {#if kind === 'onstage'}
      <button class="btn-primary !min-h-10" onclick={() => set(q, 'answered')} disabled={busy} data-testid="act-answered"><Icon name="check" size={16} />{t('admin.markAnswered')}</button>
      <button class="btn-ghost !min-h-10" onclick={() => set(q, 'followup')} disabled={busy}><Icon name="message" size={16} />{t('admin.followup')}</button>
      <button class="btn-ghost !min-h-10" onclick={() => set(q, 'open')} disabled={busy}>{t('admin.backToQueue')}</button>
    {:else if kind === 'open'}
      <button class="btn-primary !min-h-10" onclick={() => stage(q)} disabled={busy} data-testid="act-stage"><Icon name="pin" size={16} />{t('admin.onStage')}</button>
      <button class="btn-ghost !min-h-10" onclick={() => set(q, 'answered')} disabled={busy}><Icon name="check" size={16} />{t('admin.markAnswered')}</button>
      <button class="btn-ghost !min-h-10" onclick={() => set(q, 'followup')} disabled={busy} data-testid="act-followup"><Icon name="message" size={16} />{t('admin.followup')}</button>
      <button class="btn-danger !min-h-10" onclick={() => set(q, 'hidden')} disabled={busy} data-testid="act-hide"><Icon name="eye-off" size={16} />{t('admin.hide')}</button>
    {:else if kind === 'pending'}
      <button class="btn-primary !min-h-10" onclick={() => set(q, 'open')} disabled={busy} data-testid="act-approve"><Icon name="check" size={16} />{t('admin.approve')}</button>
      <button class="btn-danger !min-h-10" onclick={() => set(q, 'hidden')} disabled={busy}><Icon name="eye-off" size={16} />{t('admin.hide')}</button>
    {:else if kind === 'followup' || kind === 'answered'}
      <button class="btn-ghost !min-h-10" onclick={() => set(q, 'open')} disabled={busy}>{t('admin.backToQueue')}</button>
      {#if kind === 'followup'}
        <button class="btn-ghost !min-h-10" onclick={() => set(q, 'answered')} disabled={busy}><Icon name="check" size={16} />{t('admin.markAnswered')}</button>
      {/if}
    {:else}
      <button class="btn-ghost !min-h-10" onclick={() => set(q, 'open')} disabled={busy}>{t('admin.restore')}</button>
    {/if}
  </div>
{/snippet}

{#snippet card(q: Question, kind: 'onstage' | 'open' | 'pending' | 'followup' | 'answered' | 'hidden')}
  <article class="card flex flex-col gap-3 !p-4 {kind === 'onstage' ? 'border-2 border-brand bg-brand/5' : ''}" data-testid="admin-question" data-status={q.status}>
    <div class="flex items-start gap-3">
      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <strong data-testid="q-author">{q.pseudonym}</strong>
          <span class="text-muted">· {timeAgo(q.createdAt)}</span>
          <DeliveryBadge delivery={q.delivery} />
          {#if kind === 'onstage'}<Chip tone="brand">{t('status.onstage')}</Chip>{/if}
        </div>
        <p class="mt-1 whitespace-pre-wrap break-words text-lg leading-snug" data-testid="q-text">{q.text}</p>
        {#if q.answer}
          <p class="mt-2 rounded-lg bg-surface-2 p-2 text-sm"><span class="font-semibold text-accent-text">{t('talk.speakerAnswer')}:</span> {q.answer.text}</p>
        {/if}
      </div>
      <div class="flex w-12 shrink-0 flex-col items-center rounded-xl bg-surface-2 py-1.5 text-sm font-bold" title={t('talk.vote')}>
        <Icon name="arrow-up" size={16} /><span data-testid="vote-count">{q.votes}</span>
      </div>
    </div>
    {@render actions(q, kind)}
  </article>
{/snippet}

<main class="mx-auto flex max-w-3xl flex-col gap-4 px-4 py-4">
  {#if talk}
    <section class="flex flex-wrap items-center gap-3">
      <a href="{base}/admin/e/{eventId}" class="text-muted hover:text-ink" aria-label={t('common.back')}><Icon name="back" size={18} /></a>
      <div class="min-w-0 flex-1">
        <h2 class="truncate text-lg font-bold" data-testid="queue-title">{talk.title}</h2>
        <p class="text-sm text-muted">{talk.speakers}</p>
      </div>
      {#if talk.state === 'live'}<Chip tone="live">● {t('talk.state.live')}</Chip>{:else if talk.state === 'ended'}<Chip>{t('talk.state.ended')}</Chip>{/if}
    </section>
    <section class="flex flex-wrap gap-2">
      {#if talk.state !== 'live'}
        <button class="btn-primary !min-h-10" disabled={busy} onclick={() => talkAction(() => backend().goLive(eventId, code, talk.roomId))}><Icon name="play" size={16} />{t('admin.goLive')}</button>
      {:else}
        <button class="btn-ghost !min-h-10" disabled={busy} onclick={() => talkAction(() => backend().endTalk(eventId, code, talk.roomId))}><Icon name="square" size={16} />{t('admin.endTalk')}</button>
      {/if}
      {#if talk.state !== 'scheduled'}
        <button class="btn-ghost !min-h-10" disabled={busy} onclick={() => talkAction(() => backend().setQuestionsClosed(eventId, code, !talk.questionsClosed))}>
          <Icon name={talk.questionsClosed ? 'unlock' : 'lock'} size={16} />{talk.questionsClosed ? t('admin.reopenQuestions') : t('admin.closeQuestions')}
        </button>
      {/if}
      <a class="btn-ghost !min-h-10" href={stageUrl(eventId, talk.roomId)} target="_blank" rel="noopener"><Icon name="monitor" size={16} />{t('admin.stage')}</a>
    </section>
  {/if}

  {#if queue.onStage.length}
    <section aria-label={t('status.onstage')} class="flex flex-col gap-2" data-testid="onstage-section">
      {#each queue.onStage as q (q.id)}{@render card(q, 'onstage')}{/each}
    </section>
  {/if}

  <div class="flex flex-wrap items-center gap-2">
    <div class="flex gap-1" role="tablist">
      {#each lanes as l}
        <button role="tab" aria-selected={lane === l} class="chip {lane === l ? '!bg-ink !text-surface' : ''}" onclick={() => (lane = l)} data-testid="lane-{l}">
          {t(`lane.${l}`)} · {counts[l]}
        </button>
      {/each}
    </div>
    <div class="ml-auto flex flex-wrap gap-1" role="group" aria-label={t('admin.filter')}>
      {#each [['all', 'filter.all'], ['speak', 'delivery.speak.short'], ['read', 'delivery.read.short']] as const as [v, label]}
        <button class="chip {filter === v ? '!bg-accent !text-on-accent' : ''}" onclick={() => (filter = v)} data-testid="filter-{v}">{t(label)}</button>
      {/each}
      <button class="chip {sortMode === 'votes' ? '!bg-brand !text-on-brand' : ''}" onclick={() => (sortChoice = 'votes')}>{t('talk.sortVotes')}</button>
      <button class="chip {sortMode === 'time' ? '!bg-brand !text-on-brand' : ''}" onclick={() => (sortChoice = 'time')}>{t('talk.sortTime')}</button>
    </div>
  </div>

  <section class="flex flex-col gap-3" data-testid="queue-list">
    {#each shown as q (q.id)}{@render card(q, lane)}{/each}
    {#if shown.length === 0}<p class="py-10 text-center text-muted" data-testid="queue-empty">{t('admin.emptyLane')}</p>{/if}
  </section>
</main>
