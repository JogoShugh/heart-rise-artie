# Plan: a goal run on a local model

Status: plan only, 2026-10-30. No code, no spec text. Follows spec/ideas/goal-once-stop-and-model-settings.md,
idea 2 (the model's settings as a measured choice), and makes the model's address one of those settings.

## The idea

Every goal run so far reasoned with Claude over the Anthropic API. The same run should be able to reason with a
model on this laptop, with nothing else changed: the same goal file, the same gateway, the same trace, the same
`artie generate` after it. Then the question "what does the model buy, against what the structure buys" can be
asked of a free, slow, small model as well as of a paid, fast, large one, and the answer measured the same way
the bench measured the four forms.

## What is true now

- `ClaudeReasoner` and `ClaudePlanner` post to `https://api.anthropic.com/v1/messages`, a constant. The model is
  `claude-sonnet-5-5`, a constant. The key comes from `ModelKey.standard`: `ANTHROPIC_API_KEY`, else the
  credentials file's entry for `https://api.github.com`'s neighbour, `https://api.anthropic.com`. No key, no
  reasoner, and the run says where a key goes.
- `artie run --reasoner` accepts one value, `claude`. A goal needs it; an `act(intent = …)` needs it.
- Both prompts are one system text, one tool (`select` for the reasoner, `move` for the planner), `tool_choice`
  auto, `max_tokens` 1024, and `cache_control` on the fixed parts. A text answer with no tool call is read as a
  question for the person, never as a move or an act. The planner needs `why` and `move`; the reasoner needs
  `action`.
- `ModelUsage` counts each answer's `usage`: input, output, cache read, cache write. A missing field counts 0.
- Each `ModelCall` in the trace records the model's name, what was asked and what was answered.

## The seam

The Messages API is the seam, not Claude. Ollama (since 0.14, January 2026) and llama.cpp's server both serve an
Anthropic-compatible `/v1/messages`, tools included, on localhost. So the whole change on artie's side is three
settings: where the model is, what it is called, and whether a key is needed. Nothing about the prompts, the
tools, the trace or the generator changes. A model that answers that endpoint well is a reasoner; one that
answers it badly shows up in the trace as questions, refusals and stops, which is the measurement.

An OpenAI chat-completions reasoner (LM Studio, MLX, vLLM) is a second seam. Not now: the Messages seam covers
the two runtimes that matter on a Mac, and a second request shape is a second thing to keep right.

## The machine

Apple M3 Max, 36 GB unified memory, 642 GB free. That memory fits one model at a time up to about 20 GB of
weights with room for the KV cache of a 10,000-token prompt. Candidates, as the Ollama library names them, to
be checked against what the library lists today:

| Model | Size on disk | Why |
|---|---|---|
| `qwen3:30b-a3b` | about 19 GB | Mixture of experts: 3B active, so fast; strong tool calling |
| `gpt-oss:20b` | about 13 GB | Native tool calling and a reasoning-effort setting |
| `qwen3:14b` | about 9 GB | The small, dense fallback if the two above stumble on the tool |

A dense 32B is too slow for a 13-call run; a 70B does not fit. Install the Homebrew formula, not the Ollama
application: the formula is a command and a service, the application puts a dialog on the screen.

## What to build

### Phase 0: the runtime, by hand

1. `brew install ollama`; `brew services start ollama`. It listens on `http://localhost:11434`.
2. `ollama pull qwen3:30b-a3b` and `ollama pull gpt-oss:20b`.
3. One `curl` to `http://localhost:11434/v1/messages` with the reasoner's exact request: the `select` tool, the
   system text, one real turn taken from `runs/first.yaml`. The answer must hold a `tool_use` block whose
   `input.action` is one of the five actions. Repeat with the planner's `move` tool and a real `<goal>` turn.
   Keep both answers in `exhibits/local-model/captures.yaml`, bodies as YAML.
4. Note what the answer's `usage` carries, whether `cache_control` is ignored or refused, and whether the model
   spends its 1024 tokens thinking before it calls the tool. If it does, the spike finds the request field that
   turns thinking off or down for that runtime, and the plan's phase 1 carries it as a setting.

Nothing in the repository changes until the curl answers well. If no model answers the tool at all, the plan stops
here and says so.

### Phase 1: the settings

Feature first, red at test level, then `src/main`, then green, module by module.

- `artie-cli`: `artie run` takes `--model NAME` and `--model-url ORIGIN`. Defaults stay as they are. Both go in
  the run's record beside the usage it keeps. `run.feature` and `run-goal.feature` get the scenarios: the client is
  made with the model and address asked for; the record names them.
- `heart-script`: `ClientSettings` carries `model` and `modelUrl`.
- `artie`: `ClaudeReasoner` and `ClaudePlanner` take an endpoint. `ModelKey.standard` looks the key up by the
  model's origin, not a constant: for `https://api.anthropic.com` as today, for another origin the credentials
  file's entry for it, and for an origin with no entry, no key and no refusal. `model-key.feature` gets the
  scenarios: a local address needs no key; a key for a local address, when the file holds one, is sent as the
  file says. The header is `x-api-key` either way, which is what both runtimes read.
- `ModelUsage` already tolerates a `usage` without the cache fields.
- The names `ClaudeReasoner` and `ClaudePlanner` are wrong once they talk to anything. Renaming is refactor
  work, done in refactor mode after green, when the author switches it on.

The reasoner is still chosen as `--reasoner claude`: the value names the prompt and tool shape, which is Claude's.
Whether to rename it (`messages`) is decided with the classes.

### Phase 2: the run

The behind scene on the scratch repository, as the bench ran it, with the gateway on this machine:

```
artie run exhibits/merge-watch/merge.goal.md owner=JogoShugh repo=heart-github-test \
  --reasoner claude --model qwen3:30b-a3b --model-url http://localhost:11434 \
  --exchanges --trace runs/local.yaml
```

Then `artie generate runs/local.yaml --script exhibits/merge-watch/merge.goal.md --out merge.local.kts` and a
strict run of what it wrote. The kept script's one reasoned line is where a local model should be enough: a short
prompt, one sentence back.

Measured as the bench measured: merged or not, told once or not, steps, model calls, time to settle, and the
model's own time per call, which the bench did not need and this run does. Ten runs per model, the same two
scenes. The write-up's new row: one laptop, no key, and what that bought and cost.

What to expect: a prompt of 5,000 to 10,000 tokens per call reads at a few hundred tokens a second on this
machine, so 10 to 30 seconds of model time per call before the first token, against under 5 for Claude. Thirteen
calls is several minutes of model time on top of the wait the scene itself takes. A smaller model may answer in
words where Claude answered with the tool, which ends the run with a question; the trace shows exactly where.

### Phase 3: what it teaches

Three questions, answered by the traces rather than argued:

- Does the planner's prompt carry a small model, or does it lean on what a large one knows? A local model that
  stops, or acts on a form not offered, says the prompt leans.
- Is the kept form model-independent in practice? Two calls, one sentence: if a local model does that as well,
  the rails are the point and the model is a detail.
- What is the floor? The smallest model that finishes the behind scene ten times out of ten is a number the
  write-up can state.

## Out of scope

- An OpenAI-shaped reasoner.
- Running the model elsewhere than localhost. The address is a setting, so nothing prevents it, but the plan
  measures the laptop.
- Tuning prompts for a given model. The prompt stays one prompt; if a model needs its own, that is a finding.

## Verification

- Phase 0's captures in YAML, token-free (there is none).
- Each feature red at test level with the right module's `BDD_RUN FAIL` logged, then green; detekt clean;
  `./gradlew installDist`.
- One live goal run and one live strict run per model, traces kept under `exhibits/merge-watch/runs/`.
- Commits local, in feature order. Push only on "Push".

## Phase 0, done 2026-10-30

The runtime: Ollama 0.40.13 from the CLI tarball, since the Homebrew formula will not build on macOS 14. It
serves `/v1/messages` on Metal with 27 GiB for models and a default context of 32,768 tokens. The spike's
files and every answer are in `exhibits/local-model/`.

Three models were asked the three reasoner turns and one planner turn. What they answered:

| Model | Reasoner | Planner at 1024 | Warm call |
|---|---|---|---|
| `qwen3:30b-a3b-instruct-2507-q4_K_M` | 3 of 3 right, exact enum and names | right, with `why`, words "merge it by squashing" | 3 to 4 s |
| `qwen3:30b-a3b` | 3 of 3 right | no tool call: its thinking spends all 1024 tokens; right at 4096, without `why` | 6 to 8 s, planner 34 s |
| `gpt-oss:20b` | wrong shape: `action` holds the form's name, `affordance` the title | right, with `why` | 4 to 7 s |

So one model answers both prompts as Claude does, at the first try, with no change to either prompt: the
instruct build of Qwen3 30B-A3B, which does not think. Phase 1 and 2 use it. The thinking build is the same
model with a budget it will not stay in: the Messages API's `thinking: {type: disabled}` is not honoured
(it thinks in plain text instead), and `budget_tokens` is ignored. `gpt-oss:20b` fills the tool's optional
fields with empty strings and reads `action` as the thing to do rather than one of the five words; a run on it
would stop at its first act with "no such form", which is a finding, not a bug in artie.

Corrections and additions to the plan from the spike:

- A cold call reads the first prompt at about 150 tokens a second here: 4,800 tokens in 30 s. Warm calls on the
  same prefix hit Ollama's prompt cache, reported as `cache_read_input_tokens`, and take 3 to 8 s. So
  `ModelUsage` counts cache reads for a local model too, unchanged.
- `cache_control` is accepted and ignored; `usage` has no `cache_creation_input_tokens`, which counts 0.
- The reasoned comment is one sentence from every model. The instruct build's is two, with a "Thanks!";
  the prompt's "one sentence" rule is kept by Claude and the thinking build, not by it. Worth a row in phase 2.
- Phase 1 needs no `max_tokens` setting for the chosen model. If the thinking build is ever measured, it needs
  4096, so `--max-tokens` is cheap to add beside `--model`; not required.
- The planner's turns are not in the trace, only the reasoner's. Phase 2's traces should keep both, or the
  planner's calls can't be replayed against another model the way the reasoner's were here.

## Phase 2, first run, 2026-10-30

The goal ran through artie for real on `qwen3:30b-a3b-instruct-2507-q4_K_M` (`--model`, `--model-url`,
`--rung L2 --exchanges`), the behind scene staged as the quick start stages it: a fresh bump (1790) and a draft
(1791). It took four model calls, 4,685 input tokens, 3,837 of them cache reads, under a minute, and did the
wrong thing.

At the first step, "In {owner}/{repo}", a step with sub-steps, the planner listed the repository's issues
labelled `dependencies` instead of opening the repository, reasoning "To find issues with the 'dependencies'
label, use the issues-list-for-repo form". It then marked that step done, marked "Open an issue titled Merge
watch" done without acting, with "No issues with the 'dependencies' label exist, so there is nothing to merge
or watch", and the run stopped there with that as its reason. Nothing was merged, nothing told; the watch issue
was never opened. In the spike this model answered the merge step well; the step it fails is the one the
prompt describes at most length, "for a step with sub-steps, act only to reach the resource its sub-steps
start from". So the planner's prompt leans on what Claude knows, which is the first of phase 3's questions,
answered early.

A second finding, not about the model: `artie generate` on that two-step trace wrote a kept script whose body
is `watch(until = { that(false) }) { }`, a watch with nothing to do and no way to end, and the strict run of it
looped "return to https://localhost:8392/" until it was killed at step 129. A capture in which a "Whenever"
never fired should not generate a watch at all, or the generator should say the run never reached it, as
`--bare` already says for a goal line that never fired.

Next, in order of cost: the thinking build of the same model at 4096 tokens (which needs a `--max-tokens`
setting), then `gpt-oss:20b` for the planner only (its reasoner answers in the wrong shape), then a larger
dense model if one fits. The prompt stays one prompt.
