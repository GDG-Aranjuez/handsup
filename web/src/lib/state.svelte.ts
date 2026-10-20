import { backend, type AuthUser } from './backend';
import { DEFAULT_BRANDING, type Branding } from './domain/types';
import { brandingVars } from './domain/branding';

class Session {
  user = $state<AuthUser | null>(null);
  ready = $state(false);
  online = $state(true);
}
export const session = new Session();

let started = false;
/** Subscribes to auth once and tracks connectivity. Called from the root layout. */
export function startSession(): void {
  if (started || typeof window === 'undefined') return;
  started = true;
  backend().onAuth((u) => {
    session.user = u;
    session.ready = true;
  });
  session.online = navigator.onLine;
  window.addEventListener('online', () => (session.online = true));
  window.addEventListener('offline', () => (session.online = false));
}

/** Waits for the first auth event, then makes sure there is a user (anonymous if none). */
export async function requireUser(): Promise<AuthUser> {
  return backend().ensureAnonymous();
}

const root = () => document.documentElement;

/** Applies an Event's branding to the whole document. */
export function applyBranding(b: Branding = DEFAULT_BRANDING): void {
  const v = brandingVars(b);
  const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const set = (k: string, val: string) => root().style.setProperty(k, val);
  set('--brand', v['--brand']);
  set('--on-brand', v['--on-brand']);
  set('--brand-text', dark ? v['--brand-text-dark'] : v['--brand-text-light']);
  set('--accent', v['--accent']);
  set('--on-accent', v['--on-accent']);
  set('--accent-text', dark ? v['--accent-text-dark'] : v['--accent-text-light']);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', b.primary);
}
