# RISE vs. Fielding 2008: Is the Library/Ana Run Hypertext-Driven?

Source material:

- The run report *RISE: The Library and Ana, End to End* (22 steps, 22 as expected).
- SPEC.md §6–7 and §13.
- Roy Fielding, [REST APIs must be hypertext-driven](https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven) (2008).

Updated with the complete trace from the published report: every request, answer, form and schema. See §0.

---

## 0. What the full trace shows

Source: the published run report, whose page embeds the complete trace (every request, every answer, all
`_forms` and `_schema` blocks). The `w3id.org/heart-rise/...` addresses could not be fetched from the analysis
sandbox. They do not resolve yet; the plan is w3id redirects to GitHub Pages (see to-do 1).

### 0.1 Already right, and better than the summaries suggested

- **Full URI handover.** The watch request carries the reply as a HAL link with type and profile hints,
  never an id. The library's form *requires* this envelope. Its `tell-me-when-renewable` schema demands
  `_links.hr:reply-to` (`href`, `type` = `application/hal+json`, `profile` from an enum) and `_links.hr:keys`
  (`type` = `application/jwk-set+json`). This is the prescriptive RISE control data §4 asked for, done as hypermedia.
- **Standard signatures.** Every library act carries:
  - RFC 9421 `Signature-Input` / `Signature`, covering `@method`, `@target-uri`, `content-digest` and `if-match`;
  - RFC 9530 `Content-Digest`;
  - `keyid="library-2026-11"`, `alg="ed25519"`.

  Rule 2 asks for exactly this: a standard extension, not a private one. Covering `if-match` binds each signature
  to the version it was made against. That is a nice touch.
- **Conditional requests on every act**, and the identical replay keeps the same ETag (`seq-4…`), so nothing is
  recorded twice.
- **Every form is self-describing:** `method`, `contentType`, `title`, `description`, a target link, a JSON Schema
  with an `$id`, and an `hr:integrity` (SRI-style sha256). The entry also carries an `hr:manifest` with a
  digest. The fingerprint registry from SPEC §6.5 is real.
- **Server-supplied URI templates for reads.** The entry's GET forms carry templated targets
  (`/accounts{?name}`, `/loans/{id}`). The client fills in a template the server hands it. That is allowed: it is
  how an HTML GET form works.
- **`hr:unavailable` on every resource,** with type and integrity. The 409 is full RFC 9457 with a `type` URI.
- **The watch now uses an absolute `hr` CURIE:** `https://w3id.org/heart-rise/rels/{rel}`.

### 0.2 Findings, most serious first

1. **The reply declares two CURIEs both named `hr`.** One is the old relative `/profiles/hr#{rel}`; the other is the
   absolute `https://w3id.org/heart-rise/rels/{rel}`. A client cannot know which expansion `hr:completed` means. Drop
   the relative one. The 409's `"rel": "/profiles/hr#completed"` still uses the old relative form; make it the
   absolute URI.
2. **The entry uses `hr:` without declaring it.** Its only CURIE is `library`, yet it carries `hr:integrity` and
   `hr:manifest`, and its form demands `hr:reply-to` and `hr:keys`. Add the absolute `hr` CURIE to the entry. Every
   response that mentions `hr:` should declare it.
3. **The client does not obey the form's content type.** Every form says `contentType: application/json`. Ana's POST
   and every library act send `Content-Type: application/hal+json`, and the failure sends `application/problem+json`.
   - Either the forms are wrong (they should say `application/hal+json`, which is right for bodies carrying
     `_links`), or the client is ignoring them.
   - Either way, this is the litmus test from §2.1 failing in miniature: the client has its own idea of the
     content type.
   - For `hr:failed`, the form should say `application/problem+json`, since that is what the body is.
4. **The library's key is not discoverable from the library.** Ana sends `hr:keys` → `https://caller.example/keys`,
   which is *her* key set. But the signatures that Ana's reply verifies are the *library's*
   (`keyid="library-2026-11"`). Unless Ana's key set happens to contain the library's public key, which is an
   out-of-band arrangement, Ana has no hypermedia path to the key she must verify with. Fix:
   - the library advertises its own `hr:keys` (on its entry and on each watch);
   - the back-link (§5) gives Ana the path: reply → `hr:in-reply-to` → watch → `hr:keys`;
   - also decide what Ana's `hr:keys` is *for*: which signatures, by whom. Write it in the relation document.
