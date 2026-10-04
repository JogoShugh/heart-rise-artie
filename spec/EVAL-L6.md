# L6 evaluation: how well does a model choose?

Status: **draft approach, not built.** Written 2026-10-04 to agree the design
before any code. The open questions at the end need answers first.

## 1. Why this exists

ARTIE's L6 Reason step (SPEC §6.8, §6.10.7) is the only place where
inference happens. A model reads the user's intent and the current manifest,
then returns a selection: an affordance name and field values, or a question
for the user.

The scripted scenarios in `client/src/test/resources/features/reason-l6.feature`
already prove the **plumbing**:

- The reasoner never sees a URI or an HTTP method.
- A selection that names an absent affordance, breaks the schema, or adds
  undeclared fields is rejected before any request is sent.
- A question for the user sends nothing.

Those scenarios use a scripted reasoner, so they never execute inference.
They show that a bad choice cannot reach the server. They say nothing about
how often a real model makes a good choice. Answering that needs real
inference, run many times and scored. That is this eval.

| | Scripted scenarios (built) | Eval (this plan) |
|---|---|---|
| Question | Does the plumbing hold whatever the model says? | How good is this model at choosing? |
| Inference | None (a fixed answer) | A real model, every case |
| Result | Pass or fail, the same every run | A score, which varies between runs |
| Where it runs | The bdd-gates, on every edit | On demand; never a gate |

## 2. What it measures

These map to SPEC §10:

- **Selection accuracy.** Did the model pick the right affordance, with the
  right field values?
- **Precondition sensitivity (§10.2), the primary metric.** When the user asks
  for something that is not legal in the current state, does the model ask
  instead of picking a different action that is offered?
- **Clarification judgment.** Does it ask when a required value is missing or
  the intent is ambiguous, and not ask when the intent is clear?
- **Structural hallucination (§10.3).** How often does it name an affordance
  that is not in the manifest, or break the schema? The agent blocks these,
  and the eval counts them.
- **Domain agnosticism (§10.6).** Does accuracy hold across unrelated domains,
  and when action names are swapped for synonyms?
- **Cost (§10.1).** Latency, input and output tokens, and for Claude, cache-read
  and cache-write tokens reported separately.

## 3. Domains: a few, in real words

The domains are recognizable, so the model reasons over meaningful vocabulary
and not nonsense. Each is a small, hand-written state machine: 3 to 5 states,
the actions legal in each state, and a JSON Schema per action with real
constraints.

| Domain | States (example) | Actions (example) | Notable fields and limits |
|---|---|---|---|
| Garden beds (exists) | EMPTY, PLANTED, READY_TO_HARVEST | sowSeed, waterCell, harvest | milliliters 1–1000, depthCm 1–10 |
| Library loan | AVAILABLE, ON_LOAN, OVERDUE, RESERVED | borrow, return, renew, reserve | loanDays 7–28, renewals max 2 |
| Coffee order | OPEN, PAID, READY, CANCELLED | addItem, pay, cancel, refund | size enum, quantity 1–10, tip ≥ 0 |
| Support ticket | NEW, ASSIGNED, RESOLVED, CLOSED | assign, comment, resolve, reopen | priority enum, assignee string |
| Hotel booking | HELD, CONFIRMED, CHECKED_IN, CHECKED_OUT | confirm, checkIn, checkOut, cancel | nights 1–30, guests 1–6 |

Three to five domains are enough. The aim is a test that is varied and
readable, not a large one.

### What "randomized" means here

The vocabulary stays fixed. Variation happens within each domain, from a
fixed random seed, so every run can be reproduced:

- **State and identity.** A random current state, and random record ids and
  field values in Layer 1.
- **Order.** Affordances listed in shuffled order.
- **Synonym renames.** The same domain with renamed actions, for example
  `waterCell` → `irrigate` and `borrow` → `checkOut`. The answer key is
  renamed to match. If accuracy drops under a rename, the model is matching
  names it has memorized instead of reading the manifest.

## 4. Simulated servers

A small generator turns each domain definition into HAL Schema Forms
responses, at L0 or L2, served in-process through a scripted transport, just
as the existing client scenarios do. There is no HTTP and no Docker, so runs
are fast and repeatable. The garden domain can also run against the real
server-spring, which checks the simulator against the real thing.

The generator produces responses only. It never sees the test intents or
their answers.

## 5. Test cases

Each case is a domain, a state, the user's intent in plain English, and the
expected outcome. Five kinds:

| Kind | Example | Expected |
|---|---|---|
| Clear match | "water bed 2 with 500 ml" (PLANTED) | `waterCell`, milliliters 500 |
| Not legal in this state | "return the book" (AVAILABLE) | a question, not another action |
| Missing value | "water bed 2" | a question about how much |
| Out of bounds | "water it with 5 litres" (max 1000 ml) | a question; never clip or invent a value |
| Near neighbours | "keep the book another week" (ON_LOAN) | `renew`, not `reserve` |

