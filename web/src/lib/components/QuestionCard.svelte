<script lang="ts">
  import { MAX_QUESTION_LENGTH, type Delivery, type Question } from '#lib/domain/types';
  import { timeAgo } from '#lib/format';
  import { t } from '#lib/i18n/index.svelte';
  import Chip from './Chip.svelte';
  import DeliveryBadge from './DeliveryBadge.svelte';
  import Icon from './Icon.svelte';

  let {
    q,
    mine = false,
    voted = false,
    canVote = false,
    showStatus = false,
    onvote,
    onwithdraw,
    onedit,
  }: {
    q: Question;
    mine?: boolean;
    voted?: boolean;
    canVote?: boolean;
    showStatus?: boolean;
    onvote?: () => void;
    onwithdraw?: () => void;
    onedit?: (patch: { text?: string; delivery?: Delivery }) => void;
  } = $props();

  let editing = $state(false);
  let text = $state('');
  let delivery = $state<Delivery>('read');
  const editable = $derived(mine && (q.status === 'open' || q.status === 'pending'));
  const textEditable = $derived(editable && q.votes === 0);

  function startEdit() {
    text = q.text;
    delivery = q.delivery;
    editing = true;
  }
  function save() {
    const patch: { text?: string; delivery?: Delivery } = {};
    if (textEditable && text.trim() !== q.text) patch.text = text.trim();
    if (delivery !== q.delivery) patch.delivery = delivery;
    if (Object.keys(patch).length) onedit?.(patch);
    editing = false;
  }
  const statusTone = (s: string) => (s === 'onstage' ? 'brand' : s === 'answered' ? 'ok' : s === 'followup' ? 'warn' : 'neutral');
</script>

<article
  class="card flex gap-3 !p-3 {q.status === 'onstage' ? 'border-2 border-brand' : ''} {q.status === 'withdrawn' || q.status === 'hidden' ? 'opacity-60' : ''}"
  data-testid="question"
  data-status={q.status}
>
  <div class="flex min-w-0 flex-1 flex-col gap-2">
    <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
      <span class="font-semibold" data-testid="q-author">{q.pseudonym}</span>
      {#if mine}<Chip tone="brand">{t('talk.you')}</Chip>{/if}
      <span class="text-muted">· {timeAgo(q.createdAt)}</span>
    </div>

    {#if editing}
      <div class="flex flex-col gap-2">
        {#if textEditable}
          <textarea class="input !min-h-24 py-2" bind:value={text} maxlength={MAX_QUESTION_LENGTH} aria-label={t('talk.editText')}></textarea>
        {:else}
          <p class="whitespace-pre-wrap break-words">{q.text}</p>
          <p class="text-xs text-muted">{t('talk.editLocked')}</p>
        {/if}
        <div class="flex gap-2" role="radiogroup" aria-label={t('delivery.label')}>
          {#each ['read', 'speak'] as const as d}
            <button
              type="button"
              role="radio"
              aria-checked={delivery === d}
              class="chip {delivery === d ? '!bg-brand !text-on-brand' : ''}"
              onclick={() => (delivery = d)}
            >
              <Icon name={d === 'speak' ? 'mic' : 'volume'} size={14} />{t(`delivery.${d}.short`)}
            </button>
          {/each}
        </div>
        <div class="flex gap-2">
          <button class="btn-primary !min-h-9" onclick={save} disabled={textEditable && !text.trim()}>{t('common.save')}</button>
          <button class="btn-ghost !min-h-9" onclick={() => (editing = false)}>{t('common.cancel')}</button>
        </div>
      </div>
    {:else}
      <p class="whitespace-pre-wrap break-words text-[17px] leading-snug" data-testid="q-text">{q.text}</p>
    {/if}

    {#if q.answer}
      <div class="rounded-xl bg-surface-2 p-3 text-sm" data-testid="q-answer">
        <p class="mb-1 flex items-center gap-1 font-semibold text-accent-text"><Icon name="message" size={14} />{t('talk.speakerAnswer')}</p>
        <p class="whitespace-pre-wrap break-words">{q.answer.text}</p>
      </div>
    {/if}

    <div class="flex flex-wrap items-center gap-2">
      <DeliveryBadge delivery={q.delivery} />
      {#if showStatus || q.status === 'onstage' || q.status === 'answered' || q.status === 'followup'}
        <Chip tone={statusTone(q.status)}>{t(`status.${q.status}`)}</Chip>
      {/if}
      {#if editable && !editing}
        <button class="chip hover:bg-line" onclick={startEdit} data-testid="q-edit"><Icon name="pencil" size={14} />{t('common.edit')}</button>
      {/if}
      {#if mine && (q.status === 'open' || q.status === 'pending') && !editing}
        <button class="chip hover:bg-line" onclick={onwithdraw} data-testid="q-withdraw"><Icon name="x" size={14} />{t('talk.withdraw')}</button>
      {/if}
    </div>
  </div>

  {#if q.status === 'open' || q.status === 'onstage' || q.status === 'answered' || q.status === 'followup'}
    <button
      class="flex h-14 w-12 shrink-0 flex-col items-center justify-center gap-0.5 rounded-xl border text-sm font-bold transition-colors disabled:opacity-40 {voted
        ? 'border-brand bg-brand text-on-brand'
        : 'border-line bg-surface text-ink hover:bg-surface-2'}"
      aria-pressed={voted}
      aria-label={voted ? t('talk.unvote') : t('talk.vote')}
      disabled={!canVote && !voted}
      onclick={onvote}
      data-testid="vote"
    >
      <Icon name="arrow-up" size={18} />
      <span data-testid="vote-count">{q.votes}</span>
    </button>
  {/if}
</article>
