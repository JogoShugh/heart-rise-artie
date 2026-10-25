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

## What the watch writer makes of a goal

```kotlin
whenever {
    has { "github:labels" has "alert" }
    leaveWhere("leave it, and say so once.") { "github:labels" has hold }
    actOnce("github:comment", "body" to "Seen by the bench watch.", listedIn = "github:issues-list-comments")
    act("github:assign", "assignees" to listOf(grower)) {}
}
```

No `if`, no `else`, no `mapOf`. What remains native: the variable a chain's check sets where it is not a said-once
act, and the variables of kept values (`var issueNumber`), the next step (E).
