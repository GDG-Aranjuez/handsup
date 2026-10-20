import { base } from '#lib/paths';

const origin = () => (typeof location === 'undefined' ? '' : location.origin);

export const eventUrl = (eventId: string) => `${origin()}${base}/e/${eventId}`;
export const talkUrl = (eventId: string, code: string) => `${origin()}${base}/e/${eventId}/t/${code}`;
export const speakerUrl = (eventId: string, code: string, key: string) =>
  `${origin()}${base}/speaker/${eventId}/${code}/${key}`;
export const stageUrl = (eventId: string, roomId: string) => `${origin()}${base}/stage/${eventId}/${roomId}`;

export async function copy(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
