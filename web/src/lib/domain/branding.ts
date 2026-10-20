import type { Branding } from './types';

export function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

export function luminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

/** White or near-black, whichever reads better on `bg`. */
export function onColor(bg: string): string {
  return contrast(bg, '#ffffff') >= contrast(bg, '#111111') ? '#ffffff' : '#111111';
}

export function isHexColor(value: string): boolean {
  return /^#[0-9a-fA-F]{6}$/.test(value);
}

function mix(hex: string, other: string, amount: number): string {
  const a = hexToRgb(hex);
  const b = hexToRgb(other);
  const out = a.map((v, i) => Math.round(v + (b[i] - v) * amount));
  return '#' + out.map((v) => v.toString(16).padStart(2, '0')).join('');
}

/**
 * Darkens (light theme) or lightens (dark theme) a colour until it reaches 4.5:1
 * against the surface, so brand colours stay usable for text and outlines.
 */
export function readableOn(color: string, surface: string): string {
  const target = luminance(surface) > 0.5 ? '#000000' : '#ffffff';
  let out = color;
  for (let i = 0; i < 20 && contrast(out, surface) < 4.5; i++) out = mix(out, target, 0.1);
  return out;
}

/** CSS custom properties for a branding. Applied on the app root. */
export function brandingVars(b: Branding): Record<string, string> {
  return {
    '--brand': b.primary,
    '--on-brand': onColor(b.primary),
    '--brand-text-light': readableOn(b.primary, '#ffffff'),
    '--brand-text-dark': readableOn(b.primary, '#14141a'),
    '--accent': b.accent,
    '--on-accent': onColor(b.accent),
    '--accent-text-light': readableOn(b.accent, '#ffffff'),
    '--accent-text-dark': readableOn(b.accent, '#14141a'),
  };
}
