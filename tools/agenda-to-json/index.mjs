#!/usr/bin/env node
// Converts the freson-fest website agenda (src/data/agenda.ts) into a handsup import file.
// Usage: node tools/agenda-to-json/index.mjs ../freson-fest > firebase/seed/fresonfest-2026.talks.json
// Networking sessions are skipped (nobody asks questions at breakfast). Talk codes are derived
// from the session id, so re-running the tool gives the same codes.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const repo = process.argv[2];
if (!repo) {
  console.error('usage: agenda-to-json <path to freson-fest repo>');
  process.exit(1);
}

const agendaSrc = readFileSync(join(repo, 'src/data/agenda.ts'), 'utf8');
const siteSrc = readFileSync(join(repo, 'src/data/site.ts'), 'utf8');
const date = /const startDate = "(\d{4}-\d{2}-\d{2})"/.exec(siteSrc)?.[1];
const tz = /startDateTime: `\$\{startDate\}T[\d:]+([+-]\d{2}:\d{2})`/.exec(siteSrc)?.[1];
if (!date || !tz) throw new Error('could not read the event date from site.ts');

const sessionsBlock = agendaSrc.slice(agendaSrc.indexOf('sessions: ['));
const sessions = [];
for (const m of sessionsBlock.matchAll(/\{\s*id:\s*"[^]*?\n\s{8}\}/g)) {
  const field = (k) => {
    const f = new RegExp(`${k}:\\s*(?:\\n\\s*)?"((?:[^"\\\\]|\\\\.)*)"`).exec(m[0]);
    return f ? f[1] : undefined;
  };
  if (field('id')) {
    sessions.push({
      id: field('id'),
      start: field('startTime'),
      title: field('title'),
      type: field('type'),
      speaker: field('speaker'),
      location: field('location'),
    });
  }
}
if (sessions.length === 0) throw new Error('no sessions found');

const toMin = (hhmm) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3));
const iso = (min) => `${date}T${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}:00${tz}`;
const slug = (s) =>
  s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

// Deterministic 5-char code from the session id (FNV-1a seeded LCG), unique across the file.
function codeFor(id, taken) {
  let h = 2166136261;
  for (const c of id) h = Math.imul(h ^ c.charCodeAt(0), 16777619) >>> 0;
  for (;;) {
    let out = '';
    for (let i = 0; i < 5; i++) {
      h = (Math.imul(h, 1664525) + 1013904223) >>> 0;
      out += ALPHABET[(h >>> 8) % ALPHABET.length];
    }
    if (!taken.has(out)) {
      taken.add(out);
      return out;
    }
  }
}

const starts = [...new Set(sessions.map((s) => toMin(s.start)))].sort((a, b) => a - b);
const rooms = new Map();
const taken = new Set();
const talks = [];
for (const s of sessions) {
  if (s.type === 'Networking') continue;
  const loc = s.location && s.location !== 'Por confirmar' ? s.location : 'Auditorio';
  const roomId = slug(loc);
  if (!rooms.has(roomId)) rooms.set(roomId, { id: roomId, name: loc });
  const start = toMin(s.start);
  const next = starts.find((x) => x > start);
  const end = next ?? start + 45;
  talks.push({
    code: codeFor(s.id, taken),
    title: s.title.replace(/\s+/g, ' ').trim(),
    speakers: s.speaker && s.speaker !== 'Por confirmar' ? s.speaker : '',
    room: roomId,
    start: iso(start),
    end: iso(end),
  });
}

console.log(JSON.stringify({ rooms: [...rooms.values()], talks }, null, 2));
