# Idea: an ARTIE daemon

**Status:** future idea only, 2026-11-02. No plan, no code. From the author, while weighing
spec/PLAN-KEPT-SCRIPTS-AS-DATA.md.

## Why

Every `artie run` of a `.kts` pays about 2 s before its first call: the Kotlin script engine starting, then
compiling the script (ScriptHost.kt makes a new engine per run, with no compile cache). Measured 2026-11-02:

| Run | Time |
|---|---|
| `artie --help` (the JVM, nothing else) | 0.18 s |
| A goal file, listing its inputs | 0.2 s |
| A one-line `.kts`, no calls | 1.8 to 2.2 s |
| `exhibits/own-controls/scripts/heart.kts`, a full run (3 calls, local) | 2.6 to 2.7 s |

## The idea

A long-running ARTIE, as Gradle has its daemon. `artie run` becomes a thin client that hands the daemon a script
and its inputs over a local socket. Kept warm across runs:

- the script engine, and compiled scripts by hash, so a repeat run doesn't compile;
- the JIT;
- the HTTP cache of affordance sets and schemas, so a run starts from 304s; for many resources in the same state,
  one fetch, then revalidation;
- open connections.

It keeps `.kts`: speed without a new format.

## Costs

- **Security:** the daemon holds the person's tokens; its socket must be the person's alone.
- **Isolation:** caches and credentials kept apart between runs, people and services.
- **Lifecycle:** start, stop, a stale daemon after an upgrade.
- **Memory** while it runs.

## Already near

heart-mcp is a long-running JVM. If `heart_do` (spec/PLAN-HEART-MCP-ARTIE-BACKED.md) runs ARTIE inside it, that
process is a daemon already.
