# Implementations beyond ARTIE

- `heart-ts/`: a second HEART client, in TypeScript, written from
  `spec/SPEC.md`, `spec/HAL-SCHEMA-FORMS-HR.md` and the Internet-Draft
  (`standards/`), not ported from ARTIE. About 300 lines: enter at a
  bookmark, read state and forms at L0 or L2 with every shared layer checked
  against its link's integrity, act with If-Match, tell the refusals apart,
  follow links by relation and title.
- `conformance/`: what any HEART client must do, as Cucumber scenarios
  against the live garden (`client.feature`), with heart-ts's steps.
  `conformance/run.sh` starts the garden, runs them and stops it. Another
  client brings its own steps file with the same phrases.

Both are by the same author as ARTIE, so they show the specification is
enough to write a client from; they are not independent in the standards
sense. An independent implementation needs someone else, and
`conformance/client.feature` is what they would run.