5. **`result` is a bare `object`.** The `hr:completed` form's schema is `result: {type: object}`, with nothing
   inside. The library happened to send `loan` and `renewableFrom`. Nothing told it to, so it relied on out-of-band
   knowledge (rule 3). Ana's server publishes this form and knows what she asked for, so it should name the fields
   (see §4).
6. **`note` is a plain string, not nullable, and goes stale.** The progress schema says `note: {type: string}`. After
   completion the reply still says `percent: 50` and `note: "the hold is with the next reader"`, which describes a
   state that no longer exists. Make `note` nullable with default `null`. Also define what completion does to
   progress: clear it, or set `percent: 100`.
7. **The failure reason is not in the representation.** The library sends `{"title": "No hold record for loan 3310"}`.
   Afterwards the reply shows only `state: "failed"`. The reason Ana most needs is gone from her own resource. Keep
   the problem document in the reply, embedded or as a link. Also make the failed schema full RFC 9457: add `type`
   (URI), keep `title` required, and `detail` optional.
8. **`If-Match` is sent to a different resource than the one its ETag came from.** The ETag comes from
   `/replies/r-7f3`, but the acts go to `/replies/r-7f3/actions/{progress|completed|failed}`, all with PUT. RFC 9110
   evaluates `If-Match` against the target, and PUT means "replace this resource's state", but the action URIs have
   no state of their own.
   - Preferred fix: forms whose target is the reply itself (PATCH with `application/merge-patch+json`).
   - Alternative: document in the `hr` profile that action resources share their parent's validator.

   The entry's POST to `/actions/tell-me-when-renewable` with the entry's ETag has the same issue.
9. **The 401s lack `WWW-Authenticate`, and their problem bodies lack `type`.** RFC 9110 requires the header. Also:
   - the unsigned request and the wrongly signed one get the same title, "The signature does not verify". An
     unsigned request should say a signature is *required*;
   - add `Accept-Signature` (RFC 9421 §5.1), naming the components and the `keyid` expected.
10. **Responses do not say which profile they conform to.** Requests send `Accept-Profile: <…/hal-schema-forms>`,
    but answers carry plain `Content-Type: application/hal+json`, with no `Content-Profile`, no `profile`
    parameter and no `Link: rel="profile"`. SPEC §6.1 requires the profile on the media type. Self-descriptive
    messages mean the answer has to say what it is.
11. **The `hr:manifest` is a fingerprint index, not the offered forms, and that is by design.** It lists
    fingerprints for every affordance, including `library:renew`, `library:return` and `library:report-problem`, which
    are not offered at the entry. That is fine, as long as the profile says the manifest means "known affordances,
    by fingerprint", not "what you may do now". What is missing is the hypermedia path from a fingerprint to its
    schema. The service index should link to an affordance set resource (the L2 level), which in turn links to each
    schema by its `$id`. Then a client with a registry miss *follows a link* to the schema instead of knowing where
    schemas live.
12. **Still no back-link, and the watch is a dead end.** The reply's links are `self`, `curies` and `hr:unavailable`.
    The watch offers no forms (`_forms: {}`) and links only to Ana's reply and keys. The `hr:in-reply-to` proposal
    in §5 stands, and finding 4 adds a second reason for it. A future compensating form (cancel the watch) would
    also live on the watch.
13. **Smaller items:**
    - The library's `Authorization: Bearer` token for Ana's reply has no visible origin. If it was configured out
      of band, either convey the credential in the envelope or rely on the signature alone.
    - The trace records the library's requests as `/replies/r-7f3`, not the absolute `https://caller.example/...`
      it was given.
    - The watch says `standing` and the reply says `state` for the same idea.
    - `service-desc` → OpenAPI is fine as documentation only.
    - The `tell-me-when-renewable` schema lacks the `$schema` keyword the other forms have.

---

## 1. The scenario in one picture

```
Ana (originator)                                  Library (recipient)
----------------                                  -------------------
                     GET /  (entry, 9 forms)
                     ───────────────────────────▶
                     POST watch (reply = r-7f3, key)
                     ───────────────────────────▶  201 Location: /watches/1
/replies/r-7f3   ◀─── library becomes the client ───
  GET  (ETag + forms: progress, completed, failed)
  act  hr:progress 0%, 50%
  act  hr:completed (signed)        → reply now offers 0 forms
  act  hr:completed again, same     → 200 (idempotent replay)
  act  hr:completed, different      → 409, "hr:completed is not offered now"
```

