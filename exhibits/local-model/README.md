# Local model spike

Phase 0 of `spec/PLAN-LOCAL-MODEL.md`, run 2026-10-30. Three models on this laptop were asked the reasoner's and
the planner's prompts, in exactly the shape `ClaudeReasoner` and `ClaudePlanner` send them, through Ollama's
Anthropic-compatible `/v1/messages`. Nothing in artie was changed or run: the point was to learn whether a local
model answers the tool at all before touching code. The answers are in `captures.yaml`; what they taught is in the
plan.

## What is here

- `reasoner-system.txt`, `reasoner-tool.json`: the reasoner's system text and its `select` tool, copied from
  `ClaudeReasoner.kt`.
- `planner-system.txt`, `planner-tool.json`: the planner's, from `ClaudePlanner.kt`, with every move allowed.
- `turn-repo.txt`, `turn-merge.txt`, `turn-comment.txt`: three real reasoner turns taken from the goal run's trace
  (`runs/first.yaml` of the README quick start): the first step at the bookmark, the merge of pull request 1781,
  and the reasoned comment on the draft 1782.
- `turn-plan.txt`: one planner turn, built by hand in the planner's turn shape from the goal file and the pull
  request held at the merge step. The trace keeps the reasoner's turns, not the planner's, so this one is a
  reconstruction.
- `mk.sh MODEL`: writes `req-*.json`, one request per turn, for that model.
- `ask.sh MODEL KIND`: posts `req-KIND.json` to `http://localhost:11434/v1/messages`, keeps the answer, prints
  the gist with the wall time.
- `wait-run.sh MODEL`: waits for `ollama list` to show the model, then runs all four.
- `captures.yaml`: the requests, with the model name blanked, and every answer with its status and wall time.

## Running it again

Ollama's Homebrew formula does not build on macOS 14 (it needs `python@3.15` from source, which fails on the
Command Line Tools here), so the CLI tarball was used, with no application and no dialog:

```
mkdir -p ~/.local/ollama && cd ~/.local/ollama
curl -fL -o ollama-darwin.tgz https://github.com/ollama/ollama/releases/latest/download/ollama-darwin.tgz
tar xzf ollama-darwin.tgz
nohup ./ollama serve > serve.log 2>&1 &
./ollama pull qwen3:30b-a3b-instruct-2507-q4_K_M
```

Then, from this folder:

```
./wait-run.sh qwen3:30b-a3b-instruct-2507-q4_K_M
```

The header `x-api-key: none` is sent because the reasoner always sends one; Ollama ignores it.
