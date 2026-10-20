import {
  GoogleAuthProvider,
  linkWithPopup,
  signInAnonymously,
  signInWithCredential,
  createUserWithEmailAndPassword,
  sendEmailVerification,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut as fbSignOut,
  onAuthStateChanged,
  type User,
} from 'firebase/auth';
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  increment,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  type DocumentData,
  type Firestore,
  type WriteBatch,
} from 'firebase/firestore';
import type { ImportPlan } from '../domain/importer';
import { cleanPseudonym, generatePseudonym, pseudonymKey } from '../domain/pseudonyms';
import { generateSpeakerKey } from '../domain/speakerKey';
import {
  DEFAULT_BRANDING,
  DEFAULT_SETTINGS,
  VISIBLE_STATUSES,
  type Attendee,
  type EventDoc,
  type Question,
  type QuestionStatus,
  type Room,
  type Talk,
} from '../domain/types';
import { firebase } from './firebase';
import { BackendError, type AuthUser, type ExportRow, type QaBackend, type QuestionScope } from './types';

const date = (v: unknown): Date => (v instanceof Timestamp ? v.toDate() : new Date(0));

function wrap(e: unknown): BackendError {
  if (e instanceof BackendError) return e;
  const code = (e as { code?: string })?.code ?? '';
  const message = (e as Error)?.message ?? String(e);
  if (code === 'permission-denied') return new BackendError('denied', message);
  if (code === 'unavailable') return new BackendError('offline', message);
  if (code === 'not-found') return new BackendError('not-found', message);
  return new BackendError('unknown', message);
}

async function guard<T>(p: Promise<T>): Promise<T> {
  try {
    return await p;
  } catch (e) {
    throw wrap(e);
  }
}

function toUser(u: User): AuthUser {
  return { uid: u.uid, email: u.email, emailVerified: u.emailVerified, isAnonymous: u.isAnonymous };
}

function mapEvent(id: string, d: DocumentData): EventDoc {
  return {
    id,
    name: d.name,
    branding: { ...DEFAULT_BRANDING, ...d.branding },
    settings: { ...DEFAULT_SETTINGS, ...d.settings },
  };
}
function mapRoom(id: string, d: DocumentData): Room {
  return { id, name: d.name, liveTalkId: d.liveTalkId ?? null, order: d.order ?? 0 };
}
function mapTalk(id: string, d: DocumentData): Talk {
  return {
    id,
    title: d.title,
    speakers: d.speakers ?? '',
    roomId: d.roomId,
    start: date(d.start),
    end: date(d.end),
    state: d.state,
    questionsClosed: d.questionsClosed === true,
    endedAt: d.endedAt ? date(d.endedAt) : undefined,
  };
}
function mapQuestion(id: string, d: DocumentData): Question {
  return {
    id,
    text: d.text,
    delivery: d.delivery,
    authorUid: d.authorUid,
    pseudonym: d.pseudonym,
    status: d.status,
    votes: d.votes ?? 0,
    createdAt: d.createdAt instanceof Timestamp ? d.createdAt.toDate() : new Date(),
    answer: d.answer ? { text: d.answer.text, at: date(d.answer.at) } : undefined,
  };
}

function eventToDoc(e: EventDoc, createdAt?: unknown): DocumentData {
  const branding: DocumentData = { primary: e.branding.primary, accent: e.branding.accent };
  if (e.branding.logo) branding.logo = e.branding.logo;
  if (e.branding.tagline) branding.tagline = e.branding.tagline;
  return { name: e.name, branding, settings: { ...e.settings }, ...(createdAt ? { createdAt } : {}) };
}

function talkToDoc(t: Talk): DocumentData {
  const d: DocumentData = {
    title: t.title,
    speakers: t.speakers,
    roomId: t.roomId,
    start: Timestamp.fromDate(t.start),
    end: Timestamp.fromDate(t.end),
    state: t.state,
    questionsClosed: t.questionsClosed,
  };
  if (t.endedAt) d.endedAt = Timestamp.fromDate(t.endedAt);
  return d;
}

const CHUNK = 400;
async function commitChunks(db: Firestore, ops: Array<(b: WriteBatch) => void>): Promise<void> {
  for (let i = 0; i < ops.length; i += CHUNK) {
    const b = writeBatch(db);
    ops.slice(i, i + CHUNK).forEach((op) => op(b));
    await b.commit();
  }
}

