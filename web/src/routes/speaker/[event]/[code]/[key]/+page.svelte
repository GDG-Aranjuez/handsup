<script lang="ts">
  import { page } from '$app/state';
  import { BackendError, backend } from '#lib/backend';
  import Icon from '#lib/components/Icon.svelte';
  import LangSwitch from '#lib/components/LangSwitch.svelte';
  import { sortQuestions } from '#lib/domain/ordering';
  import { DEFAULT_BRANDING, type Question } from '#lib/domain/types';
  import { EventContext } from '#lib/eventContext.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { applyBranding, requireUser } from '#lib/state.svelte';
  import { toast } from '#lib/toast.svelte';

  const eventId = $derived(page.params.event ?? '');
  const code = $derived((page.params.code ?? '').toUpperCase());
  const ctx = new EventContext(page.params.event ?? '');
  const talk = $derived(ctx.talk(code));

  let phase = $state<'loading' | 'ok' | 'invalid'>('loading');
  let all = $state<Question[]>([]);
  let drafts = $state<Record<string, string>>({});
  let saving = $state<string | null>(null);

  const followups = $derived(sortQuestions(all.filter((q) => q.status === 'followup'), 'votes'));

  $effect(() => {
    const ev = eventId;
    const c = code;
    const key = page.params.key ?? '';
    let cancelled = false;
    let unsub: (() => void) | undefined;
    ctx.reset(ev);
    phase = 'loading';
    (async () => {
      try {
        const user = await requireUser();
        await backend().openSpeakerSession(user.uid, ev, c, key);
        if (cancelled) return;
        ctx.start();
        unsub = backend().watchQuestions(ev, c, 'public', user.uid, (q) => (all = q));
        phase = 'ok';
      } catch {
        if (!cancelled) phase = 'invalid';
      }
    })();
    return () => {
      cancelled = true;
      unsub?.();
      ctx.stop();
    };
  });

  $effect(() => {
    applyBranding(ctx.event?.branding ?? DEFAULT_BRANDING);
    return () => applyBranding(DEFAULT_BRANDING);
  });

  async function answer(q: Question) {
    const text = (drafts[q.id] ?? q.answer?.text ?? '').trim();
    if (!text) return;
    saving = q.id;
    try {
      await backend().answerQuestion(eventId, code, q.id, text);
      toast(t('speaker.saved'));
    } catch (e) {
      toast(e instanceof BackendError && e.code === 'denied' ? t('err.denied') : t('err.generic'), 'error');
    } finally {
      saving = null;
    }
  }
</script>

<svelte:head><title>{ctx.event?.name ?? 'handsup'} · {t('speaker.title')}</title></svelte:head>

<main class="mx-auto flex max-w-2xl flex-col gap-4 px-4 py-6">
  <div class="flex items-center gap-3">
    <h1 class="text-xl font-bold">{ctx.event?.name ?? 'handsup'}</h1>
    <div class="ml-auto"><LangSwitch /></div>
  </div>

  {#if phase === 'loading'}
    <p class="py-10 text-center text-muted">{t('common.loading')}</p>
  {:else if phase === 'invalid'}
    <div class="card text-center" role="alert" data-testid="speaker-invalid">
      <p class="font-semibold">{t('speaker.invalid')}</p>
    </div>
  {:else}
    <section>
      <p class="text-sm text-muted">{t('speaker.title')}</p>
      <h2 class="text-2xl font-bold leading-tight" data-testid="speaker-talk">{talk?.title ?? ''}</h2>
      <p class="mt-2 text-muted">{t('speaker.help')}</p>
    </section>

    {#if followups.length === 0}
      <p class="card text-center text-muted" data-testid="speaker-empty">{t('speaker.empty')}</p>
    {/if}

    {#each followups as q (q.id)}
      <article class="card flex flex-col gap-3" data-testid="speaker-question">
        <div class="flex items-center gap-2 text-sm">
          <strong>{q.pseudonym}</strong>
          <span class="ml-auto flex items-center gap-1 text-muted"><Icon name="arrow-up" size={14} />{q.votes}</span>
        </div>
        <p class="whitespace-pre-wrap break-words text-lg leading-snug">{q.text}</p>
        <label class="label" for="a-{q.id}">{t('speaker.yourAnswer')}</label>
        <textarea
          id="a-{q.id}"
          class="input !min-h-28 py-2"
          maxlength="1500"
          value={drafts[q.id] ?? q.answer?.text ?? ''}
          oninput={(e) => (drafts[q.id] = e.currentTarget.value)}
          data-testid="speaker-answer"
        ></textarea>
        <button class="btn-primary w-fit" onclick={() => answer(q)} disabled={saving === q.id || !(drafts[q.id] ?? q.answer?.text ?? '').trim()} data-testid="speaker-save">
          {q.answer ? t('speaker.update') : t('speaker.publish')}
        </button>
      </article>
    {/each}
  {/if}
</main>