The reply resource lives on Ana's side, and Ana's server publishes the forms the library must use to answer.
This is the core RISE idea: the originator's own form *is* the callback template.

---

## 2. Fielding's six rules, one by one

| # | Fielding's rule (paraphrased) | Verdict | Notes |
|---|---|---|---|
| 1 | Don't depend on a single protocol; URIs identify, they don't dictate the protocol. | **Meets** | Nothing in the run requires anything outside HTTP plus standard headers. |
| 2 | Don't change the protocol; only fill in underspecified bits (methods, link relations, headers) in a standardized way. | **Mostly meets; watch it** | Conditional requests (ETag / `If-Match` → 412 / 428) are standard. Signatures should be RFC 9421 HTTP Message Signatures, not a custom header. SPEC §6.2 still talks about a "manifest hash in the request", a private extension the run no longer seems to need. |
| 3 | **Spend almost all descriptive effort on media types and extension relation names.** | **The gap** | The `hr:` relations are used but not yet *defined* at a resolvable address. Some form schemas are bare `object`. SPEC §7.5's vocabulary no longer matches what the run uses. See §3. |
| 4 | Don't define fixed resource names or hierarchies; the server controls its namespace. | **Meets, with one check** | 21/22 addresses came from links, form targets or `Location`; 1 is the entry `/`. **Check:** "with reply r-7f3" must be a full URI that Ana hands over, never an id the library turns into `/replies/{id}`. |
| 5 | No "typed resources" significant to the client; only media types and relations matter. | **Meets** | The library never needs to know a resource is a "Reply". It needs only the `hr:` relations on the forms it finds there. |
| 6 | Enter with only a bookmark and standardized media types / relations; every transition comes from the representation. | **Meets in shape; depends on #3** | Ana's bookmark is `/`. The library's bookmark is the `reply-to` URI, which is correct. But "standardized relations" means the meaning of `hr:completed` must be publicly defined. Today that meaning lives in the script and in our heads. |

**Bottom line.** The *interaction mechanics* are hypertext-driven:
- every address is handed over;
- every act is a form the server offers;
- refusals explain themselves (`hr:unavailable`, the 409 reason).

Where the run does **not** yet meet Fielding is rule 3, which he called the one that matters most. The descriptive effort has gone into the code and the test script, not into published media-type and relation definitions. Fixing that is mostly documentation and schema work, not new protocol.

### 2.1 A litmus test that comes free with the PUT → PATCH change

Rule 2 plus rule 6 imply this: **switching the reply's forms from PUT to PATCH should require zero client code changes.**
- The form carries `method` and `contentType` (e.g. `application/merge-patch+json`).
- The client just does what the form says.

If the other session's change forces edits to the library-side client beyond regenerating the test, something is coupled out of band. Run that change as a conformance test, not just a refactor.

### 2.2 PUT vs. PATCH and the nullable `note`

Both are legitimate. The form just has to declare which one, and the semantics have to be written down in the relation document.

- **PUT** replaces the whole representation. Every optional field must therefore have a defined absent value. "`note` is nullable and defaults to `null` when omitted" makes a PUT *complete*. That is what makes step 15 (same result again → 200) honestly idempotent rather than accidentally so.
- **PATCH** with JSON Merge Patch (RFC 7396) suits `hr:progress`, which changes only `percent` (and maybe `message`). Merge Patch already defines "`null` means remove", so the `note` question is answered by the standard.

Suggested split:
- **PATCH** for `hr:progress`, which repeats and is partial.
- **PUT** (or POST to a form target) for the terminal acts `hr:completed` and `hr:failed`. They write a final, whole result once.

Either way, keep `If-Match` mandatory, so 428 when it's missing and 412 when it's stale.

### 2.3 The replay case needs a sentence in the spec

After step 13 the reply offers **0 forms**, yet step 15 (same result again) gets 200. That conflicts with SPEC §6.2 "Reject transitions not present in the current manifest" unless the spec says why. Suggested wording for the `hr:completed` document:

> Repeating an `hr:completed` whose content is identical to the recorded result is not a transition; the server
> SHALL answer 200 with the current representation and change nothing. A different result after completion is a
> transition that is not offered: 409, with an `hr:unavailable` reason.

---

## 3. Rule 3 in practice: what "descriptive effort in the media type" means for RISE

### 3.1 CURIEs must resolve to definitions

Per RFC 8288, an extension relation type is a URI. `hr:completed` is shorthand for one through the HAL `curies` link:

