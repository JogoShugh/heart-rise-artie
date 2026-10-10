# Hack log

## 2026-10-04

State at the end of the session: everything is committed locally and nothing
is in progress. The Fielding review is published as a private artifact:
https://claude.ai/artifact/NiZdhyhpwHrhdpDgkMJgWr

Waiting on the author:

- **EVAL-L6:** answer the 5 open questions at the end of `spec/EVAL-L6.md`
  (domains, MacBook chip and RAM, consent to install Ollama, API keys, intent
  review).
- **L6 real-model run:** needs `ANTHROPIC_API_KEY`. Until then, L6 is proven
  only by scripted reasoners, not by inference.
- **RISE:** not built.
- **Rule 4 tension (Fielding review):** resolved 2026-10-08. The client no
  longer reads the `/sha256-` path; integrity comes from the linking
  representation (see the 2026-10-08 entries below).

Other tensions the Fielding review recorded (no action decided):

- **Rule 1:** the spec is an HTTP-only binding.
- **Rule 2:** the [0.5] `If-Match` narrowing, and `Accept-Profile` is still a
  W3C draft.
- **Typed relations:** action names (`waterCell`, HAL-FORMS `default`) are not
  standardized relations. L6 Reason covers the gap by reading names.

## 2026-10-08: Repr-Digest plan (replaced)

Status: **replaced the same day** by link integrity (Subresource Integrity
style), with `Repr-Digest` optional. See `spec/HAL-SCHEMA-FORMS-HR.md` Rule 2
and SPEC §6.5. Kept as history; the corrections below still hold.

Original status: **draft plan, waiting for the author's go.** Nothing has changed in
the spec or the code. This plan resolves the Rule 4 tension listed above.

### Source

External feedback asked for the following:

- ARTIE treats every URI as opaque. The client removes any logic that reads
  `sha256-`, a CID (`Qm…`) or other meaning from a path.
- The client checks the integrity of Layer 2 (affordance sets) and Layer 3
  (schemas) only through the RFC 9530 `Repr-Digest` response header.
- The client hashes the raw response bytes before any JSON parsing and before
  the payload reaches the L6 reasoner.
- The cache key stays the full URI the server gave. The client never uses the
  computed hash as a key.
- A missing header or a mismatched digest on a Layer 2 or Layer 3 fetch
  throws at once. There is no fallback.

The direction is right, and it matches the fix already proposed above. The
feedback has defects, corrected below.

### Corrections to the feedback

1. **Header syntax.** RFC 9530 values are RFC 8941 dictionaries whose values
   are byte sequences, wrapped in colons:
   `Repr-Digest: sha-256=:89+HpTM6JCwYAPdrnKgSLgjEIBviGDlcJGT6bUY+fO4=:`.
   The feedback's form (`sha-256=Value=`, no colons) is not valid. A client
   built to it would reject a correct server.
2. **Example hashes.** `ValidBase64HashOfPayload=` is a placeholder and can
   never pass. The real SHA-256 of `{"action":"water"}` (18 bytes, no trailing
   newline) is `89+HpTM6JCwYAPdrnKgSLgjEIBviGDlcJGT6bUY+fO4=`.
3. **Compare bytes, not strings.** Decode the header's byte sequence and
   compare it to the computed digest. This avoids false mismatches from
   padding or encoding variants.
4. **Which fetches need a digest.** With opaque URIs, the transport cannot
   tell a Layer 2 or Layer 3 fetch from a Layer 1 fetch. The caller knows: it
   followed the `hr:forms` link (Layer 2) or a schema `$ref` (Layer 3). That
   caller marks the request as needing a digest. The check layer requires the
   header only on marked requests, and verifies any `Repr-Digest` that is
   present on other responses.
5. **Raw bytes.** `HeartResponse.body` is a `String` today, and
   `ContentAddressCheck` re-encodes it as UTF-8. The response must carry a
   `ByteArray`, so the hash covers the bytes as received. Per RFC 9530,
   `Repr-Digest` covers the representation data, including any content
   coding. Hash before any decompression.
