# heart-mcp proof

*Evidence file · branch `mcp-spike` · captured 2026-10-12 18:08:22 EDT*

This is the Markdown version of the published proof:
<https://claude.ai/artifact/WmCs2RSDDYmr1euFnQJp19>. How it works, with diagrams, is in
[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).

## Does it work?

Yes. An MCP host worked three different HEART services through heart-mcp, with no code about any of them. The HEART rules held throughout.

Everything below was captured from real runs on 12 October 2026. The runs used:

- the reference garden server on Postgres;
- the heart-mcp Docker image;
- Claude Code as the MCP host.

No step is simulated. When an act changed a bed, the database row count shows it. When an act was refused or never sent, the count stays the same.

| | |
|---|---|
| **3 services** | Garden, basketball and tickets, with Claude Code as the host |
| **16 steps** | The Docker image over raw JSON-RPC |
| **0** | Addresses shown to the model |
| **12/12** | Scenarios green, against the live garden |
| **Red** | When the code was broken on purpose |

## Exhibit 1 · Claude Code drives the garden through heart-mcp

The run used headless `claude -p`. Its only MCP server was the heart-mcp Docker image, and it was allowed only the six heart tools.

The prompt: “In the garden, water bed 2 with 250 ml. Then try to water it again with 700 ml. Report exactly what the service said each time.”

Nothing told Claude how the garden works. It took 6 turns.

- **Claude calls** `heart_look {"service": "garden"}`
  - heart-mcp answers **read**. Acts offered now: `garden:search-beds, garden:get-bed, garden:build-bed`.
- **Claude calls** `heart_act {"act": "garden:get-bed", "fields": {"id": 2}}`
  - heart-mcp answers **acted** — “garden:get-bed was done.” Acts offered now: `garden:water-cell`.
- **Claude calls** `heart_act {"act": "garden:water-cell", "fields": {"milliliters": 250}}`
  - heart-mcp answers **acted** — “garden:water-cell was done.” Acts offered now: `garden:harvest`.
- **Claude calls** `heart_explain {"act": "garden:water-cell"}`
  - heart-mcp answers **read** — “The crop is ready; it needs harvesting, not more water.” Acts offered now: `garden:harvest`.

What Claude reported at the end:

> First watering worked. Second watering never got sent, because the service stopped offering the water act once the first one finished.
>
> **1. Water bed 2 with 250 ml**
> - Bed 2 was "bed-2", a 4×8 ft bed of tomatoes. Before watering it was `PLANTED`, with growth 80 and moisture 40.
> - Service said: `"garden:water-cell was done."` (outcome kind `acted`).
> - After watering it was `READY_TO_HARVEST`, with growth 100 and moisture 65.
>
> **2. Water again with 700 ml**
> - After the first watering, the service offered only `garden:harvest`. `garden:water-cell` was gone, so I asked the service why instead of sending it.
> - Service said: `"The crop is ready; it needs harvesting, not more water."`
> - Bed state did not change. I did not harvest.

Database rows for bed 2 went from 3 to 4: exactly one event, the 250 ml watering. The 700 ml act was never sent: once the bed was ready, it no longer offered watering. Claude saw that, and asked why rather than guessing.

## Exhibit 1b · Basketball and support tickets, through the same image

The same heart-mcp image ran with two bookmarks: `basketball=http://host.docker.internal:8181/` and `tickets=http://host.docker.internal:8282/`.

- Neither heart-mcp nor ARTIE has any code about games or tickets.
- Claude learned each service's acts, fields and rules from the responses.
- Both servers ran on Postgres, and both were reset after the runs.

### Basketball: find the live game, score, ask why an act is missing

- **Claude calls** `heart_look {"service": "basketball"}`
  - heart-mcp answers **read**. Acts offered now: `basketball:search-games, basketball:get-game`.
- **Claude calls** `heart_act {"act": "basketball:search-games", "fields": {"name": " "}}`
  - heart-mcp answers **acted** — “basketball:search-games was done.” Acts offered now: `none`.
- **Claude calls** `heart_follow {"rel": "item", "title": "Eagles v Marauders"}`
  - heart-mcp answers **read**. Acts offered now: `basketball:tip-off`.
- **Claude calls** `heart_back {}`
  - heart-mcp answers **read**. Acts offered now: `none`.
