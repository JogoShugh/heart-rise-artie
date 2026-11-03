# Plan: a skill that has Claude use ARTIE, in Docker, for work against HEART services

Status: proposed, 2026-11-03. Plan only; nothing built. Follows the code mode runs
(docs/reports/wayfinding-through-change/) and spec/ideas/code-mode-runs-artie.md.

## Why

The runs showed where each part belongs:

- **A model in the middle of every run** (code mode, on endpoints or on HEART) finds its way again each time, needs a
  sandbox for its code, and sometimes pairs a fresh ETag with a stale value: one lost update that no server can see.
- **ARTIE in the middle, the model at the edges** finds the way once, keeps it as a `.heart.yaml` held to its pins,
  runs it with no model and no sandbox, and repairs one step when the service changes.

A skill puts that division of labour into Claude Code: Claude decides what to do and finds the way the first time;
ARTIE does the acts, the same way every time, held to the server's controls. Claude never writes raw HTTP against a
HEART service.

The `heart-client` skill in `~/.claude/skills` is the opposite: a fat-prompt prototype (protocol v0.2) in which
Claude does every request itself with `curl`. The new skill replaces it, at the author's word.

## What Claude should do, by situation

| Situation | What Claude does |
|---|---|
| A service answers with a HEART profile (`Link: rel="profile"` naming `https://w3id.org/heart-rise`, `_forms` or `_templates` with `hr:` relations), or the user names one | Use this skill |
| A one-off task, in words | Write a goal file; `artie run NAME.goal.md --reasoner claude`; report what the run did |
| A task the user will want again | The same, then `artie generate` the trace as `--format yaml`: a kept `.heart.yaml`, its pins and a config with `on-change: intent`, `keep: propose` |
| The task again | `artie run NAME.heart.yaml`: no model unless a form changed |
| The run stops (exit 3 refused, 1 not met, 2 stopped before) | Read `--output json`'s reason; say it; never retry by hand with raw HTTP |
| A kept repair proposed (`would keep …`) | Show the diff to the user; keep it on their word, or set `keep: auto` where they allow |
| A `review:` value in a generated script | Ask the user, or make it a reference (`${state.role}, retired`), before keeping the script |
| A service that isn't HEART but sits behind the HEART gateway (GitHub) | Run ARTIE against the gateway's address, so the owner's controls apply |
| Plain JSON with no hypermedia | Not this skill |

## The parts

1. **An ARTIE image.** `client/artie-cli/Dockerfile`: the `installDist` output on an Eclipse Temurin 22 JRE image,
   built locally (`docker build -t artie:0.5 .`). A native, YAML-only image later (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md,
   step 6) would start in milliseconds. Pushing it to a registry is a publication: only on the author's word.
2. **A wrapper, `artie`, in the skill's folder.** `docker run --rm -v "$PWD:/work" -w /work` with the model key passed
   through from the environment (never written to a file) and the network the service is on. On macOS a service on
   the host is `host.docker.internal`; a service in Docker is reached on its network (`--network NAME`).
3. **`SKILL.md`.** Its description is what makes Claude reach for it: HEART services, the HEART gateway, repeatable
   work against an API that offers its controls. Its body: the table above; the loop (look, goal, run, generate,
   keep, run); the rules (no raw HTTP writes to a HEART service; never hand-edit pins; review values the model chose;
   `keep: propose` by default); the exit codes; where files live.
4. **Where the work lives.** `.artie/` in the user's repository: goal files, kept scripts, pins, configs, traces.
   Reviewable, diffable, committed like code.
5. **References, loaded only when needed:** the goal syntax, the `.heart.yaml` vocabulary
   (spec/schemas/heart-script.draft.schema.json), the format (spec/HAL-SCHEMA-FORMS-HR.md), the exit codes.

## Decisions for the author

- **Where the skill lives:** the user's skills (`~/.claude/skills/artie`), the repository's (`.claude/skills/artie`),
  or a plugin others can install.
- **Who reasons on a goal run:** ARTIE's own reasoner, with the model key passed into the container (works today), or
  Claude itself, through a run that pauses and asks the caller for each choice (not built; no key in the container,
  and no second model).
- **The image:** JRE now, native later; a local build only, or published.
- **`keep:` by default:** `propose` (a person approves each repair) or `auto` (safe repairs kept).
- **The old `heart-client` skill:** retire it, or keep it for protocol teaching only.

## Steps

0. The author's decisions above.
1. **The image**, with a smoke check: `artie --help` in the container, then a kept `.heart.yaml` run against
   heart-sims' employees service on the bench network.
2. **The wrapper**, tried from a scratch repository: a goal run, generate, a kept run, a release, a kept repair.
3. **`SKILL.md` and its references**, written for the situations in the table.
4. **An evaluation in the bench** (exhibits/mcp-over-openapi): Claude Code headless (`claude -p`, JSON output) on t1
   and t3 through the release sequence, with the skill and without it. Measured: whether Claude used ARTIE or wrote
   its own HTTP; right runs; lost updates; Claude's tokens; ARTIE's model calls; the second run's cost (a kept script,
   or the way found again).
5. **The report**, in the frame of docs/reports/wayfinding-through-change/: the same process, now with Claude Code
   choosing the architecture.

## Not in it

- An MCP server for ARTIE (spec/PLAN-HEART-MCP-ARTIE-BACKED.md): another way in, planned separately.
- Code mode running ARTIE from its sandbox (spec/ideas/code-mode-runs-artie.md): the same image would serve it.
