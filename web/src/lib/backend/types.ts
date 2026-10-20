import type {
  Attendee,
  Delivery,
  EventDoc,
  Question,
  QuestionStatus,
  Room,
  Talk,
} from '../domain/types';
import type { ImportPlan } from '../domain/importer';

export type Unsub = () => void;

export interface AuthUser {
  uid: string;
  email: string | null;
  emailVerified: boolean;
  isAnonymous: boolean;
}

export type QuestionScope = 'public' | 'mine' | 'all';

export interface ExportRow {
  talk: string;
  talkTitle: string;
  pseudonym: string;
  text: string;
  delivery: Delivery;
  status: QuestionStatus;
  votes: number;
  createdAt: Date;
  answer: string;
}

/**
 * Everything the UI needs from a backend. Firestore is the first implementation
 * (ADR 0001); a self-hosted server can implement the same contract later.
 * Permission failures surface as `BackendError` with `code: 'denied'`.
 */
export interface QaBackend {
  // --- auth ---
  onAuth(cb: (user: AuthUser | null) => void): Unsub;
  ensureAnonymous(): Promise<AuthUser>;
  signInGoogle(): Promise<AuthUser>;
  linkGoogle(): Promise<AuthUser>;
  signInEmail(email: string, password: string): Promise<AuthUser>;
  /** Creates the account and sends a verification e-mail; the rules need a verified e-mail. */
  registerEmail(email: string, password: string): Promise<AuthUser>;
  signOut(): Promise<void>;
  isSuperadmin(): Promise<boolean>;
  isOrganizer(eventId: string): Promise<boolean>;

  // --- reading ---
  listEvents(): Promise<EventDoc[]>;
  watchEvent(eventId: string, cb: (e: EventDoc | null) => void, onError?: (e: Error) => void): Unsub;
  watchRooms(eventId: string, cb: (r: Room[]) => void): Unsub;
  watchTalks(eventId: string, cb: (t: Talk[]) => void): Unsub;
  watchQuestions(
    eventId: string,
    talkId: string,
    scope: QuestionScope,
    uid: string | null,
    cb: (q: Question[]) => void,
    onError?: (e: Error) => void,
  ): Unsub;
  watchMyVotes(eventId: string, talkId: string, uid: string, cb: (ids: Set<string>) => void): Unsub;

  // --- attendee ---
  /** Claims a pseudonym for this event (rerolling on a clash) and returns the attendee profile. */
  joinEvent(eventId: string, uid: string): Promise<Attendee>;
  /** Replaces the pseudonym with `name`, or a random one. Returns the new name. */
  changePseudonym(eventId: string, uid: string, name?: string): Promise<string>;
  ask(args: {
    eventId: string;
    talkId: string;
    uid: string;
    pseudonym: string;
    text: string;
    delivery: Delivery;
    preApproval: boolean;
  }): Promise<void>;
  vote(eventId: string, talkId: string, questionId: string, uid: string): Promise<void>;
  unvote(eventId: string, talkId: string, questionId: string, uid: string): Promise<void>;
  withdrawQuestion(eventId: string, talkId: string, questionId: string): Promise<void>;
  editQuestion(
    eventId: string,
    talkId: string,
    questionId: string,
    patch: { text?: string; delivery?: Delivery },
  ): Promise<void>;

  // --- organizer ---
  setQuestionStatus(eventId: string, talkId: string, questionId: string, status: QuestionStatus): Promise<void>;
  /** Moves a question on stage and marks the previously on-stage ones answered. */
  putOnStage(eventId: string, talkId: string, questionId: string, previousIds: string[]): Promise<void>;
  goLive(eventId: string, talkId: string, roomId: string): Promise<void>;
  endTalk(eventId: string, talkId: string, roomId: string): Promise<void>;
  setQuestionsClosed(eventId: string, talkId: string, closed: boolean): Promise<void>;
  saveTalk(eventId: string, talk: Talk): Promise<void>;
  deleteTalk(eventId: string, talkId: string): Promise<void>;
  saveRoom(eventId: string, room: Room): Promise<void>;
  deleteRoom(eventId: string, roomId: string): Promise<void>;
  saveEvent(event: EventDoc): Promise<void>;
  createEvent(event: EventDoc): Promise<void>;
  deleteEvent(eventId: string): Promise<void>;
  watchOrganizers(eventId: string, cb: (emails: string[]) => void): Unsub;
  addOrganizer(eventId: string, email: string): Promise<void>;
  removeOrganizer(eventId: string, email: string): Promise<void>;
  importPlan(eventId: string, plan: ImportPlan): Promise<void>;
  /** Returns the speaker link secret for a talk, creating it if needed. */
  speakerKey(eventId: string, talkId: string): Promise<string>;
  exportQuestions(eventId: string): Promise<ExportRow[]>;
  /** Removes attendee identifiers (uids, votes, profiles, pseudonym claims). Keeps pseudonym + text. */
  purgeIdentities(eventId: string): Promise<number>;

  // --- speaker ---
  openSpeakerSession(uid: string, eventId: string, talkId: string, key: string): Promise<void>;
  answerQuestion(eventId: string, talkId: string, questionId: string, text: string): Promise<void>;
}

export class BackendError extends Error {
  constructor(
    public code: 'denied' | 'offline' | 'not-found' | 'unknown',
    message: string,
  ) {
    super(message);
  }
}
