# Idea: model calls that cost less, by being small, cached and narrow

**Status:** future idea only, 2026-11-03. No plan, no code. From the author, after the code mode runs on the HEART
side (docs/reports/wayfinding-through-change/), where a 17 KB format document was read in every run.

## What a call costs

- **Tokens sent, not the window.** A model is paid by the tokens it takes in and gives out. A model with a small
  context window is not cheaper by itself; fewer tokens, and a cheaper model, are.
- **Prompt caching.** The Messages API can keep a prompt's prefix (system prompt, tool definitions, earlier turns) for
  about 5 minutes, or an hour on request. A cache read costs about a tenth of the input price; a cache write a little
  more than it, once. Only an identical prefix hits.
- **What the reports count.** "Input tokens, cached or not": what the model took in. The bill is lower than the count
  where the prefix was cached.

## Where the bench spent them

| Client | What each call carries |
|---|---|
| Code mode, on endpoints | the system prompt and the whole API library's documentation, then the conversation so far |
| Code mode on HEART, with the format's document | the document read by the program, a different output each run, so cached only within the run; 8 turns used up reading it |
| ARTIE, a fallback | one step: the state, the forms offered now, the step's words; about 1.3 to 1.5k tokens, no history, no document |

ARTIE knows the media type in code: forms, schemas, `If-Match`, reading again, refusals. No model is taught those
rules, so no call carries them.

## The idea

1. **Keep ARTIE's calls small and the same at the front.** Its reasoner already marks the tool and system prompt for
   caching (SPEC §6.8's prompt-cache rule, ClaudeReasoner.kt). Measure the cache hits across a run and across runs,
   and keep everything that changes (the state, the offers, the words) after the cached prefix.
2. **A cheaper model for narrow calls.** A fallback picks one form among those offered and fills its values from the
   words and the state. Try Haiku, and a local model (`--model-url`, spec/PLAN-LOCAL-MODEL.md), on the same sequence:
   right runs, and cost per right run.
3. **A model per kind of call.** The goal run that finds the way may need a stronger model; a repair after a release
   may not. Name the model per kind in the config.
4. **For a model that must know the format** (code mode, a skill): put the format's document in the prompt, the same
   bytes every time, so it is cached across runs, not read again by a program each run. Cheaper, but still in every
   call's context; ARTIE needs none of it.

## To measure, when taken up

- Price per right run, not tokens alone: cache reads, cache writes and uncached input priced apart, from the API's
  usage, for each arm already in the bench.
- ARTIE's fallback on Sonnet, Haiku and one local model, through the release sequence.
- Code mode on HEART with the format's document in a cached system prompt, against the same document read in the
  sandbox.

Related: spec/ideas/fewer-tokens-for-kept-scripts.md, spec/ideas/reasoning-that-earns-its-keep.md,
spec/ideas/trimming-representations.md.
