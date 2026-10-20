import { readFileSync } from 'node:fs';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  collection,
  query,
  where,
  serverTimestamp,
  setDoc,
  updateDoc,
  increment,
  writeBatch,
} from 'firebase/firestore';

const EVENT = 'demo-fest';
const TALK = 'ABCDE';
const TALK2 = 'FGHJK';
const OTHER_EVENT = 'other-fest';
const EPOCH = Timestamp.fromMillis(0);

let env;

const attendee = (uid) => env.authenticatedContext(uid).firestore();
const organizer = (email = 'org@example.com') =>
  env.authenticatedContext('org-' + email, { email, email_verified: true }).firestore();
const superadmin = () =>
  env.authenticatedContext('super', { email: 'boss@example.com', email_verified: true }).firestore();
const anon = () => env.unauthenticatedContext().firestore();

const eventData = (over = {}) => ({
  name: 'Demo Fest',
  branding: { primary: '#d91e5b', accent: '#1e90d9' },
  settings: { voting: true, defaultSort: 'votes', preApproval: false, graceMin: 10 },
  createdAt: Timestamp.now(),
  ...over,
});
const talkData = (over = {}) => ({
  title: 'A talk',
  speakers: 'Ada',
  roomId: 'main',
  start: Timestamp.now(),
  end: Timestamp.now(),
  state: 'live',
  questionsClosed: false,
  ...over,
});

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'handsup-demo',
    firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8180 },
  });
});
afterAll(async () => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await seed(async (db) => {
    await setDoc(doc(db, 'config/superadmins'), { emails: ['boss@example.com'] });
    await setDoc(doc(db, `events/${EVENT}`), eventData());
    await setDoc(doc(db, `events/${EVENT}/organizers/org@example.com`), { at: 1 });
    await setDoc(doc(db, `events/${OTHER_EVENT}`), eventData({ name: 'Other' }));
    await setDoc(doc(db, `events/${OTHER_EVENT}/organizers/other@example.com`), { at: 1 });
    await setDoc(doc(db, `events/${EVENT}/talks/${TALK}`), talkData());
    await setDoc(doc(db, `events/${EVENT}/talks/${TALK2}`), talkData({ state: 'scheduled' }));
  });
});

// ---- helpers that mirror what the clients do ----
async function join(db, uid, name) {
  const b = writeBatch(db);
  b.set(doc(db, `events/${EVENT}/pseudonyms/${name.toLowerCase()}`), { uid, name });
  b.set(doc(db, `events/${EVENT}/attendees/${uid}`), {
    pseudonym: name,
    lastQuestionAt: EPOCH,
    qCount: 0,
  });
  return b.commit();
}
function ask(db, uid, n, over = {}, talk = TALK, attName = null) {
  const b = writeBatch(db);
  b.set(doc(db, `events/${EVENT}/talks/${talk}/questions/${uid}_${n}`), {
    text: 'Why?',
    delivery: 'read',
    authorUid: uid,
    pseudonym: attName ?? 'Marie Curie',
    status: 'open',
    votes: 0,
    createdAt: serverTimestamp(),
    ...over,
  });
  b.update(doc(db, `events/${EVENT}/attendees/${uid}`), {
    lastQuestionAt: serverTimestamp(),
    qCount: increment(1),
  });
  return b.commit();
}
const qPath = (id, talk = TALK) => `events/${EVENT}/talks/${talk}/questions/${id}`;
function vote(db, uid, qid) {
  const b = writeBatch(db);
  b.set(doc(db, `events/${EVENT}/talks/${TALK}/votes/${uid}_${qid}`), { uid, q: qid });
  b.update(doc(db, qPath(qid)), { votes: increment(1) });
  return b.commit();
}
function unvote(db, uid, qid) {
  const b = writeBatch(db);
  b.delete(doc(db, `events/${EVENT}/talks/${TALK}/votes/${uid}_${qid}`));
  b.update(doc(db, qPath(qid)), { votes: increment(-1) });
  return b.commit();
}
async function seedQuestion(id, over = {}) {
  await seed((db) =>
    setDoc(doc(db, qPath(id)), {
      text: 'Seeded',
      delivery: 'read',
      authorUid: 'seedUser',
      pseudonym: 'Seed Name',
      status: 'open',
      votes: 0,
      createdAt: Timestamp.now(),
      ...over,
    }),
  );
}

