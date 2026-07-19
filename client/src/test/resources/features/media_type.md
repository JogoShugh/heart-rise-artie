# Media Type — Protocol Conformance

Corresponds to **§6.1 Media Type** of the H.E.A.R.T. specification.

---

## What this verifies

A H.E.A.R.T. response is an atomic unit: resource state and affordance manifest delivered together in a single response. This feature verifies that a compliant client can receive that unit, parse it correctly, and hold its two parts — state and affordances — separately and intact.

### Affordance fields are parsed correctly

Every affordance form in a H.E.A.R.T. response carries three required fields: a `href` (the server-provided target URI the client must use verbatim), a `hash` (the schema fingerprint used for registry cache decisions), and a `_schema` (the full JSON Schema document describing what the client may submit). This scenario confirms that all three survive parsing without loss or corruption.

### Schema preserves full JSON Schema keywords

The specification requires that `_schema` be treated as a standard JSON Schema document. The full vocabulary is available — `description`, `minimum`, `maximum`, `enum`, `pattern`, `format`, and any other standard keywords. A client that strips or ignores these keywords loses the semantic context the server provides for reasoning and validation. This scenario verifies that no keywords are dropped: `description`, `minimum`, and `enum` are all present after parsing exactly as the server sent them.

### Required fields are captured

The `required` array in a schema is a protocol-level constraint, not merely documentation. A client that fails to capture it cannot enforce required field presence before submission, which means it may send invalid payloads the server must reject. This scenario verifies that the `required` array is correctly preserved and queryable after parsing.

### Resource state is extracted independently of forms

The specification mandates that resource state and the affordance manifest are delivered simultaneously but are logically distinct. Resource state is the domain data — the current condition of the resource. The affordance manifest (`_forms`) is the protocol layer — what the client may do next. A client that conflates the two, or that allows `_forms` to leak into the resource state, violates the separation that makes domain reasoning clean. This scenario verifies that the parsed state contains only domain fields and that `_forms` is absent from it.

---

## Protocol references

| Requirement | Spec section |
|---|---|
| Affordance form must carry `rel`, `href`, `hash`, `_schema` | §6.1 Affordance Form |
| `_schema` must be treated as a full JSON Schema document | §6.1 Field Schema |
| `properties` and `required` are mandatory schema fields | §6.1 Field Schema |
| Resource state and affordance manifest are an atomic unit | §6.1 Atomic Unit |
| Content-Type must declare both profile URIs | §6.1 Media Type |