export function createFirestoreBackend(): QaBackend {
  const { auth, db } = firebase();
  const ev = (e: string) => doc(db, 'events', e);
  const talkRef = (e: string, t: string) => doc(db, 'events', e, 'talks', t);
  const questionsCol = (e: string, t: string) => collection(db, 'events', e, 'talks', t, 'questions');
  const questionRef = (e: string, t: string, q: string) => doc(db, 'events', e, 'talks', t, 'questions', q);
  const attendeeRef = (e: string, u: string) => doc(db, 'events', e, 'attendees', u);
  const claimRef = (e: string, name: string) => doc(db, 'events', e, 'pseudonyms', pseudonymKey(name));
  const voteRef = (e: string, t: string, u: string, q: string) =>
    doc(db, 'events', e, 'talks', t, 'votes', `${u}_${q}`);

  let anonymous: Promise<AuthUser> | undefined;

  async function currentUser(): Promise<User | null> {
    await auth.authStateReady();
    return auth.currentUser;
  }

  async function isSuperadmin(): Promise<boolean> {
    const u = await currentUser();
    if (!u || u.isAnonymous) return false;
    try {
      await getDoc(doc(db, 'config', 'superadmins'));
      return true;
    } catch {
      return false;
    }
  }

  // Claim `name` and point the attendee at it in one atomic batch. A clash on the
  // claim doc surfaces as permission-denied and nothing is written.
  async function claimAndSet(eventId: string, uid: string, name: string, existing: string | null): Promise<void> {
    const b = writeBatch(db);
    const sameKey = existing !== null && pseudonymKey(existing) === pseudonymKey(name);
    if (!sameKey) b.set(claimRef(eventId, name), { uid, name });
    if (existing === null) {
      b.set(attendeeRef(eventId, uid), { pseudonym: name, lastQuestionAt: Timestamp.fromMillis(0), qCount: 0 });
    } else {
      b.update(attendeeRef(eventId, uid), { pseudonym: name });
      if (!sameKey) b.delete(claimRef(eventId, existing));
    }
    await b.commit();
  }

  const backend: QaBackend = {
    // ---------- auth ----------
    onAuth(cb) {
      return onAuthStateChanged(auth, (u) => cb(u ? toUser(u) : null));
    },
    // Single-flight: two callers racing would otherwise create two anonymous users.
    ensureAnonymous() {
      anonymous ??= (async () => {
        const u = await currentUser();
        if (u) return toUser(u);
        return toUser((await guard(signInAnonymously(auth))).user);
      })().finally(() => (anonymous = undefined));
      return anonymous;
    },
    async signInGoogle() {
      return toUser((await guard(signInWithPopup(auth, new GoogleAuthProvider()))).user);
    },
    async linkGoogle() {
      const u = await currentUser();
      if (!u) throw new BackendError('denied', 'not signed in');
      try {
        return toUser((await linkWithPopup(u, new GoogleAuthProvider())).user);
      } catch (e) {
        if ((e as { code?: string }).code === 'auth/credential-already-in-use') {
          const cred = GoogleAuthProvider.credentialFromError(e as never);
          if (cred) return toUser((await signInWithCredential(auth, cred)).user);
        }
        throw wrap(e);
      }
    },
    async signInEmail(email, password) {
      return toUser((await guard(signInWithEmailAndPassword(auth, email.trim(), password))).user);
    },
    async registerEmail(email, password) {
      const cred = await guard(createUserWithEmailAndPassword(auth, email.trim(), password));
      await guard(sendEmailVerification(cred.user));
      return toUser(cred.user);
    },
    async signOut() {
      await fbSignOut(auth);
    },
    isSuperadmin,
    async isOrganizer(eventId) {
      const u = await currentUser();
      if (!u || u.isAnonymous || !u.email || !u.emailVerified) return false;
      if (await isSuperadmin()) return true;
      try {
        return (await getDoc(doc(db, 'events', eventId, 'organizers', u.email.toLowerCase()))).exists();
      } catch {
        return false;
      }
    },

    // ---------- reading ----------
    async listEvents() {
      const snap = await guard(getDocs(collection(db, 'events')));
      return snap.docs.map((d) => mapEvent(d.id, d.data()));
    },
    watchEvent(eventId, cb, onError) {
      return onSnapshot(
        ev(eventId),
        (s) => cb(s.exists() ? mapEvent(s.id, s.data()) : null),
        (e) => onError?.(wrap(e)),
      );
    },
    watchRooms(eventId, cb) {
      return onSnapshot(collection(db, 'events', eventId, 'rooms'), (s) =>
        cb(s.docs.map((d) => mapRoom(d.id, d.data())).sort((a, b) => a.order - b.order || a.name.localeCompare(b.name))),
      );
    },
    watchTalks(eventId, cb) {
      return onSnapshot(collection(db, 'events', eventId, 'talks'), (s) =>
        cb(s.docs.map((d) => mapTalk(d.id, d.data())).sort((a, b) => a.start.getTime() - b.start.getTime())),
      );
    },
    watchQuestions(eventId, talkId, scope: QuestionScope, uid, cb, onError) {
      const col = questionsCol(eventId, talkId);
      const q =
        scope === 'public'
          ? query(col, where('status', 'in', VISIBLE_STATUSES))
          : scope === 'mine'
            ? query(col, where('authorUid', '==', uid ?? ''))
            : col;
      return onSnapshot(
        q,
        (s) => cb(s.docs.map((d) => mapQuestion(d.id, d.data({ serverTimestamps: 'estimate' })))),
        (e) => onError?.(wrap(e)),
      );
    },
    watchMyVotes(eventId, talkId, uid, cb) {
      const q = query(collection(db, 'events', eventId, 'talks', talkId, 'votes'), where('uid', '==', uid));
      return onSnapshot(q, (s) => cb(new Set(s.docs.map((d) => d.data().q as string))));
    },

    // ---------- attendee ----------
    async joinEvent(eventId, uid): Promise<Attendee> {
      const existing = await guard(getDoc(attendeeRef(eventId, uid)));
      if (existing.exists()) return { uid, pseudonym: existing.data().pseudonym };

      let preferred: string | null = null;
      try {
        const pref = await getDoc(doc(db, 'users', uid));
        if (pref.exists()) preferred = pref.data().pseudonym;
      } catch {
        /* no stored preference */
      }
      let name = preferred ?? generatePseudonym();
      for (let attempt = 0; attempt < 12; attempt++) {
        try {
          await claimAndSet(eventId, uid, name, null);
          if (!preferred) setDoc(doc(db, 'users', uid), { pseudonym: name }).catch(() => {});
          return { uid, pseudonym: name };
        } catch (e) {
          if (wrap(e).code !== 'denied') throw wrap(e);
          name = generatePseudonym(); // clash: reroll for this event only
        }
      }
      throw new BackendError('denied', 'could not claim a pseudonym');
    },
    async changePseudonym(eventId, uid, name) {
      const cur = await guard(getDoc(attendeeRef(eventId, uid)));
      if (!cur.exists()) throw new BackendError('not-found', 'join the event first');
      const existing: string = cur.data().pseudonym;
      if (name !== undefined) {
        const clean = cleanPseudonym(name);
        if (clean.length < 2) throw new BackendError('denied', 'name too short');
        await guard(claimAndSet(eventId, uid, clean, existing));
        setDoc(doc(db, 'users', uid), { pseudonym: clean }).catch(() => {});
        return clean;
      }
      for (let attempt = 0; attempt < 12; attempt++) {
        const candidate = generatePseudonym();
        try {
          await claimAndSet(eventId, uid, candidate, existing);
          setDoc(doc(db, 'users', uid), { pseudonym: candidate }).catch(() => {});
          return candidate;
        } catch (e) {
          if (wrap(e).code !== 'denied') throw wrap(e);
        }
      }
      throw new BackendError('denied', 'could not claim a pseudonym');
    },
    async ask({ eventId, talkId, uid, pseudonym, text, delivery, preApproval }) {
      const att = await guard(getDoc(attendeeRef(eventId, uid)));
      if (!att.exists()) throw new BackendError('not-found', 'join the event first');
      const next = (att.data().qCount as number) + 1;
      const b = writeBatch(db);
      b.set(questionRef(eventId, talkId, `${uid}_${next}`), {
        text,
        delivery,
        authorUid: uid,
        pseudonym,
        status: preApproval ? 'pending' : 'open',
        votes: 0,
        createdAt: serverTimestamp(),
      });
      b.update(attendeeRef(eventId, uid), { lastQuestionAt: serverTimestamp(), qCount: increment(1) });
      await guard(b.commit());
    },
    async vote(eventId, talkId, questionId, uid) {
      const b = writeBatch(db);
      b.set(voteRef(eventId, talkId, uid, questionId), { uid, q: questionId });
      b.update(questionRef(eventId, talkId, questionId), { votes: increment(1) });
      await guard(b.commit());
    },
    async unvote(eventId, talkId, questionId, uid) {
      const b = writeBatch(db);
      b.delete(voteRef(eventId, talkId, uid, questionId));
      b.update(questionRef(eventId, talkId, questionId), { votes: increment(-1) });
      await guard(b.commit());
    },
    async withdrawQuestion(eventId, talkId, questionId) {
      await guard(updateDoc(questionRef(eventId, talkId, questionId), { status: 'withdrawn' }));
    },
    async editQuestion(eventId, talkId, questionId, patch) {
      await guard(updateDoc(questionRef(eventId, talkId, questionId), { ...patch }));
    },

    // ---------- organizer ----------
    async setQuestionStatus(eventId, talkId, questionId, status: QuestionStatus) {
      await guard(updateDoc(questionRef(eventId, talkId, questionId), { status }));
    },
    async putOnStage(eventId, talkId, questionId, previousIds) {
      const b = writeBatch(db);
      for (const id of previousIds) {
        if (id !== questionId) b.update(questionRef(eventId, talkId, id), { status: 'answered' });
      }
      b.update(questionRef(eventId, talkId, questionId), { status: 'onstage' });
      await guard(b.commit());
    },
    async goLive(eventId, talkId, roomId) {
      const roomRef = doc(db, 'events', eventId, 'rooms', roomId);
      const room = await guard(getDoc(roomRef));
      const b = writeBatch(db);
      const liveId = room.exists() ? (room.data().liveTalkId as string | null) : null;
      if (liveId && liveId !== talkId) {
        b.update(talkRef(eventId, liveId), { state: 'ended', endedAt: serverTimestamp() });
      }
      b.update(talkRef(eventId, talkId), { state: 'live', questionsClosed: false });
      b.set(roomRef, { liveTalkId: talkId }, { merge: true });
      await guard(b.commit());
    },
    async endTalk(eventId, talkId, roomId) {
      const roomRef = doc(db, 'events', eventId, 'rooms', roomId);
      const room = await guard(getDoc(roomRef));
      const b = writeBatch(db);
      b.update(talkRef(eventId, talkId), { state: 'ended', endedAt: serverTimestamp() });
      if (room.exists() && room.data().liveTalkId === talkId) b.set(roomRef, { liveTalkId: null }, { merge: true });
      await guard(b.commit());
    },
    async setQuestionsClosed(eventId, talkId, closed) {
      await guard(updateDoc(talkRef(eventId, talkId), { questionsClosed: closed }));
    },
    async saveTalk(eventId, talk) {
      await guard(setDoc(talkRef(eventId, talk.id), talkToDoc(talk)));
    },
    async deleteTalk(eventId, talkId) {
      await guard(deleteDoc(talkRef(eventId, talkId)));
    },
    async saveRoom(eventId, room) {
      await guard(
        setDoc(doc(db, 'events', eventId, 'rooms', room.id), {
          name: room.name,
          liveTalkId: room.liveTalkId,
          order: room.order,
        }),
      );
    },
    async deleteRoom(eventId, roomId) {
      await guard(deleteDoc(doc(db, 'events', eventId, 'rooms', roomId)));
    },
    async saveEvent(event) {
      await guard(setDoc(ev(event.id), eventToDoc(event)));
    },
    async createEvent(event) {
      await guard(setDoc(ev(event.id), eventToDoc(event, serverTimestamp())));
    },
    async deleteEvent(eventId) {
      const talks = await guard(getDocs(collection(db, 'events', eventId, 'talks')));
      const ops: Array<(b: WriteBatch) => void> = [];
      for (const t of talks.docs) {
        const [qs, vs, secret] = await Promise.all([
          getDocs(questionsCol(eventId, t.id)),
          getDocs(collection(db, 'events', eventId, 'talks', t.id, 'votes')),
          getDoc(doc(db, 'events', eventId, 'talkSecrets', t.id)),
        ]);
        qs.docs.forEach((d) => ops.push((b) => b.delete(d.ref)));
        vs.docs.forEach((d) => ops.push((b) => b.delete(d.ref)));
        if (secret.exists()) {
          const key = secret.data().speakerKey as string;
          ops.push((b) => b.delete(doc(db, 'speakerKeys', key)));
          ops.push((b) => b.delete(secret.ref));
        }
        ops.push((b) => b.delete(t.ref));
      }
      for (const sub of ['rooms', 'attendees', 'pseudonyms'] as const) {
        const snap = await getDocs(collection(db, 'events', eventId, sub));
        snap.docs.forEach((d) => ops.push((b) => b.delete(d.ref)));
      }
      await guard(commitChunks(db, ops));
      await guard(deleteDoc(ev(eventId)));
      if (await isSuperadmin()) {
        const orgs = await getDocs(collection(db, 'events', eventId, 'organizers'));
        await commitChunks(
          db,
          orgs.docs.map((d) => (b: WriteBatch) => b.delete(d.ref)),
        );
      }
    },
    watchOrganizers(eventId, cb) {
      return onSnapshot(
        collection(db, 'events', eventId, 'organizers'),
        (s) => cb(s.docs.map((d) => d.id).sort()),
        () => cb([]),
      );
    },
    async addOrganizer(eventId, email) {
      const e = email.trim().toLowerCase();
      await guard(setDoc(doc(db, 'events', eventId, 'organizers', e), { addedAt: serverTimestamp() }));
    },
    async removeOrganizer(eventId, email) {
      await guard(deleteDoc(doc(db, 'events', eventId, 'organizers', email.toLowerCase())));
    },
    async importPlan(eventId, plan: ImportPlan) {
      const ops: Array<(b: WriteBatch) => void> = [];
      plan.rooms.forEach((r, i) =>
        ops.push((b) => b.set(doc(db, 'events', eventId, 'rooms', r.id), { name: r.name, order: i }, { merge: true })),
      );
      plan.talks.forEach((t) =>
        ops.push((b) =>
          b.set(talkRef(eventId, t.code), {
            title: t.title,
            speakers: t.speakers,
            roomId: t.room,
            start: Timestamp.fromDate(t.start),
            end: Timestamp.fromDate(t.end),
            state: 'scheduled',
            questionsClosed: false,
          }),
        ),
      );
      await guard(commitChunks(db, ops));
    },
    async speakerKey(eventId, talkId) {
      const secretRef = doc(db, 'events', eventId, 'talkSecrets', talkId);
      const snap = await guard(getDoc(secretRef));
      if (snap.exists()) return snap.data().speakerKey as string;
      const key = generateSpeakerKey();
      const b = writeBatch(db);
      b.set(doc(db, 'speakerKeys', key), { eventId, talkId });
      b.set(secretRef, { speakerKey: key });
      await guard(b.commit());
      return key;
    },
    async exportQuestions(eventId): Promise<ExportRow[]> {
      const talks = await guard(getDocs(collection(db, 'events', eventId, 'talks')));
      const rows: ExportRow[] = [];
      for (const t of talks.docs) {
        const qs = await getDocs(questionsCol(eventId, t.id));
        for (const q of qs.docs) {
          const d = mapQuestion(q.id, q.data());
          rows.push({
            talk: t.id,
            talkTitle: t.data().title,
            pseudonym: d.pseudonym,
            text: d.text,
            delivery: d.delivery,
            status: d.status,
            votes: d.votes,
            createdAt: d.createdAt,
            answer: d.answer?.text ?? '',
          });
        }
      }
      return rows.sort((a, b) => a.talk.localeCompare(b.talk) || a.createdAt.getTime() - b.createdAt.getTime());
    },
    async purgeIdentities(eventId) {
      const talks = await guard(getDocs(collection(db, 'events', eventId, 'talks')));
      const ops: Array<(b: WriteBatch) => void> = [];
      let n = 0;
      for (const t of talks.docs) {
        const [qs, vs] = await Promise.all([
          getDocs(questionsCol(eventId, t.id)),
          getDocs(collection(db, 'events', eventId, 'talks', t.id, 'votes')),
        ]);
        qs.docs.forEach((d) => {
          if (d.data().authorUid !== '') {
            n++;
            ops.push((b) => b.update(d.ref, { authorUid: '' }));
          }
        });
        vs.docs.forEach((d) => ops.push((b) => b.delete(d.ref)));
      }
      for (const sub of ['attendees', 'pseudonyms'] as const) {
        const snap = await getDocs(collection(db, 'events', eventId, sub));
        snap.docs.forEach((d) => ops.push((b) => b.delete(d.ref)));
      }
      await guard(commitChunks(db, ops));
      return n;
    },

    // ---------- speaker ----------
    async openSpeakerSession(uid, eventId, talkId, key) {
      await guard(setDoc(doc(db, 'speakerSessions', uid), { key, eventId, talkId }));
    },
    async answerQuestion(eventId, talkId, questionId, text) {
      await guard(updateDoc(questionRef(eventId, talkId, questionId), { answer: { text, at: serverTimestamp() } }));
    },
  };
  return backend;
}
