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

### The proposal (revised 2026-10-18 with the author)

Three pieces, each small.

**1. `state[...]` takes a member name.** A bare name gets an implicit `/`; a leading `/` is a
JSON Pointer, for nested state. `state["state_reason"]`, `state["/head/sha"]`.

**2. `check` takes facts: member to value, or member to matcher.** Every fact is tested, and every
failure is reported, in words the matcher carries:

```kotlin
check("state" eq "closed", "state_reason" eq "completed")
check("cropName" eq "basil", "moistureLevel" above 0)
check(
    "labels" has "bug",
    "status" oneOf listOf("PLANTED", "GROWING"),
    "closed_at" to present,
    "/head/sha" matching Regex("[0-9a-f]{40}"),
)
```

- Each infix word makes a `Fact(member, matcher)`; `check(vararg Fact)` runs them all. The same
  facts may come as pairs (`"state" to "closed"`, a plain value meaning equality, or
  `"moistureLevel" to above(0)`) or as a `Map`, from data the script already has.
- A failure says what was held: `moistureLevel: expected above 0, was 0`;
  `labels: expected to contain bug, was [enhancement]`. No message is written by hand.
- No casts: JSON numbers arrive as `Long` or `Double`; numeric matchers compare as numbers.
- The trace records each fact as data (member, test, expected, held), so the run report can
  show it as a row, not as a sentence.

| Matcher | Tests |
|---|---|
| `eq`, `isNot` | equality |
| `above`, `below`, `atLeast`, `atMost`, `between` | numbers, any JSON number type |
| `has` | an array holds an item; a string contains a text |
| `oneOf` | the value is in a set |
| `matching` | a regular expression |
| `present`, `absent` | the member exists, or not (`absent` is not `null`) |

**3. `check(condition)` stays, for anything one member's test can't say**, such as two members
compared or arithmetic, with Kotlin's Power-assert compiler plugin (Kotlin 2.0 and later)
configured for `check`. A failure then prints the expression and each of its values, and the
hand-written message becomes optional:

```kotlin
check(state["merged_at"] != null || state["state"] == "closed")
```

Verified and built, 2026-10-18: heart-script-host builds the script engine (JSR-223, for `artie run`
and the features alike) with Power-assert given `-Xplugin` and told to draw `heart.script.Here.check`.
The message's first line stays the script's own words; the drawing goes into the trace, and
`artie run` prints it above the run's end. One limit: `&&` stops early, so a part after a failed
first part is not drawn.

### Alternatives considered

- **A `check { }` block of facts** (this note's first proposal). A block adds nothing the facts
  form doesn't: it defers evaluation, which `until { }` already covers, and it groups facts, which
  a single `check(…)` call does too. The facts form is a plain call, so it composes (facts can be
  built, stored and passed).
- **A `holds { }` block.** The same, under another name. The author found the word odd;
  `check` reuses the word scripts already know.
- **Bare indexing, `["state_reason"]`.** Not Kotlin: an index access needs a receiver, and `[...]`
  alone is a collection literal, allowed only in annotations. `this["state_reason"]` would work,
  with an `operator fun get` on the scope.
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

1. Checks: built, 2026-10-18 (facts with matchers, `check(condition)` with Power-assert, member
   names in `state[...]`). Still open: the matcher words' final spelling.
2. Blocks after reason: A, B or C?