- **Claude calls** `heart_follow {"rel": "item", "title": "Hornets v Comets"}`
  - heart-mcp answers **read**. Acts offered now: `basketball:call-timeout, basketball:correct-score, basketball:end-period, basketball:free-throw, basketball:record-foul, basketball:score-basket, basketball:turnover`.
- **Claude calls** `heart_act {"act": "basketball:score-basket", "fields": {"team": "home", "playerNumber": 5, "points": 2}}`
  - heart-mcp answers **acted** — “basketball:score-basket was done.” Acts offered now: `basketball:call-timeout, basketball:correct-score, basketball:end-period, basketball:free-throw, basketball:record-foul, basketball:score-basket, basketball:turnover`.
- **Claude calls** `heart_explain {"act": "basketball:tip-off"}`
  - heart-mcp answers **read** — “The game has already started.” Acts offered now: `basketball:call-timeout, basketball:correct-score, basketball:end-period, basketball:free-throw, basketball:record-foul, basketball:score-basket, basketball:turnover`.

<details><summary>What Claude reported</summary>

> Recorded one scoring play in the live game and got the service's reason for one act it doesn't offer.
>
> **Live game:** Hornets v Comets (id 2), phase `LIVE`, period 2 of 4. The other game, Eagles v Marauders, is `SCHEDULED`. I found games by searching for a single space, so a game whose name has no space would not have shown up.
>
> **Scoring play:** 2-point basket for the home team (Hornets) by #5 Kai. Service reply: `"basketball:score-basket was done."`
> - Saved as sequenceNo 8.
> - Score now Hornets 4, Comets 5.
> - Possession passed to `away`.
>
> **Act not offered:** `basketball:tip-off`. Service's exact answer:
>
> > "The game has already started."

</details>

Game 2 went from 7 to 8 events. The new row: `8 | BasketScored | {"team": "home", "points": 2, "playerNumber": 5}`.

### Tickets, first try: an agent who doesn't exist

- **Claude calls** `heart_look {"service": "tickets"}`
  - heart-mcp answers **read**. Acts offered now: `support:search-tickets, support:get-ticket, support:open-ticket`.
- **Claude calls** `heart_act {"act": "support:get-ticket", "fields": {"id": 1}}`
  - heart-mcp answers **acted** — “support:get-ticket was done.” Acts offered now: `support:assign, support:change-priority, support:comment`.
- **Claude calls** `heart_act {"act": "support:assign", "fields": {"agent": "dana"}}`
  - heart-mcp answers **rejected-before-sending** — “The fields do not satisfy support:assign's schema. Nothing was sent. · agent is not one of [priya, marco, lena, tom]” Acts offered now: `support:assign, support:change-priority, support:comment`.
- **Claude calls** `heart_act {"act": "support:assign", "fields": {"agent": "dana"}}`
  - heart-mcp answers **rejected-before-sending** — “The fields do not satisfy support:assign's schema. Nothing was sent. · agent is not one of [priya, marco, lena, tom]” Acts offered now: `support:assign, support:change-priority, support:comment`.

<details><summary>What Claude reported</summary>

> Ticket 1 is still unassigned. The service never responded to either attempt, because neither request reached it. The heart client blocked both locally.
>
> The `support:assign` act only accepts one of four agents: `priya`, `marco`, `lena`, `tom`. No agent is named Dana. The only "Dana" on ticket 1 is the customer, Dana Reyes.
>
> Ticket 1: "Cannot log in", customer Dana Reyes, priority `high`, status `NEW`, agent `null`.
>
> **Attempt 1** (`agent: "dana"`): the client blocked it before sending:
> - outcome: `rejected-before-sending`
> - message: "The fields do not satisfy support:assign's schema. Nothing was sent."
> - violation: "agent is not one of [priya, marco, lena, tom]"
>
> **Attempt 2** (same fields): identical result. Same outcome, message and violation.
>
> Because nothing was sent, the second try never tested how the service handles assigning the same agent twice. To test that, choose one of the four valid agents and I'll run it twice.

</details>

Ticket 1 stayed at 1 event. The schema allows only four agents, so heart-mcp refused “dana” before sending. Claude said so rather than guessing.

### Tickets, second try: assign to Priya, then again

- **Claude calls** `heart_look {"service": "tickets"}`
  - heart-mcp answers **read**. Acts offered now: `support:search-tickets, support:get-ticket, support:open-ticket`.
- **Claude calls** `heart_act {"act": "support:get-ticket", "fields": {"id": 1}}`
  - heart-mcp answers **acted** — “support:get-ticket was done.” Acts offered now: `support:assign, support:change-priority, support:comment`.
