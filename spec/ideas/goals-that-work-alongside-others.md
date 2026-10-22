# Idea: goals that work alongside others, in words that don't read like code

Status: idea, 2026-10-22. Nothing is built beyond what "Today" names. Drafted with the author after the meddler
trial (exhibits/native-vs-heart, `meddler.py`, commit a66a755). Shown as the artifact "Blank-Bed Triage Goal"
(https://claude.ai/artifact/VvNYsK4M6x8kURp8V5z9cM), with three views: as read, source, and syntax.

## Why

In the meddler trial a teammate changes the repository while the triage task runs. They close the crash issue as
not planned, lock it off-topic, close the duplicate as a different crash, convert the pull request to a draft, and
add a note to its description. The Python script carried on and overwrote all of it, then exited 0. The ARTIE
script `exhibits/native-vs-heart/runs/heart-script-goal.kts` kept everything the teammate did and finished:
4 of 4 runs under the storm of moves, 2 of 2 under the draft.

That script was edited by hand from a generated one. The chain the project stands for is
goal → recorded run → `artie generate` → script and lock, and the hand edit skipped it.
`client/scripts/tests/github-triage.goal.md` has no words for what the hand edit added:

- `decide { }` around an act a teammate may make stale;
- `if (offers(…))` around an act a teammate may take away;
- a note saying why a step was left;
- text that depends on an outcome ("Fixed in…" against "Waiting on…").

Adding `if`, `else` and `Wait:` keywords to the goal would get there, but the goal would turn into a small
programming language. This note proposes words a person would write to a colleague instead.

## The draft goal

```markdown
---
start: http://localhost:8392/
inputs:
  owner: { question: Whose repository? }
  repo:  { default: heart-github-test, question: Which repository? }
  label: { default: bug, question: Which label marks a bug? }
  stamp: { default: { now: MMdd-HHmmss } }
---

Triage and fix the blank-bed-name crash, start to finish. The crash gets an issue, labelled and assigned, with a
sub-issue for its test; a second report of the same crash is closed as a duplicate of it. The fix and its test go
on a branch of their own, into one pull request, which is merged. Then every issue is closed the right way, and
the crash's conversation is locked.

## Working alongside others

Other people work in this repository while you do. What they decide stands.

- Don't undo or write over anything someone else changed since you looked. Look again, and work from what's there now.
- When a step no longer makes sense because of what someone did, leave it, and say why in the run's notes.
- Nothing you write may claim something that isn't so: "fixed" only once the fix is merged.

## The work

- In {owner}/{repo}
  - Open an issue titled "Crash when a bed has no name ({stamp})", saying a bed with a blank name crashes the garden `as issue`
    - Label it {label}
    - Assign it to {owner}
  - Open an issue titled "Test blank bed names ({stamp})", saying it is part of #{issue.number} `as test`
  - Open an issue titled "Garden crashes on an empty bed name ({stamp})", saying it was seen on the demo garden `as dupe`
  - From the main branch, start a new branch named fix-blank-names-{stamp}
    - Commit a file named fix-{stamp}.md saying "Bed names may not be blank." with the message "Refuse a bed with no name"
    - Commit a file named test-{stamp}.md saying "A bed with a blank name is refused." with the message "Test that a blank bed name is refused"
    - Open a pull request into main titled "Refuse a bed with no name ({stamp})", saying it fixes #{issue.number} `as pr`
      - Add to its description that it is tested by #{test.number}
      - Once it can be merged, merge it by squashing, titled "Refuse a bed with no name (#{pr.number})".
        Held back as a draft, it stays unmerged.
- On {issue}
  - Add the test issue as a sub-issue
  - Say it's fixed in #{pr.number} and tested in #{test.number}; or, while that's still unmerged, that it waits on #{pr.number}
  - Lock its conversation as resolved
- On {test}
  - Say the test went in with #{pr.number}, and close it as completed
- On {dupe}
  - Close it as a duplicate of #{issue.number}, and say it's the same crash

## Done when

- The crash issue is closed, and the pull request is merged; or it's held back, and the crash issue says it waits on it
- Nothing anyone else decided was undone
```

## The syntax

### The file

| Part | Looks like | Means | |
|---|---|---|---|
| Header | `---` YAML `---` at the top | `start` is the entry address; `inputs` are the run's values, each with a question or a default | today |
| Brief | The first paragraph | What the task is, in words. The model reads it; no rule is taken from it | today |
| Working alongside others | A heading, a sentence, three bullets | Manners for the whole run (below) | new |
| The work | A nested bullet list | The steps, in order | today |
| Done when | Bullets | The final checks, written as outcomes. Replaces the `Verify:` lines | new |

### Inside the steps

| Element | Example | Means | |
|---|---|---|---|
| Step | Open an issue titled "…", saying … | One thing to do, as a plain sentence | today |
| Scope | In {owner}/{repo} · On {issue} | The resource the bullets under it act on | today |
| Nesting | An indented bullet | Done on what its parent step made or reached; then back up | today |
| Value | `{owner}`, `{stamp}` | An input | today |
| Value from a step | `{issue.number}` | A member of what an earlier step made | today |
| Name | `as issue` | Names what the step made, so later steps can say `{issue}` | today |
| Quoted text | "Refuse a bed with no name" | Sent exactly as written | today |
| Wait | Once it can be merged, … | Wait until the act is offered. Replaces `Wait:` | new |
| Other outcome | …; or, while that's still unmerged, … · Held back as a draft, it stays unmerged. | A second way the step can end, chosen by what earlier steps did or what is there now. Never a branch the writer spells out | new |
| Add, don't replace | Add to its description that … | Keep what is there, and add to it | new |

Step ids (`3.`, `10.`) are dropped: names (`as issue`) are enough to refer back.

### The manners, and what each turns on

| Sentence | ARTIE then | Script it generates |
|---|---|---|
| Don't undo or write over anything someone else changed since you looked. Look again, and work from what's there now. | After a 412 (stale), reads again and decides again. It never sends again over a member someone else changed (45de695) | `decide { }` around each act whose body is worked out from state (1a518b8) |
| When a step no longer makes sense because of what someone did, leave it, and say why in the run's notes. | Skips a step whose act is no longer offered, and notes the server's reason. Without this sentence, a step not offered stops the run, as it does today | `if (offers(rel)) … else note(why(rel))` |
| Nothing you write may claim something that isn't so. | Sends words that depend on an outcome ("fixed") only once that outcome happened | the text chosen by a value the run set, such as `merged` |

## Two rules underneath

1. ARTIE decides only from what the server offers and from state, never by reading the server's prose
   (SPEC §6.2). The goal's words say which offer or which state to look at.
2. The goal is read once, on a recorded run. `artie generate` turns that run into a script and its lock file,
   and every replay after it is deterministic. The manners and the "or" outcomes cost a reasoner nothing on replay.

## Open question

How are the manners recognised?

- **Fixed phrases (preferred).** The parser knows the three sentences, or close variants, and turns each into a
  run setting. Predictable and testable, but the writer has to use the expected wording.
- **The reasoner interprets them.** The writer can say anything; the recorded run shows what the reasoner made
  of it, and `artie generate` writes that down. More freedom, less certainty about what a sentence turns on.

The "or" outcomes and "Once …" have the same choice to make, less sharply, because their structure is in the
punctuation.

## What building it takes

1. `Goal.kt`: parse the two new sections, "Once …" steps, "; or, …" and "Held back as …" outcomes, and
   "Add to …". Features first, in tables.
2. ARTIE: the manners as run settings (skip when not offered, with a note; decide again on stale).
3. `artie generate`: emit `if (offers(…))`, `note(why(…))`, `decide { }`, and outcome-dependent text, from what
   the recorded run did at each step.
4. One paid reasoner run of the goal, recorded. Then generate the script and its lock, and replace the
   hand-edited `heart-script-goal.kts` in the meddle ladder (`runs/ladder.sh`, arm `artie-goal`) with it.
5. Rerun the meddle ladder: the generated script should match the hand-edited one's results.
