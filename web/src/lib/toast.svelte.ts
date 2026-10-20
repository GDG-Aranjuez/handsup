export const toasts = $state<{ items: { id: number; text: string; kind: 'info' | 'error' }[] }>({ items: [] });
let next = 1;

export function toast(text: string, kind: 'info' | 'error' = 'info'): void {
  const id = next++;
  toasts.items.push({ id, text, kind });
  setTimeout(() => {
    toasts.items = toasts.items.filter((t) => t.id !== id);
  }, 4000);
}
