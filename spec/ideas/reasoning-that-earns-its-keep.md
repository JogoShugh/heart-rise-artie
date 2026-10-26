# Idea: reasoning that earns its keep in the merge watch

**Status:** idea only. Raised by the author on 2026-10-26, after "The Other R's". No code, no spec text.

## What is true now

The merge watch's one `reasoned` line, "tell its author in one friendly sentence why it wasn't merged and what to do
next", gets only the server's `hr:unavailable` reason, put into the intent (`exhibits/merge-watch/merge.kts`, line 26).
It reads no comments, reviews, check logs or other pull request content.

In live run 2, six comments were written from just two reasons:

- "A draft can't be merged; mark it ready for review first."
- "The branch has conflicts with its base; resolve them first."

A fixed message per reason would do the same job, deterministically and with no model calls. So "every case is
different", as "The Other R's" puts it, overstates this example. Here the model only rewords what the server already
said.

## Where reasoning would earn its keep

Reasoning is needed when the message has to draw on free text that no fixed set of reasons covers:

- the author's own comments ("waiting on an upstream fix");
- a reviewer's requested changes;
- a failing check's log, such as which test broke and why.

Then the one sentence has to read and summarize what people and tools wrote, and nothing deterministic does that.

## Open

- Which sources the reasoned line reads (comments, reviews, check runs), and how the goal names them.
- A deterministic default: a fixed message per `hr:unavailable` reason, with reasoning only when there is free text to
  read.
- Correct "The Other R's" (the "Where reasoning earns its keep" section) once this is decided: either say the line is
  optional here, or use the realistic case, marked as not yet built.