- **Claude calls** `heart_act {"act": "support:assign", "fields": {"agent": "priya"}}`
  - heart-mcp answers **acted** — “support:assign was done.” Acts offered now: `support:ask-customer, support:assign, support:change-priority, support:comment, support:resolve`.
- **Claude calls** `heart_act {"act": "support:assign", "fields": {"agent": "priya"}}`
  - heart-mcp answers **rule-refused** — “support:assign was refused by a rule of the resource (422): /agent: The ticket is already assigned to priya” Acts offered now: `support:ask-customer, support:assign, support:change-priority, support:comment, support:resolve`.

<details><summary>What Claude reported</summary>

> Both assign attempts on ticket 1 are done. First one worked; second one was refused.
>
> **Ticket 1:** "Cannot log in", customer Dana Reyes, priority `high`. Before assign: status `NEW`, agent `null`.
>
> **First assign (`agent: "priya"`)**
> - Outcome: `acted`, message `"support:assign was done."`
> - Ticket now status `ASSIGNED`, agent `priya`.
>
> **Second assign (`agent: "priya"`)**
> - Outcome: `rule-refused`, message `"support:assign was refused by a rule of the resource (422): /agent: The ticket is already assigned to priya"`
> - Violations: `["/agent: The ticket is already assigned to priya"]`
> - Ticket not changed: still `ASSIGNED` to `priya`.
>
> The service still listed `support:assign` as an available act after the first assign. Repeat assign to same agent only fails when sent, with 422.

</details>

Ticket 1 went from 1 to 2 events. The new row: `2 | Assigned | {"agent": "priya"}`. The second assign was refused by the ticket's own rule, and no row was added.

## Exhibit 2 · Every kind of answer, with database counts

A Python script acted as the host. It ran `docker run -i heart-mcp` and spoke JSON-RPC over stdin and stdout. Before and after each step on a bed, it counted that bed's rows in Postgres's `heart_event` table.

| # | Step and tool call | Outcome | What the service or heart-mcp said | Acts offered after | DB rows |
|---|---|---|---|---|---|
| 1 | Start at the garden's bookmark<br>`heart_look {"service": "garden"}` | **read** |  | `garden:search-beds, garden:get-bed, garden:build-bed` |  |
| 2 | Search beds by name (a GET form)<br>`heart_act {"act": "garden:search-beds", "fields": {"name": "bed-2"}}` | **acted** | garden:search-beds was done. | `—` |  |
| 3 | Follow the result to bed 2<br>`heart_follow {"rel": "item", "title": "bed-2"}` | **read** |  | `garden:water-cell` | 3 → 3 |
| 4 | Ask the service to describe watering<br>`heart_describe {"act": "garden:water-cell"}` | **read** | “Adds the given millilitres of water to each cell. Offered only while the bed is planted. Each watering raises …” | `garden:water-cell` | 3 → 3 |
| 5 | Water 700 ml: too much<br>`heart_act {"act": "garden:water-cell", "fields": {"milliliters": 700}}` | **rule-refused** | garden:water-cell was refused by a rule of the resource (422): /milliliters: The bed would be waterlogged: at most 600 ml now. | `garden:water-cell` | 3 → 3 |
| 6 | Water 2000 ml: breaks the schema<br>`heart_act {"act": "garden:water-cell", "fields": {"milliliters": 2000}}` | **rejected-before-sending** | The fields do not satisfy garden:water-cell's schema. Nothing was sent. | `garden:water-cell` | 3 → 3 |
| 7 | Water 250 ml<br>`heart_act {"act": "garden:water-cell", "fields": {"milliliters": 250}}` | **acted** | garden:water-cell was done. | `garden:harvest` | **3 → 4** |
| 8 | Water again: no longer offered<br>`heart_act {"act": "garden:water-cell", "fields": {"milliliters": 100}}` | **rejected-before-sending** | garden:water-cell is not offered now. Offered: garden:harvest. Nothing was sent. | `garden:harvest` | 4 → 4 |
| 9 | Ask why watering is gone<br>`heart_explain {"act": "garden:water-cell"}` | **read** | The crop is ready; it needs harvesting, not more water. | `garden:harvest` | 4 → 4 |
| 10 | Back to the search results<br>`heart_back {}` | **read** |  | `—` |  |
| 11 | Back to the bookmark<br>`heart_back {}` | **read** |  | `garden:search-beds, garden:get-bed, garden:build-bed` |  |
| 12 | Search for bed 4<br>`heart_act {"act": "garden:search-beds", "fields": {"name": "bed-4"}}` | **acted** | garden:search-beds was done. | `—` |  |
| 13 | Follow to bed 4<br>`heart_follow {"rel": "item", "title": "bed-4"}` | **read** |  | `garden:water-cell` | 1 → 1 |
| 14 | *Meanwhile another client (curl) waters bed 4 with 50 ml: HTTP 200. Rows for bed 4 now: **2*** | | | | |
| 15 | Water bed 4 on what we saw<br>`heart_act {"act": "garden:water-cell", "fields": {"milliliters": 50}}` | **stale** | garden:water-cell was refused as stale (412); re-synced, act dropped. The resource below is the fresh one. | `garden:water-cell` | 2 → 2 |
| 16 | A service that is not configured<br>`heart_look {"service": "orchard"}` | **error** | No service named 'orchard'. The services are: garden. | `—` |  |

