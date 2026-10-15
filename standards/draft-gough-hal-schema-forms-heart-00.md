---
title: "HEART: A HAL Schema Forms Profile for State-Scoped Affordances"
abbrev: "HEART Profile"
docname: draft-gough-hal-schema-forms-heart-00
category: info
submissiontype: independent
ipr: trust200902
area: ART
workgroup: Independent Submission
keyword:
  - hypermedia
  - HAL
  - forms
  - REST
  - agents
stand_alone: yes
pi: [toc, sortrefs, symrefs]
author:
  -
    fullname: Josh Gough
    email: jsgough@gmail.com

normative:
  RFC2119:
  RFC8174:
  RFC6570:
  RFC6901:
  RFC7240:
  RFC7284:
  RFC8288:
  RFC9110:
  RFC9111:
  RFC9457:
  RFC9530:
  SRI:
    title: "Subresource Integrity"
    target: https://www.w3.org/TR/SRI/
    author:
      - org: W3C
    date: 2016
  JSON-SCHEMA:
    title: "JSON Schema: A Media Type for Describing JSON Documents"
    target: https://json-schema.org/draft/2020-12/json-schema-core
    author:
      - name: Austin Wright
      - name: Henry Andrews
      - name: Ben Hutton
      - name: Greg Dennis
    date: 2022
  HAL:
    title: "JSON Hypertext Application Language"
    target: https://datatracker.ietf.org/doc/html/draft-kelly-json-hal
    author:
      - name: Mike Kelly
    date: 2016
  HAL-SCHEMA-FORMS:
    title: "HAL Schema Forms"
    target: https://github.com/jbadeau/hal-schema-forms
    author:
      - name: Jeremy Badeau

informative:
  RFC7252:
  RFC8631:
  RFC9147:
  FIELDING:
    title: "Architectural Styles and the Design of Network-based Software Architectures"
    target: https://ics.uci.edu/~fielding/pubs/dissertation/top.htm
    author:
      - name: Roy T. Fielding
    date: 2000
  ALPS:
    title: "Application-Level Profile Semantics (ALPS)"
    target: https://datatracker.ietf.org/doc/html/draft-amundsen-richardson-foster-alps
    author:
      - name: Mike Amundsen
      - name: Leonard Richardson
      - name: Mark W. Foster

--- abstract

This document defines HEART, a profile of HAL Schema Forms for HTTP
resources whose legal operations depend on their current state. A HEART
representation carries the resource's state and the forms that are legal in
that state, and nothing else. Every state-changing request is conditional on
the representation the client read. The forms legal in one state are
published once, as a shared resource whose bytes the linking representation
vouches for with an integrity value, so that many resources and many clients
share one cached copy. Refusals are problem details whose types tell a stale
request, an illegal one, an invalid one and one refused by a rule apart. The
profile is written so that a client, including one whose choices are made by
a language model, needs no knowledge of a service beyond its entry URI and
this profile.

--- middle

# Introduction

A typical HTTP API description lists every operation a server could ever be
asked to perform. Which operations are legal for a given resource at a given
moment is left to prose, or to the client's own copy of the server's rules.
When the server's rules change, every client that copied them must change
too, and an operation that has become illegal is discovered by sending it and
being refused.

HEART takes the other approach, the one REST describes {{FIELDING}}: the
representation of a resource says what may be done to it now. A client binds
to operations late, at each response, rather than early, when it is built. A
form that a state does not allow is absent, and the server can say why.

HEART adds three things to HAL Schema Forms {{HAL-SCHEMA-FORMS}}, a forms
extension of HAL {{HAL}} whose form bodies are described by JSON Schema
{{JSON-SCHEMA}}:

1. Shared affordance sets. The forms legal in a state are the same for every
   resource in that state, so they are served once, at their own URI, with a
   long cache lifetime, and linked from each resource with an integrity value.
2. Conditional acts and distinct refusals. Every state-changing request
   carries If-Match, and each kind of refusal has its own problem type.
3. Self-description. Forms are named by link relation types and carry titles
   and descriptions, so a client, or the reasoner behind it, can understand
   an act it has never seen before choosing it.

## Notational Conventions

{::boilerplate bcp14-tagged}

## Terminology

Layer 1:
: A resource's state, with links. Never fresh: it is revalidated on every use.

