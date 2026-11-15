# Idea: a Dockerized SonarQube in the bdd-gates flow, scanning every N commits

Noted 2026-11-13. A first manual scan ran 2026-11-15 (below), and its first fixes are in (steps 1, 2 and part of
3 of the plan); the every-N-commits hook is not started. It adds a hook to the bdd-gates skill, and changing the gates is the author's call.

## Why

detekt runs after every edit (complexity lint) and is fast enough for the inner loop, but it is narrow: Kotlin
only, mostly smells and complexity, little security, no dependency checks. SonarQube adds breadth on a slower
cadence: bugs, duplication, coverage, trends and a quality gate, across languages.

| Tool | Finds | Security | Where it runs |
|---|---|---|---|
| detekt | Kotlin complexity, smells, style, some likely bugs | Little: no taint tracing, no dependencies | Local, seconds: after every edit |
| SonarQube | Bugs, smells, duplication, coverage, quality gates; Kotlin rules overlap detekt's | Hotspots; taint analysis only in paid editions | A server (Docker), minutes: every N commits |
| Veracode | Security only: binary SAST, dependencies, DAST | Strong, compliance-grade | Commercial SaaS; not for the inner loop |
| Semgrep | Pattern rules, many languages | Good security rules, Kotlin supported | Fast CLI; could sit in a hook |
| Snyk / OWASP Dependency-Check | Vulnerable dependencies | Strong on dependencies | CLI |

Recommendation: keep detekt in the inner loop, add SonarQube every N commits, add one dependency scanner.
Veracode only if compliance requires it.

## How

- **Run it:** `docker run -d -p 9000:9000 sonarqube:community`. The embedded database is fine for a trial; use
  Postgres to keep data across upgrades. Needs about 2–4 GB of RAM.
- **Scan:** the `org.sonarqube` Gradle plugin, `./gradlew sonar` with a token from the environment (never printed,
  never committed). Import detekt's report so findings are not duplicated.
- **Every N commits:** a post-commit hook keeps the last scanned commit in the skill's state. When
  `git rev-list --count <last>..HEAD` reaches N, it starts the scan in the background and records the quality
  gate's result. A commit never waits on the scan.
- **Configured in the skill:** `SONAR_EVERY=10` (say); `SONAR_EVERY=0` turns it off.
- **The result:** a warning at the next stop, or a blocking gate. The author chooses which.

## Open

1. Warn or block on a failed quality gate?
2. Which dependency scanner, if any: Snyk or OWASP Dependency-Check?
3. Where the server runs when the laptop is not the only machine.

## First scan, 2026-11-15

A first, manual scan of `claude/auth-branch-4bnmhc` at `cb20f58a`. Nothing was fixed.

- **Server:** SonarQube Community 25.1 with Postgres, in Docker Desktop on the Mac (`~/.artie/sonar/compose.yaml`).
  - Data persists in named volumes.
  - The dashboard answers on the LAN: http://192.168.1.18:9000/dashboard?id=artie (user `admin`; password and
    analysis token in `~/.artie/sonar/sonar.env`, mode 600).
  - Community shows one branch per project; it is named `claude/auth-branch-4bnmhc` there.
- **Scan:** `~/.artie/sonar/scan.sh`, the `sonar-scanner-cli` image, not the Gradle plugin.
  - The image is amd64 only, so on the Mac it runs emulated; it needs `-XX:+UseSerialGC` or Java crashes.
- **Scope:** hand-written code. Left out: `buster/`, `wild/`, `work/`, `runs/`, any `runs`, `build`, `.gradle`,
  `node_modules`, `generated`, `captures` or `work` folder, and `*.html`, `*.min.js`, `*.jsonl`, `*.log`, `*.out`
  and image files. Tests are `**/src/test*/**` and `**/src/*Test/**`.

### Totals

About 49.7k lines of code in 621 files: Kotlin 24.6k, JS 14.1k, Python 4.6k, CSS 4.3k, TS 1.3k, XML 0.6k.

| Measure | Result |
|---|---|
| Bugs | 9 (reliability D) |
| Vulnerabilities | 0 (security A) |
| Code smells | 1,086 (maintainability A, about 110 hours) |
| Security hotspots | 32: 2 high, 23 medium, 7 low |
| Duplicated lines | 7.4% |

