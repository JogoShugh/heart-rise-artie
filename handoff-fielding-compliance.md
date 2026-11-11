# Handoff: bring the RISE Library/Ana run into Fielding compliance

**For:** the coding session that owns the server, client and run-report code behind the run
*RISE: The Library and Ana, End to End* (published report: https://claude.ai/artifact/CWnmiNG2ufrXmBBmMtjFmP;
critic edition: https://claude.ai/artifact/QV3MWoht3xbraeGDDjVAeh).

**Why:** the run is hypertext-driven in its *mechanics*: one bookmark, every address handed over, every act an
offered form, every refusal explained. It is not yet hypertext-driven in its *descriptions* (Fielding 2008, rule 3).
It also bends standard HTTP in a few places (rule 2). Full reasoning is in `rise-fielding-2008-analysis.md` in the
`heart-rise-artie` repo. This document is the work list.

**How this was derived:** from the complete trace embedded in the published report (every request, answer, form
and schema). The implementation itself was not visible, so file names and class names are yours to find. Every item
below states the *observable* behavior to change and how to check it.

---

## Ground rules for the work

1. **Never let the client build a URI.** Every address comes from a link, a form target, or a `Location`. Tests
   select by relation name, never by path.
2. **The client obeys the form.** Method, target and content type come from the form, or from the profile's stated
   default when the form omits one. No hard-coded `Content-Type` anywhere in the client.
3. **Keep the 22 existing steps' story**: progress, two refusals, a signed result, its replay, a refused different
   result, a failure. Update their expectations where this work changes them, and add the new steps listed under
   "Acceptance".
4. **Do not touch the vocabulary hosting yourself.** The w3id redirect is a pull request the owner makes to the
   w3id.org repository. Produce the relation pages as source files; the owner publishes them.

---

## Phase 1: vocabulary and self-description (rule 3)

**1.1 One absolute `hr` CURIE, everywhere `hr:` appears.**
- *Now:* the reply declares two CURIEs named `hr`: `/profiles/hr#{rel}` and `https://w3id.org/heart-rise/rels/{rel}`.
  The entry uses `hr:integrity` and `hr:manifest` and declares no `hr` CURIE.
- *Change:* every response that uses an `hr:` name declares exactly one CURIE,
  `{"name": "hr", "href": "https://w3id.org/heart-rise/rels/{rel}", "templated": true}`. Remove the relative one.
  `library` stays server-local.
- *Also:* the 409 problem body's `"rel": "/profiles/hr#completed"` becomes `"https://w3id.org/heart-rise/rels/completed"`.

**1.2 Answers state their profile.**
- *Now:* the request sends `Accept-Profile: <https://w3id.org/heart-rise/hal-schema-forms>`; the answer carries
  plain `Content-Type: application/hal+json`.
- *Change:* every HAL answer adds:
  - `Content-Profile: <https://w3id.org/heart-rise/hal-schema-forms>`
  - `Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"`
  - `Vary: Accept, Accept-Profile` (plus `HX-Request` once the htmx variant exists, see Phase 6).

**1.3 The fingerprint manifest gets a link path to its schemas (the L2 level).**
- *Now:* the entry's `hr:manifest` lists fingerprints for every known affordance (including `library:renew`,
  `library:return` and `library:report-problem`, which are not offered at the entry). This is intended and stays.
- *Change:* the entry links to an affordance-set resource. That resource links to each schema by its `$id`, keyed
  by fingerprint. A client with a registry miss follows links to the schema and never knows a schema path in
  advance. The relation name for the affordance set is an **open decision** (see the end).

**1.4 Relation pages, as source.** Write one Markdown page per term under `docs/rels/` (or the repo's docs
convention), ready for GitHub Pages:
- **Relations:** `reply-to`, `keys`, `progress`, `completed`, `failed`, `unavailable`, `in-reply-to`, `cancel`.
- **Members:** `integrity`, `manifest`.
- **Also:** the profile page `hal-schema-forms` and problem-type pages `not-offered`, `signature-required`,
  `invalid-signature`, `no-hold-record`.

Each relation page states:
1. its meaning;
2. where it appears and in which states;
3. its method, content type, and whether `If-Match` is required;
4. its minimum schema;
5. what each status code means for it;
6. its replay and idempotency rule;
7. the links it produces.

---

## Phase 2: forms tell the whole truth (rules 3 and 6)

**2.1 Content type: one default, stated exceptions, an obedient client.**
- *Now:* every form says `contentType: application/json`, but the client sends `application/hal+json`, and
  `application/problem+json` for the failure.
- *Change:*
  - The profile page states the default: request bodies are `application/hal+json` unless a form says otherwise.
  - Forms omit `contentType` when it is the default. The `failed` form states `application/problem+json`.
    Reply acts state `application/merge-patch+json` (see 3.1).
  - The client sends exactly the form's content type, or the profile default when the form omits it.

