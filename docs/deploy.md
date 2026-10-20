# Deploying handsup

Everything below is a one-time setup per Firebase project. Nothing here has been done for you: it needs the GDG Aranjuez Google account.

## 1. Firebase project (Spark plan)

1. Create a project in the [Firebase console](https://console.firebase.google.com) (e.g. `gdg-handsup`), **Cloud Firestore in `eur3` / `europe-west1`**.
2. Authentication → Sign-in method: enable **Anonymous**, **Google** and (optional) **Email/Password**. Under *Settings → Authorized domains* add `gdg-aranjuez.github.io`.
3. Project settings → Your apps: add a **Web** app, an **Android** app (`es.gdgaranjuez.handsup`) and an **iOS** app (`es.gdgaranjuez.handsup`). You only need the *values* (API key, app ids, project id); no `google-services.json` / `GoogleService-Info.plist` is used.
4. Publish the rules and indexes:
   ```sh
   cd firebase
   firebase deploy --only firestore --project <project-id>
   ```
5. Create the superadmin list once, in the console (Firestore → *Start collection*): collection `config`, document `superadmins`, field `emails` (array) = `["you@gmail.com"]`. Rules never let a client write it.
6. Sign in once on `/admin` with that Google account → you are superadmin → create the event, add organizers, import the talks (`firebase/seed/fresonfest-2026.talks.json`).

> **Quota.** On Spark, Firestore stops answering after 50 000 reads/day. A busy event day can exceed it ([ADR 0002](adr/0002-no-cloud-functions-rules-enforced-invariants.md)). Upgrading to Blaze keeps the same free allowance without the hard cap; set a budget alert. Runbook: [quota-exhausted](runbooks/quota-exhausted.md).

## 2. Web on GitHub Pages

The workflow `.github/workflows/deploy-web.yml` builds `web/` with `BASE_PATH=/handsup` and publishes it to Pages.

1. Repository settings → Pages → *Source: GitHub Actions*.
2. Repository settings → Variables → Actions, add: `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_APP_ID`, `VITE_DEFAULT_EVENT` (e.g. `fresonfest-2026`). These are public identifiers, not secrets.
3. Push to `main` (or run the workflow). The app is served at `https://gdg-aranjuez.github.io/handsup/`.

A custom domain is a future step (see [future.md](future.md)).

## 3. Mobile

See [mobile.md](mobile.md): Android APK (sideloaded) and iOS (Xcode / internal TestFlight).

## 4. Day-of checklist

- [ ] Event, rooms and talks imported; talk codes match what is on the slides.
- [ ] `/e/<event>` opens on a phone on mobile data; the join QR scans.
- [ ] Stage screen (`/stage/<event>/<room>`) open on the room projector, fullscreen.
- [ ] Organizer devices signed in; one organizer tried *Go live → ask → On stage → Answered* on a spare talk.
- [ ] Billing alert / Blaze decision made ([runbook](runbooks/quota-exhausted.md)).
