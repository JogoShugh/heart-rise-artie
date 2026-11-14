# Idea: decades in five minutes — static MCP tools against HEART, in a world that keeps changing

Status: idea, 2026-11-02. Conceptual only; nothing built. Follows exhibits/mcp-over-openapi ("Static Tools,
Moving Service"), which changed one service five ways, once per run.

## The point

Fielding's case for REST is evolvability over the long run. He calls it "software design on the scale of decades";
check the exact words and source before quoting. In the InfoQ interview on versioning, hypermedia and REST (with Mike
Amundsen), he argues that an API with a version number in it is not REST, and that websites change all the time
without one. The client adapts because the server tells it, in each response, what it can do now.

Our bench showed one service changing once. This shows a whole ecosystem changing the way years of releases change
one, compressed into five minutes:
- services coming up;
- services moving, splitting and retiring;
- forms and workflows changing;
- other people editing the same records.

All of it happens while agents keep working.

## The world (heart-sims, one clock)

A service index at one bookmark lists the services (heart-core's `ServiceIndex`). There's one release every minute,
and something happens every 10 to 20 seconds within it.

| Minute | Releases (what years of change look like) |
|---|---|
| 0 | employees, as today. Agents start; static tools are generated from the OpenAPI twin now |
| 1 | **payroll comes up** (a new service in the index); employees' acts **move**; someone else starts editing records |
| 2 | input **renamed**; update **needs a reason**; **badges** comes up, reusing the staff profile's relations |
| 3 | dismissal needs **notice** first; update becomes **PATCH**; the employee list is now **paged** |
| 4 | employees **splits** into people and assignments: old addresses answer with where things went (308, or a link); an enum gains a value |
| 5 | employees **retires**: its bookmark entry points to its successor. The **dialect upgrades** for new clients (L0 to L2), and old clients keep L0 |

None of these releases has a version number. URLs never carry one. The OpenAPI twin changes with each release, as
a real service's would.

## Who works in it

All of them get the same task stream: about one task every 10 seconds (hire, promote, give notice, dismiss, pay,
award a badge), plus the meddler.

| Arm | What it is |
|---|---|
| Static MCP, frozen | FastMCP from the twin at minute 0, never rebuilt: the usual deployment |
| Static MCP, restarted | rebuilt from the twin every minute: the best case for static tools, and the cost of keeping them current |
| Meta-tools | list, schema, invoke over the twin, read again on demand: discovery, but from the description |
| Bare script, frozen | the calls one run made, replayed |
| heart-mcp, lean | the six verbs |
| heart-mcp backed by ARTIE | `heart_do(words)`, once built (spec/PLAN-HEART-MCP-ARTIE-BACKED.md) |
| ARTIE intent | the goal's steps, chosen from what is offered at each step |
| ARTIE kept + fallback | no model while pins hold; the words only where a form changed (`on-change: intent`) |

## What is measured, on one timeline per arm

| Measure | Why |
|---|---|
| Tasks done, per minute | adaptability over time, not once |
| Silent harm: lost edits, writes to the wrong resource, success claimed but not done | the failures nobody sees |
| Loud failures, and work left half done | what static tools do when they notice |
| Time to adapt after each release | seconds until the first success after it |
| People needed: restarts, spec regeneration, script edits | the cost static tools hide |
| Tokens per task, model calls per task | where the static tools win, shown plainly |
| Requests, bytes over the wire, latency per task | what L2 caching buys back |
| Cache: 304s, affordance sets and schemas fetched vs reused | rapidity and payload, made visible |

## Caching, shown not claimed (L2)

- Affordance sets and schemas are content-addressed and immutable. They're fetched once, then never again, until a
  release changes one.
- Layer 1 is revalidated with `If-None-Match`. When nothing changed, the answer is a 304 with no body.
- **A new service that reuses a profile costs no new fetches.** When badges comes up with the staff profile's
  relations, an L2 client has its sets already: zero bytes to learn a new service. That's the strongest picture of
  hypermedia plus caching.
- Shown per minute: bytes per task and requests per task, for L2, L0, static tools, and the restarted static tools,
  which re-read the whole description and the whole tool list every minute.

## Expected (to be confirmed, not assumed)

- **Static, frozen:** fails from minute 1 on, partly loudly and partly silently. It overwrites others' edits all the
  way through, and never sees payroll or badges.
- **Static, restarted:** recovers each minute after a restart. It pays a restart and a larger tool list per release,
  writes over edits, and fails in the window before each restart. Between releases it's still the cheapest in
  tokens, and the report says so.
- **HEART arms:** adapt within a task of each release, with no restarts. They keep every edit, or stop and ask.
  Bytes per task fall as the caches warm, and they stay low as new services arrive.

## The page

A live five-minute timeline:
- one lane per arm;
- each release a vertical line;
- each task a dot: done, failed loudly, harmed silently, or asked;
- below it, bytes, tokens and cache hits per minute.

After the run comes a report with folded details (reports-bullets-collapsed), crediting the static tools where they
win (no-one-upmanship).

## What building it takes (later, not now)

1. **heart-sims, a world clock.** A timeline file played by `/_sim/world/play` that adds services to the index and
   applies releases on schedule. It needs new kinds of change beyond today's five: split, paged, retired with a
   successor, a dialect upgrade, and a new service reusing a profile.
2. **The new services**, as heart-core declarations: payroll and badges.
3. **A task stream and the meddler**, from the bench, run for five minutes against every arm at once, each in its own
   world, reset to the same timeline.
4. **Metrics** from the sim's log and the clients' traces: bytes, 304s, and set and schema fetches.
5. **The timeline page.**

## Open questions

- One model for every arm (Sonnet), and the budget for five minutes times eight arms.
- Do the static arms' restarts happen on a fixed schedule, or when a person would notice? The fixed schedule is
  kinder to them.
- Whether minute 5's dialect upgrade is in scope, or a second run.
- Before quoting Fielding: check the exact wording of the "scale of decades" line and of the InfoQ interview.
