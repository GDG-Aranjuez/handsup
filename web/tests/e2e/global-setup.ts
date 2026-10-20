import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Fresh emulator state for every run.
export default function globalSetup() {
  const cwd = fileURLToPath(new URL('../../../firebase', import.meta.url));
  execSync('node seed.mjs --reset', { cwd, stdio: 'inherit' });
}
