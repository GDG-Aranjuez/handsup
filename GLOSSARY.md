# handsup

Live Q&A for conference talks. Attendees ask and upvote Questions; organizers run a live queue and see whether each asker wants to speak or have the host read it out.

## Language

### People

**Attendee**:
Anyone in the audience who asks or votes. Anonymous by default.
_Avoid_: User, participant, guest

**Pseudonym**:
The public name of an Attendee, made from the first and last names of famous science and technology people (e.g. "Marie Einstein"). Stable for the Attendee; replaced for one Event if it is already taken there.
_Avoid_: Nickname, username, alias

**Organizer**:
A person invited to run one Event: setup, branding and live moderation.
_Avoid_: Admin, moderator, host

**Superadmin**:
The owner of the instance. Creates Events and invites Organizers.
_Avoid_: Root, owner

**Partner**:
An outside community whose Organizers run an Event on the instance. Not a role in itself.
_Avoid_: Tenant, client

**Host**:
The person on stage who reads Questions aloud and moderates the room during a Talk.
_Avoid_: Moderator, MC

**Speaker**:
The person giving a Talk.
_Avoid_: Presenter, ponente

### Event structure

**Event**:
One conference edition with its own Rooms, Talks, branding and settings.
_Avoid_: Conference, edition

**Room**:
A physical space where Talks happen. At most one Talk is Live in a Room at a time.
_Avoid_: Track, stage, hall

**Talk**:
A scheduled session in a Room that can receive Questions.
_Avoid_: Session, presentation

**Talk code**:
A short typeable code that identifies a Talk.
_Avoid_: PIN, join code

**Live**:
The state of a Talk that is currently open for Questions in its Room. Talks move Scheduled → Live → Ended.
_Avoid_: Active, current

**Grace period**:
The time after a Talk ends during which Questions and votes are still accepted.
_Avoid_: Cooldown, buffer

**Stage screen**:
The read-only display for the room that shows how to join and the Question that is On stage.
_Avoid_: Projector view, big screen

### Questions

**Question**:
A short text an Attendee submits to a Talk.
_Avoid_: Ask, comment, query

**Delivery**:
How an Attendee wants their Question handled: **Speak** (the asker takes the mic) or **Read** (the Host reads it aloud).
_Avoid_: Mode, preference

**Vote**:
An Attendee's single upvote on a Question.
_Avoid_: Like, upvote count

**On stage**:
The Question the Host is dealing with right now. Only one per Room.
_Avoid_: Pinned, current

**Answered**:
A Question that has been dealt with during the Talk.
_Avoid_: Done, closed

**Hidden**:
A Question removed from Attendee view by an Organizer.
_Avoid_: Deleted, blocked

**Follow-up**:
A Question that was not reached live and is passed to the Speaker to answer in writing.
_Avoid_: Backlog, leftover

**Answer**:
The Speaker's written reply to a Follow-up Question.
_Avoid_: Response, reply

**Speaker link**:
A private link that lets a Speaker write Answers for one Talk without an account.
_Avoid_: Magic link, invite