describe('events and roles', () => {
  it('requires sign-in to read', async () => {
    await assertFails(getDoc(doc(anon(), `events/${EVENT}`)));
    await assertSucceeds(getDoc(doc(attendee('u1'), `events/${EVENT}`)));
  });

  it('only superadmins create events', async () => {
    const newEvent = doc(superadmin(), 'events/new-fest');
    await assertSucceeds(setDoc(newEvent, eventData()));
    await assertFails(setDoc(doc(organizer(), 'events/another-fest'), eventData()));
    await assertFails(setDoc(doc(attendee('u1'), 'events/yet-another'), eventData()));
  });

  it('rejects an unverified email as organizer', async () => {
    const db = env.authenticatedContext('x', { email: 'org@example.com', email_verified: false }).firestore();
    await assertFails(updateDoc(doc(db, `events/${EVENT}`), { name: 'Hacked' }));
  });

  it('organizers edit only their own event', async () => {
    await assertSucceeds(updateDoc(doc(organizer(), `events/${EVENT}`), { name: 'New name' }));
    await assertFails(updateDoc(doc(organizer(), `events/${OTHER_EVENT}`), { name: 'Nope' }));
    await assertFails(
      setDoc(doc(organizer(), `events/${OTHER_EVENT}/talks/${TALK}`), talkData()),
    );
  });

  it('validates branding colours and settings', async () => {
    await assertFails(
      updateDoc(doc(organizer(), `events/${EVENT}`), { branding: { primary: 'red', accent: '#000000' } }),
    );
    await assertFails(
      updateDoc(doc(organizer(), `events/${EVENT}`), {
        settings: { voting: true, defaultSort: 'bogus', preApproval: false, graceMin: 10 },
      }),
    );
  });

  it('only superadmins manage organizers', async () => {
    await assertSucceeds(setDoc(doc(superadmin(), `events/${EVENT}/organizers/new@example.com`), { at: 1 }));
    await assertFails(setDoc(doc(organizer(), `events/${EVENT}/organizers/new@example.com`), { at: 1 }));
    await assertFails(getDoc(doc(attendee('u1'), `events/${EVENT}/organizers/org@example.com`)));
  });

  it('talk ids must be valid codes', async () => {
    await assertSucceeds(setDoc(doc(organizer(), `events/${EVENT}/talks/MNPQR`), talkData()));
    await assertFails(setDoc(doc(organizer(), `events/${EVENT}/talks/OOOOO`), talkData()));
    await assertFails(setDoc(doc(attendee('u1'), `events/${EVENT}/talks/MNPQR`), talkData()));
  });

  it('attendees cannot read the speaker secret', async () => {
    await seed((db) => setDoc(doc(db, `events/${EVENT}/talkSecrets/${TALK}`), { speakerKey: 'x'.repeat(24) }));
    await assertFails(getDoc(doc(attendee('u1'), `events/${EVENT}/talkSecrets/${TALK}`)));
    await assertSucceeds(getDoc(doc(organizer(), `events/${EVENT}/talkSecrets/${TALK}`)));
  });
});