```json
"_links": {
  "curies": [{
    "name": "hr",
    "href": "https://w3id.org/heart-rise/rels/{rel}",
    "templated": true
  }]
}
```

Each expanded address (e.g. `…/rels/completed`) should serve one document, ideally by content negotiation:

- `text/html` / `text/markdown` for people and for the reasoning step of an LLM client;
- `application/schema+json` for the **normative minimum** request schema.

Use the `w3id.org/heart-rise` names in responses and redirect them to GitHub Pages. The names stay put if the hosting ever moves. Never put `github.com/.../blob/main/...` addresses in responses: they change with branches.

### 3.2 What each relation document must say

The same template for every `hr:` relation:

1. **Meaning.** One sentence, in protocol terms, not domain terms.
2. **Where it appears.** Which resources offer it, and in which states.
3. **Act.** Method(s) allowed, content type(s), whether `If-Match` is required.
4. **Minimum request schema.** Required fields; how extension fields are treated (*must-ignore unknown fields*).
5. **Outcomes.** Each status code and what it means *for this relation* (200, 201, 401, 409 + `hr:unavailable`, 412, 422, 428).
6. **Idempotency and replay rule.** See §2.3.
7. **Links it produces.** E.g. after `hr:completed` the reply gains `hr:in-reply-to` (see §5).

### 3.3 Vocabulary to define (and to reconcile with SPEC §7.5)

SPEC §7.5 lists `heart:agent`, `reply-to`, `error-reply`, `progress`, `heart:result`. The run uses `hr:`-prefixed relations: `hr:progress`, `hr:completed`, `hr:unavailable`, and presumably `hr:failed`. Pick one prefix and one list. Proposed core RISE vocabulary:

| Relation | Kind | On which resource | Purpose |
|---|---|---|---|
| `hr:reply-to` | link (in the request envelope) | the work resource (`/watches/1`) | The originator's reply resource: the recipient's bookmark for answering. |
| `hr:progress` | form | reply | Non-terminal status update. Repeatable. |
| `hr:completed` | form | reply | Terminal success with a result. |
| `hr:failed` | form | reply | Terminal failure with a problem description. |
| `hr:unavailable` | link | any | Explains why an act is not currently offered. |
| `hr:in-reply-to` | link (new, see §5) | reply | Back-link to the recipient's work resource. |
| `hr:agent` | link | any | Agent directory entry (SPEC §7.6). |
| `hr:result` | link/form | polling resource | Poll mode only (SPEC §7.7). |

Prefer IANA-registered relations wherever one fits, because Fielding asks for *standardized* relations first:
- `monitor` (RFC 5989): a resource used to watch changes in another resource. It describes a "watch" almost exactly.
- `replies` (RFC 4685): from a context to a resource that is a reply to it.
- `via` (RFC 4287): the source of the context's information.

Define an `hr:` relation only where no registered one carries the meaning.

---

## 4. "Some schemas are just `object`": flexibility vs. prescription

Separate the **control data** RISE needs to work from the **domain payload** the originator wants back:

| Layer | Who defines it | How strict |
|---|---|---|
| RISE control data (correlation, status, timing, back-link, signature) | The RISE profile, in the relation documents | **Prescriptive.** Fixed required fields. This is what makes async correlation work between strangers. |
| Domain result (e.g. "is loan 3310 renewable, and by when?") | **The originator**, in its own reply form | **Specific but open.** Named properties, `additionalProperties: true`, must-ignore unknowns. |

A bare `"result": { "type": "object" }` lets the originator off too lightly. Ana's server *publishes* the `hr:completed` form on her own reply resource, so she knows exactly what she wants back and can say so. That is SPEC §7.2's `template-fields` idea, done the hypermedia way: the form *is* the template.

```json
"result": {
  "type": "object",
  "required": ["renewable"],
  "properties": {
    "renewable": { "type": "boolean" },
    "renewBy":   { "type": "string", "format": "date" }
  },
  "additionalProperties": true
}
```

This keeps the loose coupling you want: extra fields are allowed and ignored. The reasoning step also gets what it needs to fill the form correctly the first time.

### 4.1 Minimum control fields the run is probably missing

