// RISE minimal, end to end, envisioned: every request and response, in order. Data only; page.js renders it.
// Representations are HAL Schema Forms (SPEC §6.1). Act bodies are application/hal+json under the profile. The envelope
// is the request's own _links, with hr: relations. There is no _rise member: underscore names belong to the format only.
// Keys, digests, times and identities are made up.
(() => {
  const PROFILE = "https://w3id.org/heart-rise/hal-schema-forms";
  const PROFILE_LINK = `Link: <${PROFILE}>; rel="profile"`;
  const ACCEPT = ["Accept: application/hal+json", `Accept-Profile: <${PROFILE}>`];
  const REQ = ["Content-Type: application/hal+json", ...ACCEPT];
  const CURIES = {
    curies: [
      { name: "hr", href: "https://w3id.org/heart-rise/rels/{rel}", templated: true },
      { name: "job", href: "https://target.example/rels/{rel}", templated: true },
    ],
  };
  const READ = (tag) => ["Content-Type: application/hal+json", PROFILE_LINK, "Vary: Accept, Prefer, Accept-Profile", "Cache-Control: no-cache", `ETag: "${tag}"`];
  const BODY = ["Content-Type: application/hal+json", PROFILE_LINK];
  const ACT = (tag) => ["Content-Type: application/hal+json", PROFILE_LINK, `ETag: "${tag}"`, "Cache-Control: no-store"];
  const IF_MATCH = (tag) => `If-Match: "${tag}"`;
  const PROBLEM = ["Content-Type: application/problem+json"];
  const DIGEST = "Content-Digest: sha-256=:bW9jay1kaWdlc3Q=:";
  const SIG_INPUT = 'Signature-Input: sig1=("@method" "@target-uri" "content-digest" "if-match");created=1794153600;keyid="target-2026-11";alg="ed25519"';
  const SIG = "Signature: sig1=:MEUCIQDk3Vb0xKqy8w==:";

  // Validators, as the representations name them: seq-<version>.hsf.L2.<digest>.
  const ENTRY = "seq-1.hsf.L2.a3f0b8c2d6e1974f";
  const KEYS_T = "seq-1.hsf.L2.5b9e1d4a7c2f0e83";
  const KEYS_C = "seq-1.hsf.L2.7d2a6f0c9e4b1358";
  const REPLY1 = "seq-1.hsf.L2.4c8a1d7e0b3f9265";
  const REPLY2 = "seq-2.hsf.L2.b07e5a2c9d1f4863";
  const REPLY3 = "seq-3.hsf.L2.e9d2c6f1a4b08537";
  const REPLY_URL = "https://caller.example/replies/r-7f3";

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

  // The forms the reply offers while awaiting. Schemas shortened to the members that matter here.
  const OPEN_FORMS = {
    "hr:progress": {
      method: "POST",
      _links: { target: { href: `${REPLY_URL}/actions/progress` } },
      contentType: "application/hal+json",
      schema: { required: ["percent"], properties: { percent: { type: "integer", minimum: 0, maximum: 100 }, note: { type: "string" } } },
    },
    "hr:completed": {
      method: "POST",
      _links: { target: { href: `${REPLY_URL}/actions/completed` } },
      contentType: "application/hal+json",
      schema: { required: ["result"], properties: { result: { type: "object" } } },
    },
    "hr:failed": {
      method: "POST",
      _links: { target: { href: `${REPLY_URL}/actions/failed` } },
      contentType: "application/problem+json",
      schema: {
        required: ["title"],
        properties: { type: { type: "string", format: "uri" }, title: { type: "string" }, status: { type: "integer" }, detail: { type: "string" } },
      },
    },
  };

  const REPLY_AWAITING = { state: "awaiting", _links: { ...CURIES, self: { href: REPLY_URL } }, _forms: OPEN_FORMS };
  const REPLY_PROGRESS = {
    state: "awaiting",
    progress: { percent: 40, note: "3 of 8 resized" },
    _links: { ...CURIES, self: { href: REPLY_URL } },
    _forms: OPEN_FORMS,
  };
  const REPLY_ANSWERED = {
    state: "answered",
    _links: { ...CURIES, self: { href: REPLY_URL } },
    _forms: {},
  };

  // Target's entry: the job form, and the link to Target's signing key.
  const ENTRY_BODY = {
    _links: { ...CURIES, "hr:keys": { href: "https://target.example/keys", type: "application/jwk-set+json" } },
    _forms: {
      "job:assign": {
        method: "POST",
        _links: { target: { href: "https://target.example/jobs" } },
        contentType: "application/hal+json",
        schema: {
          required: ["job"],
          properties: {
            _links: {
              type: "object",
              properties: {
                "hr:reply-to": {
                  type: "object",
                  required: ["href", "type", "profile"],
                  properties: {
                    href: { type: "string", format: "uri" },
                    type: { const: "application/hal+json" },
                    profile: { enum: ["https://w3id.org/heart-rise/hal-schema-forms", "https://w3id.org/heart-rise/hal-forms"] },
                  },
                },
                "hr:keys": {
                  type: "object",
                  required: ["href", "type"],
                  properties: {
                    href: { type: "string", format: "uri" },
                    type: { const: "application/jwk-set+json" },
                  },
                },
              },
            },
            job: { type: "string" },
            modules: { type: "array", items: { enum: ["signed"] } },
          },
        },
      },
    },
  };

  // The caller's request body: its reply and its key, as links; the job; the modules it accepts.
  const ACT_BODY = {
    _links: {
      ...CURIES,
      "hr:reply-to": { href: REPLY_URL, type: "application/hal+json", profile: PROFILE },
      "hr:keys": { href: "https://caller.example/keys", type: "application/jwk-set+json" },
    },
    job: "resize-cover-photo",
    modules: ["signed"],
  };

  const JOB_BODY = {
    state: "working",
    _links: {
      ...CURIES,
      self: { href: "https://target.example/jobs/j-88" },
      "hr:reply-to": { href: REPLY_URL, type: "application/hal+json", profile: PROFILE },
      "hr:keys": { href: "https://caller.example/keys", type: "application/jwk-set+json" },
    },
  };

  const KEYS_CALLER = { keys: [{ kty: "OKP", crv: "X25519", kid: "caller-2026-11", use: "enc", x: "…" }] };
  const KEYS_TARGET = { keys: [{ kty: "OKP", crv: "Ed25519", kid: "target-2026-11", use: "sig", x: "…" }] };
  const PROGRESS_BODY = { percent: 40, note: "3 of 8 resized" };
  const COMPLETED_BODY = { result: { href: "https://target.example/images/cover-800.jpg" } };
  const PROBLEM_REPLY = { title: "Not offered", status: 409, detail: "Reply r-7f3 is answered. hr:completed is not offered." };
  const PROBLEM_JOB = { title: "Not offered", status: 409, detail: "Reply r-7f3 already has job j-88." };

  const STEPS = [
    {
      id: "m1",
      title: "Caller creates its reply",
      note: "The reply exists before Target hears of it. Target can only send to an address that is already live.",
      exchanges: [{
        from: "Caller", to: "Caller",
        line: "POST https://caller.example/replies",
        headers: [...ACCEPT],
        res: { line: "201 Created", headers: [`Location: ${REPLY_URL}`, ...READ(REPLY1)], body: REPLY_AWAITING },
      }],
      obs: [
        { t: "Creation answers 201 with Location. The reply is a resource with its own URI.", r: ["R1"] },
        { t: "State is a GET-able representation, so Target can read it before it sends.", r: ["R2"] },
        { t: "Only the acts that fit the state are offered: progress, completed and failed, while awaiting.", r: ["R9"] },
      ],
    },
    {
      id: "m2",
      title: "Caller publishes its encryption key",
      note: "Served as its own resource. Slice 1 reads it nowhere.",
      exchanges: [{
        from: "Caller", to: "Caller",
        line: "GET https://caller.example/keys",
        headers: ["Accept: application/jwk-set+json"],
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/jwk-set+json", "Cache-Control: no-cache", `ETag: "${KEYS_C}"`],
          body: KEYS_CALLER,
        },
      }],
      obs: [
        { t: "X25519 key with use enc. It waits for encryption (decision 2).", r: [] },
        { t: "A JWK Set has its own registered media type (RFC 7517). It is not HAL, so R6's hal+json rule does not cover it. Stated here, not hidden.", r: ["R6"] },
        { t: "Read like any resource: strong ETag, revalidated on every read.", r: ["R7", "R12"] },
      ],
    },
    {
      id: "m3",
      title: "Caller reads Target's entry",
      note: "The job form comes from the entry. Caller builds no URL.",
      exchanges: [{
        from: "Caller", to: "Target",
        line: "GET https://target.example/",
        headers: [...ACCEPT],
        res: { line: "200 OK", headers: READ(ENTRY), body: ENTRY_BODY },
      }],
      obs: [
        { t: "The act is a form under its relation name, job:assign. Caller finds it by name.", r: ["R8", "R4"] },
        { t: "The form's schema declares _links, with hints. The reply link carries its media type and profile, so Target knows what to read and which dialect to ask for before it sends.", r: ["R14", "R16"] },
        { t: "Target's signing key is linked as hr:keys. Caller uses it at step 9.", r: [] },
      ],
    },
    {
      id: "m4",
      title: "Caller sends the job, with its reply and key as links",
      note: "The request body is a HAL representation. Its links are Caller's own, sent to Target.",
      exchanges: [{
        from: "Caller", to: "Target",
        line: "POST https://target.example/jobs",
        headers: [...REQ, IF_MATCH(ENTRY)],
        body: ACT_BODY,
        bodyLabel: "Request body: the job, its reply and its key, as links",
        res: { line: "202 Accepted", headers: ["Location: https://target.example/jobs/j-88", ...BODY], body: JOB_BODY },
      }],
      obs: [
        { t: "Body is application/hal+json under the profile (decision 1, decided).", r: ["R6"] },
        { t: "Accepted, not finished. The Location is the job. Caller does not have to read it.", r: ["R1"] },
        { t: "If-Match is the entry's tag from step 3. A stale tag gets 412.", r: ["R7"] },
        { t: "Target stores Caller's links and links them back on the job. Both sides use the same pattern.", r: ["R14"] },
        { t: "Caller's key is passed, not read. Signing uses only Target's key in slice 1 (decision 2).", r: [] },
        { t: "No wrapper. The envelope is the request's own _links, and modules is a plain property the schema declares (decision 3).", r: ["R14"] },
      ],
    },
    {
      id: "m5",
      title: "Target reads the reply before it sends",
      note: "Target sends only to a form it has just read.",
      exchanges: [{
        from: "Target", to: "Caller",
        line: `GET ${REPLY_URL}`,
        headers: [...ACCEPT],
        res: { line: "200 OK", headers: READ(REPLY1), body: REPLY_AWAITING },
      }],
      obs: [
        { t: "The reply is awaiting. Progress, completed and failed are offered.", r: ["R9"] },
        { t: "Reads carry no-cache and a strong ETag, so Target always sees the current state.", r: ["R12", "R7"] },
      ],
    },
    {
      id: "m6",
      title: "Target reports progress",
      note: "Progress does not end the job. The reply stays awaiting.",
      exchanges: [{
        from: "Target", to: "Caller",
        line: `POST ${REPLY_URL}/actions/progress`,
        headers: [...REQ, IF_MATCH(REPLY1)],
        body: PROGRESS_BODY,
        bodyLabel: "Request body: progress",
        res: { line: "200 OK", headers: ACT(REPLY2), body: REPLY_PROGRESS },
      }],
      obs: [
        { t: "The act goes to the form target the reply names. Target builds no URL.", r: ["R4"] },
        { t: "Each change moves the ETag. Now seq-2.", r: ["R7"] },
        { t: "The act response is not cacheable.", r: ["R13"] },
        { t: "A lost progress costs nothing. The next progress replaces it.", r: [] },
      ],
    },
    {
      id: "m7",
      title: "Target reads the reply again",
      note: "The ETag moved after progress. The outcome must carry the new one.",
      exchanges: [{
        from: "Target", to: "Caller",
        line: `GET ${REPLY_URL}`,
        headers: [...ACCEPT],
        res: { line: "200 OK", headers: READ(REPLY2), body: REPLY_PROGRESS },
      }],
      obs: [
        { t: "An outcome sent with the old tag gets 412.", r: ["R7"] },
      ],
    },
    {
      id: "m8",
      title: "Target sends the result, signed",
      note: "The reply moves to answered. Its forms go. The form used is the outcome: hr:completed here, not a field.",
      exchanges: [{
        from: "Target", to: "Caller",
        line: `POST ${REPLY_URL}/actions/completed`,
        headers: [...REQ, IF_MATCH(REPLY2), DIGEST, SIG_INPUT, SIG],
        body: COMPLETED_BODY,
        bodyLabel: "Request body: the result",
        res: { line: "200 OK", headers: ACT(REPLY3), body: REPLY_ANSWERED },
      }],
      obs: [
        { t: "Signed with Target's Ed25519 key (RFC 9421). The signature covers method, target URI, digest and If-Match.", r: [] },
        { t: "Content-Digest covers the body (RFC 9530). Change one byte and the signature fails.", r: [] },
        { t: "Answered: hr:completed and hr:failed are both gone from _forms. Nothing more can be sent. The reply keeps its state, not the result.", r: ["R9", "S1", "S2"] },
        { t: "The response is not cacheable.", r: ["S3", "R13"] },
        { t: "result is free-shaped. Here a URL. A paint job would send a color. Caller reads the shape from the job's form.", r: [] },
      ],
    },
    {
      id: "m9",
      title: "Caller checks the signature",
      note: "Caller reads Target's key once, from the hr:keys link in step 3. Then it verifies locally.",
      exchanges: [{
        from: "Caller", to: "Target",
        line: "GET https://target.example/keys",
        headers: ["Accept: application/jwk-set+json"],
        res: {
          line: "200 OK",
          headers: ["Content-Type: application/jwk-set+json", "Cache-Control: no-cache", `ETag: "${KEYS_T}"`],
          body: KEYS_TARGET,
        },
      }],
      obs: [
        { t: "Revalidated on each use with If-None-Match. A 304 costs little.", r: ["R12"] },
        { t: "Signature passes: accept the outcome. Fails: refuse it. Nothing is sent back.", r: [] },
        { t: "Target fetched nothing from Caller in slice 1, so no caller-supplied URL has been fetched yet.", r: [] },
      ],
    },
    {
      id: "m10",
      title: "Target repeats the result, and is refused",
      note: "A retry after a lost response. Refused by state, not applied twice.",
      exchanges: [{
        from: "Target", to: "Caller",
        line: `POST ${REPLY_URL}/actions/completed`,
        headers: [...REQ, IF_MATCH(REPLY3), DIGEST, SIG_INPUT, SIG],
        body: COMPLETED_BODY,
        bodyLabel: "Request body: the result again",
        res: { line: "409 Conflict", headers: PROBLEM, body: PROBLEM_REPLY },
      }],
      obs: [
        { t: "Once answered, the form is gone. Its target answers 409, not a second send.", r: ["S1", "S2", "R9"] },
        { t: "Convention, not a new standard: Target reads 409 on an answered reply with the same result as success.", r: [] },
      ],
    },
    {
      id: "m11",
      title: "Caller retries the job, and is refused",
      note: "A retry after a lost response. One reply serves one job.",
      exchanges: [{
        from: "Caller", to: "Target",
        line: "POST https://target.example/jobs",
        headers: [...REQ, IF_MATCH(ENTRY)],
        body: ACT_BODY,
        bodyLabel: "Request body: the job again",
        res: { line: "409 Conflict", headers: PROBLEM, body: PROBLEM_JOB },
      }],
      obs: [
        { t: "Refused by state: the reply already has job j-88. No second job exists.", r: ["R9"] },
        { t: "Caller keeps the job it has, j-88. It does not send again.", r: [] },
      ],
    },
  ];

  const OPEN = [
    "Decision 1, act body media type: application/hal+json (option B). Decided.",
    "Decision 2, encryption: carry Caller's key unused in slice 1, or add JWE (RFC 7516) now. The author decides.",
    "Decision 3, envelope placement: decided, no _rise member. The envelope is top-level _links with hr: relations. modules is a plain property the act's schema declares. SPEC §7.2 needs rewriting to match.",
    "Decision 4, relation names hr:progress, hr:completed, hr:failed, hr:reply-to, hr:keys. Not yet checked against the IANA registry.",
    "Decision 5, where a job declares its result shape. Proposed: on the job form.",
    "Decision 6, outcome: decided. Split into hr:completed and hr:failed, each a form offered only while awaiting, instead of one hr:outcome with an outcome field. The failure body is application/problem+json. Relations, not keys.",
  ];

  const OUT_OF_SLICE = [
    "Held mode (H1): Target keeps the outcome and the reply links to it. Slice 1 sends it.",
    "History: progress and outcome events as a collection, linked as hr:history. Slice 1 keeps the latest state only.",
    "Job retry: job:retry on a failed job. A domain act on the job, not part of RISE.",
    "Caller cancel: an act on the job, not on the reply.",
    "Input relation hr:input: Target asks Caller something. Same pattern, another relation on the reply.",
    "Encryption: JWE (RFC 7516) to Caller's key. Slice 1 carries the key and does not use it.",
    "Fetching a key a caller supplied. None in slice 1. When it arrives: https only, a short timeout, no private addresses.",
    "Failure path: hr:failed with an application/problem+json body. Its form is shown; the exchange is not walked in this report.",
    "Expiry: the reply expires, its forms go, a late outcome is refused.",
    "Other modules: ce (CloudEvents headers), otel (trace context), amqp (broker binding).",
    "HAL-FORMS _templates alongside _forms. Same forms, another rendering.",
    "SPEC §7.2 envelope fields template-rel, template-schema and template-fields. This design does not carry them yet.",
  ];

  // Our review of the design, long form. Each paragraph tests one part against a sentence from Roy Fielding's post
  // "REST APIs must be hypertext-driven" (20 October 2008). The quotes are his. The judgements are ours.
  const REVIEW = {
    heading: "Review against the 2008 constraints",
    lede: "A critique of this design, in our voice. Fielding's sentences are quoted from his post, roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven. The judgements are ours, not his.",
    paras: [
      "What holds first. The design is driven by hypertext. The caller learns where to send from the entry's form. The target learns where to deliver from the reply it reads first, and what it may send from that reply's forms. Nothing is hard-coded on the target beyond what the caller sent. The post says that if the engine of application state is not driven by hypertext, the API cannot be RESTful. Here the engine is the forms, so this part passes.",
      "The weakest step is 10. The target reads a 409 on an answered reply with the same result as success. No representation says so. The target knows it because this report says so, which is out-of-band information. The post is blunt on that point: \"Failure here implies that out-of-band information is driving interaction instead of hypertext.\" The fix is to put the answer in the refusal. The 409 body links to the outcome already recorded, so the target reads the result rather than infers it.",
      "The relation names are not yet defined. hr:progress, hr:completed, hr:failed, hr:reply-to and hr:keys appear in forms and links, but their meaning lives here, in this report. The post asks that a REST API \"spend almost all of its descriptive effort in defining the media type(s) used for representing\" resources, and extended relation names with it. Each name needs a published definition, an ALPS descriptor or profile text, before the design is done. Until then a client reads the name and has to guess.",
      "modules sits in the form, so a reader sees it before it acts. Good. But the reply does not say which modules it applied. A client reads the reply and cannot tell whether the outcome is signed unless it remembers what it asked for. The reply should report the modules it applied, as the level report does (A1). Otherwise the answer depends on memory the representation does not carry.",
      "The target uses no fixed path for the callback. It follows the link the caller sent. The cost falls on the caller. To receive a result, the caller must run a hypermedia server with a reply resource, forms and validators. That is more than a webhook receiver. It is the price of symmetry, and the spec should say so rather than leave it implied. The post opens with the entry rule: a REST API should be entered \"with no prior knowledge beyond the initial URI (bookmark)\". A caller that takes callbacks needs its own entry point too.",
      "Signature and digest are message integrity. They are real protections, and the post says nothing about them. They do not make a message more hypermedia, and the design should not claim they do. Trust is weaker than the signature suggests. The anchor today is the link the caller sent, plus the caller's own word about its key. Nothing checks that the key at that link belongs to the caller. That is an identity question, separate from the REST constraints, and it stays open.",
      "What we would change, in order: put the outcome link in the 409 body; publish a definition for each hr: relation; have the reply report the modules it applied; state the caller's server cost in the spec; and split integrity from identity in the security section. None of these changes the shape of the messages shown above. They change what the messages say to a client that has not read this report.",
    ],
  };

  window.MINIMAL = { RULES, STEPS, OPEN, OUT_OF_SLICE, REVIEW };
})();
