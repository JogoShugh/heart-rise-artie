# TODO

## Generated scripts and lock files: not yet proven (spec/PLAN-SCRIPT-GENERATOR.md)

What artie generate and the guards do that no live run has shown yet, as of 2026-10-18 (commit 42ac60d).

- [x] **Strict set check, live.** Done 2026-10-19 (commit a2609ff): both GitHub runs recorded again at `--rung L2`,
      their lock files pin the sets of 7 of 10 forms, and the generated scripts ran strict live (issue #117 and
      pull request #118, reasoned; #119 and #120, hybrid). The other 3 forms come from the service index, inline at
      every rung.
- [x] **A changed form, live.** Done 2026-10-19: the live gateway restarted on a copy of the GitHub connector
      whose comment act renames body to content (`rename: { content: body }`), and github-task-from-reasoned.kts
      ran against the scratch repository. Lenient and strict both stopped at the comment, "github:comment
      changed", nothing sent (issue #123, pull request #124; #125, #126). Reports:
      docs/reports/line-by-line/github-renamed-lenient/ and github-renamed-strict/.
- [x] **A changed form held by its values, live.** Done 2026-10-19: with `on-change: values` and `repin: auto`,
      the gateway on a connector copy whose close takes an optional milestone, the lenient run finished, close
      held by its values and re-pinned (issue #127, pull request #128); strict then finished on the re-pinned
      lock file (#129, #130). Reports: docs/reports/line-by-line/github-values-lenient/ and github-values-strict/.
- [ ] **Methods in the other lock files.** Only github-task-from-reasoned.pins.yaml was regenerated with each
      form's method. github-task-from-hybrid.pins.yaml has none, so with `on-change: values` every changed form is
      refused, "the lock file doesn't pin its method". Regenerate it from github-hybrid.trace.yaml (the script
      should come out the same) and check the diff adds only `method:` lines.
- [ ] **A changed set, live.** Lenient going past a changed set with its warning, and re-pinning it, is still shown
      only against the small GitHub in process (script-replay.feature). Live: change another act of the issue
      (as edit takes no body), run lenient with `repin: auto` in a trial config, then strict.
- [ ] **Waits and checks that use the script's own computed values.** Generated scripts copy until(…) and
      check(…) from the source script's lines. A check that reads a variable the source computed, one the
      generator doesn't declare, would not compile. Make the generator notice it: declare it, or mark the line.
- [ ] **Phone layout of the line-by-line reports.** Headless Chrome won't narrow its window below its minimum
      width, so the 400px check of the github-*-generated and github-*-source reports proves nothing. Check at
      phone width another way (device emulation, or a phone).
- [ ] **Other domains and bindings.** Generation is proven live only on the GitHub task (open an issue, branch,
      commit, pull request, merge, comment, close). Not yet: the garden (HTTP, CoAP, coaps), a follow of a templated
      link, and values read from an embedded resource (`/_embedded/…`), which the generator skips today.
- [ ] **Reason steps kept, live.** `--keep-reason` is shown only against the scripted service; run a generated
      script that keeps one, with `--reasoner claude`, against the scratch repository.
- [ ] **Captures older than the paraphrase rule.** An older capture marks a paraphrase as the reasoner's own
      choice, so the generator marks it for review. Regenerate the older reports' scripts from fresh captures, or
      leave them be and say so where they are shown.
