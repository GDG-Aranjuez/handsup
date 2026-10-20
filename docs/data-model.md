# Data model

Firestore layout used by every client. The security rules (`firebase/firestore.rules`) enforce the invariants, because the project runs without Cloud Functions ([ADR 0002](adr/0002-no-cloud-functions-rules-enforced-invariants.md)). The rules are covered by `firebase/tests/rules.test.js`.

```
config/superadmins                      { emails: [...] }       written once, from the console
users/{uid}                             { pseudonym }           the attendee's preferred pseudonym
speakerKeys/{key}                       { eventId, talkId }     secret half of a speaker link (never readable)
speakerSessions/{uid}                   { key, eventId, talkId }  proves a speaker opened a valid link

events/{eventId}                        { name, branding{primary, accent, logo?, tagline?},
                                          settings{voting, defaultSort, preApproval, graceMin}, createdAt }
  organizers/{email}                    { addedAt }
  rooms/{roomId}                        { name, liveTalkId, order }
  talks/{code}                          { title, speakers, roomId, start, end, state, questionsClosed, endedAt? }
    questions/{uid}_{n}                 { text, delivery, authorUid, pseudonym, status, votes, createdAt, answer? }
    votes/{uid}_{questionId}            { uid, q }
  talkSecrets/{code}                    { speakerKey }          organizers only
  attendees/{uid}                       { pseudonym, lastQuestionAt, qCount }
  pseudonyms/{lower-case name}          { uid, name }           create-only claim: makes names unique per event
```

`eventId` is the URL slug (`^[a-z0-9-]{3,40}$`). A talk's document id **is** its Talk code (5 characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`).

## Invariants and how they are enforced

| Invariant | Enforcement |
| --- | --- |
| One question per attendee every 30 s | A question can only be created in the same batch that bumps `attendees/{uid}.lastQuestionAt` to `request.time`, and only if the previous value is older than 30 s |
| Two questions cannot share a batch | The question id must be `{uid}_{qCount}` where `qCount` is incremented by exactly one in the same batch |
| One vote per attendee per question | `votes/{uid}_{q}` must be created in the same batch as a `+1` on the counter (and deleted with a `-1`); the counter update requires the vote doc change |
| Pseudonyms are unique in an event | `pseudonyms/{name}` is create-only; a clash is a denied write, and the client re-rolls |
| Questions only while a talk is live (+ grace) | `windowOpen`: talk `live`, or `ended` within `settings.graceMin`, and not `questionsClosed` |
| Attendees see hidden/pending questions only if they wrote them | Read rule on `status` / `authorUid` |
| Organizers manage only their own events | `events/{e}/organizers/{email}` allow-list + verified e-mail; superadmins come from `config/superadmins` |
| Speaker links without accounts | The speaker opens the link, signs in anonymously and writes `speakerSessions/{uid}` with the key; the key is checked with `exists(speakerKeys/{key})` and never stored in a readable document. Answers are allowed only for follow-up questions of that talk |

## Question status

`pending` (pre-approval) → `open` → `onstage` → `answered`; also `followup`, `hidden` and `withdrawn`. Attendees see `open`, `onstage`, `answered` and `followup`.

## Not stored

No Cloud Storage: event logos are resized client-side to a small PNG and stored as a data URI in `branding.logo` (Storage needs the Blaze plan). No analytics.
