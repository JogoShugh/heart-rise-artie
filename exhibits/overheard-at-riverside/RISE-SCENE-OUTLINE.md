# Overheard at Riverside, scene seven: "Tell me when" (outline)

Draft for the owner's review, 2026-11-11. Nothing in the deck has changed yet.

## Where it goes

- After scene six (a month later), before Closing: two or three slides.
- Same conventions as the other scenes:
  - two voices (Conversation, Points), each scene slide with its preamble;
  - code read from its file;
  - screens and numbers taken from real runs, never typed by hand.

## The story, in beats

1. **The Hobbit, again.** Ana still can't renew *The Hobbit*: a hold is waiting. She doesn't want to keep
   checking. Marcus: "Ask it to tell you."
2. **The page offers it.** The library's entry, in a browser, offers *Tell me when renewable*: the loan, where to
   answer (her reply) and her key, as links.
   - **Screenshot:** heart-core's HTML of the entry's form.
3. **Her reply is a page with controls too.** Ana's reply, in a browser: awaiting, with the forms the library may
   use on it (Report progress, Report completed, Report failed). Each is a PATCH of the reply itself.
   - **Screenshots:** heart-core's HTML of the reply while it awaits, then after the answer, with no forms and the
     reason.
4. **The agents do it by HAL Schema Forms.**
   - Sam's assistant (ARTIE) asks on Ana's behalf, signed with her key; the library answers 201 with a watch.
   - Later, the library's side answers on her reply: progress, then the result when the hold clears, each signed
     and naming the watch it answers.
   - Ana's phone shows "renewable from December 3".
   - From one real ARTIE run: the calls, its refusals that teach (401 with Accept-Signature), and the numbers.
5. **What it shows.**
   - The same form-driven web runs in both directions.
   - The answer lands on a resource that carries its own controls.
   - Who may change it is proved by signature, by keys found through links.
   - Ana can cancel while it waits; after the answer, the watch says why not.

## What has to be built first

| Piece | Why | Size |
|---|---|---|
| **ARTIE signs its acts** (RFC 9421, Ed25519, with Content-Digest) when given a key, and reads Accept-Signature from a 401 | Every RISE act is signed now. Without it, ARTIE can read but not ask or answer. | Medium: client/artie, red first |
| **The simulator serves Ana's replies** beside the library, each finding the other's keys through links | Browser screenshots, and ARTIE runs against processes | Small: heart-sims test fixtures |
| **Screens** from headless Chrome against the running services | Beats 2 and 3 | Small |
| **One ARTIE run per side**, kept as traces, numbers on the slides | Beat 4 | Small once ARTIE signs |

## Decisions for the owner

1. **Where Ana's reply lives in the fiction:** her phone's library app, a household inbox, or Sam's assistant?
   In the code it is "Ana's replies", a HEART service of her own.
2. **Who answers for the library:** a second ARTIE run, as the library's own automation (Priya), or the library
   service itself, with no agent? The first shows ARTIE on both sides; the second is closer to how a library
   would run it.
3. **ARTIE's key:** from a file named by an environment variable (as the token is), or from a key set the run is
   given?
4. **Slides:** two (the scene, then the agents) or three (adding the signatures and refusals)?
