# Plan: a seed bench run from GitHub

Status: proposed, 2026-10-24. Nothing built for it yet. It uses only the goal words and the GitHub resources the
watch runs on today (spec/PLAN-WATCH-GOALS.md, spec/PLAN-LISTENERS.md), and names what it still needs.

## The bench, as GitHub resources

A greenhouse team sows seed trays on benches: lettuce, chard, beets, microgreens, 128 cells to a tray. The work is
kept in one repository, `greenhouse`, with no new kind of resource:

- **A tray** is an issue labelled `tray`, for example "Tray 14: Red Sails lettuce, 128 cells, bench 2". Its stage
  is a label: `sown`, `germinated`, `true-leaves`, `hardening-off`, `transplanted`. The grower who tends it is its
  assignee.
- **A week's sowing** is a milestone. Each tray is a sub-issue of that week's sowing issue.
- **A sowing plan** is a pull request labelled `sowing` that adds rows to `trays.yaml`. Merging it means the trays
  are sown. A required check counts the cells on each bench, and fails a plan that overfills one, so that plan
  can't be merged.
- **An alert** is an issue labelled `alert`, opened by a sensor stand-in or a grower: "Bench 2 fell to 4 °C
  overnight", "Damping-off on tray 14".
- **A person** labels anything `hold` to have it left alone, and closes the watch's issue to end the watch.

## The goal file

`exhibits/goal-syntax/goals/seed-bench.goal.md`:

```
---
start: https://localhost:8392/
inputs:
  owner:  { question: "Whose greenhouse repository?" }
  repo:   { default: greenhouse, question: "Which repository?" }
  week:   { question: "Which sowing week?" }
  grower: { question: "Who is on duty at the bench?" }
  hold:   { default: hold, question: "Which label means a person objects?" }
manners: alongside-others
---

Keep the seed bench moving for week {week}: sow each plan once it passes the bench check, answer
every alert once and hand it to the grower on duty, and close each tray once it is transplanted.
Keep watching until the bench watch's own issue is closed.

## Working alongside others

Growers move trays, relabel them and write on them by hand. What they decide stands.

- Don't undo or write over anything someone else changed since you looked.
- Leave anything labelled {hold} alone, and say so once.
- Nothing you write may claim something that isn't so: "sown" only once the plan is merged.
- Don't say again what has already been said.

## The work

- In {owner}/{repo}
  - Open an issue titled "Bench watch, week {week}", saying "Close this issue to stop the watch." `as watch`
  - Whenever an issue labelled alert changes `as alert`
    - If it's labelled {hold}, leave it, and say so once.
    - Say on {alert} "Seen by the bench watch; {grower} will check the tray today.", unless it's said already.
    - Assign it to {grower}.
  - Whenever a pull request labelled sowing changes `as plan`
    - If it's labelled {hold}, leave it, and say so once.
    - Once it can be merged, merge it by squashing.
      Or give up after 20 minutes.
      Otherwise, say on {plan} why it wasn't merged.
  - Whenever an issue labelled tray changes `as tray`
    - If it's labelled transplanted, close it.
- Until someone closes {watch}

## Done when

- Every alert during the watch says it was seen, once, and is assigned to {grower}
- Every sowing plan is merged, or says why not
- Nothing labelled {hold} was changed
```

## What each line runs on

- The front matter's inputs and manners: as in the Dependabot goals (runs g9 to g17).
- The three Whenevers side by side: one watch, three listeners, each on its own thread with its own client. Each
  Whenever's first look is made in turn, in the goal's order, and each may reach its list from the start.
- "If it's labelled {hold}, leave it, and say so once": an If that leaves the member, said once per resource. The
  planner is told the check holds exactly when the condition is true (after run g15).
- The alert's comment and assignment: a chain of acts, decided at the first alert and replayed for the others. The
  watch's own comment doesn't count as a change to answer (after run g13).
- "Once it can be merged … Otherwise, say on {plan} …": the wait, its give-up, and the Otherwise. The comment lands
  on the pull request itself, which offers `github:comment` (after run g14).
- "If it's labelled transplanted, close it": an If that acts.
- The Until, and Done when.

## What it still needs

1. **"…, unless it's said already" can't be trusted yet.** It is a chain: read the comments, check, comment. The
   planner put the comment before the check in runs g13 and g14, and after it in g15. A watch doesn't answer its
   own comment, so a new alert still gets one; but a grower's comment on an alert replays the chain, and a chain in
   the wrong order says it again. Either the runner keeps a chain's check before the act it guards, or an If may
   read a linked list (the comments) as well as the member.
2. **Matching issues by their labels is unproven.** It works on pull requests. A scenario on issues comes first.
3. **A watch that runs for days.** With give-ups of a day, the run must survive a restart. `--remember` isn't kept
   per listener yet, so a restarted watch answers everything again. Until the journal (spec/PLAN-LISTENERS.md,
   slice 5), give-ups stay in minutes.

Not in this plan, each needing words or sources not built yet:

- "No germination after 10 days: label it resow": Once waits for an act to be offered, not for a label.
- "Every morning at 7": a timer source (slice 2).
- "When the sensor says frost": the words `by` and `says` (slice 4).
- Emailing the grower: the mail gateway (slice 7).
- Moving a tray from one stage to the next: unlabel then label, as one chain, to be tried first.

## Stand-ins

Each is Kotlin, under `artie run`, in `exhibits/seed-bench/`:

- `bench-setup.kts`: the labels, the week's milestone and sowing issue, `trays.yaml`, and the bench-capacity
  check (a required Actions check, as `exhibits/dependabot-sim/checks.kts` makes one).
- `plan.kts`: a sowing pull request; `over=true` overfills a bench.
- `sensor.kts`: an alert issue.
- `person.kts`: as in dependabot-sim, for `hold`, `transplanted`, and closing the watch.

## The run

1. Setup; two trays already sown; the watch starts.
2. `plan.kts` adds three trays of Red Sails: the check passes, and the watch merges the plan.
3. `sensor.kts` reports "Bench 2 at 4 °C": the watch comments once and assigns the grower on duty.
4. `plan.kts over=true` adds 60 trays of chard: the check fails; after the give-up, the watch says why on the
   pull request.
5. A grower labels a plan `hold`: the watch leaves it, with one note.
6. A grower labels tray 14 `transplanted`: the watch closes it.
7. A grower comments on the alert: the watch says nothing again (needs point 1 above).
8. A person closes the watch's issue: the run ends, with its Done when checks.
