# Plan: heart-mcp backed by ARTIE itself

Status: planned, 2026-11-02. Nothing built. From exhibits/mcp-over-openapi (report: "Static Tools, Moving Service").

## Why

heart-mcp borrows only ARTIE's protocol code, `ArtieClient`: enter, act, follow, describe, explain. Everything
ARTIE does above that is left to the outer model:
- choosing an act from the forms offered;
- deciding again after a 412;
- taking a step toward an act not offered yet;
- not writing over a change it was never shown.

The model does these well (36 of 36 runs through every change, 10 of 10 kept both edits). But it pays for them:
- every step is a model call;
- every result is a full view (state, forms, links);
- the views pile up in the conversation, so the cost grows with each step.

The measurements, input tokens per run:

| Arm | Hire, promote, dismiss | Someone else changes him | Words that set a value |
|---|---|---|---|
| FastMCP (static, bare calls) | 4.4k | 5.2k, lost the edit | 5.6k, wrote over it silently |
| heart-mcp, lean | 15.5k | 11.5k | 11.5k |
| ARTIE intent run | 7.3k | 5.6k | 5.6k, asked first |

The intent run is ARTIE's own `act(intent = …)`. Each step is one small reasoner call, made on the current resource
only, with no history. The same steps behind heart-mcp should cost what the intent run costs, plus a little for
the outer model.

## What changes

### 1. A tool that hands a step to ARTIE: `heart_do`

`heart_do(words)` takes one step in a person's words, for example "hire Samwise Gamgee as a gardener". heart-mcp
runs it as ARTIE's intent step, through heart-script's navigator (ArtieNavigator and Reason.kt). So it gets
everything ARTIE's runs get:
- choosing from what is offered, with ARTIE's checks against schema and choices;
- deciding again after a 412, told what changed;
- a form out of date treated as stale;
- acts toward the words when what they ask isn't offered yet;
- the step's own read guarding its act;
- the trace.

Its result is short:

| Outcome | The tool returns |
|---|---|
| done | one line (what was done, at which resource), the state members that changed, the names of the acts offered next |
| asked | ARTIE's question, for example "Frodo's role was changed … overwrite it anyway?", for the outer model to put to the person |
| refused | the kind and the server's reason, the names of the acts offered |

No schemas and no full views. The outer model still can `heart_look` when it wants the whole resource.

### 2. The inner reasoner, with two backends

- **MCP sampling.** heart-mcp asks the host's own model, through `sampling/createMessage`, with ARTIE's reasoner
  prompt and only the current resource. No key on the server. First check whether Claude Code and the Kotlin MCP
  SDK in use support it.
- **A model key on the server.** ARTIE's `ClaudeReasoner`, as `artie run --reasoner claude` uses it, with the
  key from ARTIE's credentials. A smaller model can do it: the reasoner prompt is small and the choice closed.

### 3. The six verbs stay

`heart_look`, `heart_act`, `heart_follow`, `heart_describe`, `heart_explain` and `heart_back` stay, for fine
control. `heart_do` becomes the one the server's instructions recommend first.

### 4. Later: `heart_run(script)`

The outer model writes a short heart-script: relations and values, never addresses. ARTIE runs it in one call,
with every guard, and stops at a refusal with what's offered there. It's Code Mode, but over HEART, and costs one
model call for a whole path.

### 5. Free from ARTIE: a session becomes a kept script

Every `heart_do` lands in an ARTIE trace. `artie generate` turns a session into a kept script and lock file that
run again with no model.

## How it's built (gated, feature first)

1. A `heart-mcp` feature, `mcp-do.feature`, with a scripted reasoner (as heart-script's `ScriptedService` does):

   | Case | Expected result |
   |---|---|
   | words matched to an offered act | done, changed members, offered names |
   | a 412 while acting | decided again, done, the change named |
   | words that would write over a change | asked, nothing sent |
   | an act not offered yet (notice) | the act toward it, then the act, done |
   | no reasoner configured | refused, saying where a key goes or that sampling is off |

2. Red runs; then `Tools.kt` (the new tool), `HeartMcp.kt` (a heart-script `Run` around the existing
   `ArtieClient`, so the six verbs and `heart_do` share one held resource), and a small `Views` addition for the
   short result.
3. The reasoner backends: sampling behind a check of the host's capabilities, and the key as a fallback.
4. Green: mcp suite, client suite.

## How it's measured

A new bench arm in exhibits/mcp-over-openapi, `heart-mcp-artie`:
- the same tasks T1–T4, and every change at start and midway;
- **tokens counted from both models**, the outer model and the inner reasoner, so the comparison stays fair.

Expected, to be confirmed or refuted:
- tokens near the intent run's, below heart-mcp lean's;
- every change done but the new required field, where it asks;
- both edits kept;
- no write over a change unseen.

The report gets the arm, and says plainly where FastMCP is still cheaper.

## Out of scope

- Changing what the six verbs return beyond what `heart_do` needs.
- A reasoner in heart-ts (see spec/ideas/heart-ts-forms-out-of-date.md for the part heart-ts can take).