describe('pseudonyms', () => {
  it('joins with a unique pseudonym', async () => {
    await assertSucceeds(join(attendee('u1'), 'u1', 'Marie Curie'));
  });

  it('denies a second claim of the same name (case-insensitive)', async () => {
    await join(attendee('u1'), 'u1', 'Marie Curie');
    await assertFails(join(attendee('u2'), 'u2', 'marie curie'));
  });

  it('cannot claim a name for someone else', async () => {
    const db = attendee('u2');
    const b = writeBatch(db);
    b.set(doc(db, `events/${EVENT}/pseudonyms/ada lovelace`), { uid: 'u1', name: 'Ada Lovelace' });
    await assertFails(b.commit());
  });

  it('rerolls through a new claim', async () => {
    const db = attendee('u1');
    await join(db, 'u1', 'Marie Curie');
    const b = writeBatch(db);
    b.set(doc(db, `events/${EVENT}/pseudonyms/ada lovelace`), { uid: 'u1', name: 'Ada Lovelace' });
    b.update(doc(db, `events/${EVENT}/attendees/u1`), { pseudonym: 'Ada Lovelace' });
    b.delete(doc(db, `events/${EVENT}/pseudonyms/marie curie`));
    await assertSucceeds(b.commit());
  });

  it('cannot take a pseudonym without claiming it', async () => {
    const db = attendee('u1');
    await join(db, 'u1', 'Marie Curie');
    await assertFails(updateDoc(doc(db, `events/${EVENT}/attendees/u1`), { pseudonym: 'Free Rider' }));
  });

  it('only organizers can read claim docs', async () => {
    await join(attendee('u1'), 'u1', 'Marie Curie');
    await assertFails(getDoc(doc(attendee('u1'), `events/${EVENT}/pseudonyms/marie curie`)));
    await assertSucceeds(getDocs(collection(organizer(), `events/${EVENT}/pseudonyms`)));
    await assertFails(getDocs(collection(organizer('other@example.com'), `events/${EVENT}/pseudonyms`)));
  });
});