6. **The server must change too.** The feedback covers only the client. The
   server must send `Repr-Digest` on Layer 2 and Layer 3 responses, or the
   live-server scenarios fail. SPEC §6.5 needs new text.
7. **Security is weaker, and that should be said.** A digest in the same
   response only detects corruption, because the server vouches for its own
   bytes. Today the linking resource names the hash in the URL, so a
   substituted file fails the check. That pinning is lost. To get it back
   without reading URLs, the link can carry the expected digest as a link
   property, as HTML's `integrity` attribute does (a fork rule). This is a
   separate follow-up.

Already true, no change needed:

- The cache key is the full URL (`CachingTransport.entries[request.url]`).
- The check sits below the cache, so rejected bytes are never stored.
- The server may keep `/sha256-…` paths. That is the server's own naming, and
  the client never reads it.

### Feature (corrected)

```gherkin
Feature: RFC 9530 Repr-Digest verification for opaque URIs

  Rule: The client never reads a URI to check integrity

    Scenario Outline: ARTIE checks a Layer 2 or Layer 3 payload only by its header
      Given the Layer 1 resource links to "<href>"
      When ARTIE fetches "<href>" as a Layer 2 or Layer 3 resource
      And the server answers 200 with body '{"action":"water"}'
      And the server sends Repr-Digest "<digest>"
      Then ARTIE <outcome>

      Examples:
        | href                | digest                                                  | outcome                                          |
        | /schemas/xyz-123    | sha-256=:89+HpTM6JCwYAPdrnKgSLgjEIBviGDlcJGT6bUY+fO4=: | accepts the payload and caches it under the URL  |
        | /ipfs/QmYwAPJzg5    | sha-256=:89+HpTM6JCwYAPdrnKgSLgjEIBviGDlcJGT6bUY+fO4=: | accepts the payload and caches it under the URL  |
        | /schemas/sha256-abc | sha-256=:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=: | refuses with IntegrityException and caches nothing |
        | /any-opaque-path    | (none)                                                  | refuses with StrictHeartException and caches nothing |
        | /any-opaque-path    | md5=:1B2M2Y8AsgTpgAmY7PhCfg==:                          | refuses with StrictHeartException and caches nothing |

    Scenario: A refused payload never reaches the reasoner
```

The last example row covers a header that names only an algorithm the client
does not support.

### Work, in order (under the gates)

1. Feature file first: the scenarios above. Rewrite the `sync-l2.feature`
   scenario "Bytes that do not hash to their address are refused" to use the
   header.
2. Client:
   - `HeartResponse` carries `ByteArray`, with `body` as UTF-8 text derived
     from it.
   - `HeartRequest` carries a "digest required" marker. The Layer 2 and
     Layer 3 fetch paths set it.
   - Replace `ContentAddressCheck` with `ReprDigestCheck`. Delete the
     `/sha256-` regex.
   - Parse the RFC 8941 dictionary. Support `sha-256` and `sha-512`. Use
     `MessageDigest` and compare decoded bytes.
   - Add `IntegrityException` (mismatch) and `StrictHeartException` (missing
     or unsupported). Remove `ContentAddressMismatch`.
3. Server (`server-spring`): send `Repr-Digest` on `/affordance-sets/…` and
   `/schemas/…`, computed from the exact bytes written.
4. Spec: rewrite SPEC §6.5 (integrity by `Repr-Digest`; URIs are opaque to
   the client). Update `CONFORMANCE-GAPS.md`.
5. Run every gate and the live-server run. Commit locally.

Follow-up, separate: pin the digest in the link (correction 7), as a rule in
the HAL Schema Forms fork.

## 2026-10-08: decisions

- **Fork:** keep affordance sets; write a differences-only H.E.A.R.T. profile
  of HAL Schema Forms (`spec/HAL-SCHEMA-FORMS-HR.md`).
- **Template pointers:** forms declare `hr:templatePointers` (JSON Hyper-Schema
  `templatePointers`, RFC 6901 pointers). A variable with no pointer and no
  field falls back to the Hyper-Schema same-name default.
