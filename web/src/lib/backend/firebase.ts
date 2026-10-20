import { initializeApp, type FirebaseApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth';
import {
  connectFirestoreEmulator,
  initializeFirestore,
  memoryLocalCache,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from 'firebase/firestore';

const env = import.meta.env;

export const USE_EMULATOR = env.VITE_USE_EMULATOR === 'true';
export const DEFAULT_EVENT: string = env.VITE_DEFAULT_EVENT ?? '';

let app: FirebaseApp | undefined;
let auth: Auth | undefined;
let db: Firestore | undefined;

export function firebase(): { app: FirebaseApp; auth: Auth; db: Firestore } {
  if (!app) {
    app = initializeApp({
      apiKey: env.VITE_FIREBASE_API_KEY || 'demo-key',
      authDomain: env.VITE_FIREBASE_AUTH_DOMAIN,
      projectId: env.VITE_FIREBASE_PROJECT_ID || 'handsup-demo',
      appId: env.VITE_FIREBASE_APP_ID,
    });
    auth = getAuth(app);
    let cache;
    try {
      // offline persistence: questions queue while the venue Wi-Fi drops
      cache = persistentLocalCache({ tabManager: persistentMultipleTabManager() });
    } catch {
      cache = memoryLocalCache();
    }
    db = initializeFirestore(app, { localCache: cache });
    if (USE_EMULATOR) {
      const host = location.hostname || '127.0.0.1';
      connectAuthEmulator(auth, `http://${host}:9099`, { disableWarnings: true });
      connectFirestoreEmulator(db, host, 8080);
    }
  }
  return { app, auth: auth!, db: db! };
}