| Form | Field | Required | Why |
|---|---|---|---|
| all three | `_links.hr:in-reply-to` (URI) | first act yes; then must match | Back-link to the recipient's work resource (§5). |
| `hr:progress` | `percent` (0–100) | yes | Already present per the run. |
| `hr:progress` | `message` | no (nullable) | Human/LLM-readable status. |
| `hr:progress` | `expectedBy` (date-time) | no | Lets the originator decide when fallback (§7.6) should fire. |
| `hr:completed` | `result` | yes | Domain payload, shaped by the originator (above). |
| `hr:completed` | `note` | no (nullable, default `null`) | Per the other session; required for honest PUT idempotency. |
| `hr:failed` | `problem` | yes | Use **RFC 9457 Problem Details** (`type`, `title`, `detail`, `instance`), a standard shape instead of an invented one. The run's "no record of the hold" maps directly. |
| `hr:failed` | `retryable` | no | Tells the originator whether fallback or retry makes sense. |

Correlation needs **no** separate correlation-id field. The reply resource's URI *is* the correlation id. That design choice is worth keeping and worth saying out loud in the spec.

Signatures and keys belong in headers (RFC 9421 `Signature` / `Signature-Input`), not in the body. The `keyid` should be resolvable or announced by link, e.g. the watch request carries a link to the originator's key set. Then no key is ever known out of band.

---

## 5. Bidirectional linkage: reply ⇄ work resource

### 5.1 The idea

Today the link points one way. The library's watch knows Ana's reply (`reply-to`), but Ana's reply knows nothing about the watch. While Ana's reply sits waiting, nothing on it says *who is working on it* or *where to look*.

Proposal: when the library first acts on the reply (its first `hr:progress`, or the terminal act if there is no progress), it submits its own work-resource URI. It sends it the same way Ana sent `hr:reply-to`, as a HAL link in the body:

```json
{ "percent": 0, "note": null,
  "_links": { "hr:in-reply-to": { "href": "https://library.example/watches/1",
                                  "type": "application/hal+json" } } }
```

The reply then exposes it as a link:

```
/watches/1  ──hr:reply-to──▶  /replies/r-7f3      (given by Ana when creating the watch)
/watches/1  ◀─hr:in-reply-to─ /replies/r-7f3      (given by the library on its first act)
```

This is not a redundant cycle. Each side holds the other's address because the other side *handed it over*, so rule 4 is preserved: nobody builds a URI.

### 5.2 Naming

The relation names should read correctly from the resource that carries the link:

| Carrier | Relation | Reads as |
|---|---|---|
| work resource (`/watches/1`) | `hr:reply-to` | "Send your reply to …" |
| reply resource (`/replies/r-7f3`) | `hr:in-reply-to` | "This is a reply to the work at …" |

`in-reply-to` borrows its meaning from email (RFC 5322) and Atom threading (RFC 4685). Both use it for exactly this direction, so readers will get it right. Avoid `reply-from`: it is ambiguous whether it points to the replier or the original request.

For standard relations, the watch could also be offered on the reply as `monitor` (RFC 5989): the originator can follow it to watch the work progress directly. Keep `hr:in-reply-to` as the precise RISE meaning.

### 5.3 Rules for the back-link

- Supplied as `_links.hr:in-reply-to` in the first act. On later acts it MUST equal the recorded value. A different value is 409 with an `hr:unavailable` reason.
- Accepted only on a correctly signed act. Unsigned or wrongly signed acts are already 401, as in steps 9 and 11.
- Once recorded, it appears in the reply's `_links` as `hr:in-reply-to` in every state, including after completion.

### 5.4 What it buys

- **Crash recovery on Ana's side.** After a restart, Ana's reply resource leads straight back to the work, with no lookup table.
- **Observability.** Follow the back-link to see the library's own view (state, history).
- **Prerequisite for compensation.** See §6.

---

## 6. Later: history and compensating actions (parked)

Noted, not designed:

- **History.** IANA already has `version-history`, `predecessor-version` and `latest-version` (RFC 5829). The reply's progress trail can be exposed with those instead of new relations.
- **Compensating actions.** The library's work resource could offer a compensating form (e.g. `hr:compensate` / cancel) while undo is still possible. Its absence plus `hr:unavailable` explains why not. Ana can only *find* that form through the back-link in §5, so the back-link is the foundation compensation will need later.

---

## 7a. Short list: one line each on why it matters to Fielding

