# Idea: heart-ts treats a form out of date as stale, as ARTIE does

Status: planned, 2026-11-02. Nothing built. From exhibits/mcp-over-openapi, the changes made midway.

## Why

ARTIE now treats a form that has gone out of date as stale (commit 07152e3,
client/artie/src/test/resources/features/script-steadiness.feature). The service moved the act, or changed its
method, after the client read the form. The old target then answers 404 or 405, and the server can't tell that
from any other 404 or 405. ARTIE reads the resource again. When its ETag has changed since the form was read, the
act is refused as stale, just as a 412 is, and the client holds the fresh resource. When the ETag is the same, the
404 or 405 stands.

heart-ts (implementations/heart-ts/src/client.ts, `refusal()`) reads the resource again only after the statuses
`needsRereading` names. A 404 or 405 is reported as it came, and the client keeps the stale form.

ARTIE's other fixes from the same night have no place in heart-ts, because heart-ts has no reasoner and no
script layer. They are: deciding an intent again after a 412, taking acts toward the words, and refusing an act on
a resource changed since its step read it.

## Plan

1. **A shared scenario first**, in `implementations/conformance/features/refusals.feature`, so both clients
   meet it. heart-sims moves an act after the client has read its form (`Change.MOVED` already does this for the
   employees service). It's one Scenario Outline:

   | Case | Act answered | Resource read again | The client reports |
   |---|---|---|---|
   | moved | 404 | new ETag | stale; holds the fresh resource, whose form has the new target |
   | new method | 405 | new ETag | stale; holds the fresh resource |
   | gone for real | 404 | same ETag | not found, as today |

2. **Red runs**, through `.claude/bdd-gates/bdd-run.sh` for heart-ts, and through ARTIE's conformance adapter.
   ARTIE should pass the first two rows already. If its adapter needs a step, that's test code only.
3. **heart-ts**, `client.ts`: in `refusal()`, on 404, 405 or 410 from an act, read the held resource again. If
   the ETag changed, return the stale refusal (the same kind as a 412), holding the fresh representation;
   otherwise return the refusal as it came. `refusal.ts` keeps its words: "the resource changed since it was
   read".
4. **Green** for heart-ts (lint, tsc, mutation as the gate asks) and for ARTIE's conformance suite.

## Out of scope

- A reasoner or script layer for heart-ts.
- The 404 with an unchanged ETag in ARTIE, which still crashes a script run (`UnexpectedResponse`). That's a
  separate small fix: report it as not found at that step.
