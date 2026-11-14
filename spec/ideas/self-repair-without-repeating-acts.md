# Idea: a step repaired mid-run must not make earlier acts run again

**Status:** idea, for follow-up. Raised by the author on 2026-10-19, while agreeing the script, lock file and config
shape (spec/ideas/replay-guards-lenient-strict.md). No code; it is a hard problem.

## The problem

A pinned script has already made N acts in this run when one step's guard refuses and the step repairs itself
(it falls back to its intent words, or a person answers a question). That step and the ones after it must go
on; the N acts before it must not run again in this run. Acts change the world: a second "open an issue" makes
a second issue, and a second merge or close fails or does harm.

It is easy while the repair happens inline: the run simply continues. It becomes hard as soon as the run has to
stop and start again:

- **A person has to answer,** and the answer comes minutes or days later, after the run has ended.
- **The budget runs out,** and the run stops at the step that needed the model.
- **The repair re-pins the script,** and the natural thing is to run the new script from the top.
- **The process dies** halfway.

## What HEART already gives toward it

- **The capture** records each step as it happens, with its outcome and the resources it created (`Location`).
  It is a journal of what was done.
- **`If-Match`** refuses an act made against a state that moved on (412). A repeated act sent with the old
  `ETag` is refused, not done twice. This does not protect a create sent fresh from a newly read state.
- **Each step's identity** is its place in the script: its line, and the block it sits in. That place survives
  pinning in place, since pinning changes only relations and values.

## Directions to weigh

1. **Resume from the journal.** Restarting with the run's capture skips every step the capture says finished,
   reading back the variables those steps set (`issue = state.long("number")`) from what was recorded, and
   continues at the first step that didn't finish.
2. **Look before acting.** Before each act, check whether the state already shows it done: the issue exists, the
   pull request is merged. Close to what `until` and `check` express today; could be declared per act.
3. **Idempotency keys.** Each act carries a key made from the run and the step; a server that keeps keys answers a
   repeat with the first answer. It needs the server's help (the IETF's `Idempotency-Key` draft); HEART could
   advertise support in the form.
4. **A mix of them:** the journal for skipping, look-before-acting where the state shows it, keys where the server
   offers them.

## Questions

- How a resumed run reads back variables set in steps it skips, when their resources may have changed since.
- What happens when a step that already ran sits inside a block whose resource has since changed state.
- Whether the re-pinned script, run from the top with the journal, counts as the same run (one capture) or a
  new one.
- How the line-by-line report shows a run that stopped and resumed.
