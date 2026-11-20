# Plan: HTML for people, by content negotiation

**Status:** slices 1 to 5 done 2026-10-30 (commits c99a749, 50b8bf2, 749d74b, 56c4bd7; report
docs/reports/html-for-people/). A 201 moves the browser with `HX-Redirect`, a full navigation, rather than
`HX-Location`. Still open: the gateway's browser sign-in, field order (schemas are canonical, so alphabetical), and view
hints. The client-side JSON Schema check is done (f21f635). The author asked for it built with as little of their input as possible ("we are
going full content negotiation"). Design: spec/ideas/html-dialect-for-people.md (its review and interaction
defaults); mockup: docs/reports/html-dialect-mockup/.

## Decisions taken

| Question | Decision |
|---|---|
| A HEART dialect? | Not yet. HTML is an on-ramp outside the profile, as plain JSON is: not in `Dialect`, held by test to offering exactly the acts the JSON offers. Promoted once proven. |
| Who renders it | heart-core, from the L0 HAL Schema Forms body and the explanation of absent acts. So heart-spring, heart-http4k and the gateway (which serves through heart-http4k) all get it from one renderer. |
| When it is chosen | `Accept` names `text/html` above both dialects. A browser does; `*/*` alone never does, so an agent that names nothing still gets HEART. |
| Validator | Its own ETag, tag `html`, over the L0 body and the renderer's version. Forms carry it as If-Match. `Vary: Accept`. |
| Scripts | htmx 2.0.4, vendored, plus heart-forms.js and heart.css, served by the server at `/heart/assets/`, immutable. No CDN. |
| Sign-in | Whatever the binding already has: Basic on the garden. The gateway's browser sign-in stays open (mockup finding 1). |
| Acts | Each act a button that opens a native `<dialog>`; acts with no fields too, as a confirmation. Form fields are posted form-encoded and converted to JSON by the act's schema in heart-core before the usual checks. |
| Unavailable acts | Shown greyed in the type's declared order, `aria-disabled`, focusable, the reason shown as text when pressed. An act neither offered nor explained has no button. |
| Labels | Field and state labels by the field-title convention (moved to heart-core); the member's name on hover. The wire never changes. |
| Answers | Success: 200 with the resource's HTML, swapped in place, and an `HX-Trigger` announcement. 201: `HX-Location` to the new resource. Refusals: an HTML problem fragment with the same status, shown in the dialog. 412: re-read; keep the dialog when the act is still offered. |
| Client validation | HTML constraints from the schema: required, min and max, lengths, pattern, enum as a select, boolean as a checkbox, an array of strings as repeatable inputs. A full JSON Schema validator in the browser is deferred; the server decides. |
| Lists | A grid: title column, then up to five shared scalar members; paging by the list's links; each row an Actions menu that reads the item. |

## Slices, each one commit, feature first

1. **A resource read as HTML.** Negotiation, the renderer (state, links, embedded, offered acts as dialogs, absent
   acts greyed with reasons), the assets route, both bindings. A binding feature (heart-tck `heart/binding`) run by
   heart-spring and heart-http4k on the lamp.
2. **Acts from a browser.** Form-encoded input converted by schema; HTML answers; refusals as HTML; 201 as
   `HX-Location`.
3. **The index and lists.** The bookmark's forms (searches are GET forms: full navigation); search results and listings
   as a grid with row Actions.
4. **Live.** The garden and the GitHub gateway in headless Chrome; screenshots.
5. **Report.** The mockup page becomes the built page's report, with the screenshots.

## Out of scope for now

The gateway's browser sign-in; a client-side JSON Schema validator; view hints; HTML as a dialect in SPEC.
