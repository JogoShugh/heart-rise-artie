# Idea: bdd-gates that check the work, not only the order

**Status:** idea only, saved 2026-11-14. Nothing is built. The gate is the author's: these changes go into
`~/repos/bdd-gates-skill` (and are reinstalled here) only when the author asks. Decisions marked **D?** are
the author's; each carries a recommendation.

## Where things stand

The bdd-gates skill checks that events happened in the right order since the last commit:

- `gherkin-first.sh` (PreToolUse, Edit/Write) blocks a production write unless some feature file differs from
  HEAD in `git status`.
- `log-event.sh` (PostToolUse, Edit/Write) appends `MAIN_WRITE` or `FEATURE_WRITE` to the event log.
- Each module's Gradle task appends `BDD_RUN PASS|FAIL total= failures= errors=` after every test run, or
  `BDD_RUN FAIL reason=compile|no-results`. `gate-bash.sh` blocks raw test commands so no run goes unlogged.
- `pre-commit` requires a `FEATURE_WRITE` and a `BDD_RUN FAIL` before the first `MAIN_WRITE`, and a passing
  last run.
- `protect-gate.sh` keeps the log, the hooks, the settings, `--no-verify` and `commit -n` out of Claude's reach.

That stops the lazy path (code first, tests after). It does not stop a determined one. Today the rest rests
on the agent's honesty and the author's review.

## The holes

| Shortcut | Caught today? | Why not |
|---|---|---|
| An empty or whitespace-only `.feature` edit | No | Any Edit/Write to a feature path logs `FEATURE_WRITE`; nothing reads the content. An untracked empty file counts as dirty. |
| A feature change that is never committed, or reverted | No | `pre-commit` does not require a feature file in the staged change. |
| A red run that is only a compile failure | No | Any `FAIL` line counts, `reason=compile` included. |
| A red run from some unrelated failing test | No | Ordering is per commit, not per scenario (the script says so). |
| A green run over a subset | No | `bdd-run.sh` passes extra args through; in artie the last pass may be another module's. |
| **Production code written through Bash** (`sed -i`, `cat >`, `cp`, `git apply`) | No | The write hooks watch Edit/Write only, so no `MAIN_WRITE` is logged and the ordering checks are skipped. |
| Tests that assert nothing | Partly | The Stop hook runs lint, architecture and coverage; no stack runs mutation testing. |

## The improvements

### 1. Every staged production file has its write on record

At commit, each staged production file must have a `MAIN_WRITE` for it since the last commit. A file changed
any other way (Bash, an external editor, a patch) fails the commit with its name. This closes the Bash hole
without trying to recognize every shell command that writes a file.

A person committing by hand from a terminal hits this too. **D?** Recommendation: allow it only in refactor
mode, or through a new author-only switch, the same way refactor mode is switched on.

### 2. Red means a test failed

The red run must be `BDD_RUN FAIL` with `total>0` and `failures>0` (or `errors>0`). `reason=compile` and
`reason=no-results` are logged as they are today but no longer count as the red.

### 3. The feature change is real and is committed

The staged change must include a feature file whose diff adds a `Scenario`, `Scenario Outline`, `Examples`
or a table row (`git diff --cached -U0 -- '*.feature'`, counting `+` lines that are not blank or comments).
Whitespace-only and comment-only changes don't count.

### 4. The new scenario is the one that went red, then green

The run logger also writes the names of failing scenarios (from the JUnit XML `testcase` elements) on a
`BDD_FAILED` line beside each `BDD_RUN`. At commit, at least one scenario added in the staged feature diff
must appear in a failing list before the first `MAIN_WRITE`, and must not appear in the last run's list.
Scenario Outline rows are matched by the outline's name.

**D?** Recommendation: match by scenario name only; matching by example row is fragile across runners.

### 5. The last green is the whole suite

The final passing run must be a run with no filter arguments, of every module the staged change touches,
and its `total` must not be lower than the previous full run's. `bdd-run.sh` and the Gradle logger record
`filtered=yes` when extra arguments narrow the run.

### 6. Mutation testing in the check

Add mutation testing to each stack's `checkCommand`: PIT for gradle-kotlin, Stryker (already a protected file
in node-typescript) for node, Stryker.NET for dotnet. It catches steps that assert nothing.

**D?** Recommendation: run it on changed files only, or every N commits as in
[sonarqube-every-n-commits](sonarqube-every-n-commits.md); a full mutation run per commit is too slow.

## Order to build

1. Number 1, the Bash hole: the biggest gap, and the smallest change (pre-commit only).
2. Numbers 2 and 3: pre-commit only, a few lines each.
3. Number 4: needs the logger change in every stack's template and in artie's modules.
4. Number 5, then 6.

Each one is built gate-first, like everything else: a `scripts/selftest.sh` case that commits the shortcut and
expects the refusal, red before the check is written.

## What stays the same

- The event log stays append-only and out of Claude's reach.
- Refactor mode keeps its own terms, switched only by the author.
- The checks still read only the log and git, so they work for a plain terminal `git commit` too.