Step 5, a rule refusal, as heart-mcp returned it:

```json
{
 "kind": "rule-refused",
 "message": "garden:water-cell was refused by a rule of the resource (422): /milliliters: The bed would be waterlogged: at most 600 ml now.",
 "violations": [
  "/milliliters: The bed would be waterlogged: at most 600 ml now."
 ]
}
```

Step 15: another client wrote first, so the act was stale. heart-mcp dropped the act and never resent it:

```json
{
 "kind": "stale",
 "message": "garden:water-cell was refused as stale (412); re-synced, act dropped. The resource below is the fresh one."
}
```

## Exhibit 3 · What actually crosses stdio

The handshake, the fixed tool list, and one raw `tools/call` with its answer, copied from the transcript.

`initialize`, and heart-mcp's answer:

```json
{
 "protocolVersion": "2025-06-18",
 "capabilities": {
  "tools": {
   "listChanged": false
  }
 },
 "serverInfo": {
  "name": "heart-mcp",
  "version": "0.1.0"
 }
}
```

`tools/list`: six tools, and none takes an address.

| Tool | Inputs |
|---|---|
| `heart_look` | `service` |
| `heart_act` | `act, fields` |
| `heart_follow` | `rel, title` |
| `heart_describe` | `act` |
| `heart_explain` | `act` |
| `heart_back` | `none` |

Host to heart-mcp:

```json
{
 "jsonrpc": "2.0",
 "method": "tools/call",
 "params": {
  "name": "heart_act",
  "arguments": {
   "act": "garden:water-cell",
   "fields": {
    "milliliters": 700
   }
  }
 },
 "id": 7
}
```

heart-mcp to host (trimmed):

```json
{
 "id": 7,
 "result": {
  "content": [
   {
    "text": "{\"resource\":{\"state\":{\"id\":2,\"label\":\"bed-2\",\"widthFt\":4,\"lengthFt\":8,\"status\":\"PLANTED\",\"cropName\":\"tomato\",\"growthPoints\":80,\"moistureLevel\":40}},\"acts\":[{\"name\":\"garden:water-cell\",\"title\":\"Water the bed\",\"description\":\"Pours water into every cell of a planted bed. Enough water makes it ready to harvest.\",\"schema\":{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"properties\":{\"milliliters\":{\"description\":\"Millilitres of water poured into each cell\",\"examples\":[250],\"maximum\":1000,\"minimum\":1,\"title\":\"Water per cell\",\"type\":\"integer\"}},\"required\":[\"milliliters\"],\"title\":\"Water Cell\",\"type\":\"object\"}}],\"links\":[],\"outcome\":{\"kind\":\"rule-refused\",\"message\":\"garden:water-cell was refused by a rule of the resource (422): /milliliters: The bed would be waterlogged: at most 600 ml now.\",\"violations\":[\"/milliliters: The bed would be waterlogged: at most 600 ml now.\"]}}",
    "type": "text"
   }
  ],
  "isError": false,
  "structuredContent": {
   "resource": {
    "state": {
     "id": 2,
     "label": "bed-2",
     "widthFt": 4,
     "lengthFt": 8,
     "status": "PLANTED",
     "cropName": "tomato",
     "growthPoints": 80,
     "moistureLevel": 40
    }
   },
   "acts": [
    {
     "name": "garden:water-cell",
     "title": "Water the bed",
     "description": "Pours water into every cell of a planted bed. Enough water makes it ready to harvest.",
     "schema": {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": {
       "milliliters": {
        "description": "Millilitres of water poured into each cell",
        "examples": [
         250
        ],
        "maximum": 1000,

 …
```

