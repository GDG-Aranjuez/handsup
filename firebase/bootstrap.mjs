// One-time (idempotent) bootstrap of a real Firebase project: superadmins, first event, rooms, talks.
//
//   gcloud auth application-default login          # or set GOOGLE_APPLICATION_CREDENTIALS
//   node bootstrap.mjs --project handsup-5f58e --superadmin you@gmail.com [--superadmin other@gmail.com]
//
// Options: --event <slug> (default fresonfest-2026)   --talks <json> (default seed/fresonfest-2026.talks.json)
//          --organizer <email> (repeatable; superadmins are added automatically)   --no-event
// Safe to re-run: documents are overwritten with the same content; existing talk states are kept.
import { readFileSync } from 'node:fs';
import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';

const args = process.argv.slice(2);
const list = (flag) => args.flatMap((a, i) => (a === flag ? [args[i + 1]] : []));
const one = (flag, fallback) => list(flag)[0] ?? fallback;

const project = one('--project');
const superadmins = list('--superadmin').map((e) => e.trim().toLowerCase());
if (!project || superadmins.length === 0) {
  console.error('usage: node bootstrap.mjs --project <id> --superadmin <email> [--superadmin <email>…]');
  process.exit(1);
}
const eventId = one('--event', 'fresonfest-2026');
const talksFile = one('--talks', new URL('./seed/fresonfest-2026.talks.json', import.meta.url).pathname);
const organizers = [...new Set([...superadmins, ...list('--organizer').map((e) => e.trim().toLowerCase())])];

initializeApp({ credential: applicationDefault(), projectId: project });
const db = getFirestore();

await db.doc('config/superadmins').set({ emails: superadmins });
console.log(`superadmins: ${superadmins.join(', ')}`);

if (!args.includes('--no-event')) {
  const plan = JSON.parse(readFileSync(talksFile, 'utf8'));
  const eventRef = db.doc(`events/${eventId}`);
  if (!(await eventRef.get()).exists) {
    await eventRef.set({
      name: 'FresON Fest 2026',
      branding: { primary: '#f37d16', accent: '#a34d0f', tagline: 'Haz tu pregunta en directo' },
      settings: { voting: true, defaultSort: 'votes', preApproval: false, graceMin: 10 },
      createdAt: Timestamp.now(),
    });
    console.log(`created event ${eventId}`);
  } else {
    console.log(`event ${eventId} already exists: left as is`);
  }
  for (const email of organizers) {
    await db.doc(`events/${eventId}/organizers/${email}`).set({ addedAt: Timestamp.now() }, { merge: true });
  }
  for (const [i, r] of plan.rooms.entries()) {
    const ref = db.doc(`events/${eventId}/rooms/${r.id}`);
    const snap = await ref.get();
    await ref.set(
      snap.exists ? { name: r.name, order: i } : { name: r.name, order: i, liveTalkId: null },
      { merge: true },
    );
  }
  for (const t of plan.talks) {
    const ref = db.doc(`events/${eventId}/talks/${t.code}`);
    const snap = await ref.get();
    const content = {
      title: t.title,
      speakers: t.speakers,
      roomId: t.room,
      start: Timestamp.fromDate(new Date(t.start)),
      end: Timestamp.fromDate(new Date(t.end)),
    };
    await ref.set(snap.exists ? content : { ...content, state: 'scheduled', questionsClosed: false }, { merge: true });
  }
  console.log(`${organizers.length} organizers, ${plan.rooms.length} rooms, ${plan.talks.length} talks in ${eventId}`);
}
