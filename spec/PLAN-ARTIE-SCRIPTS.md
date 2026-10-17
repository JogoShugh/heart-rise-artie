# ARTIE scripted, then scripts captured from words

Status: steps 1 to 3 authorized (2026-10-17); steps 4 to 7 wait until 1 to 3
are done, then we revisit them.

The aim: ARTIE driven by a Kotlin script, with no reasoner, from a bookmark
and the profile's vocabulary alone (relation names, field names, values),
never URLs; then a reasoner run, asked in words, captured as a trace and
compiled into such a script, whose parameters are the values that came
from the words (slot filling, after the frame-based dialogue systems of the
1990s, ATIS). Later runs need no model.

Every step is feature first (bdd-gates skill): tables, a red run, then
green. Every report follows the heart-run-report skill.

## Now

### 1. ARTIE scripted by hand against the GitHub gateway (phase 11a)

Find and close what ARTIE lacks against the gateway, each gap a client
feature, tested against the fake GitHub through the gateway in process.
Expected, unverified: templated search and lookup forms at declared
collection paths; item paths and template pointers; sub-collection links;
following Location after a 201.

Done when a Kotlin test drives ArtieClient from the bookmark to issue 10,
comments, follows the comment link, edits the comment and closes the
issue, with no URL in the test.

### 2. A Kotlin script host

The client published to mavenLocal; a `.main.kts` with `@file:DependsOn`
and its inputs as arguments. A thin script API over HeartRiseClient:
`enter`, `search(rel, fields)`, `pick(rel, title)`, `act(rel, fields)`,
`follow(rel)`, and `expectOffered(rel)`, which fails with `explain(rel)`.
Every step writes a trace line in the heart-run-report format.

Done when a hand-written `close-issue.main.kts` (owner, repo, number) runs
live on JogoShugh/heart-github-test.

### 3. Live: ARTIE scripted, ARTIE reasoning, heart-mcp (phase 11b)

The step 2 script live; the reasoner asked in words ("comment 'fixed in
1.2' on issue 10 and close it"); the same through heart-mcp.

Done when all three run live, each as a run report, and the GitHub
Gateway Status report has ARTIE scripted, ARTIE reasoning and heart-mcp
columns.

## Later (revisit after 1 to 3)

### 4. Trace capture

DecisionLog keeps replayable steps: enter, search(rel, fields), pick(rel,
title), act(rel, fields), follow(rel), with each result's status and
Location. Each value is tagged by its source: the user's words, the
server's state, or a constant. Done when a reasoner run's trace, replayed,
makes the same requests against the fake GitHub.

### 5. Script synthesis (slot filling)

From a trace, a `.main.kts`: each step a call; values from the words become
parameters, named and typed from the form schemas; values from state are
read again at run time, never hard-coded. `expectOffered` before every act;
a clear stop with the server's reason. A person confirms the parameters
before the script is kept. Done when "close issue 10 as not planned"
produces `close-issue-not-planned.main.kts(owner, repo, number)`, run live
with other inputs and no model.

### 6. Robustness

The synthesized scripts against the garden's Wednesday changes (move-url,
rename-field, retitle, new-required-input) and the OpenAPI twin's clients.
Expected: URL and title changes survive; vocabulary and schema changes stop
with a reason. Done when the change by outcome matrix is in a report.

### 7. Beyond one path

Loops and branches ("for each item of search X, if Y is offered, act") in a
small DSL, traces generalized into it. Out of scope until 5 and 6 hold.
