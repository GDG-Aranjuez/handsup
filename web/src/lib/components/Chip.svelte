<script lang="ts">
  import type { Snippet } from 'svelte';
  let {
    tone = 'neutral',
    children,
  }: { tone?: 'neutral' | 'brand' | 'ok' | 'warn' | 'live'; children: Snippet } = $props();
  const tones = {
    neutral: 'bg-surface-2 text-ink',
    brand: 'bg-brand text-on-brand',
    ok: 'bg-surface-2 text-ok',
    warn: 'bg-surface-2 text-warn',
    live: 'bg-[#d6282f] text-white',
  };
</script>

<span class="inline-flex min-h-6 items-center gap-1 rounded-full px-2.5 text-xs font-semibold {tones[tone]}">
  {@render children()}
</span>