## Exhibit 4 · Zero addresses in every answer

Every response heart-mcp sent in Exhibit 2 was searched for the strings below. The model is shown states, act names, titles, schemas and link titles. ARTIE keeps every URL, method and validator to itself.

| Searched for | Times found |
|---|---|
| `href` | 0 |
| `/beds/` | 0 |
| `localhost` | 0 |
| `If-Match` | 0 |
| `/schemas/` | 0 |
| `/affordance-sets/` | 0 |

## Exhibit 5 · Only legal acts reached the server

ARTIE wrote 27 OpenTelemetry-style lines from inside the container (rung.applied 9, integrity.ok 9, forms.followed 4, act.sent 3, act.refused 2).

- Exactly three acts were sent, each with the validator heart-mcp held and a trace span.
- The two choices that broke a rule before sending appear nowhere here: 2000 ml, and watering a bed that no longer offered it.

| Act sent | If-Match | Server answered | Span |
|---|---|---|---|
| `garden:water-cell` | `"seq-3.hsf.L2"` | 422 | `ab55c27e1be54fe5` |
| `garden:water-cell` | `"seq-3.hsf.L2"` | 200 | `c67e9f0543fcddf6` |
| `garden:water-cell` | `"seq-1.hsf.L2"` | 412 | `bb55de151d566714` |

## Exhibit 6 · 12/12 green, run fresh at 2026-10-12 22:09:18 UTC

`scripts/mcp-check.sh` starts its own garden server and resets the data before each scenario. It drives heart-mcp through the official MCP client SDK, over the SDK's stdio transport. Then it stops the server.

- ✅ The tools are six verbs, and none takes an address
- ✅ A service that is not configured is refused, naming those that are
- ✅ Looking at the garden shows its acts with titles and schemas, and no address
- ✅ From the bookmark, the host finds bed 2, which offers only watering
- ✅ Watering bed 2 is acted, and the result is the bed's new state and acts
- ✅ An act the bed does not offer is rejected, and nothing is sent
- ✅ Fields that break the act's schema are rejected, and nothing is sent
- ✅ Too much water is refused by the bed's rule, in the server's words
- ✅ When another client waters first, the act is stale, dropped, and the result is fresh
- ✅ Asked why bed 1 cannot be harvested, the host gets the server's reason
- ✅ Describing an offered act gives the description from the service's profile
- ✅ Back returns to the resources before, each read again

## Exhibit 7 · Broken on purpose, the suite went red

To check that the tests aren't decorative, I removed the line that strips a schema's own address before the schema is shown to the model. The live run failed at once, naming the leaked address. I restored the line, and the run went green again. That run happened earlier the same evening; the failure message is copied as reported.

```text
FAIL  Looking at the garden shows its acts with titles and schemas, and no address
org.opentest4j.AssertionFailedError: addresses in the result:
  [/acts/2/schema/$id, /acts/2/schema/$id=/schemas/d017ea1b10b893b8]
```

## Limits, stated plainly

- **Three services:** the garden in full; basketball and tickets by Claude Code runs only. The 12 automated scenarios cover the garden alone.
- **stdio only:** MCP's HTTP transport and auth aren't built.
- **Protocol version:** it speaks MCP 2025-06-18, the version the Kotlin SDK 0.8.1 implements, not the 2026-07-28 revision that SPEC §8.1 describes.
- **Four Claude Code runs, one prompt each:** they show the path works. They say nothing about how often a model chooses well.
- **TDD gate:** the repository's gate doesn't watch `mcp/` yet. The work followed the order anyway: feature, a red compile, code, then green.
- **Status:** it's a spike branch. It's pushed, but not merged.

## Reproduce it

From the repository root, on branch `mcp-spike`, with nothing on port 8080:

```sh
scripts/mcp-check.sh
scripts/mcp-check.sh --docker
```

For the host run, register the image with Claude Code:

```sh
claude mcp add heart -- docker run -i --rm -e HEART_BOOKMARKS=garden=http://host.docker.internal:8080/ heart-mcp
```

The source:

- `mcp/heart-mcp`: the library;
- `mcp/heart-mcp-server`: the stdio app;
- `mcp/Dockerfile`;
- `mcp/heart-mcp/src/test/resources/features/mcp-tools.feature`.
