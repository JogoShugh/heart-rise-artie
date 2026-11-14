# Plan: fill targets from form fields, as HAL Schema Forms does

Status: **superseded 2026-10-08.** Kept as history. The author chose to keep
affordance sets and write a H.E.A.R.T. profile of HAL Schema Forms instead:
see `spec/HAL-SCHEMA-FORMS-HR.md`. Template variables are now bound by declared
`hr:templatePointers` (JSON Hyper-Schema), not by `readOnly` fields, so option
B below is not being built.

## 1. The problem

The server's Layer 2 affordance set (rungs L1 and L2) gives targets such as
`/beds/{id}/actions/water-cell`, marked `templated: true`. SPEC §6.9.1 and its
`[0.5]` resolution rule tell the client to fill `{id}` from the top-level `id`
property of the Layer 1 resource (`"id": 2` gives `/beds/2/...`).

That is not how HAL Schema Forms fills a template. Its text says:

> "When target link is a template it MUST be expanded using the form fields in
> order to resolve it into a actual URL."
>
> "Form fields must be converted in to a set of variable definitions whose
> values are the field values encoded using the form value transcoding rules."

So a variable comes from **the form's own fields**, not from the surrounding
resource. A conformant HAL Schema Forms client given our Layer 2 form would
find no `id` field and could not expand the target.

What is and is not affected today:

| Where | Target | Valid? |
|---|---|---|
| HAL-FORMS, L0 | Concrete URL (`/beds/2/actions/water-cell`) | Yes. HAL-FORMS `target` is a plain URL; it has no templated targets. Its only `templated` flag is on a property's `value`. |
| HAL Schema Forms, L0 | Concrete URL (`resolvedFormsForBed` fills `{id}` on the server) | Yes. |
| HAL Schema Forms, L1 and L2 | Template filled from Layer 1 state | **No.** The H.E.A.R.T. lookup replaces the HAL Schema Forms rule. |

## 2. What starbornes did

`~/repos/starbornes` (commits `0b8936a`, `dcba3ae`, `3146f33`, and
`hal-schema-agent.md`) follows HAL Schema Forms closely:

- **One template for every bed.** For example
  `/beds/{bedId}/focus/cell/{row}{column}`, the same on every bed
  (`one-template-for-every-bed.feature`).
- **The bed is a field, fixed the JSON Schema way.** In `FormFields.bed`:
  `"bedId": {"type": "string", "format": "uuid", "const": "<id>", "default": "<id>", "readOnly": true, "title": "Bed", "description": "The bed, fixed by where the form came from"}`.
  `const` is the only valid value, `default` is where to start, and `readOnly`
  says the server manages it. A care form fixes its `location` the same way
  when the focus decides the cells.
- **The target is filled from fields, fixed ones included.** The client
  collects every field value, expands the template from them, and sends the
  same values in the body.
- **Bounds in the schema keep addresses legal.** GET forms (`go-to-row`,
  `go-to-column`, `go-to-cell`, `refresh{?recent}`) bound rows by `enum` and
  columns by `minimum` and `maximum`. In the agent run (`agentlog-2.md`,
  turns 6 and 9), F9 and an out-of-range C3 were refused before any request.
- **Every form and field has a `title` and a `description`,** with units and
  what happens when the field is left out.
- **Schemas declare `$schema` (JSON Schema 2019-09).**
- **HAL answers name the profile in the media type:**
  `application/hal+json; profile="https://github.com/jbadeau/hal-schema-forms"`,
  plus `Link: rel="profile"`.
- **Errors use `application/vnd.error+json`,** with a JSON pointer `path` for
  each field and an `about` link to re-read.
- **The server honours open fields and overrides only fixed ones**
  (`3146f33`, found by the interactive agent run).
- **The agent skill's hard rule:** never build an address except by filling a
  server-given template with values that pass the schema; never change a
  `const`.

## 3. The tension: `const` against sharing

H.E.A.R.T. adds something starbornes does not have: at L1 and L2, one Layer 2
affordance set is **shared by every bed in the same state**, fetched once and
cached for a year (§6.9.2, §6.10.4). That is only possible if the set has no
instance identity in it.

A `const` bed id is instance identity. Put `"const": 2` into the shared set and
it becomes one set per bed. The L3 sharing proof then fails. If the id is in
the Layer 3 schema too, schema sharing goes as well.

So starbornes' per-bed `const` cannot simply be copied into Layer 2.

## 4. Options

**A. `const` everywhere (pure starbornes).** Every form carries the bed's id as
`const`, at every rung. This is fully HAL Schema Forms. But Layer 2 and Layer 3
become per-bed, and the sharing property and the L3 proof are lost. That makes
the server the "concrete profile" of §6.9.1 in all but name.

**B. Two forms of one rule (recommended).**
- **L0** (one response per bed, so nothing is shared anyway): every fixed field
  carries `const`, `default` and `readOnly`, exactly as starbornes does. The
  target is a template filled from fields. This is plain HAL Schema Forms, and
  a client that knows nothing of H.E.A.R.T. can use it.
