# Script checks, and blocks after reason

An idea, written 2026-10-17 from a conversation with the author. Two questions about the
heart-script DSL, independent of each other. Nothing is decided and nothing is built.

The script they start from (`client/scripts/tests/garden-task-reasoned.kts`):

```kotlin
heart(entry) {
    reason("build a 4 by 4 foot herb bed called \"Herbs, reasoned\"") {
        reason("plant basil in it, about 2 centimetres deep")
        reason("water it with 250 millilitres per cell")
        check(state["/cropName"] == "basil") { "the bed should hold basil" }
        check((state["/moistureLevel"] as Long) > 0) { "the bed should be watered" }
    }
}
```

## 1. Checks that read well

### The problem

`check(state["/cropName"] == "basil") { "the bed should hold basil" }` has four faults:

- a JSON Pointer string, `"/cropName"`, where a member name would do;
- a cast, `as Long`, to compare a number;
- a message written by hand that repeats the condition;
- a failure that says "the bed should hold basil" but not what the bed held.

### The proposal

Keep `check(condition) { message }` as it is, for any condition. Add a second shape, a block
of facts about the representation the script holds:

```kotlin
check {
    "cropName" eq "basil"
    "moistureLevel" above 0
    "status" isOneOf listOf("PLANTED", "GROWING")
    "/plot/soil" eq "loam"          // a leading slash: a JSON Pointer, for nested state
}
```

- Keys are member names; a leading `/` makes them JSON Pointers.
- Words: `eq`, `isNot`, `above`, `below`, `atLeast`, `atMost`, `isOneOf`, `contains`,
  `exists`, `isMissing`. Numbers compare as numbers; no casts.
- Each line is its own check step in the trace, with the expected and held values, so the run
  report can show "cropName: expected basil, held basil".
- A failure writes its own message: "stopped at step 5: cropName expected basil, held mint".

### Alternatives considered

- **A `holds { }` block.** The same, under another name. The author found the word odd;
  `check` reuses the word scripts already know.
- **Kotest-style matchers**, `state("cropName") shouldBe "basil"`. Familiar to Kotlin people,
  but reads like a test framework in everyday scripts.
- **Typed reads with plain Kotlin**, `val crop by state<String>("cropName")`, then
  `check(crop == "basil")`. Plain Kotlin, but a message showing the held value needs Kotlin's
  Power-assert compiler plugin, unproven with the script engine; and it leans toward the typed
  layer (`spec/HEART-SCRIPT-TYPED.md`), where `state.cropName` would come from the schema.

## 2. A block after reason

### How blocks work today

A block after `act`, `follow` or `reason` is a visit: the client goes to what the step led to,
runs the block there, and when the block ends returns to where it was and reads it again.
With no block, the client moves to what the step led to and stays there. Where a step leads is
the server's answer, known only once it comes back: a new resource, the same resource read
fresh, or a linked one. A form doesn't say in advance, for `act` or for `reason`.

### The question

For `act`, the script's author chose the form, and so knows roughly where it leads. For
`reason`, the model chose, so the author can't be sure where the block will run. ARTIE limits
the model to the forms and links on offer, with fields that fit the schema, but within those
the model may pick something other than what the author pictured: a search instead of a build.
Then the steps in the block run somewhere unexpected, until a check stops them.

### Options

**A. Keep blocks after reason; guard with checks.** As today. A `check { }` inside the block
stops the run if the steps landed somewhere other than intended. In test scripts,
`expect(NewResource) { reason("…") { … } }` says it outright.

```kotlin
reason("build a 4 by 4 foot herb bed called \"Herbs, reasoned\"") {
    reason("plant basil in it, about 2 centimetres deep")
    reason("water it with 250 millilitres per cell")
    check { "cropName" eq "basil"; "moistureLevel" above 0 }
}
```

Simple, and the indentation reads as "these happen in the new bed". The guard comes after the
fact: a wrong turn is caught at the check, not at the step.

**B. reason takes no block.** A model step only moves the client; the script checks where it
landed before going on. Honest about the uncertainty; loses "visit and come back" for model
steps.

```kotlin
reason("build a 4 by 4 foot herb bed called \"Herbs, reasoned\"")
check { "label" eq "Herbs, reasoned" }
reason("plant basil in it, about 2 centimetres deep")
reason("water it with 250 millilitres per cell")
check { "cropName" eq "basil"; "moistureLevel" above 0 }
```

**C. reason says what it must lead to.** The script states the outcome it needs; ARTIE stops
the run at that step if the answer is anything else, and the block runs only on a match. Like
`expect`, but for everyday scripts.

```kotlin
reason("build a 4 by 4 foot herb bed called \"Herbs, reasoned\"", leadsTo = NewResource) {
    reason("plant basil in it, about 2 centimetres deep")
    reason("water it with 250 millilitres per cell")
    check { "cropName" eq "basil"; "moistureLevel" above 0 }
}
```

Explicit and safe; one more thing to write. A variant could also limit the model's choice to
forms whose ALPS descriptor says they create, if the profile says so.

### Rejected along the way

- **One verb: `act` treating words as a reason step** (any string with spaces, or not a
  relation). A mistyped relation would quietly become a model call, costing money and varying;
  `artie run` couldn't tell before the first request that a script needs `--reasoner`; and the
  trace would no longer show which steps a model chose.
- **`act { reason("…") }`.** A level of nesting that adds nothing.
- **Flattening by default.** Removing the outer block changes only where the script ends (on the
  bed rather than back at the bookmark). The author prefers the nesting for how it reads.

## To decide

1. Checks: `check { }` with these words, or another word or shape?
2. Blocks after reason: A, B or C?
