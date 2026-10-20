import { en } from './en';
import { es } from './es';

export type Locale = 'es' | 'en';
const dictionaries: Record<Locale, Record<string, string>> = { es, en };

function detect(): Locale {
  try {
    const stored = localStorage.getItem('handsup.locale');
    if (stored === 'es' || stored === 'en') return stored;
  } catch {
    /* storage unavailable */
  }
  const lang = typeof navigator !== 'undefined' ? navigator.language : 'es';
  return lang.toLowerCase().startsWith('en') ? 'en' : 'es';
}

export const i18n = $state<{ locale: Locale }>({ locale: 'es' });

export function initLocale(): void {
  i18n.locale = detect();
  if (typeof document !== 'undefined') document.documentElement.lang = i18n.locale;
}

export function setLocale(l: Locale): void {
  i18n.locale = l;
  document.documentElement.lang = l;
  try {
    localStorage.setItem('handsup.locale', l);
  } catch {
    /* storage unavailable */
  }
}

/** Translate `key`; `{name}` placeholders are replaced from `params`. Falls back to Spanish, then to the key. */
export function t(key: string, params: Record<string, string | number> = {}): string {
  const raw = dictionaries[i18n.locale][key] ?? dictionaries.es[key] ?? key;
  return raw.replace(/\{(\w+)\}/g, (_, k) => String(params[k] ?? ''));
}
