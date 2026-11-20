# Idea: explore once with a model, replay forever without one

**Status:** idea, 2026-10-16. From the author, while planning Part 2 of "Take
HEART" (GitHub pull requests through the OpenAPI gateway). No code yet.

## The idea

A person says or types a multi-step task in plain language:

> List the pull requests. For each one, get its base branch (the branch it
> merges into), its head branch (the branch it merges from), and the latest
> commit on the head branch.

The first time, a model works it out against a HEART server, choosing forms
and following links over several requests. That run is **captured as a
script**: not the model's reasoning, but the moves it made. Every later run is
a plain, deterministic replay of the script, with **no inference and no
tokens**, for as long as the server still offers what the script relies on.
When it doesn't, the replay stops before acting, and a new exploratory pass
from the original words produces a new version.

## What a captured script holds

- **The originator:** the original spoken or typed request, kept verbatim, so
  the script can be regenerated from it.
- **Steps, by meaning, never by URL:** which relation each step followed or
  which form it used (`github:list-pulls`), so a moved endpoint doesn't break it.
- **Bindings:** which values each step took from earlier answers, by JSON
  Pointer (the base name at `/base/ref`, the head commit at `/head/sha`).
- **Iteration:** where it loops ("for each item listed").
- **Guards:** for each step, the content address of the affordance set it was
  offered, and the schema addresses of the forms it used.
- **Outputs:** what the run reports or returns.

## Why HEART makes replay safe and cheap

- **Drift is detected exactly, before acting.** Affordance sets and schemas are
  immutable and named by their content (SPEC §6.5). If the server changes a
  form the script uses (a new required field, a different target), its
  address changes, and the runner sees it before sending anything. Browser
  record-and-replay tools infer drift from page structure; here the server
  states it.
- **No URLs to rot.** The script follows relations and links, so moved paths
  are absorbed, as ARTIE absorbed move-url in the exhibit.
- **The server still guards.** If-Match, 409, 412, 422 and 428 apply on replay
  too. The address check is about knowing in advance, not the only safety.

## Design points

1. **Guard what is used, not everything seen.** An open pull request, a draft
   and a merged one are different states with different affordance sets; that
   is not drift. The default guard: the form the step needs is offered, and
   its schema address matches the recording. A strict mode can require the
   whole set's address to match.
2. **Judgement calls must become explicit.** If the model decided "merge the
   one that's ready", the capture must turn that into a deterministic
   condition (mergeable state is clean), or keep that one step as an
   inference step and pay only there. Never bake in a guess silently.
3. **Reads and writes differ.** A read-only script (list, report) can replay
   forever. A script that acts (merge) needs a confirmation policy and must be
   safe to run twice; If-Match makes a second act on a stale view a 412.
4. **Integrity is optional.** A matching address means the same set; checking
   the bytes against the link's integrity also guards against a tampered
   cache, for the cost of one hash.
5. **Drift is a report, not a crash.** The runner says which step, which form,
   and what changed (old and new addresses, and the schema difference), then
   offers to re-explore from the originator.
6. **Versions and provenance.** Each script records when and against which
   server it was captured, and each re-exploration makes a new version.

## Prior art, to check before citing

From knowledge, not verified on 2026-10-16:

- **Record and replay without a model:** Selenium IDE, Playwright's code
  generator, RPA recorders (UiPath). Bound to page structure; drift shows up
  as breakage.
- **Persisted skills:** Voyager (2023) kept the code its Minecraft agent wrote
  as a reusable skill library. Anthropic's recent direction, agents writing
  code against tools (code execution with MCP) and Agent Skills, also saves
  code for reuse, partly to cut tokens.
- **Plan caching:** 2025 research on caching an agent's plan and reusing it
  for similar tasks ("agentic plan caching" is the term recalled).
- **Browser agents with action caching** that replay without a model and call
  it again on failure; Stagehand is the one recalled.

Most on-the-fly script generation regenerates every run, and the systems that
persist scripts detect staleness by failure. Guarding replay with the server's
own content hashes of what is allowed, so staleness is known exactly and
before acting, appears to be new, and falls out of HEART's design.

## Connection to what exists

- The exhibit's scripted ARTIE client is a crude captured script: hard-coded
  relation and field, no guards. It broke on rename-act and
  new-required-input. With recorded addresses as guards and an originator to
  regenerate from, it becomes this.
- Part 2 of "Take HEART" (GitHub pull requests through the gateway) is the
  natural first example: "what's the status of my PRs?" explored once, then
  replayed every morning for free.
