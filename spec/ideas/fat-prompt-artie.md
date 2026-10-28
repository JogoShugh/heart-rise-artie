# Idea: a "fat prompt" ARTIE, inference only, beside the real one

**Status:** idea only. Raised by the author on 2026-10-28. Nothing is built.

## The question

How far does inference alone get? Write ARTIE's behaviour down as a skill, a long prompt with a few reference files,
and let Claude carry out the merge watch with nothing but `curl`, reasoning about every step on every run. Then put
it beside ARTIE's own two ways: wayfinding (the goal run, the model choosing each step from what is offered) and the
kept script (deterministic, the model only writing the note to an author). Same goal, same gateway, same repository,
same awkward pull request.

It is the inference-only approach to determinism, against determinism with as little reasoning as possible beyond
wayfinding.

## The skill

`SKILL.md` and its references tell Claude to act as ARTIE does:

- **Enter at the goal's `start`, and go only where the answers lead.** Follow links and fill forms the server hands
  over; never build a URL from knowledge of the API.
- **Act only on what is offered.** Before an act, check that its form is in the representation; when it isn't, read
  `hr:unavailable` and use its reason.
- **Fill a form from its schema.** Send only the fields it names, of the types it says.
- **Carry the validator.** Send `If-Match` with the ETag last read; a 412 means read again and decide again.
- **Keep the goal's words.** Whenever, If … leave it and say so once, Once … give up after, Otherwise, Until, each
  as ARTIE reads them (spec/PLAN-GOAL-MARKDOWN.md).
- **Remember what it said.** A small file for "say so once" and "said already", read at each pass.
- **Write a trace in ARTIE's format** (YAML, one document a step), so the run report and the comparison read it as
  they read ARTIE's.

Its only tool is the shell: `curl` against the gateway, with the token from the credentials.

## Fairness

- **The model drives every pass.** A watch the model turns into a bash loop has become a script, and measures
  something else. The skill says so, and the trace shows a model turn per pass.
- **A cap.** A watch reasoned on every pass is expensive: about five minutes, a handful of passes.
- **The same scene for each arm,** made the same way: a fresh bump that can merge, one behind its base, the held
  ones, a draft, a conflict.

## What to measure

| | Fat-prompt skill | ARTIE, wayfinding | ARTIE, kept script |
|---|---|---|---|
| The bump behind its base held, with the gateway's reason | ? | yes (live-3) | yes (lenient-1) |
| The held ones left, said once | ? | yes | yes |
| Only offered acts tried; no invented URLs | ? | yes, by construction | yes, by construction |
| Model calls and tokens per pass | ? | every step | the note to an author only |
| Seconds per pass | ? | | |
| The same acts on three runs | ? | | yes |

## Open

- Run it over the gateway only, or also against GitHub directly, with no hypermedia: a second arm showing what the
  prompt has to make up when the answers carry no controls.
- Where the skill lives: in this repository, beside the exhibits, or in `~/.claude/skills`.
- Whether the trace the skill writes can be generated into a script by `artie generate`, closing the loop.
