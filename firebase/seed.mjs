// Seeds the local emulators with a ready-to-test FresON Fest 2026 event.
//   pnpm emulators        (terminal 1)
//   pnpm seed             (terminal 2)
// Creates: superadmin@handsup.test and organizer@handsup.test (password: handsup-test),
// config/superadmins, the event, its rooms and talks (from seed/fresonfest-2026.talks.json).
import { readFileSync } from 'node:fs';
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';

const PROJECT = 'handsup-demo';
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= '127.0.0.1:9099';

const app = initializeApp({ projectId: PROJECT });
const auth = getAuth(app);
const db = getFirestore(app);

const PASSWORD = 'handsup-test';
const SUPERADMIN = 'superadmin@handsup.test';
const ORGANIZER = 'organizer@handsup.test';
const EVENT_ID = 'fresonfest-2026';

if (process.argv.includes('--reset')) {
  await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
  await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/accounts`, { method: 'DELETE' });
}

for (const email of [SUPERADMIN, ORGANIZER]) {
  try {
    await auth.createUser({ email, password: PASSWORD, emailVerified: true });
  } catch (e) {
    if (e.code !== 'auth/email-already-exists') throw e;
  }
}

await db.doc('config/superadmins').set({ emails: [SUPERADMIN] });
await db.doc(`events/${EVENT_ID}`).set({
  name: 'FresON Fest 2026',
  branding: { primary: '#f37d16', accent: '#a34d0f', tagline: 'Haz tu pregunta en directo' },
  settings: { voting: true, defaultSort: 'votes', preApproval: false, graceMin: 10 },
  createdAt: Timestamp.now(),
});
await db.doc(`events/${EVENT_ID}/organizers/${ORGANIZER}`).set({ addedAt: Timestamp.now() });

const plan = JSON.parse(readFileSync(new URL('./seed/fresonfest-2026.talks.json', import.meta.url), 'utf8'));
const batch = db.batch();
plan.rooms.forEach((r, i) =>
  batch.set(db.doc(`events/${EVENT_ID}/rooms/${r.id}`), { name: r.name, order: i, liveTalkId: null }),
);
for (const t of plan.talks) {
  batch.set(db.doc(`events/${EVENT_ID}/talks/${t.code}`), {
    title: t.title,
    speakers: t.speakers,
    roomId: t.room,
    start: Timestamp.fromDate(new Date(t.start)),
    end: Timestamp.fromDate(new Date(t.end)),
    state: 'scheduled',
    questionsClosed: false,
  });
}
await batch.commit();

console.log(`Seeded ${EVENT_ID}: ${plan.rooms.length} rooms, ${plan.talks.length} talks.`);
console.log(`Admin (organizer):  ${ORGANIZER} / ${PASSWORD}`);
console.log(`Superadmin:         ${SUPERADMIN} / ${PASSWORD}`);
