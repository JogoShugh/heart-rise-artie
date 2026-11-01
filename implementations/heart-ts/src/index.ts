// heart-ts: a second HEART client, written from the specification
// (spec/SPEC.md, spec/HAL-SCHEMA-FORMS-HR.md) and the Internet-Draft
// (standards/draft-gough-hal-schema-forms-heart-00.md), not ported from ARTIE.
//
// It does what a HEART client must, and no more: enter at a bookmark; read a
// resource's state and its legal forms at L0 or L2, checking every shared layer
// against the integrity value of the link that led to it; act with If-Match;
// tell the refusals apart; and follow links by relation and title. It speaks both
// dialects: HAL Schema Forms, and HAL-FORMS at L0.
export { HeartClient, Options } from "./client";
export { Choice } from "./choices";
export * from "./errors";
export { Json } from "./json";
export { SchemaFailure } from "./validation";
export { Dialect, Embedded, Form, Link, PROFILE, Representation, Rung } from "./wire";
