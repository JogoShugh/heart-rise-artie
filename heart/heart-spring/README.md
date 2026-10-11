# heart-spring

HEART over HTTP on Spring WebFlux. A Spring Boot auto-configuration
(`HeartAutoConfiguration`): add the dependency, register `HeartResource`
beans, and every route below is served. The server writes no controllers.

## Routes, per declared collection

| Route | Handler |
|---|---|
| `GET /` | `HeartIndexHandler`: index in the negotiated dialect, or JSON Home; 304 on a matching If-None-Match |
| `GET /{collection}` | Search pages, when the resource has `find` |
| `GET /{collection}/{id}` | `HeartResourceHandler.read`: Layer 1 at the asked rung (capped by `heart.max-rung`); ETag, `Cache-Control: no-cache`, `Vary`, profile `Link`, `Preference-Applied`; 304 |
| `GET /{collection}/{id}?asOf=`, `/history` | The past, when the resource has `past` |
| `POST /{collection}/{id}/actions/{path}` | `HeartResourceHandler.act`: negotiates first (406 before any write), runs `ActPipeline`, answers new Layer 1 with `Content-Location` |
| `GET /affordance-sets/{address}`, `/schemas/{address}`, `/unavailable/{address}` | `HeartContentController`: exact bytes, `immutable`, `Repr-Digest` |
| `GET /profiles/{name}` | `HeartProfileController`: ALPS, ETag, one-day cache |

## Dialects

- HAL Schema Forms (`application/hal+json`): bodies from `heart-core`'s `Layers`.
- HAL-FORMS (`application/prs.hal-forms+json`): rendered by Spring HATEOAS from
  each act's input class; always L0.

## Errors

`HttpRefusals` maps heart-core's exceptions: 428, 412, 409, 422 as RFC 9457
problems; 404, 406 and 400 as `{"error": …}`.

## Using a resource from your own routes

`HeartHandlers.of(resource).represent(state, request)` renders any state
exactly as Layer 1 is rendered.

## Tests

`src/test/resources/features`: content routes, the resource and its acts,
the index, and the past, all against the lamp.
