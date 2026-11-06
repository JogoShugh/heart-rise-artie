# Idea: code mode that runs ARTIE, an optimization of code mode

**Status:** future idea only, 2026-11-03. No plan, no code. From the author, after the fresh run of
spec/PLAN-CODE-MODE-VS-HEART.md: instead of ARTIE against code mode, code mode using ARTIE, for services that speak
HEART (or sit behind the HEART gateway).

## The angle

Code mode stays as its makers describe it (Anthropic, "Code execution with MCP"; Cloudflare, Code Mode): a model
writes a program, and a sandbox runs it. That article already has agents keep working code as reusable skills.
ARTIE gives such a skill a better form, for a HEART service:

- **The first pass:** the model's program does the task through ARTIE, which runs in its own container beside the
  sandbox. ARTIE records the run and keeps it with `artie generate --format yaml`: a `.heart.yaml` and its pins.
- **Every later pass:** the program, or the harness without a model, calls `artie run NAME.heart.yaml`. That is no
  model and no new code, and it needs no sandbox.
- **On a change:** ARTIE's pins stop a changed form before any write, and its exit says why (`--output json`: exit
  and reason). Code mode's own loop already writes again on a nonzero exit, so the model takes that step again
  through ARTIE, and the repair is kept (spec/ideas/fewer-tokens-for-kept-scripts.md, H1).

So the skill code mode saves is data held to the server's controls, not Python that prints a 422 and exits 0 (the
saved arm's 8 silent failures).

## Where ARTIE runs

| Where | For | Against |
|---|---|---|
| **A sidecar container beside the sandbox** (recommended) | ARTIE holds the model key and the sandbox never sees it; ARTIE runs no code a model wrote, so it needs no sandbox; one ARTIE serves many programs, with its HTTP cache of forms and schemas warm | the sandbox needs a way to call it: a CLI over a socket, or heart-mcp's tools surfaced as code, as the article does with MCP servers |
| Inside the sandbox image | simplest: the program shells out to `artie` | the key would sit where model-written code runs (only with a key-holding proxy, and `ClaudeReasoner`'s endpoint is fixed to api.anthropic.com today); a cold JVM each time |

The sidecar matches spec/PLAN-HEART-MCP-ARTIE-BACKED.md: heart-mcp backed by ARTIE, with `heart_do(words)` taking
one step through ARTIE's own reasoner, and step 7 of spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, `heart_do` writing YAML.
heart-mcp already builds as an image (mcp/Dockerfile, MCP over stdio).

## What exists and what doesn't

| Piece | State |
|---|---|
| The YAML runner, `generate --format yaml`, pins, `on-change: intent`, `decide` | built |
| Exit codes that say how a run ended, and `--output json` | built |
| heart-mcp as a container image | built (MCP over stdio) |
| The code-mode sandbox and harness | built, in exhibits/mcp-over-openapi/bench.py |
| heart-mcp backed by ARTIE, with `heart_do` | planned, not built |
| `heart_do` writing a kept `.heart.yaml` | planned (step 7), not built |
| Keeping the repair after a fallback | an idea (H1) |
| An ARTIE image; a native, YAML-only ARTIE | not built; native builds without the script engine, which `.heart.yaml` doesn't need (step 6) |
| The outer model as ARTIE's reasoner (a run that pauses for the caller's choice) | not built; would avoid two models on the first pass |

## Said plainly

- On the first pass there may be two models: the code-mode model and ARTIE's reasoner. That could cost more than
  fresh code mode's first pass. The paused-run reasoner (last row above) would remove it.
- It reaches HEART services only, or others behind the HEART gateway (exhibits/own-controls).
- What code mode keeps: composing, filtering large results in code, loops over many records, joining systems.
  What ARTIE takes: the acts, done the same way every time, held to the server's controls.

## To measure, when taken up

A `code-artie` arm in the same bench and sequence (as they are, a teammate, a field renamed): right runs, tokens,
sandbox runs, and model-written code run per pass. The expectation to test: right 20 of 20; tokens about one
first pass plus one repair; no model-written code after the first pass.
