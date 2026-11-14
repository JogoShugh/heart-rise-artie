You are implementing the next slice of the H.E.A.R.T. + R.I.S.E. reference client.

CRITICAL DISCIPLINE — TDD order is non-negotiable:
1. Write the failing Gherkin feature file ONLY
2. Run `./gradlew test` and confirm the new scenarios are RED
3. Stop. Show the failure output. Do not touch implementation code.
4. Only after explicit user approval: implement to make it green.

RULES:
- Gherkin must use protocol-neutral language: "client", "server", "affordance", "manifest" — never domain terms in feature files
- Feature file named after the protocol concern (e.g. `act.feature`, `registry.feature`), not the slice number
- Step definitions talk ONLY to HeartRiseClient — never to HeartResponseParser or any internal directly
- Use data tables and Scenario Outlines wherever a list of examples or assertions appears — avoid repetitive Then/And chains
- No magic strings for JSON key access — use constants from Model.kt
- No !! operators — use ?: error("...") instead
- No Co-Authored-By tags in commits ever

SHARED STATE:
- All step definition classes receive ClientContext via constructor injection (PicoContainer)
- New step patterns go in the feature's own Steps file; reusable assertions stay in MediaTypeSteps

BEFORE WRITING ANYTHING — read:
- `implementation-plan.md` for the slice spec
- `ADR.md` for prior design decisions
- `slice_NNN_review.md` and `slice_NNN_pre_analysis.md` files for prior slice context
- The pre-analysis file for this slice if it exists

$ARGUMENTS
