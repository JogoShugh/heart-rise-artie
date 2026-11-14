# Idea: the other R's

**Status:** idea only. Raised by the author on 2026-10-26, after the merge watch's hour run. No code, no spec text.
Possibly a theme for a later explainer.

## The thought

Everyone talks about reasoning. Reasoning is the one R that is expensive, slow and different every time it is asked.
The others are what make a system trustworthy, and the design question is how little reasoning a system can get
away with, so that the others are strong. "Reason once, then let the other R's carry the load."

## The R's, and where the merge watch shows each

- **Repeatability.** Same goal, same state, same result. Decide once, replay: the second pull request is merged as
  the first was, not reconsidered.
- **Reproducibility.** Show afterwards exactly what happened and why: the capture, the pins, the derivations.
- **Reliability.** It works every time, unattended: 2,740 steps and 5 model calls in an hour. Fewer dice rolls,
  fewer losses.
- **Resilience.** A failed look is tried again; a wait gives up instead of hanging; a stale view gets a 412, not a
  bad merge. It comes from the server saying no clearly and the client treating no as normal.
- **Right behavior.** Only what is allowed, now. Structural in HEART: merge isn't offered on a draft, so the wrong
  move is unavailable rather than forbidden by a prompt.
- **Restraint.** The model only where judgment lives; the `reasoned` mark is restraint written into the language.
- **Readability.** A person can read the goal and the generated script; what can't be read can't be approved.
- **Reviewability.** Someone other than the author can check it before it runs, like code.
- **Recency.** The rules come from the server at the moment of acting, not from docs or training: the drift problem.
- **Refusal.** The server's right to say no, with a reason the agent can pass on.
- **Recoverability.** It stops and picks up where it left off: the watch's journal.
- **Revocability.** Pull the plug cleanly: close the issue; a changed form makes a pinned script refuse.
- **Respect.** Working alongside people: leave held things alone, say so once, don't comment twice.
- **Responsibility.** Every act traced to a goal line, a decision and an answer.
- **Rate.** Frugality: five model calls an hour, not thousands; cost and carbon.

## Mapped to Fielding

Reliability, resilience and recency come from the server being the source of truth. Right behavior and refusal
come from controls arriving with the state. Repeatability and readability come from a generic client and kept
decisions. Reasoning is the R that HEART deliberately keeps small.
