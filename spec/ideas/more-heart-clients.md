# Idea: more HEART clients, in other languages

**Status:** idea. Raised by the author on 2026-11-15. Since then heart-cs is built (C#, L1, 106 of 108 conformance scenarios, local only); Python is next, outlined below (2026-11-24).

## Why

- heart-ts showed the media type works for a client written from the spec alone, not from ARTIE.
- Kept scripts are data, with their own schema (`spec/schemas/heart-script.draft.schema.json`). Today only Kotlin runs them.
- More clients test both claims in more places:
  - the protocol can be learned from the spec;
  - a kept script runs anywhere, with no model.

## What each client does, in levels

- **L1, the library:**
  - follows links, acts on offered forms with `If-Match`, checks bodies against the form's schema, and reads refusals;
  - passes the conformance features, as heart-ts does.
- **L2, the replay runner:**
  - reads a kept YAML script and its pins file, and fills in `${...}` values;
  - follows and acts, and stops before sending anything when a pinned form is no longer offered as pinned;
  - replays only: no model, no generating.
- **The gates:** each language gets a bdd-gates stack folder, so the client is built Gherkin first like everything else.

## Shared first: replay features every runner passes

- These are a language-neutral set of Gherkin replay scenarios, like the conformance features but for kept scripts.
- They cover:
  - inputs;
  - kept values;
  - `for each`;
  - a pin that still matches;
  - a pin that changed, which must stop the run;
  - a refusal on purpose.
- Every runner, Kotlin included, must pass them. That keeps the runners from drifting apart.

## Languages

| Language | Why it matters | Notes |
|---|---|---|
| TypeScript | done (heart-ts, L1) | First to get L2: the client and its full coverage are already there |
| Python | automation, data, AI tooling; most agent code is Python | The bdd-gates stack would use pytest-bdd, Ruff and import-linter. New kept Python is the author's call (see Open) |
| Go | cloud and ops tooling, CLIs, Kubernetes; the people who run releases | Strong case: single binary, a natural fit for a replay runner in CI |
| F# | functional .NET; discriminated unions mirror acts, refusals and rungs closely | The .NET client: built in F#, called from C# (see below) |
| C# | enterprise .NET shops | Calls the F# library through a C#-shaped facade |
| Rust | embedded, edge, high assurance; CoAP devices | Pairs with `heart-coap` |
| Zig | small and embeddable; C ABI | Reaches C and C++ hosts; a young ecosystem (no mature BDD runner yet) |
| R | statisticians and analysts; resources read as data frames | Mostly reading (L1 read side); could also call the Python client through reticulate |
| Swift | iOS and macOS apps | A phone app as a HEART client |
| PHP, Ruby | still large on the web; Rails has a hypermedia culture | Lower priority |

## Java: is it needed?

- Not as a separate client. Java already runs on the JVM with ARTIE, and calling Kotlin from Java works.
- What a Java shop needs instead is a check that ARTIE is pleasant from Java:
  - a small Java example and test against heart-core and artie;
  - Kotlin-only parts kept out of the public API, or given `@JvmStatic`/`@JvmOverloads`.
- A second JVM client would prove less than any non-JVM one.
- The same reasoning applies to .NET: one library proves the point.

## .NET: F# first, C# calls it

- **Overtaken:** heart-cs was built in C# directly, with its libraries behind core ports (HTTP in `HeartCs.Http`, an architecture test keeping them out of `HeartCs.Core`). The F# idea below stays only as an option.
- F# suits the media type: an act, a refusal (`412`, `409`, `405`) or a rung is a discriminated union, matched exhaustively.
- Raw F# reads awkwardly from C#: unions, `option`, curried functions and `Async`.
- So the library has a thin facade for C#: classes and methods, `Task`, nullable results and named refusals.
- The C# conformance runs go through that facade, so it is tested, not assumed.

## The model parts stay in ARTIE

- heart-ts has no model, and none of these clients needs one.
- ARTIE's model work happens once: reading a goal, reasoning, capturing, and generating the kept script. Its output is YAML that any runner replays.
- So "think once, replay anywhere" needs the runner in every language, not the reasoner.
- Porting the reasoner to every language would copy the costly part and prove nothing new.
- When a replay stops because a pin changed, the runner hands the goal back to ARTIE (its CLI, its container or heart-mcp) to think again. It does not repair the run itself.
- Optional later: a capture mode in other runners, so their runs can feed `artie generate`.

## Suggested order

1. The shared replay features, run against the Kotlin runner.
2. heart-ts L2.
3. Python L1 and L2, with its bdd-gates stack.
4. Go.
5. F#, with its C# facade.
6. Rust.
7. The rest on demand.

## Python: heart-py, built like heart-cs

The author settled the open question on 2026-11-23: a Python client is wanted, with its own bdd-gates stack. Python is the subject of this work, so the no-crafted-Python rule does not apply to it.

### The bdd-gates stack: `stacks/python-uv/`

The template's five questions, answered:

1. **Where things live.**
   - Production: `packages/*/src/*.py`.
   - Excluded: `tests/`, step definitions, `.venv`, build output.
   - Features: `tests/features/*.feature`, copied word for word from artie.
2. **BDD runner: pytest-bdd.**
   - A scenario with a missing step is an error.
   - It brings pytest's fixtures, plugins and coverage.
   - behave (`--strict`) is the alternative.
   - Gap to check: neither is known to write Cucumber's ndjson message stream. Either set `scenarioResults: false`, or write a small converter.
3. **Logging: the wrapper**, as in heart-cs.
   - `bddCommand`: `uv run pytest "$@"`.
   - `bddRawCommandRegex` blocks bare `pytest` and `python -m pytest`.
   - Native logging is possible: pytest's `pytest_sessionfinish` hook runs on failure too. It would mean protecting `conftest.py`, so the wrapper is simpler.
4. **Complexity, at defaults.**
   - Ruff `C901` and `PLR09xx`.
   - mypy `--strict`.
   - jscpd for duplication.
   - `pytest-cov --cov-fail-under=80`.
5. **Architecture as a failing test.**
   - Structural first: a uv workspace with core and adapter packages.
   - Then import-linter: core never imports `httpx`, `requests` or `jsonschema`.
   - Protected files: `ruff.toml`, `.importlinter`, `.coveragerc`. They stay out of `pyproject.toml`, so protecting them never blocks adding dependencies.

### The client: the same shape as heart-cs

| heart-cs | heart-py |
|---|---|
| `HeartCs.Core`, no `System.Net.Http` | `heart-py-core`, standard library only |
| `HeartCs.Http` adapter | `heart-py-httpx` adapter |
| `ITransport`, `ILayerCache` ports | `Transport`, `LayerCache` Protocols |
| `HeartClient(transport, auth, rung, dialect, cache)` | the same, async: `enter`, `refresh`, `choices`, `follow(rel, title)`, `act(name, body)` |
| `Representation`, `Rung`, `Dialect` | a frozen dataclass and enums |
| `RefusedException(Kind, Status)` | `RefusedError(kind, status)` |
| Architecture tests | import-linter contracts |
| `Serial.cs`, `Sims.cs` | pytest runs one test at a time by default; a sims fixture on `HEART_SIMS_URL` and `X-Sim-Host` |
| JsonSchema.Net later, behind a port | `jsonschema` later, in its own adapter package |

- **Slices:** in the same order as heart-cs, with the same shared features, through headless runs.
- **Report:** pytest writes JUnit XML, not TRX. A JUnit reader in `docs/reports/assets/build-conformance-report.mjs` gives heart-py the same conformance page.
- **L2:** heart-py could be the first replay runner outside Kotlin, ahead of heart-ts.

### First step

The `python-uv` stack folder in bdd-gates-skill, passing `scripts/selftest.sh`. Then the heart-py repo scaffold and install.

## Open

- heart-py:
  - pytest-bdd or behave? pytest-bdd is the recommendation.
  - Async only, or a sync facade as well for data and notebook users?
  - Repo `heart-py`, import name `heart_py`?
- R: a client of its own, or the Python client through reticulate.
- Zig waits until a BDD runner exists, or uses a cucumber wire protocol.
