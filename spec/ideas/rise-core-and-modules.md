# Idea: RISE core, with optional modules

**Status:** an exploration, 2026-11-08, for the author to prioritize. Nothing here is built, and SPEC is unchanged. It
builds on `rise-deep-dive.md` §6 (the prototype) and §7 (the dynamic webhook, narrowed), `rise-awakening.md`, and the
reply-envelope mockup in `docs/reports/reply-envelope/`.

## The problem

RISE keeps gaining parts: CloudEvents, OpenTelemetry, signatures, AMQP, fan-out. If each lands in the core, every
implementer carries all of them, and RISE turns into a pile of vendor-shaped requirements. The core should hold only
what HTTP, JSON and the REST constraints need. Everything else is a module: optional, additive, built on a standard or
a de facto convention, and tested on its own.

## Shape

- **Core:** HTTP, JSON, RFC 9110 and RFC 3339. Nothing else is required.
- **Modules:** `ce`, `otel`, `signed`, `amqp`. Each is optional.
- **HEART layers are unchanged.** Layers 0 to 3 describe representations. Modules are a separate axis, covering
  annotation and transport. They are not new layers and get no layer numbers.

## The core

- **Reply resource:** `awaiting`, then `answered` or `expired`.
- **Deliver:** POST with `If-Match`. The body is the caller's template, with the responder's values filled in.
- **Address as capability:** unguessable, single-use, https, with an `expires` time.
- **Stamp members in the body:** `id`, `time`, `source` and `subject`, under plain names. They are chosen so the `ce`
  module maps each one to a CloudEvents attribute, one to one. Nothing is renamed later.
- **Refusals:** 409 when the reply is not offered (already answered or expired), 412 when the validator is stale, 422
  for invalid input.
- **Repeats:** a sender whose answer was lost reads the reply again, compares `id`, and stops (§7).

## Modules

| Module | Adds | Depends on | Built on |
|---|---|---|---|
| `ce` | `ce-*` headers in binary mode. `type` names a state change (`reply.answered`), never an operation. Structured mode optional. | core | CloudEvents 1.0 (CNCF; de facto) |
| `otel` | `traceparent` and `tracestate`. Span names and attributes per the OpenTelemetry semantic conventions. | none | W3C Trace Context (Recommendation); OpenTelemetry conventions (de facto) |
| `signed` | `Signature-Input`, `Signature`, `Content-Digest`. The key is found through `hr:keys`. | none | RFC 9421; RFC 9530 (both codified) |
| `amqp` | heart-method, heart-target and heart-status headers; `reply_to` and `correlation_id`. | core | AMQP 1.0 (OASIS); AMQP 0-9-1 (RabbitMQ, de facto) |

CloudEvents over AMQP is `ce` and `amqp` together. The `ce` attributes go in application-properties under AMQP 1.0,
and in headers under 0-9-1.

## Dovetail rules

1. **Additive only.** A module adds headers, members or relations. It never renames or repurposes a core name.
2. **Opt in per reply.** The caller lists its modules in an `hr:modules` member when it creates the reply. The responder
   applies all of them, or refuses with 422 `modules-unsupported`. No silent partial support.
3. **Dependencies are declared.** The responder checks them at create, using the table above.
4. **The ETag covers the bytes.** Module members are part of the representation, so they change the ETag, as SPEC §6.1
   already requires. Caches stay honest.
5. **`signed` covers every header a module adds.** Each module lists its headers in its spec, and the signed module's
   covered set includes them. Otherwise the stamp can be forged.
6. **Conformance is layered.** The core test suite passes with no modules. Each module has its own tests. A matrix
   covers the combinations that are claimed.

## Fielding and vendor check

- **Core alone** is REST as it stands today. Nothing in it touches a constraint.
- **`ce`** adds self-descriptive messages. Its `type` names a state change, so the operation stays on the resource. This
  is the rule from the Awakening work: the operation is on the resource, the event is a hint, and the resource is the
  truth.
- **`otel`** does not change REST semantics.
- **`signed`** adds headers only.
- **`amqp`** is a binding. It carries the same HTTP semantics in headers, as `amqp-binding-vision.md` already describes.
- **Vendor exposure:** the core names no product. The modules name CNCF projects (CloudEvents, OpenTelemetry), and no
  module is required.

## Decisions for the author

1. **Is `signed` in the core?** *Recommended:* no, it is optional. It is required for any deployment that carries a
   `result` over an open network. An address that is only secret is weak protection for money or personal data.
2. **How does the caller opt in?** *Recommended:* the `hr:modules` member at create. It sits in the representation, so
   the inbox can advertise the modules it supports through a link. A `Prefer` header would be hidden from hypermedia.
3. **What do we call them?** *Recommended:* "core" and "modules". SPEC already uses "extension" for relations and
   profile URIs, so "extension" would be ambiguous here.
4. **Where do module specs live?** *Recommended:* one spec per module under `spec/`, written when the module is first
   needed, not before.

## Build order

1. **Core**, as slice 1 of `client/artie-rise` (`rise-deep-dive.md` §6). No signatures in slice 1, as §6 already says.
2. **`amqp`**, as slice 2 (§6).
3. **`ce`**, then **`otel`**, then **`signed`**. Each one only when a consumer needs it.
