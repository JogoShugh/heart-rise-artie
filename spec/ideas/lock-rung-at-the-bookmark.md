# Bug: a run that acted only on the bookmark pins rung L0

**Status:** found 2026-11-03 by the bench's code-artie arm; not fixed.

## What happens

`artie generate` writes the lock file's `rung` as the highest rung any step was answered at (LockWriter). A bookmark
answers at L0 whatever is asked, so a goal run at `--rung L2` whose only act is on the bookmark (hire Samwise Gamgee,
the form at `/`) pins `rung: L0`. Its kept script, run again with `--rung L2`, stops before the first request:

    stopped before the first request: the lock file was recorded at L0, not L2

Run without `--rung`, it reads at the lock file's rung and works, which is how the bench runs kept scripts now.

## The fix, feature first

- The capture records the rung the run asked (the run document beside `model` and `usage`), and LockWriter pins
  that, falling back to the highest answered for captures made before.
- Or, in artie-cli alone: steps at the entry don't say the rung, since the bookmark answers at L0 at any rung; a run
  with no other step pins none.

A form's integrity is the same at every rung (staff:hire is `sha256-mxzDdaZ5…` at L0 and at L2), so either keeps
the guard.
