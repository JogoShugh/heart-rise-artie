# HEART scripts, typed: an add-on to the core DSL (draft)

Status: draft, 2026-10-17, under challenge with the author; nothing built.
Companion to `spec/PLAN-ARTIE-SCRIPTS.md`, section "The DSL", which defines
the core this layer sits on.

The core DSL is HEART's own semantics: relations, field names, titles and
JSON Pointers as plain values, the profile's vocabulary, tolerant of
anything a server adds. People like strong types, and they should not be
boxed out. This layer gives them their own local types, as an add-on: it
compiles down to the core's calls, and the core never knows it exists.

## The rule

- **Typing is the client's own view, never a mirror of the server.** A
  local data class names only the members this client uses. Reading is
  tolerant: unknown members are ignored, missing optional ones take their
  defaults.
- **One way only.** `heart-script-typed` depends on `heart-script`; the
  core depends on nothing typed. Everything typed is an extension function
  that ends in a core call: `act(rel, fields)`, `follow(rel, title)`,
  `state[pointer]`, `input(…)`.
- **The server's truth still rules.** A typed input is still checked
  against the form's schema and choices; an act not in the manifest is
  still refused from it. Types never bypass HEART's run-time checks.
- **Mixing is always allowed.** Typed and untyped calls sit in the same
  block; any step can drop to the core.

## Side by side

The GitHub scenario of the plan, each line in the core and in the typed
layer:

| Core DSL | Typed layer |
|---|---|
| `val i = inputs { val owner by input(); val repo by input(); val number by input() }` | `val key = inputs<IssueKey>()` (each property an input; a property's default is the input's default) |
| `act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to number)` | `act(Github.issue, key)` |
| `act("github:comment", "body" to text) { … }` | `act(Github.comment, Body(text)) { … }` |
| `act("github:edit", "body" to "$text (edited)")` | `act(Github.edit, Body("$text (edited)"))` |
| `follow("github:issues-list-comments") { … }` | `follow(Github.comments) { … }` |
| `follow("item", title = "$text (edited)") { … }` | the same: core calls stay available |
| `check(state["/body"] == "$text (edited)")` | `check(state<CommentView>().body == "$text (edited)")` |
| `act("github:lock", "lock_reason" to "off-topic")` | `act(Github.lock, Lock(LockReason.OffTopic))` |
| `expect(NotOffered) { act("github:lock") }` (with `heart-script-test`) | `expect(NotOffered) { act(Github.lock, Lock(LockReason.OffTopic)) }` |
| `act("github:unlock")` | `act(Github.unlock)` |
| `until(timeout = 20.seconds) { offers("github:close") }` | `until(20.seconds) { offers(Github.close) }` |
| `act("github:close")` | `act(Github.close)` |
| `check(state["/state"] == "closed")` | `check(state<IssueView>().state == "closed")` |

`expect` is `heart-script-test`'s, for testing only; the typed layer works
with it unchanged. Both scripts make the same selections; their traces, and so their run
reports, are identical.

## The client's declarations

Hand-written by whoever writes the scripts; a generator may write the same
later (below), never required.

```kotlin
// Local types: this client's view of what it uses.
@Serializable data class IssueKey(val owner: String, val repo: String, @SerialName("issue_number") val number: Int)
@Serializable data class IssueView(val number: Int, val title: String, val state: String, val locked: Boolean = false)
@Serializable data class CommentView(val body: String)
@Serializable data class Body(val body: String)
@Serializable data class Lock(@SerialName("lock_reason") val reason: LockReason)
@Serializable enum class LockReason { @SerialName("off-topic") OffTopic, @SerialName("resolved") Resolved }

// Typed handles: a relation name, and the type this client sends with it.
object Github {
    val issue    = form<IssueKey>("github:issues-get")
    val comment  = form<Body>("github:comment")
    val edit     = form<Body>("github:edit")
    val lock     = form<Lock>("github:lock")
    val unlock   = form("github:unlock")
    val close    = form("github:close")
    val comments = link("github:issues-list-comments")
}
```