- **Integrity:** the client never reads a digest from a URL. The Layer 1 link
  carries `integrity`; each Layer 2 schema `$ref` carries `hr:integrity`
  (Subresource Integrity syntax, full digest). `Repr-Digest` is sent by the
  server and checked by the client when present, never required.
- **Vocabulary:** say resource or representation, never "page".

Work in progress follows `~/.claude/plans/functional-cuddling-hearth.md`.

## 2026-10-08: done

All four phases of the plan are committed locally:

1. Docs: `spec/HAL-SCHEMA-FORMS-HR.md`, SPEC §6.5 and §6.9.1.
2. Link integrity: `IntegrityCheck` replaces `ContentAddressCheck`; the
   server sends `integrity`, `$ref` + `hr:integrity`, and `Repr-Digest`.
3. `hr:templatePointers` on every shared form; the client fills variables
   from pointers, then fields, then the same-named property.
4. Live check: 12 @live scenarios pass against the Docker server. Journal
   and `server-spring/run/` recaptured (`capture.sh` now follows `$ref`);
   all 10 journal verdicts ok.

Still open:

- **Fielding review artifact:** correct the Rule 6 verdict and mark the
  Rule 4 tension resolved. Needs the author's OK to republish.
- **Not regenerated:** `exhibits/cache-demo/` (its own fixtures and server)
  and `scripts/test-report/` still show the old link shape. Both are
  historical exhibits; regenerate if they are shown publicly.
- **Integrity on old links:** done 2026-10-09. A link with no integrity is
  refused before any request; `ArtieClient(allowUncheckedLinks = true)`
  follows such links for older servers.
- **Later fork rules:** form names as link relations; error format
  (`vnd.error` or `violations`); the entry point.
- **EVAL-L6**, the L6 real-model run, and RISE are unchanged from above.

## 2026-10-08: entry point

Done (`88f0a2c`): `GET /` is the service index, with `searchBeds`
(`/beds{?name}`) and `getBed` (`/beds/{id}`) GET forms. `GET /beds?name=`
lists beds under `item`, titled by label. ARTIE submits GET forms safely
and follows links by relation and title. SPEC §6.11. Live: three new
scenarios pass from "/" alone.

Still open from this work:

- **HAL-FORMS:** done (`82202d5`). The index is served in HAL-FORMS with
  query GET templates (`/beds?name=`, `/beds?id=`); live from "/" alone.
- **L6:** the reasoner is offered forms only, not links, so an agent cannot
  yet choose "follow the item titled X" by itself.
- **Pagination:** search returns every match in one representation.

## 2026-10-09: declaring and discovering the rung

Done (SPEC §6.11.1), one commit per phase:

1. Spec: `Preference-Applied` is the per-response truth of the rung; the
   service index is also served as `application/json-home`, with link hints
   `formats` (each dialect, its profile in `links`), `accept-prefer` and
   `precondition-req`. Built on draft-nottingham-json-home and
   draft-ietf-httpapi-link-hint rather than a format of our own.
2. Client: `appliedRung` from `Preference-Applied`; no header is L0.
3. Server: the JSON Home index, and `heart.max-rung` (`HEART_MAX_RUNG`,
   also passed through `docker-compose.yml`) to cap the rung served. A
   capped server answers higher requests at its cap, picks the matching
   ETag for acts, and declares only what it serves.
4. Client: `discover(url)` reads the home document and asks for the best
   dialect and rung offered, up to its own. HAL-FORMS-only servers get
   HAL-FORMS at L0. No home document: the client keeps its settings.
5. Live: the real server run at L0, L1 and L2
   (`HEART_MAX_RUNG=L1 scripts/live-client-check.sh` after restarting the
   app with the same variable). 16, 16 and 18 scenarios pass; the two
   L2-caching scenarios are tagged `@needs-L2` and skipped below L2.

Journal entry 07 shows the home document and `Preference-Applied` from
servers started at L2, L1 and L0 (`HEART_MAX_RUNG=L1 ./capture.sh home`
adds one level without clearing `run/`); all 11 verdicts ok. The Fielding
review covers discovery (Rule 6 and the scoreboard).

