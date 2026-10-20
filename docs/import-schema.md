# Talk import (JSON)

Used by the *Setup → Data → Import* screen (web and mobile) and produced by `tools/agenda-to-json`.

```json
{
  "rooms": [
    { "id": "auditorio", "name": "Auditorio" },
    { "id": "sala-17", "name": "Sala 17" }
  ],
  "talks": [
    {
      "code": "A4EEQ",
      "title": "La primera misión: conseguir tu trabajo en tecnología",
      "speakers": "Arantxa Delgado Ruiz",
      "room": "auditorio",
      "start": "2026-10-24T10:00:00+02:00",
      "end": "2026-10-24T10:45:00+02:00"
    }
  ]
}
```

| Field | Rule |
| --- | --- |
| `rooms[].id` | `a-z`, `0-9`, `-`, up to 30 characters. An existing room keeps its live talk |
| `rooms[].name` | required, up to 60 characters |
| `talks[].code` | optional. Five characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`. Generated (unique within the event) when missing |
| `talks[].title` | required, up to 160 characters |
| `talks[].speakers` | optional free text, up to 200 characters |
| `talks[].room` | must be one of the `rooms[].id` in the same file |
| `talks[].start`, `end` | ISO-8601 **with an offset**; `end` must be after `start` |

Importing a talk whose code already exists overwrites it (state resets to *scheduled*).

## Converting the FresON website agenda

```sh
node tools/agenda-to-json/index.mjs ../freson-fest > firebase/seed/fresonfest-2026.talks.json
```

Networking sessions (breakfast, lunch…) are skipped. Codes are derived from the session id, so running the tool again gives the same codes — safe once the codes have been printed on slides.
