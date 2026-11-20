# Idea: a compile cache for `.kts` scripts

**Status:** future idea, 2026-11-02. No plan, no code. Chosen by the author for later, beside
spec/PLAN-KEPT-SCRIPTS-AS-DATA.md (which goes ahead for generated scripts).

## Why

Measured in that plan's step 0a: a `.kts` run spends 1.6 to 1.7 s warming up the Kotlin compiler, once per
process, before any call. Compiling a script once the compiler is warm takes 0.03 to 0.17 s. ARTIE makes a new
script engine for every run and keeps nothing (ScriptHost.kt:26-39).

## The idea

The first run of a script compiles it and saves what the compiler made, on disk, under a hash of the script's text,
its default imports and ARTIE's version. A later run with the same hash loads the saved classes and never starts
the compiler. A change to any of the three makes a new hash, and the script compiles again.

- Kotlin's scripting host supports this; ARTIE uses the JSR-223 engine today, which doesn't.
- It helps hand-written `.kts`, which stay Kotlin whatever happens to generated scripts.
- Expected: about 1.6 s saved a run, from the second run on. Not measured.

## Limits

- The JVM still starts every run (0.19 s).
- It does nothing for a native ARTIE, which can't run `.kts`.
- A cached script is still code.
- The cache is code that runs: it must be the person's alone, and checked against its hash before it is loaded.