- **By severity:** 2 blocker, 730 critical, 305 major, 58 minor.
- **By language:** JS 779, Kotlin 161, CSS 69, Python 59, TS 19, Dockerfile 8.

### Why the JS looks so much worse than the Kotlin

- **One rule.** 601 of the 779 JS issues are `javascript:S3504`, `var` instead of `let` or `const`: style, not
  bugs.
- **Copies.** `deck.js` (72 issues) sits in four folders (`docs/reports/how-far`, `how-far-2`, `how-far-3`,
  `exhibits/heart-deck/page`), and `morph.js` (36–37) in five. `docs/reports` alone holds 516 of the 1,095 issues.
- **No gate.** Kotlin passes detekt, the BDD gate and tests on every commit. The browser JS (decks, report viewers,
  `heart-forms.js`) has no linter, no tests and no build.
- **What is left** once `var` and the copies are set aside: nested ternaries (54), functions nested too deep (43),
  functions too complex (19).

### Top rules

| Rule | Count | What it flags |
|---|---|---|
| `javascript:S3504` | 601 | `var` instead of `let`/`const` |
| `css:S4666` | 69 | The same selector twice |
| `kotlin:S6624` | 60 | Versions written directly in Gradle files |
| `javascript:S3358` | 54 | Nested ternaries |
| `javascript:S2004` | 43 | Functions nested too deep |
| `kotlin:S1481` | 32 | Unused local variables |
| `kotlin:S1192` | 25 | Repeated string literals |
| `python:S1192` | 23 | Repeated string literals |
| `javascript:S3776` | 19 | Functions too complex |
| `javascript:S4624` | 16 | Nested template literals |

### By folder

| Folder | Issues |
|---|---|
| `docs/reports` | 516 |
| `exhibits/heart-deck` | 125 |
| `heart/heart-core` | 100 |
| `exhibits/overheard-at-riverside` | 93 |
| `heart/heart-gateway` | 34 |
| `exhibits/mcp-over-openapi` | 26 |
| `client/heart-script` | 25 |
| `docs/decks` | 24 |
| `exhibits/openapi-vs-heart` | 19 |
| `client/artie-cli` | 17 |

### Blockers and bugs

- **Blockers:**
  - `docs/decks/how-far/newblocks.js:474`: `n` assigned with no `let`, `const` or `var`.
  - `heart/heart-gateway/live/github/act.py:103`: the function always returns the same value.
- **Bugs in shipped code:**
  - `client/artie/src/main/kotlin/heart/ArtieClient.kt:219`: the condition repeats line 217's.
  - `client/artie-release/.../DeskReplies.kt:39` and `client/heart-script/.../Time.kt:102`: the Boolean `await`
    returns is ignored.
  - `exhibits/allowed-now/proxy.ts:42`: a promise-returning function where a void one is expected.
  - `implementations/conformance/features/support/garden-fixture.mjs:15`: a regex whose precedence needs grouping.
- **The other four bugs** are in deck build scripts under `docs/decks`: a duplicate name, a call with arguments the
  function doesn't take, a regex needing grouping, and a Python statement with no effect.

### Security hotspots

- **High:** `exhibits/merge-watch-release/jenkins/Dockerfile:15` and `:16` copy files into the image writable.
- **Medium, regex:** 14 regexes that could backtrack on hostile input, among them `heart-forms.js:281`,
  `docs/reports/assets/run-report.js:324`, `goal-view.js:57` and `:61`, `kotlin-view.js:11`, and
  `implementations/heart-ts/src/template.ts:9`.
- **Medium, Dockerfiles:** eight images run as root (`kit/Dockerfile:29`, `kit/release/Dockerfile:22`,
  `mcp/Dockerfile:16`, `server-spring/Dockerfile:34`, `heart/heart-sims/Dockerfile:22` among them), and one
  recursive copy may pull sensitive files into an image (`heart/heart-sims/Dockerfile:19`).

## Plan for the first fixes

In order: the cheap ones that remove the most noise first, then real bugs, then the gate that keeps it fixed.

1. **One copy of each deck script.** Move `deck.js` and `morph.js` to `docs/reports/assets`, and have every deck
   load that copy, as the reports already share theirs. About 300 issues go with the duplicates, and so does most
   of the 7.4% duplication.
2. **`var` to `let`/`const`, by tool.** ESLint (or Biome) with `no-var` and `prefer-const`, run once with `--fix`
   over the hand-written JS; then check every deck and report still renders in headless Chrome. About 600 issues.
