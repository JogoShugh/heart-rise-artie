# RISE vs. Fielding 2008: Is the Library/Ana Run Hypertext-Driven?

Source material:

- The run report *RISE: The Library and Ana, End to End* (22 steps, 22 as expected).
- SPEC.md §6–7 and §13.
- Roy Fielding, [REST APIs must be hypertext-driven](https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven) (2008).

Limits: the saved report holds only step summaries, not request or answer bodies. Statements about specific
schemas (e.g. fields typed as bare `object`) come from discussion, not from reading the bodies. Each such
statement should be checked against the implementation.

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
    "href": "https://jogoshugh.github.io/heart-rise/rels/{rel}",
    "templated": true
  }]
}
```

Each expanded address (e.g. `…/rels/completed`) should serve one document, ideally by content negotiation:

- `text/html` / `text/markdown` for people and for the reasoning step of an LLM client;
- `application/schema+json` for the **normative minimum** request schema.

A stable host (GitHub Pages or a domain) is better than `github.com/.../blob/main/...`, which changes with branches.

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
| all three | `inReplyTo` (URI) | first act yes; then must match | Back-link to the recipient's work resource (§5). |
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

Proposal: when the library first acts on the reply (its first `hr:progress`, or the terminal act if there is no progress), it submits its own work-resource URI. The reply then exposes it as a link:

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

- Supplied in the `inReplyTo` field of the first act. On later acts it MUST equal the recorded value. A different value is 409 with an `hr:unavailable` reason.
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

## 7. To-do list

1. **Write the relation documents** (§3.2) for `hr:reply-to`, `hr:progress`, `hr:completed`, `hr:failed`, `hr:unavailable`, `hr:in-reply-to`. Publish them at the CURIE address.
2. **Add the `curies` link** to every HEART response and make the profile URI dereference to a profile document that lists the vocabulary.
3. **Replace bare `object` schemas** on the reply's forms with originator-specific, open schemas (§4).
4. **Add `inReplyTo`** to the three reply forms, and the `hr:in-reply-to` link to the reply (§5). Add run steps for:
   - the back-link appearing after the first act;
   - a mismatched back-link being refused with 409.
5. **Adopt RFC 9457 Problem Details** for `hr:failed` and for 4xx bodies.
6. **Reconcile SPEC.md with the run:**
   - SPEC §6.2 error table: the run uses 409 = not offered, 412 = stale, 428 = no validator; SPEC says 403 / 409 / 428-with-manifest-hash.
   - SPEC §7.4 says 202 Accepted; the run uses 201 for the watch, which is better because the watch *is* a resource.
   - SPEC §7.5: replace the old vocabulary with the table in §3.3.
   - Write down the replay rule (§2.3).
7. **Use the PUT → PATCH change as a conformance test** (§2.1): no client code change should be needed.
8. **Confirm** that the reply is passed to the library as a full URI, not an id (rule 4 check in §2).
