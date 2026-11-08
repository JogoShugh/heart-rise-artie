// RISE core, the RESTful minimum: the data. Each interaction lists the rules it exercises (see RULES).
// Digests, times and identities are made up; the shapes are the ones the core proposes.
(() => {
  const RULES = [
    ["R1", "Every resource has its own URI. Creation returns 201 with Location."],
    ["R2", "A reply's state is a GET-able representation."],
    ["R3", "GET and HEAD never change state."],
    ["R4", "A state change is a POST to a form target named in the representation. The client builds no URL."],
    ["R5", "Acts go to resource URIs, never to one RPC endpoint."],
    ["R6", "Every message names its media type. Errors are application/problem+json."],
    ["R7", "Responses carry ETag. Writes carry If-Match. A write without it is 428."],
    ["R8", "The client knows only the entry link and relation names."],
    ["R9", "The representation offers only the acts available now."],
    ["R10", "Stateless: each request carries all it needs."],
    ["R11", "A callback stands alone: its own Date and signature window."],
    ["R12", "Reads carry ETag and Cache-Control: no-cache."],
    ["R13", "Acts are not cacheable."],
    ["R14", "Client and server share only links and standard names."],
    ["R15", "Intermediaries are transparent. Nothing needs the path."],
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
        headers: ["Accept: application/json"],
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/json", "Cache-Control: no-cache", "ETag: \"root-1\""],
          body: { _links: { "hr:loans": { href: "https://library.example/loans" } } },
        },
      }],
      obs: [
        { t: "One URL known. Everything else comes from links.", r: ["R8"] },
        { t: "Relation name, not a path.", r: ["R8"] },
        { t: "Safe read.", r: ["R3"] },
        { t: "Entry is a link, not the library's layout.", r: ["R15"] },
      ],
    },
    {
      id: "i2",
      title: "Read the loan; its form says how to act",
      exchanges: [{
        line: "GET https://library.example/loans/L-40219",
        headers: ["Accept: application/json"],
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/json", "Cache-Control: no-cache", "ETag: \"loan-7\""],
          body: {
            state: "renewable",
            title: "The Hobbit",
            _links: { self: { href: "https://library.example/loans/L-40219" } },
            _forms: {
              "hr:tell-me-when-renewable": {
                method: "POST",
                target: "https://library.example/loans/L-40219/actions/tell-me-when-renewable",
                schema: { type: "object", properties: { _rise: { type: "object" } } },
              },
            },
          },
        },
      }],
      obs: [
        { t: "Target comes from the form. The client builds no URL.", r: ["R4"] },
        { t: "Form offered now, because the loan is renewable.", r: ["R9"] },
        { t: "ETag names this representation for the later write.", r: ["R7"] },
        { t: "Read changed nothing.", r: ["R3"] },
      ],
    },
    {
      id: "i3",
      title: "Open a reply on the assistant's inbox",
      exchanges: [{
        line: "POST https://assistant.example/replies",
        headers: ["Content-Type: application/json"],
        body: { want: ["status", "link", "inline"], expires: "2026-11-15T09:00:00Z" },
        bodyLabel: "Request body: the levels accepted, and the expiry",
        res: {
          line: "201 Created",
          headers: ["Location: https://assistant.example/replies/7f3a", "Content-Type: application/json", "ETag: \"rep-1\""],
          body: {
            state: "awaiting",
            want: ["status", "link", "inline"],
            _links: { self: { href: "https://assistant.example/replies/7f3a" } },
            _forms: {
              "hr:deliver": {
                method: "POST",
                target: "https://assistant.example/replies/7f3a/actions/deliver",
                schema: { type: "object", properties: { outcome: { enum: ["done", "refused", "failed"] }, result: { type: "object" }, applied: { type: "array" } } },
              },
              "hr:progress": {
                method: "POST",
                target: "https://assistant.example/replies/7f3a/actions/progress",
                schema: { type: "object", properties: { stage: { type: "string" } } },
              },
              "hr:fail": {
                method: "POST",
                target: "https://assistant.example/replies/7f3a/actions/fail",
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
        headers: ["Content-Type: application/json", "If-Match: \"loan-7\""],
        body: { _rise: { _links: { "hr:reply-to": { href: "https://assistant.example/replies/7f3a" } } } },
        res: {
          line: "201 Created",
          headers: ["Location: https://library.example/watches/w-88", "Content-Type: application/json"],
          body: {
            state: "waiting",
            _links: {
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
        headers: ["Accept: application/json"],
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/json", "Cache-Control: no-cache", "ETag: \"rep-1\""],
          body: {
            state: "awaiting",
            want: ["status", "link", "inline"],
            _forms: {
              "hr:deliver": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/deliver" },
              "hr:progress": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/progress" },
              "hr:fail": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/fail" },
            },
          },
        },
      }],
      obs: [
        { t: "Read before send. The validator is taken here.", r: ["R7"] },
        { t: "Library picks from the forms offered now.", r: ["R9"] },
        { t: "Read changed nothing.", r: ["R3"] },
      ],
    },
    {
      id: "i6",
      title: "Progress while waiting",
      exchanges: [{
        line: "POST https://assistant.example/replies/7f3a/actions/progress",
        headers: ["Content-Type: application/json", "If-Match: \"rep-1\""],
        body: { stage: "waiting for the hold to clear" },
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/json", "ETag: \"rep-2\""],
          body: {
            state: "awaiting",
            stage: "waiting for the hold to clear",
            _forms: {
              "hr:deliver": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/deliver" },
              "hr:progress": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/progress" },
              "hr:fail": { method: "POST", target: "https://assistant.example/replies/7f3a/actions/fail" },
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
          "Content-Type: application/json",
          "If-Match: \"rep-2\"",
          "Date: Sun, 08 Nov 2026 15:40:12 GMT",
          "Content-Digest: sha-256=:bW9jay1kaWdlc3Q=:",
        ],
        body: {
          outcome: "done",
          applied: ["status", "inline"],
          result: { loan: "https://library.example/loans/L-40219", standing: "renewable", renewUntil: "2026-12-06" },
        },
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/json", "Cache-Control: no-store", "ETag: \"rep-3\""],
          body: {
            state: "answered",
            "hr:delivery": "once",
            applied: ["status", "inline"],
            digest: "sha-256=:bW9jay1kaWdlc3Q=:",
            time: "2026-11-08T15:40:12Z",
            _links: { self: { href: "https://assistant.example/replies/7f3a" } },
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
        headers: ["Accept: application/json"],
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/json", "Cache-Control: no-cache", "ETag: \"rep-3\""],
          body: {
            state: "answered",
            "hr:delivery": "once",
            applied: ["status", "inline"],
            digest: "sha-256=:bW9jay1kaWdlc3Q=:",
            time: "2026-11-08T15:40:12Z",
            _links: { self: { href: "https://assistant.example/replies/7f3a" } },
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
          "Content-Type: application/json",
          "If-Match: \"rep-2\"",
          "Date: Sun, 08 Nov 2026 15:46:12 GMT",
          "Content-Digest: sha-256=:bW9jay1kaWdlc3Q=:",
        ],
        body: { outcome: "done", applied: ["status", "inline"], result: { standing: "renewable" } },
        res: {
          line: "412 Precondition Failed",
          headers: ["Content-Type: application/problem+json"],
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
        headers: ["Content-Type: application/json"],
        body: { outcome: "done" },
        res: {
          line: "428 Precondition Required",
          headers: ["Content-Type: application/problem+json"],
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
        headers: ["Content-Type: application/json"],
        body: { want: ["status", "link"], expires: "2026-11-15T09:00:00Z" },
        bodyLabel: "Request body: the levels accepted, and the expiry",
        res: {
          line: "201 Created",
          headers: ["Location: https://assistant.example/replies/7f40", "Content-Type: application/json", "ETag: \"rep-1\""],
          body: {
            state: "awaiting",
            want: ["status", "link"],
            _links: { self: { href: "https://assistant.example/replies/7f40" } },
            _forms: {
              "hr:deliver": {
                method: "POST",
                target: "https://assistant.example/replies/7f40/actions/deliver",
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
        headers: ["Content-Type: application/json", "If-Match: \"rep-1\""],
        body: {
          outcome: "done",
          applied: ["status", "link"],
          _links: { "hr:result": { href: "https://library.example/results/r-88" } },
        },
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/json", "Cache-Control: no-cache", "ETag: \"rep-2\""],
          body: {
            state: "answered",
            "hr:delivery": "link",
            applied: ["status", "link"],
            _links: {
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
        headers: ["Accept: application/json"],
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/json", "Cache-Control: no-cache", "ETag: \"res-1\""],
          body: {
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
    "Offered forms follow state: I3 offers deliver, progress and fail. I8 offers none.",
    "Send-once keeps the digest and says once: I7, I8.",
    "Held answer is GET-able with no-cache: I13.",
    "No caller internals in any body.",
  ];

  window.CORE = { RULES, STEPS, CHECKS };
})();
