# Support tickets

A HEART domain server (spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, B5): its domain
code and one declaration. The HEART library (`heart/`) serves it over HTTP and
CoAP; it has no controllers.

A ticket is one resource, `/tickets/{id}`. Its state is the subject, the
customer, the description, the priority, the assigned agent, and the
conversation in order. A ticket comes into being by `open-ticket`, an act the
service index offers at `/actions/open-ticket`; there is no create operation.
The answer is `201 Created` with the new ticket.

What may be done depends on the ticket's status:

| Status | Offered |
|---|---|
| `NEW` | assign, comment, change-priority |
| `ASSIGNED` | assign, comment, ask-customer, resolve, change-priority |
| `WAITING_ON_CUSTOMER` | comment, customer-replies, resolve, change-priority |
| `RESOLVED` | reopen, close, change-priority |
| `CLOSED` | nothing |

Agents are fixed values: `priya`, `marco`, `lena`, `tom`. Priority is `low`,
`normal`, `high` or `urgent`. Every text field is 1 to 2000 characters. Two
rules a schema cannot say are refused with 422: assigning the ticket to the
agent who already has it (`/agent`), and changing the priority to the one it
already has (`/priority`).

## Code

| Package | Holds |
|---|---|
| `domain` | Events, the state and its fold, the rules a schema cannot say (`TicketService`) |
| `declaration` | The vocabulary (`SupportProfile`), the resource type (`Tickets`), act inputs, what each act records (`TicketActs`) |
| `store` | Tickets' numbers and subjects, in memory or Postgres; the transaction around a new ticket |
| `config` | The `HeartResource` bean, and the Postgres stores |

## Run

```
../../scripts/dev-credentials.sh   # once: a Bearer token, its SHA-256 in .env, the token in ~/.artie
docker compose -f ../../server-spring/docker-compose.yml up -d postgres
./reset-db.sh                      # first time: creates the tickets database
set -a; . ./.env; set +a           # the service admits only that token, and will not start without one
./gradlew bootRun                  # HTTP on 8282, CoAP on 5685 (loopback only); migrates
./reset-db.sh                      # seeds ticket 1 (new) and ticket 2 (waiting on the customer)
```

Every state, act and the bookmark need the credential (SPEC §6.13). ARTIE and
heart-mcp find it in `~/.artie/credentials`.

Tests: `./gradlew test` runs the ticket rules (`tickets.feature`), the HEART
conformance features (`RunHeartTckTest`, the bookmark act included), the
architecture rules, and the whole server on Postgres in Testcontainers.
`tickets.feature` holds only the ticket's own rules; what every HEART server
does is the conformance features' business.

A Buster campaign: `scripts/buster-campaign.sh tickets`.
