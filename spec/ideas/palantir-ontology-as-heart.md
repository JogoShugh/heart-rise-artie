# Idea: a Palantir Foundry ontology served as HEART

**Status:** researched, not started, 2026-10-15. A question from the author
while the OpenAPI gateway's connectors were being built
(spec/PLAN-OPENAPI-GATEWAY.md, Phase 3). No code yet. Compare
spec/ideas/rally-as-heart.md. Its purpose is an exit: see "Why", below.

## The question

Can the metadata of a Foundry ontology (its object types, link types,
interfaces and action types) be served as standard HEART, so that any client
outside Foundry gets what Workshop gets inside it, rather than an
early-bound, generated OSDK?

## Why: a strangler fig, to leave Foundry

The author's purpose is not to extend Foundry but to make leaving it
possible. This is the strangler fig pattern:

1. Put the HEART gateway in front of the ontology. Clients (ARTIE, agents,
   apps) move from OSDK and Workshop to HEART, and learn only HEART's media
   types, profiles and forms. Nothing they hold names Foundry.
2. Replace the ontology behind the gateway one object type at a time, with
   services of the organization's own (heart-spring or heart-http4k
   declarations, or any OpenAPI service through the gateway). Because HEART
   clients bind late, to relations and forms rather than to endpoints or
   generated code, each type moves without a client release.
3. When the last type has moved, the gateway no longer calls Foundry, and
   Foundry can be switched off.

OSDK is the lock-in: generated code that names the vendor's types, compiled
into every app. HEART is the way out: clients that depend only on open
standards (HAL, JSON Schema, ALPS, RFC 9457, HTTP validators), so whatever
answers behind the gateway can change. The rest of this note is how step 1
works; steps 2 and 3 are what the existing HEART libraries and gateway
already do.

## Short answer

Yes, and Foundry is the best candidate so far, because it is already
hypermedia-shaped inside. Workshop is the late-bound client: it generates
the form for an action from the action's definition at run time. OSDK is the
early-bound one: code generated from a chosen subset of the ontology, the
same role as the bespoke OpenAPI dashboard in the exhibit. The strategy is to
serve what Workshop reads, as HEART.

## What was verified (15 October 2026)

| Foundry | HEART |
|---|---|
| Object type: properties, `titleProperty`, `primaryKey` | Resource type; the object is the state |
| Link types (`linkTypes`) | HAL links between resources |
| Interfaces (`implementsInterfaces`), shared property types | Profiles shared across types |
| Action type: `apiName`, `displayName`, `description`, `parameters`, `toolDescription`, `status` | A form, its relation in an ALPS profile, its schema; `toolDescription` is written for AI agents |
| Submission criteria, a failure message per root condition | Whether an act is offered now, and why not |
| Validation: `submissionCriteria[].configuredFailureMessage`; per parameter `result`, `required`, `evaluatedConstraints` (`range`, `oneOf`, `arraySize`, `objectPropertyValue`, `objectQueryResult`) | Absence reasons; 422 problems at a field; choices from state (HAL-SCHEMA-FORMS-HR Rule 8) |
| Workshop generates the form from the action definition | ARTIE renders forms from HEART |
| OSDK generates TypeScript, Python, Java, or an OpenAPI spec, for a chosen subset | The early-bound client the exhibit beats |

- Endpoints: `GET /api/v2/ontologies/{ontology}/objectTypes/{objectType}/fullMetadata`
  (preview), `GET /api/v2/ontologies/{ontology}/actionTypes/{actionType}`,
  `POST /api/v2/ontologies/{ontology}/actions/{action}/apply` (with
  `applyBatch`, up to 20 actions per call), and v1's
  `POST /api/v1/ontologies/{ontologyRid}/actions/{actionType}/validate`.
- A 200 from apply only means the request was processed; whether the action
  ran is in the validation result in the body.
- Submission criteria can test the current user (id, groups, attributes),
  parameters, properties of an object passed as a parameter, and whether the
  action runs in a Scenario. Conditions combine with all, any and none. They
  are checked at the moment of submission.
- Apply takes `branch` and `scenarioRid`. Edits to Object Storage V1 are
  eventually consistent; V2 edits are visible at once.
- OAuth2 scopes: `api:ontologies-read`, `api:ontologies-write`.

Sources:
- https://www.palantir.com/docs/foundry/api/v2/ontologies-v2-resources/actions/apply-action
- https://www.palantir.com/docs/foundry/api/ontology-resources/actions/validate-action
- https://www.palantir.com/docs/foundry/api/v2/ontologies-v2-resources/action-types/get-action-type
- https://www.palantir.com/docs/foundry/api/v2/ontologies-v2-resources/object-types/get-object-type-full-metadata
- https://www.palantir.com/docs/foundry/action-types/submission-criteria
- https://www.palantir.com/docs/foundry/workshop/actions-use/
- https://palantir.com/docs/foundry/action-types/parameter-overview/
- https://palantir.com/docs/foundry/ontology-sdk
- https://palantir.com/docs/foundry/announcements/2023-09/
- https://github.com/palantir/foundry-platform-python/blob/develop/docs/v2/Ontologies/Action.md
- https://www.palantir.com/docs/foundry/api/general/overview/errors
- https://community.palantir.com/t/validate-submission-criteria-for-action-types-for-users-and-set-of-parameters/4328

