<script lang="ts">
  import { toasts } from '../toast.svelte';
</script>

<div class="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4" aria-live="polite">
  {#each toasts.items as item (item.id)}
    <div
      class="pointer-events-auto max-w-md rounded-xl px-4 py-3 text-sm font-medium shadow-lg {item.kind === 'error'
        ? 'bg-[#b3261e] text-white'
        : 'bg-ink text-surface'}"
      role="status"
    >
      {item.text}
    </div>
  {/each}
</div>
