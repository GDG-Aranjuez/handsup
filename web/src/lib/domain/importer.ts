import { isValidTalkCode, uniqueTalkCode } from './talkCode';

export interface ImportRoom {
  id: string;
  name: string;
}
export interface ImportTalk {
  code: string;
  title: string;
  speakers: string;
  room: string;
  start: Date;
  end: Date;
}
export interface ImportPlan {
  rooms: ImportRoom[];
  talks: ImportTalk[];
}

/**
 * Schema (docs/import-schema.md):
 * { "rooms": [{ "id": "main", "name": "Sala principal" }],
 *   "talks": [{ "code"?: "ABCDE", "title": "…", "speakers": "…", "room": "main",
 *               "start": "2026-10-24T10:00:00+02:00", "end": "2026-10-24T10:45:00+02:00" }] }
 */
export function parseImport(raw: unknown, existingCodes: Iterable<string> = []): ImportPlan {
  if (typeof raw !== 'object' || raw === null) throw new Error('El JSON debe ser un objeto');
  const obj = raw as Record<string, unknown>;
  if (!Array.isArray(obj.rooms) || obj.rooms.length === 0) throw new Error('"rooms" debe ser una lista con al menos una sala');
  if (!Array.isArray(obj.talks)) throw new Error('"talks" debe ser una lista');

  const rooms: ImportRoom[] = obj.rooms.map((r, i) => {
    const room = r as Record<string, unknown>;
    if (typeof room?.id !== 'string' || !/^[a-z0-9-]{1,30}$/.test(room.id)) throw new Error(`rooms[${i}].id inválido (a-z, 0-9, guion)`);
    if (typeof room.name !== 'string' || !room.name.trim()) throw new Error(`rooms[${i}].name requerido`);
    return { id: room.id, name: room.name.trim().slice(0, 60) };
  });
  const roomIds = new Set(rooms.map((r) => r.id));

  const taken = new Set<string>(existingCodes);
  const explicit = obj.talks
    .map((t) => (t as Record<string, unknown>)?.code)
    .filter((c): c is string => typeof c === 'string');
  explicit.forEach((c) => taken.add(c));

  const seen = new Set<string>();
  const talks: ImportTalk[] = obj.talks.map((t, i) => {
    const talk = t as Record<string, unknown>;
    if (typeof talk?.title !== 'string' || !talk.title.trim()) throw new Error(`talks[${i}].title requerido`);
    if (typeof talk.room !== 'string' || !roomIds.has(talk.room)) throw new Error(`talks[${i}].room desconocida`);
    const start = new Date(String(talk.start));
    const end = new Date(String(talk.end));
    if (Number.isNaN(start.getTime())) throw new Error(`talks[${i}].start inválido`);
    if (Number.isNaN(end.getTime())) throw new Error(`talks[${i}].end inválido`);
    if (end <= start) throw new Error(`talks[${i}] termina antes de empezar`);
    let code: string;
    if (talk.code === undefined) {
      code = uniqueTalkCode(taken);
    } else {
      if (typeof talk.code !== 'string' || !isValidTalkCode(talk.code)) throw new Error(`talks[${i}].code inválido`);
      if (seen.has(talk.code)) throw new Error(`talks[${i}].code repetido`);
      code = talk.code;
    }
    seen.add(code);
    return {
      code,
      title: talk.title.trim().slice(0, 160),
      speakers: typeof talk.speakers === 'string' ? talk.speakers.trim().slice(0, 200) : '',
      room: talk.room,
      start,
      end,
    };
  });
  return { rooms, talks };
}
