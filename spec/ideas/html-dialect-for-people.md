# Idea: HTML as a third dialect, for people, with htmx

**Status:** idea only. Raised by the author on 2026-10-26, from the question "how hard would a volleyball domain be,
and an htmx UI atop the HEART service?" Nothing is built. Not to be started before the author's review of the
existing work.

## In one sentence

Point a plain browser at a HEART resource, and content negotiation hands it HTML: the state laid out for a person, and
a button for each act that is legal now, and no button for the rest. It comes from the same declaration and the same
legality decision as the two JSON dialects, with no code in the browser beyond htmx.

## Why it matters

HEART's whole argument starts with the browser: a generic client, coupled to HTML and to no site, shown only the
controls the server offers for this resource now (spec/ideas/heart-tour.md, part one). Machine APIs lost that; HEART
gives it back to agents. An HTML dialect gives it back to people too, from the same server, and closes the loop. The
absent button does in a browser exactly what the absent form does for an agent.

It is also the best onboarding demo there could be (spec/ideas/onboarding-from-evidence.md): session 2 of the
onboarding map could happen in a browser, where a newcomer sees the merge button simply missing on a draft.

## Three dialects, one declaration

| Accept | Dialect | For | Rung |
|---|---|---|---|
| `application/prs.hal-forms+json` | HAL-FORMS, `_templates` | existing toolchains (Spring HATEOAS) | the L0 on-ramp |
| `application/hal+json` with the HEART profile | HAL Schema Forms, `_forms` | agents and ARTIE | L0 to L2, the reference |
| `text/html` | HTML with htmx | people in a browser | L0-like: everything in one answer |

A browser sends `Accept: text/html` by default, so it gets the third without asking. SPEC §6.10.6 (L5) already
requires dialects to agree on which acts are legal; the HTML dialect is held to the same rule.

## How it works at runtime, step by step

The browser never sees JSON, schemas or hints. It only ever gets finished HTML. All the assembly happens on the
server.

1. **The browser asks for the game, as browsers always do:** `GET /games/1`, `Accept: text/html`.
2. **The server does what it does for JSON,** up to the last step: it loads game 1's state and decides which acts are
   legal now. Then, instead of writing HAL JSON, it writes HTML. This is the only new step, and the only place view
   hints (below) are used.
3. **The browser receives plain HTML:** the score, the players, and one form per legal act. Each form carries the
   act's address, its method for htmx (`hx-post`, `hx-put`), the current ETag in `hx-headers` as `If-Match`, and an
   input for each field. An act that isn't legal has no form at all.
4. **A person clicks "Score basket".** htmx sends the form, with `If-Match` and `Accept: text/html`.
5. **The server runs the act checks it always runs:** 428, 412, 409, 422. If they pass, it records the act and answers
   with the new HTML for the game: the new score, a new ETag inside each form, and whichever buttons are legal now.
6. **htmx swaps that HTML into the page.** No reload, no logic in the browser.

A refusal comes back as HTML too. A 412 says "the game changed" and shows the current state; a 422 shows the field
and why; a 409 can't normally happen, since the button wasn't there, but is shown plainly if it does.

## Where things live

Basketball (`domains/basketball/`) is already split by what each part knows. The HTML dialect adds one optional part
and touches none of the others.

| Layer | Basketball today | Knows about |
|---|---|---|
| Domain | `domain/`: `Game`, `GameState`, `GameEvent`, `GameService` | basketball only. No HTTP, no HEART, no UI. Unchanged. |
| Declaration | `declaration/`: `Games`, `GameActs`, `Inputs`, `BasketballProfile` | how the domain is offered: resources, acts, inputs, vocabulary. Unchanged. |
| View (new, optional) | e.g. `view/GameView.kt` | how a person might like to see a game |
| Library | `heart/heart-core` and the bindings | how a declaration becomes each dialect, and how acts are checked. Gains an HTML writer. |

| What | Where it lives | Who uses it |
|---|---|---|
| Rules and acts | the domain and the declaration, on the server | every dialect |
| View hints | the view, on the server | only the server's HTML writer |
| htmx | one small script the page includes | the browser, to send clicks and swap answers |
| Logic in the browser | none | |

## View hints