Affordance set:
: The forms legal in one state of one resource type. Also Layer 2.

Schema:
: A JSON Schema for a form's body, when it is referenced rather than inlined.
  Also Layer 3.

Owning resource:
: The Layer 1 resource whose representation linked to, or embedded, a form.

Act:
: A request sent by submitting a form whose method is not safe.

# The Profile

The profile is identified by the URI:

    https://w3id.org/heart-rise/hal-schema-forms

A response that conforms to it names it in a `Link` header field with
relation type `profile` {{RFC7284}}, beside the parent profile
`https://w3id.org/heart-rise`. The media type is `application/hal+json`. A
client asks for it with `Accept: application/hal+json` and
`Accept-Profile: <https://w3id.org/heart-rise/hal-schema-forms>`.

A service's entry resource SHOULD link to a machine-readable description of
the service with the `service-desc` relation {{RFC8631}}; HEART does not
depend on it.

The prefix `hr:` in this document is a HAL CURIE that a server declares in
`_links.curies`, expanding to `https://w3id.org/heart-rise/rels/{rel}`.

## Rungs

A client may ask, with the `Prefer` header field {{RFC7240}}, for less to be
inlined:

- With no preference (L0), Layer 1 carries its forms and their schemas
  inline.
- With `Prefer: forms=link` (L1), Layer 1 links to its affordance set, whose
  forms carry their schemas inline.
- With `Prefer: forms=link, schema=link` (L2), the affordance set refers to
  each schema by `$ref`.

A server that honours a preference names it in `Preference-Applied`. A
response without `Preference-Applied` is at L0, whatever its body links to.

# Additions to HAL Schema Forms

## Template Pointers

A form MAY carry `hr:templatePointers`, an object whose members map URI
Template {{RFC6570}} variable names to JSON Pointers {{RFC6901}}. Each pointer
is resolved against the owning resource's JSON body. A client fills each
variable from the first of: a pointer for it; a form field of the same name;
the owning resource's top-level member of the same name. The value MUST be a
string or a number; otherwise the client MUST NOT send the request.

Template pointers let one affordance set serve every resource in a state: the
set names no instance, and each client fills the instance in from the
resource it read.

## Integrity

A link MAY carry `integrity`, and a schema reference MAY carry
`hr:integrity`, in the syntax of Subresource Integrity {{SRI}}.

A server MUST send `integrity` on a Layer 1 link to an affordance set, and
`hr:integrity` on every schema reference in an affordance set. The digest
MUST NOT be truncated.

A client fetching a target that has an integrity value MUST hash the body
exactly as received and compare it with that value before parsing it. On a
mismatch it MUST NOT parse, cache or use the body. A client MUST refuse,
before sending any request, a link to an affordance set or a schema
reference that has no integrity value.

A client MUST NOT read a digest, or any other meaning, from a URI. The
expected digest comes only from the linking representation.

A server SHOULD also send `Repr-Digest` {{RFC9530}} on affordance sets and
schemas. A client SHOULD check it, and MUST NOT require it.

## Schema by Reference

At L2, a form's `schema` is an object with a JSON Schema `$ref` to the
schema's URI and an `hr:integrity` value. The client fetches and checks the
schema, then uses it as the form's schema.

## Forms Named by Link Relation

A form's key in `_forms` is a link relation type {{RFC8288}}, written as a URI
or as a CURIE declared in `_links.curies`. A client expands a CURIE before
comparing names. The relation URI SHOULD identify a descriptor in an ALPS
document {{ALPS}} that says what the act does and when it is offered.

## Self-Description

Each form in an affordance set MUST carry a `title` and SHOULD carry a
`description`. Each schema property SHOULD carry `title`, `description` and,
where it helps, `examples`. Text in an affordance set MUST NOT include
instance identifiers, since the set is shared by every resource in the state.

# Conditional Acts

Every act MUST carry `If-Match` with the entity tag of the representation
the client read, and repeat the negotiation header fields it read with
({{RFC9110}}, Section 13.1.1). A server MUST refuse an act without
`If-Match`.

A server refuses an act with a problem details object {{RFC9457}} whose type
is one of:

