# No Cloud Functions; invariants live in Firestore security rules

The project stays on the Firebase Spark plan with no Cloud Functions, so every invariant is enforced by security rules plus client behaviour: the rate limit, one Vote per Attendee, Pseudonym uniqueness, Organizer isolation between Events and the Speaker link. The cost is complex, heavily tested rules and no server-side hooks, so LLM moderation, scheduled purges and push notifications wait until Functions exist. Spark also hard-caps Firestore at 50K reads per day, which the event-day load will probably exceed. We accepted that risk; the mitigation is moving to Blaze with a budget alert, still without Functions (see `docs/runbooks/quota-exhausted.md`).

## Considered Options

- **Blaze with Functions**: simpler invariants, but not chosen for the first event.
- **Spark with fewer live listeners**: lower read usage, but a less live feel.
