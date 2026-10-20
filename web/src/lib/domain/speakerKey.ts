const CHARS = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** 32 random characters from a CSPRNG; the secret half of a speaker link. */
export function generateSpeakerKey(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => CHARS[b % CHARS.length]).join('');
}
