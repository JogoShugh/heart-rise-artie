# Idea: map once, then run on the pins (integration between systems)

**Status:** idea only. Raised by the author on 2026-11-04, while drawing how far a server change travels up each
stack. No code, no spec text. It builds on spec/ideas/orchestration-across-systems.md (one script across systems)
and spec/ideas/defaults-from-state.md.

## The angle

An integration moves records from a system we own into another system's acts: new hires from our HR records into
a payroll service, say. Today that mapping is written by hand for each integration, or a model is asked for every
record.

With ARTIE, the target's forms already say what they take: each field's JSON Schema, with `title`, `description`,
`enum`, `hr:options`, `required`. So the mapping can be **inferred once**, from our record's shape and the target's
form, and then **kept as data**, held to the target's pins like any other kept script.

## How it would run

| When | What happens | The model |
|---|---|---|
| The first run | The model reads one source record and the target form's schema, and infers the mapping: `/name` split into `givenName` and `familyName`, `"FT"` to `"full-time"`, `/startedOn` to `hireDate` | once |
| Kept | A `.heart.yaml` step whose `fields` read from the source record (`${hire.name}`), pinned on two sides: the target's form integrity, and the source's shape | none |
| Every later run | Each record mapped by the kept fields; each body checked against the target's schema before it is sent | none |
| The target's form changes | Its pin stops the step before any write; only the changed fields are inferred again; the diff goes to a person | once per change |
| Our own shape changes | The source pin stops it the same way | once per change |

The cost follows the forms, not the records: the same pattern as "Many records, a few states".

## Why it fits HEART

- **The target's controls do the checking.** The mapping is valid against the form's schema before anything is
  sent, and the server refuses what still slips through (422 at the field).
- **It's an anti-corruption layer** in the sense of domain-driven design: our model stays ours, theirs stays
  theirs, and the translation sits between them. Here it is written once by inference and held there by pins, not
  hand-written and silently stale.
- **The goal stays the stable layer.** "Put each new hire on the payroll" doesn't change when either side renames
  a field; the kept mapping does, by a reviewable repair.

## Open questions

- **Transforms.** Kept YAML today sends values and `${name.path}` references. A split name or a code-to-value table
  needs a small, closed set of transforms (a lookup table, a split, a date format), or a decided step per field.
  Not code.
- **The source pin.** What pins our own side: a JSON Schema of our record, or a hash of the field names the mapping
  reads.
- **Semantics.** A mapping can be valid against the schema and still mean the wrong thing: hire date versus start
  date. A person reviews the first mapping once, and `check` facts after each act catch drift.
- **Keeping without a person.** By the rule in force today, a repair is kept automatically only where every value
  it sent is the script's own. Inferred mappings are new values, so the first one is proposed (`keep: propose`),
  not kept.

## To measure, when taken up

A second simulated service (a payroll target with HAL Schema Forms) and a source of N records: tokens for the
first mapping, then per run at 10, 100 and 1,000 records; a release that renames a target field; right records
against a hand-written mapping.