## 2026-10-09: URI opacity is not a REST rule

SPEC §6.5 no longer prescribes the `/affordance-sets/sha256-{digest}` path
format; it is the reference server's choice, given as an example. The
requirement is one URL per distinct body, `immutable`, never reused. The
reason the client reads nothing from a URL is Rule 4 (no fixed names in the
API), not opacity: Fielding says REST does not require opaque URIs and
encourages readable ones. The W3C opacity advice stays as supporting Web
guidance only. `/.well-known/ni` (RFC 6920) naming was considered and not
taken: it adds a fixed name and makes the URLs unreadable.

## 2026-10-09: paging

`GET /beds` pages with the IANA relations `next`, `prev`, `first` and
`last` (RFC 5005 pattern) in `_links`; `count` is the total. Page length is
`HEART_PAGE_SIZE` (20). The client follows `next` by relation; it needed no
code change, since `follow` already accepts an untitled link. Tested in
server `paging.feature` and client `service-index.feature`; not exercised
live, because the seed data fits on one page.

## Ideas parked

Not planned. Kept so they are not re-derived.

### Real-model runs and EVAL-L6 (parked 2026-10-09)

No real model is used yet. Parked:

- **The L6 real-model run.** The `@llm` scenario in `reason-l6.feature`
  puts Claude behind the Reason step; it needs `ANTHROPIC_API_KEY` and has
  never been run. L6 stays tested with a scripted reasoner only.
- **EVAL-L6** (`spec/EVAL-L6.md`): the evaluation across five domains and
  renamed actions, and its open questions.

Pick up by running the `@llm` scenario once, reading the transcript, then
deciding the EVAL-L6 questions.

### RISE (set aside 2026-10-09)

All RISE work (SPEC §7) is on hold until H.E.A.R.T. is finished. Nothing
is built. Open questions, each with the editor's proposal in the spec:

- **Does RISE require L2?** Proposed (§7.2): yes, scoped to the server
  that issued the template affordance, not to how the originator synced.
- **Envelope wire binding.** Proposed (§7.2): a top-level `_rise` body
  member, since every RISE request is a state-changing POST with If-Match.
- **Poll mode:** a profile over MCP's `io.modelcontextprotocol/tasks`
  extension, or its own mechanism (§7.7).
- **Multi-party:** whether `heart:agent` plus the agent directory suffices.
- **Rel name vocabulary** governance (§7.5).

Pick up by deciding the first two, then a feature for one push-mode round
trip between two ARTIE instances.

### HAL-FORMS beyond L0 (parked 2026-10-08)

Decision: HAL-FORMS stays the L0 on-ramp (SPEC "Decided for 0.5"). Two
alternatives were considered and parked:

- **A H.E.A.R.T. profile of HAL-FORMS.** Add a templated `target`,
  `hr:templatePointers` and `integrity` on the forms link, as the HAL Schema
  Forms profile does. That would make L1 possible: one shared, cached forms
  document per state. L3 would also need a schema reference, at which point
  it is HAL Schema Forms by another name. Cost: Spring HATEOAS cannot
  produce the additions, so the server would build the bytes by hand and
  lose the off-the-shelf tooling that is HAL-FORMS's reason to be here.
- **Propose a templated `target` upstream.** HAL-FORMS already templates a
  property's `value`, so a templated `target` is a natural extension. If
  accepted, L1 would be plain HAL-FORMS and toolchains could follow. Cost: a
  short proposal and waiting on the spec's authors. How active the
  HAL-FORMS repository is has not been checked.

Facts these rest on (HAL-FORMS spec, checked 2026-10-08): `target` has no
`templated` flag; `templated` applies only to a property's `value`; an absent
target means the form document's own address; GET, HEAD and DELETE put
fields in the query (mutate-action-URL), so a field never reaches the path;
`_htarget` and `_hsource` are query parameters on a standalone form
document's address, set by the server.

