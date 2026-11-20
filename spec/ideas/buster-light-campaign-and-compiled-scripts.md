# Idea: a light Buster campaign in every full client run, and scripts compiled ahead

Noted 2026-11-15. Not started.

## Why

- **Buster is not in the regression.** `./gradlew test` in `client/` runs no Buster campaign; campaigns run only
  by hand (`scripts/buster-campaign.sh`). A chaos agent that runs only when someone remembers guards nothing.
- **Scripts compile on every run.** ARTIE scripts and Buster plugins are `.kts`. `ScriptHost` compiles each one
  through the JSR-223 engine at run time (`dependenciesFromCurrentContext(wholeClasspath = true)`, Power-assert
  plugin), every run. That is the overhead a campaign of many runs pays many times.
- `.kts` stays: people write scripts and plugins in it. It should be one way to run, not the only one.

## Plan

1. **A light campaign in every full client run.**
   - A fixed, small campaign: a few plugins, one seed, against a sim server.
   - It records, plans, arms 2–3 rounds and reads the gate (`/_buster/report?flush=true`), as
     `scripts/buster-campaign.sh` does.
   - A Gradle task wired to `check`: with the full regression, not after every edit.
2. **Where the time goes.** Time one `artie run` and one campaign round, split into compile and run, before
   anything changes. The numbers go here.
3. **Compile ahead; `.kts` remains.**
   - **a. Ahead-of-time jars.** A Gradle task compiles each kept `.kts` to a jar with the same compiler, default
     imports and Power-assert plugin as `ScriptHost`. `artie run` uses the jar when it is newer than the script,
     else compiles as now.
   - **b. A compile cache.** Compiled scripts kept by the hash of their text (and the classpath's), so a run-time
     compile happens once per change.
   - **c. Buster's own plugins as plain Kotlin.** The fixed plugins in `src/main`, compiled with the build;
     `.kts` for plugins people add.
4. **Measure again.** The same timings after 3b and 3a, here beside the first ones.

## Order

1 and 3b first (cheapest), then 3a, the real compile-down to bytecode; 3c when a campaign's plugin set settles.

## Open

1. Which plugins and how many rounds make the light campaign: fast enough for `check`, still a real gate.
2. Whether ahead-of-time jars keep Power-assert's drawn failures the same as run-time compiles do.
3. Where compiled jars live: beside the script, or in a build folder the run looks up.
