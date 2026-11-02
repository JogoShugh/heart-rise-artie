# Idea: the gateway as a dam for a plain client (pass-through)

Status: idea, 2026-10-22. The transparent rung is built (`X-Gateway-Mode: pass-through`,
github-pass-through.feature). The plan for the rest, 2026-11-02, is at the end: "Plan: our own controls in front
of GitHub".

## Why

In the native-vs-HEART trial the Python script talks to api.github.com directly, and ARTIE talks to the gateway,
which talks to GitHub. The two arms do not cross the same layer. Part of HEART's cost (a local hop, the gateway's
re-read before each act) is the gateway's, and the Python never pays it. The Python also never gets anything the
gateway could give it.

A pass-through mode puts the plain client behind the same gateway. The client's code stays as it is: only its base URL
changes. That gives two things:

1. **A fair hop.** Both arms pay the same network path, so the timing difference is HEART's work alone.
2. **A dam.** The gateway can hold back some of what a plain client gets wrong, without the client knowing HEART. This
   is a small barrier, admittedly, but a real one. It answers the question: how much of the protection that
   ARTIE's checks give can sit in the server, for a client that will never follow a link?

## The rungs of the dam

Each rung is a switch, so a run can measure what it costs and what it stops.

| Rung | What the gateway does | What it stops | Extra calls to GitHub |
|---|---|---|---|
| `transparent` | Forwards method, path, query, headers and body as the caller; answers GitHub's answer untouched. Keeps the upstream record and the traceparent. | Nothing. It measures the hop. | none |
| `described` | Also refuses an operation GitHub's description does not have (404), and a body that breaks the operation's request schema (422), before GitHub is called. | Typos in paths, unknown fields, wrong types. | none |
| `connector` | Also refuses an operation the connector does not list (403, naming it). | Writes the deployment never meant to allow. | none |
| `offered` | Also reads the resource first, and refuses an act the connector's rules say is not offered now (409, with the same reason HEART gives: locked, archived, already closed). | Acting on a state that does not allow it. | one GET per write |
| `conditional` | Also asks for `If-Match` on writes and compares it with the state just read (428, 412), as HEART does. | Lost updates. The client must now send an ETag, so this is the first rung that asks something of it. | one GET per write |

The first four need no change to the client. The fifth needs the client to keep and send ETags, which most plain
clients do not do. That is where "plain client behind a dam" turns into "client that does part of HEART's job".

## How a request is told apart

The gateway already serves HEART resources at GitHub's own paths. A plain request to the same path must get GitHub's
own JSON, not a HEART representation. Two ways:

- **By `Accept` (recommended).** `application/hal+json` or HEART's media types get HEART. `application/vnd.github+json`
  (what GitHub's own clients send) gets pass-through. This is ordinary content negotiation, on one port.
- **By a separate port or path prefix**, such as `/raw/…`. This is simpler to reason about, but it is a second
  address for the same resource.

Writes have no clash: HEART's acts live at their own action targets, and GitHub's own write paths
(`POST /repos/{o}/{r}/issues/{n}/comments`) are free for pass-through.

## Things to get right

- **Addresses in answers.** GitHub's `Link` headers (paging), `Location`, and `url` members point at api.github.com.
  If they are left as they are, the client steps around the dam on the next page. Rewrite them to the gateway, as the
  gateway already does for links it lifts.
- **Headers kept.** ETag, rate-limit headers and `X-GitHub-Request-Id` pass back unchanged, so the client behaves as it
  did.
- **Credentials.** The same `UPSTREAM_AUTH=caller`. The gateway holds nothing of its own.
- **The record.** Both arms write the same upstream record (GATEWAY_RECORD), so the report can show what GitHub itself
  saw from each arm, call by call.

## What to measure

The same task and checker as the trial (23 checks), with the Python script unchanged except for its base URL:

| Arm | Path |
|---|---|
| native, direct | Python to GitHub (the existing runs) |
| native, transparent | Python to gateway to GitHub |
| native, described / connector / offered | the same, one rung up each time |
| HEART | ARTIE to gateway to GitHub (strict, lenient, look-then-leap, off-the-rails) |

For each arm, record the time, the requests the client sent, the calls GitHub saw, and the refusals the dam made.
A second pass uses the drift and chaos trial (fictional changes in the gateway) to see what each rung stops when the
world moves. It also shows that the Python, behind the `offered` rung, gets refusals it does not understand, because
it has no way to learn why except by reading prose.

## How it bears on the report

The report's table "To do what ARTIE does, the Python would have to…" lists 12 checks. GitHub allows 4, partly 6, and
not at all 2. The dam rungs show how many of those 12 a server can take over for a client that does not change. A
first guess, to check: `described` and `connector` cover the schema and allow-list checks, `offered` covers the
state rules, and none of them can supply what needs the client to change course (choose the act, follow a link,
decide again after a 412). That remainder is the honest line between what a server can protect and what only a
hypermedia client does.

## Open

- Whether `offered` reuses the gateway's resource types as they are (read, compute offers, then forward GitHub's raw
  write) or needs a second path through them.