The target is about 15 cases per domain, 60 to 75 in total. Claude drafts the
intents and the author reviews them. Intents and answer keys live in their own
files, separate from the domain definitions, so the generator cannot leak an
answer.

A case may accept more than one right answer (for example "any question" or
"waterCell with 450–550 ml"). The answer key states this explicitly; the
scorer never guesses.

## 6. Scoring and reporting

For each case and model, the eval records:

- whether the action was right;
- whether the fields were right (exact, or inside the key's stated range);
- whether it asked when it should have, and did not ask when it should not
  have;
- whether it named an affordance not on offer, or broke the schema (counted
  as structural hallucination);
- latency and tokens.

Each case runs 3 to 5 times, because model output varies between runs, even
at temperature 0 on some backends. The report shows rates with their spread,
not single outcomes.

The output is one HTML report per run. It has a score table by domain and by
case kind, a comparison table when several models run, and the full prompt
and answer for every failure.

## 7. Trusting the harness before trusting the numbers

The harness is built test-first under bdd-gates, like everything else. Before
any model number is believed, scripted reasoners must score as predicted:

- A **perfect** reasoner, which reads the answer key, scores 100%.
- An **always-ask** reasoner scores exactly the share of cases whose answer is
  a question.
- An **always-first-offer** reasoner scores what the key predicts.

If these do not come out exactly, the scorer is wrong, and model results mean
nothing.

## 8. Models: hosted and local

The `Reasoner` interface is already the plug-in point. Two adapters cover
almost everything:

| Adapter | Covers | Needs |
|---|---|---|
| `ClaudeReasoner` (built) | Claude models | `ANTHROPIC_API_KEY` |
| OpenAI-compatible (to build) | Ollama, LM Studio, llama.cpp server, vLLM (all local); DeepSeek API; OpenRouter | a base URL; an API key for hosted ones |

**Tool calling.** Where a model supports it (for example Qwen 2.5, Llama 3.1,
the DeepSeek API), the adapter forces the one `select` tool, as the Claude
adapter does. Where a model does not (for example DeepSeek-R1 distills), it
falls back to structured output: Ollama's `format` parameter with the JSON
Schema of a selection. The answer is parsed into the same `Selection` type
either way.

**Prompt layout.** This is the same for every model: a fixed instruction
prefix, then the state, the offers, and the intent last (§6.8). Cache-token
figures are reported only where the provider returns them.

### Running locally on the MacBook

1. **Ollama, headless.** Install with `brew install ollama` and run
   `ollama serve` in the background. Do not use the Ollama.app, which opens
   windows; the author works remotely.
2. **Models.** Each 7–8B model is about 5 GB to download:
   - `qwen2.5:7b`: good tool calling, a strong small baseline;
   - `llama3.1:8b`: tool calling;
   - `deepseek-r1:8b`: a reasoning model, run through the JSON fallback.
3. **Memory.** 16 GB of RAM runs 7–8B models comfortably. 14B needs about
   24 GB; 32B needs about 48 GB.
4. **Time and power.** On Apple Silicon, an 8B model takes roughly 1–3 s per
   case. 75 cases × 3 runs is about 5–10 minutes per model. This keeps the
   GPU busy, so run it plugged in.
5. **Invocation.** One Gradle task outside the gates, for example:
   `./gradlew eval -Pmodel=ollama:qwen2.5:7b` or `./gradlew eval -Pmodel=claude`.
   It writes the report and never blocks a commit.

## 9. Where it lives

- A separate Gradle source set in `client/` (for example `src/eval/`), so eval
  code and data never enter the gated `check`.
- Domain definitions, intents and answer keys as YAML under `src/eval/resources/`.
- Generator, adapters and scorer as Kotlin under `src/eval/kotlin/`. The
  scorer's own scenarios (section 7) run in the normal gated suite.

## 10. Build order

1. Domain format and generator, with the garden domain reproducing what
   server-spring serves.
2. Answer-key format and scorer, with the section 7 scripted reasoners as
   its gated tests.
3. The remaining domains and about 60–75 reviewed intents.
4. OpenAI-compatible adapter: Ollama first, then the DeepSeek API and
   OpenRouter.
5. Report, a first local run, then a Claude run when a key is available.

## 11. Open questions for the author

1. **Domains.** The five above, or others?
2. **Hardware.** The MacBook's chip and RAM, to choose model sizes.
3. **Local install.** Is it OK to install Ollama with Homebrew (headless,
   no app) and download about 15 GB of models?
4. **Hosted models and keys.** Which are in scope: Anthropic, DeepSeek API,
   OpenRouter?
5. **Intent review.** Should the author review every intent, or a sample?