## Still to verify against a real enrollment

- Whether v2 apply's options include a validate-only mode, and whether a
  batched validate exists.
- Whether full metadata exposes the structure of submission criteria (which
  conditions reference which parameters).
- Whether objects carry a version usable as a validator, or If-Match exists.
- Whether `ActionParameterV2` carries `required` and a description.

## The strategy: a Foundry source for the gateway

The same architecture as the OpenAPI gateway: heart-http4k serves, open
offers, shared content-addressed affordance sets. Three Foundry pieces.

### 1. Types from ontology metadata, not OpenAPI

- Each object type is a resource type: its properties are the state, each
  link type a `_links` entry to a collection of the linked objects, and
  `titleProperty` the title of its self link.
- One ALPS profile per ontology; each action type a relation, with
  `displayName` as title, `description` as doc, and `toolDescription` in the
  form's description.
- Interfaces become profiles shared by their implementing types, so a client
  that knows an interface works on every type implementing it.
- OSDK's generated OpenAPI spec is not used: it is a static subset, early
  binding again.

### 2. Binding actions to the object they act on

- An action with exactly one object-reference parameter of type `T` is an act
  offered by objects of `T`; that parameter is filled from the object, as a
  template pointer fills a target.
- An action with no object parameter is an act at the bookmark; its result
  is a new resource.
- An action with several object parameters of one type needs a connector
  reader to name the subject.
- Each parameter's `dataType` becomes JSON Schema: strings, numbers,
  booleans, dates and timestamps directly; object references as choices from
  a link to that type's search (`hr:options`, a link source); attachments and
  markings later. Schemas stay per action, so sets stay shared; constraints
  that differ per object stay Foundry's to enforce, and come back as 422 at
  the field.

### 3. What is allowed now: a probe signal

- Foundry evaluates allowed actions on request rather than in the object. For
  each action bound to an object's type, the gateway validates with the
  subject filled, as the caller. Actions whose submission criteria pass are
  offered; the rest are absent with their `configuredFailureMessage` as the
  reason.
- The offer key is the set offered plus a digest of the reasons, as the
  gateway already keys tables, so identical states share one set.
- On submit, parameter failures become `invalid-input` at JSON Pointers and
  criteria failures `rule-refused` with Foundry's message.
- A parameter's evaluated `oneOf` options (display name and value) become a
  choices list the gateway serves; an `objectQueryResult`, a link to a search
  (HAL-SCHEMA-FORMS-HR Rule 8).

## The two hard problems

1. **Criteria that depend on other inputs.** A criterion on a parameter the
   user hasn't filled can't pass in a probe with the subject alone, so it
   would look absent. The default is to offer the act and let Foundry refuse
   it at submission as `rule-refused`, with its message: honest, and nothing
   lost. Absent is used only where a failing criterion can't depend on
   unfilled inputs, which needs the criteria's structure; a connector reader
   can override per action.
2. **Probe cost.** N validations per read, for N actions bound to the type.
   Cache per caller and object, keyed by the object's digest; batch where
   Foundry allows; probe only actions a connector marks as per-object, and
   offer the rest statically. Measure the cost; don't argue it.

## Also required

- **Per-caller credentials.** Criteria test who asks, so the gateway calls
  Foundry as each user (OAuth2). The credential forwarding deferred on
  15 October 2026 becomes required. Affordance sets hold no user data, so they
  stay shared.
- **Consistency.** Reads straight after an act may be stale on Object
  Storage V1. Without If-Match, the gateway's check-then-act applies, with
  the weaker guarantee the plan names.
- **Branches and Scenarios.** Each branch can be its own HEART service: one
  profile, different state.
- **Functions.** Read-only functions become HEART search forms or safe links.

## The exhibit it enables

An OSDK app generated from a test ontology, and the same regression suite.
Make Wednesday-style changes in Ontology Manager: rename an action parameter,
add a required one, tighten a submission criterion, add a `oneOf`. The OSDK
app needs regenerating and recompiling; ARTIE, heart-mcp and an agent
through HEART should adapt unchanged, as with the garden. Workshop proves
late binding works inside Foundry; HEART lets anything outside Foundry be
Workshop.

## Phases, when picked up

0. **Access**, arranged by the author: a Foundry enrollment or developer
   tier, and a test ontology with a few object types and actions. Settle the
   points still to verify.
1. **Read-only**: types, links, interfaces, profile; Layer 1 and search;
   heart-tck's read scenarios through the gateway.
2. **Actions as forms**: subject binding, schemas, apply, validation as
   problem details.
3. **The probe signal**: offers and reasons, caching, the offer-by-default
   rule, measured cost.
4. **Choices**: `oneOf` and object query results as Rule 8 choices.
5. **The exhibit**: the OSDK app against HEART clients through ontology
   changes, written up on Pages.

## Risks

- Access and licence are the author's to arrange.
- The full-metadata endpoint is in preview.
- Submission criteria's structure may not be public, which limits how
  precise an absence can be.
- Probe cost on types with many actions.
- Markings and permissions need careful forwarding.
