# Idea: replay guards, lenient and strict, on affordance sets and forms

**Status:** idea, 2026-10-18. From the author, while planning the line-by-line run capture
(spec/PLAN-LINE-BY-LINE-REPORT.md). Builds on spec/ideas/captured-scripts.md. No code yet; the capture
records what it needs from the start, so this can be layered in later with no change of format.

## The idea

A script captured from one run, replayed later, must know before it acts whether the server still offers
what the script relies on. HEART already names what it offers by content: an affordance set is fetched
through a link with an integrity value (L1), and each schema too (L2). A replay compares what it finds
with what the capture recorded, in one of two modes:

- **Lenient (the default).** Follow whatever `hr:forms` link the server gives now, whether or not the set's
  address or integrity matches the recording. In the set found, look up the relation the step used. Fail
  before acting if the form is **absent**, or if its **integrity changed**. If the set changed around it
  but the form kept its integrity, and so its definition, act on it.
- **Strict.** Fail before acting if the **set's** integrity differs from the recording, even when the form
  the step uses is unchanged.

In both modes, bytes are always checked against the integrity of the link they were fetched through
(HAL-SCHEMA-FORMS-HR Rule 2). That check is about transport, not drift, and no mode turns it off.

## What it needs

1. **Per-form integrity, published by the server** (decided 2026-10-18). Each form in an affordance set
   carries its own `integrity`, over the form as served: target template, method, content type,
   `hr:templatePointers`, and its schema or schema link; not its title or description. A set's integrity covers the whole set; a
   form's lets a lenient replay tell "the set changed" from "my form changed". Computed in heart-core's
   layers, so the gateway and every HEART server get it alike. A client-side digest (JCS, RFC 8785) is the
   fallback for servers without it, and is weaker: it is the client's word, not the server's.
2. **The capture records identities, not addresses** (spec/PLAN-LINE-BY-LINE-REPORT.md, `guard`):

   ```yaml
   guard:
     rung: L2
     set:  { href: /affordance-sets/03af2350eb6c76d5, integrity: 'sha256-A68j…' }
     form:
       relation: github:close
       integrity: 'sha256-…'
       schema: { href: /schemas/…, integrity: 'sha256-…' }
     if-match: '"seq-3.hsf.L2.…"'
   ```

3. **Captures recorded at L2** (`artie run --rung L2`). At L2 a form's target is a template filled by
   `hr:templatePointers`, so one set is shared by every resource in the same state: the guard compares the
   template, never the expanded address, and a script recorded on issue 87 replays on issue 99. At L0 the
   forms are inline and no server address exists; only client digests are possible there, and both modes
   are weaker.
4. **The mode is a replay option**, `--guard lenient|strict`, not part of the capture.
5. **The cache helps.** A replay reuses the HTTP cache's sets and schemas; a revalidation (304) costs
   nothing, and a set that changed is then judged by the mode.

## Why it matters

Browser record-and-replay infers drift from page structure. Here the server states it: the form a step
used either has the integrity it had, or it doesn't. Lenient replay survives everything that doesn't touch
the forms a script uses (new acts, removed acts it never used, reworded others); strict replay is for a
script that must run only against exactly what it was recorded against.

## When the guard refuses: repair on the fly, or ask (added 2026-10-19)

A refusal is not the end of a run. Today it stops the run (a generated script needs no model); with the
reasoner allowed, the refused step can be repaired on the fly. Not built yet; this is the A3 contestant of
spec/PLAN-AGENT-FACE-OFF.md.

