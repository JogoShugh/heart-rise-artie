# Handoff: Overheard at Riverside

Put aside on 2026-11-05. This is where everything stands, how it is built, and what is still open.

## Where things are

| What | Where | State |
|---|---|---|
| The deck | <https://claude.ai/artifact/CpbXk1kmDDH6cPo8mtrLnE> | Version 14, 25 slides |
| The story, read aloud | <https://claude.ai/artifact/H1jUDom6fenZUKBy9uz1Kz> | Version 1; source `aloud/riverside-aloud.html` |
| The 1997 report, "The Web That Wasn't" | <https://claude.ai/artifact/2hyYxASNS7JHyQMe1t1vRj> | Version 9; source `docs/reports/the-web-that-wasnt/` |
| Deck source | `exhibits/overheard-at-riverside/` | `slides/*.yaml`, `page/`, `code/`, `build.mjs` |
| Deck as built | `docs/reports/overheard-at-riverside/` | Written by `build.mjs`; also served on Pages |
| Commits | branch `claude/auth-branch-4bnmhc` | All local, 173 ahead of origin. Nothing pushed; wait for "Push" |

## The story, slide by slide

| # | Part | Title | Carries |
|---|---|---|---|
| 1 | Opening | Overheard at Riverside | The cast: Ana Torres, Marcus, Priya, Sam |
| 2 | Opening | One loop, two readers | Look, think, act beside sync, reason, act |
| 3 | Opening | HEART, in brief | The acronym; standards; Spring HATEOAS; HAL Schema Forms |
| 4–6 | Scene one · 1997 | Every act from a manual · The person joins them, by hand · Rules learned by refusal | WebWalker, forms.html, the sticky note, the 409; folds with the Perl CGI |
| 7 | What actually happened | The Marriage of Information and Controls | Animation: WebWalker's two windows become Netscape Navigator 3.0 with forms in the page; the married CGI |
| 8 | Scene two · today | The page carries its controls | Ana's phone: Renew greyed, with the reason |
| 9 | Between the scenes | The same moment, two webs | 1997 beside today |
| 10 | Scene three · today | The machine web's manual: OpenAPI | Priya, Sam, Swagger UI, the agent's 409 |
| 11 | Between the scenes | The Great Divorce | Animation: Ana's page splits into JSON and OpenAPI, then returns as HAL Schema Forms |
| 12 | Between the scenes | Information and Controls | Fielding's definition of hypertext; a timeline fold |
| 13–15 | Scene four · an hour later | Give the answer its controls · Declared once, enforced by the server · OpenAPI clients can opt in | The six whiteboard items; the declaration; 1997's CGI beside it; the affordance link |
| 16 | Scene four · that evening | A purpose, not a program | Fielding's 8 November 2008 reply, whole; `member.goal.md` |
| 17–18 | Scene five · the next week | Find the way once, then keep it · The kept script, five members | 11 Claude calls, then no model; five members by name |
| 19–20 | Scene six · a month later | A release, found once, kept as loops · Is hypermedia bloated? Measured | The release; 80 calls; the nested loops; L0, L1, L2 |
| 21–25 | Closing | This deck is HEART too · Upgrading is optional · The library, live · One web · What's fiction, what's real | heart-ts, plain JSON, `deck:code`, the live capture, notes |

## How the deck is built

- **Every slide is a HEART resource.** `build.mjs` turns each `slides/*.yaml`, in file order, into `deck/<name>.json`
  with next, prev, contents and alternate links, and `deck/contents.json` embeds them all. heart-ts, bundled for
  the browser, reads them in `page/deck.js`. `deck/plain/` holds the same slides as data only, for the Plain JSON
  switch.
- **Two voices, one title.** Each scene slide carries its conversation in HTML and its story in brief as
  `points`. Points name the characters and end with "What it shows". A scene's first slide has a `preamble` told
  through its people, shown in both voices. The title and the outline are the same in both.
- **Code read from its file.** A `<pre class="source" data-source="repo/path" data-from="…">` in a slide is filled at
  build time from that file: from the line holding `data-from` to the next line starting with `data-to`, or
  `data-count` lines, or a line that is only `}`. The file and lines appear under it. Write `<`, `>` and `"` in a
  marker as `&lt;`, `&gt;` and `&quot;`. A marker that no longer matches stops the build.
- **Code on demand.** A slide's `code` names a script in `code/`. The build links it as `deck:code` with its
  SHA-256 as `integrity`; `deck.js` loads it, and the browser runs it only if the bytes match. `rels.html`
  explains the relation. Plain JSON carries no links, so animated slides show their stills.
- **One animation engine.** `code/morph.js` knows no slide. A `.morph` element names its start still
  (`data-start`) and its cycles (`data-cycles`: to, label, heading). Each value marked `data-token` flies from one
  still to the next; a value with no partner fades with its still.
- **Drawn versus run.** The browser windows in both animations are CSS. The pages in them come from running code:
  the Marriage's from `married/library.cgi`, the Divorce's from the running library.