3. **The two blockers and the five bugs in shipped code.** Each through the BDD gate where it is in `src/main`: a
   failing scenario first (the repeated condition in `ArtieClient.kt`, the ignored `await` results), then the fix.
4. **The high hotspots.** `COPY --chown` with read-only modes in the Jenkins Dockerfile; mark the rest reviewed or
   fixed one by one.
5. **Keep it fixed.** ESLint `no-var` in the commit checks for JS, beside detekt for Kotlin. Then the every-N-commits
   scan above, with `scan.sh` moved into the repo (`scripts/sonar/`) so it is not a Mac-only file.
6. **Later, not first:** the Gradle version catalog for `kotlin:S6624`; the regex hotspots; non-root images, which
   need the kit's bind mounts checked for uid on Linux.

**D?** Steps 1 and 2 are mechanical and touch no `src/main`; steps 3 and 5 touch the gate or `src/main` and are
the author's call.

## Where it stands, 2026-11-15

A scan of `claude/auth-branch-4bnmhc` at `84a3b7cc`, with the compiled classes and the Gradle cache's jars
given to the scanner (`sonar.java.binaries`, `sonar.java.libraries` in `scan.sh`).

| Measure | First scan | Now |
|---|---|---|
| Issues | 1,095 | 244 |
| Bugs | 9 | 3, all false positives |
| Code smells | 1,086 | 241 |
| Security hotspots | 32 | 29 |
| Duplicated lines | 7.4% | 4.3% |

- **By language:** Kotlin 133, JS 90, Python 14, TS 6, Dockerfile 1.
- **In production code:** 76 (`src/main` and `heart-forms.js`).

### What the fixes covered

- **Outside production code:** one copy of each deck script in `docs/reports/assets`; `var` to `let`/`const`
  by ESLint; CSS selectors merged; nested ternaries untangled; Python constants and single returns; Dockerfiles
  (Jenkins job files root-owned, heart-sims as a non-root user); Gradle dependencies grouped.
- **Production code, in refactor mode:** repeated literals as constants, unused imports, two if-chains as
  `when`, `Transport` and `KeepsModelCalls` as `fun interface`, `heart-forms.js` declared with `let` and
  `const` (checked old against new in headless Chrome).

### Left for the author's review

<details><summary>False positives in production code (about 50)</summary>

- **`kotlin:S1481` unused local (32):** every one is used; types from the compiled classes did not change it.
- **`kotlin:S6531` useless cast (7):** each cast narrows `Any?` from YAML or JSON and is needed.
- **`kotlin:S6519` `equals` to `==` (4):** each is `equals(…, ignoreCase = true)`, which `==` cannot say.
- **`kotlin:S6508` `Void` to `Unit` (2):** `Mono<Void>` is Spring's `WebFilter` signature.
- **The three bugs:** `ArtieClient.kt:219` (a Kotlin 2 `when` guard read as a repeated condition);
  `DeskReplies.kt:39` and `Time.kt:102` (`await` inside a loop that checks its condition again).

</details>

<details><summary>Left on purpose</summary>

- **`DataSteps.kt` literals (9):** the script vocabulary table reads best with each key written out.
- **`Trace.kt` delegation (2):** the class wraps only some calls; `by` would change which it overrides.
- **Complexity:** `Watch.kt` (S3776), `Heart.kt` (S107, nine parameters), two nestings in `heart-forms.js`.
- **`heart-forms.js` nested ternaries (3).**
- **`heart-tck` `HtmlSteps.kt`:** step code; its `data-rel` literal stays.
- **`implementations/heart-ts/src/client.ts` (4):** not changed. The conformance suite must run through the
  gate's wrapper to be logged, and the hook that protects the gate blocks naming it, so a refactor there
  cannot show green. The gate's two rules need reconciling first.
- **Separate builds:** `exhibits/*`, `server-spring`, `domains/tickets`.
- **Outside production code:** about 60 `kotlin:S6624` version literals (a version catalog), the regex
  hotspots, and images that still run as root because they use the Docker socket, bind mounts or port 80.

</details>

### Next

1. The author reviews the false positives above and marks them in SonarQube (or asks for it).
2. Step 5 of the plan: ESLint `no-var` in the commit checks, then the every-N-commits scan.
