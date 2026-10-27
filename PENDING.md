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

## Open questions, not blocking

- Why the gateway answers 422 when reading pulls/813, which GitHub serves directly.
- Unbuilt ideas, in spec/ideas/: the five ways, the HTML dialect and volleyball, orchestration across systems, the
  onboarding items, an Arazzo export of a bare script.
