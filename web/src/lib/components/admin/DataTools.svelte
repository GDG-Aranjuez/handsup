<script lang="ts">
  import { goto } from '$app/navigation';
  import { base } from '#lib/paths';
  import { backend } from '#lib/backend';
  import Icon from '#lib/components/Icon.svelte';
  import { toCsv } from '#lib/domain/csv';
  import { parseImport, type ImportPlan } from '#lib/domain/importer';
  import { getEventContext } from '#lib/eventContext.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { toast } from '#lib/toast.svelte';

  let { eventId }: { eventId: string } = $props();
  const ctx = getEventContext();

  let text = $state('');
  let plan = $state<ImportPlan | null>(null);
  let importError = $state('');
  let confirmPurge = $state(false);
  let deleteSlug = $state('');
  let busy = $state(false);

  function validate() {
    plan = null;
    importError = '';
    try {
      plan = parseImport(JSON.parse(text), ctx.talks.map((x) => x.id));
    } catch (e) {
      importError = e instanceof SyntaxError ? t('setup.importBadJson') : String((e as Error).message);
    }
  }
  async function readFile(e: Event) {
    const file = (e.currentTarget as HTMLInputElement).files?.[0];
    if (file) {
      text = await file.text();
      validate();
    }
  }
  async function runImport() {
    if (!plan) return;
    busy = true;
    try {
      await backend().importPlan(eventId, plan);
      toast(t('setup.imported', { n: plan.talks.length }));
      plan = null;
      text = '';
    } catch {
      toast(t('err.denied'), 'error');
    } finally {
      busy = false;
    }
  }

  async function exportCsv() {
    busy = true;
    try {
      const rows = await backend().exportQuestions(eventId);
      const csv = toCsv(
        ['talk', 'title', 'pseudonym', 'text', 'delivery', 'status', 'votes', 'created_at', 'answer'],
        rows.map((r) => [r.talk, r.talkTitle, r.pseudonym, r.text, r.delivery, r.status, r.votes, r.createdAt, r.answer]),
      );
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `${eventId}-questions.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast(t('err.denied'), 'error');
    } finally {
      busy = false;
    }
  }
  async function purge() {
    if (!confirmPurge) {
      confirmPurge = true;
      return;
    }
    confirmPurge = false;
    busy = true;
    try {
      const n = await backend().purgeIdentities(eventId);
      toast(t('setup.purged', { n }));
    } catch {
      toast(t('err.denied'), 'error');
    } finally {
      busy = false;
    }
  }
  async function deleteEvent() {
    if (deleteSlug !== eventId) return;
    busy = true;
    try {
      await backend().deleteEvent(eventId);
      goto(`${base}/admin`);
    } catch {
      toast(t('err.denied'), 'error');
      busy = false;
    }
  }
</script>

<section class="flex flex-col gap-4" data-testid="data-tools">
  <h2 class="text-lg font-bold">{t('setup.data')}</h2>

  <div class="card flex flex-col gap-3">
    <h3 class="font-semibold">{t('setup.import')}</h3>
    <p class="text-sm text-muted">{t('setup.importHelp')}</p>
    <input type="file" accept="application/json,.json" onchange={readFile} class="text-sm" data-testid="import-file" />
    <textarea class="input !min-h-32 py-2 font-mono text-xs" bind:value={text} oninput={validate} placeholder={'{ "rooms": [...], "talks": [...] }'} data-testid="import-text"></textarea>
    {#if importError}<p class="text-sm text-[#c0262d]" role="alert" data-testid="import-error">{importError}</p>{/if}
    {#if plan}
      <p class="text-sm text-ok" data-testid="import-preview">{t('setup.importPreview', { rooms: plan.rooms.length, talks: plan.talks.length })}</p>
      <button class="btn-primary w-fit" onclick={runImport} disabled={busy} data-testid="run-import"><Icon name="upload" size={16} />{t('setup.importRun')}</button>
    {/if}
  </div>

  <div class="card flex flex-col gap-3">
    <h3 class="font-semibold">{t('setup.export')}</h3>
    <button class="btn-ghost w-fit" onclick={exportCsv} disabled={busy} data-testid="export-csv"><Icon name="download" size={16} />CSV</button>
  </div>

  <div class="card flex flex-col gap-3">
    <h3 class="font-semibold">{t('setup.privacy')}</h3>
    <p class="text-sm text-muted">{t('setup.purgeHelp')}</p>
    <button class="btn-danger w-fit" onclick={purge} disabled={busy} data-testid="purge">{confirmPurge ? t('common.sure') : t('setup.purge')}</button>
  </div>

  <div class="card flex flex-col gap-3 border-[#c0262d]/40">
    <h3 class="font-semibold text-[#c0262d]">{t('setup.danger')}</h3>
    <p class="text-sm text-muted">{t('setup.deleteHelp', { slug: eventId })}</p>
    <div class="flex gap-2">
      <input class="input font-mono" bind:value={deleteSlug} placeholder={eventId} aria-label={t('setup.deleteConfirm')} data-testid="delete-slug" />
      <button class="btn-danger" onclick={deleteEvent} disabled={busy || deleteSlug !== eventId} data-testid="delete-event">{t('setup.deleteEvent')}</button>
    </div>
  </div>
</section>
