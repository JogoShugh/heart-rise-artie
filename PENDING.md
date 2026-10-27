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
   `--look everything`. Live on the changed-form scene: used leapt, everything stayed lenient. Left: regenerate
   `exhibits/merge-watch/merge.pins.yaml` from a fresh goal run so it keeps the digest (the live everything run used
   a demo copy, `runs/demo-digest.pins.yaml`).

## Open questions, not blocking

- Why the gateway answers 422 when reading pulls/813, which GitHub serves directly.
- Unbuilt ideas, in spec/ideas/: the five ways, the HTML dialect and volleyball, orchestration across systems, the
  onboarding items, an Arazzo export of a bare script.
