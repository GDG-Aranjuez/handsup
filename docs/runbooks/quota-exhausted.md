# Runbook: Firestore quota exhausted on event day

**Symptom**: Questions stop loading or submitting; clients show permission or resource-exhausted errors. Spark allows 50K reads and 20K writes per day.

**Fix** (takes a few minutes):

1. Open the Firebase console for the project → Usage and billing → upgrade to **Blaze**.
2. Set a budget alert at €5 in Google Cloud Billing.
3. Reload an admin client and submit a test Question to confirm recovery.

Do not deploy Cloud Functions as part of this fix; see ADR 0002.
