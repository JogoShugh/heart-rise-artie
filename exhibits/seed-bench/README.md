# The seed bench

A greenhouse bench, played on the scratch repository `JogoShugh/heart-github-test` through the HEART gateway
(spec/PLAN-SEED-BENCH.md). The goal file, `../goal-syntax/goals/seed-bench.goal.md`, says in plain words what a
watch over the bench should do:

- answer each alert from the bench sensors once, and hand it to the grower on duty;
- merge each sowing plan once it passes the bench check, and say why when it can't;
- close each tray once it is transplanted;
- leave alone anything a person labels `hold`.

ARTIE runs it with Claude deciding each part once, the first time it comes up. `artie generate` writes those
decisions as a script, `generated/seed-bench.kts`, and the script then does the same work alone, with no model.

## The pieces

- `bench-setup.kts`: the stage. A base branch `bench-main`, a required check `bench-check` (12 trays at most),
  the labels, and two trays already sown.
- `bench-clear.kts`: clears `bench-main` of the plans merged, when the bench fills up.
- `plan.kts`: a sowing plan, as a person would open one. `over=true` makes one too big for the bench.
- `sensor.kts`: an alert, as a bench sensor would open one.
- `runs/`: each run's output. `b` runs had Claude deciding; `g` runs are the generated script alone.
- `generated/`: the script made from run b19, its lock file, and how each value in it was derived.
- `results.yaml` and `report.kts`: what happened, and the page made from it
  (`docs/reports/seed-bench/index.html`).

## Running it

From this folder, with `artie` on the path (`. ../../client/scripts/.env`) and the gateway on `localhost:8392`:

```
artie run ../goal-syntax/goals/seed-bench.goal.md owner=JogoShugh grower=JogoShugh --reasoner claude --rung L2 \
  --trace runs/bench-1024-bN.yaml
artie generate runs/bench-1024-bN.yaml --out generated/seed-bench.kts --script ../goal-syntax/goals/seed-bench.goal.md
artie run generated/seed-bench.kts owner=JogoShugh grower=JogoShugh --rung L2
```

A run ends when its watch issue, "Bench watch, week 43", is closed.
