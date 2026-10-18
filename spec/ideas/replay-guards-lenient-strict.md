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
   `hr:templatePointers`, title, and its schema or schema link. A set's integrity covers the whole set; a
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

## Open

- What a lenient replay reports when it acts on an unchanged form in a changed set: a warning in the
  capture and the report, with the set's two integrity values.
- Whether `title` belongs inside a form's integrity: a retitled form behaves the same. Leaving it out lets
  lenient replay survive retitles; keeping it in is the stricter reading.
- Links: the same two modes for a followed relation (present, and its target template unchanged).
