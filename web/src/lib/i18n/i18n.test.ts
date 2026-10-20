import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { en } from './en';
import { es } from './es';

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f);
    return statSync(p).isDirectory() ? files(p) : /\.(svelte|ts)$/.test(f) && !f.endsWith('.test.ts') ? [p] : [];
  });
}

const EXPANSIONS: Record<string, string[]> = {
  'delivery.${d}.hint': ['delivery.speak.hint', 'delivery.read.hint'],
  'delivery.${d}.short': ['delivery.speak.short', 'delivery.read.short'],
  'delivery.${delivery}.long': ['delivery.speak.long', 'delivery.read.long'],
  'delivery.${delivery}.short': ['delivery.speak.short', 'delivery.read.short'],
  'lane.${l}': ['open', 'pending', 'followup', 'answered', 'hidden'].map((s) => `lane.${s}`),
  'setup.tab.${k}': ['talks', 'rooms', 'branding', 'settings', 'team', 'data'].map((s) => `setup.tab.${s}`),
  'status.${q.status}': ['pending', 'open', 'onstage', 'answered', 'followup', 'hidden', 'withdrawn'].map((s) => `status.${s}`),
  'filter.all': ['filter.all'],
};

describe('translations', () => {
  const used = new Set<string>();
  for (const f of files(join(__dirname, '..', '..'))) {
    const src = readFileSync(f, 'utf8');
    for (const m of src.matchAll(/\bt\(\s*['`]([^'`]+)['`]/g)) {
      for (const k of EXPANSIONS[m[1]] ?? [m[1]]) used.add(k);
    }
  }
  it('finds keys in the source', () => expect(used.size).toBeGreaterThan(100));
  it('every used key exists in Spanish', () => {
    expect([...used].filter((k) => !(k in es))).toEqual([]);
  });
  it('every used key exists in English', () => {
    expect([...used].filter((k) => !(k in en))).toEqual([]);
  });
  it('both languages have the same keys', () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(es).sort());
  });
  it('placeholders match', () => {
    for (const k of Object.keys(es)) {
      const ph = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
      expect(ph(en[k]), k).toEqual(ph(es[k]));
    }
  });
});