1. Before sending, the guard finds the form changed (or a link's template variables changed). Nothing is
   sent: no 4xx, no silent wrong value.
2. That one step goes to the reasoner, with the form as served now (its schema, `required`, titles,
   descriptions, examples) and the step's original words, inputs and the state.
3. The reasoner fills the step from what it can justify, or asks. The run goes on; the steps whose forms are
   unchanged stay scripted, with no model call.
4. After the run, `artie generate` proposes a new script, lock file and derivation report, and the person
   approves it. No one edits code.

**Self-repair can't invent information.** A new required field is filled only from a justified source; when
there is none, the honest repair is a precise question, asked once:

| Where the new field's value comes from | Example | Asks? |
|---|---|---|
| The schema's `default` | `method` required, default `"drip"` | No; the derivation says "default from the schema" |
| One choice allowed | `enum: ["drip"]`, or `hr:options` with one item | No |
| The current state or the inputs | `hr:templatePointers`, or a value an earlier answer brought in | No |
| The step's own words | The intent said "drip-water bed 2" | No; the words give it, so no review mark |
| None of these: new information | `method: drip or spray?`, no default, no hint | **Yes**: a person, or a configured source. Never a guess |

Seen live: under the garden's `new-required-input` change (exhibits/openapi-vs-heart), Claude asked "drip or
spray?" and sent nothing. That is the correct outcome. HEART makes the question precise, since the form
carries the field's title, description, allowed values and examples.

**The answer is asked once, not on every run.** It becomes an input of the regenerated script,
`val method by input(default = "drip")`, and the derivation report shows where it came from.

**Policy decides what is automatic.**

- **Accept on its own** when every value is justified by a default, the state, the inputs or the words.
  This covers renames, moves, retitles, a new field with a default or one choice, and a tighter limit the
  value still meets.
- **Ask, then propose** when a value is new information or the reasoner's own judgement. The same line
  `artie generate` draws with `review:`.

Either way, the old script never runs wrong in the meantime: it is refused, never wrong.

| | A hard-coded script | Guard, then repair |
|---|---|---|
| Notices the change | No: a 422 in production, or silently wrong | Yes, before sending |
| Says what changed | No | Which form, which field, old and new schema |
| Asks the right question | Someone digs through docs and logs | One question, from the schema |
| Remembers the answer | A code edit and a redeploy | An input of the regenerated script |
| Who changes code | A developer | No one; a person approves the proposed script |

## Script, lock file and config: the shape agreed (2026-10-19)

Agreed with the author, not built. The schema compatibility check (stage 2 below) is deferred: it needs a
schema diff, a separate piece of work.

### Three files, each with one job, one author, and one reason to change

| File | What it holds | Found by default at | Override for a trial run | Written by |
|---|---|---|---|---|
| The script | What to do and why: each step's relation, values and intent words | The path given to `artie run` | — | A person, or an AI; `artie generate` pins it |
| The lock file | What the server offered: affordance-set and form integrities | `NAME.pins.yaml`, beside the script (exists) | `--pins FILE` (exists) | The tool only |
| The config | How strict to be and how much to spend: guard, fallback, budget, re-pinning, per-relation overrides | `NAME.config.yaml`, beside the script (new) | `--config FILE` (new) | A person only |

They are kept apart because each changes for a different reason, at a different pace, by a different
author. In one file, a regeneration would wipe a person's tuning, or a person's edits would fight the tool's.

- **Trial runs leave the defaults alone.** With `--pins` or `--config` given, re-pinning writes only to the
  file named. `--no-repin` runs with no re-pinning at all.
- **A missing config means today's behaviour:** refuse on a changed form, never fall back.
- **Precedence:** a flag, then the config's rule for the relation, then the config's default, then the
  built-in default. `--guard` stays as a flag, and is also a config setting.

### The script: one verb, `act`, for its whole life

- `act(intent = "merge it, squashing its commits")`, with no relation, reasons. It replaces `reason(...)`,
  which goes away, with no alias.
- `act("github:merge", "merge_method" to "squash", intent = "merge it, squashing its commits")` is pinned. It
  runs with no model while its pin holds, and falls back to its intent words when it doesn't.
- An act written by hand with no intent never falls back. It refuses, unless the config allows the form's own
  title and description to stand as the words.

A script starts as intent only, written by a person or an AI. A run pins it, and a later run re-pins it when
the service changes.

A sketch (not yet the DSL):

```kotlin
// water.kts, as written: intent only
heart("http://localhost:8080/") {
    act(intent = "open bed $bed") {
        act(intent = "water it with 250 ml, by drip")
    }
}

// water.kts, after artie generate pinned it in place
heart("http://localhost:8080/") {
    act("garden:bed", "bed" to bed, intent = "open bed $bed") {
        // A long act gives its values in a block, one a line (built 2026-10-19); the block after still visits.
        act("garden:water", intent = "water it with 250 ml, by drip", fields = {
            "milliliters" to 250
            "method" to "drip"      // review: the reasoner chose it
        })
    }
}
```

```yaml
# water.config.yaml: written by a person; absent means today's behaviour
guard: lenient
fallback: justified        # never | justified | ask | always
budget: 5                  # model calls per run
repin: propose             # auto | propose | never
relations:
  github:merge: { fallback: ask, repin: propose }
```

### `artie generate` pins in place

- **It adds the relation and values in front of each `intent =`, and changes nothing else.** The words,
  nesting and checks stay byte for byte.
- **It refuses a script with uncommitted changes, and prints the diff.** `--check` shows the diff without
  writing; `--out` writes a new file, as today, for comparisons such as the derivation report.
- **A re-pin proposed by a self-repair needs approval:** a flag, or a commit. A self-repair never pins itself
  silently.

### The config

- `guard`: lenient or strict.
- `fallback`: never; only when every value can be justified; ask a person; always.
- `budget`: the most model calls in one run.
- `repin`: automatic, propose only, or never.
- Per relation, overrides, e.g. `github:merge: { fallback: ask, repin: propose }`.

### The walk at run time, per step (a Merkle walk)

Content-addressed means immutable: a changed schema gets a new address, so the change shows as a different link
in its parent, up to the affordance set. Only the root, the resource (Layer 1), is revalidated; schemas need no
`ETag` of their own.

1. Revalidate the resource (a 304 is cheap). It links to its affordance set.
2. **The set's integrity is the one pinned:** everything under it is unchanged, by definition. Act from the
   cache, with no fetch and no model. This is the common case.
3. **The set changed, but the form's integrity is the one pinned** (another act was added or removed, say):
   act, and add the new set to the lock file.
4. **The form changed:** fetch only its new schema. By default (`on-change: refuse`), refuse. Built 2026-10-19,
   `on-change: values` in the config: a lenient run acts when the form keeps the method the lock file pins and
   the values the script sends fit the new schema (every field sent is one it names, every field it requires is
   sent, types and limits met); the trace says `held: values`, and `repin: auto` writes the new integrity,
   method and set. Otherwise it refuses, saying which. Later values of the same setting: fall back to the intent
   words, or ask. Compatibility of whole schemas is not needed: a run checks the values it actually sends.

Strict mode stops at step 3. Re-pinning happens only after the act succeeds, and only where the config allows
it. A form's integrity also covers its target template, method and template pointers: a moved target is still
compatible (the client always uses the target the server gives); a changed method is not. A form's integrity
leaves out its title and description, so a change of meaning with the same names and schema is invisible to any
hash: a server that changes what a field means must change its name or its schema.

### Stage 2 is protobuf's question; borrow its answers, not its machinery (added 2026-10-19)

Protocol Buffers settled schema evolution for a compiled contract shared out of band: field numbers on the wire,
optional fields, unknown fields kept, and a list of changes that are safe or not. HEART's contract is served at
run time and self-describing, and the pins only notice a change, so none of protobuf's machinery carries over.
Its experience does: what went wrong for its users is a checklist for stage 2.

- **Never reinterpret silently.** A reused field number made old clients read new data as the old field.
  Stage 2 must refuse when a field it relies on now means something else (a changed type, format or enum), never
  coerce.
- **Required is forever.** proto3 dropped `required` because a field once required can never be removed. A new
  required field without a default is a break; one made optional is not.
- **Presence differs from a default.** proto3 at first could not tell 0 from unset, and `optional` came back in
  3.15. A script that omits a field relies on the server's default; a changed default is a change of meaning.
- **Enums change on both sides.** A value added to a request's enum widens it (safe to send); one removed may be
  the value the script sends. A value added to a response's enum can surprise a check.
- **Unknown members pass through.** proto3 once dropped unknown fields, losing data through intermediaries.
  Clients and the gateway keep members they don't know.
- **Tooling keeps people honest.** `buf breaking` exists because hand review missed breaks. Stage 2 should use
  published JSON Schema compatibility rules (Confluent Schema Registry checks backward and forward compatibility
  of JSON Schemas), not rules of our own.
- **Old clients live forever.** Mobile apps never all update, so protobuf's rules assume any old version still
  runs. A pinned script is the same: the server can't know who still holds an old form, which is why the client,
  not the server, decides.

## Open

- What a lenient replay reports when it acts on an unchanged form in a changed set: a warning in the
  capture and the report, with the set's two integrity values. (Built 2026-10-19: commit 1c16f7d.)
- Decided 2026-10-18: a form's integrity leaves out its title and description, so lenient replay
  survives a retitled form.
- Links: the same two modes for a followed relation (present, and its target template unchanged).