describe('asking questions', () => {
  beforeEach(async () => {
    await join(attendee('u1'), 'u1', 'Marie Curie');
  });

  it('asks on a live talk', async () => {
    await assertSucceeds(ask(attendee('u1'), 'u1', 1));
  });

  it('cannot ask on a scheduled talk', async () => {
    await assertFails(ask(attendee('u1'), 'u1', 1, {}, TALK2));
  });

  it('cannot ask when questions are closed', async () => {
    await seed((db) => updateDoc(doc(db, `events/${EVENT}/talks/${TALK}`), { questionsClosed: true }));
    await assertFails(ask(attendee('u1'), 'u1', 1));
  });

  it('rate limits to one question per 30 s', async () => {
    const db = attendee('u1');
    await assertSucceeds(ask(db, 'u1', 1));
    await assertFails(ask(db, 'u1', 2));
  });

  it('allows asking again after 30 s', async () => {
    const db = attendee('u1');
    await assertSucceeds(ask(db, 'u1', 1));
    await seed((a) =>
      updateDoc(doc(a, `events/${EVENT}/attendees/u1`), { lastQuestionAt: Timestamp.fromMillis(Date.now() - 60000) }),
    );
    await assertSucceeds(ask(db, 'u1', 2));
  });

  it('cannot bypass the rate limit with two questions in one batch', async () => {
    const db = attendee('u1');
    const b = writeBatch(db);
    for (const n of [1, 2]) {
      b.set(doc(db, qPath(`u1_${n}`)), {
        text: 'spam', delivery: 'read', authorUid: 'u1', pseudonym: 'Marie Curie',
        status: 'open', votes: 0, createdAt: serverTimestamp(),
      });
    }
    b.update(doc(db, `events/${EVENT}/attendees/u1`), { lastQuestionAt: serverTimestamp(), qCount: increment(1) });
    await assertFails(b.commit());
  });

  it('cannot ask without bumping the attendee counter', async () => {
    const db = attendee('u1');
    await assertFails(
      setDoc(doc(db, qPath('u1_1')), {
        text: 'x', delivery: 'read', authorUid: 'u1', pseudonym: 'Marie Curie',
        status: 'open', votes: 0, createdAt: serverTimestamp(),
      }),
    );
  });

  it('rejects long text, bad delivery, forged author and forged pseudonym', async () => {
    const db = attendee('u1');
    await assertFails(ask(db, 'u1', 1, { text: 'a'.repeat(281) }));
    await assertFails(ask(db, 'u1', 1, { text: '' }));
    await assertFails(ask(db, 'u1', 1, { delivery: 'shout' }));
    await assertFails(ask(db, 'u1', 1, { authorUid: 'someone-else' }));
    await assertFails(ask(db, 'u1', 1, { pseudonym: 'Albert Einstein' }));
    await assertFails(ask(db, 'u1', 1, { votes: 50 }));
    await assertFails(ask(db, 'u1', 1, { status: 'answered' }));
  });

  it('accepts exactly 280 characters', async () => {
    await assertSucceeds(ask(attendee('u1'), 'u1', 1, { text: 'a'.repeat(280) }));
  });

  it('uses pending status when pre-approval is on', async () => {
    await seed((db) =>
      updateDoc(doc(db, `events/${EVENT}`), {
        settings: { voting: true, defaultSort: 'votes', preApproval: true, graceMin: 10 },
      }),
    );
    await assertFails(ask(attendee('u1'), 'u1', 1, { status: 'open' }));
    await assertSucceeds(ask(attendee('u1'), 'u1', 1, { status: 'pending' }));
  });

  it('accepts questions within the grace period and rejects after', async () => {
    await seed((db) =>
      updateDoc(doc(db, `events/${EVENT}/talks/${TALK}`), {
        state: 'ended',
        endedAt: Timestamp.fromMillis(Date.now() - 5 * 60000),
      }),
    );
    await assertSucceeds(ask(attendee('u1'), 'u1', 1));
    await seed((db) =>
      updateDoc(doc(db, `events/${EVENT}/talks/${TALK}`), {
        endedAt: Timestamp.fromMillis(Date.now() - 20 * 60000),
      }),
    );
    await seed((db) =>
      updateDoc(doc(db, `events/${EVENT}/attendees/u1`), { lastQuestionAt: EPOCH }),
    );
    await assertFails(ask(attendee('u1'), 'u1', 2));
  });
});

describe('visibility', () => {
  beforeEach(async () => {
    await seedQuestion('open1', { status: 'open' });
    await seedQuestion('hidden1', { status: 'hidden' });
    await seedQuestion('pending1', { status: 'pending' });
    await seedQuestion('mine', { status: 'hidden', authorUid: 'u1' });
  });

  it('attendees list visible questions only', async () => {
    const db = attendee('u1');
    const q = query(
      collection(db, `events/${EVENT}/talks/${TALK}/questions`),
      where('status', 'in', ['open', 'onstage', 'answered', 'followup']),
    );
    await assertSucceeds(getDocs(q));
    await assertFails(getDocs(collection(db, `events/${EVENT}/talks/${TALK}/questions`)));
  });

  it('attendees see their own hidden questions', async () => {
    const db = attendee('u1');
    await assertSucceeds(getDoc(doc(db, qPath('mine'))));
    await assertFails(getDoc(doc(db, qPath('hidden1'))));
    await assertFails(getDoc(doc(attendee('u2'), qPath('mine'))));
  });

  it('organizers see everything of their event', async () => {
    await assertSucceeds(getDocs(collection(organizer(), `events/${EVENT}/talks/${TALK}/questions`)));
    await assertFails(getDocs(collection(organizer('other@example.com'), `events/${EVENT}/talks/${TALK}/questions`)));
  });
});