Without hints, the HTML writer renders any resource generically: the state's fields as a list, nested lists as
tables, forms in the order declared. That works, and it is plain. Hints say, as data, how a person might like to see
a resource type. They are never a hand-written page and never code.

```kotlin
// view/GameView.kt: optional; delete it and the generic rendering is used
val gameView = View(
    headline = listOf("/homeScore", "/awayScore"),          // shown big, side by side
    summary  = listOf("/period", "/possession", "/phase"),
    tables   = mapOf("/home/players" to listOf("number", "name", "points", "fouls")),
    primary  = listOf("bb:score-basket", "bb:free-throw", "bb:record-foul"),
    confirm  = listOf("bb:final-whistle"),                   // irreversible: ask first, in the page
    hidden   = listOf("/scoringPlays"),                       // behind "show more"
)
```

Kinds of hint: emphasis, grouping and order of fields; lists shown as tables with chosen columns; the order and weight
of forms (primary buttons, a "more" menu); confirmation for irreversible acts; formats (a clock, a score, a date,
partly covered by JSON Schema's `format`). Labels come from the ALPS profile the server already serves, so no new
words are invented.

The rules:

- **Hints never touch legality.** A hint can make a button big or small; it can't make an absent form appear.
- **A hint is a function of the resource type, not the instance.** One view per type, the same for every game, so it
  could be content-addressed and shared like an affordance set if it is ever served on its own.
- **Hints are optional.** Without them the generic rendering still works.
- **Only the HTML writer reads them.** The JSON dialects ignore them, and agents never see them.

Prior art for the shape: JSON Forms (EclipseSource) has a "UI schema", a separate document beside the JSON Schema
with layouts (`VerticalLayout`, `Group`) and controls that point into the data by scope; react-jsonschema-form has a
similar `uiSchema`. Both describe forms rather than state, but the hints could follow JSON Forms closely enough to be
recognised, extended for showing state.

## Inputs from schemas

The HTML writer turns each form's JSON Schema into inputs:

- `enum` and `hr:options` (choices from state, profile Rule 8) become a `<select>` of the allowed values only;
- `integer` and `number` become `type=number`, with `minimum` and `maximum`;
- `string` with `maxLength` or `pattern` become text inputs with those limits; `format` picks `date`, `email` and so on;
- `required` becomes `required`;
- `default` fills the value; `title` and `description` become the label and help text.

The browser's own checks are a convenience; the server's 422 is still the rule.

## Disabled, not only hidden

Added 2026-10-26, from the author: UIs commonly show unavailable actions greyed out or disabled, and that is good
design. Stable layout, discoverability ("you could do this once…"), and no "where did the button go?". Hiding every
act that isn't legal is the purist reading of HATEOAS and a blind spot of it. `hr:unavailable` (SPEC §6.12) is the
answer HEART already has; the HTML dialect should use it fully.

### Three cases per act

For every act a resource type can offer, in a given state:

| Case | Server says | HTML shows |
|---|---|---|
| Legal now | a form in `_forms` | an enabled button with its inputs |
| Not legal, reason given | an entry in `hr:unavailable` | a disabled button with the reason shown |
| Not legal, nothing said | nothing | hidden |

The third row keeps the honesty: SPEC §6.12 says a client shall not infer a reason from absence alone, so the UI
never greys out a button from a catalogue of everything the type could do. It shows only what the server explained.
A disabled button cannot act, which matches the rule that a client never acts on a relation it finds in
`hr:unavailable`.

### What is needed beyond today

- **Stable positions.** The view hints list the acts in order, so an absent act keeps its slot. Per act, a hint says
  whether to show it disabled or hide it when absent (`whenAbsent = disabled | hidden`); the default is disabled when a
  reason is given.
- **Reasons always fetched for HTML.** Agents fetch `hr:unavailable` only when asked. The HTML writer, on the server,
  always includes it. It is shared and cached, so it costs almost nothing.
- **Accessibility.** The reason is visible text, or reachable by keyboard: `aria-disabled="true"` with the reason in the
  accessible description. Never only a hover tooltip, which touch and keyboard users never see. A disabled button stays
  focusable so its reason can be read.
- **Kinds of absence (a gap in the profile today).** Three different stories, which a UI wants to treat differently:
  - **not yet:** "The crop is not ready." It will become possible: show it disabled, perhaps with what it waits on;
  - **not for you:** a permission. It won't become possible for this caller: usually hide it, and explain no more than
    needed;
  - **never again:** the game is final. Hide it, or show a quiet "game over".

  An optional `kind` on each `hr:unavailable` entry (`not-yet`, `not-permitted`, `final`), and perhaps a short
  "becomes available when…" phrase, would let the UI choose. Agents gain too: "not yet" means wait or watch, "not for
  you" means stop, "final" means give up. This belongs in the profile itself, not only in the HTML dialect, and would
  need its own feature-first change to SPEC §6.12 and `spec/HAL-SCHEMA-FORMS-HR.md`.
- **Excluded choices, inside a form.** The same applies to fields. A fouled-out player could appear in the `<select>`,
  greyed out, labelled "fouled out". `hr:options` (profile Rule 8) keeps only the allowed values today. An extension
  could list the excluded ones with a reason each, served the same way: the server still refuses them with 422. Open,
  and smaller than the rest.

## Two flavours, both possible

- **Server-rendered HTML with htmx** (this idea's core). For people. No client program.
- **An explorer**, in the spirit of Mike Kelly's HAL Browser and Kai Toedter's HAL Explorer: a JavaScript client in the
  browser that fetches the JSON dialects and draws them, for developers, showing raw JSON, the layers and the integrity
  checks. It is a client, so it is separate and optional work. A cheap middle ground: the HTML carries
  `<link rel="alternate">` to its own JSON dialects, so a developer can flip between the human view and the machine
  view of the same resource.

## What everything gets

Every HEART service gets a browser UI from the same declaration: the garden, basketball, tickets, a volleyball domain
(below), and GitHub through the gateway. That last is a GitHub screen generated from GitHub's OpenAPI description and
a connector, where the merge button exists only when merging is legal and "why not?" shows the reason from
`hr:unavailable`.

## Limits and decisions

- **Credentials: the first decision.** SPEC §6.13 admits Basic and Bearer. Browsers handle Basic natively but with a
  native login dialog; Bearer doesn't fit a browser. A browser UI wants a cookie session: a new decision about who holds
  the credential, with CSRF protection to match (htmx can send a token header). The gateway, which calls GitHub as each
  caller, would need a sign-in of its own.
- **Methods and headers.** Plain HTML forms only send GET and POST and can't set `If-Match`. htmx sends any method and
  any header. Without JavaScript the fallback is POST with a hidden validator field and a method field, accepted only by
  the HTML dialect.
- **Body encoding.** htmx sends form data; acts take JSON. Either htmx's `json-enc` extension, or acts accept
  `application/x-www-form-urlencoded` too, converted to the same JSON before the schema check.
- **Addresses.** HTML forms carry concrete addresses: the server fills the templates (`hr:templatePointers`) itself.
  That is fine, because a Layer 1 answer is never cached.
- **Caching.** HTML is L0-like: state and forms in one answer, nothing shared. Fine for people. `Vary: Accept` already
  applies to Layer 1 (SPEC §6.1).
- **Escaping.** Content through the gateway is untrusted (GitHub titles, comments). Every value is escaped; no raw HTML
  from state, ever.
- **Is `text/html` a HEART dialect?** Either it gets a profile of its own (for example
  `https://w3id.org/heart-rise/html`) and conformance rules, or it is described as "the on-ramp for people", outside the
  profile. Open.
- **Conformance.** A test per domain asserts that the HTML offers exactly the forms the JSON offers, in every state the
  features reach.

## A volleyball domain, as its showcase

A second scoring domain shaped like basketball (about 1,300 lines with tests; `domain/`, `declaration/`, one feature,
the TCK), feature first, with recreational or youth rules to keep it small:

- rally scoring; sets to 25, the fifth to 15; win by 2; best of five;
- the serve rotates when the receiving side wins the rally;
- two timeouts per team per set, so the timeout form disappears when they are used up;
- limited substitutions per set, offered with `hr:options` as the eligible bench players only;
- phases: scheduled, live, timeout, set break, final.

Nearly every rally changes what is legal (who serves, whether a timeout is left, who may come on), so it shows HEART,
and the HTML dialect, at their best. The main risk is rule creep (liberos, rotation faults); keep to the small rules.

## Suggested order, when started

1. Decide the credential question and whether `text/html` gets a profile.
2. The generic HTML writer in heart-core, with htmx, feature first; the dialect-agreement test on the garden.
3. Basketball in a browser, generic; then a view file for it.
4. GitHub through the gateway in a browser.
5. Volleyball, with a view, as the showcase.
6. The explorer, if wanted, after all of the above.

## Review, 2026-10-30

Read against the code as it stands. The idea holds; the points below are what the code and htmx's own defaults
change about it.

**What fits.** Negotiation is one function (`Negotiation.dialect`) and the dialect an enum, so a third value is a
small, local change, and its tag joins the Layer 1 ETag the way `hf` and `hsf` do. Both bindings (Spring for the
domains, http4k for the gateway) already ask heart-core for the dialect and the representation, so an HTML writer in
heart-core serves both. Legality, `hr:unavailable` and `hr:options` are all decided before any writer runs, so the
HTML cannot offer what the JSON does not.

**What the idea gets wrong or leaves out.**

- **A browser's Accept falls to the default today.** `text/html,…,*/*;q=0.8` has a wildcard, so the server answers
  HAL-FORMS JSON. `text/html` must be ranked like a dialect media type, and named explicitly: a wildcard alone must
  never select HTML, or a client that names nothing would get a page.
- **htmx does not send `Accept: text/html`.** Its requests go out with the browser's default for scripts, `*/*`, and
  would get JSON back. Each page sets `hx-headers` with the Accept (and the ETag), or the server reads `HX-Request`.
  The first is plainer and keeps negotiation by Accept alone.
- **htmx does not swap a 4xx answer by default.** A 412, 409 or 422 rendered as HTML would be dropped. htmx 2's
  `responseHandling` config must swap them; one line in the page.
- **Form data is all strings.** htmx's `json-enc` would send `"milliliters": "250"`, which the schema refuses as not an
  integer. The act pipeline should take `application/x-www-form-urlencoded` and convert each field by its schema type
  before the check, in heart-core, so both bindings get it and the 422 stays the rule.
- **A 201 has nowhere to go.** An act that makes a resource answers 201 with a Location; htmx swaps the body in place.
  The HTML answer to a 201 should send `HX-Location` so the browser moves to the new resource, with history.
- **Links need no htmx.** A link is a plain `<a href>`: the browser follows it with `Accept: text/html` by itself.
  htmx is for forms only.
- **Machine-readable too.** Each form carries `data-rel` with its relation, so the dialect-agreement test reads the
  HTML's forms without a browser, and an agent could, too.

**The two decisions, with a recommendation each.**

1. **Credentials.** Recommend: first slice on a domain server run locally with its existing Basic credentials, which a
   browser handles natively (one native sign-in, acceptable for a local demo); a cookie session, with CSRF, only when a
   shared or gateway deployment needs it.
2. **Is `text/html` a HEART dialect?** Recommend: not yet. Serve it as the people's on-ramp, outside the profile, held
   to the one rule that matters (it offers exactly the forms the JSON offers). A profile can follow once it is used.

**First slice, if started.** Feature first, in heart-core and the Spring binding: `text/html` negotiated by name; a
generic writer (state as a definition list, nested lists as tables, one htmx form per legal act with `data-rel`,
`If-Match` and Accept in `hx-headers`, inputs from the schema, `hr:unavailable` as disabled buttons with the reason);
form-encoded acts converted by schema type; refusals rendered and swapped; `HX-Location` on 201. Shown on the garden,
with the agreement test there. No view hints, no volleyball, no gateway in this slice.

## Generic interaction defaults, 2026-10-30

From the author's sketch (acts in overlay dialogs, unavailable acts greyed and inert, errors inline in the dialog,
client-side checks from the schema, entry-point searches as a full-page transition, lists as grids with per-row
actions), assessed against the protocol. Where this differs from the sketch, the reason is given.

**One rule decides overlay or page: the form's method, not where the form sits.**

- An unsafe act (POST, PUT, PATCH, DELETE) opens an overlay dialog on the resource held, and its answer replaces the
  resource in place.
- A safe form (GET: find, search, filter) also takes its fields in a dialog, but submits as a plain HTML GET form: a
  full-page navigation to the result, which then has its own address, a back button and a bookmark.
- So an entry point's searches give the full-page transition the sketch asks for, for the result. They do not get a
  page of their own for their fields: a form has no address in HEART (it lives inside a representation), and making
  one would add protocol surface and a round trip for nothing the dialog doesn't already do. It also covers the cases
  "entry point vs resource" would get wrong: a repository offers safe forms ("list pull requests labelled…"), and an
  entry point may offer an unsafe act.

**Unavailable acts: in their slot, inert, and saying why.**

- The buttons are the union of the acts offered now and the acts `hr:unavailable` lists, in the type's declared order,
  so each act keeps its place as the state changes. The writer is on the server, so it knows that order with no hints.
- An act in `hr:unavailable` is a greyed button with `aria-disabled`, still focusable, never sending anything. Its
  reason shows when it is focused or clicked, as visible text, never only a hover tooltip. A greyed button with no
  reason is the familiar frustration; the reason is the whole point of `hr:unavailable`.
- An act neither offered nor listed is not shown (§6.12: no reason is inferred from absence).

**The dialog.**

- A native `<dialog>`: focus moves in, Escape closes, focus returns to the button. Title and description from the
  form; one input per field the client fills (fields bound from state by template pointers are filled by the server
  and not shown); the submit button named by the act's title.
- An act with no fields still opens the dialog, as its confirmation, with its description. One more click, but every
  act behaves the same and none fires by accident. A view hint may later mark an act as one-click.

**Checks in the browser, two layers, from the form's own JSON Schema.**

1. HTML constraints written from the schema: `required`, `min`, `max`, `step` (from `multipleOf`), `minlength`,
   `maxlength`, `pattern`, input types from `type` and `format`, a `<select>` from `enum` and `hr:options`. No script,
   and the browser's own accessible messages.
2. The schema itself, embedded in the dialog, checked on submit by a JSON Schema validator for what HTML cannot say:
   `oneOf`, `anyOf`, `if`/`then`, `const`, `dependentRequired`, array rules. Its messages go beside the field its
   pointer names.

htmx and the validator are served by the HEART server itself, as shared immutable files like an affordance set, so a
page needs no CDN and works under a strict CSP. The server's checks stay the rule: a rule of the resource (422
rule-refused) and a race (412) can only be found there.

