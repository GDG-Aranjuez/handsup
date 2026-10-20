import { i18n } from './i18n/index.svelte';

export function formatTime(d: Date): string {
  return d.toLocaleTimeString(i18n.locale, { hour: '2-digit', minute: '2-digit' });
}

export function timeAgo(d: Date, now = Date.now()): string {
  const s = Math.round((d.getTime() - now) / 1000);
  const rtf = new Intl.RelativeTimeFormat(i18n.locale, { numeric: 'auto', style: 'short' });
  const abs = Math.abs(s);
  if (abs < 45) return rtf.format(0, 'second');
  if (abs < 3600) return rtf.format(Math.round(s / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(s / 3600), 'hour');
  return rtf.format(Math.round(s / 86400), 'day');
}
