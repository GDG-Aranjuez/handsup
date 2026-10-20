const FIRST = [
  'Marie', 'Albert', 'Ada', 'Alan', 'Grace', 'Isaac', 'Nikola', 'Rosalind', 'Katherine', 'Linus',
  'Margaret', 'Richard', 'Hedy', 'Carl', 'Tim', 'Dennis', 'Barbara', 'Galileo', 'Stephen', 'Niels',
  'Emmy', 'Johannes', 'Charles', 'Lise', 'Jocelyn', 'Donald', 'Radia', 'Vint', 'Frances', 'Edsger',
  'Mary', 'Michael', 'Santiago', 'Margarita', 'Ramon', 'Severo', 'Claude', 'John', 'Anita', 'Guido',
];
const LAST = [
  'Curie', 'Einstein', 'Lovelace', 'Turing', 'Hopper', 'Newton', 'Tesla', 'Franklin', 'Johnson',
  'Torvalds', 'Hamilton', 'Feynman', 'Lamarr', 'Sagan', 'Berners-Lee', 'Ritchie', 'Liskov', 'Galilei',
  'Hawking', 'Bohr', 'Noether', 'Kepler', 'Darwin', 'Meitner', 'Bell', 'Knuth', 'Babbage', 'Faraday',
  'Maxwell', 'Pasteur', 'Planck', 'Hubble', 'Goodall', 'Perlman', 'Cerf', 'Allen', 'Dijkstra',
  'Cajal', 'Ochoa', 'Salas', 'Shannon', 'McCarthy', 'Borg', 'Rossum', 'Hertz', 'Volta', 'Ampere',
];

export const PSEUDONYM_COMBINATIONS = FIRST.length * LAST.length;

/** A random "First Last" mixing famous science and technology names, e.g. "Marie Einstein". */
export function generatePseudonym(random: () => number = Math.random): string {
  const f = FIRST[Math.floor(random() * FIRST.length)];
  const l = LAST[Math.floor(random() * LAST.length)];
  return `${f} ${l}`;
}

/** Normalised form used as the uniqueness key inside an Event. Must match the rules (`name.lower()`). */
export function pseudonymKey(name: string): string {
  return name.toLowerCase();
}

export function cleanPseudonym(input: string): string {
  return input.replace(/[\/\n\r\t]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 30);
}

export function isValidPseudonym(name: string): boolean {
  return name.length >= 2 && name.length <= 30 && !name.includes('/');
}
