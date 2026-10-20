import { createFirestoreBackend } from './firestore';
import type { QaBackend } from './types';

let instance: QaBackend | undefined;

/** The backend used by the app. Swap the factory here to use another implementation. */
export function backend(): QaBackend {
  return (instance ??= createFirestoreBackend());
}

export * from './types';
