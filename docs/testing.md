# Testing

| Layer | Command | What it covers |
| --- | --- | --- |
| Firestore rules | `cd firebase && pnpm test` | 60+ cases against the emulator: roles, rate limit, one vote, pseudonym claims, grace period, speaker link, organizer isolation |
| Web domain + i18n | `cd web && pnpm test` | ordering, codes, pseudonyms, branding contrast, import, CSV, both languages have every key |
| Web types | `cd web && pnpm check` | `svelte-check`, strict |
| Web end to end | `cd web && pnpm test:e2e` | attendee, organizer, stage, speaker and setup flows on a mobile viewport; needs the emulators and a seed (below) |
| Kotlin | `cd mobile && ./gradlew :domain:jvmTest :domain:iosSimulatorArm64Test` | the same domain behaviour on JVM and iOS |

Local emulators for e2e and manual testing:

```sh
cd firebase
pnpm install
pnpm emulators                  # terminal 1: Auth :9099, Firestore :8080, UI :4000
pnpm seed --reset               # terminal 2: FresON Fest 2026 + users
cd ../web && pnpm dev           # http://localhost:5173
```

Seed users (password `handsup-test`): `organizer@handsup.test` (organizer of FresON), `superadmin@handsup.test`.

## Manual rehearsal (before the event)

1. Three phones: iPhone (web), Android (web), Android (APK). One laptop as admin, one screen on the *stage* URL.
2. Throttle one phone to a slow 3G profile and ask, vote and refresh.
3. Switch a phone to airplane mode, ask a question, reconnect: it must be delivered once.