describe('voting', () => {
  beforeEach(async () => {
    await seedQuestion('q1');
  });

  it('votes once', async () => {
    await assertSucceeds(vote(attendee('u1'), 'u1', 'q1'));
  });

  it('cannot vote twice', async () => {
    const db = attendee('u1');
    await vote(db, 'u1', 'q1');
    await assertFails(vote(db, 'u1', 'q1'));
  });

  it('different attendees can both vote', async () => {
    await assertSucceeds(vote(attendee('u1'), 'u1', 'q1'));
    await assertSucceeds(vote(attendee('u2'), 'u2', 'q1'));
  });

  it('cannot bump the counter without a vote doc', async () => {
    await assertFails(updateDoc(doc(attendee('u1'), qPath('q1')), { votes: increment(1) }));
  });

  it('cannot bump the counter by more than one', async () => {
    const db = attendee('u1');
    const b = writeBatch(db);
    b.set(doc(db, `events/${EVENT}/talks/${TALK}/votes/u1_q1`), { uid: 'u1', q: 'q1' });
    b.update(doc(db, qPath('q1')), { votes: increment(5) });
    await assertFails(b.commit());
  });

  it('cannot create a vote doc for someone else', async () => {
    const db = attendee('u1');
    const b = writeBatch(db);
    b.set(doc(db, `events/${EVENT}/talks/${TALK}/votes/u2_q1`), { uid: 'u2', q: 'q1' });
    b.update(doc(db, qPath('q1')), { votes: increment(1) });
    await assertFails(b.commit());
  });

  it('removes a vote', async () => {
    const db = attendee('u1');
    await vote(db, 'u1', 'q1');
    await assertSucceeds(unvote(db, 'u1', 'q1'));
  });

  it('cannot remove a vote that was not cast', async () => {
    await assertFails(unvote(attendee('u1'), 'u1', 'q1'));
  });

  it('cannot unvote and re-vote to inflate the counter', async () => {
    const db = attendee('u1');
    await vote(db, 'u1', 'q1');
    // decrement the counter only, keep the vote doc
    await assertFails(updateDoc(doc(db, qPath('q1')), { votes: increment(-1) }));
  });

  it('is blocked when voting is off', async () => {
    await seed((db) =>
      updateDoc(doc(db, `events/${EVENT}`), {
        settings: { voting: false, defaultSort: 'time', preApproval: false, graceMin: 10 },
      }),
    );
    await assertFails(vote(attendee('u1'), 'u1', 'q1'));
  });

  it('is blocked when the window is closed', async () => {
    await seed((db) => updateDoc(doc(db, `events/${EVENT}/talks/${TALK}`), { questionsClosed: true }));
    await assertFails(vote(attendee('u1'), 'u1', 'q1'));
  });

  it('is blocked on hidden questions', async () => {
    await seedQuestion('h', { status: 'hidden' });
    await assertFails(vote(attendee('u1'), 'u1', 'h'));
  });

  it('lets an attendee read only their own vote docs', async () => {
    await vote(attendee('u1'), 'u1', 'q1');
    await assertSucceeds(getDoc(doc(attendee('u1'), `events/${EVENT}/talks/${TALK}/votes/u1_q1`)));
    await assertFails(getDoc(doc(attendee('u2'), `events/${EVENT}/talks/${TALK}/votes/u1_q1`)));
    await assertSucceeds(
      getDocs(
        query(collection(attendee('u1'), `events/${EVENT}/talks/${TALK}/votes`), where('uid', '==', 'u1')),
      ),
    );
  });
});