**2.2 The `completed` form names the originator's fields.**
- *Now:* `result: {type: object}` with nothing inside; the library sends `loan` and `renewableFrom` anyway.
- *Change:* Ana's reply resource publishes the shape it wants: `state` const `completed`; `result` with
  `loan` (integer, required) and `renewableFrom` (string, format date, required); `additionalProperties: true`.
  The library's client fills the form from the schema. It must not carry its own idea of the result's shape.

**2.3 `progress`: nullable note.** `percent` is an integer from 0 to 100, required. `note` is
`type: ["string", "null"]`, `default: null`. Omitting `note` clears it.

**2.4 `failed`: full RFC 9457.** Content type `application/problem+json`. The schema requires `type` (URI) and
`title`; `detail` is optional; `state` is const `failed`.

---

## Phase 3: plain HTTP, no private readings (rule 2)

**3.1 Reply acts PATCH the reply itself.**
- *Now:* `PUT /replies/{id}/actions/{progress|completed|failed}`, carrying the *reply's* ETag in `If-Match`.
  HTTP evaluates `If-Match` against the target, and PUT replaces the target's state, which an action URI does not have.
- *Change:*
  - All three reply forms target the reply's own URI, with method `PATCH` and `application/merge-patch+json`
    (`failed`: `application/problem+json` body, applied as the failure patch). `If-Match` is the reply's ETag.
  - Each form fixes `state` with a const. The server reads intent from `state`.
  - The reply advertises `Accept-Patch: application/merge-patch+json, application/problem+json`.
  - Retire the `/actions/*` targets on the reply: answer `404` or `405`, and nothing links to them.

**3.2 Creating a watch does not borrow the entry's version.** Drop `If-Match` from the POST to
`tell-me-when-renewable`; a new watch does not depend on the entry's ETag. If the server requires a precondition
there, document why on the relation page instead.

**3.3 Use the ETag the last answer gave.** The library acts on the ETag returned by its previous act, with no
re-read in between. Keep the explicit read steps in the run script only where they test something.

**3.4 Completion and failure leave no stale state.**
- On `completed`: `percent` becomes 100, and `note` becomes `null` unless the act supplies one.
- On `failed`: the problem document stays in the reply's representation, as an embedded `problem` member or
  under `_embedded`. Ana's resource must show *why* it failed.

**3.5 Replay rule (behavior stays, documentation added).** An identical completed patch after completion gets
`200` with the same ETag and records nothing. A different one gets `409` `not-offered`. Write this on the
`completed` page.

**3.6 401s that teach.**
- *Now:* `401` with `{title, status}`, no `WWW-Authenticate`, and the same title for unsigned and badly signed.
- *Change:*
  - Body: RFC 9457 with `type` `…/problems/signature-required` (no signature) or `…/problems/invalid-signature`
    (does not verify).
  - Headers: `WWW-Authenticate` (scheme per the **open decision** below) and
    `Accept-Signature: sig1=("@method" "@target-uri" "content-digest" "if-match");keyid="library-2026-11";alg="ed25519"`.

---

## Phase 4: keys and the back-link (rules 4 and 6)

**4.1 The library publishes its own keys.** The entry and each watch carry `hr:keys` pointing to the library's
JWK Set, which includes `library-2026-11`.

**4.2 Ana's `hr:keys` has a defined job.** It is the key set *Ana* signs with. Ana signs her watch POST with RFC 9421
(same components, plus `Content-Digest`). The library verifies the envelope against Ana's keys and answers `401`
if the signature is missing or invalid.

**4.3 The `hr:in-reply-to` back-link.**
- **Sending it:** the library's first act on a reply includes
  `"_links": {"hr:in-reply-to": {"href": "<absolute watch URI>", "type": "application/hal+json"}}` in its patch body.
- **Recording it:** Ana's reply records it and exposes it in `_links` from then on, in every state.
- **Changing it:** later acts that carry a different `hr:in-reply-to` get `409`.
- **Trust rule (write it on the relation page):** the back-link must equal the `Location` from the 201 that created
  the watch. Ana verifies the library's signatures with keys from *that* watch's `hr:keys`, never from a link the
  signer supplied.

---

## Phase 5: the next layer

