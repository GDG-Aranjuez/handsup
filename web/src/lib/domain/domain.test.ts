import { describe, expect, it } from 'vitest';
import { brandingVars, contrast, onColor, readableOn } from './branding';
import { csvCell, toCsv } from './csv';
import { parseImport } from './importer';
import { buildQueue, sortQuestions } from './ordering';
import {
  PSEUDONYM_COMBINATIONS,
  cleanPseudonym,
  generatePseudonym,
  isValidPseudonym,
  pseudonymKey,
} from './pseudonyms';
import { generateSpeakerKey } from './speakerKey';
import {
  TALK_CODE_ALPHABET,
  generateTalkCode,
  isValidTalkCode,
  normalizeTalkCode,
  uniqueTalkCode,
} from './talkCode';
import type { Question } from './types';

const q = (id: string, votes: number, minute: number, status: Question['status'] = 'open'): Question => ({
  id,
  text: id,
  delivery: 'read',
  authorUid: 'u',
  pseudonym: 'A B',
  status,
  votes,
  createdAt: new Date(2026, 9, 24, 10, minute),
});

describe('ordering', () => {
  const list = [q('a', 1, 0), q('b', 5, 1), q('c', 5, 2), q('d', 0, 3)];
  it('sorts by votes then oldest', () => {
    expect(sortQuestions(list, 'votes').map((x) => x.id)).toEqual(['b', 'c', 'a', 'd']);
  });
  it('sorts by time', () => {
    expect(sortQuestions(list, 'time').map((x) => x.id)).toEqual(['a', 'b', 'c', 'd']);
  });
  it('does not mutate the input', () => {
    const copy = [...list];
    sortQuestions(list, 'votes');
    expect(list).toEqual(copy);
  });
  it('builds lanes', () => {
    const view = buildQueue(
      [q('a', 1, 0), q('s', 9, 1, 'onstage'), q('f', 2, 2, 'followup'), q('h', 0, 3, 'hidden'), q('p', 0, 4, 'pending'), q('x', 0, 5, 'answered')],
      'votes',
    );
    expect(view.onStage.map((x) => x.id)).toEqual(['s']);
    expect(view.open.map((x) => x.id)).toEqual(['a']);
    expect(view.followup.map((x) => x.id)).toEqual(['f']);
    expect(view.hidden.map((x) => x.id)).toEqual(['h']);
    expect(view.pending.map((x) => x.id)).toEqual(['p']);
    expect(view.answered.map((x) => x.id)).toEqual(['x']);
  });
});

describe('talk codes', () => {
  it('has no ambiguous glyphs', () => {
    for (const c of '01OIL') expect(TALK_CODE_ALPHABET).not.toContain(c);
  });
  it('generates valid codes', () => {
    for (let i = 0; i < 200; i++) expect(isValidTalkCode(generateTalkCode())).toBe(true);
  });
  it('normalises typed input', () => {
    expect(normalizeTalkCode(' ab-cde ')).toBe('ABCDE');
    expect(normalizeTalkCode('abcdefgh')).toBe('ABCDE');
  });
  it('rejects bad codes', () => {
    expect(isValidTalkCode('ABCD')).toBe(false);
    expect(isValidTalkCode('ABCD0')).toBe(false);
    expect(isValidTalkCode('abcde')).toBe(false);
  });
  it('never repeats a taken code', () => {
    const taken = new Set(['AAAAA']);
    let calls = 0;
    // first code collides with the taken one (all zeros), the second does not
    const rnd = () => (calls++ < 5 ? 0 : 0.5);
    const code = uniqueTalkCode(taken, rnd);
    expect(code).not.toBe('AAAAA');
    expect(taken.has(code)).toBe(true);
  });
});