describe('author controls', () => {
  beforeEach(async () => {
    await seedQuestion('mine', { authorUid: 'u1' });
  });

  it('withdraws their own question', async () => {
    await assertSucceeds(updateDoc(doc(attendee('u1'), qPath('mine')), { status: 'withdrawn' }));
  });

  it('cannot withdraw someone else\'s', async () => {
    await assertFails(updateDoc(doc(attendee('u2'), qPath('mine')), { status: 'withdrawn' }));
  });

  it('cannot set any other status', async () => {
    await assertFails(updateDoc(doc(attendee('u1'), qPath('mine')), { status: 'answered' }));
    await assertFails(updateDoc(doc(attendee('u1'), qPath('mine')), { status: 'onstage' }));
  });

  it('edits text while there are no votes', async () => {
    await assertSucceeds(updateDoc(doc(attendee('u1'), qPath('mine')), { text: 'better', delivery: 'speak' }));
  });

  it('cannot edit text once voted but can still change delivery', async () => {
    await seedQuestion('voted', { authorUid: 'u1', votes: 2 });
    await assertFails(updateDoc(doc(attendee('u1'), qPath('voted')), { text: 'bait and switch' }));
    await assertSucceeds(updateDoc(doc(attendee('u1'), qPath('voted')), { delivery: 'speak' }));
  });

  it('cannot change delivery once on stage', async () => {
    await seedQuestion('stage', { authorUid: 'u1', status: 'onstage' });
    await assertFails(updateDoc(doc(attendee('u1'), qPath('stage')), { delivery: 'speak' }));
  });

  it('cannot change the vote counter directly', async () => {
    await assertFails(updateDoc(doc(attendee('u1'), qPath('mine')), { votes: 99 }));
  });
});

describe('organizer moderation', () => {
  beforeEach(async () => {
    await seedQuestion('q1', { authorUid: 'u1' });
  });

  it('moves a question through its statuses', async () => {
    const db = organizer();
    for (const status of ['onstage', 'answered', 'followup', 'hidden', 'open']) {
      await assertSucceeds(updateDoc(doc(db, qPath('q1')), { status }));
    }
  });

  it('rejects unknown statuses and text edits', async () => {
    await assertFails(updateDoc(doc(organizer(), qPath('q1')), { status: 'weird' }));
    await assertFails(updateDoc(doc(organizer(), qPath('q1')), { text: 'edited by organizer' }));
  });

  it('another event\'s organizer cannot moderate', async () => {
    await assertFails(updateDoc(doc(organizer('other@example.com'), qPath('q1')), { status: 'hidden' }));
  });

  it('purges the author uid', async () => {
    await assertSucceeds(updateDoc(doc(organizer(), qPath('q1')), { authorUid: '' }));
    await assertFails(updateDoc(doc(organizer(), qPath('q1')), { authorUid: 'someone' }));
  });

  it('goes live and ends a talk', async () => {
    const db = organizer();
    await assertSucceeds(updateDoc(doc(db, `events/${EVENT}/talks/${TALK2}`), { state: 'live' }));
    await assertSucceeds(
      updateDoc(doc(db, `events/${EVENT}/talks/${TALK2}`), { state: 'ended', endedAt: Timestamp.now() }),
    );
    // ended without endedAt is rejected (TALK has none yet)
    await assertFails(updateDoc(doc(db, `events/${EVENT}/talks/${TALK}`), { state: 'ended' }));
  });

  it('deletes attendees\' vote docs (purge / event deletion)', async () => {
    await seedQuestion('q1', { authorUid: 'u1' });
    await vote(attendee('u2'), 'u2', 'q1');
    await assertSucceeds(getDocs(collection(organizer(), `events/${EVENT}/talks/${TALK}/votes`)));
    await assertFails(getDocs(collection(attendee('u9'), `events/${EVENT}/talks/${TALK}/votes`)));
    await assertSucceeds(deleteDoc(doc(organizer(), `events/${EVENT}/talks/${TALK}/votes/u2_q1`)));
    await vote(attendee('u3'), 'u3', 'q1');
    await assertFails(deleteDoc(doc(organizer('other@example.com'), `events/${EVENT}/talks/${TALK}/votes/u3_q1`)));
    await assertFails(deleteDoc(doc(attendee('u4'), `events/${EVENT}/talks/${TALK}/votes/u3_q1`)));
  });

  it('deletes questions and the event', async () => {
    await assertSucceeds(deleteDoc(doc(organizer(), qPath('q1'))));
    await assertFails(deleteDoc(doc(attendee('u1'), qPath('q1'))));
    await assertSucceeds(deleteDoc(doc(organizer(), `events/${EVENT}`)));
  });
});

