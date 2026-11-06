# Idea: the typed script layer's open choices

**Status:** idea only, 2026-10-17. To settle before the typed layer
(`spec/HEART-SCRIPT-TYPED.md`) is built, after steps 1 to 3 of
`spec/PLAN-ARTIE-SCRIPTS.md`.

1. **Values a server may extend.** A local enum fails to decode a value it
   doesn't know (a lock reason GitHub adds later). Proposed: enums for what
   a client sends; for what it reads back, string-backed types or enums with
   an `Unknown` fallback, the fallback the default in read types.
2. **A field the form no longer has.** A local type sends a field the form
   dropped. Proposed: strict by default (a client failure, `InvalidInput`,
   naming the field); lenient on request (send only what the form declares,
   and say in the run report what was left out).
3. **An early check.** `verify(Github)` at the entry: the profile still
   describes every relation the script names, drift reported before the
   first act. Advisory only; the manifest at each step stays the authority.
