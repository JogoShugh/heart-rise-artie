# Idea: where HEART fits, fairly — live, transactional systems, beside the agents and knowledge tools people use

Status: idea, 2026-11-06. An outline of an outline: the frame to hold, then the whiteboard session it shapes
(`heart-whiteboard.md`). Nothing here attacks graph RAG, knowledge bases, or the agents people
build today. Each is good at its own job. This says which job HEART is for, and how the others sit beside it.

## The frame

- **HEART's job: live, transactional systems.** Resources whose state changes, acts that change it, and rules that
  depend on that state: an issue that can be closed only while it's open, a release that can be deployed only once its
  build passed.
- **Not HEART's job: knowledge.** Documents, wikis, knowledge bases, graph RAG answer questions about what's written.
  They're good at it, and they fit beside HEART: they can inform a decision; HEART says which acts are allowed, and
  the server enforces it.
- **What a model does for such systems, at the edges only:**
  1. **Wayfinding.** It finds the way once, from one bookmark, by the links and forms each resource offers.
  2. **Capture.** That walk is kept as a script, pinned to what each resource offered, and replayed with no model.
  3. **Just-in-time decisions.** The one judgement a step needs (is this issue a bug or a question?) is asked at that
     step, every run, and nothing else is.
  4. **Self-repair.** When a form changes, the replay stops at that step; the model may repair that one step, from its
     words and the form as offered now, and the repair can be kept (`on-change: intent`, `keep: propose | auto`).
- **With or without a person.** Each of the four can stop for review or run unattended: a walk proposes and a person
  keeps it; a chosen value is marked `review:`; a repair is proposed rather than kept. Either way the server enforces
  the same rules, and a person and a model see the same allowed acts (the HTML representation shows them as buttons).
- **What it's for:** wayfinding, evolvability, security, compliance, controls. Tokens and time are evidence, not the
  point.

## Fair comparisons: good at, the gap for live systems, how they fit

Each one in the same three parts, and where our own numbers lose, said first.

- **Tool-calling agents** (MCP servers, tools generated from OpenAPI, one HTTP tool plus what the model knows).
  - Good at: a fast start; APIs the model knows by heart. Our own trial (`docs/reports/native-vs-heart-trial`,
    GitHub triage, 29 steps): Claude with one HTTP tool did it 3 of 3 in 70 s and 13 model calls a run; through HEART
    3 of 3 in 261 s and 77.
  - Gap: what's allowed now lives in prose or memory; the rules arrive as errors after the act; the model decides
    every step of every run.
  - Fit: HEART behind MCP, the act tool's schema taken from the current state.
- **Code mode** (the model writes a script once).
  - Good at: one model call, then a fast script (22 s against HEART's 68 s in the same trial).
  - Gap: the script holds the API's addresses and rules; when the API changes, it fails, or does the wrong thing
    quietly.
  - Fit: a kept HEART script is that script, held to the forms it was written against.
- **Computer use** (screenshots and clicks).
  - Good at: systems that have only a UI.
  - Gap: pixels, read again every run.
  - Fit: a HEART resource is its own UI: `Accept: text/html` gives the same controls as buttons.
- **Graph RAG and knowledge bases.**
  - Good at: questions over documents, and sense-making across them (Microsoft Research's GraphRAG, 2024).
  - Gap, for live systems only: a snapshot that goes stale; information without controls; edges a model inferred;
    access flattened into one index.
  - Fit: in a hypermedia system the API is already a graph, stated by its owner, always current, carrying the allowed
    acts and the caller's own permissions. The knowledge graph informs; the live one decides and enforces.

## The whiteboard session it shapes

Two columns, drawn side by side: a typical agent today on the left, HEART on the right. One example throughout: a
GitHub issue through the gateway, with real JSON from the kit, trimmed.

0. Frame: the browser knows HTML, not your bank; ARTIE knows one media type, not GitHub.
1. Getting started: a catalogue of every tool, against one bookmark.
2. Sync: plain state, against state plus only the allowed forms plus an ETag. Card: a graph-RAG node beside the live
   issue.
3. Reason: the model writes the whole call, against one choice among the offered forms.
4. Act: rules found as errors, against `If-Match` and the schema checked before sending.
5. Rules that depend on state: prose, against the 1.2 build that failed and so is never offered.
6. Running it again: a model every run, or a script that holds the API, against a walk kept and replayed, stopping at a
   changed form.
7. Our own numbers, both ways.
8. Where HEART is for: unseen systems, enforcement, change, our own controls in front of systems we don't control.
9. One resource, two representations: JSON for the program, HTML for the person.
10. Question cards: "Claude already knows GitHub", "Why not MCP?", "Isn't this OpenAPI?", "What if the server
    changes?", "What does it cost?", "Isn't this graph RAG?"

## Before it is given

- The trial's numbers are from 2026-10-22; rerun them if the client or the gateway changes.
- Check the GraphRAG reference (Edge et al., "From Local to Global: A Graph RAG Approach to Query-Focused
  Summarization", Microsoft Research, 2024) before quoting it.
- Self-repair is built as the lenient guard's `on-change: intent` and `keep` (client/README.md); show it from a run,
  not from this outline.