1. **w3id → GitHub Pages redirects, plus a page for every `hr:` relation:** puts the descriptive effort into published relation definitions, which is rule 3.
2. **One absolute `hr` CURIE in every response that uses it:** each relation then has one global name, the URI that RFC 8288 makes the real name.
3. **Forms and client agree on content type:** the client does what the form says, which is rule 6's "every transition comes from the representation".
4. **Name the result fields in Ana's `hr:completed` form:** the library learns what to send from the hypermedia, not from out-of-band knowledge.
5. **Nullable `note`, and completion clears progress:** a PUT then means exactly what HTTP says, a full replacement, so nothing about the protocol is reinterpreted.
6. **Keep the failure problem in the reply:** the resource's representation tells the whole truth about its state, so no side channel is needed.
7. **Answers state their profile:** messages describe themselves, so a client never has to assume what it received.
8. **Service index → affordance set (L2) → schemas by `$id`:** a registry miss is solved by following links, not by knowing where schemas live.
9. **PATCH on the reply itself for progress:** `If-Match` and the method apply to the resource they name, so there is no private reading of standard HTTP (rule 2).
10. **401 with `WWW-Authenticate` and `Accept-Signature`:** the server says how to authenticate, using standard headers, instead of the client knowing in advance.
11. **Library publishes its own `hr:keys`:** the verifying key is found by following links, not arranged out of band.
12. **`hr:in-reply-to` back-link:** each side holds the other's address only because it was handed over, which is rule 4, and it opens the path to keys and future compensation.
13. **SPEC.md matches the run:** the written contract describes the real media type and vocabulary, the documentation Fielding says should carry the weight.

## 7. To-do list

Ordered by how much each item matters for Fielding's rules. Each § reference is to the finding that explains it.

**Vocabulary and self-description (rule 3)**

1. **Make the vocabulary resolve.** Keep `https://w3id.org/heart-rise/...` as the permanent names in every response.
   Point them at GitHub Pages with a w3id redirect: a pull request adding `heart-rise/.htaccess` to the w3id.org
   repository. Responses never mention `github.io`, so hosting can move later without renaming a single relation.
2. **Write one page per relation** (§3.2) at `…/rels/{rel}`:
   - relations: `reply-to`, `keys`, `progress`, `completed`, `failed`, `unavailable`, `in-reply-to`;
   - non-relation `hr:` members: `integrity`, `manifest`.

   Also write the profile page `…/hal-schema-forms` and the problem-type pages under `…/problems/`.
3. **One `hr` CURIE, absolute, declared in every response that uses `hr:`** (§0 findings 1–2). Remove the relative
   `/profiles/hr#{rel}`, add `hr` to the entry, and make the 409's `rel` absolute.
4. **Say the profile in every answer** (§0 finding 10): `Content-Profile`, or a `profile` parameter on the
   media type, or `Link: rel="profile"`.
5. **Link the service index to an affordance set that links to every schema by `$id`** (§0 finding 11).

**Forms say what to send, and the client obeys (rules 3 and 6)**

6. **Fix the content-type mismatch** (§0 finding 3). The forms should say `application/hal+json`, and
   `application/problem+json` for `hr:failed`. Then make the client send what the form says, not what it assumes.
7. **Name the result fields** in Ana's `hr:completed` form instead of a bare `object` (§0 finding 5, §4).
8. **Make `note` nullable with default `null`, and define what completion does to progress** (§0 finding 6).
9. **Keep the failure reason in the reply, and make `hr:failed` full RFC 9457** (§0 finding 7).

**Plain HTTP, no private readings (rule 2)**

10. **Fix `If-Match` targeting** (§0 finding 8): progress as PATCH on the reply itself, with
    `application/merge-patch+json`. Use the switch as the §2.1 conformance test: no client code change.
11. **401s:** add `WWW-Authenticate` and `Accept-Signature`, give the body a `type`, and distinguish "signature
    required" from "signature does not verify" (§0 finding 9).
12. **Explain where the library's bearer token comes from**, or drop it in favor of the signature (§0 finding 13).

**Linkage (rule 6, and the future)**

13. **Library advertises its own `hr:keys`** on its entry and each watch. Define what Ana's `hr:keys` is for
    (§0 finding 4).
14. **Add the `hr:in-reply-to` back-link** (§5), sent in the first callback's `_links`. Add run steps for:
    - the link appearing after the first act;
    - a mismatched one refused with 409.

**Spec and trace hygiene**

15. **Reconcile SPEC.md with the run:**
    - the error table: 409 = not offered, 412 = stale, 428 = no validator;
    - 201 for the watch;
    - the vocabulary in §3.3;
    - the replay rule in §2.3;
    - RFC 9421 signatures and `hr:integrity` in place of the manifest-hash-in-request.
16. **Record absolute URIs in the trace** for calls to `caller.example` (§0 finding 13).