## The typed script

```kotlin
val key = inputs<IssueKey>()

heart("http://localhost:8392/") {
    act(Github.issue, key)

    expect(NewResource) {
        act(Github.comment, Body("Fixed in 1.2.")) {
            act(Github.edit, Body("Fixed in 1.2. (edited)"))
        }
    }

    follow(Github.comments) {
        follow("item", title = "Fixed in 1.2. (edited)") {
            check(state<CommentView>().body.endsWith("(edited)"))
        }
    }

    act(Github.lock, Lock(LockReason.OffTopic))
    expect(NotOffered) { act(Github.lock, Lock(LockReason.OffTopic)) }
    act(Github.unlock)

    until(20.seconds) { offers(Github.close) }
    act(Github.close)
    check(state<IssueView>().state == "closed")
}
```

## The whole layer

Extension functions on the core, nothing else:

```kotlin
class Form<I>(val rel: String, internal val input: KSerializer<I>?)
class LinkRel(val rel: String)
inline fun <reified I> form(rel: String) = Form(rel, serializer<I>())
fun form(rel: String) = Form<Unit>(rel, null)
fun link(rel: String) = LinkRel(rel)

// The tolerant reader: what a server adds never fails a read.
val Tolerant = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

fun <I> Representation.act(form: Form<I>, input: I, block: (Representation.() -> Unit)? = null) =
    act(form.rel, *fieldsOf(form, input), block = block)                    // the core call
fun Representation.act(form: Form<Unit>, block: (Representation.() -> Unit)? = null) =
    act(form.rel, block = block)
fun Representation.follow(link: LinkRel, title: String? = null, block: Representation.() -> Unit) =
    follow(link.rel, title, block)
fun Representation.offers(form: Form<*>) = offers(form.rel)
inline fun <reified T> Representation.state(): T = decodeState(Tolerant, serializer<T>())
inline fun <reified T> inputs(): T = declareAll(serializer<T>())             // one core input per property
```

## How it stays an add-on

| Assurance | Enforced by |
|---|---|
| The core never depends on types | separate modules; an architecture test (Konsist) fails the build if `heart-script` imports the typed layer or a serializer of domain types |
| Typed calls are core calls | extension functions only; a test runs the same scenario typed and untyped and asserts identical traces |
| Server additions never break a typed script | the script conformance kit's evolution matrix, against the lamp and garden servers: an added member, link, form or optional schema property passes, typed and untyped |
| What a type requires, missing, fails clearly | a member a local type requires that is gone is a step failure, `StateShape: /title missing for IssueView`, in the run report, not a stack trace; make it nullable and it is tolerated |
| HEART's checks still apply | typed input is checked against the form's schema and choices; absent acts are refused from the manifest |
| Mixing | typed and untyped calls in one block, always |
| Nothing generated by force | hand-written declarations; a generator is optional |

## Choices to make

1. **Values the server may extend.** A local enum fails to decode a value
   it doesn't know (a lock reason added later). Proposed: enums for what
   this client sends; for what it reads back, string-backed types or enums
   with an `Unknown` fallback, the fallback the default in read types.
2. **A field the form no longer has.** A local type sends a field the form
   dropped. Proposed: strict by default, a client failure `InvalidInput`
   naming the field; lenient on request, sending only what the form
   declares and noting what was left out in the report.
3. **An early check.** `verify(Github)` at entry: the profile still
   describes every relation the script names; drift reported before the
   first act. Advisory only; the manifest at each step stays the
   authority.

## Later: a generator

From the server's profile (ALPS) and its forms' JSON Schemas, generate the
declarations above with the same tolerance (read types with defaults and
`Unknown` fallbacks, inputs from the forms' properties). It writes nothing
the hand-written layer can't; it is a convenience, opt-in, and the core
stays untouched.
