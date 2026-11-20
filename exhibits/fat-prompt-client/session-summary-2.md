# ARTIE Session Summary — Live Game Feed

## Highlights: Uniform Interface in action

- **Fielding's "no out-of-band knowledge" goal, satisfied live:**
  - ARTIE knew nothing about "games," "periods," "possession," or
    "turnovers" beforehand — only the bookmark `http://localhost:3000`.
  - Every field, enum value, and legal next action came from `_forms`/
    `_links` in the response actually in hand.
  - The legal-action set visibly changed as game state changed:
    `pauseGame`/`recordScore`/`turnover` → collapsed to just `resumeGame`
    the instant the game paused, then expanded back.

- **Uniform interface, not domain interface:**
  - ARTIE is coupled to exactly one thing: the H.E.A.R.T. envelope, itself
    built on [HAL Schema Forms](https://github.com/jbadeau/hal-schema-forms)
    — a HAL extension, but a distinct one from the separate HAL-FORMS spec.
  - It is not coupled, even slightly, to "basketball."
  - Swap this server for the garden-bed one from last session and nothing
    about ARTIE's logic changes — only the `_links`/`_forms` payloads do.

- **Direct correlate to web browsers:**
  - Same shape Chrome or Firefox has run on for 30+ years: a browser has no
    OpenAPI spec or schema file for your bank, your blog, or your shopping
    cart.
  - It's coupled only to a small fixed set of media types (`text/html`,
    `image/jpeg`, etc.) and the generic hypermedia controls those media
    types define (`<a>`, `<form>`).
  - It renders and drives all of them identically without knowing or caring
    what domain it's looking at.
  - ARTIE is that same browser, just retargeted at `application/hal+json` +
    H.E.A.R.T.'s forms convention instead of HTML.

---

## What happened

- **Cold sync `GET /`** (full-schema, registry empty)
  - Landed on a live basketball game — different domain entirely from the
    garden-bed session. 67 Eagles vs. Marietta Marauders, period 1, clock
    `12:00`, status `LIVE`, score 0–0.
  - Affordances: `pauseGame` (`POST /game/status`, optional `action` enum
    `PAUSE`/`RESUME`), `recordScore` (`POST /game/score`, requires `points`
    1–3 + `playerNumber`), `turnover` (`POST /game/possession`, requires
    `reason` enum). Plus plain HAL links to `homeTeam`/`awayTeam`.

- **"record 3 points"** → asked for missing `playerNumber` → **"55"**
  - Acted: `recordScore` `{points: 3, playerNumber: 55}`
  - Resync: `"67 Eagles player #55 scores 3 points!"`, score 3–0, possession
    → Marauders

- **"turnover !!"** → asked which `reason` → **"STEAL"**
  - Acted: `turnover` `{reason: "STEAL"}`
  - Resync: `"Turnover! STEAL. Possession: 67 Eagles"`, score unchanged

- **"pause"**
  - Acted: `pauseGame` `{action: "PAUSE"}`
  - Resync: status → `PAUSED`; manifest *shrank* to just `resumeGame` —
    `pauseGame`/`recordScore`/`turnover` gone from `_forms` entirely

- **"score 2 by 55"** (while still paused)
  - Manifest had only `resumeGame`. No `recordScore` request attempted, no
    affordance invented, no "closest match" guessed — reported unavailable.

- **"resume then score"**
  - Acted: `resumeGame` `{action: "RESUME"}`
  - Resync: status → `LIVE`, full affordance set back
  - Then acted: `recordScore` `{points: 2, playerNumber: 55}`
  - Resync: `"67 Eagles player #55 scores 2 points!"`, score 5–0, possession
    → Marauders

- **"score 3 by 33"**
  - Acted: `recordScore` `{points: 3, playerNumber: 33}`
  - Resync: `"Marietta Marauders player #33 scores 3 points!"`, score 5–3,
    possession → 67 Eagles

**Final:** 67 Eagles 5 – Marietta Marauders 3, `LIVE`, possession with 67
Eagles.

Full request/response detail for every call is in
[`20260709-113831-cold-sync-heart-server.yaml`](./20260709-113831-cold-sync-heart-server.yaml)
(`DEBUG ON` log).
