# Idea: five ways to run one goal

**Status:** idea only. Raised by the author on 2026-10-26. Extends the native trial (spec/PLAN-NATIVE-VS-HEART-TRIAL.md,
two arms, the triage task) to five arms on the merge watch, a long-running goal where the other R's
(spec/ideas/the-other-rs.md) and papering over (spec/ideas/papering-over-gaps.md) show most.

## The question

Given the same plain-text goal (`exhibits/merge-watch/merge.goal.md`, front matter and Markdown), the same model,
the same scratch repository and the same stream of pull requests, what does Claude do with each of five kinds of
access, and how does each measure on the R's?

## The arms

1. **`gh` installed.** Claude Code with a shell and the GitHub CLI. Someone else's knowledge of GitHub, written down.
2. **The REST API only.** No `gh`. Claude reaches `https://api.github.com` through one HTTP tool, the credential
   added by the harness. The `start:` address in the goal is replaced by this one.
3. **The gateway, no help.** `start:` is the HEART gateway. Claude has an HTTP tool and nothing that explains HEART:
   no skill, no documentation. Whatever it does comes from reading the answers.
4. **The gateway, with a skill.** The same, plus a skill that gives the HEART client rules: start at the entry,
   follow only links and forms the server gives, send `If-Match`, read `hr:unavailable`, never build an address.
   It behaves like ARTIE, but by instruction rather than code.
5. **ARTIE as a tool.** Claude drives, and the `artie` binary is its tool: plan the goal, run it, keep the script.
   This is the way the merge watch has run so far.

## Kept the same

- Model, at its defaults, and the wording that hands it the goal.
- A fresh session per run, headless (`claude -p`), on this machine, with no dialogs.
- The scratch repository JogoShugh/heart-github-test only, with a token limited to it.
- One hour per run, or until the watch's issue is closed.
- The same scripted stream, from `exhibits/dependabot-sim/` (`bump.kts`, `person.kts`), with its traps:
  - a draft that GitHub calls mergeable and clean;
  - a pull request with conflicts;
  - a held pull request that keeps changing;
  - one that becomes mergeable only after a while;
  - a change between reading and merging, which should be refused rather than merged;
  - a rule changed halfway through, to show drift;
  - the issue closed at the end, to stop it.
- Each arm run three times, to see repeatability.
- The harness in shell and Kotlin `.kts`, never Python.

## Measured

- **Right behavior:** merged the right set; merged nothing wrong; left held ones alone; told each author once.
- **Repeatability:** the same outcome across the three runs.
- **Cost:** model calls, tokens in and out, wall time.
- **Resilience:** what it did with the failed look, the stale merge and the rule change.
- **Recoverability:** killed at minute 30 and restarted; did it pick up where it left off?
- **What it leaves behind:** a script someone can read and review? Is it pinned? A trace of every act?
- **Stopping:** did it notice that the issue was closed?

## Evidence

Every arm's traffic is recorded the same way, so they are scored on the same evidence. Arms 3 to 5 are already
recorded by the gateway. Arms 1 and 2 need GitHub traffic recorded too, for example through the gateway as a plain
pass-through (spec/ideas/gateway-pass-through.md), so that `gh` and curl leave the same capture. Transcripts are
kept as YAML.

## Expected (to be checked, not assumed)

- Arms 1 and 2 write a polling script from memory, or poll turn by turn and run out of session. The draft trap is
  the likely failure.
- Arm 3 is the open question: whether self-describing answers are enough without being taught.
- Arm 4 behaves well but reasons at every look, so its cost and variation are high.
- Arm 5 reasons a few times, then replays.

## Order

Build the harness and the recording once. Run arm 3 and arm 1 first, the sharpest contrast. Then 2, 4 and 5.
Report under `docs/reports/five-ways/`.
