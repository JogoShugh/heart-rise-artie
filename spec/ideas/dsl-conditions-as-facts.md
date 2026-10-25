# Conditions as facts blocks and guards, not Kotlin's if

**Status:** agreed with the author 2026-10-25; being built. Supersedes the parameter forms built earlier that day
(`whenever(has = …)`, `onlyWhere`, `unless`, `holdsAt`), which are dropped, as are `whenever(by =, says =)`.

## Why

The author wants generated scripts with as little native Kotlin control flow as possible and as many declarative
DSL constructs as possible. Conditions first went in as parameters, and that does not scale: `act` already hit the
six-parameter limit, and every new kind of condition would add another. A block can grow without its signature
changing, and it can hold ordinary code where a script needs it.

## The constructs

**Facts are a block.** A `Facts` receiver collects what must hold; it ends up as the map `holds` has always read.

```kotlin
"github:labels" has "alert"        // the member's values include it
"state" eq "open"                   // the member equals it
at("watch") { "state" eq "closed" } // a fact about the resource kept as "watch" (keepAs), read again
that(looks >= 3)                    // any condition the script works out itself
```

The block is ordinary Kotlin, so a fact may depend on an input (`if (strict) "github:labels" has "reviewed"`).
`@HeartDsl` keeps it a condition: a facts block reads, it never acts.

**Guards end a member's turn.** Inside a `whenever` body, each guard either lets the rest run or ends the turn for
this member, which counts as answered:

| Guard | The rest runs where |
|---|---|
| `has { facts }` | the facts hold |
| `leaveWhere("words") { facts }` | the facts don't hold; where they do, it notes "Left <address>: words" |
| `madeBy(who)` | an embedded author, user or creator names who |
| `saying(words)` | the member's words say it |

`whenever` keeps its plain body; new guards are new functions, with no change to `whenever`.

**A branch** is `given { facts } then { … } otherwise { … }`, each part optional but the first.

**An Until is a facts block**: `listeners(until = { at("watch") { "state" eq "closed" } }) { … }`.

**A value read from a kept resource is declared, not filled.** `val number by kept("issue") { long("number") }`
is declared once, as an input is, and read after the act whose block says `keepAs("issue")`. It is the value the
resource had when it was kept; `at("issue") { … }` reads the resource again. Read before anything is kept by that
name, it stops the script and says so. The generator writes this for every value an answer brought into the run,
keeping each resource once, by its goal step's `as` name, else by its first value's name.

## What the watch writer makes of a goal

```kotlin
whenever {
    has { "github:labels" has "alert" }
    leaveWhere("leave it, and say so once.") { "github:labels" has hold }
    actOnce("github:comment", "body" to "Seen by the bench watch.", listedIn = "github:issues-list-comments")
    act("github:assign", "assignees" to listOf(grower)) {}
}
```

No `if`, no `else`, no `mapOf`, and no `var`.

**A part done already is left.** A chain of acts with a check that is not a said-once act is a `doing` block, in
the step's words, each check a `doneWhere { facts }` on what the act before it reached:

```kotlin
doing("say on it why it wasn't merged, unless that is said there already.") {
    follow("github:issues-list-comments") { doneWhere { "item" eq "…" } }
    act("github:comment", "body" to "Not merged: $why") {}
}
```

Where the facts hold, the block ends, the run notes "Done already: <words>", and the script goes on after it. Every
block a guard ends on its way out goes back where it was, read again, as it does at its end. A `doneWhere` outside
any `doing` stops the script.
