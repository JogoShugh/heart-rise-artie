# Idea: the release, step by step, from plain words to the kept script, with zero gaps

**Status:** idea only. Raised by the author on 2026-11-21. Nothing built.

**The ask:** people need to see each transformation, one step at a time. The Goal in Pictures deck jumps from the
goal to the kept script. The step in between, the intent script, isn't shown for the release anywhere. The author
wants it shown with zero gaps: every form real output, none mocked up, none with a known defect.

## The chain, as it is today

1. **The goal in plain words:** `exhibits/merge-watch-release/release.goal.md`.
2. **Parsed, nothing sent:** `artie run release.goal.md --plan-only` prints 7 steps and their structure (`at`, `as`,
   `once … answered`, `if` / `otherwise`).
3. **Walked once with Claude:** each line becomes acts, and the walk is captured (`release.capture.yaml`).
4. **The intent script:** `artie generate … --format yaml --intents` writes `kind: intent`. It has the walk's shape,
   with each act still in words (`intent:`, `inferred:`) for a model to choose on every run.
5. **The kept script:** `artie generate … --format yaml` writes `release.heart.yaml`. Every act is a relation with
   its fields, held to its pins, and runs with no model.

The intent script comes from the walk, not before it, as the kept script does.

## Where it is shown today

- The Merge Watch, not the release: docs/reports/local-models-yaml/ puts the goal, the intent YAML and the kept YAML
  side by side, and qwen3 30B-A3B ran the intent YAML 7 of 7 on a laptop. docs/reports/local-models/ is the same
  with the scripts as Kotlin (16 of 20).
- The release: nowhere.

## Gaps found on 2026-11-21

Running `artie generate --intents` on today's `release.capture.yaml` (into a scratch folder only) gave:

- `ask:` with no relation named, at both the watcher and Jenkins steps.
- `inputs:` listing only `scm`. The goal's other inputs (`pull`, `owner`, `repo`, `watcher`, `jenkins`, `dev`, …)
  are missing.
- Values left as `{merged.merge_commit_sha}`, `{smoke.passing}` and `{smoke.of}`, where the script's own form is
  `${…}`.
- The capture is the real-GitHub walk (pull request 2328), not 501 on the local forge, where the deck's other
  slides come from.

A related gap, in plain `artie generate` (the kept form): with the goal line "At {pr}, merge it by squashing", the
kept script enters `${pr.@self}` but never keeps `pr`, so its replay stops before the first request
("${pr.@self} reads nothing: no input, and nothing kept, is named pr"). Seen in
exhibits/merge-watch-release/work/by-hand/first-try/ on 2026-11-21. The release's own kept script doesn't show it,
so it may depend on the goal's shape. Not yet pinned down.

## Steps toward zero gaps

1. A failing scenario for each gap above, then the fix, through the gate (the author switches it on).
2. Walk the release once more on the local forge for 501, and generate both forms from that one walk.
3. Check the intent script runs: once with Claude, once with a local model, as the Merge Watch's did.
4. A slide in The Goal in Pictures, right after the goal: one line ("merge it by squashing") through all five forms,
   side by side, one form per click. Then the whole goal the same way, if one line isn't enough.

## Open

- Should the slide show `--plan-only`'s parse as its own form, or fold it into the goal?
- Whole goal or one line first?
