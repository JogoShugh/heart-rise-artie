// RISE core, the RESTful minimum: the data. Each interaction lists the rules it exercises (see RULES).
// Representations are HAL Schema Forms (the reference dialect, SPEC §6.1): application/hal+json, named by its profile.
// Digests, times and identities are made up; the shapes are the ones the core proposes.
(() => {
  const PROFILE = "https://w3id.org/heart-rise/hal-schema-forms";
  const PROFILE_LINK = `Link: <${PROFILE}>; rel="profile"`;
  const ACCEPT = [`Accept: application/hal+json`, `Accept-Profile: <${PROFILE}>`];
  const CURIES = { curies: [{ name: "hr", href: "https://w3id.org/heart-rise/rels/{rel}", templated: true }] };
  const ETAG = (tag) => `ETag: "${tag}"`;
  // A read: Layer 1, never fresh unchecked, varied by the request's negotiation headers.
  const READ = (tag) => ["Content-Type: application/hal+json", PROFILE_LINK, "Vary: Accept, Prefer, Accept-Profile", "Cache-Control: no-cache", ETAG(tag)];
  // An act's request body: a HEART representation of the input, named by the same profile.
  const BODY = ["Content-Type: application/hal+json", PROFILE_LINK];
  const IF_MATCH = (tag) => `If-Match: "${tag}"`;
  const PROBLEM = ["Content-Type: application/problem+json"];
  const ROOT_TAG = "seq-1.hsf.L2.a1c3e5f7091b2d41";
  const LOAN_TAG = "seq-7.hsf.L2.7e2d9b4c1a6f0358";
  const REP1 = "seq-1.hsf.L2.c4b8a0e62d9f1137";
  const REP2 = "seq-2.hsf.L2.19d6e3f0a8c57b24";
  const REP3 = "seq-3.hsf.L2.e05a7c9b3d1f6482";
  const RES1 = "seq-1.hsf.L2.4f8e2a6c1b0d9735";
  const B1 = "seq-1.hsf.L2.9d3f7a0c5e2b1846";
  const B2 = "seq-2.hsf.L2.8c0e4b7f2a19d365";
  const DIGEST = "sha-256=:bW9jay1kaWdlc3Q=:";

  const RULES = [
    ["R1", "Every resource has its own URI. Creation returns 201 with Location."],
    ["R2", "A reply's state is a GET-able representation."],
    ["R3", "GET and HEAD never change state."],
    ["R4", "A state change is a POST to a form target named in the representation. The client builds no URL."],
    ["R5", "Acts go to resource URIs, never to one RPC endpoint."],
    ["R6", "Representations are application/hal+json. Errors are application/problem+json. Bare application/json is not used."],
    ["R7", "Responses carry a strong ETag. Writes carry If-Match. A write without it is 428."],
    ["R8", "The client knows only the entry link and relation names."],
    ["R9", "The representation offers only the acts available now."],
    ["R10", "Stateless: each request carries all it needs."],
    ["R11", "A callback stands alone: its own Date and signature window."],
    ["R12", "Reads carry ETag and Cache-Control: no-cache."],
    ["R13", "Acts are not cacheable."],
    ["R14", "Client and server share only links and standard names."],
    ["R15", "Intermediaries are transparent. Nothing needs the path."],
    ["R16", "Negotiation: Accept and Accept-Profile on requests; Link rel=\"profile\" and Vary on every read."],
    ["A1", "The caller lists the levels it accepts. The responder reports the levels it applied."],
    ["S1", "Send once: the answer is not stored on the reply. The digest records what was sent."],
    ["S2", "Send once: the representation says hr:delivery once."],
    ["S3", "Send once: the deliver response is not cacheable."],
    ["H1", "Held: the responder keeps the answer as a resource, and the reply links to it."],
  ];

  const STEPS = [
    {
      id: "i1",
      title: "Enter at the library's root",
      exchanges: [{
        line: "GET https://library.example/",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(ROOT_TAG),
          body: { _links: { ...CURIES, "hr:loans": { href: "https://library.example/loans" } } },
        },
      }],
      obs: [
        { t: "One URL known. Everything else comes from links.", r: ["R8"] },
        { t: "Relation name, not a path. The hr prefix is declared in curies.", r: ["R8"] },
        { t: "Profile named in Link rel=\"profile\". Client negotiated with Accept-Profile.", r: ["R16"] },
        { t: "Safe read.", r: ["R3"] },
      ],
    },
    {
      id: "i2",
      title: "Read the loan; its form says how to act",
      exchanges: [{
        line: "GET https://library.example/loans/L-40219",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(LOAN_TAG),
          body: {
            state: "renewable",
            title: "The Hobbit",
            _links: { ...CURIES, self: { href: "https://library.example/loans/L-40219" } },
            _forms: {
              "hr:tell-me-when-renewable": {
                method: "POST",
                target: "https://library.example/loans/L-40219/actions/tell-me-when-renewable",
                contentType: "application/hal+json",
                schema: { type: "object", properties: { _rise: { type: "object" } } },
              },
            },
          },
        },
      }],
      obs: [
        { t: "Target comes from the form. The client builds no URL.", r: ["R4"] },
        { t: "Form offered now, because the loan is renewable.", r: ["R9"] },
        { t: "Strong ETag names this representation for the later write.", r: ["R7"] },
        { t: "Read changed nothing.", r: ["R3"] },
      ],
    },
    {
      id: "i3",
      title: "Open a reply on the assistant's inbox",
      exchanges: [{
        line: "POST https://assistant.example/replies",
        headers: [...BODY],
        body: { want: ["status", "link", "inline"], expires: "2026-11-15T09:00:00Z" },
        bodyLabel: "Request body: the levels accepted, and the expiry",
        res: {
          line: "201 Created",
          headers: ["Location: https://assistant.example/replies/7f3a", ...READ(REP1).filter((h) => !h.startsWith("Vary"))],
          body: {
            state: "awaiting",
            want: ["status", "link", "inline"],
            _links: { ...CURIES, self: { href: "https://assistant.example/replies/7f3a" } },
            _forms: {
              "hr:deliver": {
                method: "POST",
                target: "https://assistant.example/replies/7f3a/actions/deliver",
                contentType: "application/hal+json",
                schema: { type: "object", properties: { outcome: { enum: ["done", "refused", "failed"] }, result: { type: "object" }, applied: { type: "array" } } },
              },
              "hr:progress": {
                method: "POST",
                target: "https://assistant.example/replies/7f3a/actions/progress",
                contentType: "application/hal+json",
                schema: { type: "object", properties: { stage: { type: "string" } } },
              },
              "hr:fail": {
                method: "POST",
                target: "https://assistant.example/replies/7f3a/actions/fail",
                contentType: "application/hal+json",
                schema: { type: "object", properties: { problem: { type: "object" } } },
              },
            },
          },
        },
      }],
      obs: [
        { t: "Created: 201, Location names the new reply.", r: ["R1"] },
        { t: "Caller states the levels it accepts. The responder reports what it applied, later.", r: ["A1"] },
        { t: "No template sent. The schema comes from the representation.", r: ["R4"] },
        { t: "Readable from its first state.", r: ["R2"] },
        { t: "Acts sit on the reply's own URIs, not one endpoint.", r: ["R5"] },
      ],
    },
    {
      id: "i4",
      title: "Act on the loan, with the reply's address",
      exchanges: [{
        line: "POST https://library.example/loans/L-40219/actions/tell-me-when-renewable",
        headers: [...BODY, IF_MATCH(LOAN_TAG)],
        body: { _rise: { _links: { "hr:reply-to": { href: "https://assistant.example/replies/7f3a" } } } },
        res: {
          line: "201 Created",
          headers: ["Location: https://library.example/watches/w-88", "Content-Type: application/hal+json", PROFILE_LINK, ETAG("seq-1.hsf.L2.5d0b8e2f7a3c9164")],
          body: {
            state: "waiting",
            _links: {
              ...CURIES,
              self: { href: "https://library.example/watches/w-88" },
              "hr:loan": { href: "https://library.example/loans/L-40219" },
            },
          },
        },
      }],
      obs: [
        { t: "Act on the form target from I2.", r: ["R4", "R5"] },
        { t: "Act carries the validator from the read.", r: ["R7"] },
        { t: "New watch: 201 with Location.", r: ["R1"] },
        { t: "The library learns one address and nothing of the inbox's layout.", r: ["R14"] },
      ],
    },
    {
      id: "i5",
      title: "The library reads the reply before it sends",
      exchanges: [{
        line: "GET https://assistant.example/replies/7f3a",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(REP1),
          body: {
            state: "awaiting",
            want: ["status", "link", "inline"],
            _links: { ...CURIES },
            _forms: {
              "hr:deliver": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/deliver", contentType: "application/hal+json" },
              "hr:progress": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/progress", contentType: "application/hal+json" },
              "hr:fail": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/fail", contentType: "application/hal+json" },
            },
          },
        },
      }],
      obs: [
        { t: "Read before send. The validator is taken here.", r: ["R7"] },
        { t: "Library picks from the forms offered now.", r: ["R9"] },
        { t: "Negotiated profile is named on the response.", r: ["R16"] },
        { t: "Read changed nothing.", r: ["R3"] },
      ],
    },
    {
      id: "i6",
      title: "Progress while waiting",
      exchanges: [{
        line: "POST https://assistant.example/replies/7f3a/actions/progress",
        headers: [...BODY, IF_MATCH(REP1)],
        body: { stage: "waiting for the hold to clear" },
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/hal+json", PROFILE_LINK, ETAG(REP2)],
          body: {
            state: "awaiting",
            stage: "waiting for the hold to clear",
            _links: { ...CURIES },
            _forms: {
              "hr:deliver": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/deliver", contentType: "application/hal+json" },
              "hr:progress": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/progress", contentType: "application/hal+json" },
              "hr:fail": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/fail", contentType: "application/hal+json" },
            },
          },
        },
      }],
      obs: [
        { t: "Progress is an act: a POST to its form target.", r: ["R4"] },
        { t: "The write changes the validator. The new ETag replaces the old.", r: ["R7"] },
        { t: "Forms still offered while awaiting.", r: ["R9"] },
        { t: "Request carries its own validator and body. Nothing kept per session.", r: ["R10"] },
      ],
    },
    {
      id: "i7",
      title: "Send the answer once",
      exchanges: [{
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [
          ...BODY,
          IF_MATCH(REP2),
          "Date: Sun, 08 Nov 2026 15:40:12 GMT",
          `Content-Digest: ${DIGEST}`,
        ],
        body: {
          outcome: "done",
          applied: ["status", "inline"],
          result: { loan: "https://library.example/loans/L-40219", standing: "renewable", renewUntil: "2026-12-06" },
        },
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/hal+json", PROFILE_LINK, "Cache-Control: no-store", ETAG(REP3)],
          body: {
            state: "answered",
            "hr:delivery": "once",
            applied: ["status", "inline"],
            digest: DIGEST,
            time: "2026-11-08T15:40:12Z",
            _links: { ...CURIES, self: { href: "https://assistant.example/replies/7f3a" } },
          },
        },
      }],
      obs: [
        { t: "Responder applied inline, not the link: a subset of want.", r: ["A1"] },
        { t: "The answer is not stored on the reply. The digest records what was sent.", r: ["S1"] },
        { t: "Representation says once.", r: ["S2"] },
        { t: "Callback stands alone: its own Date and window.", r: ["R11"] },
        { t: "Deliver response is not cacheable.", r: ["S3", "R13"] },
      ],
    },
    {
      id: "i8",
      title: "Read the reply after the answer",
      exchanges: [{
        line: "GET https://assistant.example/replies/7f3a",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(REP3),
          body: {
            state: "answered",
            "hr:delivery": "once",
            applied: ["status", "inline"],
            digest: DIGEST,
            time: "2026-11-08T15:40:12Z",
            _links: { ...CURIES, self: { href: "https://assistant.example/replies/7f3a" } },
          },
        },
      }],
      obs: [
        { t: "Read shows the status, not the answer.", r: ["S2"] },
        { t: "No act offered once answered. No _forms.", r: ["R9"] },
        { t: "The digest lets the caller check its stored copy.", r: ["S1"] },
        { t: "Read carries ETag and no-cache.", r: ["R12"] },
      ],
    },
    {
      id: "i9",
      title: "A repeat after a lost answer, with the old validator",
      exchanges: [{
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [
          ...BODY,
          IF_MATCH(REP2),
          "Date: Sun, 08 Nov 2026 15:46:12 GMT",
          `Content-Digest: ${DIGEST}`,
        ],
        body: { outcome: "done", applied: ["status", "inline"], result: { standing: "renewable" } },
        res: {
          line: "412 Precondition Failed",
          headers: [...PROBLEM],
          body: {
            type: "https://w3id.org/heart-rise/problems/validator-stale",
            title: "The reply changed since you read it",
            status: 412,
          },
        },
      }],
      obs: [
        { t: "Refusal is a problem document.", r: ["R6"] },
        { t: "Old validator refused. Nothing applied twice.", r: ["R7"] },
        { t: "Sender reads again, as in I8.", r: ["R10"] },
      ],
    },
    {
      id: "i10",
      title: "A write with no validator",
      exchanges: [{
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [...BODY],
        body: { outcome: "done" },
        res: {
          line: "428 Precondition Required",
          headers: [...PROBLEM],
          body: {
            type: "https://w3id.org/heart-rise/problems/validator-required",
            title: "This write needs If-Match",
            status: 428,
          },
        },
      }],
      obs: [
        { t: "Missing If-Match refused before state is checked.", r: ["R7"] },
        { t: "Problem type names the rule.", r: ["R6"] },
      ],
    },
    {
      id: "i11",
      title: "A second reply, with fewer levels",
      exchanges: [{
        line: "POST https://assistant.example/replies",
        headers: [...BODY],
        body: { want: ["status", "link"], expires: "2026-11-15T09:00:00Z" },
        bodyLabel: "Request body: the levels accepted, and the expiry",
        res: {
          line: "201 Created",
          headers: ["Location: https://assistant.example/replies/7f40", "Content-Type: application/hal+json", PROFILE_LINK, ETAG(B1)],
          body: {
            state: "awaiting",
            want: ["status", "link"],
            _links: { ...CURIES, self: { href: "https://assistant.example/replies/7f40" } },
            _forms: {
              "hr:deliver": {
                method: "POST",
                target: "https://assistant.example/replies/7f40/actions/deliver",
                contentType: "application/hal+json",
                schema: { type: "object", properties: { outcome: { enum: ["done", "refused", "failed"] }, applied: { type: "array" } } },
              },
            },
          },
        },
      }],
      obs: [
        { t: "Second reply, its own URI.", r: ["R1"] },
        { t: "Caller accepts fewer levels this time.", r: ["A1"] },
      ],
    },
    {
      id: "i12",
      title: "Second answer, held: only a link travels",
      exchanges: [{
        line: "POST https://assistant.example/replies/7f40/actions/deliver",
        headers: [...BODY, IF_MATCH(B1)],
        body: {
          outcome: "done",
          applied: ["status", "link"],
          _links: { "hr:result": { href: "https://library.example/results/r-88" } },
        },
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/hal+json", PROFILE_LINK, "Cache-Control: no-cache", ETAG(B2)],
          body: {
            state: "answered",
            "hr:delivery": "link",
            applied: ["status", "link"],
            _links: {
              ...CURIES,
              self: { href: "https://assistant.example/replies/7f40" },
              "hr:result": { href: "https://library.example/results/r-88" },
            },
          },
        },
      }],
      obs: [
        { t: "Responder holds the answer. The reply links to it.", r: ["H1"] },
        { t: "Only the link travels, not the answer.", r: ["H1"] },
      ],
    },
    {
      id: "i13",
      title: "Fetch the held result",
      exchanges: [{
        line: "GET https://library.example/results/r-88",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(RES1),
          body: {
            _links: { ...CURIES, self: { href: "https://library.example/results/r-88" } },
            loan: "https://library.example/loans/L-40219",
            standing: "renewable",
            renewUntil: "2026-12-06",
          },
        },
      }],
      obs: [
        { t: "The held answer is a resource. GET works at any time.", r: ["H1", "R2"] },
        { t: "no-cache: the validator is checked every time.", r: ["R12"] },
        { t: "The read changes nothing on the library.", r: ["R3"] },
      ],
    },
  ];

  const CHECKS = [
    "Entry link only: I1 starts from one URL. Every later URL comes from a link or a form.",
    "No GET changes state: I1, I2, I5, I8, I13.",
    "Every write carries If-Match: I4, I6, I7, I9, I12. Refused without it: I10.",
    "Every refusal is application/problem+json: I9, I10.",
    "Every representation is application/hal+json with its profile link. No bare application/json anywhere: all I.",
    "Offered forms follow state: I3 offers deliver, progress and fail. I8 offers none.",
    "Send-once keeps the digest and says once: I7, I8.",
    "Held answer is GET-able with no-cache: I13.",
    "No caller internals in any body.",
  ];

  window.CORE = { RULES, STEPS, CHECKS };
})();
