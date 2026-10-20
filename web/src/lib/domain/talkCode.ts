// No 0/O/1/I/L: codes are typed and read aloud. Must match the rules' talk id pattern.
export const TALK_CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
export const TALK_CODE_LENGTH = 5;

export function generateTalkCode(random: () => number = Math.random): string {
  let out = '';
  for (let i = 0; i < TALK_CODE_LENGTH; i++) {
    out += TALK_CODE_ALPHABET[Math.floor(random() * TALK_CODE_ALPHABET.length)];
  }
  return out;
}

/** Uppercases and strips anything that is not a letter or digit. */
export function normalizeTalkCode(input: string): string {
  return input
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, TALK_CODE_LENGTH);
}

export function isValidTalkCode(code: string): boolean {
  return code.length === TALK_CODE_LENGTH && [...code].every((c) => TALK_CODE_ALPHABET.includes(c));
}

/** Returns a code not in `taken`, and records it there. */
export function uniqueTalkCode(taken: Set<string>, random: () => number = Math.random): string {
  for (let i = 0; i < 1000; i++) {
    const code = generateTalkCode(random);
    if (!taken.has(code)) {
      taken.add(code);
      return code;
    }
  }
  throw new Error('could not generate a unique talk code');
}
