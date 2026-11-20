# Pending: waiting on the author

As of 2026-10-27. In order of what to pick up next; reorder as we go.

1. **"Wayfinding to Rails."** The piece on navigation and discovery versus orchestration, with HEART between. Its
   shape is approved: what ARTIE can do, on the merge watch only, concepts over metrics. It moves along the dial:
   goal in plain text, reasoned run, kept script, guard modes, and bare at the end, now real
   (`exhibits/merge-watch/merge.bare.kts`). Waiting on: go-ahead to build it. After approval, it is copied to
   `docs/reports/wayfinding-to-rails/`.
2. **Onboarding Map.** The artifact (https://claude.ai/artifact/C3MpLm9soTddCVbSYvbfzb). Waiting on: approval. Then it
   is copied into `docs/reports/` and committed.
3. **"The Other R's".** Waiting on: the author's corrections.
4. **Untracked files.** `ONBOARDING.md`, `ROLLOUT.md`, `client/runs/`, `client/scripts/generated/*`, `runs/`, and run
   output under `exhibits/` (dependabot-sim, listeners-live, merge-watch). Waiting on: which to commit, which to
   ignore. `exhibits/merge-watch/runs/script-1.yaml` is 45 MB and is left out unless the author says otherwise.
5. **Buster.** An uncommitted w3id fix in `buster/`. Waiting on: commit or drop.
6. **The scratch repository** (JogoShugh/heart-github-test). It has many test pull requests, comments, and closed
   watch issues (#827, #828, #830). Waiting on: whether to clean it, and how much.
7. **A native Python merge watch** (spec/ideas/python-merge-watch.md). Waiting on: whether a Python written by a
   separate Claude run, as a subject of the experiment, is allowed under the no-new-Python rule. The author may run
   this experiment in another session first.

8. **Wayfinding to Rails, appendix: a larger, interleaved run.** Every rung (bare, off-the-rails, look-then-leap,
   lenient, strict) on one fixed scene, several rounds, interleaved, for firmer per-pass numbers than the single
   runs of 2026-10-27. Agreed to do later.

9. **Look-then-leap that sees past the entry: built 2026-10-27** (72818d6, d6c2caf, 3e7e5f2, 8038761). The index's
   `hr:manifest` lists every declared form's integrity by relation, with a digest; `--look used` (default) or
   `--look everything`. Live on the changed-form scene: used leapt, everything stayed lenient. `merge.kts` made again
   from goal run 3, its lock file keeping the digest; looking at everything, it leaps (8e62a7a).

10. **A new system deployed mid-run (idea, spec/ideas/new-system-mid-run.md).** A closing demo for Wayfinding to
    Rails: a build service appears during the merge watch, the gateway links each pull request to its build, and ARTIE
    follows the link with no change to ARTIE, the script or the goal. Plan only.

11. **A fat-prompt ARTIE (idea, spec/ideas/fat-prompt-artie.md).** ARTIE's behaviour written as a skill, Claude
    doing the merge watch with only `curl` and inference, beside ARTIE's wayfinding and its kept script: the same
    scene, measured for correctness, tokens, time and repeatability. Idea only.
12. **Script levels in the README (in progress).** `intent` for a person's words, `inferred` for a model's
    (heart-script and the recorded watch done); next, the generator's `inferred` and `--intents`, then a fresh goal
    run and a README section from plain text to bare.

## Open questions, not blocking

- Why the gateway answers 422 when reading pulls/813, which GitHub serves directly.
- Unbuilt ideas, in spec/ideas/: the five ways, the HTML dialect and volleyball, orchestration across systems, the
  onboarding items, an Arazzo export of a bare script.