### Rule 1: protocol independence (parked 2026-10-09)

Fielding's Rule 1, verbatim: "A REST API should not be dependent on any
single communication protocol, though its successful mapping to a given
protocol may be dependent on the availability of metadata, choice of
methods, etc. In general, any protocol element that uses a URI for
identification must allow any URI scheme to be used for the sake of that
identification. [Failure here implies that identification is not separated
from interaction.]"

What it asks: that the API's definition (media types, relations,
processing rules) not depend on HTTP, and that links allow any URI scheme.
It does not ask a server to speak more than one protocol. A client follows
the schemes it can dereference; a link in any other scheme is a choice it
cannot take, as in a browser.

Where H.E.A.R.T. falls short today (review verdict: Tension):

1. SPEC §6 is written directly in HTTP terms (`ETag`, `If-Match`, `Prefer`,
   412, 409). There is no protocol-free layer.
2. ARTIE's only transport is `HttpTransport`; a non-HTTP link would fail
   confusingly rather than be refused as unsupported.
3. The spec never says a link may use any scheme.

Plan, if taken up:

1. Split SPEC §6 into a protocol-free core and an HTTP binding. Core:
   resources, representations, the three layers, affordances, state
   versions, and four refusals (precondition required, stale, not legal
   now, invalid input). The HTTP binding keeps today's text: 428, 412, 409,
   422, `ETag`, `If-Match`, `Prefer`, `Cache-Control`.
2. Any URI scheme for links: one sentence in the spec; ARTIE picks a
   transport by scheme; an unsupported scheme refuses that link or form
   before any request (Gherkin first).
3. Optional: name Layers 2 and 3 with RFC 6920 `ni:` URIs
   (`ni:///sha-256;...`), fetched over HTTP through RFC 6920's
   `/.well-known/ni/` mapping or any other way. Reading the hash from an
   `ni:` URI is licensed by that scheme's own specification (the exception
   W3C AWWW §2.5 allows), unlike the private `/sha256-` path pattern.
4. Not needed: shipping a second protocol. A mapping table in an appendix
   shows the core binds elsewhere.

Second-protocol notes:

- **CoAP (RFC 7252; HTTP mapping RFC 8075)** is the serious example of REST
  off HTTP. `ETag`, `If-Match` (4.12 on failure), `Max-Age` and `Accept`
  map directly. Gaps: no `Prefer` (rung would need a new option or a URI
  query), no `immutable`, no header-style profile links.
- **AMQP:** addresses name message destinations, not resources with
  representations. Request-reply is possible (reply-to, correlation-id),
  but a resource binding would mostly tunnel HTTP. Better fit: RISE's
  asynchronous completion notices. An OASIS effort to carry HTTP semantics
  over AMQP may exist; status not verified.
- **Historic:** HTML `<form method="post" action="mailto:...">` and `ftp:`
  links show hypertext indifferent to the scheme.

With plan items 1 and 2, Rule 1 would be Supports; item 3 would make it a
showcase.

### Discovery beyond the home document (parked 2026-10-09)

Decided instead: `Preference-Applied` as the per-response truth, and a JSON
Home representation of the service index for declaring what a server serves
(SPEC §6.11.1). Parked:

- **`/.well-known/api-catalog` (RFC 9727).** A fixed, standard address on a
  host that lists its APIs as a link set; `service-desc` would point to `/`
  and `service-doc` to the spec. It would let an agent given only a host
  name find the entry point. Cheap, but adoption is tiny (API Evangelist
  counted four real providers in May 2026), and ARTIE is always given an
  entry URI today.
- **Per-resource alternates (connegp "list profiles").** Each bed response
  carries `Link: <...>; rel="alternate"; type="..."; formats="<profile>"`
  for the other dialect, the way an HTML page names its feeds. Useful to a
  browser-like client that lands on a resource without the index.

Rejected: OPTIONS (no defined response body, not cacheable); RFC 2295
`Alternates` and 300 (experimental, never implemented); Client Hints
`Accept-CH` (the server asking the client, the wrong direction).
