# Idea: Rally (Broadcom's Agile Central) served as HEART

**Status:** researched, not started, 2026-10-15. A side question from the
author while the OpenAPI gateway's connectors were being built
(spec/PLAN-OPENAPI-GATEWAY.md, Phase 3). No code yet.

## The question

Could the gateway present Rally's Web Services API (WSAPI) to HEART clients,
so that a person or an agent sees "accept this story" as a form the story
offers now, rather than a field to write?

## Short answer

Yes, but not through the OpenAPI path. Rally publishes no OpenAPI
description, and its writes are field updates, not acts. It does publish
machine-readable type metadata, which is a richer source of forms than most
OpenAPI specs. A Rally gateway needs two things the OpenAPI gateway doesn't
have: types read from Rally's metadata, and a catalog of acts written in the
connector.

## What was verified (15 October 2026)

- The supported Rally APIs are WSAPI and the Lookback API (LBAPI); Broadcom
  does not guarantee other endpoints stay unchanged. No OpenAPI or Swagger
  description of either was found.
- WSAPI exposes `typedefinition` objects; a type's `Attributes` collection
  links to each attribute's `AllowedValues`, such as a dropdown's choices.
  AllowedValues are scoped per workspace.
- Writes in v2.0 need an API key (the `ZSESSIONID` header) or a security
  token from `/security/authorize`.
- Flow State maps Schedule States to a team's board columns; Broadcom advises
  changing Schedule State, not Flow State, so the two don't drift apart.

Sources:
- https://knowledge.broadcom.com/external/article/272971/which-rally-api-services-are-supported.html
- https://knowledge.broadcom.com/external/article/57584/wsapi-api-get-allowedvalues-for-rally.html
- https://knowledge.broadcom.com/external/article/57528/use-api-key-with-curl.html
- https://knowledge.broadcom.com/external/article/57510/rally-how-to-create-and-update-an-artif.html
- https://knowledge.broadcom.com/external/article/199527/rally-is-flow-state-available-for-notif.html
- https://knowledge.broadcom.com/external/article/145540

## What is from memory, still to verify against a real workspace

- Each answer carries `_ref`, `_type`, `_refObjectName` and `ObjectID`, close
  to HAL's self link and title.
- A change is a POST of changed fields to the object: "start" is
  `ScheduleState` = `In-Progress`, "block" is `Blocked`, "plan" is `Iteration`.
- Nothing per object says what is allowed now.
- Permissions are per user per project (viewer, editor).
- Searches use WSAPI's query language, such as `(ScheduleState = "In-Progress")`.
- Whether Rally offers ETags or If-Match on writes is unknown.

## What a Rally gateway would need

1. **Types from Rally's metadata, not OpenAPI.** A second source beside
   `GatewaySpec`, reading `typedefinition` and its `Attributes` per
   workspace: required and read-only flags, and AllowedValues as closed
   choices in the forms.
2. **A catalog of acts in the connector.** Each act a name, a small input
   schema, and the field update it performs: `start`, `complete`, `accept`,
   `block` (with a reason), `unblock`, `plan` (an Iteration), `estimate`,
   `assign` (an Owner). This turns field edits into domain acts, the shape
   HEART wants, and keeps to "new resources come from acts, not create".
3. **What is allowed now: a state table, plus the caller's permission.**
   Defined allows start; In-Progress allows complete or block; Completed
   allows accept; and so on. Whether a caller may accept depends on who they
   are, so the gateway must call Rally as each caller: the per-caller
   credential forwarding deferred on 15 October 2026 becomes required here.
4. **Choices from state (HAL-SCHEMA-FORMS-HR Rule 8).** `plan` takes its
   choices from the project's current iterations, and `assign` from the
   project's members, each a list the gateway serves.
5. **Concurrency.** Without If-Match from Rally, the gateway's check-then-act
   applies, with the weaker guarantee the plan names.
6. **Bookmark, search and past.** Workspaces and projects as the bookmark;
   searches by WSAPI queries; and the Lookback API's snapshots as HEART's
   `?asOf=` and `/history`.

## Why it is worth it

- An agent working Rally through HEART sees only the acts a story allows now,
  with closed sets for states, iterations and owners: it can't invent any.
- Clients follow forms, so a workspace's custom fields and states don't break
  them.
- It tests HEART on a large real system nobody here controls, which is
  stronger evidence than the garden.

## Risks

- Types differ per workspace, so affordance sets are per workspace: fine,
  but more of them.
- Process rules or custom workflows may not show in the metadata; a table in
  the gateway can drift from them, the known weakness of early binding.
- Rate limits, and Broadcom's caveat on endpoints.
- It needs a Rally sandbox or trial workspace, which the author would arrange.

## First steps, when picked up

1. Pull `typedefinition` and `Attributes` for HierarchicalRequirement and
   Defect from a workspace the author can reach, and settle the points from
   memory above.
2. Write the act catalog for stories (about eight acts) as a design note.
3. Spike a Rally type source and the act catalog in the gateway, behind the
   conformance kit (heart-tck).
