# Plan: the triage task, done by Claude alone, beside the same task done through HEART

Status: run, 2026-10-22. Report: `docs/reports/native-vs-heart-trial/`; harness, checker and runs in
`exhibits/native-vs-heart/` (Python, standard library: live GitHub tooling). Planned as below; Follows spec/PLAN-GOAL-MARKDOWN.md, whose triage goal
(`client/scripts/tests/github-triage.goal.md`) Claude did live through ARTIE and the GitHub gateway (#165 to #168),
and whose generated script did again with no model (#178 to #185). The comparison drawn in
`docs/reports/native-vs-heart/` says what each way is good at; this trial measures it, on one task, run for run.

## The question

Given the same task, the same model and the same budget, how does Claude do it with only what it knows of
GitHub's REST API, against Claude through ARTIE and HEART? Then: how does each make the task repeatable, and how
does each repeat it?

## The arms

| | HEART | Native |
|---|---|---|
| Model | `claude-sonnet-5-5`, planner and reasoner | `claude-sonnet-5-5`, one agent |
| Task | `github-triage.goal.md` | `github-triage.native.md`: the same body, adjusted (below) |
| Inputs | owner, repo, label, a fresh stamp | the same values, filled in |
| Way in | `http://localhost:8392/`, the gateway | `https://api.github.com`, nothing else |
| Tool | moves and acts, checked by ARTIE | one `http` tool: method, path, query, headers, body in; status, headers, body out |
| Credential | held by ARTIE | added by the harness; the model never sees it |
| Budget | 90 moves, 15 a step | 90 tool calls; the same wall-clock limit |
| Help | what the service offers | none: no documentation, no `gh`, no MCP server; what is in the weights |

One `http` tool is what makes it fair: `gh` or a GitHub MCP server is someone else's knowledge of the API, written
down. That is a third arm worth running later (Open), never "native".

The model is the same in both, at its defaults. Each arm gets its run's transcript kept as YAML, as a capture is.

## The task, adjusted for native

`client/scripts/tests/github-triage.native.md`, written by hand from the goal file, the differences shown in the
report:

- **The front matter goes.** The inputs are filled in before the model sees the text, as ARTIE fills them: the
  stamp fresh for each run.
- **The way in is said in words:** "Use GitHub's REST API at https://api.github.com, through the http tool."
- **Marks become words:** `` `as issue` `` is "(the crash issue)"; `{issue.number}` is "the crash issue's number".
  `moves 15` and `reasoned` go: one is ARTIE's limit, the other its generator's.
- **`Wait:` and `Verify:` stay, as words:** "wait until it can be merged", "make sure it merged".
- **The list stays.** It is Markdown; it gives the model the structure ARTIE's runner had. The one asymmetry kept
  on purpose: ARTIE's runner holds the planner to that structure; native Claude only reads it.

## The native harness

A small Kotlin program, `exhibits/native-vs-heart/` (a build of its own, as `exhibits/koog-first-pass` is):

- **The loop:** Claude's Messages API, through Java's HTTP client; the `http` tool; the task as the user's
  message; a system prompt of about the length of ARTIE's planner's, saying what the tool does, to ask rather than
  guess, to stop when done or when it can't be done, and nothing about GitHub's API.
- **The tool:** a request to `https://api.github.com` + path, with `Authorization`, `Accept:
  application/vnd.github+json` and `X-GitHub-Api-Version` added by the harness. The answer goes back as status,
  the headers that matter (ETag, Location, Link, rate limits) and the body, cut at 20,000 characters, saying so.
- **Safety, by the harness, not the model:** any request off `api.github.com`, any non-GET outside
  `/repos/{owner}/{repo}/`, a DELETE of the repository or a PATCH of its settings, is refused before it is sent, and
  counted. Writes go to the scratch repository only (JogoShugh/heart-github-test).
- **The transcript:** YAML, one document a call: what the model said, the request, the answer, the time, the
  tokens. Credentials never in it; the harness scans for the token and stops if it finds it.
- **The budget:** 90 calls; the model's own words "done" or "stopped" end the run; so does the wall clock.

## The checker

`exhibits/native-vs-heart/` again: one program that judges a run of either arm by what GitHub holds after it,
given the run's stamp. It never reads a transcript, so neither arm is scored by how it tells its story.

- **Outcome, step by step**, each a pass or a fail:
  - the crash issue: exists once, labelled, assigned to the owner, locked as resolved, closed as completed, with
    the test issue as its one sub-issue, a comment naming the pull request and the test issue;
  - the test issue: says it is part of the crash issue, a comment naming the pull request, closed as completed;
  - the duplicate: a comment naming the crash issue, closed as a duplicate (of the crash issue);
  - the branch: both files on it, with their messages;
  - the pull request: into main, its description naming both issues, squash-merged with the given commit title;
  - both files on main.
- **Side effects:** issues, pull requests, branches or comments made that the task didn't ask for.
- **Cost:** model calls and tokens (in, out, cached), its price, requests to GitHub, refusals (4xx), wall time.

ARTIE's captures keep each model call (`model:`); if they don't keep its tokens yet, that is added first, behind a
feature, so both arms count the same way.

## The runs

1. **Arm by arm, 3 runs each**, each with a fresh stamp, alternating arms so neither has the API's quieter hour:
   enough to see a pattern, not a firm rate.
2. **Repeatable, 3 runs each, no model:**
   - HEART: `artie generate` from one successful run, the script run 3 times (two such runs already: #178 to #185).
   - Native: the model is given its own successful transcript and asked to write a script that does the task again
     with no model (Python, standard library only, the token from the environment), that script run 3 times.
3. **Drift, later (decided 2026-10-22), 1 run each:** between runs, change one thing the scripts depend on: the label made required,
   main protected, the merge method squash turned off. Does each script refuse before its harm, or do something
   wrong, or stop half done?

Between runs, the checker's own clean-up closes what a run left open and deletes its branches, in the scratch
repository only.

## The report

`docs/reports/native-vs-heart-trial/`: the task in both forms, its differences marked; then, per arm, the outcome
table (step by step, run by run), side effects, cost; then the repeatable runs and the drift; and one native run and
one HEART run side by side, call by call. Listed in the reports index, published as an artifact, the claims of
`docs/reports/native-vs-heart/` updated to what was measured.

## Build order

1. The native task file, and its diff against the goal file.
2. The checker, run against the two HEART runs already done (#165 to #168, #182 to #185): it must pass them.
3. Tokens in ARTIE's captures, if missing.
4. The native harness: the loop, the tool, the safety, the transcript. One run.
5. The 3 + 3 runs; then the scripts and their 3 + 3 runs. Drift after, on its own.
6. The report.

## Decided (2026-10-22)

- **3 runs per arm**, goal runs and script runs alike.
- **Drift later**, as a follow-up of its own.
- **The third arm later**, after this trial.

## Open

- **A third arm:** native with `gh`, or with GitHub's own MCP server: Claude with the best tools a person would
  give it.
- **Documentation:** a native variant allowed to read docs.github.com.
- **Other models:** the same trial with a smaller model, where the structure HEART gives may matter more.
