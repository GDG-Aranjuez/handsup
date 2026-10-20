<script lang="ts">
  import { backend } from '#lib/backend';
  import { DEFAULT_SETTINGS, type SortMode } from '#lib/domain/types';
  import { getEventContext } from '#lib/eventContext.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { toast } from '#lib/toast.svelte';

  const ctx = getEventContext();
  let voting = $state(DEFAULT_SETTINGS.voting);
  let preApproval = $state(DEFAULT_SETTINGS.preApproval);
  let defaultSort = $state<SortMode>(DEFAULT_SETTINGS.defaultSort);
  let graceMin = $state(DEFAULT_SETTINGS.graceMin);
  let loadedFor = '';

  $effect(() => {
    const e = ctx.event;
    if (e && loadedFor !== e.id) {
      loadedFor = e.id;
      ({ voting, preApproval, defaultSort, graceMin } = e.settings);
    }
  });

  async function save(e: Event) {
    e.preventDefault();
    if (!ctx.event) return;
    try {
      await backend().saveEvent({
        ...ctx.event,
        settings: { voting, preApproval, defaultSort, graceMin: Math.max(0, Math.min(240, Math.round(graceMin))) },
      });
      toast(t('common.saved'));
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
</script>

<form class="flex flex-col gap-3" onsubmit={save} data-testid="settings-editor">
  <h2 class="text-lg font-bold">{t('setup.settings')}</h2>
  <div class="card flex flex-col gap-4">
    <label class="flex items-start gap-3">
      <input type="checkbox" class="mt-1 h-5 w-5" bind:checked={voting} data-testid="set-voting" />
      <span><span class="block font-medium">{t('setup.voting')}</span><span class="text-sm text-muted">{t('setup.votingHelp')}</span></span>
    </label>
    <label class="flex items-start gap-3">
      <input type="checkbox" class="mt-1 h-5 w-5" bind:checked={preApproval} data-testid="set-preapproval" />
      <span><span class="block font-medium">{t('setup.preApproval')}</span><span class="text-sm text-muted">{t('setup.preApprovalHelp')}</span></span>
    </label>
    <div>
      <label class="label" for="s-sort">{t('setup.defaultSort')}</label>
      <select id="s-sort" class="input" bind:value={defaultSort}>
        <option value="votes">{t('talk.sortVotes')}</option>
        <option value="time">{t('talk.sortTime')}</option>
      </select>
    </div>
    <div>
      <label class="label" for="s-grace">{t('setup.grace')}</label>
      <input id="s-grace" class="input w-32" type="number" min="0" max="240" bind:value={graceMin} />
      <p class="mt-1 text-sm text-muted">{t('setup.graceHelp')}</p>
    </div>
  </div>
  <button class="btn-primary w-fit" type="submit" data-testid="save-settings">{t('common.save')}</button>
</form>
