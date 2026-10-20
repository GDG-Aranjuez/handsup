import { getContext, setContext } from 'svelte';
import { backend, type Unsub } from './backend';
import type { EventDoc, Room, Talk } from './domain/types';

/** Live view of one Event (document, rooms, talks) shared by every page under it. */
export class EventContext {
  event = $state<EventDoc | null>(null);
  rooms = $state<Room[]>([]);
  talks = $state<Talk[]>([]);
  loaded = $state(false);
  error = $state<string | null>(null);
  #unsubs: Unsub[] = [];

  id: string;
  constructor(id: string) {
    this.id = id;
  }

  reset(id: string): void {
    this.stop();
    this.id = id;
    this.event = null;
    this.rooms = [];
    this.talks = [];
    this.loaded = false;
    this.error = null;
  }

  start(): void {
    this.stop();
    const b = backend();
    this.#unsubs = [
      b.watchEvent(
        this.id,
        (e) => {
          this.event = e;
          this.loaded = true;
        },
        (err) => {
          this.error = err.message;
          this.loaded = true;
        },
      ),
      b.watchRooms(this.id, (r) => (this.rooms = r)),
      b.watchTalks(this.id, (t) => (this.talks = t)),
    ];
  }

  stop(): void {
    this.#unsubs.forEach((u) => u());
    this.#unsubs = [];
  }

  talk(code: string): Talk | undefined {
    return this.talks.find((t) => t.id === code);
  }
  room(id: string): Room | undefined {
    return this.rooms.find((r) => r.id === id);
  }
  get liveTalks(): Talk[] {
    return this.talks.filter((t) => t.state === 'live');
  }
}

const KEY = Symbol('event-context');
export const setEventContext = (c: EventContext) => setContext(KEY, c);
export const getEventContext = () => getContext<EventContext>(KEY);

/** The Talk accepts questions and votes: live, or ended within the grace period. */
export function windowOpen(talk: Talk, graceMin: number, now = Date.now()): boolean {
  if (talk.questionsClosed) return false;
  if (talk.state === 'live') return true;
  if (talk.state === 'ended' && talk.endedAt) return now < talk.endedAt.getTime() + graceMin * 60_000;
  return false;
}
