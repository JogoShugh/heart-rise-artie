# Idea: MCP over OpenAPI, as others build it, set beside heart-mcp

Status: run, 2026-11-02, in exhibits/mcp-over-openapi (bench.py, report/index.html): FastMCP's from_openapi
over the twin and over a typical spec, meta-tools, heart-mcp (as first met and lean), an ARTIE goal run and an
ARTIE script, on the employees service in heart-sims, with a concurrent edit and five changes made under the
client. Code Mode and the npm openapi-mcp-server were not run. First written 2026-11-01. From a question: what is the usual way to give an agent MCP tools over an API that has
an OpenAPI description? Is it one tool per endpoint, or something more dynamic, even hypermedia?

## What others do (as of November 2026)

| Way | How | Who | What it costs |
|---|---|---|---|
| One tool per endpoint | Each operation becomes a tool: `operationId` its name, parameters and body its input schema | The default of most generators: Stainless, Speakeasy, FastMCP's `from_openapi`, community OpenAPI-to-MCP servers | Every tool's schema is in the model's context from the start; Stainless reports Cursor takes about 40 tools per server; vendors say selection gets worse beyond 25 to 50 tools |
| A few meta-tools | List the endpoints, get one endpoint's schema, invoke it | Stainless's dynamic mode (`--tools=dynamic`, three tools); Apideck (four tools, about 1,300 tokens up front whether 50 endpoints or 500); some community servers switch to it on their own past 100 endpoints | One more call per endpoint used, for its schema |
| Code over the spec | One tool searches the OpenAPI document by code the model writes; another runs code against the API | Cloudflare's Code Mode: about 2,500 endpoints behind `search()` and `execute()`, about 1,000 tokens fixed; `?codemode=false` gives one tool per endpoint again | The model writes and runs code; it runs in an isolated worker that keeps credentials out of the model's context |

So the usual way is still one tool per endpoint, and large APIs move to discovery. But the discovery is over the
static description: what exists, in principle. None of these tells the model what this resource offers now, in
its state, or why an act is absent. The order of calls is still learned from prose, and a call that isn't allowed
now is learned from the server's refusal.

The evidence is vendors' own blogs, each with a stake in its way. No independent benchmark of task success between
the ways was found.

## What heart-mcp does instead

heart-mcp (mcp/README.md) keeps six fixed tools (`heart_look`, `heart_act`, `heart_follow`, `heart_describe`,
`heart_explain`, `heart_back`), and every result holds what the current resource offers now: its acts with their
schemas, its links, and, on request, why an act is absent. The tool list never grows with the API, because the model
sees one resource at a time. It is discovery, as the meta-tools are, but over the state
the service is in, not over its description. (One tool per offered act was weighed and parked:
mcp-per-act-tools.md.)

| | Per endpoint | Meta-tools | Code Mode | heart-mcp |
|---|---|---|---|---|
| Tools in context | one per endpoint | 3 or 4 | 2 | 6 |
| Discovers from | nothing; all up front | the description | the description, by code | the current representation |
| Knows what is legal now | no | no | no | yes: only offered acts are listed |
| Knows why not | from a refusal | from a refusal | from a refusal | asked before sending (`heart_explain`) |
| Concurrent change | lost update, unless the API has ETags and the model uses them | same | same | If-Match on every act; a stale act is dropped and the fresh state shown |

## The experiment

Every heart-core service already serves its OpenAPI twin at `/openapi.json` (OpenApiDocument), so the same service
can be driven all four ways with nothing new on the service's side. The employees service in heart-sims (Spring
HATEOAS's example employees, as HEART) and the garden are the first candidates; openapi-twin-comparison.md and
openapi-at-its-best-client.md are the same comparison from the client's side.

1. Generate per-endpoint tools from the twin, as a common generator does (FastMCP's `from_openapi`, run as it
   ships, not written by us).
2. The same twin behind Stainless-style meta-tools (list, schema, invoke).
3. heart-mcp against the HEART service.
4. One goal each, run by the same model, several times: for the employees, hire Samwise as gardener, make him mayor,
   then dismiss Bilbo; for the garden, a goal that needs an act only some states offer.

Measured from each run's trace:

| Measure | Why |
|---|---|
| Tokens up front, and per step | the cost each way names as its reason |
| Calls per goal | what discovery costs in calls |
| Refused calls, by reason (not offered, stale, invalid) | what the model learned only by failing |
| Goal reached, and steps wasted | whether the cheaper context costs correctness |
| A concurrent change mid-run | whether the lost update happens |

## Open

- Which generator to use for (1) and (2), run as their makers ship them, so the comparison is fair to them
  (no-one-upmanship: credit their ways plainly).
- Whether Code Mode is run at all: it needs Cloudflare's worker, or a fair local stand-in.