- Whether a refused request should answer with problem+json (HEART's own) or in GitHub's own error shape, so the
  Python's existing error handling reads it.
- Name of the switch: `GATEWAY_PASS_THROUGH=transparent|described|connector|offered|conditional`, unset meaning off.

## Plan: our own controls in front of GitHub (2026-11-02)

The story: GitHub's API gives a client no
conditional writes, no "what is allowed now", no reasons, and no policy finer than a token's scopes. A gateway the
owner runs, even on a laptop, adds them. Any client gets most of them by changing its base URL; a HEART client gets
all of them. On an API the owner builds (the employees service), the same controls are built in, and stronger: its
conditional write is atomic.

**The owner chooses the controls, not the caller.** The rungs are the gateway's configuration,
`GATEWAY_DAM=described,connector,offered,conditional`, unset meaning transparent. A caller still asks for pass-through
with `X-Gateway-Mode: pass-through`.

| # | Step | What it adds |
|---|---|---|
| 1 | **`conditional`**: GitHub's JSON answered with a synthetic `ETag` (a digest of the body as read), and `If-None-Match` answered 304 by the gateway. A write that replaces a resource (PATCH, PUT or DELETE on a path GitHub also reads) needs `If-Match`: with none, 428; otherwise the gateway reads the resource again, as the caller, and a different digest is 412 (problem+json). POSTs that add to a resource (a comment) are not conditioned: that is `offered`'s work | Optimistic concurrency GitHub lacks, for a plain client |
| 2 | **Bare + validators**: `Bare.call` takes headers and gives back the answer's; a bare script can send `If-Match` with the `ETag` of its last read of the resource it writes. `artie generate --bare` with validators writes it so. Target B: to the gateway, passed through | A synthetic-ETag bare script: no HEART forms, no model, no lost edit |
| 3 | **`described`, `connector`, `offered`**: an operation GitHub's description lacks, 404; one the connector doesn't list, 403, naming it; a write the connector's state rules don't offer now (matched to its act by operation and fixed body), 409 with the rule's reason. All before GitHub is called | The owner's policy and state rules in front of GitHub |
| 4 | **The meddler ladder** (exhibits/native-vs-heart): one task, one teammate writing straight to GitHub. Arms: bare direct, bare via `transparent`, bare + validators via `conditional`, ARTIE kept script; the earlier ARTIE goal runs beside them | One table: what survives a teammate, what GitHub saw, what the gateway refused. No model needed |
| 5 | **The window, measured**: GitHub has no atomic conditional write, so the gateway checks, then writes. The time between its read and its write, beside an agent's time between its read and its write | Narrowed, not closed, said plainly; the owned API closes it |
| 6 | **The write-up**, "Our own controls in front of GitHub": controls by arm, the numbers, GitHub credited where it guards something itself | Bullets, folded details |

Static MCP tools pointed at the gateway come later, with model credit. FastMCP gives the model only the body, so
they would get the dam's refusals but could not carry a validator.
