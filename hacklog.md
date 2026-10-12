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

## 2026-10-10: Buster findings, and the master plan

Done today: each ARTIE client names itself in `User-Agent` (`ed10e70`), and
ARTIE reads the rung from `Preference-Applied` before following any forms
link (`1e5c41a`, Buster #37). A seed-42 Buster run showed two more kinds of
problem: Buster rules that judge the wrong thing, and verdicts left
inconclusive because the right behaviour sends nothing.

### Principles every item below follows

1. The client trusts only what each response declares (`Preference-Applied`,
   links, `integrity`). It never infers from a URL or a body's shape.
2. Layer 1 is never fresh, for any length of time. The client says so itself
   on every Layer 1 request with the standard request directive
   `Cache-Control: no-cache` (RFC 9111 §5.2.1.4), plus `If-None-Match` once
   it holds an ETag, whatever `Cache-Control` the response carried. Every
   cache on the path, the client's own and any shared one, must then
   revalidate. Layers 2 and 3 stay cacheable for a year, pinned by
   `integrity`.
3. Safety comes from `If-Match`; checking again is for accuracy.
4. Proof comes from the wire first. ARTIE's own events can only turn silence
   into "pass (attested)", never clear a FAIL. Test tools read no URLs either.
5. Standards first: RFC 9110, RFC 9111, RFC 7240, W3C Subresource Integrity,
   W3C Trace Context, the OpenTelemetry log data model, RFC 9457.

### Decisions (2026-10-10)

- **Layer 1 freshness:** as principle 2. The old "revalidate before every
  act" reading is withdrawn: the act straight after a read is acting on a
  fresh representation, and `If-Match` covers the rest.
- **Breadcrumb events:** ARTIE MAY write one event per decision, off by
  default, available to any ARTIE (an audit trail, not only a test aid).
  Delivery to a JSON Lines file, or by POST to a configured URL, or both.
  Each line uses OpenTelemetry log data model field names (`timestamp`,
  `trace_id`, `span_id`, `attributes`).
- **Correlation:** ARTIE sends W3C `traceparent` on every request, one trace
  id per client. Buster groups requests by trace id and ties events to the
  exact request; `User-Agent` stays as the readable label, and is the
  fallback for clients without `traceparent`.
- **Error bodies:** RFC 9457 `application/problem+json` for 428, 412, 409
  and 422, each with a `type` URI per refusal; 422 lists field errors in a
  `violations` extension member with JSON Pointers.
- **Explaining absence:** `heart:unavailable` accepted, to be built now. The
  affordance set links, with `integrity`, to a resource listing the acts
  absent in this state and a short reason each; the client fetches it only
  when asked why.
- **Form names as link relations:** forms are keyed by CURIE relations
  (`garden:water`), expanded like any other relation. Each form carries a
  `title` and `description`; each field carries JSON Schema `title`,
  `description` and `examples`. The relation URI serves a fuller document
  (effect, resulting state, when allowed), fetched on demand and cached. The
  reasoner sees the inline text and may ask for the document. HAL-FORMS at
  L0 keeps its template names.

### Corrections the plan makes to existing work

- Buster `relax-layer1-cache` fails the act straight after a read. Rewrite:
  the client's next read of that resource must reach the server with
  `Cache-Control: no-cache`.
- Buster counts an act as "on this resource" when its path starts with the
  resource's path. That reads URLs; replace it with trace-id attribution
  from the `act.sent` event.
- Buster `flip-byte` passes on a refetch, but ARTIE refuses the bytes and
  stops. New pass: an `integrity.refused` event and no act from that set.
- SPEC §12: close "Digest truncation" (the full digest travels in
  `integrity`); remove "full URIs rather than CURIEs" (decided 2026-10-09);
  `heart:unavailable` is linked with `integrity`, not "content-addressed".
- `spec/CONFORMANCE-GAPS.md`: three rows are stale (expansion "by the
  `[0.5]` rule", digest length, "address = SHA-256").
- `spec/HAL-SCHEMA-FORMS-HR.md` "Not covered here": form names and the
  error format are now decided.

### Order of work (test-first, one commit per step)

1. Spec sweep: §6.3 Layer 1 rule; §6.2 problem details; §12 corrections and
   `heart:unavailable` accepted; form relations and descriptions in the
   profile document; this entry.
2. ARTIE: Layer 1 `no-cache` request directive; `CachingTransport` honours a
   request's `no-cache`.
3. Server and ARTIE: RFC 9457 error bodies.
4. Server and ARTIE: forms keyed by relation, with titles, descriptions and
   field annotations; relation documents; reasoner sees the text.
5. Server and ARTIE: `heart:unavailable`.
6. ARTIE: `traceparent`, `EventSink` (file, POST), events at each decision,
   `client.done`.
7. Buster: `onEvent`, attested passes, trace-id correlation; the three
   plugin rewrites; README table.
8. Docs: CONFORMANCE-GAPS, journal, Fielding review and spec artifacts.
9. Live proof: plain run green, plus paging with `HEART_PAGE_SIZE=2`;
   Buster seed 42 on a fresh server with no inconclusive verdicts left.

Parked items below are untouched by this plan.

### Done (2026-10-10)

1. Spec sweep (`6d040ed`).
2. Layer 1 `no-cache` request directive (`401fd34`).
3. RFC 9457 refusals, server and ARTIE (`d37a97c`).
4. Forms named by relation, self-describing, ALPS profile (`ff17614`,
   `4605198`).
5. `hr:unavailable`, server and ARTIE (`79634e8`, `8c0306f`).
6. `traceparent` and the event log (`dbf76cb`).
7. Buster: trace-id clients, `/_buster/events` and `buster.client-events`,
   attested passes, `client.done`, and the plugin rewrites. Green (98
   tests), but **not committed**: Buster's pre-commit check reads Buster's
   own gate log, and these edits were made from a session rooted in artie,
   whose hooks log only artie paths. The red runs are in Buster's log; the
   feature and production writes are not. Commit by hand after review, or
   redo the edits from a session opened in `buster/`.
8. Docs: CONFORMANCE-GAPS rows, journal recaptured (11/11 ok),
   `live-client-check.sh` writes ARTIE's event log where a Buster server
   reads it, and always reruns `liveTest`.
9. Live proof. Plain server: 23/23 live scenarios, also with
   `HEART_PAGE_SIZE=2` (the search walks two pages by `next`). Buster, seed
   42, fresh server, ARTIE's event log read through `buster.client-events`:
   ARTIE's verdicts are 27 pass, 8 pass (attested), 6 inconclusive, 0 FAIL.
   The first run's FAILs: #3 and #21 (relax-layer1-cache) now pass, because
   the read said `no-cache`; #24 (drop-affordance) passes; #37
   (lie-preference-applied) is an attested pass. The 6 inconclusive are all
   stale-etag "never acted": the forged ETag landed on a resource the
   scenario never acted on, so nothing was exercised. The one FAIL in the run
   is curl, the script's readiness probe, which re-read a bed without
   `no-cache`, which is correct.

## 2026-10-10: Buster campaigns, and no undecided verdicts

The random seed-42 run against the latest Buster gave ARTIE 13 pass, 8 pass
(attested), 20 inconclusive and 0 FAIL. Inconclusive is not acceptable: a
mutation the client never exercises proves nothing either way. Buster (main
at 66675a1) now aims mutations from a dry run, and its verdicts are only pass,
pass (attested) or FAIL.

`scripts/buster-campaign.sh` runs the whole campaign:
1. It starts a fresh Buster server.
2. It records a dry run of the `@live` scenarios and gets the plan.
3. It arms each round and runs the scenarios again.
4. It exits non-zero unless the report's `campaign.ok` is true.

The first campaign: 25 rounds, and every plugin was exercised:

| plugin | combinations |
|---|---|
| alias-forms-href | 11 |
| drop-affordance | 11 |
| drop-curie | 11 |
| flip-byte | 13 |
| lie-preference-applied | 11 |
| relax-layer1-cache | 50 |
| stale-etag | 19 |
| strip-forms-integrity | 11 |

Verdicts: 54 pass, 42 pass (attested), 21 FAIL, 0 undecided. The gate failed.
All 21 FAILs are Buster judging the wrong thing; ARTIE behaved correctly in
each:

- **relax-layer1-cache on an act's response (14).** The "triggering read" was
  a POST to `/beds/{id}/actions/...`, and `Cache-Control: no-cache` means
  nothing on a POST. The resource the response represents is `/beds/{id}`.
  The rule applies to the next GET of that resource, and the planner should
  arm only where the dry run makes one.
- **stale-etag on an act's response (4).** ARTIE re-synced by GET of the
  bed's self link, `/beds/1`. The plugin waited for a GET of the action URL.
- **drop-curie and lie-preference-applied at L2 (2).** Buster forces
  `no-store` on a mutated response, so ARTIE re-read `/beds/3` in full. The
  new, unmutated representation linked the set, and ARTIE followed it. A later
  representation of the same resource replaces the mutated one.
- **stale-etag with two clients (1).** Round 3 armed both clients of "Someone
  else writes first". The second client's act was refused as stale, which
  ended the scenario, so the first client never made its act. Clients that
  share a scenario cannot be armed in the same round.

Server side: act responses do not send `Content-Location`. RFC 9110 §8.7 is
the standard way to say that a POST response is a representation of
`/beds/{id}`. Buster can only learn that today from the HAL self link.

Closed the same day:
- **Artie:**
  - act responses send `Content-Location` (cc70f20);
  - every live scenario starts from the seed data (78966dd). Before that, a mutated
    scenario left its bed half-grown and 19 of 120 planned mutations never fired.
- **Buster:**
  - act responses are judged by the resource they name (4da016d);
  - a later unmutated representation closes the window (c9b034d);
  - interacting clients are not armed together (b5cd244);
  - the gate counts made combinations and names misaligned ones (ad0f70f, d7ae43b).

Campaign run 4 (Buster d7ae43b):
- 22 rounds; 120 combinations planned and 120 made.
- 70 pass, 50 pass (attested), 0 FAIL.
- No holes, nothing misaligned, nothing unrun.
- `campaign.ok` is true, and the script exits 0.

## 2026-10-10: Fielding's Rule 1, any protocol

Fielding asks for identification to be separate from interaction: a URI of
any scheme may identify, and the mapping to a protocol is a separate matter.
H.E.A.R.T. was an HTTP spec through and through, and ARTIE's core read status
codes and headers. Now:

- **SPEC §6.0, "Core and bindings".** It lists the protocol-neutral concepts
  (read, requested and applied rendering, validator, never fresh, act, done,
  stale, not offered, invalid, refusal detail…) and maps each to its HTTP
  form. §6.1–§6.12 are now the HTTP binding.
  - A client picks a binding by URI scheme.
  - It refuses a URI whose scheme it has no binding for, before sending
    anything.
  - It never rewrites a URI to another scheme.
- **ARTIE.**
  - The core works in `Received` and `Outcome`, with no status codes or
    headers. `HttpBinding` holds every header and code.
  - `Bindings` dispatches by scheme.
  - Shared layers are checked against their link's `integrity` in the core,
    whatever the binding. The HTTP binding still checks below its cache as well.
- **Proof.** `any-protocol.feature` adds a toy `mem:` binding that knows no
  HTTP. Through it, ARTIE:
  - syncs, acts, and is refused as stale, with nothing sent over HTTP;
  - follows a link from an HTTP representation to a `mem:` resource;
  - refuses a `gopher:` link without sending anything.
- **Event log.** `act.sent` and `act.refused` now carry `heart.code` and
  `heart.method` in place of the `http.*` attributes.

The campaign still passes after the change: 120 of 120 mutations made, 70 pass,
50 pass (attested), 0 FAIL.

A second real binding was parked at first; it is done below. `ni:` names
(RFC 6920) are dropped.

## 2026-10-10: the CoAP binding

The user decided two things:
- The profile and the rung travel in experimental CoAP options, one for one
  with the HTTP headers. Content-Format numbers per rung were rejected: they
  would put the profile into the format ID, which §6.1 rejected for HTTP.
- The server speaks CoAP natively, not through a CoAP-to-HTTP gateway.

What was built:
- **SPEC §6.0.1.**
  - Options: 65000 Accept-Profile, 65003 No-Cache, 65004 Prefer,
    65008 Profile, 65012 Preference-Applied.
  - No-Cache is critical and unsafe-to-forward, so a proxy that does not know
    it must refuse and cannot answer from its cache.
  - Content-Formats 65000–65004 for hal+json, schema+json, problem+json,
    json-home and alps+json.
  - The validator is 8 bytes: the first 8 of SHA-256 over the HTTP ETag.
  - Codes: 2.05, 2.04, 2.03, 4.12 stale, 4.09 not offered, 4.22 invalid, and
    4.00 with the problem type for precondition required (CoAP has no 4.28).
- **Server.**
  - `CoapFrontDoor`, a Californium server on UDP 5683 beside HTTP, set by
    `heart.coap.port`. It serves beds, acts, shared layers, the index, search
    and ALPS from the same services.
  - The act checks moved into `BedActs`, shared by both front doors, so the
    order and the refusals are the same everywhere.
  - `coap.feature` runs 10 scenarios with a real CoAP client over UDP. One of
    them checks that the CoAP body is the same JSON as the HTTP body.
- **ARTIE.** `CoapBinding` is registered for `coap:`. `coap-binding.feature`
  has 6 scenarios against a scripted CoAP server. The architecture test
  allows network libraries only in Transport and Binding implementations.
- **Live, over UDP against the real server.**
  - Bed 1 through sow, water and harvest at L0 and at L2.
  - From `coap://…/` alone: look a bed up by id, then water it.
  - In all three, nothing went over HTTP.
  - The live suite is 24 of 24, and the Buster campaign still passes
    (120 of 120, 0 FAIL).

Gaps:
- No `coaps` (DTLS).
- No HAL-FORMS over CoAP: it answers 4.06.
- No client identity or trace context over CoAP.
- The option and format numbers are experimental until registered with IANA.

## 2026-10-10: the first real-model run, against the real server

This is one scripted scenario (`live-server.feature`, tagged `@llm`). ARTIE's
Reason step is a real Claude call (`claude-sonnet-5-5`), and everything around
it stays deterministic. The request is "please water bed 2 with 250 ml" at
`/beds/2` on the running server.
- The model chose `garden:water-cell` with `milliliters` 250.
- ARTIE's checks passed it, and the server accepted it.
- The server's own history for bed 2 now ends with `CellWatered` of 250 ml.

It passed in 1.8 s. The older `@llm` scenario in `reason-l6.feature`, against a
scripted manifest, ran for the first time and passed too.

Found on the way:
- The model refuses a forced tool call: `tool_choice: type "tool" and "any" are
  not supported for this model`. `ClaudeReasoner` now sends
  `tool_choice: auto`. An answer in words alone is taken as a question for the
  user, never as an act. That rule has its own scripted scenario.
- My first expectation was wrong. Bed 2 starts with 400 ml, so 250 ml more makes
  it `READY_TO_HARVEST`, not `PLANTED`.

Running it:
- `liveTest` now leaves `@llm` out.
- `llmTest` runs it against the live server, with the key from
  `ANTHROPIC_API_KEY` or `~/.anthropic/api_key`.

This is one case, so it shows that the AI-driven path works end to end, not
that a model chooses well. The "AI-driven clients" row stays Partly until
`spec/EVAL-L6.md` is built.

## 2026-10-11: Part A done, a HEART server library

This is Part A of `spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md`. HEART moved out
of the garden server into a library at `heart/`:
- `heart-core`: HEART itself, with no protocol. The declaration model, HAL
  Schema Forms rendering, content addressing, schemas generated from input
  classes, the act checks, the index, JSON Home and search pages.
- `heart-spring`: the HTTP binding.
- `heart-coap`: the CoAP binding.
- `heart-eventsourcing`: an optional event store, in memory or on Postgres.
- `heart-tck`: the conformance features every domain runs against itself.
- `heart-spring-boot-starter`.

`heart/README.md` shows a HEART server in five minutes.

The garden's main code went from 2786 lines to its domain, its event-sourcing
declaration and its HEART declaration. It has no controllers and no CoAP code;
an architecture test now keeps it that way.

The lamp is declared only in the library's tests and writes no HTTP or CoAP
code. It passes all 13 TCK scenarios (over HTTP), heart-coap's features over
real UDP, and, as event-sourced lamps, heart-eventsourcing's features on both
stores. The garden passes the same 13 TCK scenarios.

Every step stayed green:
- server, client and live suites;
- the Buster campaign after each step that changed the server: 22 rounds, 120
  combinations made, 70 pass, 50 attested, 0 FAIL, no holes, gate ok. That is
  the same as the baseline before the move.

Found on the way:
- The garden's sow-seed schema had drifted from the server:
  - it said `depthCm` was any number, while the server silently cut 2.5 to 2;
  - it left out `seedType`'s `minLength`, which the server enforced.

  Schemas are now generated from the input class and enforced as served. The
  author approved the three content addresses this changed. Every other
  address is byte-identical to before the move, pinned by
  `content-addresses.feature`.
- A library needs `-java-parameters` set itself (the Spring Boot plugin sets
  it for applications), and Jakarta JSON-P 2.1 with Parsson, the pair Spring
  Boot manages.
- Committing the plan document between a red run and the code reset the TDD
  window. Code is committed first now, documents after.

Migration V2 moved the beds' events to the library's tables and dropped the
old event and snapshot tables. `reset-db.sh` seeds the new table.

## 2026-10-11: acts on the bookmark, support tickets, and features that test only their own

**Acts the bookmark offers.** A first version added a "create" operation:
`POST /{collection}`. The author pointed out that create is merely an act,
and that HEART is not CRUD. Now the service index is a resource that offers
acts like any other, at `/actions/{path}`: the same form, the same checks in
the same order (428, 412, 422; never 409, since the index always offers its
acts), with the index's ETag in `If-Match`. Only the answer differs, because
HTTP says so: an act that brings a resource into being answers 201 with
`Location` and `Content-Location`. SPEC §6.2 says so. ARTIE's explorer starts
from the bookmark when it offers an act it has a sample for.

**Support tickets (plan B5)** at `domains/tickets`, the second domain on the
library. A ticket is opened by `open-ticket`, the bookmark's act; its number
and its first event commit in one transaction. Statuses NEW, ASSIGNED,
WAITING_ON_CUSTOMER, RESOLVED and CLOSED; agents are fixed values. It passes
all 17 TCK scenarios, the bookmark act's included. Buster campaign: 41
rounds, 64 combinations, 40 pass, 24 attested, 0 FAIL, no holes, gate ok.

**Features test only their own.** Each domain feature now holds only its
domain's rules: what each state offers, the transitions, the reasons, and
the rules a schema cannot say. What every HEART server does is tested once in
the library, and the TCK runs it against every domain.
- The garden had 75 scenarios, all about the wire, from before the library
  existed. About 30 of them checked library behaviour that heart tested
  nowhere else. Those moved to heart-spring and heart-coap, against the lamp;
  the rest were already covered and were removed. The garden kept its code
  layout, its runtime stack, its pinned content addresses, and one check that
  CoAP and HTTP serve the same JSON. It gained `garden.feature`, its rules.
- Basketball lost the rows that tested its schema rather than its game.
- Tickets was written this way from the start.

Found on the way: Spring HATEOAS rendered HAL-FORMS properties by reflecting
on each act's input class. Every Kotlin `val` came out `readOnly: true`, and
an optional string came out as a numeric range up to 2147483647. So the
HAL-FORMS forms of basketball and tickets said their fields could not be
filled. Only the garden's input classes avoided it, with `var` and a test of
their own. heart-core now renders HAL-FORMS from each act's schema, as it
already did for the index, and Spring HATEOAS is gone from the library and
the garden.

**Refactor mode for the TDD gate.** A refactor changes structure, not
behaviour, so it has no new scenario and cannot be seen red first; the gate
used to refuse it. Now the author can switch on refactor mode for one commit,
by typing `! scripts/refactor-mode on "why"` at the prompt. The commit passes
only if no feature or test file differs from HEAD, a run passed before the
first code change, and the last run passed after the last one. It carries a
`Refactor-Mode: <why>` trailer, and the mode ends with the commit. A
PreToolUse hook blocks Claude from switching the mode on, from changing the
gate's scripts, hooks, settings or event log, and from `--no-verify`. Before
this, nothing stopped Claude from editing the gate itself. Claude still runs
as the author's user, so a determined bypass is possible; the hook makes it
deliberate and visible rather than accidental, and the unchanged tests limit
what a misuse could hide.

## 2026-10-11: a write between the If-Match check and the act

Found while walking garden bed one by hand for the author's review
(`spec/MANUAL-REVIEW-PLAN.md`). The binding read the resource and checked
If-Match against it, but each domain's act then read the resource again and
appended its event after whatever it found. A write that landed between the
check and the act was therefore not refused: the second act was applied to a
state its client never saw, and answered 200 where SPEC §6.2 says 412. The
garden's own status check hid part of this, for acts that change the status.

`HeartResource.perform` is now given the state the validator was checked
against, and the domain appends at the number after that state's version, so
the event store refuses the act if anything landed in between. A scenario in
heart-spring stages the write after the check and expects 412. A new
conformance scenario makes every domain prove it: another client's act lands,
then the domain's `perform` is called on the state read before it, and must
refuse. Putting the old re-read back into tickets makes it fail. The garden's
leftover status check, and its comment about catching 409, are gone.

Two more from the same walk. `@NotBlank` gave the schema only `minLength 1`,
so a seed of three spaces was sown; heart-core now adds a pattern that means
"some character that is not white space" both to JSON Schema, which searches,
and to HAL-FORMS clients, which match the whole value. And a domain rule's
refusal, such as assigning a ticket to the agent who has it, was sent as
`invalid-input`, "The body does not match the form's schema", though the body
did match. It now has its own problem type, `rule-refused`, still 422 with
`violations`, so a client can tell "I built the body wrong" from "the
resource's state says no". SPEC §6.2 lists five types.

Later the same day: generated schemas now declare `$schema` (HAL-SCHEMA-FORMS
Rule 5), and content addresses lost their `sha256-` prefix. Sixteen hex
digits are a short name, not a digest; only the full integrity value is
checked, so the name should not look like one. A clash of two different
bodies under one name, about one chance in ten quadrillion, used to keep one
body and drop the other without a word, because the maps were built with
`associateBy`. Every binding now serves Layers 2 and 3 from one
`SharedContent`, which refuses to start on a clash. SPEC §6.5's truncation
paragraph is rewritten to match. Addresses are not kept stable for their own
sake: nothing is in production.

## 2026-10-12: trace context over CoAP

Roy Bott's review found that ARTIE's CoAP requests carried no trace context,
so its events about them had an empty `span_id` and could not be tied to a
request. Trace Context defines no CoAP carrier, and no IETF draft adds one.

The CoAP binding now carries `traceparent` in experimental option 65020, one
for one with the HTTP header, as Accept-Profile and Prefer already are. 65020
is elective, safe-to-forward and NoCacheKey (RFC 7252 §5.4.6), so a proxy
that does not know it passes it on and a trace never splits a cache. ARTIE
mints a new span per CoAP request under the URL it was given; keying it by
Californium's rewritten URI missed, since Californium drops the default port.
In the live run, all 30 events about CoAP requests now name a span (0 before).
Client identity is still not carried over CoAP.

## 2026-10-12: the garden refuses over-watering

Roy Bott's review found the garden never answered `rule-refused`: it had no
rule a schema-valid body could break, so over-watering was silently trimmed
at moisture 100. Now `garden:water-cell` refuses water that would push
moisture past 100, as 422 `rule-refused` at `/milliliters` ("The bed would be
waterlogged: at most 600 ml now."). Because moisture only ever rose, the rule
alone would leave a bed unable to grow after two crops, so harvesting now
leaves the bed dry: the crop drank the water. That replaces the 2026-10-11
decision that the soil stayed moist. Crop rotation, a rule that leaves no bed
stuck, is parked in `spec/ideas/crop-rotation-rule.md`.

## 2026-10-12: a bed is built from the bookmark

Roy Bott's review found four TCK scenarios skipped for the garden: they test
an act on the bookmark that brings a resource into being, and the garden's
bookmark offered only its search and lookup. It now offers `garden:build-bed`:
a name and a width and length in whole feet (1 to 100). The answer is 201,
with the new bed's Location and its representation: empty, offering only
`garden:sow-seed`. Name and size never change, so they live on the bed's
identity row (`garden_bed`, migration V3; older beds are taken as 4 by 8),
not in its events. The garden TCK now runs all its scenarios, none skipped,
and a live scenario has ARTIE build a bed from `/` and sow it.

## 2026-10-12: Roy Bott's second findings

- A built bed had a past from before it was built: `GET /beds/5?asOf=` a
  date before building answered 200 with an empty bed. Now it is 404, "Bed 5
  was not built yet at …", judged by the bed's `created_at`. Migration V4
  moves any bed's `created_at` back to its first event, and `reset-db.sh`
  seeds beds as built 30 days ago, before their seed events.
- A `rule-refused` detail repeated its title ("a rule of the resource
  refuses the act: …"). The detail is now the rule's own words.
- `server-spring/script.sh` wrote to tables V2 removed and used the old
  `embed=schemas` preference. Removed; the live suite and `capture.sh` cover
  what it showed.

## 2026-10-12: HEART through MCP (branch mcp-spike)

`mcp/` lets any MCP host (Claude Code, Claude Desktop, any agent) work any
HEART service. The positioning: MCP gives an agent a catalogue scoped to the
server; HEART gives it this resource's legal next moves; heart-mcp puts the
second inside the first.

- **Decision: six fixed verbs**, not one tool per offered act. The tool list
  (heart_look, heart_act, heart_follow, heart_describe, heart_explain,
  heart_back) never changes; what is legal now comes back in every result.
  That is the browser model (fixed buttons, changing resources). It works in
  every host, whatever its `list_changed` support. It keeps the host's prompt
  cache warm, and keeps CURIEs intact (API tool names allow only
  `[a-zA-Z0-9_-]`). Per-act tools are parked in
  `spec/ideas/mcp-per-act-tools.md`.
- The host's model is the Reason step. No tool input and no result carries an
  address, a method, a header or a validator. Schemas lose their `$id`.
  ARTIE checks a choice against the resource it holds before sending
  anything, and acts on the state the model saw, never a re-read.
- Built as a library (`mcp/heart-mcp`), a thin stdio app
  (`mcp/heart-mcp-server`), and an image (`mcp/Dockerfile`). The MCP
  Kotlin SDK 0.8.1 speaks protocol 2025-06-18; Claude Code connected to the
  spike.
- ARTIE now raises `RuleRefused` for a 422 `rule-refused`, apart from
  `InvalidPayload`.
- Proof: `scripts/mcp-check.sh` gives 12/12 scenarios through the official
  MCP client SDK over stdio, 10 of them against the live garden.
  `--docker` drives the image over JSON-RPC and waters bed 2.
- Found while building: ArtieAgent's link offers include `hr:unavailable`
  (only `self` and the forms link are excluded), so an L6 reasoner could
  "follow" the explanation link. heart-mcp excludes it. ArtieAgent is
  unchanged for now.

## Ideas parked

Not planned. Kept so they are not re-derived.

### Old exhibits (marked historical 2026-10-09)

`exhibits/cache-demo/` and `scripts/test-report/` show the early-October
design and carry a "Historical" note. They are evidence of that design,
not of the current one. To bring them up to date, if we decide to:

1. `exhibits/cache-demo/kotlin-client/GenerateFixtures.kt`: emit
   `{"$ref": ..., "hr:integrity": "sha256-<base64>"}` schema references and
   an `integrity` member on the Layer 1 forms link (full SHA-256, Base64,
   over the canonical bytes). Regenerate `fixtures/`.
2. `ArtieCacheDemo.kt`: stop reading the hash from the URL; check each
   body against the integrity value of the link that led to it, before
   caching, and refuse a link without one. Follow `$ref`, not `$id`.
3. `server.py`: optionally send `Repr-Digest: sha-256=:<base64>:`. Paths
   may stay hash-shaped; nothing may depend on them.
4. Rerun the demo, then `scripts/test-report/build_report.py` against
   fresh JUnit XML, the new `cache-demo.log`, a fresh `live-hash.txt`
   (rewrite `live-hash.sh` to check integrity values, not path hashes) and
   the current journal. Remove the Historical notes.

Simpler alternative: delete both, since the journal and live suites prove
the same things; git history keeps them.

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
