---
name: artie-by-hand
description: Draft, not installed. Claude plays ARTIE, the HEART client, by hand with curl and follows every rule ARTIE's own features hold it to, logging each exchange to a YAML trace so a run can be scored against real ARTIE for accuracy and cost. Use only when a run asks for "artie-by-hand" by name; for ordinary HEART work, run ARTIE itself.
---

# ARTIE by hand

You are the client. There is no ARTIE process: you send each request with `curl`,
read each answer, and decide the next step yourself. You follow the same rules ARTIE
follows. Where this file names a rule, the ARTIE feature that holds ARTIE to it is in
brackets, under `client/artie/src/test/resources/features/`.

This skill sits beside `heart-client` and replaces nothing.

## The loop: sync, reason, act

1. **Sync.** Read the resource. Keep its state, its links, its forms and its ETag.
2. **Reason.** Pick one form that the resource offers now, or one link, that moves
   the goal on. If none does, stop and say why.
3. **Act.** Fill that form and send it to the form's target.
4. **Sync again.** The answer is the next state. Go back to 2.

## Rules

### Entering and reading

- Enter only at an address the goal gives you: its bookmark or its inputs. Never
  make up an address and never change one by hand. [sync-l0]
- On every request for a resource, send these headers:
  - `Accept: application/hal+json`
  - `Accept-Profile: <https://w3id.org/heart-rise/hal-schema-forms>`
  - `Cache-Control: no-cache`
  - `If-None-Match: <etag>` once you hold an ETag for that resource.
  - `User-Agent: artie-by-hand`, so the server tells this client apart. [user-agent]
  Send no `Prefer` header: at L0 the server sends state, forms and full schemas
  in one response. [sync-l0, layer1-freshness]
- Resolve each target against the address you entered at. [sync-l0]
- A response that doesn't name the hal-schema-forms profile in a `Link: rel="profile"`
  header or its own links is data only. Read it, but act on none of it. [sync-l0]
- Take exactly `_links`, `_embedded`, `_forms` and `_templates` out of the state;
  the rest is the state. Read an embedded resource in place, with no request, and
  follow its link to act on it. [sync-l0, embedded]
- A templated link stays a template until you fill it. Don't read it as a URI
  before then. [templated-links]

### Integrity

- Fetch a shared affordance set or schema only through a link that carries its
  integrity value (`integrity` or `hr:integrity`). Refuse a link without one. [integrity-required]
- Hash the bytes you got (SHA-256, base64) and compare them with that value. If they
  differ, refuse them, don't keep them, and stop. [sync-l2, integrity-required]
- If a `Repr-Digest` header disagrees with the bytes, refuse the response. [sync-l2]
- Never read meaning out of a URL, even one that looks like a hash. [sync-l2]

### Acting

- Act only on a form the resource offers now. A form you remember from an earlier
  state, or one you expect to be there, is not on offer. [act-unknown]
- Fill the body only from the form's schema: its required fields, its types, its
  enums. Check the body against the schema before you send it. [schema-check]
- Fill a template variable from its `hr:templatePointers` JSON pointer into the
  state. With no pointer, use the state property or the form field of the same name.
  If there is none, or the pointer doesn't reach a string or a number, refuse before
  sending anything. [conditional-acts]
- Send `If-Match` with the ETag you hold, unchanged. [conditional-acts]
- Send the body as JSON, to the form's resolved target, with the form's method. [act]

### Answers

| Answer | What you do |
|---|---|
| 2xx | Read the answer. It is the next state. [act] |
| 201 with `Location` | Hold the new resource next. Read it only if the body isn't it: compare `Content-Location` with `Location`. [new-resource] |
| 304 | Your copy is current. Keep it. [sync-l2] |
| 409 | Not legal here. Sync again, report it, don't retry. [conditional-acts] |
| 412 | The resource changed. Sync again and drop the act. Don't resend it. [conditional-acts] |
| 428 | The server wants a precondition. Sync again, then send once more with it. [conditional-acts] |
| 422, body wrong | Report the server's reasons. Don't retry. [conditional-acts] |
| 422, rule-refused | The body was right, a rule of the resource said no. Report the rule's words. Don't retry. [conditional-acts] |
| A form you need is gone | Only when asked why: if the resource or its affordance set links `hr:unavailable`, fetch that list once, check its integrity and report the reason. Never act on what it says. With no such link, say the server gave no reason. [explaining-absence] |

### Waiting on another party (RISE)

- When a goal step asks to be told, the act carries a reply, kept by the step's name.
  Wait until that reply is answered, then go on holding it. Never guess an outcome.
  [goal-rise] (Rough: how a reply is read by hand isn't written out yet.)

### Never

- Never send an act the resource doesn't offer.
- Never retry a 409 or a 412 by hand.
- Never fix a refused integrity check, or skip one.
- Never invent a value the goal, the state or the form doesn't give you. Ask the user.

## The trace

Write every exchange to `runs/<goal>-<UTC time>.trace.yaml` as it happens, in the
shape ARTIE's own traces use. Write bodies as YAML, never as escaped JSON.

```yaml
goal: merge-watch-release
client: artie-by-hand
steps:
  - n: 1
    request:
      method: GET
      url: http://localhost:8080/repos/acme/web/pulls/501
      headers: {Accept: application/hal+json, Accept-Profile: "<https://w3id.org/heart-rise/hal-schema-forms>", Cache-Control: no-cache}
    response:
      status: 200
      etag: '"…"'
      body: |
        number: 501
        state: open
    picked: scm:merge
    why: the goal says merge it by squashing
    refused: null
outcome: met            # met | not-met | refused | stopped
```

At the end, report the outcome, the requests sent, and every refusal and its rule.

## How a run would be measured

These notes are for whoever runs the harness, not for the model running the skill.

- **Arms.** Run each goal three ways: ARTIE with `--reasoner claude` (walked), ARTIE
  replaying its kept script (no model), and this skill under `claude -p`.
- **Accuracy**, scored by a checker against the server's own log and the ARTIE
  trace, never taken from the skill's self-report:
  - outcome right (met, refused or stopped, as ARTIE's was);
  - acts right: the same relations, in order, with the same bodies;
  - rule breaks counted: an act not on offer, a missing `If-Match` or
    `Accept-Profile`, an unchecked integrity value, a retried 409 or 412, an
    invented value.
- **Cost:** input, output and cache tokens and dollars from `claude -p
  --output-format json`, model calls, HTTP requests, and wall time. Use the same goals,
  the same server states and the same model in every arm, with at least 5 repeats
  each, because a model arm varies from run to run.
