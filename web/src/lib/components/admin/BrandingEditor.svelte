<script lang="ts">
  import { backend } from '#lib/backend';
  import { contrast, isHexColor, onColor } from '#lib/domain/branding';
  import { getEventContext } from '#lib/eventContext.svelte';
  import { t } from '#lib/i18n/index.svelte';
  import { applyBranding } from '#lib/state.svelte';
  import { toast } from '#lib/toast.svelte';

  let { eventId }: { eventId: string } = $props();
  const ctx = getEventContext();

  let name = $state('');
  let tagline = $state('');
  let primary = $state('#d91e5b');
  let accent = $state('#1e6ad9');
  let logo = $state<string | undefined>(undefined);
  let loadedFor = '';

  $effect(() => {
    const e = ctx.event;
    if (e && loadedFor !== e.id) {
      loadedFor = e.id;
      name = e.name;
      tagline = e.branding.tagline ?? '';
      primary = e.branding.primary;
      accent = e.branding.accent;
      logo = e.branding.logo;
    }
  });

  const valid = $derived(name.trim().length > 0 && isHexColor(primary) && isHexColor(accent));
  const primaryContrast = $derived(isHexColor(primary) ? contrast(primary, onColor(primary)) : 0);

  // Live preview of the colours while editing.
  $effect(() => {
    if (isHexColor(primary) && isHexColor(accent)) applyBranding({ primary, accent, logo });
  });

  async function pickLogo(e: Event) {
    const file = (e.currentTarget as HTMLInputElement).files?.[0];
    if (!file) return;
    try {
      const bitmap = await createImageBitmap(file);
      const scale = Math.min(1, 256 / Math.max(bitmap.width, bitmap.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(bitmap.width * scale));
      canvas.height = Math.max(1, Math.round(bitmap.height * scale));
      canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      logo = canvas.toDataURL('image/png');
    } catch {
      toast(t('setup.logoFailed'), 'error');
    }
  }

  async function save(e: Event) {
    e.preventDefault();
    if (!valid || !ctx.event) return;
    try {
      await backend().saveEvent({
        ...ctx.event,
        name: name.trim(),
        branding: { primary, accent, logo, tagline: tagline.trim() || undefined },
      });
      toast(t('common.saved'));
    } catch {
      toast(t('err.denied'), 'error');
    }
  }
</script>

<form class="flex flex-col gap-3" onsubmit={save} data-testid="branding-editor">
  <h2 class="text-lg font-bold">{t('setup.branding')}</h2>
  <div class="card grid gap-3 sm:grid-cols-2">
    <div class="sm:col-span-2">
      <label class="label" for="b-name">{t('admin.eventName')}</label>
      <input id="b-name" class="input" bind:value={name} maxlength="80" data-testid="brand-name" />
    </div>
    <div class="sm:col-span-2">
      <label class="label" for="b-tag">{t('setup.tagline')}</label>
      <input id="b-tag" class="input" bind:value={tagline} maxlength="120" />
    </div>
    <div>
      <label class="label" for="b-primary">{t('setup.primary')}</label>
      <div class="flex gap-2">
        <input type="color" class="h-11 w-14 rounded-lg border border-line bg-surface p-1" bind:value={primary} aria-label={t('setup.primary')} />
        <input id="b-primary" class="input font-mono" bind:value={primary} maxlength="7" data-testid="brand-primary" />
      </div>
    </div>
    <div>
      <label class="label" for="b-accent">{t('setup.accent')}</label>
      <div class="flex gap-2">
        <input type="color" class="h-11 w-14 rounded-lg border border-line bg-surface p-1" bind:value={accent} aria-label={t('setup.accent')} />
        <input id="b-accent" class="input font-mono" bind:value={accent} maxlength="7" />
      </div>
    </div>
    <div class="sm:col-span-2">
      <label class="label" for="b-logo">{t('setup.logo')}</label>
      <div class="flex items-center gap-3">
        {#if logo}<img src={logo} alt="" class="h-12 w-12 rounded-lg border border-line object-contain" />{/if}
        <input id="b-logo" type="file" accept="image/png,image/jpeg,image/svg+xml,image/webp" onchange={pickLogo} class="text-sm" />
        {#if logo}<button type="button" class="btn-ghost !min-h-9" onclick={() => (logo = undefined)}>{t('common.remove')}</button>{/if}
      </div>
    </div>
    <div class="sm:col-span-2 flex flex-wrap items-center gap-3">
      <span class="btn-primary pointer-events-none">{t('setup.preview')}</span>
      <span class="text-sm {primaryContrast >= 4.5 ? 'text-ok' : 'text-warn'}">
        {t('setup.contrast', { n: primaryContrast.toFixed(1) })}
      </span>
    </div>
  </div>
  <button class="btn-primary w-fit" type="submit" disabled={!valid} data-testid="save-branding">{t('common.save')}</button>
</form>
