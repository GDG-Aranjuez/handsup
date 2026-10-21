# Deploying handsup

Everything below is a one-time setup per Firebase project. Nothing here has been done for you: it needs the GDG Aranjuez Google account.

## Current state (handsup-5f58e)

The production project is `handsup-5f58e` (owner `gdg.aranjuez@gmail.com`, Spark plan, Firestore `(default)` in `europe-west1`). Already done from the CLI:

- Firestore database, rules and indexes deployed (`cd firebase && firebase deploy --only firestore --project handsup-5f58e`).
- Web, Android and iOS apps registered; their public identifiers are committed in `web/.env.production`, `mobile/androidApp/build.gradle.kts` and `mobile/iosApp/Config.xcconfig` (no `google-services.json` / `GoogleService-Info.plist` is used).
- `config/superadmins`, the `fresonfest-2026` event, rooms, talks and organizers created with `firebase/bootstrap.mjs`.
- GitHub Pages enabled (source: GitHub Actions); `.github/workflows/deploy-web.yml` publishes `web/` on every push to `main`.

**Console steps the API cannot do** (Authentication is not initialised until the console button is pressed; Identity Platform's API needs billing):

1. [Authentication → Get started](https://console.firebase.google.com/project/handsup-5f58e/authentication).
2. Sign-in method: enable **Anonymous**, **Google** (the console creates the OAuth client) and **Email/Password**.
3. Settings → Authorized domains: add `gdg-aranjuez.github.io`.
4. For Google sign-in on Android: copy the *Web client ID* from the Google provider and build with `-Phandsup.googleWebClientId=…`.
5. Recommended: Google Cloud → APIs & Services → Credentials: restrict the Android/iOS API keys to the app ids.

## 1. Setting up another project (new event owner / self-owned instance)

1. Create a Firebase project, **Cloud Firestore in `europe-west1`**; do the console steps above.
2. Register the Web, Android and iOS apps and put their ids in the three files listed above.
3. `cd firebase && firebase deploy --only firestore --project <id>`
4. Bootstrap the data (superadmins are the only ones who can create events):
   ```sh
   gcloud auth application-default login
   node bootstrap.mjs --project <id> --superadmin you@gmail.com
   ```
   Or skip the sample event with `--no-event` and create it from `/admin`.

> **Quota.** On Spark, Firestore stops answering after 50 000 reads/day. A busy event day can exceed it ([ADR 0002](adr/0002-no-cloud-functions-rules-enforced-invariants.md)). Upgrading to Blaze keeps the same free allowance without the hard cap; set a budget alert. Runbook: [quota-exhausted](runbooks/quota-exhausted.md).

## 2. Web on GitHub Pages

`.github/workflows/deploy-web.yml` builds `web/` with `BASE_PATH=/handsup` and the production env file and publishes it. The app is at `https://gdg-aranjuez.github.io/handsup/`. A custom domain is a future step (see [future.md](future.md)).

## 3. Mobile

See [mobile.md](mobile.md): Android APK (sideloaded) and iOS (Xcode / internal TestFlight).

## 4. Day-of checklist

- [ ] Event, rooms and talks imported; talk codes match what is on the slides.
- [ ] `/e/<event>` opens on a phone on mobile data; the join QR scans.
- [ ] Stage screen (`/stage/<event>/<room>`) open on the room projector, fullscreen.
- [ ] Organizer devices signed in; one organizer tried *Go live → ask → On stage → Answered* on a spare talk.
- [ ] Billing alert / Blaze decision made ([runbook](runbooks/quota-exhausted.md)).
