<script lang="ts">
  import { t } from '#lib/i18n/index.svelte';
  import type { Delivery } from '#lib/domain/types';
  import Icon from './Icon.svelte';
  let { delivery, compact = false }: { delivery: Delivery; compact?: boolean } = $props();
</script>

<span
  class="inline-flex min-h-6 items-center gap-1 rounded-full bg-surface-2 px-2.5 text-xs font-semibold {delivery === 'speak'
    ? 'text-accent-text'
    : 'text-muted'}"
  data-testid="delivery-{delivery}"
  title={t(`delivery.${delivery}.long`)}
>
  <Icon name={delivery === 'speak' ? 'mic' : 'volume'} size={14} />
  {#if !compact}{t(`delivery.${delivery}.short`)}{/if}
</span>