describe('pseudonyms', () => {
  it('has a big pool', () => expect(PSEUDONYM_COMBINATIONS).toBeGreaterThan(1500));
  it('generates First Last', () => {
    for (let i = 0; i < 100; i++) {
      const name = generatePseudonym();
      expect(name).toMatch(/^\S+ \S+$/);
      expect(isValidPseudonym(name)).toBe(true);
    }
  });
  it('is deterministic with a seeded RNG', () => {
    expect(generatePseudonym(() => 0)).toBe('Marie Curie');
  });
  it('key matches lower()', () => expect(pseudonymKey('Marie CURIE')).toBe('marie curie'));
  it('cleans custom names', () => {
    expect(cleanPseudonym('  a/b\n  c  ')).toBe('a b c');
    expect(cleanPseudonym('x'.repeat(50))).toHaveLength(30);
  });
  it('validates', () => {
    expect(isValidPseudonym('A')).toBe(false);
    expect(isValidPseudonym('a/b')).toBe(false);
  });
});

describe('branding', () => {
  it('picks readable text colours', () => {
    expect(onColor('#ffffff')).toBe('#111111');
    expect(onColor('#000000')).toBe('#ffffff');
    expect(contrast(onColor('#d91e5b'), '#d91e5b')).toBeGreaterThan(4);
  });
  it('makes brand colours readable on a surface', () => {
    expect(contrast(readableOn('#ffee00', '#ffffff'), '#ffffff')).toBeGreaterThanOrEqual(4.5);
    expect(contrast(readableOn('#101040', '#14141a'), '#14141a')).toBeGreaterThanOrEqual(4.5);
  });
  it('emits css vars', () => {
    const vars = brandingVars({ primary: '#d91e5b', accent: '#1e6ad9' });
    expect(vars['--brand']).toBe('#d91e5b');
    expect(Object.keys(vars)).toContain('--on-accent');
  });
});

describe('csv', () => {
  it('quotes and escapes', () => {
    expect(csvCell('a,b')).toBe('"a,b"');
    expect(csvCell('say "hi"')).toBe('"say ""hi"""');
  });
  it('neutralises formula injection', () => {
    expect(csvCell('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`);
    expect(csvCell('+1')).toBe("'+1");
  });
  it('writes rows', () => {
    expect(toCsv(['a', 'b'], [[1, 'x']])).toBe('a,b\n1,x\n');
  });
});

describe('import', () => {
  const base = {
    rooms: [{ id: 'main', name: 'Sala' }],
    talks: [
      { title: 'One', speakers: 'Ada', room: 'main', start: '2026-10-24T10:00:00+02:00', end: '2026-10-24T10:45:00+02:00' },
      { code: 'ABCDE', title: 'Two', room: 'main', start: '2026-10-24T11:00:00+02:00', end: '2026-10-24T11:45:00+02:00' },
    ],
  };
  it('imports and generates missing codes', () => {
    const plan = parseImport(base);
    expect(plan.talks).toHaveLength(2);
    expect(isValidTalkCode(plan.talks[0].code)).toBe(true);
    expect(plan.talks[1].code).toBe('ABCDE');
    expect(plan.talks[0].code).not.toBe('ABCDE');
  });
  it('rejects unknown rooms, bad dates and repeats', () => {
    expect(() => parseImport({ ...base, talks: [{ ...base.talks[0], room: 'nope' }] })).toThrow(/room/);
    expect(() => parseImport({ ...base, talks: [{ ...base.talks[0], end: 'x' }] })).toThrow(/end/);
    expect(() => parseImport({ ...base, talks: [{ ...base.talks[0], end: base.talks[0].start }] })).toThrow(/termina/);
    expect(() => parseImport({ ...base, talks: [base.talks[1], base.talks[1]] })).toThrow(/repetido/);
    expect(() => parseImport(null)).toThrow();
    expect(() => parseImport({ rooms: [], talks: [] })).toThrow(/rooms/);
  });
  it('avoids codes already used by the event', () => {
    const plan = parseImport(base, ['ZZZZZ']);
    expect(plan.talks.map((t) => t.code)).not.toContain('ZZZZZ');
  });
});

describe('speaker keys', () => {
  it('is long and random', () => {
    const a = generateSpeakerKey();
    expect(a).toHaveLength(32);
    expect(a).not.toBe(generateSpeakerKey());
  });
});
