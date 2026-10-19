# TODO

## Generated scripts and lock files: not yet proven (spec/PLAN-SCRIPT-GENERATOR.md)

What artie generate and the guards do that no live run has shown yet, as of 2026-10-18 (commit 42ac60d).

- [ ] **Strict set check, live.** Both live GitHub captures were read at L0, where a resource's forms come inline
      and there are no affordance sets, so their lock files pin no sets. Run github-task-reasoned.kts with
      `--rung L2`, generate from that capture, and run the generated script with `--guard strict`.
- [ ] **A changed form, live.** The guard refusing a changed form, and lenient going past a changed set with its
      warning, are shown only against the small GitHub in process (script-replay.feature, script-guard.feature).
      Live: edit a copy of the gateway's GitHub connector (a field on github:close), restart the gateway on it,
      and run a generated script lenient and strict against the scratch repository.
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