- **L1 and L2** (shared Layer 2): the field is declared with **no** `const`.
  For example:
  `"id": {"type": "integer", "readOnly": true, "title": "Bed", "description": "The bed this form acts on, taken from the resource that linked to the form"}`.
  The H.E.A.R.T. rule becomes:

  > A `readOnly` field with no `const` takes its value from the Layer 1
  > top-level property of the same name.

  The client then fills the **field**, and expands the template from the
  fields as HAL Schema Forms requires. The H.E.A.R.T. part is now one
  declared binding per field, not a lookup hidden inside URL expansion. The
  set stays free of instance identity, so sharing holds.

  L1 and L2 are already H.E.A.R.T. extensions (forms by link, content
  addresses), so a binding rule there is honest, provided §6.1 says so.

**C. Concrete targets at L1 and L2.** No templates at all. This is simple and
valid, but every bed gets its own Layer 2, so sharing is lost. It is rejected
for the same reason as A.

## 5. What B changes

### Spec (`spec/SPEC.md`)

1. **§6.9.1:** replace the `[0.5]` resolution rule. Template variables come from
   form fields (HAL Schema Forms). A field that a shared Layer 2 cannot fix
   (instance identity) is declared `readOnly` without `const`, and binds to
   the same-named Layer 1 property. At L0 the server SHALL give such fields
   `const` and `default`.
2. **§6.3:** the client sends the fixed fields in the body as well. The server
   SHALL check that a fixed field in the body matches the target it was sent
   to. A mismatch is a `422`. Open fields the client sends are honoured
   (starbornes `3146f33`).
3. **§6.8:** the Reason step sees **open** fields only. Fixed and bound fields
   are filled by deterministic code. This extends "the reasoner never sees a
   URI" to "the reasoner never sees identity".
4. **§6.1:** reconcile profile identification. HAL Schema Forms names its
   profile as a media-type parameter, while 0.4 dropped that parameter for
   `heart-rise` on connegp grounds. Proposal: send the base dialect's
   parameter where its own spec asks for it, and keep `Accept-Profile` and
   `Link: rel="profile"` for the `heart-rise` profiles.
5. **§6.2 (errors):** decide between `application/vnd.error+json`
   (starbornes, which records it as what HAL Schema Forms requires; verify that
   against the HAL Schema Forms text) and the current ad hoc
   `{"violations": [...]}` body.
6. **Form quality, SHOULD level:** a `title` and a `description` on every form
   and field, with units, and `$schema` on every schema (the JSON Schema draft
   to be confirmed against HAL Schema Forms).
7. Clear the `[0.5]` marker from each passage this plan settles, and write the
   rule's content wherever the code or other documents cite it today.

### Server (`server-spring`)

- `JsonSchemaDialectSupport`: L0 forms get a fixed `id` field (`const`,
  `default`, `readOnly`) and keep a template target. The L1 and L2 sets and
  schemas get the bound `readOnly` field without `const`. Add `title`,
  `description` and `$schema` throughout.
- `ActBodyBinder` and the controller: accept the fixed field in the body, and
  return `422` when it does not match the path.
- Content addresses change, because the bytes change. Regenerate the expected
  hashes and the captured journal.
- Optional, later: navigation GET forms such as `go-to-bed`, with bounds,
  following starbornes.

### Client (`client`)

- `UriTemplate.expand(template, fields)`: expand from the form's fields, not
  from Layer 1. Possibly add RFC 6570 `{?var}` for GET forms.
- New `FormFill`, a deterministic step that runs before the act:
  1. `const` fields take their `const`.
  2. `readOnly` fields without `const` take the Layer 1 value.
  3. Open fields come from the caller (or the reasoner).
  4. `SchemaCheck` runs on the whole set.
- `SchemaCheck`: add `const` (a field given another value is refused) and
  `readOnly` (the caller may not set it).
- `ArtieAgent`: `Offer` shows only open fields. A selection that names a fixed
  field is rejected as "not in the schema".
- `HeartResponseParser`: read vnd.error, if that is chosen.

### Tests (Gherkin first, under the gates)

- Server:
  - At L0, a form fixes `id` with `const`, `default` and `readOnly`, and its
    target is filled from fields.
  - At L1 and L2, two beds in the same state still share one set, which has
    no `const`.
  - A body whose `id` does not match the path is a `422`.
  - Open fields are honoured.
- Client:
  - Expansion uses fields only.
  - A `const` cannot be overridden.
  - A bound field comes from Layer 1.
  - The reasoner is never shown a fixed field.
- A cross-check scenario: a plain HAL Schema Forms client, with no H.E.A.R.T.
  rules, can act on an L0 response.

## 6. Order

1. Spec text for B (§6.9.1, §6.3, §6.8), with the `[0.5]` markers it settles
   cleared.
2. Client: `FormFill`, `SchemaCheck` (`const` and `readOnly`), field-based
   `UriTemplate`, and `Offer` trimmed to open fields.
3. Server: L0 fixed fields; L1 and L2 bound fields; the body check; titles and
   descriptions.
4. Regenerate the hashes, the journal and the live run. Update
   `CONFORMANCE-GAPS.md`.
5. Separately: profile parameter, error format, GET navigation forms.

## 7. Questions for the author

1. **B, or A?** B keeps sharing and is plain HAL Schema Forms at L0 only. A is
   plain HAL Schema Forms everywhere and gives up sharing.
2. **Field name:** `id` (matches today's Layer 1) or `bedId` (as starbornes
   does, which means renaming the Layer 1 property)?
3. **Errors:** move to `application/vnd.error+json`?
4. **Profile parameter:** send HAL Schema Forms' media-type `profile`
   parameter as well?
5. **Scope:** add GET navigation forms to the garden server now, or later?