**5.1 Cancel on the watch (compensation's first form).**
- While the watch is `awaiting`, it offers `hr:cancel`: a `PATCH` setting `standing` to `cancelled`.
- After the library answers, `hr:cancel` disappears, and the watch's `hr:unavailable` explains why.
- A domain with side effects would offer a compensating form here instead. This is not needed for the library run.

**5.2 History on the reply.**
- The reply links `version-history` (an IANA-registered relation) to a list of its changes. Each entry has:
  - its time;
  - the ETag it replaced;
  - the retained signature input and signature;
  - a `predecessor-version` link (also registered).

---

## Phase 6: more than one format, without repetition

1. **Proactive negotiation** on every resource:
   - HTML for `Accept: text/html` (the htmx renderer);
   - HAL with the heart profile;
   - HAL-FORMS (`application/prs.hal-forms+json`, forms under `_templates`).

   Answers carry `Content-Type`, `Content-Profile`, and `Vary: Accept, Accept-Profile, HX-Request`.
2. **List alternates once, not on every body.**
   - The entry's `_links.alternate` is a list with one item per format (`type`, and `profile` where needed).
   - Every answer carries the same alternates as `Link: <…>; rel="alternate"; type="…"; profile="…"` headers.
   - HTML pages add `<link rel="alternate" type="application/hal+json" href="…">` in the head.
3. **Teach on failure.**
   - `406` for an unproducible `Accept`, listing the alternates.
   - `415` for an unsupported request body, with `Accept-Patch` or `Accept-Post`.
4. **The envelope can offer both reply formats.** `hr:reply-to` may be a list of links to the same address, one per
   profile (`hal-schema-forms`, `hal-forms`). The library picks the one it understands.

---

## Phase 7: trace and report

- **Absolute addresses in the trace.** Record absolute URIs for calls to `caller.example`. The trace currently
  shows `/replies/r-7f3` after the library was handed `https://caller.example/replies/r-7f3`.
- **Response headers in the summary lines.** The report's answer line should also show `Content-Profile`,
  `WWW-Authenticate`, `Accept-Signature` and `Accept-Patch`, so the new behavior is visible without opening bodies.

---

## Acceptance: run steps to add or change

Everything below runs as scripted steps in the same run, with expectations written before each step.

- [ ] **Entry.** Answer has `Content-Profile`. There is exactly one `hr` CURIE, and it is absolute. It links to the
      affordance set, and a schema can be reached from there by following links.
- [ ] **Unsigned watch POST from Ana → 401** with `signature-required`, `WWW-Authenticate` and `Accept-Signature`.
- [ ] **Signed watch POST → 201** with `Location`. The watch carries the library's `hr:keys` and `hr:cancel`.
- [ ] **Reply forms all target the reply URI with PATCH.** Progress at 0% carries `hr:in-reply-to`, and the reply
      then shows the back-link.
- [ ] **Progress with a different `hr:in-reply-to` → 409.**
- [ ] **Badly signed result → 401 `invalid-signature`; unsigned result → 401 `signature-required`**, both with
      `Accept-Signature`.
- [ ] **Completed → 200.** The reply shows `percent: 100`, `note: null`, the named result fields and the back-link,
      with zero forms and `hr:unavailable`.
- [ ] **Identical replay → 200, same ETag. Different result → 409** with an absolute `rel`.
- [ ] **Cancel after the answer → 409** with a reason. On the second watch, cancel while awaiting → 200.
      (Add a third reply if the failure path must stay intact.)
- [ ] **Failed → 200.** The reply shows the embedded problem with `type` and `title`.
- [ ] **History** lists every change with `predecessor-version` links.
- [ ] **Formats.** `Accept: text/html` → HTML. `Accept: application/prs.hal-forms+json` → HAL-FORMS with
      `_templates`. An unsupported `Accept` → 406 listing the alternates. A PATCH as `application/json` → 415 with
      `Accept-Patch`.
- [ ] **Conformance litmus.** Change one reply form's method or content type in the server only. The client passes
      every step with no client code change.
- [ ] **Hypermedia tile stays green:** every address after the entry comes from the server.

---

## Open decisions (ask the owner; do not guess)

1. **401 scheme.** Option A: `WWW-Authenticate: Signature realm="…"`, a declared extension documented on the
   profile page (candidate for IANA registration). Option B: keep `Bearer` as the formal scheme, with the signature
   as the proof. If B, say where the library's bearer token for Ana's reply comes from. Today its origin is not
   visible in the trace.
2. **Relation name for the L2 affordance set** linked from the entry.
3. **Reply acts:** PATCH on one URI with `state` consts (recommended above), or separate readable sub-resources
   (`/result`, `/problem`) written with PUT.
4. **Embedded failure shape:** a `problem` member, or `_embedded.problem`.

## Out of scope here

- Publishing the w3id redirect and GitHub Pages site. That is the owner's action; this work only produces the pages.
- Multi-party RISE and agent-directory fallback (SPEC §7.6).
- Reconciling `SPEC.md` in `heart-rise-artie`. That follows once this lands:
  - error table: 409 not offered, 412 stale, 428 no validator;
  - 201 for the watch;
  - the `hr` vocabulary;
  - the replay rule;
  - RFC 9421 and `hr:integrity` replacing the manifest-hash-in-request.