| Status | Type | Meaning | Client |
|---|---|---|---|
| 428 | `https://w3id.org/heart-rise/problems/precondition-required` | No `If-Match` was sent. | Re-read, then send once more with the current validator. |
| 412 | `https://w3id.org/heart-rise/problems/stale` | The state has changed since the client read it. | Re-read and decide again. MUST NOT resend the act. |
| 409 | `https://w3id.org/heart-rise/problems/not-offered` | The act is not legal in the current state. | Re-read. MUST NOT retry. |
| 422 | `https://w3id.org/heart-rise/problems/invalid-input` | The body breaks the form's schema. | Report each violation. MUST NOT retry. |
| 422 | `https://w3id.org/heart-rise/problems/rule-refused` | The body is valid, but a rule of the resource refuses it. | Report the rule's words. MUST NOT retry. |

A `422` problem carries a `violations` member: an array of objects, each with
a JSON Pointer to the offending value and a message.

# Explaining Absence

An affordance set MAY link, with relation `hr:unavailable` and an integrity
value, to a document listing the acts absent in this state, each with its
relation, a title and a short reason. Like the set, it is a function of the
state alone and is shared. A client fetches it only when someone asks why an
act is missing, and MUST NOT act on any relation it lists.

# Credentials

A server MAY require credentials on Layer 1 resources and acts, and SHOULD
serve affordance sets, schemas and explanations to any caller, since they
are the same for every caller. A refusal is `401` with problem type
`https://w3id.org/heart-rise/problems/not-authenticated` and a
`WWW-Authenticate` challenge for each scheme accepted.

A client MUST send a credential only to the origin it is for, MUST NOT send
one over unencrypted HTTP beyond the local host, and MUST NOT show a
credential to any component that chooses acts, such as a language model.
Over CoAP {{RFC7252}}, which has no Authorization option, a service reached
beyond the local host SHOULD be reached over DTLS {{RFC9147}}.

# Security Considerations

Shared affordance sets are cached for a long time, possibly by shared
caches {{RFC9111}}. Integrity values carried by the freshly revalidated Layer 1 are what
make it safe for a client to trust bytes it will keep: each layer vouches
for the next. A client that skips the check can be given forms that send its
acts, and its users' data, anywhere.

Because affordance sets are shared, a server MUST NOT put instance
identifiers or per-user data in them. Template pointers are how instance
values reach a form, from the resource the client read.

Forms name targets, and a representation chooses where its links lead. A
client MUST scope credentials to their origin, so that a link to another
origin cannot collect them.

A client whose choices are made by a language model SHOULD give the model
only form names, titles, descriptions, schemas and link relations, never
URIs, methods, header fields or credentials, and SHOULD check each choice
against the representation it holds before sending anything. A model that
is never shown a URI cannot invent one.

# IANA Considerations

## Link Relation Types

IANA is asked to register the following in the "Link Relation Types"
registry {{RFC8288}}:

Relation Name: forms
: Description: Refers to a resource listing the forms (affordances) that are
  legal in the context resource's current state, shared among resources in
  that state.
: Reference: This document.

Relation Name: unavailable
: Description: Refers to a resource listing the actions that are not
  available in the context resource's current state, each with a reason.
: Reference: This document.

Until these are registered, HEART uses the extension relation types
`https://w3id.org/heart-rise/rels/forms` and
`https://w3id.org/heart-rise/rels/unavailable`, which identify the same
relations.

## Profile URIs

IANA is asked to register `https://w3id.org/heart-rise/hal-schema-forms` and
`https://w3id.org/heart-rise` in the "Profile URIs" registry {{RFC7284}},
with this document as the reference.

## HTTP Problem Types

IANA is asked to register the five problem types in the table in the
Conditional Acts section, and `not-authenticated` from the Credentials
section, in the "HTTP Problem Types" registry {{RFC9457}}.

--- back

# Implementation Status

A reference server library (Spring, Kotlin), three domain services built on
it, a reference client (ARTIE, Kotlin) with HTTP and CoAP bindings, and a
conformance suite exist at https://github.com/JogoShugh/heart-rise-artie.
Every identifier this document defines has a page reached through
https://w3id.org/heart-rise/.

# Acknowledgements
{:numbered="false"}

HAL Schema Forms is Jeremy Badeau's. HAL is Mike Kelly's. The architectural
argument is Roy Fielding's.