- **Story time.** The library's release (company groups, Report a problem) arrives in scene six. Slides 8–15 keep
  the screens and wire excerpts recorded before the release. Scene six, both animations and the live slide show
  the release.

## Rebuild and publish

1. In `exhibits/overheard-at-riverside/`: `node build.mjs --artifact <scratch>/overheard/overheard-at-riverside.html`.
2. Publish that file to the deck's URL, with `root` set to `docs/reports/overheard-at-riverside` and every changed
   file under it (except `index.html`) in `files`, keyed by its own path. A removed slide's files go in as `null`.
3. Check in headless Chrome: Conversation and Points, both animations, light and dark, 400 px wide. The check
   scripts used this session lived in the scratchpad; rewrite them from puppeteer-core in
   `exhibits/lo-to-heart/node_modules`.

## The running pieces

| Piece | Where | Run |
|---|---|---|
| The 1997 site (the web that wasn't) | `exhibits/the-web-that-wasnt/perl/` | Apache and Perl CGI in Docker; see its `Dockerfile` |
| The 1997 site, as it actually happened | `married/library.cgi` | `sh married/run.sh`: answers kept in `married/captures/` |
| The library today | `heart/heart-sims` (`HeartLibrary.kt`) | `java -cp "heart/heart-sims/build/sims/lib/*" com.artie.heart.sims.MainKt 8140 library.test` |
| The whole simulator | the same | the same, with `8181` and no service name; requests carry `X-Sim-Host: library.test` |
| ARTIE | `client/artie-cli/build/install/artie/bin/artie` | `JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-22.0.1/Contents/Home` |
| Runs and kept scripts | `artie/` | `member.*`, `company.*`, `five-members.mjs`, `rungs/measure.mjs` |
| Live captures | `live/` | `upgrade.mjs` (re-run after the release), `openapi-client.mjs` (from before it) |

- **The library today:** Ana's three loans and four more members; company groups (Harbor Point Logistics 15, Riverside
  Credit Union 2), searched five to a page. Every loan that is out offers renew (when allowed), report-problem and
  return. report-problem's `problem` takes its choices by `hr:options` from `/problem-kinds`; `comment` is
  optional, up to 200 characters.
- **heart-core's HTML** draws an `hr:options` field as a drop-down, with the server filling in the choices.
- **ARTIE runs:** `ARTIE_TOKEN_LOCALHOST_8140=library-token`; for Claude, `ANTHROPIC_API_KEY=$(cat ~/.anthropic/api_key)`,
  never printed. Generate with `artie generate <trace> --out <script> --format yaml --script <goal>`.
- **Numbers on the slides:** member run 11 calls; company run 80 calls, 111 steps, 14 of 23 renewed; kept company
  run 110 steps; L0 118,348 bytes (1,871 a loan), L1 52,404 (−56%; 462 a loan, −75%), L2 51,972.

## Open

1. **Radio buttons for a few choices.** heart-core's HTML could draw four choices or fewer as radio buttons. That
   would be its own rendering choice, with no `hr:radio` keyword, since a form says what may be chosen, not
   which widget. Undecided.
2. **The read-aloud page** tells the story as of version 13: the cast, the Marriage with real CGI, the release.
   Update its text when the story changes.
3. **The company run report** (`artie/company-report/`) is built from the earlier company run. Rebuild it from
   the new `company.trace.yaml` with the heart-run-report skill.
4. **Kept scripts** note "none of these acts is offered here" where the server's reason would read better. The
   ARTIE run report's "return ×7" means ARTIE's return-to steps, not the Return act.
5. **A real Netscape**, through oldweb.today, for the 1997 scenes. It needs `! npm install` run by the author.
6. **The screens before the release** (`media/openapi-list.png`, the kitchen's) stay as recorded then. Re-shoot
   them only if the release moves earlier in the story.

## Gotchas

- **Java:** Gradle needs Java 21: `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.12-graal`, as the lint gate uses.
  The default `java` is 25. ARTIE runs on openjdk 22.
- **The TDD gate:** feature files and `src/main` change only through Edit or Write, or the log misses them. A red
  run must fail on behaviour and be logged before any `src/main` write. A command naming the gate or its log is
  blocked: read `.claude/tdd-events.log` with the Read tool, near its end.
- **Rung traces need `--exchanges`,** or `measure.mjs` counts nothing.
- **Ports:** check `lsof -i :8140` before starting the library; an old one can hold it.
- **Order of forms:** HAL Schema Forms lists forms alphabetically, so declare acts in that order (renew,
  report-problem, return) for every dialect to agree.
- **The Stop gate runs every suite.** server-spring pins the garden's published OpenAPI byte for byte; regenerate
  with `./gradlew test -Popenapi.write=true` in `server-spring/` when the twin changes on purpose.
- **Goal wording matters.** One step, "for each member, on every page of the results: open the member, and renew
  each book…", went through all fifteen; splitting it made Claude stop after one member.
