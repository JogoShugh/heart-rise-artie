# Implementations beyond ARTIE

- `heart-ts/`: a second HEART client, in TypeScript, written from
  `spec/SPEC.md`, `spec/HAL-SCHEMA-FORMS-HR.md` and the Internet-Draft
  (`standards/`), not ported from ARTIE. It can enter at a
  bookmark, read state and forms at L0, L1 or L2 with every shared layer checked
  against its link's integrity, act with If-Match, tell the refusals apart,
  follow links by relation and title, speak HAL-FORMS at L0 as well as HAL
  Schema Forms, and offer a form's choices from state
  (`hr:options`), sending no value outside them.
- `conformance/`: what any HEART client must do, as Cucumber scenarios. Each
  client brings its own steps for the same feature files: heart-ts's are
  `conformance/features/support/`, ARTIE's are `client/artie/src/test/kotlin/conformance/`
  (`./gradlew :artie:conformanceTest`, part of ARTIE's `check`). The scenarios
  run against two kinds of service:

  | Service | What it is | Features |
  |---|---|---|
  | The garden | The reference service, replayed from `conformance/fixtures/garden/` offline; live with `GARDEN_URL` (`run.sh`); re-recorded with `record.sh` when representations change | `client`, `dialects`, and part of `rungs` and `hal-forms` |
  | The simulated services | `heart/heart-sims`: a build service (a narrow Jenkins) and a deploy service, event-sourced HEART services, and the GitHub gateway in front of a small GitHub, behind a fault layer for what a broken service sends (spec/PLAN-CONFORMANCE-SERVICES.md). ARTIE runs them in memory; heart-ts runs them as one process | `schema`, `choices`, `rungs`, `hal-forms`, `refusals`, `reads`, `links`, `acts`, `templates`, `options` |

  In `heart-ts`, `npm run mutation` runs Stryker. It edits `src/` in place
  while it runs, so run it where nobody else is working.

Both are by the same author as ARTIE, so they show the specification is
enough to write a client from; they are not independent in the standards
sense. An independent implementation needs someone else, and
`conformance/client.feature` is what they would run.
