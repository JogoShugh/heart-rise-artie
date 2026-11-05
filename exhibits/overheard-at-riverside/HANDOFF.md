# Handoff: Overheard at Riverside, and what it pulled in

Written 2026-11-04 (evening), to pick up later.

## Where things are

- **The deck:** <https://claude.ai/artifact/CpbXk1kmDDH6cPo8mtrLnE>, version 8, 23 slides. It is built from
  `exhibits/overheard-at-riverside/` (`slides/*.yaml`, `page/`, `build.mjs`) into
  `docs/reports/overheard-at-riverside/`. To rebuild, run
  `node build.mjs --artifact <scratch>/overheard-at-riverside.html` in the exhibit folder, then publish that file
  with every file under `docs/reports/overheard-at-riverside/` (except `index.html`) in the `files` map.
- **The report:** "The Web That Wasn't", <https://claude.ai/artifact/2hyYxASNS7JHyQMe1t1vRj>, version 9 (set in
  1997). Its source is `docs/reports/the-web-that-wasnt/`.
- **Commits:** all local. The branch is 159 commits ahead of `origin/claude/auth-branch-4bnmhc`. Nothing is pushed;
  wait for "Push".

## What was built this session

- **The deck itself is HEART.**
  - Each slide is a JSON resource with next, prev, contents and alternate links.
  - The contents resource embeds the outline.
  - heart-ts, bundled for the browser, reads it in the page.
  - A switch serves the same deck as plain JSON, which heart-ts reads as data only.
- **Protocol (SPEC §6.1):**
  - A static host may name its profile in `_links.profile`.
  - An answer that names no profile is read as data only.
  - A client may ask for plain JSON.
  - Both clients pass the shared conformance suite.
- **heart-core:**
  - The OpenAPI twin no longer names `jsonSchemaDialect`, so Swagger UI shows no warning.
  - The garden's published OpenAPI copies were regenerated to match.
- **The library (heart-sims):**
  - It sends the affordance link on plain JSON.
  - It has five members, found by name.
  - It has company groups: search-members by company, five to a page. There are 15 staff at Harbor Point
    Logistics and 2 at Riverside Credit Union.
- **artie generate:**
  - A link the run followed by an input's value is written as that input, for example `title: ${member}`.
  - A list the run went through, page after page, is one `whenever`, nested where each member has a list of its own.
  - YAML and Kotlin are written alike.
- **Runs:** in `exhibits/overheard-at-riverside/artie/`.
  - **By name:** `member.goal.md` → `member.heart.yaml`, from one Claude run of 11 calls. `five-members.mjs` ran it
    for five members, with results in `five-members.yaml`.
  - **By company:** `company.goal.md` → `company.heart.yaml`, from one Claude run of 79 calls and about 370,000
    input tokens. Run with no model, it renewed 14 books, each once.
  - **Rungs:** `rungs/` holds the same company script at L0, L1 and L2, measured by `measure.mjs` into
    `rungs.yaml`.
- **Live scripts:** in `exhibits/overheard-at-riverside/live/`.
  - `upgrade.mjs`: heart-ts against the library, as plain JSON and as HEART.
  - `openapi-client.mjs`: a JavaScript OpenAPI client before and after it opts in to the affordance link.

## Open questions for the author

1. **The nested `whenever: - whenever:` reads oddly.**
   - What it means: the outer loop is each member the search lists, page after page; the inner loop is each book
     that member has out.
   - Options:
     - (a) the generator writes a comment on each `whenever` naming what it goes through, taken from the run;
     - (b) a clearer keyword, such as `for-each:` with a label, which changes the script format and its schema.
   - Undecided.
2. **Showing the plain-text goal.**
   - Show `member.goal.md` and `company.goal.md` beside their kept scripts on the slides ("what you wrote, what ARTIE
     kept").
   - Have `artie generate` copy the goal into the kept script's header comment.
   - Undecided. The scripts carry the goal only step by step today, as `intent:` and `from-intent:`.

## Leftovers worth doing

- **The IT office screenshot** (`exhibits/overheard-at-riverside/media/openapi-list.png`) predates the company
  release. It shows 6 operations; the library now has 8 (search-members, get-member). Re-shoot it, or leave it as
  the moment before the release.
- **The deck's notes slide** (`slides/23-notes.yaml`) doesn't mention the name and company runs, the generator
  changes, or the rung measurements yet.
- **The ARTIE run report** says "return ×7", meaning ARTIE's return-to steps rather than the Return act. Kept
  scripts note "none of these acts is offered here" where the server's reason would be better.
- **Netscape / oldweb.today** for the 1997 scenes, later, when at home. It needs `! npm install` run by the user.

## How to run things

- **Library alone on 8140**, as the ARTIE runs use it:
  `java -cp "heart/heart-sims/build/sims/lib/*" com.artie.heart.sims.MainKt 8140 library.test`, with Java 21
  (graal). Rebuild first with `./gradlew -q :heart-sims:installSims` in `heart/`. Reset with
  `curl -X POST localhost:8140/_sim/reset`.
- **The whole simulator on 8181**, for the live scripts: the same command with `8181` and no service name. Requests
  name the service in `X-Sim-Host: library.test`.
- **ARTIE:** `client/artie-cli/build/install/artie/bin/artie`, with JAVA_HOME set to
  `~/Library/Java/JavaVirtualMachines/openjdk-22.0.1`.
  - The library's credential goes in as `ARTIE_TOKEN_LOCALHOST_8140` (the fixture token).
  - For Claude runs, the key goes in as `ANTHROPIC_API_KEY=$(cat ~/.anthropic/api_key)`. Never print it.
  - Example: `artie run company.goal.md "company=Harbor Point" --rung L2 --reasoner claude --model claude-sonnet-5-5 --trace company.trace.yaml`.
  - Then: `artie generate company.trace.yaml --out company.heart.yaml --format yaml --script company.goal.md`.
- **Pins hold the rung they were recorded at.** To run a kept script at another rung, copy it somewhere without its
  `.pins.yaml`, as `artie/rungs/` does.

## Gotchas met this session

- **An old simulator held 8140 from the morning**, so the new one exited at once. Check with `lsof -i :8140`.
- **Feature files only through Edit/Write.** A shell append isn't logged as a FEATURE_WRITE.
- **Running heart-ts's conformance suite:** use `.claude/bdd-gates/bdd-run.sh`. A raw `npx cucumber-js` is blocked.
- **The Stop gate runs every suite.** server-spring pins the garden's published OpenAPI byte for byte; regenerate it
  with `./gradlew test -Popenapi.write=true` in `server-spring/` when the twin's bytes change on purpose.
- **Goal wording matters.** A goal that splits "open the member" from a nested "for each book" made Claude stop
  after one member. One "for each member … renew each book …" step went through all fifteen.