**Errors, inside the dialog, by kind.**

| Answer | In the dialog |
|---|---|
| 422 invalid input | each message at its field, by the problem's pointer; values kept; focus on the first |
| 422 refused by a rule | the rule's words at the top; values kept |
| 412 stale | "This changed since you opened it." The resource behind is read again; if the act is still offered, the dialog stays, values kept, with the new ETag; if not, it closes and the button shows its reason |
| 409 not legal now | as a 412 whose act is no longer offered |
| 401, 403 | the refusal at the top; sign-in is the page's business |
| network, 5xx | a plain message and a retry; values kept |

Success: a 200 closes the dialog, replaces the resource in place, and says "Done: <act title>" in a polite live
region. A 201 moves to the new resource (`HX-Location`), with history.

**Lists: a grid, and actions per row fetched when asked for.**

- Columns: the item's title, as the link to it, first; then up to five scalar members most items share, from the page
  shown; nested objects and arrays left out; long text cut. On a phone, rows become cards. Paging by the list's own
  next and previous links. The order is the server's; sorting appears only where the server offers it, as a safe
  form.
- The sketch's per-row action forms cannot come from the list: an embedded resource carries no forms (SPEC §6.11,
  decided 2026-10-18), because every act needs `If-Match` and an embedded copy has no ETag. That is also the right
  answer for a person: what may be done to each item depends on that item's own state, read fresh.
- So each row has an "Actions" menu that, when opened, reads the item (`Accept: text/html`) and shows its action bar
  from that answer: its legal acts and its greyed ones with reasons, at its current ETag. Choosing one opens the same
  dialog as on the item's own page. After a success the list is read again. One read per opened menu, none for rows
  never touched.
- Better than the menu for working through a list: a side panel that shows the chosen item, its state and its action
  bar, beside the list, so a person keeps their place. Left for later, as an option; the menu and the link to the item
  are the plain default. No bulk selection: HEART has no act on many resources at once.
- `spec/ideas/embedded-forms-with-validators.md` would allow row forms without the read; if it is ever decided, the
  menu can use them.
