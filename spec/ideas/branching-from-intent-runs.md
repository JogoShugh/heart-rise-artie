# Idea: branching in scripts generated from intent-based runs

Status: idea, 2026-10-19.

## Where it stands

Scripts can branch. A script is Kotlin script: `if`, `when` and loops, plus the DSL's `offers(rel)` (is this act
offered now), `until(…)` and `check(…)`. A hand-written script can say "if merge is offered, merge; otherwise
comment and stop".

A generated script can't, today. artie generate works from one capture, and a capture holds only the path the run
took; a branch not taken leaves nothing. The generated script is a straight line, held to its lock file. The one
exception: `until(…)` and `check(…)` lines of the source script are copied across.

## Ways to get branches, cheapest first

1. **Keep the model where it decided.** Built: `--keep-reason` keeps a step as the `reason(…)` step it was. The
   fixed steps become plain script; the deciding step asks the reasoner at run time. No inference.
2. **Branch on what is offered.** HEART-native. The capture already records what was offered at each step, and a
   resource's offers already say its state. The generator could wrap each act in `if (offers(rel))`, with an else
   that stops, asks, or falls back to the step's intent words. It doesn't recover the logic, but a run against a
   resource in another state ends cleanly, saying where.
3. **Have the reasoner say why.** In the intent-based run, each choice also returns a structured reason: a state
   pointer and the value it relied on (`/state` is `open`). The generator writes it as a condition,
   `if (state.string("state") == "open")`. As good as the reasons given, so each is marked for review, as judged
   values are today. Medium effort.
4. **Merge several runs.** Run the same intent against resources in different states, and merge the captures
   where they diverge into a `when`. Which difference in state caused the divergence is the hard part: with 3's
   stated reasons it's plausible, without them it's a guess. Research.

## Worked through: the garden bed, growing branches by use (added 2026-10-20)

Option 2 with a light option 4. The intent is one sentence: "give bed 2 its basic care". In a reasoned run the
reasoner sees what the bed offers at each step and picks one; the capture already holds both, what was offered
and what was chosen. That is the branch's condition: the offer. The server decided the bed was frozen; the script
only reads the offer.

From a run where the bed was frozen, generate writes the branch it saw, and falls back to the words otherwise:

```kotlin
when {
    offers("garden:thaw") -> act("garden:thaw", intent = "thaw the bed")
    else -> reason("give bed 2 its basic care")
}
```

On a day the bed isn't frozen, the run takes the else, the reasoner picks water, and the next generate, from both
captures, adds that branch:

```kotlin
when {
    offers("garden:thaw") -> act("garden:thaw", intent = "thaw the bed")
    offers("garden:water-cell") -> act("garden:water-cell", "milliliters" to 250, intent = "water about 250 ml")
    else -> reason("give bed 2 its basic care")
}
```

So the script grows by use: a run takes a branch it knows, with no model, or teaches it a new one.

- **Order.** Where two acts were offered together, the branch the reasoner picked comes first. Captures that
  disagree leave that choice to the reasoner.
- **Values that depend on state.** A fixed value (250 ml) is copied; one the reasoner judged from the state stays
  a reason step, or is marked for review, as judged values are today.

## Open

- Whether the else branch of 2 stops, asks, or falls back to the intent words: the same choice as `on-change`
  for a changed form (spec/ideas/replay-guards-lenient-strict.md); one setting may serve both.
- How a stated reason is checked: against the state the step chose from, recorded in the capture, so a reason
  that doesn't hold there is refused at generate time.
- Whether a condition on state is a coupling the script shouldn't have, where an offer already says the same
  thing (HEART's point: the server decides what's allowed now). Prefer 2 where the offer suffices.
