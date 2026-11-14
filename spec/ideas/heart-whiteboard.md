# Idea: HEART at the whiteboard — sync, reason, act, beside a typical agent

Status: idea, 2026-11-06. A whiteboard session for explaining HEART to another developer, about twenty minutes. Its
frame is `where-heart-fits.md`: live, transactional systems; fair to every other approach. Nothing to build.

The board has two columns, drawn step by step: **a typical agent today** on the left, **HEART** on the right. One
example throughout: a GitHub issue, reached through the kit's gateway. The right column's JSON is real, from the kit's
run on `JogoShugh/heart-kit-test` on 2026-11-06, trimmed (`…`). The left column's JSON is illustrative: the usual shape,
not any one product's.

## 0. The frame

- **Draw:** a browser beside a bank's website. Then ARTIE beside GitHub.
- **Say:** the browser knows HTML and nothing about your bank; the bank's site tells it, in every answer, what can be
  done next. ARTIE knows one media type and nothing about GitHub; each resource tells it what can be done next.
- **Left:** a model in the middle, a catalogue of tools around it (an MCP server, tools generated from OpenAPI, or one
  HTTP tool and what the model knows), and a loop through the model on every step. Beside it, code mode (the model
  writes a script once) and computer use (screenshots and clicks).
- **Right:** a loop of three, Sync, Reason, Act. Sync and Act are plain code. Reason is the only place a model may sit,
  and it may be code instead.

## 1. Getting started

- **Left:** every tool's definition, loaded up front, each allowing every field that could ever apply:
  ```json
  {"name": "update_issue",
   "inputSchema": {"properties": {"owner": {}, "repo": {}, "issue_number": {},
     "state": {"enum": ["open", "closed"]}, "state_reason": {}, "duplicate_of": {}, "labels": {}, …}}}
  ```
- **Right:** one bookmark. Its answer offers the ways in, as forms:
  ```json
  GET http://localhost:8392/
  {"title": "GitHub v3 REST API",
   "_forms": {"github:repos-get": {…}, "github:issues-get": {
       "_links": {"target": {"href": "/repos/{owner}/{repo}/issues/{issue_number}", "templated": true}},
       "method": "GET", "title": "Get an issue"}, "github:issues-list-for-repo": {…}, …}}
  ```
- **Say:** a catalogue says what can ever be done. A bookmark says where to start.

## 2. Sync

- **Left:** `get_issue` answers plain state. What may be done to it now lives in tool descriptions, docs, or the
  model's memory.
- **Right:** the state, only the forms allowed now, and an ETag. Draw issue 71 twice, open and then closed:
  ```json
  GET /repos/JogoShugh/heart-kit-test/issues/71          ETag: "seq-0.hsf.L0.016c82fe2986603c"
  {"number": 71, "state": "open", …,
   "_forms": {"github:close": {…}, "github:close-not-planned": {…}, "github:close-duplicate": {…},
              "github:comment": {…}, "github:label": {…}, …}}

  … after it is closed:                                  ETag: "seq-0.hsf.L0.21647591466fbd4a"
  {"number": 71, "state": "closed", "state_reason": "completed", …,
   "_forms": {"github:reopen": {…}, "github:comment": {…}, "github:label": {…}, …}}
  ```
- **Say:** close isn't offered on a closed issue, so no client can try it. The absence is the enforcement.
- **Card, graph RAG:** a node a model extracted from documents last week, beside the live issue:
  ```json
  {"entity": "Issue #71", "relation": "DUPLICATE_OF", "target": "Issue #1", "extracted": "2026-10-30"}
  ```
  Graph RAG answers questions over documents, and does it well. For a live system, the API is already a graph: its
  owner states the links, they're current, and they carry the acts allowed now and the caller's own permissions.

## 3. Reason

- **Left:** the model writes the whole call: the tool, and every argument, the IDs among them:
  ```json
  {"name": "update_issue", "arguments": {"owner": "JogoShugh", "repo": "heart-kit-test", "issue_number": 71,
    "state": "closed", "state_reason": "completed"}}
  ```
- **Right:** the model sees the forms offered, with their titles, descriptions and schemas, and makes one choice. Its
  whole answer, from the run:
  ```json
  {"action": "act", "affordance": "github:close", "fields": {}}
  ```
  The one judgement triage asks of it, at its reasoned step, on every run:
  ```json
  {"action": "act", "affordance": "github:label", "fields": {"labels": ["question"]}}
  ```
