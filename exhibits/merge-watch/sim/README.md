# The merge watch, rerun on heart-sims' GitHub

`merge.goal.md`, the merge watch's goal, pointed at heart-sims' small GitHub (`http://localhost:8098/`, run as
`docker run -p 8098:8099 heart-sims 8099 github.test`), rerun on 2026-11-02 so its capture keeps the watch as data
(spec/PLAN-KEPT-SCRIPTS-AS-DATA.md). Pull request 482 was labelled `dependencies` first; the watch's issue was closed
by hand once 482 was merged, as a person would.

| File | What |
|---|---|
| `merge-sim.trace.yaml` | The goal run, with the model: 14 model calls, 26,276 input tokens (16,018 from the cache), 2,564 output |
| `merge.kts`, `merge.heart.yaml` | What `artie generate` wrote from it, as Kotlin and as data (`--format yaml`) |
| `merge.pins.yaml`, `merge.derivation.yaml` | The lock file both are held to, and how the Kotlin was derived |
| `runs/replay-data.trace.yaml` | `merge.heart.yaml` run on a fresh small GitHub with no model: it merged 482, 0 model calls |

    artie run merge.heart.yaml owner=acme repo=web
