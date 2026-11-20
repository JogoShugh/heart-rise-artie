# Idea: ArtiePy, a HEART client for code mode to write against

**Status:** idea only, 2026-11-04. No plan, no code. From the author, after the `code-skill-escalate` runs: to make a
kept skill any better, it has to become a generic hypermedia client, coupled to the media type and not to the domain.
That client, in the language code mode writes, is ArtiePy.

**Note:** the author's rule is no Python left for them to own. ArtiePy would be the deliberate exception: a product,
like heart-ts, not a script. Their call when it is taken up.

## Why

The kept skill code mode wrote for t5 (`runs/codemode/skills-escalate/miller-step-leaving/skill.py`) carries the
domain in its code:

| In the skill | What it copies from the domain | What broke, or would |
|---|---|---|
| `if s == "working": giveNotice(...)` | the server's state rule | a new state, or a new rule, is not seen |
| `r["body"].get("next") or r["body"].get("nextPage")` | a guess at paging, written at 6 employees (one page) | at 90, the answer is `{count: 90, items: [20]}`: it stopped at the first page, exit 0, **50 of 90 right** |
| `"position": b["role"]` (t3, after the release) | a field's name | the next rename: a 422, after the write was sent |
| `api.update(id, ...)` | an address built from a template and an ID | a moved resource |

The same search, asked for hypermedia, carries `_links.next`: a registered relation any client follows without
knowing the service. ARTIE's kept script followed it and was right at 90.

## What it is

A Python library that a code-mode program imports instead of the generated `api` module:

```python
import artie

here = artie.enter(bookmark)                       # a saved address the server handed over
for member in here.search("search", name=surname).members():   # follows next, whatever the page size
    member.when_offered(
        give_notice=lambda act: act(),             # only if the form is offered in this state
        dismiss=lambda act: act(),
        otherwise=lambda: None,                    # dismissed: nothing offered, nothing done
    )
```

- **Reads:** HAL Schema Forms (`_forms`, JSON Schema), L2 form sets via `hr:forms`, `hr:templatePointers`,
  `hr:options`, `hr:unavailable`; HAL-FORMS at L0.
- **Acts:** ETag and If-Match on every write; the refusals told apart (428, 412, 409, 422); a form's body checked
  against its schema before sending.
- **Follows:** links by relation and title, `next` through a collection, Location after a new resource. It never
  builds an address.
- **Pins:** each act held to the form it was offered; a changed form stops the step before a write.
- **Runs kept scripts:** the same `.heart.yaml` ARTIE writes and runs (`when-offered`, `decide`, `check`,
  `keep-as`, inputs). A script kept by one runs on the other.

## No drift

The author's rule for the Kotlin DSL and the YAML holds here: one behaviour, two languages.

- **The shared conformance suite** (`implementations/conformance/`) is the gate, as for heart-ts: ArtiePy brings
  its own steps for the same feature files, against the `heart-sims` image.
- **Kept scripts join the suite.** Today the YAML runner's scenarios live in artie-cli (`when-offered.feature`,
  `generate-yaml.feature`, `goal-steps.feature`). Lifting those about running a `.heart.yaml` into
  `conformance/kept-scripts.feature` makes them every client's, ARTIE's first.
- **Kotlin stays the reference.** ArtiePy does not generate scripts or plan goals at first; `artie generate` stays
  the one writer.

## What it would measure

A `code-artiepy` arm in `exhibits/mcp-over-openapi/bench.py`, in the same release sequence and over many records:
code mode keeping skills written on ArtiePy, run by the plain harness.

| The question | Expected |
|---|---|
| Right at 90 from a skill written at 6 | yes: `next` is followed, not guessed |
| The release (a field renamed) | stopped by a pin before the write; the model or ARTIE's words repair it |
| The teammate's change | 412, read again, go on, no model (as ARTIE did) |
| Tokens | near the harness running ARTIE; the skill is shorter, so less to write |
| What the skill holds | relations and inputs only: no states, no field names, no addresses |

If those hold, the endpoint skill and the ARTIE script converge, as the author said: the task part shrinks to data
(which relations, in what order, with which inputs), and the rest is a generic client.

## Order, when taken up

1. The conformance suite's `client` and `acts` features green (reads, links, forms, If-Match, refusals).
2. Pins and members (`next`), then `when_offered`.
3. Kept scripts: lift the YAML scenarios into conformance, then run them green in both ARTIE and ArtiePy.
4. The `code-artiepy` arm, and its rows in the reports.

## Said plainly

- A third client to keep in step. The conformance suite is what makes that affordable; without it, this is drift.
- It reaches HEART services only, or others behind the HEART gateway.
- Code mode writing ArtiePy still runs model-written code in a sandbox; a kept `.heart.yaml` does not.
