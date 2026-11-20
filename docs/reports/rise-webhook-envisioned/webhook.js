// RISE dynamic webhook, envisioned: every request and response, in order. Data only; page.js renders it.
// Representations are HAL Schema Forms (SPEC §6.1). Defaults: act bodies are application/hal+json under the profile;
// the answer is held by the responder and linked. Digests, times and identities are made up.
(() => {
  const PROFILE = "https://w3id.org/heart-rise/hal-schema-forms";
  const PROFILE_LINK = `Link: <${PROFILE}>; rel="profile"`;
  const ACCEPT = ["Accept: application/hal+json", `Accept-Profile: <${PROFILE}>`];
  const CURIES = { curies: [{ name: "hr", href: "https://w3id.org/heart-rise/rels/{rel}", templated: true }] };
  const ETAG = (tag) => `ETag: "${tag}"`;
  const READ = (tag) => ["Content-Type: application/hal+json", PROFILE_LINK, "Vary: Accept, Prefer, Accept-Profile", "Cache-Control: no-cache", ETAG(tag)];
  const BODY = ["Content-Type: application/hal+json", PROFILE_LINK];
  const IF_MATCH = (tag) => `If-Match: "${tag}"`;
  const PROBLEM = ["Content-Type: application/problem+json"];
  const DATE = "Date: Sun, 08 Nov 2026 15:40:12 GMT";
  const DIGEST = "sha-256=:bW9jay1kaWdlc3Q=:";

  // Validators, as the representations name them: seq-<version>.hsf.L2.<digest>.
  const ROOT = "seq-1.hsf.L2.a1c3e5f7091b2d41";
  const LOAN = "seq-7.hsf.L2.7e2d9b4c1a6f0358";
  const REP1 = "seq-1.hsf.L2.c4b8a0e62d9f1137";
  const REP2 = "seq-2.hsf.L2.19d6e3f0a8c57b24";
  const REP3 = "seq-3.hsf.L2.e05a7c9b3d1f6482";
  const REP4 = "seq-4.hsf.L2.6a3f1e8d0c9b5277";
  const WATCH1 = "seq-1.hsf.L2.5d0b8e2f7a3c9164";
  const WATCH2 = "seq-2.hsf.L2.3a9c7e1d5b0f8246";
  const RES1 = "seq-1.hsf.L2.4f8e2a6c1b0d9735";

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

  // Participants: Assistant (the originator), Inbox (its reply resources, at assistant.example), Library (the responder).
  const STEPS = [
    {
      id: "w1",
      title: "Enter at the library's root",
      exchanges: [{
        from: "Assistant", to: "Library",
        line: "GET https://library.example/",
        headers: [...ACCEPT],
        res: { line: "200 OK", headers: READ(ROOT), body: { _links: { ...CURIES, "hr:loans": { href: "https://library.example/loans" } } } },
      }],
      obs: [
        { t: "One URL known. Everything else comes from links.", r: ["R8"] },
        { t: "Profile named on the response. Client negotiated it with Accept-Profile.", r: ["R16"] },
      ],
    },
    {
      id: "w2",
      title: "Read the loan; its form says how to ask for a watch",
      exchanges: [{
        from: "Assistant", to: "Library",
        line: "GET https://library.example/loans/L-40219",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(LOAN),
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
        { t: "Target comes from the form.", r: ["R4"] },
        { t: "Form offered now, because the loan is renewable.", r: ["R9"] },
        { t: "Strong ETag for the later act.", r: ["R7"] },
      ],
    },
    {
      id: "w3",
      title: "Open a reply on the assistant's inbox",
      exchanges: [{
        from: "Assistant", to: "Inbox",
        line: "POST https://assistant.example/replies",
        headers: [...BODY],
        body: { want: ["status", "link"], expires: "2026-11-15T09:00:00Z" },
        bodyLabel: "Request body: the levels accepted, and the expiry",
        res: {
          line: "201 Created",
          headers: ["Location: https://assistant.example/replies/7f3a", ...READ(REP1).filter((h) => !h.startsWith("Vary"))],
          body: {
            state: "awaiting",
            want: ["status", "link"],
            _links: { ...CURIES, self: { href: "https://assistant.example/replies/7f3a" } },
            _forms: {
              "hr:deliver": {
                method: "POST",
                target: "https://assistant.example/replies/7f3a/actions/deliver",
                contentType: "application/hal+json",
                schema: { type: "object", properties: { outcome: { enum: ["done", "refused", "failed"] }, applied: { type: "array" } } },
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
        { t: "Created: 201, Location names the reply.", r: ["R1"] },
        { t: "Levels accepted: status and held link. No inline.", r: ["A1"] },
        { t: "The schema comes from the representation. No template sent.", r: ["R4"] },
      ],
    },
    {
      id: "w4",
      title: "Ask the library to tell it when renewable",
      exchanges: [{
        from: "Assistant", to: "Library",
        line: "POST https://library.example/loans/L-40219/actions/tell-me-when-renewable",
        headers: [...BODY, IF_MATCH(LOAN)],
        body: { _rise: { ...CURIES, _links: { "hr:reply-to": { href: "https://assistant.example/replies/7f3a" } } } },
        res: {
          line: "201 Created",
          headers: ["Location: https://library.example/watches/w-88", "Content-Type: application/hal+json", PROFILE_LINK, ETAG(WATCH1)],
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
        { t: "A watch comes into being: 201 with Location.", r: ["R1"] },
        { t: "The state change carries the validator read in w2.", r: ["R7"] },
        { t: "The library learns one address: the reply's link.", r: ["R14"] },
      ],
    },
    {
      id: "w5",
      title: "The assistant checks the watch while it waits",
      exchanges: [{
        from: "Assistant", to: "Library",
        line: "GET https://library.example/watches/w-88",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(WATCH1),
          body: {
            state: "waiting",
            _links: { ...CURIES, self: { href: "https://library.example/watches/w-88" }, "hr:loan": { href: "https://library.example/loans/L-40219" } },
          },
        },
      }],
      obs: [
        { t: "Read changed nothing.", r: ["R3"] },
        { t: "Watch is a resource with its own state.", r: ["R2"] },
      ],
    },
    {
      id: "w6",
      title: "The hold clears",
      exchanges: [],
      note: "A simulator control clears the hold on L-40219. The library's wake-up client runs next.",
      obs: [],
    },
    {
      id: "w7",
      title: "The library reads the reply before it sends",
      exchanges: [{
        from: "Library", to: "Inbox",
        line: "GET https://assistant.example/replies/7f3a",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(REP1),
          body: {
            state: "awaiting",
            want: ["status", "link"],
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
        { t: "The library picks from the forms offered now.", r: ["R9"] },
      ],
    },
    {
      id: "w8",
      title: "Progress while waiting (optional)",
      exchanges: [{
        from: "Library", to: "Inbox",
        line: "POST https://assistant.example/replies/7f3a/actions/progress",
        headers: [...BODY, IF_MATCH(REP1)],
        body: { stage: "the library is preparing the answer" },
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/hal+json", PROFILE_LINK, ETAG(REP2)],
          body: {
            state: "awaiting",
            stage: "the library is preparing the answer",
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
        { t: "The write changes the validator.", r: ["R7"] },
      ],
    },
    {
      id: "w9",
      title: "The library settles the watch: the answer becomes a held result",
      exchanges: [{
        from: "Library", to: "Library",
        line: "POST https://library.example/watches/w-88/actions/settle",
        headers: [...BODY, IF_MATCH(WATCH1)],
        body: {
          outcome: "done",
          result: { loan: "https://library.example/loans/L-40219", standing: "renewable", renewUntil: "2026-12-06" },
        },
        bodyLabel: "Request body (in-process call, same message shape)",
        res: {
          line: "201 Created",
          headers: ["Location: https://library.example/results/r-88", "Content-Type: application/hal+json", PROFILE_LINK, ETAG(RES1)],
          body: {
            _links: { ...CURIES, self: { href: "https://library.example/results/r-88" } },
            loan: "https://library.example/loans/L-40219",
            standing: "renewable",
            renewUntil: "2026-12-06",
          },
        },
      }],
      obs: [
        { t: "A result comes into being: 201, Location names it.", r: ["R1", "H1"] },
        { t: "Runs in-process. Shown as the wire form so the shape can be reviewed.", r: ["R14"] },
      ],
    },
    {
      id: "w10",
      title: "The library delivers: the reply links to the held result",
      exchanges: [{
        from: "Library", to: "Inbox",
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [...BODY, IF_MATCH(REP2), DATE],
        body: {
          outcome: "done",
          applied: ["status", "link"],
          _links: { ...CURIES, "hr:result": { href: "https://library.example/results/r-88" } },
        },
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/hal+json", PROFILE_LINK, "Cache-Control: no-cache", ETAG(REP3)],
          body: {
            state: "answered",
            "hr:delivery": "link",
            applied: ["status", "link"],
            _links: {
              ...CURIES,
              self: { href: "https://assistant.example/replies/7f3a" },
              "hr:result": { href: "https://library.example/results/r-88" },
            },
          },
        },
      }],
      obs: [
        { t: "Responder applied the link, a subset of want.", r: ["A1"] },
        { t: "Only the link travels, not the answer.", r: ["H1"] },
        { t: "Callback carries its own Date.", r: ["R11"] },
      ],
    },
    {
      id: "w11",
      title: "The library marks the watch delivered",
      exchanges: [],
      note: "Once w10 returns 200, the watch moves to delivered. The next read shows it (w13).",
      obs: [],
    },
    {
      id: "w12",
      title: "The assistant fetches the held result",
      exchanges: [{
        from: "Assistant", to: "Library",
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
      ],
    },
    {
      id: "w13",
      title: "The assistant checks the watch again",
      exchanges: [{
        from: "Assistant", to: "Library",
        line: "GET https://library.example/watches/w-88",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(WATCH2),
          body: {
            state: "delivered",
            _links: {
              ...CURIES,
              self: { href: "https://library.example/watches/w-88" },
              "hr:result": { href: "https://library.example/results/r-88" },
            },
          },
        },
      }],
      obs: [
        { t: "Watch shows delivered, and links to the result.", r: ["R2"] },
      ],
    },
    {
      id: "w14",
      title: "The assistant reads its reply after the answer",
      exchanges: [{
        from: "Assistant", to: "Inbox",
        line: "GET https://assistant.example/replies/7f3a",
        headers: [...ACCEPT],
        res: {
          line: "200 OK",
          headers: READ(REP3),
          body: {
            state: "answered",
            "hr:delivery": "link",
            applied: ["status", "link"],
            _links: {
              ...CURIES,
              self: { href: "https://assistant.example/replies/7f3a" },
              "hr:result": { href: "https://library.example/results/r-88" },
            },
          },
        },
      }],
      obs: [
        { t: "No act offered once answered. No _forms.", r: ["R9"] },
        { t: "Reply links to the result. The answer itself is not here.", r: ["H1"] },
      ],
    },
    {
      id: "w15",
      title: "A repeat after a lost answer, with the old validator",
      exchanges: [{
        from: "Library", to: "Inbox",
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [...BODY, IF_MATCH(REP2), DATE],
        body: { outcome: "done", applied: ["status", "link"], _links: { ...CURIES, "hr:result": { href: "https://library.example/results/r-88" } } },
        res: {
          line: "412 Precondition Failed",
          headers: [...PROBLEM],
          body: { type: "https://w3id.org/heart-rise/problems/validator-stale", title: "The reply changed since you read it", status: 412 },
        },
      }],
      obs: [
        { t: "Old validator refused. Nothing applied twice.", r: ["R7"] },
        { t: "Refusal is a problem document.", r: ["R6"] },
        { t: "Library reads again, as in w14.", r: ["R10"] },
      ],
    },
    {
      id: "w16",
      title: "A different answer is refused",
      exchanges: [{
        from: "Library", to: "Inbox",
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [...BODY, IF_MATCH(REP3), DATE],
        body: { outcome: "refused", applied: ["status"] },
        res: {
          line: "409 Conflict",
          headers: [...PROBLEM],
          body: { type: "https://w3id.org/heart-rise/problems/already-answered", title: "This reply is already answered", status: 409, answer: "lib-dlv-90211" },
        },
      }],
      obs: [
        { t: "Deliver is not offered once answered: 409.", r: ["R9"] },
        { t: "answer is an RFC 9457 extension member. Its name is ours.", r: ["R6"] },
      ],
    },
    {
      id: "w17",
      title: "A write with no validator",
      exchanges: [{
        from: "Library", to: "Inbox",
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [...BODY],
        body: { outcome: "done" },
        res: {
          line: "428 Precondition Required",
          headers: [...PROBLEM],
          body: { type: "https://w3id.org/heart-rise/problems/validator-required", title: "This write needs If-Match", status: 428 },
        },
      }],
      obs: [
        { t: "Missing If-Match refused before state is checked.", r: ["R7"] },
      ],
    },
    {
      id: "w18",
      title: "A late answer, to a reply that has expired",
      exchanges: [{
        from: "Library", to: "Inbox",
        line: "POST https://assistant.example/replies/7f41/actions/deliver",
        headers: [...BODY, IF_MATCH(REP4), DATE],
        body: { outcome: "done", applied: ["status", "link"], _links: { ...CURIES, "hr:result": { href: "https://library.example/results/r-89" } } },
        res: {
          line: "409 Conflict",
          headers: [...PROBLEM],
          body: { type: "https://w3id.org/heart-rise/problems/reply-expired", title: "This reply expired before the answer", status: 409, expires: "2026-11-15T09:00:00Z" },
        },
      }],
      obs: [
        { t: "Expired: deliver is not offered. The reason is named.", r: ["R9"] },
      ],
    },
    {
      id: "w19",
      title: "A bare JSON request is refused",
      exchanges: [{
        from: "Assistant", to: "Library",
        line: "GET https://library.example/loans/L-40219",
        headers: ["Accept: application/json"],
        res: {
          line: "406 Not Acceptable",
          headers: [...PROBLEM],
          body: {
            type: "https://w3id.org/heart-rise/problems/not-acceptable",
            title: "No representation this server offers matches Accept",
            status: 406,
            available: ["application/hal+json"],
          },
        },
      }],
      obs: [
        { t: "Bare application/json is not offered. The client is told what is.", r: ["R6"] },
      ],
    },
    {
      id: "w20",
      title: "An unknown profile falls back to the default, and says so",
      exchanges: [{
        from: "Assistant", to: "Library",
        line: "GET https://library.example/loans/L-40219",
        headers: ["Accept: application/hal+json", "Accept-Profile: <https://w3id.org/heart-rise/hal-forms>"],
        res: {
          line: "200 OK",
          headers: READ(LOAN),
          body: {
            state: "renewable",
            _links: { ...CURIES, self: { href: "https://library.example/loans/L-40219" } },
          },
        },
      }],
      obs: [
        { t: "Profile asked for is not the one served. Link names what was served.", r: ["R16"] },
        { t: "Client reads the Link, not the request, to know its profile.", r: ["R16"] },
      ],
    },
    {
      id: "v1",
      title: "Variant: send once, instead of w9 to w14",
      exchanges: [{
        from: "Library", to: "Inbox",
        line: "POST https://assistant.example/replies/7f3a/actions/deliver",
        headers: [...BODY, IF_MATCH(REP2), DATE, `Content-Digest: ${DIGEST}`],
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
            _links: { ...CURIES, self: { href: "https://assistant.example/replies/7f3a" } },
          },
        },
      }],
      obs: [
        { t: "Answer travels once. The reply keeps only the digest.", r: ["S1", "S2"] },
        { t: "Deliver response is not cacheable.", r: ["S3"] },
      ],
    },
  ];

  const OUT_OF_SLICE = [
    "Signatures (signed module): slice 1 has no sender proof beyond the capability address.",
    "CloudEvents, OpenTelemetry, AMQP: modules, later.",
    "Cancelling a watch, and more than one subscriber.",
    "Fan-out, subscriptions, feeds: other variations.",
  ];

  window.WEBHOOK = { RULES, STEPS, OUT_OF_SLICE };
})();
