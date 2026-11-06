# Idea: bare runs — a reasoned ARTIE run replayed as plain HTTP, every guardrail off

Status: target A built, 2026-10-22 (commits 9479893, 00e3e01, 9ae84bc, 56eaac1). The meddler runs and the rest
are ideas, below.

## The point

The native trial's Python, which Claude wrote from its own transcript, is brute force. It sends the requests it
remembers, reads only what it needs for the next number, and ignores everything HTTP and HEART offer for safety:
validators, conditional requests, optimistic concurrency, offered forms, schemas and refusals. It is fast, and it
goes off the rails as soon as someone else touches the repository (the meddler trial).

A bare run does the same thing with ARTIE. It takes a full reasoned run and divorces it from every guardrail. It
replays that run as the bare HTTP calls it came down to, still run by `artie`, but in a configuration that behaves
like the plain HTTP client ARTIE wraps. Then everything is held equal: the same recorded run and the same runner,
with one switch. The difference in time, and in what survives a meddler, is the cost and the worth of the
safeguards, and nothing else. It is neither the language, the runtime nor HEART's media types.

## What a bare run drops, and what it keeps

| | Strict / lenient / off-the-rails (now) | Bare |
|---|---|---|
| The entry and the links | followed from the entry, lists paged | none: each recorded address called directly |
| Offered forms | the act must be offered | not looked at |
| Schemas and the lock file's pins | checked | not checked |
| `If-Match`, `If-None-Match`, ETags | sent and kept | never sent |
| 409, 412, 422, 428 | each handled; decide again | any non-2xx stops the run, as in the Python |
| Reads before acts, after blocks, `sync()` | made | only the reads whose answers supply a value a later call sends |
| `check(...)` | made | dropped |
| The values sent | from the script's code | inputs, values read from earlier answers by pointer, or as recorded |
| Waits (`until`) | kept, by reading again | kept, as retrying the next write a second apart: a wait belongs to the task, not to the safeguards |
| Credentials, tracing | ARTIE's | ARTIE's: `~/.artie/credentials`, and the trace in the run-report format |

Values the request bodies carry as data stay as recorded. One example is the head `sha` on the merge, which is
GitHub's own check; the Python sends it too.

## Where it comes from

Everything is already captured during a run:

1. **ARTIE's trace** (`artie run --trace`) holds each step's relation, the values it sent and where each came from
   (`values.from`: an input, or a pointer into an earlier step's state), and its `until` waits.
2. **The gateway's upstream record** (`GATEWAY_RECORD`) holds every call the gateway made to GitHub, each with its
   method, URL, body and answer. The run's `traceparent` links them to ARTIE's run.

Translating a run is mechanical:
- Keep the writes, in order.
- Keep the reads that supply a number, id or sha a later call sends.
- Write each value as an input, a pointer into an earlier answer, or the recorded literal.
- Mark the write after each `until` as one to retry.

## Two targets (the author's call)

- **A. Directly to GitHub (recommended).** The calls come from the upstream record, with no gateway at all. This is
  the like-for-like comparison with `py-direct`.
- **B. To the gateway's HEART addresses, with no validators.** The calls come from ARTIE's own trace, and the
  gateway allows blind writes. HEART stays on the wire, with every client-side guardrail off. It sits between
  `off-the-rails` and A.

## How it fits in ARTIE

- **heart-script gets a bare vocabulary:** `call(method, url, body, retry)`, the answers as JSON, `answer["number"]`
  by pointer, and `b64(text)`. It runs on ARTIE's HTTP client, so credentials, timing and the trace work as in any
  run.
- **`artie generate RUN.yaml --bare UPSTREAM.yaml --out NAME.bare.kts`** writes the bare script (target A) from a
  capture kept with `--exchanges` and the gateway's upstream record. Target B would work from the trace alone. A bare script has no lock file, because it pins nothing.
- **`artie run NAME.bare.kts`** with `inputs` as usual. Generated, never hand-edited: the chain stays
  goal, recorded run, generated script.

## Done

1. heart-script's `bare { call(...) }` (`bare.feature`), then ARTIE sending bare calls through `WiredNavigator`
   (`bare-script.feature`), then `artie generate --bare` (`generate-bare.feature`): each red at test level, then
   green.
2. `runs/heart-script.bare.kts`, generated from `careful-heart` 1022-205149: 19 of its 237 GitHub calls (18 writes,
   1 read), every value an input or an earlier answer by pointer, only the merge retried. The ladder's
   `artie-bare` arm runs it.
3. The ladder, three interleaved rounds, each run 23/23:

   | Round | artie-bare (s) | py-direct (s) |
   |---|---|---|
   | 1 | 23.6 | 21.9 |
   | 2 | 21.0 | 23.5 |
   | 3 | 19.0 | 21.7 |
   | Median | 21.0 | 21.9 |

   ARTIE with every guardrail off is as fast as the Python, compiling the script included (about 2 s a run). The
   guardrails are what the careful runs' 58 to 81 s pay for.

## Rails: a watch brought down (2026-10-27)

A straight-line replay of one run can't watch. For a goal run that watched, `artie generate --bare` now translates
the kept script's watch word by word into plain calls at fixed addresses, the way a hand-written script polls:

| Kept script | Bare |
|---|---|
| `act("github:open-issue", …)` and `keepAs("watch")` | `val watch = call("POST", ".../issues", …)` |
| `watch(until = { at("watch") { "state" eq "closed" } })` | `watch(until = { call("GET", ".../issues/${watch["number"].text}")["state"].text == "closed" })` |
| `act("github:pulls-list", …)` and `whenever` | `whenever(call("GET", ".../issues?labels=${label}")) { item ->` and a read of `.../pulls/${item["number"].text}` |
| `has { "github:labels" has label }` | `has(member["labels"] has label)` |
| `leaveWhere(…) { "github:labels" has hold }` | `leaveWhere(…, member["labels"] has hold)` |
| `once("github:merge", giveUp = 3.minutes, otherwise = …)` | `once("PUT", ".../pulls/${member["number"].text}/merge", …, giveUp = 3.minutes, otherwise = …)` |
| reasoned `act(intent = "tell its author …")` | `call("POST", ".../issues/…/comments", mapOf("body" to write("tell its author …")))` |

- Each act's call, method and body come from the gateway's upstream record, under the spans of the steps that
  made it. Each value is an input, the list's item, the member read, or the resource kept by name.
- The gateway's rules (draft, still checking, behind) are dropped: a bare `once` tries the call at each look, and
  GitHub's refusal is the gate. Its `message` is the reason the Otherwise is told.
- The one reasoned line stays: `write(…)` asks ARTIE's reasoner for the words, offered as the one field of a form
  of its own.

Built: `bare-watch.feature` (heart-script), `bare-script.feature` (artie), `generate-bare-watch.feature`
(artie-cli). The exhibit: `exhibits/merge-watch/merge.bare.kts`, generated from `runs/rails-1.yaml` (a goal run
recorded with `--exchanges`) and `runs/rails-1.upstream.yaml`.

Live, 2026-10-27, on the scratch repository (`runs/bare-1.*`):
- one look covered all ten labelled pull requests;
- #829 was refused at first (GitHub still working out mergeability) and merged at the next look;
- three held pull requests were left, each said once;
- six drafts and conflicted ones were commented on after three minutes, in the model's words from GitHub's
  reasons ("Pull Request is still a draft", "Merge conflict").

In the same repository, the gateway-backed goal run (`runs/rails-1.out`) stopped every look at #813, because the
gateway answered 422 reading it. The bare watch read #813 directly. The gateway's 422 is still unexplained.

Known limits:
- A list item that is a plain issue, not a pull request, would fail its member read.
- One member's failed read ends that look for the members after it, as in the kept script.
- Only the words the merge watch uses are translated (watch, whenever, has, leaveWhere, once, a reasoned or
  plain Otherwise). Any other word stops the generator, saying which.

## Next (ideas, not started)

- **A native Python merge watch** (spec/ideas/python-merge-watch.md). The Python we have does the triage task,
  not a watch, so the bare watch has nothing yet to be compared with.

- **The meddler on `artie-bare`.** `MEDDLE=M1,M2,M3,M5` and `MEDDLE=M4`, 3 rounds each, beside `py-direct` and
  `artie-goal`. Expected: `artie-bare` goes off the rails like `py-direct` (overwrites the teammate's close, its
  duplicate reason, its comment), and `artie-goal` keeps everything. That shows it is the safeguards that save
  ARTIE, not the runtime. Then write it up under `docs/reports/native-vs-heart-trial/`, update its artifact, and
  link this note from `off-the-rails.md`.
- **Generator blemishes.** The header names the upstream record by the path given (a scratch path, in the
  committed script); name it by its file name. Inputs the calls never use (`gateway`) are declared; drop them.
- **Retry only on "not yet".** A waited-for write is tried again on any non-2xx; trying again only on 405 and 409
  would stop at once on a real error.
- **Compiled scripts cached.** `artie run` compiles a script every run, about 2 s even for a small one; a cache by
  the script's hash would save most of it, for every script, bare or not.
- **Target B: a bare run over HEART.** The calls from ARTIE's own trace, to the gateway's addresses, with no
  validators: between `off-the-rails` and bare.
- **Mixed scripts.** `bare { }` and `heart { }` blocks in one script: HEART for most steps, bare calls for a hot few.

## Research before building

A prototype translator, `exhibits/native-vs-heart/bare.kts`, is run by `artie run`, outside the client.
- **Input:** the clean `heart:every` run of 1022-130257, with 176 GitHub calls.
- **Output:** 19 calls: 18 writes and 1 read (`branches/main`, for the sha), with only the merge marked to retry.
- **Every value in that output is resolved:** the issue, pull request and sub-issue numbers and ids, and the
  branch and merge shas.

Live runs of that output, on the scratch repository:

| Run | Stamp | Exit | Seconds | Checked |
|---|---|---|---|---|
| bare, first output (Python, since deleted) | 1022-195344 | 0 | 21.5 | 23/23 |
| bare.kts output under `artie run` | 1022-195623 | 0 | 43.5 | 23/23 |
| bare.kts output under `artie run` | 1022-195726 | 0 | 2830.1 | 23/23 |
| bare.kts output under `artie run` | 1022-204457 | 0 | 51.5 | 23/23 |

The 2830 s run stalled somewhere unknown, and its output has no per-request times. The times under `artie run`
include starting the JVM and compiling the script, which hasn't been measured apart. For comparison, the
`py-direct` median is 25.7 s, and `careful-heart` is 76.1 s.

The prototype's output called `java.net.http` directly, not ARTIE's client, and is replaced by the generated
script (Done, above), which runs on ARTIE's.

**The join between ARTIE's steps and the gateway's calls is exact.** The prototype found a run's calls by the
run's trace id and its start time. A run recorded with `artie run --exchanges` (`EXCHANGES=1 ladder.sh`) keeps
each request's `traceparent` in its own trace. For `careful-heart` 1022-205149 (23/23, 58.4 s):
- the trace has 98 ARTIE requests;
- the gateway made 237 GitHub calls under 60 of those requests' spans;
- every GitHub call has an ARTIE request;
- 38 ARTIE requests caused no GitHub call.

## Decided

- Target A first: directly to GitHub, the most bare-metal correlate. A gateway-mediated bare run, still over
  HEART, is for later.

- Waits are kept, as retrying the waited-for write: the service's own "not yet" is the signal, with no reads
  spent polling. The native Python polls instead; a bare run doesn't copy what the Python does worse.
- The bare vocabulary lives in heart-script (`Bare.kt`), and a client sends it through `Wire`, beside `Navigator`.
