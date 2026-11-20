# Idea: `deploy:` typed relations in front of JSON-RPC (Amazon ECS first)

Noted 2026-11-13. Not started. Mockup first, as with `scm:` (see the scm profile decision).

## The idea in one paragraph

Put the HEART gateway in front of the most RPC-shaped API there is, Amazon ECS, and serve container deployments
as HEART resources with a small `deploy:` vocabulary: register, roll out, roll back, scale. Each act is offered
only when the service's state allows it, refusals explain themselves (`hr:unavailable`), and "tell me when the
rollout finishes" is a RISE watch. Then show the same kept script deploying through the same relations to a second
target, as the release did on GitHub and GitLab. If hypermedia can stand in front of JSON-RPC, nobody can say it
only works because the API was already REST-ish.

## Why JSON-RPC, and why it is worth taking on

- **JSON-RPC is RPC in its purest form.** One endpoint, a list of operation names, and nothing in an answer says
  what may be done next. Fielding's 2008 post opens on exactly this ("That is RPC. It screams RPC.").
- **ECS is JSON-RPC.** Every call is `POST /` with a header naming the operation
  (`X-Amz-Target: AmazonEC2ContainerServiceV20141113.UpdateService`), a JSON body, and SigV4 signing. The client
  hard-codes operation names, their parameters, and which call makes sense in which state. ECS won't tell you
  that `UpdateService` is pointless while a rollout is in progress; the docs do, or an error does.
- **MCP is JSON-RPC 2.0.** Every MCP tool call is `{"jsonrpc": "2.0", "method": "tools/call", …}`. "We put
  hypermedia in front of JSON-RPC" lands directly on what an AI team uses every day.
- **It completes the "own your controls" pitch:** our controls, in front of an API we don't control, however it
  is shaped.

## What Fielding said about it: comment 742

In the comments on "REST APIs must be hypertext-driven", answering the objection that a client always has prior
knowledge, Fielding wrote (22 October 2008, [comment 742](https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven#comment-742)):

> “Of course the client has prior knowledge. Every protocol, every media type definition, every URI scheme, and
> every link relationship type constitutes prior knowledge that the client must know (or learn) in order to make
> use of that knowledge. REST doesn’t eliminate the need for a clue. What REST does is concentrate that need for
> prior knowledge into readily standardizable forms. That is the essential distinction between data-oriented and
> control-oriented integration.
>
> It has value because it is far easier to standardize representation and relation types than it is to
> standardize objects and object-specific interfaces. In other words, there are fewer things to learn and they can
> be recombined in unanticipated ways while remaining understandable to the client.”

This is what `scm:` already does, and what `deploy:` would do:

| | Object-specific interface | Standardizable form |
|---|---|---|
| Merge | GitHub `PUT …/pulls/{n}/merge {merge_method, sha}`; GitLab `PUT …/merge_requests/{iid}/merge {squash, sha}` | `scm:merge` with a `method` input |
| Roll out | ECS `POST /` · `X-Amz-Target: …UpdateService` `{cluster, service, taskDefinition, forceNewDeployment}` | `deploy:roll-out` with a `revision` input |
| Can it act now? | GitHub `mergeable_state`, GitLab `detailed_merge_status`, ECS `deployments[].rolloutState` | the form is offered, or not, with `hr:unavailable` saying why |
| Who knows the specifics | every client | one connector per target |

**The honest caveat:** the coupling doesn't disappear; it moves into one place. The connector knows
`X-Amz-Target: …UpdateService` once. Every client, whether ARTIE, a browser or an agent, knows only
`deploy:roll-out` and the media type. That is the trade comment 742 describes.

**Standardizable, not standardized.** `scm:` and `deploy:` are our profiles. What makes them standardizable: small,
defined by meaning rather than by one server's endpoints, and shared by implementations that agree on nothing.
Publishing each profile as a document others can adopt is the step toward standard.

## Sketch

**Resources the gateway would serve:** cluster, service (with its current and pending deployments), task
definition revisions.

**Relations, offered by state:**

| Relation | Offered when | ECS operation behind it |
|---|---|---|
| `deploy:register` | always, on the service's task family | `RegisterTaskDefinition` |
| `deploy:roll-out` | no rollout `IN_PROGRESS` | `UpdateService` (new task definition) |
| `deploy:roll-back` | a previous revision exists and no rollout is in progress | `UpdateService` (previous task definition) |
| `deploy:scale` | the service is `ACTIVE` | `UpdateService` (`desiredCount`) |
| `deploy:watch` (RISE) | a rollout is `IN_PROGRESS` | `DescribeServices`, polled by the gateway until `COMPLETED` or `FAILED` |

**Guards:** If-Match on the service's state (a rollout started since the client read it is a 412); a refused act
answers with the reason from the connector's `absent:` table.

## What the gateway needs

1. **SigV4 signing** of upstream calls, with credentials from the environment (never printed, never committed).
2. **JSON-RPC operations** in connectors: today a connector names REST operations (method and path); this needs
   "POST / with this target header and this body".
3. **AWS's API models are Smithy, not OpenAPI.** Either read the Smithy JSON AST (published by AWS in
   `aws/api-models-aws`), or write the few operations by hand in the connector, as a start.
4. **State for guards:** derive "rollout in progress" from `DescribeServices`'s `deployments`.

## Where to run it

- **LocalStack**, if its free tier covers ECS (to check), so the demo runs on a laptop with no cloud account, as
  the local GitLab does.
- Or **a real AWS sandbox account**: real, but it needs the author's credentials and costs money. The author's call.

## Second target, later

The same `deploy:` relations in front of Kubernetes (a Deployment's rollout) or Nomad. With two targets the kept
deploy script runs unchanged on either, as the release does on GitHub and GitLab.

## To check before building

- ECS's protocol and operation names as above (`awsJson1_1`, `X-Amz-Target`), against the current API reference.
- `deployments[].rolloutState` values and how a circuit-breaker rollback shows up.
- LocalStack's ECS coverage on the free tier.
