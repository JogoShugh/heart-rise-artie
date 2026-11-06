# Idea: a property's default taken from state

**Status:** idea only. Raised by the author on 2026-10-19, while walking through what an L2 form and its schema
hold. No code, no spec text.

## The gap

A form can already take values from the resource that owns it, in two ways:

- **`hr:templatePointers`**, on the form: fill the target's URI template variables from the owning resource
  (HAL-SCHEMA-FORMS-HR Rule 1). They fill the address only, never a body field.
- **`hr:options`**, in a property's schema: the choices for that property, from a JSON Pointer into the owning
  resource or from a linked resource (HAL-SCHEMA-FORMS-HR, "Choices from state"). It is built in heart-core and
  ARTIE.

What a form cannot say is "this field starts as this value from the state". JSON Schema's `default` is a fixed
value written into the schema. So a body field cannot be pre-filled from the resource, or from another resource.

## The sketch

A property schema keyword shaped like `hr:options`, holding where the value comes from, never the value:

```json
"dueDate": {
  "type": "string",
  "format": "date",
  "hr:default": { "pointer": "/plan/nextWatering" }
}

"agent": {
  "type": "string",
  "hr:default": {
    "link": { "href": "/agents{?skill}", "templated": true },
    "templatePointers": { "skill": "/category" },
    "items": "/agents/0",
    "valueField": "name"
  }
}
```

- **Sources:** exactly one of `pointer` (the owning resource, a Level 1 template over the form's other
  properties allowed, as for choices) or `link` (another resource, read as any Layer 1 resource).
- **Allowed:** JSON Schema 2020-12 lets a schema carry keywords it does not define. Plain validators ignore
  them. A formal `$vocabulary` for the `hr:` keywords would make that explicit.
- **Sharing:** the schema holds only the instruction, so it stays the same for every resource in the same
  state. Its address and the affordance set's stay shared (SPEC §6.9), as with `hr:options`.
- **Reuse:** ARTIE's resolver for `hr:options` (`client/artie/src/main/kotlin/heart/Choices.kt`) already
  resolves both kinds of source.

## To decide

1. **Precedence:** a default is a suggestion. A value the script, a person or the reasoner gives wins.
2. **The server:** whether it fills the field from the same source when the body leaves it out, or only the
   client does. Unlike choices, nothing needs enforcing.
3. **Resolving to nothing:** the field is simply left without a default, unless it is required and nothing
   else fills it.
4. **With choices:** when a property has both, the default must be one of the choices, or it is ignored.
5. **Other dialects:** HAL-FORMS has a `value` per property, filled per resource at L0; the OpenAPI twin would
   say it in prose and an `x-heart-default` extension.
