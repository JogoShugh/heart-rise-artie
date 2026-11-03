# Plan: an upgrade for Spring HATEOAS apps, from `_templates` to HEART's `_forms`

Status: proposed, 2026-11-03. Plan only; nothing built.

## The question

An app on Spring HATEOAS already describes what can be done (`afford(methodOn(…).updateEmployee(…))`) and serves
it as HAL-FORMS `_templates`. Can a dependency, added with no rewrite, serve the same app as HEART: `_forms` with
JSON Schema, writes guarded by ETags, shared form sets, and, with a little help from the app, acts offered and
refused by state? Yes, in four levels; the first three need no change to the app.

## What exists

- **The untouched example.** spring-hateoas-examples `affordances` (Spring Boot 2.3.4, Spring MVC), run by ARTIE on
  2026-11-02 over HAL-FORMS (wild/fresh-run/LOG.md). ARTIE got through by working around it: methods in lower case
  (`post`, which Tomcat refuses 405), no `target`, no ETag, no profile, 204 with no body after a write.
- **heart/heart-spring.** HEART's own model on WebFlux, which also serves HAL-FORMS. It doesn't read Spring HATEOAS's
  affordances, by decision (spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md): an app has to declare its acts again.
- **heart-core.** Affordance sets, content addressing and integrity (`Layers`), input schemas, the act pipeline's
  order (428, 412, 409, 422), problem details. Plain Kotlin, reusable here.
- **The gateway** (OpenAPI in, HEART out), for an app that changes nothing at all.

## The module: `heart-spring-hateoas`

A dependency and one switch (`@EnableHypermediaSupport` gains HEART's media type, or a property). Spring MVC first,
which is what most Spring HATEOAS apps run; WebFlux after.

| Level | What a client gets | Built from | The app changes |
|---|---|---|---|
| 1. Forms | Each affordance as a `_forms` entry: named as a link relation, method upper case, `_links.target`, a JSON Schema of its input type with its validation constraints; the profile named. HAL-FORMS still answered to a client that asks for it | Spring HATEOAS's hook for custom media types (a `HypermediaMappingInformation` bean and its Jackson module) and its affordance model (each input property, required, pattern, bounds) | nothing |
| 2. Conditional writes | An ETag on every representation; a write without `If-Match` refused 428, a stale one 412, as problem details. After a write, the new state, where the app answered 204 | A filter: the resource's current representation, from the app's own GET in-process, hashed | nothing; a route can opt out |
| 3. Shared form sets (L2) | `Prefer: forms=link, schema=link` answered with an `hr:forms` link; `/affordance-sets/{hash}` and `/schemas/{hash}`, immutable, cached a year | heart-core's `Layers` | nothing |
| 4. Offered by state | An act the resource doesn't offer now refused 409 with the reason; the reason shown beside the forms (`hr:unavailable`) | Before a write, the app's own GET in-process: the write goes ahead only if that representation offers an affordance with the request's target and method. The reason from a small addition: `afford(…)` made conditional, as many apps already do, and `.because("…")` | per affordance, small |

Level 4 reuses the app's own rules for what it offers: nothing is declared twice.

## Edges

- **Names.** Spring names an affordance by its method (`updateEmployee`; the first on a link is `default` in
  HAL-FORMS). HEART names a form as a link relation. The module writes `{prefix}:{method in kebab case}`
  (`staff:update-employee`), the prefix configured, with a table to rename; HAL-FORMS keeps Spring's names.
- **Versions.** Spring HATEOAS 1.x (Boot 2) and 2.x (Boot 3) differ a little, and older versions say less about each
  property's constraints. Target Boot 3; fill missing constraints from the input type's Bean Validation annotations.
  The example moves to Boot 3 for the proof; Boot 2.3 too, if cheap.
- **Cost.** One extra in-process GET per write, for the ETag (level 2) and the check (level 4).

## The proof

1. The `affordances` example, unchanged but for the dependency: ARTIE runs it with no `--dialect` and no
   workaround: `_forms`, ETags, a 412 when someone changes Frodo between the read and the write, decided again.
2. At L2: one form set per state, fetched once a run.
3. heart-tck against it, levels 1 to 3; level 4 with `.because`.
4. Other clients: heart-ts, and code mode told only the address.
5. A short report, beside wild/fresh-run/LOG.md: the same table of what Spring sends, before and after.

## Steps (feature first, as always)

0. The decisions below.
1. The module in heart/, with a small test app holding the example's employee controller.
2. Level 1, then level 2: the proof's first run.
3. Level 3.
4. Level 4.
5. The TCK, the other clients, the report.

## Decisions for the author

- The name: `heart-spring-hateoas`, or another.
- Spring MVC first and WebFlux after, or both at once.
- Boot 3 only, or Boot 2 too.
- The shape of level 4's addition: `.because("…")` on the affordance, or an annotation on the handler method.
- Releasing: built and kept here until you say; publishing it (Maven Central) or proposing it to the Spring HATEOAS
  project is yours to do.

## Said plainly

- Without level 4, forms are only as state-aware as the controller already makes them, and the server doesn't
  refuse an act it didn't offer.
- What a JSON Schema can say depends on what the input type says: a field with no annotations is just its type.
