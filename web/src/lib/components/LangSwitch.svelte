<script lang="ts">
  import { i18n, setLocale, t } from '#lib/i18n/index.svelte';
  import Icon from './Icon.svelte';
</script>

<button
  class="btn-ghost !min-h-9 !px-3 text-sm"
  onclick={() => setLocale(i18n.locale === 'es' ? 'en' : 'es')}
  aria-label={t('lang.switch')}
>
  <Icon name="globe" size={16} />
  {i18n.locale === 'es' ? 'EN' : 'ES'}
</button>
