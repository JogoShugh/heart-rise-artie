# Idea: a trusting profile, for speed where nothing changes, and to show what the safeguards cost

Status: idea, 2026-10-22. From the native trial (spec/PLAN-NATIVE-VS-HEART-TRIAL.md,
`docs/reports/native-vs-heart-trial/`): the ARTIE script took 68 s and 58 steps where the Python Claude wrote took
22 s and 23 requests. Much of the difference is strictness: reads before acts, validators, schema checks, the lock
file, the re-read after each block, the recorded route through lists. This note is a third profile beside
`lenient` and `strict` (spec/ideas/replay-guards-lenient-strict.md) that turns most of it off.

## Why

1. **To measure the safeguards.** Run the same script strict, lenient and trusting against the same service: the
   difference in time and requests is what the safeguards cost, axis by axis. The trial can then say "HEART as
   fast as the Python, when it trusts as much", or not.
2. **To show what trust breaks.** Run trusting against a service that changed (the chaos trial): it should fail
   like the Python does, late or silently, where strict refuses before harm. The same script, two profiles, two
   outcomes: the safeguards' worth, shown, not argued.
3. **For services that genuinely don't change.** A fixture, a test double, a pinned internal service, a migration
   run once: where change is not expected, paying for its detection on every step is waste.

## The same script, a different run

The profile is the runtime's, not the script's: `artie run task.kts --profile trusting`, or `profile: trusting` in
`NAME.config.yaml`. The script and its lock file don't change. What trusting needs to go faster, the lock file
mostly has already: each form's method and target, each link's address, as recorded.

## The axes

Each axis is its own switch, so a run can turn off one thing to see its cost; `trusting` is all of them off.

| Axis | Strict / lenient (now) | Trusting |
|---|---|---|
| Is the act allowed now? | the form must be in the representation held | sent to the recorded target, held or not |
| Is the body valid? | checked against the form's schema first | not checked |
| Has the form changed? | the lock file's integrity, before every act | not checked |
| Has the resource changed? | `If-Match` on every act; a 412 is decided again | no validator, where the server allows it (below) |
| What does a refusal mean? | 409, 412, 422, 428 each handled | any 2xx goes on; anything else stops |
| After a block | the resource the block began at is read again | nothing read; the next step acts on the recorded address |
| After making a resource | `sync()` reads it again | not read |
| Links and routes | followed where offered; lists paged on | the recorded address fetched directly, or skipped when nothing is read from it |
| Waits | `until { offers(…) }`, re-reading | kept: a wait is the task's, not a safeguard |
| Checks | `check(…)` | kept, or turned off on their own switch |

Waits and checks stay by default: they are what the person asked for, not ARTIE's caution.

### The one the client can't turn off alone

A HEART server requires `If-Match` on every act and answers 428 without one (SPEC §6.3). A trusting client can't
make it accept a blind write. Two ways, both honest about it:

- **Keep the validator, drop the read.** The 201 or 200 of the last act carries the resource's ETag; send that,
  without reading again. Fast, and still safe where the resource only changes by this script's own acts; GitHub's
  late changes to a new pull request then come back as a 412, which trusting doesn't decide again: it stops.
- **A server that allows it.** A HEART server's development switch, `unconditional acts: allowed`, for a fixture
  or a simulation only, never by default. The gateway in front of GitHub could offer it for the trial.

## Trust, but check once

A middle profile, `trusting-checked`, for "should be stable, prove it": before its first act, the run reads the
service's root and compares one digest, the affordance sets' addresses it recorded (they are content-addressed,
SPEC §6.9.1), against what is offered now. The same: run trusting. Changed: fall back to lenient, and say so. One
request buys back most of the safety for a service that really is still.

## What it would show in the trial

A fourth and fifth column in the report's scripted table:

| | Python | ARTIE strict | ARTIE lenient | ARTIE trusting | ARTIE trusting-checked |
|---|---|---|---|---|---|
| Today's GitHub | 22 s, 23 requests | 68 s, 58 steps | measured | expected near the Python | trusting + 1 read |
| A changed gateway (chaos) | fails late or silently | refuses before harm | refuses before harm | fails like the Python | falls back, refuses before harm |

## Open

- **Names.** `trusting` says what it does; `fast` says why it's wanted; `unguarded` warns. The report would show
  it beside the others, so the name should carry the warning.
- **The recorded route.** Skipping a follow whose page gives no value later (the branch list on the way to main)
  is the generator's Detours rule applied at run time; fetching the recorded address directly is a URL baked in,
  which is exactly the Python's weakness, on purpose.
- **Where the switches live.** The DSL's `Here` holds the follow pre-check, ARTIE's navigator the schema check and
  the guard, its client the validators and the re-reads: one `Profile` the run gives the client, read in each.