- **Say:** it can't write a URL, a method or a header. It picks among what the resource offered.

## 4. Act

- **Left:** the call goes out; the rules come back as errors, after the act; the model reads them and tries again.
- **Right:** plain code takes the chosen form, checks the body against its schema, and sends it on the state it saw:
  ```json
  PATCH /repos/JogoShugh/heart-kit-test/issues/71/actions/close
  If-Match: "seq-0.hsf.L0.016c82fe2986603c"
  Content-Type: application/merge-patch+json
  {}
  → 200, the issue's new state and its new forms: the loop starts again
  ```
- **Say:** a stale state is refused (`412`); a missing validator is refused (`428`); an act not offered now is refused
  (`409`); a body that doesn't fit is refused (`422`). After any refusal the client syncs again; it never guesses.

## 5. Rules that depend on state

- **Left:** "never deploy a build that failed" lives in prose. The model has to remember it.
- **Right:** release 1.2's build failed, so the deploy service never offers 1.2 as a choice. Asked anyway, the client
  refuses before it sends a byte:
  ```
  stopped at step 4, "cd:deploy": not a choice, refused by the client: "release: 1.2 is not one of the choices"
  ```
  And an act that isn't offered says why, in the server's words, when someone asks:
  ```
  deploy, while one runs: "A deployment is in progress."
  ```
- **Say:** a person sees the same thing: the HTML representation shows Deploy greyed out, with that reason.

## 6. Running it again

- **Left:** a model on every step of every run: tokens each time, and the path may differ. Or code mode: a script
  written once, holding GitHub's addresses and rules; when the API changes, it fails, or does the wrong thing quietly.
- **Right:** walk once, keep the walk as a script, replay it with no model. A step of the kept release script:
  ```yaml
  - act: cd:deploy
    fields:
      environment: staging
      region: ${app.regions.staging.0.name}   # review: read from the app's state
      release: ${version}
    then:
      - check: [{field: status, is: deploying}, {field: release, is: ${version}}, …]
  ```
  and its pin, the form it was written against:
  ```yaml
  cd:deploy:
    integrity: sha256-Iqmo/lPMp+NgAa7cI/cFympBDgIDxwlfMAUEPmANerY=
    method: POST
  ```
- **Say:** if the deploy service changes that form, the replay stops at that step and sends nothing. A model may then
  repair that one step, and a person may review the repair before it's kept.

## 7. Our own numbers, both ways

- `docs/reports/native-vs-heart-trial`: GitHub triage, 29 steps.
  - With a model on every move: Claude and one HTTP tool, 3 of 3, 70 s, 13 model calls a run. Through HEART, 3 of 3,
    261 s, 77.
  - As scripts, no model: the agent's own script 22 s, HEART's 68 s.
- **Say it plainly:** on an API the model knows by heart, the typical agent is faster and cheaper.

## 8. What HEART is for

- Systems the model has never seen: the kit's release crossed a build service and a deploy service it had no docs
  for, by following links, in 52 seconds; its replay took 6, with no model.
- Rules the server enforces, not ones the model is trusted with.
- Change: the server changes a form, and every client reads the new one; a kept script stops at it.
- Our own controls, in front of systems we don't control: the gateway in front of GitHub.
- A model at the edges only: finding the way, the one judgement a step needs, repairing a step. With or without a
  person reviewing.

## 9. One resource, two representations

- **Draw:** the same issue address, two arrows: `Accept: application/hal+json` to the program, `Accept: text/html` to
  a person.
- **Show:** the kit's screenshots: the deploy service with Deploy live, then greyed with its reason; the issue with
  Close, then Reopen.
- **Say:** one source of truth for a person and a program; nobody keeps the rules twice.

## 10. Question cards

- "Claude already knows GitHub; why bother?" Step 7, then step 8.
- "Why not MCP?" They fit: HEART behind MCP, the act tool's schema taken from the current state.
- "Isn't this OpenAPI?" OpenAPI says what can ever be done; HEART says what may be done to this resource now.
- "What if the server changes?" The replay stops at the changed step and sends nothing; a repair is proposed.
- "What does it cost?" The walk, once; the replays, nothing but HTTP.
- "Isn't this graph RAG?" Knowledge beside a live system: the card at step 2.

## Before it is given

- Rerun the kit and take the fragments and screenshots again if the gateway or the client changes.
- The numbers at step 7 are from 2026-10-22, the release's at step 8 from the kit trial; rerun them if either
  changes.
