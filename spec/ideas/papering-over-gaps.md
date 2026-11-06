# Idea: a model papers over gaps; use it as a probe, not as plaster

**Status:** idea only. Raised by the author on 2026-10-26, after asking what plain Claude would do with
`exhibits/merge-watch/merge.goal.md`. A possible section for "The Other R's", or an explainer of its own.

## The thought

Left to itself, a model works with whatever it finds and gets something done: messy APIs, missing documentation,
fields that are half right. That is a real strength, and nothing else bridges bad systems as cheaply. It is also a
serious danger to repeatability and to the other R's (spec/ideas/the-other-rs.md), because papering over a gap
does not close it. It moves the gap somewhere no one can see.

## Why it is a concern

- **The gap is paid for on every run.** The model has to rediscover each time, for example, that GitHub calls a
  draft `mergeable: true` and `"clean"`. Each time costs calls, and each time it can get it wrong.
- **The fix lives nowhere.** It is not in code anyone can review or pin. It is in the model's behavior that day.
- **It fails quietly.** A bad workaround still gives plausible output. A broken script crashes; a papered-over gap
  drifts.
- **It takes away the pressure to fix the system.** If the AI copes, no one repairs the API, and the bad structure
  stays, with the model as a permanent tax on it.
- **It gives the illusion of repeatability, utility and speed.** The demo works because the common path works. The
  variation hides in the tail, and business systems live in the tail.

Screen scraping, RPA and glue scripts papered over gaps too. A model is much better paper, which is exactly why the
gaps become harder to see.

## The constructive turn

Use the model as a probe: let it find the gap, then close the gap in the system, where it stays closed. The merge
watch did this twice. The embedded repository that made every pull request look changed became a gateway fix
(`LinkRules.summaries`). The draft that says it is mergeable became a server refusal with a reason. Each fix took
reasoning out of the loop for good.

AI that papers over gaps gives speed today and debt forever. AI that exposes gaps gives a better system.

## Related

- spec/ideas/reasoning-that-earns-its-keep.md
- spec/ideas/five-ways-to-run-one-goal.md, which would measure it.
