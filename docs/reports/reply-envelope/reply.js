// Reply envelope, end to end: the data. Every name is tagged with where its standard comes from (see LEGEND).
// Digests, keys, times and identities are made up; the shapes are the ones the design proposes.
(() => {
  // HEART representations: HAL Schema Forms, the reference dialect (SPEC §6.1). The hr prefix is declared in curies.
  const CURIES = { curies: [{ name: "hr", href: "https://w3id.org/heart-rise/rels/{rel}", templated: true }] };

  // The caller's template: its own message, with the standard names left null for the responder to fill.
  const template = () => ({
    id: null,
    time: null,
    source: null,
    subject: null,
    outcome: null,
    result: null,
    problem: null,
    correlation: "asst-req-5521",
    note: "Ana: text me too, and tell me the date",
  });

  const LEGEND = [
    { k: "codified", t: "Codified", d: "A published IETF RFC, W3C Recommendation, OASIS or ISO standard. Safe to cite as a standard." },
    { k: "proposed", t: "Proposed", d: "Still a draft: an IETF Internet-Draft or a W3C Working Draft. It can change, or never ship." },
    { k: "defacto", t: "De facto", d: "Widely used, with no standards-body document: a written spec from a foundation or vendor, a book's pattern, or a product's own protocol." },
    { k: "thesis", t: "Thesis", d: "A principle from a doctoral thesis, not a standard. Fielding's constraints, 2000." },
    { k: "ours", t: "Ours", d: "Named in this design, with no standard behind it. It can change." },
  ];

  const CHANGED = [
    "One template, not an echo wrapped in a stamp. The caller writes its whole reply. The responder fills the standard names left null, and removes the rest.",
    "Signed with the responder's own key (RFC 9421), not a shared secret. A secret would have to travel inside the template, so it is out.",
    "A repeat after a lost answer gets 412. The sender reads again, finds its own id, and stops. No new replay rule.",
    "The reply's address is the correlation for the wait. The caller's own id, correlation, serves its own log; the responder never reads it.",
  ];

  const STANDARDS = [
    { key: "id", does: "The responder's id for this delivery. A repeat carries the same id.", tags: [{ k: "defacto", note: "CloudEvents attribute id" }], src: "CloudEvents 1.0. A written spec from CNCF, not an IETF, W3C or ISO document." },
    { key: "time", does: "When the responder processed the delivery, in UTC.", tags: [{ k: "defacto", note: "CloudEvents attribute time" }, { k: "codified", note: "format: RFC 3339" }], src: "CloudEvents 1.0 for the name. RFC 3339 for the format: date-time with Z." },
    { key: "source", does: "URI of the responder.", tags: [{ k: "defacto", note: "CloudEvents attribute source" }], src: "CloudEvents 1.0." },
    { key: "subject", does: "URI of the resource the result is about.", tags: [{ k: "defacto", note: "CloudEvents attribute subject" }], src: "CloudEvents 1.0." },
    { key: "outcome", does: "done, refused or failed.", tags: [{ k: "ours" }], src: "No standard for these three words. They are not HTTP status codes." },
    { key: "result", does: "The answer itself, shaped by the responder's own form.", tags: [{ k: "ours", note: "name" }, { k: "defacto", note: "shape: HAL-FORMS" }], src: "The shape comes from the responder's form, read before acting. HAL-FORMS is a written spec, not a standards-body document." },
    { key: "problem", does: "A failure, as an RFC 9457 problem. Also the body of every refusal in this report.", tags: [{ k: "codified", note: "RFC 9457" }], src: "RFC 9457 Problem Details for HTTP APIs. Its extension members (answer, limit, expires) are allowed; their names are ours." },
    { key: "null-fill", does: "A null in the template means: fill me. Names left unfilled are removed.", tags: [{ k: "ours" }], src: "Not RFC 7396 JSON Merge Patch, where null means delete. Stated here so nobody reads it that way." },
    { key: "correlation", does: "The caller's own id for its request. Copied untouched; the responder never reads it.", tags: [{ k: "ours", note: "name" }, { k: "defacto", note: "concept" }], src: "Concept: Enterprise Integration Patterns, Correlation Identifier (Hohpe and Woolf, 2003). AMQP 1.0 has correlation-id (CODIFIED: OASIS, ISO/IEC 19464). AMQP 0-9-1 has correlation_id (DE FACTO: RabbitMQ)." },
    { key: "expires", does: "After this instant the reply is expired. The caller sets it on the reply.", tags: [{ k: "ours", note: "name" }, { k: "codified", note: "format: RFC 3339" }], src: "The format is RFC 3339. The idea matches AMQP 1.0's absolute-expiry-time; the name is ours." },
    { key: "Date", does: "When the responder sent the delivery.", tags: [{ k: "codified", note: "RFC 9110 §6.6.1" }], src: "RFC 9110. A header; the caller stores the body's time, not this." },
    { key: "If-Match", does: "The validator the sender last read. A delivery is refused if the reply has changed since.", tags: [{ k: "codified", note: "RFC 9110 §13.1.1" }], src: "RFC 9110. Used with ETag." },
    { key: "ETag", does: "The reply's validator, sent with every read and answer.", tags: [{ k: "codified", note: "RFC 9110 §8.8.3" }], src: "RFC 9110." },
    { key: "Location", does: "Where a new resource is: the reply, the watch.", tags: [{ k: "codified", note: "RFC 9110 §10.2.2" }], src: "RFC 9110." },
    { key: "traceparent", does: "Continues the caller's trace: same trace id, new span id.", tags: [{ k: "codified", note: "W3C Trace Context, Level 1" }], src: "W3C Recommendation, for HTTP headers. Carrying it in AMQP messages is common practice (OpenTelemetry), not in the W3C document." },
    { key: "Content-Digest", does: "A digest of the body. Proves the bytes, not who sent them.", tags: [{ k: "codified", note: "RFC 9530" }], src: "RFC 9530, Digest Fields." },
    { key: "Signature-Input", does: "The signed parts, and the parameters created, expires, keyid, alg and tag: when, which key, which purpose.", tags: [{ k: "codified", note: "RFC 9421" }], src: "RFC 9421, HTTP Message Signatures. tag is a parameter the RFC defines; the value heart-reply is ours." },
    { key: "Signature", does: "The signature itself, as a byte sequence.", tags: [{ k: "codified", note: "RFC 9421, RFC 8941" }], src: "RFC 9421 uses RFC 8941 Structured Fields for the :base64: value." },
    { key: "JWK", does: "The responder's public key, as a JSON Web Key.", tags: [{ k: "codified", note: "RFC 7517; RFC 8037 for OKP and Ed25519" }], src: "RFC 7517 (JSON Web Key). RFC 8037 (OKP keys, Ed25519)." },
    { key: "WWW-Authenticate", does: "Tells a refused sender how to authenticate. Required on a 401.", tags: [{ k: "codified", note: "RFC 9110 §11.6.1" }, { k: "ours", note: "scheme Signature" }], src: "The rule is RFC 9110. The scheme name Signature is ours; no registered scheme is assumed." },
    { key: "status codes", does: "200, 201, 401, 409, 412, 413 and 428 in this design.", tags: [{ k: "codified", note: "RFC 9110 §15; 428 from RFC 6585" }], src: "RFC 9110 names 413 Content Too Large." },
    { key: "_links", does: "Links to related resources, by relation name.", tags: [{ k: "defacto", note: "HAL" }], src: "HAL is an IETF Internet-Draft that expired. Widely used; no RFC." },
    { key: "application/hal+json", does: "The media type of every HEART representation, with its profile link: heart-rise/hal-schema-forms.", tags: [{ k: "defacto", note: "HAL" }, { k: "ours", note: "profile" }], src: "HAL is an IETF Internet-Draft that expired. The profile URI is HEART's; the client selects it with Accept-Profile and the server names it in Link rel=\"profile\"." },
    { key: "_forms", does: "The forms (acts) a resource offers: method, target, contentType and the schema of the input.", tags: [{ k: "defacto", note: "HAL Schema Forms" }, { k: "ours", note: "HEART's reference dialect" }], src: "HAL Schema Forms is a community spec. HEART's reference dialect uses its _forms key, with HEART's additions." },
    { key: "JSON Schema", does: "Describes the properties of the input, including which names are fillable.", tags: [{ k: "proposed", note: "draft 2020-12" }], src: "An IETF Internet-Draft, in wide use. Check its current status before citing it as a standard." },
    { key: "hr: relations", does: "hr:reply-to, hr:keys, hr:loan, hr:deliver: relation names.", tags: [{ k: "ours" }], src: "HEART's own prefix. Not registered anywhere." },
  ];

  const REJECTED = [
    { name: "Standard Webhooks headers (webhook-id, webhook-timestamp, webhook-signature)", tags: [{ k: "defacto" }], why: "Its signature is HMAC with a shared secret. The secret would travel inside the template. Rejected." },
    { name: "Idempotency-Key header", tags: [{ k: "proposed" }], why: "An IETF httpapi Internet-Draft. Not needed: the answered state and If-Match already allow one answer only." },
    { name: "RFC 7396 JSON Merge Patch, null means delete", tags: [{ k: "codified" }], why: "Our null means fill. Reusing the name would confuse anyone who knows merge patch. Not used." },
    { name: "AsyncAPI document", tags: [{ k: "defacto" }], why: "A written spec that describes channels and payloads both sides share at design time. That shared document is the coupling this design avoids; see the coupling section." },
    { name: "Digest alone, no signature", tags: [{ k: "codified" }], why: "RFC 9530 proves the bytes, not the sender. Kept, but not enough alone." },
  ];

  const STEPS = [
    {
      id: "s1",
      title: "The assistant opens a reply on its own inbox",
      lede: "Before it asks, the assistant writes the reply it wants back. The template is its own message. The standard names it wants filled are null.",
      std: ["null-fill", "correlation", "expires", "_forms", "JSON Schema", "Location", "status codes", "_links", "time", "id", "subject", "outcome", "result", "source"],
      exchanges: [{
        line: "POST https://assistant.example/replies",
        headers: ["Content-Type: application/hal+json", 'Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"'],
        body: { template: template(), expires: "2026-11-15T09:00:00Z" },
        bodyLabel: "Request body: the template and the expiry",
        res: {
          line: "201 Created",
          headers: ["Location: https://assistant.example/replies/7f3a"],
          body: {
            state: "awaiting",
            expires: "2026-11-15T09:00:00Z",
            template: template(),
            _links: { ...CURIES, self: { href: "https://assistant.example/replies/7f3a" } },
            _forms: {
              "hr:deliver": {
                method: "POST",
                target: "https://assistant.example/replies/7f3a/actions/deliver",
                schema: {
                  type: "object",
                  properties: {
                    id: { type: "string" },
                    time: { type: "string", format: "date-time" },
                    source: { type: "string", format: "uri" },
                    subject: { type: "string", format: "uri" },
                    outcome: { enum: ["done", "refused", "failed"] },
                    result: { type: "object" },
                    problem: { type: "object" },
                  },
                },
              },
            },
          },
        },
      }],
      note: "The responder fills only the names listed in this schema, and only where the template has null. Everything else, the caller's own fields included, comes back exactly as sent.",
    },
    {
      id: "s2",
      title: "The assistant asks the library, and passes the reply's address",
      lede: "The assistant acts on the loan. Its one hypermedia input is the reply's link, in _rise.",
      std: ["Location", "_links", "hr: relations", "status codes"],
      exchanges: [{
        line: "POST https://library.example/loans/L-40219/actions/tell-me-when-renewable",
        headers: ["Content-Type: application/hal+json", 'Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"'],
        body: { _rise: { _links: { ...CURIES, "hr:reply-to": { href: "https://assistant.example/replies/7f3a" } } } },
        res: {
          line: "201 Created",
          headers: ["Location: https://library.example/watches/w-88"],
          body: {
            state: "waiting",
            _links: { ...CURIES,
              self: { href: "https://library.example/watches/w-88" },
              "hr:loan": { href: "https://library.example/loans/L-40219" },
              "hr:keys": { href: "https://library.example/keys" },
            },
          },
        },
      }],
      note: "The library learns one address. It learns nothing of the assistant's channels, topics or queues. The assistant gets the library's key location here, for step 6.",
    },
    {
      id: "s3",
      title: "The hold clears",
      lede: "A simulator control clears the hold on the loan. The watch moves to ready, and the library wakes.",
      std: [],
      exchanges: [],
      note: "No HTTP exchange here. This is inside the library.",
    },
    {
      id: "s4",
      title: "The library reads the reply before it sends",
      lede: "The library reads the reply first. Read before send: it learns the validator, the form, and which names to fill.",
      std: ["ETag", "If-Match", "Date", "_forms", "status codes"],
      exchanges: [{
        line: "GET https://assistant.example/replies/7f3a",
        headers: ["Accept: application/hal+json", "Accept-Profile: <https://w3id.org/heart-rise/hal-schema-forms>"],
        res: {
          line: "200 OK",
          headers: ["ETag: \"seq-1.hsf.L2.c4b8a0e62d9f1137\""],
          body: { state: "awaiting", expires: "2026-11-15T09:00:00Z", template: template() },
        },
      }],
      note: "The library takes the target from the form in the representation. It never builds the deliver address itself.",
    },
    {
      id: "s5",
      title: "The library delivers: fills the template, signs the request",
      lede: "The library copies the caller's template, fills the null names it has values for, and signs the whole request with its own key.",
      std: ["If-Match", "ETag", "Date", "traceparent", "Content-Digest", "Signature-Input", "Signature", "id", "time", "source", "subject", "outcome", "result", "correlation", "status codes", "_links"],
      exchanges: [{
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [
          "If-Match: \"seq-1.hsf.L2.c4b8a0e62d9f1137\"",
          "Date: Sun, 08 Nov 2026 15:40:12 GMT",
          "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
          "Content-Type: application/hal+json", 'Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"',
          "Content-Digest: sha-256=:bW9jay1kaWdlc3Q=:",
          "Signature-Input: sig1=(\"@method\" \"@target-uri\" \"content-digest\" \"if-match\" \"traceparent\");created=1794152412;expires=1794152712;keyid=\"lib-2026-11\";alg=\"ed25519\";tag=\"heart-reply\"",
          "Signature: sig1=:bW9jay1zaWduYXR1cmU=:",
        ],
        body: {
          id: "lib-dlv-90211",
          time: "2026-11-08T15:40:12Z",
          source: "https://library.example/riverside",
          subject: "https://library.example/loans/L-40219",
          outcome: "done",
          result: { loan: "https://library.example/loans/L-40219", standing: "renewable", renewUntil: "2026-12-06" },
          correlation: "asst-req-5521",
          note: "Ana: text me too, and tell me the date",
        },
        bodyLabel: "Request body: the caller's template, filled",
        res: {
          line: "200 OK",
          headers: ["ETag: \"seq-2.hsf.L2.19d6e3f0a8c57b24\""],
          body: { state: "answered", id: "lib-dlv-90211", time: "2026-11-08T15:40:12Z" },
        },
      }],
      note: "problem was never filled, so the responder removed it. The caller's note and correlation come back exactly as sent.",
    },
    {
      id: "s6",
      title: "The assistant checks the library's signature",
      lede: "The assistant fetches the library's key from the link it read at step 2, and verifies the request before it trusts the answer.",
      std: ["JWK", "Signature-Input", "Content-Digest", "ETag", "_links"],
      exchanges: [{
        line: "GET https://library.example/keys",
        headers: ["Accept: application/hal+json", "Accept-Profile: <https://w3id.org/heart-rise/hal-schema-forms>"],
        res: {
          line: "200 OK",
          headers: ["ETag: \"seq-3.hsf.L2.7a1e5c0d9b2f4863\""],
          body: { keys: [{ kty: "OKP", crv: "Ed25519", kid: "lib-2026-11", x: "bW9jay1wdWJsaWMta2V5" }] },
        },
      }],
      note: "The keyid in Signature-Input matches kid. Then the assistant checks Content-Digest, the created to expires window, and its own log for asst-req-5521. Only then does it mark its own request done.",
    },
    {
      id: "s7",
      title: "A lost answer: the library sends the same delivery again",
      lede: "The first answer never reached the library. It retries: the same body and id, a new Date and a new signature, because the old window has closed. The validator is the one it read at step 4.",
      std: ["If-Match", "Date", "Signature-Input", "problem", "status codes"],
      exchanges: [{
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [
          "If-Match: \"seq-1.hsf.L2.c4b8a0e62d9f1137\"",
          "Date: Sun, 08 Nov 2026 15:46:12 GMT",
          "Content-Type: application/hal+json", 'Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"',
          "Content-Digest: sha-256=:bW9jay1kaWdlc3Q=:",
          "Signature-Input: sig1=(\"@method\" \"@target-uri\" \"content-digest\" \"if-match\" \"traceparent\");created=1794152772;expires=1794153072;keyid=\"lib-2026-11\";alg=\"ed25519\";tag=\"heart-reply\"",
        ],
        body: "same as step 5",
        res: {
          line: "412 Precondition Failed",
          headers: ["Content-Type: application/problem+json"],
          body: {
            type: "https://w3id.org/heart-rise/problems/validator-stale",
            title: "The reply changed since you read it",
            status: 412,
            detail: "The reply's ETag is now \"seq-2.hsf.L2.19d6e3f0a8c57b24\".",
          },
        },
      }],
      note: "Nothing is applied twice. The sender reads again (step 8).",
    },
    {
      id: "s8",
      title: "The library reads again and finds its own answer",
      lede: "The sender reads the reply and compares the id with the one it sent.",
      std: ["ETag", "id"],
      exchanges: [{
        line: "GET https://assistant.example/replies/7f3a",
        headers: ["Accept: application/hal+json", "Accept-Profile: <https://w3id.org/heart-rise/hal-schema-forms>"],
        res: {
          line: "200 OK",
          headers: ["ETag: \"seq-2.hsf.L2.19d6e3f0a8c57b24\""],
          body: { state: "answered", id: "lib-dlv-90211", time: "2026-11-08T15:40:12Z" },
        },
      }],
      note: "The id is its own, so the answer is its own. The library stops retrying.",
    },
    {
      id: "s9",
      title: "A different answer is refused",
      lede: "A second, different delivery arrives with the current validator. It is a bug on the library's side, or a second replica.",
      std: ["If-Match", "problem", "status codes", "id", "outcome"],
      exchanges: [{
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: ["If-Match: \"seq-2.hsf.L2.19d6e3f0a8c57b24\"", "Content-Type: application/hal+json", 'Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"'],
        body: {
          id: "lib-dlv-90419",
          time: "2026-11-08T16:02:55Z",
          source: "https://library.example/riverside",
          subject: "https://library.example/loans/L-40219",
          outcome: "refused",
          correlation: "asst-req-5521",
        },
        bodyLabel: "Request body (signature headers as in step 5, omitted here)",
        res: {
          line: "409 Conflict",
          headers: ["Content-Type: application/problem+json"],
          body: {
            type: "https://w3id.org/heart-rise/problems/already-answered",
            title: "This reply is already answered",
            status: 409,
            answer: "lib-dlv-90211",
          },
        },
      }],
      note: "Deliver is not offered once answered, so the answer is 409. answer is an extension member; its name is ours.",
    },
    {
      id: "s10",
      title: "A reply that expired first",
      lede: "The library was late. The assistant's inbox moved the reply to expired at its expires time.",
      std: ["expires", "problem", "status codes", "If-Match"],
      exchanges: [{
        line: "POST https://assistant.example/replies/7f40/actions/deliver",
        headers: ["If-Match: \"seq-4.hsf.L2.6a3f1e8d0c9b5277\"", "Content-Type: application/hal+json", 'Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"'],
        body: { id: "lib-dlv-90533", time: "2026-11-15T09:20:00Z", source: "https://library.example/riverside", subject: "https://library.example/loans/L-40219", outcome: "done", correlation: "asst-req-5522" },
        bodyLabel: "Request body (signature headers omitted here)",
        res: {
          line: "409 Conflict",
          headers: ["Content-Type: application/problem+json"],
          body: {
            type: "https://w3id.org/heart-rise/problems/reply-expired",
            title: "This reply expired before the answer",
            status: 409,
            expires: "2026-11-15T09:00:00Z",
          },
        },
      }],
      note: "Once expired, deliver is not offered. The caller learns when the reply expired, not only that it did.",
    },
    {
      id: "s11",
      title: "A forged delivery",
      lede: "Someone who holds the reply's address, but not the library's key, sends a body the library never wrote.",
      std: ["Signature-Input", "Signature", "WWW-Authenticate", "problem", "status codes"],
      exchanges: [{
        line: "POST https://assistant.example/replies/7f41/actions/deliver",
        headers: [
          "If-Match: \"seq-1.hsf.L2.c4b8a0e62d9f1137\"",
          "Content-Type: application/hal+json", 'Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"',
          "Content-Digest: sha-256=:bm90LXRoZS1kaWdlc3Q=:",
          "Signature-Input: sig1=(\"@method\" \"@target-uri\" \"content-digest\" \"if-match\" \"traceparent\");created=1794152412;expires=1794152712;keyid=\"lib-2026-11\";alg=\"ed25519\";tag=\"heart-reply\"",
          "Signature: sig1=:bm90LXRoZS1yaWdodC1rZXk=:",
        ],
        body: { id: "forged-1", time: "2026-11-08T15:41:00Z", source: "https://library.example/riverside", outcome: "done", result: { standing: "renewable" } },
        res: {
          line: "401 Unauthorized",
          headers: ["WWW-Authenticate: Signature realm=\"heart-reply\"", "Content-Type: application/problem+json"],
          body: {
            type: "https://w3id.org/heart-rise/problems/signature-invalid",
            title: "The signature does not match the request",
            status: 401,
          },
        },
      }],
      note: "The signature check runs before any state check, so a forger learns nothing about the reply. The scheme name Signature is ours; RFC 9110 requires WWW-Authenticate on a 401.",
    },
    {
      id: "s12",
      title: "A template too large",
      lede: "A caller tries to store a long message in the reply.",
      std: ["problem", "status codes"],
      exchanges: [{
        line: "POST https://assistant.example/replies",
        headers: ["Content-Type: application/hal+json", 'Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"'],
        body: { template: { ...template(), note: "Ana: (5,000 characters, shortened in this report)" }, expires: "2026-11-15T09:00:00Z" },
        bodyLabel: "Request body, shortened for this report",
        res: {
          line: "413 Content Too Large",
          headers: ["Content-Type: application/problem+json"],
          body: {
            type: "https://w3id.org/heart-rise/problems/reply-too-large",
            title: "The reply template is larger than 4,096 bytes",
            status: 413,
            limit: 4096,
          },
        },
      }],
      note: "The cap stops a reply address from becoming storage. Both sides hold the template while the reply waits.",
    },
  ];

  const COUPLING = [
    { what: "Where the answer goes", caller: "Its own reply address, passed in _rise.", responder: "Only the link it was given.", shared: "No." },
    { what: "Which names to fill", caller: "The standard names, as null in its template.", responder: "The names in the reply's _forms schema, read at delivery time.", shared: "No." },
    { what: "The answer's shape", caller: "The responder's form, read before it acts.", responder: "Its own form.", shared: "No." },
    { what: "The caller's own fields (note, correlation)", caller: "All of them.", responder: "Copies them. Never reads them.", shared: "No." },
    { what: "Channels, topics, queues", caller: "None of the responder's.", responder: "None of the caller's.", shared: "No." },
    { what: "The signing key", caller: "Found by link (hr:keys), cached by ETag.", responder: "Its own.", shared: "No." },
    { what: "What both sides share at design time", caller: "Standard names only: CloudEvents, RFC 3339, RFC 9421, traceparent, RFC 9457.", responder: "The same.", shared: "Standards only." },
  ];

  const FIELDING = [
    { c: "Client-server", how: "Each side acts on the other only through links it was given. Neither holds the other's internals.", steps: "2, 5" },
    { c: "Stateless", how: "Each delivery carries its own Date, created window and traceparent. The only state is the resource the reply names.", steps: "5, 7" },
    { c: "Cacheable", how: "Reads of the reply and of the keys carry an ETag. Deliver is a POST and is not cached.", steps: "4, 6" },
    { c: "Uniform interface: identification", how: "The reply, the watch and the keys each have their own URI, given in Location or _links.", steps: "1, 2" },
    { c: "Uniform interface: manipulation through representations", how: "The reply is JSON. The library changes it by sending a representation, not by calling a method.", steps: "5" },
    { c: "Uniform interface: self-descriptive messages", how: "Signature names its key, Content-Digest names the body, the template names what it wants back, and media types say how to read it.", steps: "5, 6" },
    { c: "Uniform interface: hypermedia as the engine of state", how: "The library never builds the deliver address. It takes the target from _forms on the reply it read.", steps: "4, 5" },
    { c: "Layered system", how: "The signature covers the target URI and the headers, so an intermediary cannot change them without failing verification.", steps: "5, 11" },
    { c: "Code on demand", how: "Not used.", steps: "none" },
  ];

  const BOUNDS = [
    { text: "The template is at most 4,096 bytes. Larger is 413, with the limit in the problem.", tags: [{ k: "ours", note: "the limit" }, { k: "codified", note: "413, RFC 9110" }] },
    { text: "Only names listed in the reply's schema are filled. Other nulls are removed.", tags: [{ k: "ours" }] },
    { text: "Every deliver carries If-Match. Without it: 428.", tags: [{ k: "codified", note: "428, RFC 6585; If-Match, RFC 9110" }] },
    { text: "A signature is good for five minutes, from created to expires.", tags: [{ k: "codified", note: "parameters, RFC 9421" }, { k: "ours", note: "the five minutes" }] },
    { text: "At its expires time, the reply moves to expired. Deliver is then not offered.", tags: [{ k: "ours" }, { k: "codified", note: "RFC 3339" }] },
    { text: "One answer only. Once answered, deliver is not offered: 409.", tags: [{ k: "ours", note: "the rule" }, { k: "codified", note: "409, RFC 9110" }] },
    { text: "Reply addresses are https only in this design.", tags: [{ k: "ours" }] },
    { text: "The template holds no secrets. The responder stores it while the reply waits.", tags: [{ k: "ours", note: "the policy" }] },
    { text: "The signature check runs before any validator or state check, so a forger learns nothing.", tags: [{ k: "ours", note: "the order" }, { k: "codified", note: "RFC 9421" }] },
    { text: "Retries are bounded by the sender's policy. A sender that gives up reads once more before it stops.", tags: [{ k: "ours", note: "the policy" }] },
  ];

  const OPEN = [
    "The reply as a HEART resource (recommended). It keeps read before send, If-Match and one answer. A one-shot POST drops all three.",
    "Repeats: 412, then read again, as in step 7 (recommended). No replay rule.",
    "Signing: RFC 9421 with the responder's key (recommended). Standard Webhooks is dropped; its shared secret would travel in the template.",
    "A receipt: a hr: relation, on the responder's side, to a resource that tracks the occurrence. Link only in the first version (recommended). The relation name must come from the IANA registry before use; none is assumed here.",
    "Standard names bare, spelled as CloudEvents spells them (recommended). The hr: prefix only on HEART's own names.",
  ];

  window.REPLY = { CHANGED, LEGEND, STANDARDS, REJECTED, STEPS, COUPLING, FIELDING, BOUNDS, OPEN };
})();