describe('speaker link', () => {
  const KEY = 'k'.repeat(24);
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `speakerKeys/${KEY}`), { eventId: EVENT, talkId: TALK });
    });
    await seedQuestion('fu', { status: 'followup' });
    await seedQuestion('plain', { status: 'open' });
  });

  it('keys are not readable', async () => {
    await assertFails(getDoc(doc(attendee('s1'), `speakerKeys/${KEY}`)));
  });

  it('organizers create keys for their events only', async () => {
    const key2 = 'z'.repeat(24);
    await assertSucceeds(setDoc(doc(organizer(), `speakerKeys/${key2}`), { eventId: EVENT, talkId: TALK2 }));
    await assertFails(setDoc(doc(organizer('other@example.com'), `speakerKeys/${key2}`), { eventId: EVENT, talkId: TALK2 }));
    await assertFails(setDoc(doc(attendee('u'), `speakerKeys/${key2}`), { eventId: EVENT, talkId: TALK2 }));
  });

  it('opens a session only with a valid key for that talk', async () => {
    const db = attendee('s1');
    await assertSucceeds(setDoc(doc(db, 'speakerSessions/s1'), { key: KEY, eventId: EVENT, talkId: TALK }));
    await assertFails(setDoc(doc(attendee('s2'), 'speakerSessions/s2'), { key: 'bad'.repeat(8), eventId: EVENT, talkId: TALK }));
    await assertFails(setDoc(doc(attendee('s3'), 'speakerSessions/s3'), { key: KEY, eventId: EVENT, talkId: TALK2 }));
    await assertFails(setDoc(doc(attendee('s4'), 'speakerSessions/s5'), { key: KEY, eventId: EVENT, talkId: TALK }));
  });

  it('answers follow-up questions of their talk only', async () => {
    const db = attendee('s1');
    await setDoc(doc(db, 'speakerSessions/s1'), { key: KEY, eventId: EVENT, talkId: TALK });
    const answer = { text: 'Because.', at: serverTimestamp() };
    await assertSucceeds(updateDoc(doc(db, qPath('fu')), { answer }));
    await assertFails(updateDoc(doc(db, qPath('plain')), { answer }));
    await assertFails(updateDoc(doc(db, qPath('fu')), { answer, status: 'answered' }));
    await seed((a) => setDoc(doc(a, qPath('fu2', TALK2)), {
      text: 'x', delivery: 'read', authorUid: 'u', pseudonym: 'P Q', status: 'followup', votes: 0, createdAt: Timestamp.now(),
    }));
    await assertFails(updateDoc(doc(db, qPath('fu2', TALK2)), { answer }));
  });

  it('plain attendees cannot answer', async () => {
    await assertFails(updateDoc(doc(attendee('u9'), qPath('fu')), { answer: { text: 'hi', at: serverTimestamp() } }));
  });
});

describe('user preferences', () => {
  it('stores a pseudonym preference for the owner only', async () => {
    await assertSucceeds(setDoc(doc(attendee('u1'), 'users/u1'), { pseudonym: 'Ada Lovelace' }));
    await assertFails(setDoc(doc(attendee('u2'), 'users/u1'), { pseudonym: 'Ada Lovelace' }));
    await assertFails(getDoc(doc(attendee('u2'), 'users/u1')));
  });

  it('only superadmins can read the superadmin list, nobody can write it', async () => {
    await assertSucceeds(getDoc(doc(superadmin(), 'config/superadmins')));
    await assertFails(getDoc(doc(organizer(), 'config/superadmins')));
    await assertFails(getDoc(doc(attendee('u1'), 'config/superadmins')));
    await assertFails(setDoc(doc(superadmin(), 'config/superadmins'), { emails: ['x@y.z'] }));
  });
});
