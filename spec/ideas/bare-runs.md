# Idea: bare runs — a reasoned ARTIE run replayed as plain HTTP, every guardrail off

Status: plan, 2026-10-22. Waiting for the author's go before any code goes into the client.

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
- **`artie generate --bare RUN.trace.yaml --upstream RECORD`** writes `NAME.bare.kts` (target A), or works from the
  trace alone (target B). A bare script has no lock file, because it pins nothing.
- **`artie run NAME.bare.kts`** with `inputs` as usual. Generated, never hand-edited: the chain stays
  goal, recorded run, generated script.

## Steps

1. Features first, red at test level, then green, for each of these:
   - heart-script's bare vocabulary;
   - `generate --bare` from a fixture trace and upstream record: what is kept, what is dropped, values by pointer,
     and the wait becoming a retry.
2. Generate from a clean recorded run, then add the `artie-bare` arm to `runs/ladder.sh`.
3. Run the ladder: `artie-bare` beside `py-direct`, `careful-heart` and `artie-goal`, 3 rounds, checked by
   `checker.py check`.
4. Run the meddle ladder: storm and M4, 3 rounds. Expected: `artie-bare` goes off the rails like `py-direct`, and
   the same run with its guardrails on doesn't.
5. Write the report under `docs/reports/native-vs-heart-trial/` and update its artifact. Link this note from
   `off-the-rails.md`.

## Research so far

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

The prototype's output calls `java.net.http` directly, not ARTIE's client. That is what step 1 replaces.

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

## Open questions
- Should a bare run keep the `until` waits? (Recommended: yes, as above.)
- Should the bare vocabulary live in heart-script, or in a separate small module that the bare scripts import?
