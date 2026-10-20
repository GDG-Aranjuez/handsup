# handsup

Live Q&A for conference talks. Attendees scan a QR code and ask questions during a talk — with a made-up scientist name (“Marie Einstein”) instead of an account — and vote the ones they care about. Organizers run a live queue and see, for each question, whether the asker wants to **speak** or have the **host read** it aloud. Built for [FresON Fest 2026](https://fresonfest.gdg-aranjuez.com) by GDG Aranjuez; generic enough for any event, with per-event branding.

| | |
| --- | --- |
| **Attendees** | web app (no install, PWA) · Android / iOS app (Kotlin Multiplatform) |
| **Organizers** | same apps: live queue, talk control, event setup, room screen, speaker links |
| **Backend** | Firebase (Firestore + Auth) with security rules, no Cloud Functions; a self-hosted server is a planned second implementation ([ADR 0001](docs/adr/0001-backend-contract-firebase-first-selfhost-later.md)) |

## How a talk runs

1. The organizer opens the event, taps **Go live** on a talk. The room screen (`/stage/<event>/<room>`) shows the join QR and the talk code.
2. Attendees open the event link: the live talk opens by itself. They write a question (280 characters), choose **“I will ask it”** or **“Host reads it”**, and send. Questions are visible immediately; others vote.
3. The organizer's queue lists questions by votes (or time) with the delivery choice on each one. **Put on stage** shows it on the room screen; **Answered** moves it on. Anything not reached can go to **Follow-up**: the speaker gets a private link to write answers, shown under the question.
4. **End** the talk; questions stay open for a grace period unless the organizer closes them.

Vocabulary is defined in [GLOSSARY.md](GLOSSARY.md).

## Repository

```
web/        SvelteKit + Tailwind app (attendee, organizer, stage, speaker pages)
mobile/     Kotlin Multiplatform: domain/, composeApp/, androidApp/, iosApp/
firebase/   Firestore rules + emulator tests, seed script, emulator config
tools/      agenda-to-json (FresON agenda → import file), gen-kotlin-strings
docs/       data model, import format, deploy, mobile, testing, ADRs, runbooks
```

## Try it locally

```sh
# terminal 1 — Firebase emulators (Auth + Firestore)
cd firebase && pnpm install && pnpm emulators

# terminal 2 — seed FresON Fest 2026 (12 talks, 2 rooms) and two users
cd firebase && pnpm seed --reset

# terminal 3 — web app
cd web && pnpm install && pnpm dev          # http://localhost:5173
```

Organizer sign-in on `/admin` shows an emulator form: `organizer@handsup.test` / `handsup-test` (or `superadmin@handsup.test`). Take a talk live from the dashboard, then open `http://localhost:5173/` in another browser profile as an attendee.

Mobile: see [docs/mobile.md](docs/mobile.md).

## Tests and CI

`cd firebase && pnpm test` (rules) · `cd web && pnpm check && pnpm test && pnpm test:e2e` · `cd mobile && ./gradlew :domain:jvmTest`. Details in [docs/testing.md](docs/testing.md). CI runs on every push (Linux only; the iOS build is a manual workflow).

## Deploying

[docs/deploy.md](docs/deploy.md): Firebase project, rules, GitHub Pages, day-of checklist. Known risks and future steps: [docs/future.md](docs/future.md), [quota runbook](docs/runbooks/quota-exhausted.md).

## License

MIT — see [LICENSE](LICENSE).
