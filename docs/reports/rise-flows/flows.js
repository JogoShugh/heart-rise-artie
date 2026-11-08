// RISE, end to end: every variation as messages between parties, with each message's lines and body.
// A mockup, not a capture: nothing here was run. Digests, tags, tokens and times are made up; the shapes follow
// HEART as built (HAL Schema Forms, hr: relations, RFC 9457 refusals) and spec/ideas/rise-deep-dive.md.
//
// A flow is lanes and steps. A step is a message { from, to, line, h, body, note, key, diff, kind },
// a note { at, note }, or a section { section }. `key` names a message's body; `diff` shows a body as a change
// from the keyed one. `kind` is "amqp" for broker frames and "call" for a model call; otherwise HTTP.
(() => {
  const HR = { name: "hr", href: "https://w3id.org/heart-rise/rels/{rel}", templated: true };
  const LIB = { name: "library", href: "/profiles/library#{rel}", templated: true };
  const LDP = "http://www.w3.org/ns/ldp#";
  const ENVELOPE = "https://w3id.org/heart-rise/schemas/envelope";
  const SRI = {
    replyForms: "sha256-nEHiK2v0lJ8t…dLhT0eQx4s=",
    replyForms2: "sha256-UdCqXk1ePw0r…7GmYbH2sVo=",
    clubForms: "sha256-K3dT0pQm8wZs…r1Xh2nYc5A=",
    envelope: "sha256-q8LmT3fd0Hs2…wP4Zk9aRb8=",
    unavailable: "sha256-Yb3cR8kq0LfT…M2pW7xVn4E=",
    schema: "sha256-4Wdl6B35t29f…GWGvIb7x5yQ=",
  };
  const SOURCE = "library:tell-me-when-renewable@sha256-d6qrW1sZ…u8Lk0=";
  const T = (h, m, s = 0, d = 7, mo = 11) =>
    `2026-${String(mo).padStart(2, "0")}-${String(d).padStart(2, "0")}T${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}Z`;
  const HAL = { Accept: "application/hal+json" };
  const JSONC = { "Content-Type": "application/json" };
  const etag = (tag) => ({ ETag: `"${tag}"` });
  const ifMatch = (tag) => ({ "If-Match": `"${tag}"` });
  const ifNone = (tag) => ({ "If-None-Match": `"${tag}"` });
  const problem = (type, title, status, more = {}) =>
    ({ type: `https://w3id.org/heart-rise/problems/${type}`, title, status, ...more });
  const PROBLEM = { "Content-Type": "application/problem+json" };

  // ---------------------------------------------------------------------------------------------------------
  // The library's resources.

  const riseField = {
    title: "Reply link",
    description: "Where to answer once the loan can be renewed: a RISE envelope. Delivered to https and amqp addresses only.",
    allOf: [
      { $ref: ENVELOPE, "hr:integrity": SRI.envelope },
      { properties: { _links: { properties: { "hr:reply-to": { properties: { href: { pattern: "^(https|amqp)://" } } } } } } },
    ],
  };

  const loanHeld = {
    id: 3310, title: "The Hobbit", author: "J. R. R. Tolkien", due: "2026-11-12", renewals: 0, standing: "hold waiting",
    _links: {
      self: { href: "/loans/3310" },
      curies: [LIB, HR],
      "hr:unavailable": { href: "/unavailable/61bc1cd6003e61f0", integrity: SRI.unavailable },
    },
    _forms: {
      "library:tell-me-when-renewable": {
        title: "Tell me when I can renew",
        description: "Watches this loan and answers once it can be renewed. Give a reply link in _rise to be told; leave it out and read the watch instead.",
        method: "POST",
        contentType: "application/json",
        _links: { target: { href: "/loans/3310/actions/tell-me-when-renewable" } },
        schema: { type: "object", properties: { _rise: riseField } },
      },
      "library:report-problem": {
        method: "POST",
        _links: { target: { href: "/loans/3310/actions/report-problem" } },
        schema: { $ref: "/schemas/4c0d1e2f9a7b3c55", "hr:integrity": SRI.schema },
      },
      "library:return": { method: "POST", _links: { target: { href: "/loans/3310/actions/return" } } },
    },
  };

  const loanRenewable = {
    id: 3310, title: "The Hobbit", author: "J. R. R. Tolkien", due: "2026-11-12", renewals: 0, standing: "renewable",
    _links: {
      self: { href: "/loans/3310" },
      curies: [LIB, HR],
      "hr:unavailable": { href: "/unavailable/0a93be4471c2d8e6", integrity: SRI.unavailable },
    },
    _forms: {
      "library:renew": { title: "Renew", description: "Keeps the book three weeks more.", method: "POST", _links: { target: { href: "/loans/3310/actions/renew" } } },
      "library:report-problem": {
        method: "POST",
        _links: { target: { href: "/loans/3310/actions/report-problem" } },
        schema: { $ref: "/schemas/4c0d1e2f9a7b3c55", "hr:integrity": SRI.schema },
      },
      "library:return": { method: "POST", _links: { target: { href: "/loans/3310/actions/return" } } },
    },
  };

  const loanRenewed = { ...loanRenewable, due: "2026-12-03", renewals: 1 };

  const watch88 = {
    id: 88, status: "waiting", until: "the loan can be renewed",
    opened: T(21, 4, 12), expires: "2026-12-07T00:00:00Z",
    _links: {
      self: { href: "/watches/88" },
      curies: [HR],
      "hr:about": { href: "/loans/3310", title: "The Hobbit" },
      "hr:reply-to": { href: "https://assistant.example/replies/7f3a" },
      "hr:mapping": { href: "/mappings/12" },
    },
    _forms: { "hr:cancel": { title: "Stop watching", method: "POST", _links: { target: { href: "/watches/88/actions/cancel" } } } },
  };

  const watch88Mapping = {
    ...watch88,
    status: "waiting for its mapping",
    waitingOn: "Mapping 12 is proposed and not yet approved.",
  };

  const titleHobbit = {
    id: "hobbit", title: "The Hobbit", author: "J. R. R. Tolkien", copies: 4, onShelf: 0, holds: 2,
    _links: {
      self: { href: "/titles/hobbit" },
      curies: [LIB, HR],
      "hr:events": [
        { href: "/titles/hobbit/events", type: "application/hal+json", title: "Changes, newest first" },
        { href: "amqp://broker.example/library.titles", title: "Changes as messages", "hr:routing": "titles.hobbit.#" },
      ],
    },
    _forms: {
      "library:bring-a-copy": {
        title: "Bring a copy from another branch",
        description: "Sends a copy on the van to the branch you pick it up at. Usually a day.",
        method: "POST",
        contentType: "application/json",
        _links: { target: { href: "/titles/hobbit/actions/bring-a-copy" } },
        schema: {
          type: "object", required: ["from", "pickUpAt"],
          properties: {
            from: { type: "string", title: "From branch", enum: ["harbor-point", "eastside"] },
            pickUpAt: { type: "string", title: "Pick up at", enum: ["riverside", "eastside"] },
          },
        },
      },
      "hr:subscribe": {
        title: "Tell me about changes",
        description: "Sends a short notice each time one of the chosen kinds of change happens: to your inbox, or to a queue the library makes for you. Lasts the days you ask, up to 90, unless renewed.",
        method: "POST",
        contentType: "application/json",
        _links: { target: { href: "/titles/hobbit/actions/subscribe" } },
        schema: {
          type: "object", required: ["kinds"],
          properties: {
            kinds: {
              type: "array", title: "Kinds of change", minItems: 1,
              items: { type: "string", format: "uri" },
              "hr:options": { link: { href: "/event-kinds" }, items: "/kinds", valueField: "rel", promptField: "title" },
            },
            leaseDays: { type: "integer", title: "For how many days", minimum: 1, maximum: 90, default: 30 },
            delivery: { type: "string", title: "Deliver to", enum: ["inbox", "queue"], default: "inbox" },
            _rise: { $ref: ENVELOPE, "hr:integrity": SRI.envelope, title: "Your inbox", description: "Needed when delivering to an inbox." },
          },
          if: { properties: { delivery: { const: "inbox" } } },
          then: { required: ["_rise"] },
        },
      },
    },
  };

  const titleOnShelf = { ...titleHobbit, onShelf: 1 };

  const hint = (id, time, version) => ({
    specversion: "1.0",
    id,
    source: "https://library.example/titles/hobbit",
    type: "https://library.example/profiles/library#copy-on-shelf",
    subject: "/titles/hobbit",
    time,
    dataref: "https://library.example/titles/hobbit",
    etag: `"${version}"`,
  });

  const feed = (items, newest) => ({
    title: "The Hobbit: changes",
    _links: {
      self: { href: "/titles/hobbit/events" },
      curies: [LIB, HR],
      "hr:about": { href: "/titles/hobbit" },
      next: { href: `/titles/hobbit/events?before=${items[items.length - 1].id}`, title: "Older" },
    },
    _embedded: { item: items },
    newest,
  });
  const ev = (id, kind, time, version) => ({
    id, type: `https://library.example/profiles/library#${kind}`, time,
    _links: { "hr:about": { href: "/titles/hobbit" } }, etag: `"${version}"`,
  });
  const feedOld = feed([ev("evt-90210", "hold-placed", T(11, 40, 3, 8), "t9"), ev("evt-90207", "copy-checked-out", T(9, 12, 51, 8), "t8")], "evt-90210");
  const feedNew = feed([
    ev("evt-90211", "copy-on-shelf", T(15, 2, 44, 8), "t10"),
    ev("evt-90210", "hold-placed", T(11, 40, 3, 8), "t9"),
    ev("evt-90207", "copy-checked-out", T(9, 12, 51, 8), "t8"),
  ], "evt-90211");

  // ---------------------------------------------------------------------------------------------------------
  // Ana's assistant's inbox and its replies.

  const inbox = {
    title: "Ana's replies", status: "taking replies", awaiting: 1,
    _links: {
      self: { href: "/inbox" },
      curies: [HR],
      item: [{ href: "/replies/6d02", title: "Interlibrary loan of Dune" }],
    },
    _forms: {
      "hr:open-reply": {
        title: "Open a reply",
        description: "Brings a reply into being: a resource another service answers once, through the form its shape gives it. Its address goes in a _rise envelope.",
        method: "POST",
        contentType: "application/json",
        _links: { target: { href: "/inbox/actions/open-reply" } },
        schema: {
          type: "object", required: ["about", "shape"],
          properties: {
            about: { type: "string", title: "About", description: "What the reply will answer, in Ana's words", maxLength: 120 },
            shape: { type: "string", title: "Shape", description: "The fields the answer must have", enum: ["loan-standing", "done-or-not"] },
            expires: { type: "string", format: "date-time", title: "Expires", description: "After this the reply takes no answer" },
          },
        },
      },
    },
  };

  const replyAwaiting = {
    id: "7f3a", status: "awaiting", about: "Renewing The Hobbit at Riverside", shape: "loan-standing",
    opened: T(21, 4, 10), expires: "2026-12-07T00:00:00Z",
    _links: {
      self: { href: "/replies/7f3a" },
      curies: [HR],
      "hr:forms": { href: "/forms/9c41e2", integrity: SRI.replyForms },
      [`${LDP}inbox`]: { href: "/replies/7f3a" },
    },
  };

  const replyAnswered = {
    id: "7f3a", status: "answered", about: "Renewing The Hobbit at Riverside", shape: "loan-standing",
    opened: T(21, 4, 10), expires: "2026-12-07T00:00:00Z", answered: T(21, 31, 5), answeredBy: "library.example",
    answer: { loan: "https://library.example/loans/3310", standing: "renewable" },
    _links: {
      self: { href: "/replies/7f3a" },
      curies: [HR],
      "hr:unavailable": { href: "/unavailable/0d5f7a1e", integrity: SRI.unavailable },
      [`${LDP}inbox`]: { href: "/replies/7f3a" },
    },
  };

  const replyExpired = {
    id: "7f3a", status: "expired", about: "Renewing The Hobbit at Riverside", shape: "loan-standing",
    opened: T(21, 4, 10), expires: "2026-12-07T00:00:00Z",
    _links: {
      self: { href: "/replies/7f3a" },
      curies: [HR],
      "hr:unavailable": { href: "/unavailable/5e21c0b9", integrity: SRI.unavailable },
    },
  };

  const replyFailed = {
    ...replyExpired,
    status: "failed",
    failed: T(21, 31, 5), failedBy: "library.example",
    reason: "The library can't give this loan's standing in your reply's words: it reads lost, and the mapping has no value for it.",
    _links: { ...replyExpired._links, "hr:unavailable": { href: "/unavailable/77c3a1d0", integrity: SRI.unavailable } },
  };

  const deliverSchema = {
    $schema: "https://json-schema.org/draft/2020-12/schema",
    type: "object",
    required: ["loan", "standing"],
    properties: {
      loan: { type: "string", format: "uri", title: "Loan", description: "The loan's address at the library" },
      standing: {
        type: "string", title: "Standing", description: "Whether Ana can renew it now",
        enum: ["renewable", "held", "at-limit", "returned"],
      },
      note: { type: "string", title: "Note", description: "Anything Ana should read", maxLength: 280 },
    },
  };

  const formSet = (schema) => ({
    _links: { self: { href: "/forms/9c41e2" }, curies: [HR] },
    _forms: {
      "hr:deliver": {
        title: "Deliver the answer",
        description: "Answers this reply, once: the loan, and whether Ana can renew it now.",
        method: "POST",
        contentType: "application/json",
        _links: { target: { href: "/replies/{id}/actions/deliver", templated: true } },
        "hr:templatePointers": { id: "/id" },
        schema,
      },
      "hr:progress": {
        title: "Say how it's going",
        description: "A short note while the answer isn't ready. The reply stays awaiting.",
        method: "POST",
        contentType: "application/json",
        _links: { target: { href: "/replies/{id}/actions/progress", templated: true } },
        "hr:templatePointers": { id: "/id" },
        schema: { type: "object", required: ["note"], properties: { note: { type: "string", title: "Note", maxLength: 280 } } },
      },
      "hr:fail": {
        title: "Say it can't be answered",
        description: "Ends the reply without an answer, with the reason Ana will read.",
        method: "POST",
        contentType: "application/json",
        _links: { target: { href: "/replies/{id}/actions/fail", templated: true } },
        "hr:templatePointers": { id: "/id" },
        schema: { type: "object", required: ["reason"], properties: { reason: { type: "string", title: "Reason", maxLength: 280 } } },
      },
    },
  });
  const forms9c = formSet(deliverSchema);
  const forms51 = (() => {
    const set = formSet({
      ...deliverSchema,
      properties: {
        ...deliverSchema.properties,
        dueBy: { type: "string", format: "date", title: "Due by", description: "When the book is due now" },
      },
    });
    set._links.self.href = "/forms/51d0aa";
    return set;
  })();

  const envelope = (reply, forms = "https://assistant.example/forms/9c41e2", integrity = SRI.replyForms) => ({
    _rise: { expires: "2026-12-07T00:00:00Z", _links: { "hr:reply-to": { href: reply }, "hr:forms": { href: forms, integrity } } },
  });

  const unavailableAnswered = {
    unavailable: [
      { rel: "https://w3id.org/heart-rise/rels/deliver", title: "Deliver the answer", reason: "This reply has been answered." },
      { rel: "https://w3id.org/heart-rise/rels/progress", title: "Say how it's going", reason: "This reply has been answered." },
      { rel: "https://w3id.org/heart-rise/rels/fail", title: "Say it can't be answered", reason: "This reply has been answered." },
    ],
  };

  // ---------------------------------------------------------------------------------------------------------
  // The mapping, kept by its digest pair.

  const mappingFields = {
    loan: { from: "/_links/self/href", resolve: "absolute" },
    standing: {
      from: "/standing",
      values: { renewable: "renewable", "hold waiting": "held", "renewed the most allowed": "at-limit", returned: "returned" },
    },
  };

  const mappingProposed = {
    id: 12, status: "proposed", proposedBy: "a model, once", proposed: T(21, 4, 14),
    reply: SRI.replyForms,
    source: SOURCE,
    fields: mappingFields,
    check: {
      on: "/loans/3310, as of \"v4\"",
      output: { loan: "https://library.example/loans/3310", standing: "held" },
      against: "the deliver form's schema",
      passes: true,
    },
    waiting: 1,
    _links: {
      self: { href: "/mappings/12" },
      curies: [HR],
      "hr:reply-forms": { href: "https://assistant.example/forms/9c41e2", integrity: SRI.replyForms },
      item: [{ href: "/watches/88", title: "Watch 88, The Hobbit" }],
    },
    _forms: {
      "hr:approve": { title: "Approve", description: "Every envelope with this pair uses the mapping from now on.", method: "POST", _links: { target: { href: "/mappings/12/actions/approve" } } },
      "hr:reject": {
        title: "Reject",
        description: "A model proposes again, told why.",
        method: "POST",
        contentType: "application/json",
        _links: { target: { href: "/mappings/12/actions/reject" } },
        schema: { type: "object", required: ["why"], properties: { why: { type: "string", title: "Why", maxLength: 280 } } },
      },
    },
  };

  const mappingApproved = (() => {
    const m = { ...mappingProposed, status: "approved", approvedBy: "Rosa Lind, librarian", approved: T(21, 9, 40), waiting: 0 };
    m._forms = {
      "hr:retire": { title: "Retire", description: "Stops using it for new envelopes; open watches keep it.", method: "POST", _links: { target: { href: "/mappings/12/actions/retire" } } },
    };
    return m;
  })();

  // ---------------------------------------------------------------------------------------------------------
  // Subscriptions, inboxes and tasks.

  const clubInbox = {
    title: "The Hobbit, for the Thursday book club", status: "taking deliveries",
    _links: {
      self: { href: "/inbox/hobbit" },
      curies: [HR],
      "hr:forms": { href: "/forms/2b7d01", integrity: SRI.clubForms },
      [`${LDP}inbox`]: { href: "/inbox/hobbit" },
    },
  };
  const clubInboxPaused = {
    title: "The Hobbit, for the Thursday book club", status: "paused", paused: T(16, 10, 0, 8),
    _links: {
      self: { href: "/inbox/hobbit" },
      curies: [HR],
      "hr:forms": { href: "/forms/88e0f4", integrity: SRI.clubForms },
      "hr:unavailable": { href: "/unavailable/2c90d1", integrity: SRI.unavailable },
      [`${LDP}inbox`]: { href: "/inbox/hobbit" },
    },
  };

  const sub = (id, status, extra, forms, more = {}) => ({
    id, status,
    kinds: ["https://library.example/profiles/library#copy-on-shelf"],
    ...extra,
    _links: {
      self: { href: `/subscriptions/${id}` },
      curies: [HR],
      "hr:about": { href: "/titles/hobbit", title: "The Hobbit" },
      ...(more.links || {}),
    },
    ...(forms ? { _forms: forms } : {}),
  });
  const act = (id, rel, title, extra = {}) => ({
    [`hr:${rel}`]: { title, method: "POST", ...extra, _links: { target: { href: `/subscriptions/${id}/actions/${rel}` } } },
  });
  const leaseForm = { contentType: "application/json", schema: { type: "object", properties: { leaseDays: { type: "integer", title: "For how many days", minimum: 1, maximum: 90, default: 30 } } } };
  const unavail = (path) => ({ "hr:unavailable": { href: path, integrity: SRI.unavailable } });
  const clubReply = { "hr:reply-to": { href: "https://club.example/inbox/hobbit" } };

  const sub501Pending = sub(501, "pending", { since: T(14, 20, 0, 8), leaseEnds: T(14, 20, 0, 8, 12), checking: "Waiting to check that the inbox answers." },
    act(501, "cancel", "Cancel"), { links: { ...clubReply, ...unavail("/unavailable/a1c4e0") } });
  const sub501Active = sub(501, "active", { since: T(14, 20, 0, 8), leaseEnds: T(14, 20, 0, 8, 12), delivered: 0 },
    { ...act(501, "pause", "Pause"), ...act(501, "renew", "Renew", leaseForm), ...act(501, "cancel", "Cancel") }, { links: clubReply });
  const sub501Expired = sub(501, "expired", { since: T(14, 20, 0, 8), leaseEnded: T(14, 20, 0, 8, 12), delivered: 6 },
    act(501, "renew", "Renew", leaseForm), { links: { ...clubReply, ...unavail("/unavailable/e7f210") } });
  const sub501Renewed = sub(501, "active", { since: T(14, 20, 0, 8), leaseEnds: "2027-01-07T09:05:00Z", delivered: 6 },
    { ...act(501, "pause", "Pause"), ...act(501, "renew", "Renew", leaseForm), ...act(501, "cancel", "Cancel") }, { links: clubReply });

  const sub502 = sub(502, "active", { since: T(14, 25, 0, 8), leaseEnds: T(14, 25, 0, 8, 12), delivery: "queue" },
    { ...act(502, "renew", "Renew", leaseForm), ...act(502, "cancel", "Cancel") },
    { links: { "hr:consume": { href: "amqp://broker.example/sub-502", title: "Your queue: read access for the club's broker user" } } });
  const sub502Cancelled = sub(502, "cancelled", { since: T(14, 25, 0, 8), cancelled: T(9, 0, 0, 20), delivery: "queue" }, null,
    { links: unavail("/unavailable/c0f3e1") });

  const task = (status, extra, forms, links = {}) => ({
    id: 301, status, about: "A copy of The Hobbit, from Harbor Point to Riverside", asked: T(21, 40, 2),
    ...extra,
    _links: { self: { href: "/tasks/301" }, curies: [LIB, HR], ...links },
    ...(forms ? { _forms: forms } : {}),
  });
  const taskCancel = { "hr:cancel": { title: "Call it off", method: "POST", _links: { target: { href: "/tasks/301/actions/cancel" } } } };

  // ---------------------------------------------------------------------------------------------------------
  // The parties.

  const LANES = {
    artie: { name: "Ana's assistant", host: "ARTIE, a HEART client" },
    inbox: { name: "Assistant's inbox", host: "assistant.example" },
    library: { name: "Library", host: "library.example" },
    reasoner: { name: "Model", host: "the library's, called once" },
    librarian: { name: "Librarian", host: "a person, in a browser" },
    curl: { name: "A script", host: "curl, reads nothing" },
    broker: { name: "Broker", host: "RabbitMQ 3.13" },
    club: { name: "Book club", host: "club.example" },
    hub: { name: "Hub", host: "hub.library.example" },
    reader: { name: "Feed reader", host: "reader.example, WebSub only" },
    ldn: { name: "Old notifier", host: "notify.library.example, LDN only" },
    social: { name: "Ana's fediverse server", host: "social.example" },
  };

  // ---------------------------------------------------------------------------------------------------------
  // The flows.

  const FLOWS = [];
  const flow = (f) => FLOWS.push(f);

  flow({
    id: "webhook", group: "Request and reply", short: "Push: the dynamic webhook",
    title: "Push: the dynamic webhook, end to end",
    lede: "Ana's assistant asks the library to say when The Hobbit can be renewed, handing over where to answer; the library answers through the assistant's own form, and the assistant renews.",
    points: [
      "The address arrives with the request, in the act's _rise envelope. Nothing about Ana's assistant is configured at the library.",
      "The client sends hypermedia: a link to a reply resource, with its form set and that set's integrity.",
      "The library fetches the form set once per digest; the second read of the reply is a cache hit.",
      "The library reads the reply before it answers it, and answers with If-Match. The reply's shape is the assistant's own; a kept mapping fills it.",
      "A tell-me-when act always answers 201 with a watch. Prefer: respond-async belongs to ordinary acts that run long (see the 202 flow).",
    ],
    lanes: ["artie", "inbox", "library"],
    steps: [
      { from: "artie", to: "library", line: "GET /loans/3310", h: { ...HAL, Authorization: "Bearer <token>" } },
      { from: "library", to: "artie", line: "200 OK", h: { "Content-Type": "application/hal+json", ...etag("v4"), "Cache-Control": "no-cache" }, body: loanHeld, key: "loan4",
        note: "Hold waiting: renew is absent, tell-me-when-renewable is offered, and its schema takes a _rise envelope." },
      { from: "artie", to: "inbox", line: "GET /inbox", h: HAL },
      { from: "inbox", to: "artie", line: "200 OK", h: etag("i2"), body: inbox },
      { from: "artie", to: "inbox", line: "POST /inbox/actions/open-reply", h: { ...ifMatch("i2"), ...JSONC },
        body: { about: "Renewing The Hobbit at Riverside", shape: "loan-standing", expires: "2026-12-07T00:00:00Z" } },
      { from: "inbox", to: "artie", line: "201 Created", h: { Location: "/replies/7f3a", ...etag("r1") }, body: replyAwaiting, key: "r1",
        note: "The reply's address is unguessable. Its form set is linked by digest, and the same set serves every loan-standing reply." },
      { from: "artie", to: "library", line: "POST /loans/3310/actions/tell-me-when-renewable", h: { ...ifMatch("v4"), ...JSONC },
        body: envelope("https://assistant.example/replies/7f3a") },
      { at: "library", note: "Before answering: https is a scheme it delivers to. The form set's digest isn't in its cache." },
      { from: "library", to: "inbox", line: "GET /forms/9c41e2", h: HAL },
      { from: "inbox", to: "library", line: "200 OK", h: { "Cache-Control": "public, max-age=31536000, immutable" }, body: forms9c, key: "forms9c",
        note: "The bytes hash to the envelope's integrity. Both required fields, loan and standing, have a source in a loan. The pair has mapping 12, approved." },
      { from: "library", to: "artie", line: "201 Created", h: { Location: "/watches/88", ...etag("w1") }, body: watch88 },
      { section: "21:31: the hold clears" },
      { at: "library", note: "The hold is filled by another copy. Loan 3310 becomes renewable, and watch 88 wakes up." },
      { from: "library", to: "inbox", line: "GET /replies/7f3a", h: HAL, note: "Read before you send: still awaiting? Still this form set?" },
      { from: "inbox", to: "library", line: "200 OK", h: etag("r1"), body: replyAwaiting,
        note: "Awaiting, and its hr:forms digest is the envelope's: a cache hit, no fetch." },
      { at: "library", note: "Mapping 12 runs on the loan: loan from /_links/self/href, standing from /standing through its value table. The output passes the deliver form's schema." },
      { from: "library", to: "inbox", line: "POST /replies/7f3a/actions/deliver", h: { ...ifMatch("r1"), ...JSONC },
        body: { loan: "https://library.example/loans/3310", standing: "renewable" } },
      { from: "inbox", to: "library", line: "200 OK", h: etag("r2"), body: replyAnswered, diff: "r1", key: "r2" },
      { at: "library", note: "Watch 88 reads delivered." },
      { section: "Ana's assistant hears, and renews" },
      { at: "artie", note: "Its own inbox tells it (an event stream on the reply, not shown). Any client can also just read." },
      { from: "artie", to: "inbox", line: "GET /replies/7f3a", h: { ...HAL, ...ifNone("r1") } },
      { from: "inbox", to: "artie", line: "200 OK", h: etag("r2"), body: replyAnswered },
      { from: "artie", to: "library", line: "GET /loans/3310", h: { ...HAL, ...ifNone("v4") } },
      { from: "library", to: "artie", line: "200 OK", h: etag("v5"), body: loanRenewable, diff: "loan4", key: "loan5" },
      { from: "artie", to: "library", line: "POST /loans/3310/actions/renew", h: ifMatch("v5") },
      { from: "library", to: "artie", line: "200 OK", h: etag("v6"), body: loanRenewed, diff: "loan5" },
    ],
  });

  flow({
    id: "again", group: "Request and reply", short: "The same answer twice",
    title: "The same answer twice: at least once, answered once",
    lede: "The library crashes after delivering and delivers again; a script posts without reading. The reply's validator lets exactly one answer in.",
    points: [
      "At least once from the sender becomes exactly once at the reply, by its ETag.",
      "412 means read again. The read shows deliver gone, with a reason, so the library stops without ever meeting a 409.",
      "A sender that never reads gets 428 without If-Match, and 409 with a current one. Never a second answer.",
    ],
    lanes: ["library", "inbox", "curl"],
    steps: [
      { section: "The library retries after a crash" },
      { at: "library", note: "It crashed after delivering, before recording watch 88 as delivered. On restart it delivers again, with the ETag it read." },
      { from: "library", to: "inbox", line: "POST /replies/7f3a/actions/deliver", h: { ...ifMatch("r1"), ...JSONC },
        body: { loan: "https://library.example/loans/3310", standing: "renewable" } },
      { from: "inbox", to: "library", line: "412 Precondition Failed", h: PROBLEM, body: problem("stale", "The reply changed since it was read", 412) },
      { from: "library", to: "inbox", line: "GET /replies/7f3a", h: HAL },
      { from: "inbox", to: "library", line: "200 OK", h: etag("r2"), body: replyAnswered },
      { from: "library", to: "inbox", line: "GET /unavailable/0d5f7a1e", h: { Accept: "application/json" } },
      { from: "inbox", to: "library", line: "200 OK", h: { "Cache-Control": "public, max-age=31536000, immutable" }, body: unavailableAnswered },
      { at: "library", note: "Answered, by itself at 21:31. Watch 88 is recorded delivered; nothing is resent." },
      { section: "A script that doesn't read" },
      { from: "curl", to: "inbox", line: "POST /replies/7f3a/actions/deliver", h: JSONC, body: { loan: "https://library.example/loans/3310", standing: "held" } },
      { from: "inbox", to: "curl", line: "428 Precondition Required", h: PROBLEM,
        body: problem("precondition-required", "Read the reply first, and send its ETag in If-Match", 428) },
      { from: "curl", to: "inbox", line: "POST /replies/7f3a/actions/deliver", h: { ...ifMatch("r2"), ...JSONC }, body: { loan: "https://library.example/loans/3310", standing: "held" } },
      { from: "inbox", to: "curl", line: "409 Conflict", h: PROBLEM,
        body: problem("not-offered", "Not offered in this state", 409, { rel: "https://w3id.org/heart-rise/rels/deliver", detail: "This reply was answered at 21:31 by library.example." }) },
    ],
  });

  flow({
    id: "mapping", group: "Request and reply", short: "First run: the mapping",
    title: "First run: a mapping proposed once, approved, kept",
    lede: "The library meets Ana's reply form for the first time. A model proposes how to fill it from a loan, a librarian approves, and every later envelope with the same pair of digests uses it with no model.",
    points: [
      "The key is a pair: the reply's form set digest, and the library's own act with its model's version.",
      "The mapping is data (pointers, a value table), checked against the reply's schema before anyone sees it, and run with no model.",
      "One approved mapping serves every member's assistant using that form set. Whether a member may receive a field is a separate decision.",
      "A changed form set is a new pair and one proposal. Open replies keep the set they were made with.",
    ],
    lanes: ["artie", "inbox", "library", "reasoner", "librarian"],
    names: { artie: { name: "Members' assistants", host: "ARTIE, one per member" } },
    steps: [
      { from: "artie", to: "library", line: "POST /loans/3310/actions/tell-me-when-renewable", h: { ...ifMatch("v4"), ...JSONC }, body: envelope("https://assistant.example/replies/7f3a") },
      { from: "library", to: "inbox", line: "GET /forms/9c41e2", h: HAL },
      { from: "inbox", to: "library", line: "200 OK", h: { "Cache-Control": "public, max-age=31536000, immutable" }, body: forms9c, key: "forms9c" },
      { at: "library", note: "No mapping for the pair (reply forms sha256-nEHi…, source tell-me-when-renewable@sha256-d6qr…). Both required fields could come from a loan, so this is no reason to refuse." },
      { from: "library", to: "artie", line: "201 Created", h: { Location: "/watches/88", ...etag("w0") }, body: watch88Mapping },
      { from: "library", to: "reasoner", kind: "call", line: "Propose a mapping",
        body: {
          task: "Fill the target form's fields from the source representation. Use only JSON Pointers into the source, value tables and constants. No code.",
          target: { form: "hr:deliver", schema: deliverSchema },
          source: { act: SOURCE, sample: { id: 3310, standing: "hold waiting", _links: { self: { href: "/loans/3310" } } }, standings: ["renewable", "hold waiting", "renewed the most allowed", "returned"] },
        } },
      { from: "reasoner", to: "library", kind: "call", line: "Proposal", body: { fields: mappingFields } },
      { at: "library", note: "Checked mechanically: run on loan 3310 now, the output passes the deliver schema. Mapping 12 is proposed." },
      { from: "librarian", to: "library", line: "GET /mappings/12", h: { Accept: "text/html" }, note: "The librarian sees it as a page with Approve and Reject; the same resource, here in HAL." },
      { from: "library", to: "librarian", line: "200 OK", h: etag("m1"), body: mappingProposed, key: "m1" },
      { from: "librarian", to: "library", line: "POST /mappings/12/actions/approve", h: ifMatch("m1") },
      { from: "library", to: "librarian", line: "200 OK", h: etag("m2"), body: mappingApproved, diff: "m1" },
      { at: "library", note: "Watch 88 now waits for the hold, as in the webhook flow." },
      { section: "Grace Okafor's assistant, the same software, a week later" },
      { from: "artie", to: "library", line: "POST /loans/3315/actions/tell-me-when-renewable", h: { ...ifMatch("v2"), ...JSONC },
        body: envelope("https://assistant.example/replies/b210") },
      { at: "library", note: "The digest is in its cache, and the pair has mapping 12, approved. No fetch, no model, no person." },
      { from: "library", to: "artie", line: "201 Created", h: { Location: "/watches/91", ...etag("w1") },
        body: { ...watch88, id: 91, opened: T(10, 2, 0, 14), _links: { ...watch88._links, self: { href: "/watches/91" }, "hr:about": { href: "/loans/3315", title: "Wonder" }, "hr:reply-to": { href: "https://assistant.example/replies/b210" } }, _forms: { "hr:cancel": { title: "Stop watching", method: "POST", _links: { target: { href: "/watches/91/actions/cancel" } } } } } },
      { section: "The assistant ships a new reply form" },
      { at: "inbox", note: "The assistant redeploys: its loan-standing shape gains dueBy. New replies link form set /forms/51d0aa, a new digest. Reply b210 keeps 9c41e2." },
      { from: "artie", to: "library", line: "POST /loans/3316/actions/tell-me-when-renewable", h: { ...ifMatch("v1"), ...JSONC },
        body: envelope("https://assistant.example/replies/c9e4", "https://assistant.example/forms/51d0aa", SRI.replyForms2) },
      { from: "library", to: "inbox", line: "GET /forms/51d0aa", h: HAL },
      { from: "inbox", to: "library", line: "200 OK", h: { "Cache-Control": "public, max-age=31536000, immutable" }, body: forms51, diff: "forms9c" },
      { at: "library", note: "A new pair: mapping 13 is proposed, one model call. Mapping 12 stays approved for the old set." },
      { from: "library", to: "artie", line: "201 Created", h: { Location: "/watches/97", ...etag("w0") },
        body: { ...watch88Mapping, id: 97, waitingOn: "Mapping 13 is proposed and not yet approved.", _links: { self: { href: "/watches/97" }, curies: [HR], "hr:about": { href: "/loans/3316", title: "Coraline" }, "hr:reply-to": { href: "https://assistant.example/replies/c9e4" }, "hr:mapping": { href: "/mappings/13" } }, _forms: { "hr:cancel": { title: "Stop watching", method: "POST", _links: { target: { href: "/watches/97/actions/cancel" } } } } } },
    ],
  });

  flow({
    id: "fail", group: "Request and reply", short: "When it fails",
    title: "When it fails: at once only when certain",
    lede: "Asynchronous work can take weeks, and whether a reply can be filled is often unknowable until it's done. The library refuses at once only when it is sure, and otherwise fails later with a reason.",
    points: [
      "At once: the act isn't offered (409), the address's scheme isn't one it delivers to (422), the form set's bytes don't match their integrity (422).",
      "Later: the reply expired first (the watch lapses), or the mapping can't fill the reply (the reply's fail form, with a reason).",
      "Delivery itself is guarded by 412 and 409, as in the previous flow.",
    ],
    lanes: ["artie", "inbox", "library"],
    steps: [
      { section: "At once: the act isn't offered" },
      { at: "artie", note: "Charlotte's Web has been renewed the most allowed, so its loan doesn't offer tell-me-when-renewable, and its hr:unavailable says why. A client that read it never gets here." },
      { from: "artie", to: "library", line: "POST /loans/3312/actions/tell-me-when-renewable", h: { ...ifMatch("v9"), ...JSONC }, body: envelope("https://assistant.example/replies/7f3a") },
      { from: "library", to: "artie", line: "409 Conflict", h: PROBLEM,
        body: problem("not-offered", "Not offered in this state", 409, { rel: "/profiles/library#tell-me-when-renewable", detail: "Charlotte's Web has been renewed the most allowed; it will never be renewable." }) },
      { section: "At once: a scheme it can't deliver to" },
      { from: "artie", to: "library", line: "POST /loans/3310/actions/tell-me-when-renewable", h: { ...ifMatch("v4"), ...JSONC }, body: envelope("ftp://assistant.example/replies/7f3a") },
      { from: "library", to: "artie", line: "422 Unprocessable Content", h: PROBLEM,
        body: problem("invalid-input", "The body does not match the form's schema", 422, { violations: [{ pointer: "/_rise/_links/hr:reply-to/href", message: "must match ^(https|amqp)://" }] }) },
      { section: "At once: the bytes don't match" },
      { from: "artie", to: "library", line: "POST /loans/3310/actions/tell-me-when-renewable", h: { ...ifMatch("v4"), ...JSONC },
        body: envelope("https://assistant.example/replies/7f3a", "https://assistant.example/forms/9c41e2", "sha256-AAAAAAAAAAAA…AAAAAAAAAA=") },
      { from: "library", to: "inbox", line: "GET /forms/9c41e2", h: HAL },
      { from: "inbox", to: "library", line: "200 OK", body: forms9c, note: "These bytes hash to sha256-nEHi…, not to the envelope's value. The library doesn't parse, cache or map them." },
      { from: "library", to: "artie", line: "422 Unprocessable Content", h: PROBLEM,
        body: problem("rule-refused", "The library can't use this envelope", 422, { violations: [{ pointer: "/_rise/_links/hr:forms", message: "the form set at this address doesn't match its integrity" }] }) },
      { section: "Later: the reply expired first" },
      { at: "library", note: "The hold takes five weeks to clear. Ana's reply expired on 7 December." },
      { from: "library", to: "inbox", line: "GET /replies/7f3a", h: HAL },
      { from: "inbox", to: "library", line: "200 OK", h: etag("r3"), body: replyExpired },
      { at: "library", note: "Nothing to deliver to. Watch 88 reads lapsed: \"The reply expired on 7 December, before the loan could be renewed.\"" },
      { section: "Later: the mapping can't fill the reply" },
      { at: "library", note: "The library has added a standing, lost. Mapping 12's value table has no entry for it, so its output fails the deliver schema." },
      { from: "library", to: "inbox", line: "GET /replies/7f3a", h: HAL },
      { from: "inbox", to: "library", line: "200 OK", h: etag("r1"), body: replyAwaiting, key: "r1" },
      { from: "library", to: "inbox", line: "POST /replies/7f3a/actions/fail", h: { ...ifMatch("r1"), ...JSONC },
        body: { reason: "The library can't give this loan's standing in your reply's words: it reads lost, and the mapping has no value for it." } },
      { from: "inbox", to: "library", line: "200 OK", h: etag("r4"), body: replyFailed, diff: "r1" },
      { at: "library", note: "Watch 88 reads failed, and mapping 12 is marked for repair: one new proposal, for the pair." },
    ],
  });

  flow({
    id: "poll", group: "Request and reply", short: "Poll: read the watch",
    title: "Poll: no envelope, read the watch",
    lede: "The same act with no _rise: the library still brings a watch into being, and the assistant reads it, holding each read open until something changes.",
    points: [
      "No address is handed over, so nothing is pushed: the watch is the truth, and its own status resource.",
      "Each read sends If-None-Match. Prefer: wait=60 holds it open (RFC 7240), as the gateway's hub does today.",
      "Once done, the watch links its result with hr:result.",
    ],
    lanes: ["artie", "library"],
    steps: [
      { from: "artie", to: "library", line: "POST /loans/3310/actions/tell-me-when-renewable", h: { ...ifMatch("v4"), ...JSONC }, body: {} },
      { from: "library", to: "artie", line: "201 Created", h: { Location: "/watches/89", ...etag("w1") },
        body: { ...watch88, id: 89, opened: T(21, 4, 12), _links: { self: { href: "/watches/89" }, curies: [HR], "hr:about": { href: "/loans/3310", title: "The Hobbit" } }, _forms: { "hr:cancel": { title: "Stop watching", method: "POST", _links: { target: { href: "/watches/89/actions/cancel" } } } } },
        key: "w89" },
      { from: "artie", to: "library", line: "GET /watches/89", h: { ...HAL, ...ifNone("w1"), Prefer: "wait=60" } },
      { from: "library", to: "artie", line: "304 Not Modified", h: { ...etag("w1"), "Preference-Applied": "wait=60" }, note: "After 60 seconds: nothing changed. The assistant asks again." },
      { at: "library", note: "21:31: the hold clears." },
      { from: "artie", to: "library", line: "GET /watches/89", h: { ...HAL, ...ifNone("w1"), Prefer: "wait=60" } },
      { from: "library", to: "artie", line: "200 OK", h: { ...etag("w2"), "Preference-Applied": "wait=60" }, note: "Answered early, 14 seconds into the wait.",
        body: { id: 89, status: "done", until: "the loan can be renewed", opened: T(21, 4, 12), done: T(21, 31, 2), _links: { self: { href: "/watches/89" }, curies: [HR], "hr:about": { href: "/loans/3310", title: "The Hobbit" }, "hr:result": { href: "/loans/3310", title: "The Hobbit, renewable" } } },
        diff: "w89" },
      { from: "artie", to: "library", line: "GET /loans/3310", h: { ...HAL, ...ifNone("v4") } },
      { from: "library", to: "artie", line: "200 OK", h: etag("v5"), body: loanRenewable },
    ],
  });

  flow({
    id: "async", group: "Request and reply", short: "Slow act: 202",
    title: "A slow act: RFC 7240's respond-async, and 202",
    lede: "An ordinary act that takes a day: the assistant prefers not to wait, the library answers 202 with a task, and the task offers cancel only while it still can.",
    points: [
      "Prefer: respond-async is RFC 7240 (IETF, 2014). The server may ignore it, so a client handles 200 and 202 alike.",
      "The status resource is a RISE task, with forms by state and reasons for the rest.",
      "Add a _rise envelope and the task also delivers to the reply when done, as in the webhook flow.",
      "heart-core answers no 202 today; this needs it (decision 3).",
    ],
    lanes: ["artie", "library"],
    steps: [
      { section: "A quick act ignores the preference" },
      { from: "artie", to: "library", line: "POST /loans/3311/actions/renew", h: { ...ifMatch("v3"), Prefer: "respond-async" } },
      { from: "library", to: "artie", line: "200 OK", h: etag("v4"), note: "Done at once, so no Preference-Applied.",
        body: { id: 3311, title: "Matilda", author: "Roald Dahl", due: "2026-12-03", renewals: 1, standing: "renewable", _links: { self: { href: "/loans/3311" }, curies: [LIB, HR] }, _forms: { "library:renew": { method: "POST", _links: { target: { href: "/loans/3311/actions/renew" } } }, "library:return": { method: "POST", _links: { target: { href: "/loans/3311/actions/return" } } } } } },
      { section: "A slow act: bring a copy from another branch" },
      { from: "artie", to: "library", line: "GET /titles/hobbit", h: HAL },
      { from: "library", to: "artie", line: "200 OK", h: etag("t9"), body: titleHobbit },
      { from: "artie", to: "library", line: "POST /titles/hobbit/actions/bring-a-copy", h: { ...ifMatch("t9"), Prefer: "respond-async", ...JSONC },
        body: { from: "harbor-point", pickUpAt: "riverside" } },
      { from: "library", to: "artie", line: "202 Accepted", h: { Location: "/tasks/301", "Preference-Applied": "respond-async", ...etag("k1") },
        body: task("working", { step: "Finding a copy on the shelf at Harbor Point" }, taskCancel), key: "k1" },
      { from: "artie", to: "library", line: "GET /tasks/301", h: { ...HAL, ...ifNone("k1"), Prefer: "wait=60" } },
      { from: "library", to: "artie", line: "200 OK", h: { ...etag("k2"), "Preference-Applied": "wait=60" },
        body: task("on the van", { step: "On the 8 a.m. van to Riverside" }, null, { "hr:unavailable": { href: "/unavailable/9f13aa", integrity: SRI.unavailable } }), diff: "k1", key: "k2",
        note: "Cancel is gone; hr:unavailable says \"The copy is on the van and can't be called back.\"" },
      { from: "artie", to: "library", line: "GET /tasks/301", h: { ...HAL, ...ifNone("k2"), Prefer: "wait=60" } },
      { from: "library", to: "artie", line: "304 Not Modified", h: { ...etag("k2"), "Preference-Applied": "wait=60" } },
      { at: "library", note: "The next morning, the copy reaches Riverside." },
      { from: "artie", to: "library", line: "GET /tasks/301", h: { ...HAL, ...ifNone("k2") } },
      { from: "library", to: "artie", line: "200 OK", h: etag("k3"),
        body: task("done", { done: T(10, 12, 0, 8) }, null, { "hr:result": { href: "/holds/7781", title: "Your hold, ready to pick up" } }), diff: "k2" },
      { from: "artie", to: "library", line: "GET /holds/7781", h: HAL },
      { from: "library", to: "artie", line: "200 OK", h: etag("h1"),
        body: { id: 7781, title: "The Hobbit", status: "ready", pickUpAt: "riverside", until: "2026-11-11", _links: { self: { href: "/holds/7781" }, curies: [LIB, HR] }, _forms: { "library:cancel-hold": { title: "Cancel the hold", method: "POST", _links: { target: { href: "/holds/7781/actions/cancel-hold" } } } } } },
    ],
  });

  const amqpMsg = (method, extra) => ({ method, ...extra });
  flow({
    id: "amqp", group: "Request and reply", short: "Over AMQP",
    title: "The same reply over AMQP",
    lede: "The envelope's reply link has an amqp scheme. Each HEART request travels as one message and its answer comes back correlated, so read before you send, If-Match and 412 work unchanged.",
    points: [
      "The form set stays on https, cached by digest. Only the reply's address is amqp.",
      "Method, target and If-Match go in message headers; the answer goes to reply_to with the request's message_id as correlation_id.",
      "The inbox's front door consumes the queue and hands each message to the same HEART handler https reaches.",
      "RabbitMQ 3.13 speaks AMQP 0-9-1, which has no to or subject; AMQP 1.0 comes later, on RabbitMQ 4.",
    ],
    settle: "How an amqp URI names the queue and the resource. Here the first segment is the queue (assistant.heart) and the rest the resource; RabbitMQ's own URIs use the path for the virtual host.",
    lanes: ["artie", "library", "broker", "inbox"],
    steps: [
      { from: "artie", to: "library", line: "POST /loans/3310/actions/tell-me-when-renewable", h: { ...ifMatch("v4"), ...JSONC },
        body: envelope("amqp://broker.example/assistant.heart/replies/7f3a") },
      { at: "library", note: "It delivers to https and amqp. The form set at https is in its cache." },
      { from: "library", to: "artie", line: "201 Created", h: { Location: "/watches/88", ...etag("w1") },
        body: { ...watch88, _links: { ...watch88._links, "hr:reply-to": { href: "amqp://broker.example/assistant.heart/replies/7f3a" } } } },
      { section: "21:31: the hold clears. Read before you send, as a message" },
      { from: "library", to: "broker", kind: "amqp", line: "basic.publish  GET /replies/7f3a",
        body: amqpMsg("basic.publish", { exchange: "", routing_key: "assistant.heart", properties: { message_id: "lib-5521", reply_to: "amq.rabbitmq.reply-to", expiration: "30000", headers: { "heart-method": "GET", "heart-target": "/replies/7f3a", accept: "application/hal+json" } }, body: "" }) },
      { from: "broker", to: "inbox", kind: "amqp", line: "basic.deliver",
        body: amqpMsg("basic.deliver", { consumer_tag: "inbox-front-door", delivery_tag: 41, redelivered: false, routing_key: "assistant.heart", properties: "as published" }) },
      { at: "inbox", note: "The front door turns the message into GET /replies/7f3a for the inbox's HEART handler, and the answer into a message." },
      { from: "inbox", to: "broker", kind: "amqp", line: "basic.publish  200 OK",
        body: amqpMsg("basic.publish", { exchange: "", routing_key: "amq.rabbitmq.reply-to.g1hkAA…", properties: { correlation_id: "lib-5521", content_type: "application/hal+json", headers: { "heart-status": 200, etag: "\"r1\"" } }, body: replyAwaiting }) },
      { from: "broker", to: "library", kind: "amqp", line: "basic.deliver", body: amqpMsg("basic.deliver", { routing_key: "amq.rabbitmq.reply-to.g1hkAA…", properties: "as published" }) },
      { section: "Deliver, as a message" },
      { from: "library", to: "broker", kind: "amqp", line: "basic.publish  POST …/deliver",
        body: amqpMsg("basic.publish", { exchange: "", routing_key: "assistant.heart", properties: { message_id: "lib-5522", reply_to: "amq.rabbitmq.reply-to", content_type: "application/json", headers: { "heart-method": "POST", "heart-target": "/replies/7f3a/actions/deliver", "if-match": "\"r1\"" } }, body: { loan: "https://library.example/loans/3310", standing: "renewable" } }) },
      { from: "broker", to: "inbox", kind: "amqp", line: "basic.deliver", body: amqpMsg("basic.deliver", { consumer_tag: "inbox-front-door", delivery_tag: 42, redelivered: false, properties: "as published" }) },
      { from: "inbox", to: "broker", kind: "amqp", line: "basic.publish  200 OK",
        body: amqpMsg("basic.publish", { exchange: "", routing_key: "amq.rabbitmq.reply-to.g1hkAA…", properties: { correlation_id: "lib-5522", content_type: "application/hal+json", headers: { "heart-status": 200, etag: "\"r2\"" } }, body: replyAnswered }) },
      { from: "broker", to: "library", kind: "amqp", line: "basic.deliver", body: amqpMsg("basic.deliver", { properties: "as published" }) },
      { section: "Redelivered: the broker hands it over twice" },
      { at: "broker", note: "The front door's ack for delivery 42 was lost, so RabbitMQ redelivers it." },
      { from: "broker", to: "inbox", kind: "amqp", line: "basic.deliver  redelivered", body: amqpMsg("basic.deliver", { consumer_tag: "inbox-front-door", delivery_tag: 43, redelivered: true, properties: { message_id: "lib-5522", headers: { "heart-method": "POST", "heart-target": "/replies/7f3a/actions/deliver", "if-match": "\"r1\"" } } }) },
      { from: "inbox", to: "broker", kind: "amqp", line: "basic.publish  412",
        body: amqpMsg("basic.publish", { exchange: "", routing_key: "amq.rabbitmq.reply-to.g1hkAA…", properties: { correlation_id: "lib-5522", content_type: "application/problem+json", headers: { "heart-status": 412 } }, body: problem("stale", "The reply changed since it was read", 412) }) },
      { at: "library", note: "Its transport already has the answer for lib-5522 and drops this one. Even if it hadn't, a 412 sends it to read, not to resend." },
    ],
  });

  flow({
    id: "subscribe", group: "One publisher, many subscribers", short: "A. Subscriptions as resources",
    title: "A. Subscriptions as resources: self-registered, leased, readable",
    lede: "The Thursday book club asks the library to tell it when a copy of The Hobbit is back on the shelf. It registers itself by an act; the library checks its inbox, sends thin notices, and meets backpressure when the club pauses.",
    points: [
      "Subscribing is an act the title offers, not a create on a collection; the subscription is a resource with states and forms.",
      "The library checks the inbox answers before the first delivery: read before you send, as WebSub's check of intent.",
      "Notices are thin hints (CloudEvents fields with the version); the club reads the title for the truth.",
      "The inbox's ETag covers only whether it takes deliveries, so parallel deliveries don't 412 each other; pausing it is backpressure.",
      "The library's configuration names no subscriber: only policy (schemes, how many per caller, rates).",
    ],
    lanes: ["club", "library"],
    steps: [
      { from: "club", to: "library", line: "GET /titles/hobbit", h: HAL },
      { from: "library", to: "club", line: "200 OK", h: etag("t9"), body: titleHobbit, key: "t9" },
      { from: "club", to: "library", line: "POST /titles/hobbit/actions/subscribe", h: { ...ifMatch("t9"), ...JSONC },
        body: { kinds: ["https://library.example/profiles/library#copy-on-shelf"], leaseDays: 30, delivery: "inbox", _rise: { _links: { "hr:reply-to": { href: "https://club.example/inbox/hobbit" }, "hr:forms": { href: "https://club.example/forms/2b7d01", integrity: SRI.clubForms } } } } },
      { from: "library", to: "club", line: "201 Created", h: { Location: "/subscriptions/501", ...etag("s1") }, body: sub501Pending, key: "s1" },
      { from: "library", to: "club", line: "GET /inbox/hobbit", h: HAL, note: "The check of intent: does this address answer as an inbox taking deliveries?" },
      { from: "club", to: "library", line: "200 OK", h: etag("c1"), body: clubInbox, key: "c1" },
      { at: "library", note: "It does: subscription 501 becomes active." },
      { from: "club", to: "library", line: "GET /subscriptions/501", h: { ...HAL, ...ifNone("s1") } },
      { from: "library", to: "club", line: "200 OK", h: etag("s2"), body: sub501Active, diff: "s1" },
      { section: "A copy comes back" },
      { at: "library", note: "Nov 8, 15:02: a copy is checked in at Riverside." },
      { from: "library", to: "club", line: "POST /inbox/hobbit/actions/deliver", h: { ...ifMatch("c1"), "Content-Type": "application/cloudevents+json" }, body: hint("evt-90211", T(15, 2, 44, 8), "t10") },
      { from: "club", to: "library", line: "201 Created", h: { Location: "/inbox/hobbit/received/31", ...etag("c1") }, note: "The inbox's ETag is unchanged: it still takes deliveries." },
      { from: "club", to: "library", line: "GET /titles/hobbit", h: { ...HAL, ...ifNone("t9") } },
      { from: "library", to: "club", line: "200 OK", h: etag("t10"), body: titleOnShelf, diff: "t9" },
      { section: "The club pauses its inbox: backpressure" },
      { at: "club", note: "16:10: the club pauses its own inbox. Its ETag becomes c2." },
      { at: "library", note: "16:30: another copy is checked in." },
      { from: "library", to: "club", line: "POST /inbox/hobbit/actions/deliver", h: { ...ifMatch("c1"), "Content-Type": "application/cloudevents+json" }, body: hint("evt-90214", T(16, 30, 9, 8), "t11") },
      { from: "club", to: "library", line: "412 Precondition Failed", h: PROBLEM, body: problem("stale", "The inbox changed since it was read", 412) },
      { from: "library", to: "club", line: "GET /inbox/hobbit", h: HAL },
      { from: "club", to: "library", line: "200 OK", h: etag("c2"), body: clubInboxPaused, diff: "c1" },
      { at: "library", note: "Deliver isn't offered. The library holds the notice; subscription 501 reads \"inbox paused, 1 notice held\". It reads the inbox again later and never resends blind." },
      { section: "The lease ends" },
      { from: "club", to: "library", line: "GET /subscriptions/501", h: HAL },
      { from: "library", to: "club", line: "200 OK", h: etag("s5"), body: sub501Expired, key: "s5", note: "Only renew is offered; hr:unavailable says \"The lease ended on 8 December.\"" },
      { from: "club", to: "library", line: "POST /subscriptions/501/actions/renew", h: { ...ifMatch("s5"), ...JSONC }, body: { leaseDays: 30 } },
      { from: "library", to: "club", line: "200 OK", h: etag("s6"), body: sub501Renewed, diff: "s5" },
    ],
  });

  flow({
    id: "broker", group: "One publisher, many subscribers", short: "B. A broker's topology",
    title: "B. A broker's topology: the club binds its own queue",
    lede: "The title links its exchange. The club declares and binds a queue of its own, and the library never hears of it.",
    points: [
      "The exchange is a link in the title's representation (hr:events), so topic names stop living in wikis.",
      "The library publishes once; the broker fans out. The library knows nothing of the club.",
      "Messages are hints. The title is the truth, read with If-None-Match.",
      "Who may bind is the broker's policy, not the library's configuration.",
    ],
    lanes: ["club", "library", "broker"],
    steps: [
      { from: "club", to: "library", line: "GET /titles/hobbit", h: HAL },
      { from: "library", to: "club", line: "200 OK", h: etag("t9"), body: titleHobbit, key: "t9", note: "hr:events links amqp://broker.example/library.titles, routing titles.hobbit.#" },
      { from: "club", to: "broker", kind: "amqp", line: "queue.declare  club.hobbit", body: amqpMsg("queue.declare", { queue: "club.hobbit", durable: true, arguments: { "x-message-ttl": 86400000 } }) },
      { from: "broker", to: "club", kind: "amqp", line: "queue.declare-ok", body: amqpMsg("queue.declare-ok", { queue: "club.hobbit", message_count: 0, consumer_count: 0 }) },
      { from: "club", to: "broker", kind: "amqp", line: "queue.bind  library.titles", body: amqpMsg("queue.bind", { queue: "club.hobbit", exchange: "library.titles", routing_key: "titles.hobbit.copy-on-shelf" }) },
      { from: "broker", to: "club", kind: "amqp", line: "queue.bind-ok", body: amqpMsg("queue.bind-ok", {}) },
      { from: "club", to: "broker", kind: "amqp", line: "basic.consume  club.hobbit", body: amqpMsg("basic.consume", { queue: "club.hobbit", consumer_tag: "club-1", no_ack: false }) },
      { at: "library", note: "Nov 8, 15:02: a copy is checked in." },
      { from: "library", to: "broker", kind: "amqp", line: "basic.publish  library.titles",
        body: amqpMsg("basic.publish", { exchange: "library.titles", routing_key: "titles.hobbit.copy-on-shelf", properties: { message_id: "evt-90211", content_type: "application/cloudevents+json", timestamp: 1794063764 }, body: hint("evt-90211", T(15, 2, 44, 8), "t10") }) },
      { from: "broker", to: "club", kind: "amqp", line: "basic.deliver", body: amqpMsg("basic.deliver", { consumer_tag: "club-1", delivery_tag: 1, routing_key: "titles.hobbit.copy-on-shelf", properties: "as published" }) },
      { from: "club", to: "library", line: "GET /titles/hobbit", h: { ...HAL, ...ifNone("t9") } },
      { from: "library", to: "club", line: "200 OK", h: etag("t10"), body: titleOnShelf, diff: "t9" },
    ],
  });

  flow({
    id: "queues", group: "One publisher, many subscribers", short: "C. Publisher-made queues",
    title: "C. Publisher-made queues: subscribe by act, consume a queue",
    lede: "The club subscribes by the same act as in A, asking for a queue. The library makes and binds one, grants the club read on it, and hands back its address in the subscription's state.",
    points: [
      "The club needs no rights on the library's exchange, only read on its own queue.",
      "The lease is the queue's own expiry (x-expires), so a vanished club stops costing anything.",
      "Cancelling the subscription deletes the queue.",
    ],
    lanes: ["club", "library", "broker"],
    steps: [
      { from: "club", to: "library", line: "POST /titles/hobbit/actions/subscribe", h: { ...ifMatch("t9"), ...JSONC },
        body: { kinds: ["https://library.example/profiles/library#copy-on-shelf"], leaseDays: 30, delivery: "queue" } },
      { from: "library", to: "broker", kind: "amqp", line: "queue.declare  sub-502", body: amqpMsg("queue.declare", { queue: "sub-502", durable: true, arguments: { "x-expires": 2592000000 } }) },
      { from: "broker", to: "library", kind: "amqp", line: "queue.declare-ok", body: amqpMsg("queue.declare-ok", { queue: "sub-502", message_count: 0, consumer_count: 0 }) },
      { from: "library", to: "broker", kind: "amqp", line: "queue.bind  library.titles", body: amqpMsg("queue.bind", { queue: "sub-502", exchange: "library.titles", routing_key: "titles.hobbit.copy-on-shelf" }) },
      { from: "broker", to: "library", kind: "amqp", line: "queue.bind-ok", body: amqpMsg("queue.bind-ok", {}) },
      { at: "library", note: "It grants the club's broker user read on sub-502 only, through RabbitMQ's permissions." },
      { from: "library", to: "club", line: "201 Created", h: { Location: "/subscriptions/502", ...etag("s1") }, body: sub502, key: "s502" },
      { from: "club", to: "broker", kind: "amqp", line: "basic.consume  sub-502", body: amqpMsg("basic.consume", { queue: "sub-502", consumer_tag: "club-2", no_ack: false }) },
      { at: "library", note: "Nov 8, 15:02: a copy is checked in." },
      { from: "library", to: "broker", kind: "amqp", line: "basic.publish  library.titles",
        body: amqpMsg("basic.publish", { exchange: "library.titles", routing_key: "titles.hobbit.copy-on-shelf", properties: { message_id: "evt-90211", content_type: "application/cloudevents+json" }, body: hint("evt-90211", T(15, 2, 44, 8), "t10") }) },
      { from: "broker", to: "club", kind: "amqp", line: "basic.deliver", body: amqpMsg("basic.deliver", { consumer_tag: "club-2", delivery_tag: 1, routing_key: "titles.hobbit.copy-on-shelf", properties: "as published" }) },
      { section: "Done with it" },
      { from: "club", to: "library", line: "POST /subscriptions/502/actions/cancel", h: ifMatch("s1") },
      { from: "library", to: "broker", kind: "amqp", line: "queue.delete  sub-502", body: amqpMsg("queue.delete", { queue: "sub-502", if_unused: false, if_empty: false }) },
      { from: "library", to: "club", line: "200 OK", h: etag("s2"), body: sub502Cancelled, diff: "s502" },
    ],
  });

  flow({
    id: "feed", group: "One publisher, many subscribers", short: "D. A feed",
    title: "D. A feed: nobody registers",
    lede: "The title's changes as a list, newest first. Anyone reads it; a held read comes back as soon as something new arrives.",
    points: [
      "No registration and no list of subscribers: any number of readers, and caches absorb the reads.",
      "Prefer: wait holds a read until the feed changes, as the gateway's hub does now.",
      "The feed is the truth that every push variation only hints at.",
    ],
    lanes: ["club", "library"],
    steps: [
      { from: "club", to: "library", line: "GET /titles/hobbit/events", h: HAL },
      { from: "library", to: "club", line: "200 OK", h: { ...etag("e40"), "Cache-Control": "no-cache", Link: "<https://hub.library.example/>; rel=\"hub\"" }, body: feedOld, key: "e40" },
      { from: "club", to: "library", line: "GET /titles/hobbit/events", h: { ...HAL, ...ifNone("e40"), Prefer: "wait=300" } },
      { from: "library", to: "club", line: "200 OK", h: { ...etag("e41"), "Preference-Applied": "wait=300" }, body: feedNew, diff: "e40", note: "Answered 41 seconds into the wait, when the copy was checked in." },
      { from: "club", to: "library", line: "GET /titles/hobbit/events", h: { ...HAL, ...ifNone("e41"), Prefer: "wait=300" } },
      { from: "library", to: "club", line: "304 Not Modified", h: { ...etag("e41"), "Preference-Applied": "wait=300" }, note: "After 300 seconds, nothing new." },
    ],
  });

  const form = (s) => s; // a form-encoded body, shown as written
  const hubRep = {
    title: "Riverside's hub",
    _links: { self: { href: "/" }, curies: [HR] },
    _forms: {
      "hr:subscribe": {
        title: "Subscribe to a topic",
        description: "WebSub's subscribe, as a form. A WebSub subscriber posts these fields without reading this; a HEART client finds them here.",
        method: "POST",
        contentType: "application/x-www-form-urlencoded",
        _links: { target: { href: "/" } },
        schema: {
          type: "object", required: ["hub.mode", "hub.topic", "hub.callback"],
          properties: {
            "hub.mode": { const: "subscribe" },
            "hub.topic": { type: "string", format: "uri", title: "Topic", "hr:options": { link: { href: "/topics" }, items: "/topics", valueField: "href", promptField: "title" } },
            "hub.callback": { type: "string", format: "uri", title: "Callback" },
            "hub.lease_seconds": { type: "integer", title: "Lease, in seconds", minimum: 3600, maximum: 2592000, default: 864000 },
            "hub.secret": { type: "string", title: "Secret for signatures", maxLength: 199, writeOnly: true },
          },
        },
      },
    },
  };
  const sub77 = (status, extra, forms) => ({
    id: 77, status, topic: "https://library.example/titles/hobbit/events", callback: "https://reader.example/websub/42", ...extra,
    _links: { self: { href: "/subscriptions/77" }, curies: [HR], "hr:about": { href: "https://library.example/titles/hobbit/events" } },
    ...(forms ? { _forms: forms } : {}),
  });
  const sub77Forms = {
    "hr:renew": { title: "Renew", method: "POST", _links: { target: { href: "/subscriptions/77/actions/renew" } } },
    "hr:pause": { title: "Pause", method: "POST", _links: { target: { href: "/subscriptions/77/actions/pause" } } },
    "hr:cancel": { title: "Unsubscribe", method: "POST", _links: { target: { href: "/subscriptions/77/actions/cancel" } } },
  };

  flow({
    id: "websub", group: "One publisher, many subscribers", short: "E. A hub (WebSub)",
    title: "E. A hub that speaks WebSub, extended",
    lede: "A feed reader that knows only WebSub subscribes at the library's hub exactly as it would anywhere. RISE adds a Location on the 202: the subscription as a resource it, or any HEART client, can read and act on.",
    points: [
      "The hub's subscribe form uses WebSub's own field names and encoding, so an unchanged WebSub subscriber works.",
      "Verification of intent is WebSub's challenge echo here; for a HEART subscriber it is read before you send.",
      "Deliveries are signed with the subscriber's secret (X-Hub-Signature). WebSub sends whole content; a HEART subscriber may take thin hints.",
      "What WebSub lacks: reading the subscription, pausing it, and asking why it lapsed.",
    ],
    lanes: ["reader", "hub", "library"],
    steps: [
      { from: "reader", to: "library", line: "GET /titles/hobbit/events", h: { Accept: "application/json" } },
      { from: "library", to: "reader", line: "200 OK", h: { ...etag("e40"), Link: "<https://hub.library.example/>; rel=\"hub\", <https://library.example/titles/hobbit/events>; rel=\"self\"" }, body: feedOld },
      { from: "reader", to: "hub", line: "GET /", h: HAL, note: "Optional: a WebSub subscriber skips this read." },
      { from: "hub", to: "reader", line: "200 OK", h: etag("h4"), body: hubRep },
      { from: "reader", to: "hub", line: "POST /", h: { "Content-Type": "application/x-www-form-urlencoded" },
        body: form("hub.mode=subscribe\n&hub.topic=https%3A%2F%2Flibrary.example%2Ftitles%2Fhobbit%2Fevents\n&hub.callback=https%3A%2F%2Freader.example%2Fwebsub%2F42\n&hub.lease_seconds=864000\n&hub.secret=<secret>") },
      { from: "hub", to: "reader", line: "202 Accepted", h: { Location: "/subscriptions/77", ...etag("u1") }, body: sub77("pending verification", { asked: T(14, 0, 0, 8) }, { "hr:cancel": sub77Forms["hr:cancel"] }),
        note: "WebSub says only 202. The Location is RISE's addition; a WebSub subscriber ignores it." },
      { from: "hub", to: "reader", line: "GET /websub/42?hub.mode=subscribe&hub.topic=…&hub.challenge=kq81Zt3w&hub.lease_seconds=864000" },
      { from: "reader", to: "hub", line: "200 OK", h: { "Content-Type": "text/plain" }, body: form("kq81Zt3w") },
      { at: "hub", note: "The echo matches: subscription 77 is active for ten days." },
      { section: "A copy comes back" },
      { from: "library", to: "hub", line: "POST /", h: { "Content-Type": "application/x-www-form-urlencoded" },
        body: form("hub.mode=publish\n&hub.url=https%3A%2F%2Flibrary.example%2Ftitles%2Fhobbit%2Fevents"),
        note: "A ping, as most hubs take one; the Recommendation leaves how a publisher tells its hub open." },
      { from: "hub", to: "library", line: "204 No Content" },
      { from: "hub", to: "library", line: "GET /titles/hobbit/events", h: { Accept: "application/json", ...ifNone("e40") } },
      { from: "library", to: "hub", line: "200 OK", h: etag("e41"), body: feedNew },
      { from: "hub", to: "reader", line: "POST /websub/42", h: { "Content-Type": "application/json", Link: "<https://hub.library.example/>; rel=\"hub\", <https://library.example/titles/hobbit/events>; rel=\"self\"", "X-Hub-Signature": "sha256=3f1c9a…b07e" }, body: feedNew },
      { from: "reader", to: "hub", line: "204 No Content" },
      { section: "What WebSub can't do: read the subscription" },
      { from: "reader", to: "hub", line: "GET /subscriptions/77", h: HAL },
      { from: "hub", to: "reader", line: "200 OK", h: etag("u3"), body: sub77("active", { leaseEnds: T(14, 0, 2, 18), delivered: 1, lastDelivery: T(15, 2, 46, 8), failedDeliveries: 0 }, sub77Forms), key: "u3" },
      { from: "reader", to: "hub", line: "POST /subscriptions/77/actions/pause", h: ifMatch("u3") },
      { from: "hub", to: "reader", line: "200 OK", h: etag("u4"),
        body: sub77("paused", { leaseEnds: T(14, 0, 2, 18), delivered: 1, paused: T(18, 0, 0, 8), held: 0 }, { "hr:resume": { title: "Resume", method: "POST", _links: { target: { href: "/subscriptions/77/actions/resume" } } }, "hr:cancel": sub77Forms["hr:cancel"] }),
        diff: "u3" },
    ],
  });

  flow({
    id: "ldn", group: "The W3C standards, extended", short: "LDN: a reply is an inbox",
    title: "Linked Data Notifications: every reply is also an inbox",
    lede: "An older library notifier that speaks only LDN finds the reply's inbox by its link and posts a notification without reading first. The same resource serves Ana's assistant in HAL.",
    points: [
      "The reply links itself as ldp:inbox, and its form set as ldp:constrainedBy: an LDN sender learns there are rules, a HEART sender gets them as a form.",
      "A plain LDN sender posts blind; the inbox takes it while awaiting and lists it under ldp:contains.",
      "A notification posted that way doesn't answer the reply. Only the deliver form does.",
    ],
    settle: "Whether inboxes take these blind appends at all, exempt from 428 on that one target (decision 15).",
    lanes: ["ldn", "inbox", "artie"],
    steps: [
      { from: "ldn", to: "inbox", line: "GET /replies/7f3a", h: { Accept: "application/ld+json" } },
      { from: "inbox", to: "ldn", line: "200 OK", h: { "Content-Type": "application/ld+json", Link: "</replies/7f3a>; rel=\"http://www.w3.org/ns/ldp#inbox\", </forms/9c41e2>; rel=\"http://www.w3.org/ns/ldp#constrainedBy\"" },
        body: { "@context": "http://www.w3.org/ns/ldp", "@id": "https://assistant.example/replies/7f3a", "@type": "ldp:Container", contains: [] } },
      { from: "ldn", to: "inbox", line: "POST /replies/7f3a", h: { "Content-Type": "application/ld+json" },
        body: { "@context": "https://www.w3.org/ns/activitystreams", type: "Announce", actor: "https://notify.library.example/", object: "https://library.example/loans/3310", summary: "The Hobbit can be renewed now.", published: T(21, 31, 3) } },
      { from: "inbox", to: "ldn", line: "201 Created", h: { Location: "/replies/7f3a/received/1" } },
      { from: "artie", to: "inbox", line: "GET /replies/7f3a", h: HAL },
      { from: "inbox", to: "artie", line: "200 OK", h: etag("r1"),
        body: { ...replyAwaiting, received: 1, _links: { ...replyAwaiting._links, [`${LDP}contains`]: [{ href: "/replies/7f3a/received/1", title: "Announce from notify.library.example" }], [`${LDP}constrainedBy`]: { href: "/forms/9c41e2", integrity: SRI.replyForms } } },
        note: "Still awaiting, still r1: a notification isn't an answer. The assistant can read the loan it names to see." },
      { section: "After the reply is answered" },
      { from: "ldn", to: "inbox", line: "POST /replies/7f3a", h: { "Content-Type": "application/ld+json" },
        body: { "@context": "https://www.w3.org/ns/activitystreams", type: "Announce", actor: "https://notify.library.example/", object: "https://library.example/loans/3310", summary: "The Hobbit can be renewed now.", published: T(21, 40, 0) } },
      { from: "inbox", to: "ldn", line: "409 Conflict", h: PROBLEM,
        body: problem("not-offered", "Not offered in this state", 409, { rel: "http://www.w3.org/ns/ldp#inbox", detail: "This reply was answered at 21:31 and takes no more notifications." }) },
    ],
  });

  flow({
    id: "websub-std", group: "The W3C standards, extended", short: "WebSub: see E", alias: "websub",
  });

  flow({
    id: "activitypub", group: "The W3C standards, extended", short: "ActivityPub: a topic as actor",
    title: "ActivityPub: a title that is also an actor",
    lede: "Ana follows The Hobbit at Riverside from her fediverse account. The Follow becomes a pending subscription, a librarian approves it, and each copy that comes back reaches her as an Announce.",
    points: [
      "Self-registration is following: the Follow arrives in the actor's inbox, and the library reads the follower before it sends anything.",
      "Approval is an act on the subscription; doing it sends Accept.",
      "Fediverse software shows the Announce as a post. A HEART client follows its object and reads the title, its state and its forms.",
      "In HAL the actor says, as a form or with a reason, whether it takes followers now: what ActivityPub can't say today.",
    ],
    lanes: ["social", "library", "librarian", "artie"],
    steps: [
      { from: "social", to: "library", line: "GET /actors/hobbit", h: { Accept: "application/activity+json" } },
      { from: "library", to: "social", line: "200 OK", h: { "Content-Type": "application/activity+json" },
        body: { "@context": ["https://www.w3.org/ns/activitystreams", { hr: "https://w3id.org/heart-rise/rels/" }], id: "https://library.example/actors/hobbit", type: "Service", preferredUsername: "hobbit", name: "The Hobbit at Riverside", summary: "Posts when a copy of The Hobbit is back on the shelf.", url: "https://library.example/titles/hobbit", inbox: "https://library.example/actors/hobbit/inbox", outbox: "https://library.example/actors/hobbit/outbox", followers: "https://library.example/actors/hobbit/followers", manuallyApprovesFollowers: true } },
      { from: "social", to: "library", line: "POST /actors/hobbit/inbox", h: { "Content-Type": "application/activity+json", "Content-Digest": "sha-256=:k2Vb…=:", "Signature-Input": "sig1=(\"@method\" \"@target-uri\" \"content-digest\");keyid=\"https://social.example/users/ana#main-key\"", Signature: "sig1=:MEUCIQ…=:" },
        body: { "@context": "https://www.w3.org/ns/activitystreams", id: "https://social.example/users/ana#follows/1", type: "Follow", actor: "https://social.example/users/ana", object: "https://library.example/actors/hobbit" } },
      { from: "library", to: "social", line: "202 Accepted", note: "The Follow becomes subscription 602, pending: the actor approves followers by hand." },
      { from: "library", to: "social", line: "GET /users/ana", h: { Accept: "application/activity+json" }, note: "Read the follower before sending to it: its inbox, and its server's shared inbox." },
      { from: "social", to: "library", line: "200 OK", h: { "Content-Type": "application/activity+json" },
        body: { "@context": "https://www.w3.org/ns/activitystreams", id: "https://social.example/users/ana", type: "Person", preferredUsername: "ana", name: "Ana Torres", inbox: "https://social.example/users/ana/inbox", endpoints: { sharedInbox: "https://social.example/inbox" } } },
      { from: "librarian", to: "library", line: "GET /subscriptions/602", h: HAL },
      { from: "library", to: "librarian", line: "200 OK", h: etag("s1"), key: "s602",
        body: { id: 602, status: "pending", by: "follow", follower: "https://social.example/users/ana", kinds: ["https://library.example/profiles/library#copy-on-shelf"], _links: { self: { href: "/subscriptions/602" }, curies: [HR], "hr:about": { href: "/titles/hobbit" } }, _forms: { "hr:approve": { title: "Approve", description: "Sends Accept to the follower.", method: "POST", _links: { target: { href: "/subscriptions/602/actions/approve" } } }, "hr:reject": { title: "Reject", description: "Sends Reject to the follower.", method: "POST", _links: { target: { href: "/subscriptions/602/actions/reject" } } } } } },
      { from: "librarian", to: "library", line: "POST /subscriptions/602/actions/approve", h: ifMatch("s1") },
      { from: "library", to: "social", line: "POST /inbox", h: { "Content-Type": "application/activity+json", "Signature-Input": "sig1=(…);keyid=\"https://library.example/actors/hobbit#main-key\"", Signature: "sig1=:MEQCIF…=:" },
        body: { "@context": "https://www.w3.org/ns/activitystreams", id: "https://library.example/activities/a-17", type: "Accept", actor: "https://library.example/actors/hobbit", object: { id: "https://social.example/users/ana#follows/1", type: "Follow", actor: "https://social.example/users/ana", object: "https://library.example/actors/hobbit" } } },
      { from: "social", to: "library", line: "202 Accepted" },
      { from: "library", to: "librarian", line: "200 OK", h: etag("s2"), diff: "s602",
        body: { id: 602, status: "active", by: "follow", follower: "https://social.example/users/ana", kinds: ["https://library.example/profiles/library#copy-on-shelf"], approvedBy: "Rosa Lind, librarian", _links: { self: { href: "/subscriptions/602" }, curies: [HR], "hr:about": { href: "/titles/hobbit" } }, _forms: { "hr:pause": { title: "Pause", method: "POST", _links: { target: { href: "/subscriptions/602/actions/pause" } } }, "hr:cancel": { title: "Remove the follower", method: "POST", _links: { target: { href: "/subscriptions/602/actions/cancel" } } } } } },
      { section: "A copy comes back" },
      { from: "library", to: "social", line: "POST /inbox", h: { "Content-Type": "application/activity+json", Signature: "sig1=:MEYCIQ…=:" },
        body: { "@context": ["https://www.w3.org/ns/activitystreams", { hr: "https://w3id.org/heart-rise/rels/" }], id: "https://library.example/activities/a-18", type: "Announce", actor: "https://library.example/actors/hobbit", to: ["https://library.example/actors/hobbit/followers"], object: "https://library.example/titles/hobbit", summary: "A copy of The Hobbit is back on the shelf at Riverside.", published: T(15, 2, 45, 8), "hr:etag": "\"t10\"" } },
      { from: "social", to: "library", line: "202 Accepted" },
      { section: "Ana unfollows" },
      { from: "social", to: "library", line: "POST /actors/hobbit/inbox", h: { "Content-Type": "application/activity+json", Signature: "sig1=:MEUCIB…=:" },
        body: { "@context": "https://www.w3.org/ns/activitystreams", id: "https://social.example/users/ana#follows/1/undo", type: "Undo", actor: "https://social.example/users/ana", object: "https://social.example/users/ana#follows/1" } },
      { from: "library", to: "social", line: "202 Accepted", note: "Subscription 602 reads cancelled." },
      { section: "The same actor, read as HEART" },
      { from: "artie", to: "library", line: "GET /actors/hobbit", h: HAL },
      { from: "library", to: "artie", line: "200 OK", h: etag("a7"),
        body: { name: "The Hobbit at Riverside", followers: 41, takingFollowers: false, _links: { self: { href: "/actors/hobbit" }, curies: [HR], "hr:about": { href: "/titles/hobbit" }, "hr:unavailable": { href: "/unavailable/f0110a", integrity: SRI.unavailable } } },
        note: "No follow form: hr:unavailable says \"This account isn't taking followers.\"" },
    ],
  });

  // ---------------------------------------------------------------------------------------------------------
  // The overview, and the pieces every flow shares.

  const OVERVIEW = [
    ["webhook", "The originator, in the act's _rise envelope", "The watch, as its state", "The library acts on the reply's deliver form, with If-Match", "LDN inbox; RFC 7240"],
    ["again", "", "", "A repeat meets 412, reads, and stops", "HTTP validators"],
    ["mapping", "", "A mapping, kept by its digest pair", "Proposed once by a model, approved by a person", ""],
    ["fail", "", "", "409 or 422 at once only when certain; later, the fail form or a lapsed watch", "RFC 9457"],
    ["poll", "Nobody", "Nobody", "The client reads the watch: If-None-Match, Prefer: wait", "RFC 7240 wait"],
    ["async", "Nobody", "", "202 and a task at Location", "RFC 7240 respond-async"],
    ["amqp", "The envelope, with an amqp address", "The watch", "One message per request; method and status in headers", "AMQP 0-9-1"],
    ["subscribe", "The subscriber, by the title's subscribe act", "The library, as subscription resources", "Thin hints to the inbox's deliver form", "WebSub's ideas; CloudEvents"],
    ["broker", "The subscriber binds its own queue", "The broker only", "The broker fans out", "AMQP 0-9-1"],
    ["queues", "The subscriber, by the subscribe act", "The library, and the broker", "A queue per subscription", "AMQP 0-9-1"],
    ["feed", "Nobody", "Nobody", "Reads with If-None-Match and Prefer: wait", "HTTP caching"],
    ["websub", "The subscriber, by hub.* fields", "The hub", "The hub posts content, signed", "WebSub"],
    ["ldn", "The sender finds ldp:inbox", "The reply", "A blind POST while awaiting", "LDN"],
    ["activitypub", "The follower, by Follow", "The library (followers)", "Announce to the shared inbox", "ActivityPub"],
  ];

  const STATES = [
    ["Reply", "assistant.example", "awaiting → answered · failed · expired · cancelled", "deliver, progress, fail (awaiting only)"],
    ["Watch", "library.example", "waiting for its mapping → waiting → delivered · lapsed · failed · cancelled", "cancel (until delivered)"],
    ["Task", "library.example", "working → on the van → done · failed · cancelled", "cancel (while working)"],
    ["Mapping", "library.example", "proposed → approved → retired; proposed → rejected", "approve, reject; retire"],
    ["Subscription", "library or hub", "pending → active ⇄ paused → expired → cancelled; pending → denied", "cancel; pause, renew, cancel; resume; renew"],
    ["Inbox", "the subscriber", "taking deliveries ⇄ paused", "deliver (while taking)"],
  ];

  const RELATIONS = [
    ["hr:reply-to", "link", "In an envelope: the reply resource to answer"],
    ["hr:forms", "link", "The affordance set, with integrity (HEART as built)"],
    ["hr:open-reply", "form", "On an inbox: brings a reply into being"],
    ["hr:deliver · hr:progress · hr:fail", "form", "On a reply, while awaiting"],
    ["hr:about", "link", "From a watch, task or subscription to what it is about"],
    ["hr:result", "link", "From a done watch or task to its result"],
    ["hr:mapping", "link", "From a watch to the mapping it waits on"],
    ["hr:approve · hr:reject · hr:retire", "form", "On a mapping or a subscription"],
    ["hr:events", "link", "From a topic to its feed, or its exchange"],
    ["hr:subscribe", "form", "On a topic or a hub"],
    ["hr:pause · hr:resume · hr:renew · hr:cancel", "form", "On subscriptions, watches and tasks, by state"],
    ["hr:consume", "link", "From a subscription to its queue"],
    ["ldp:inbox · ldp:contains · ldp:constrainedBy", "link", "LDN's own, on every reply and inbox"],
  ];

  const ENVELOPE_SCHEMA = {
    $schema: "https://json-schema.org/draft/2020-12/schema",
    $id: ENVELOPE,
    title: "RISE envelope",
    description: "Where and how to answer later: a link to a reply resource, and that reply's form set with its integrity. The recipient narrows the schemes it delivers to in its own form.",
    type: "object",
    required: ["_links"],
    properties: {
      expires: { type: "string", format: "date-time", title: "Expires", description: "After this, don't answer." },
      _links: {
        type: "object",
        required: ["hr:reply-to", "hr:forms"],
        properties: {
          "hr:reply-to": { type: "object", required: ["href"], properties: { href: { type: "string", format: "uri" } } },
          "hr:forms": { type: "object", required: ["href", "integrity"], properties: { href: { type: "string", format: "uri" }, integrity: { type: "string", pattern: "^sha(256|384|512)-" } } },
          "hr:error-reply": { type: "object", required: ["href"], properties: { href: { type: "string", format: "uri" } }, description: "Only when failures go somewhere other than the reply's own fail form." },
        },
      },
    },
  };

  // Each flow in plain English: a headline and a few short sentences, no jargon.
  const PLAIN = {
    webhook: ["Leave your number", "You ask the library to call you when your book can be renewed, and you hand them a card that says exactly where to call. When it's ready, they check the card still works, then call once. You renew. Done."],
    again: ["No double calls", "If the library forgets it already called and calls again, your line says \"already answered\" and they hang up. Only one answer ever gets in, no matter how many times anyone tries."],
    mapping: ["Learn the form once", "Your card says how you want the answer written. The first time, the library's helper works out how to fill it in, and a librarian signs off. After that, they fill it in the same way every time. No thinking, no cost."],
    fail: ["Say no only when sure", "If the library knows for sure it can't do it, it says no on the spot. If it can't know yet, it says yes, and if it goes wrong later, it tells you exactly why."],
    poll: ["Keep checking", "No card this time. The library gives you a ticket, and you keep asking \"is it ready?\" Each time, they hold the line a minute in case it happens while you wait."],
    async: ["Take a number", "For a slow job, like bringing a book over from another branch, the library says \"we're on it\" and gives you a ticket to check. You can call it off until the book is on the van."],
    amqp: ["Same call, by courier", "Same as leaving your number, but the messages go by courier instead of a direct line. Same checks, same single answer."],
    subscribe: ["Sign up for alerts", "A book club signs itself up to hear when a copy is back. The library checks the club's mailbox is real, sends a short \"it's back\" note each time, waits when the club says pause, and stops when the sign-up runs out."],
    broker: ["Tune in to the station", "The library broadcasts on a channel it tells everyone about. The club tunes in by itself. The library never knows who's listening."],
    queues: ["A mailbox of your own", "The club signs up, and the library opens a mailbox just for the club at the post office and hands over the key. Cancel, and the box is closed."],
    feed: ["Read the notice board", "The library keeps a board of what changed, newest first. Anyone can look. Nobody signs up."],
    websub: ["The alert service everyone already uses", "Lots of websites already use one standard alert service. The library runs one that works the usual way, and adds a page where you can see your sign-up and pause it."],
    ldn: ["A mailbox anyone can find", "Every answer card is also a mailbox that older systems know how to drop notes in. A note is just a note: only the proper form counts as the answer."],
    activitypub: ["Follow it like an account", "The book is an account you can follow from apps like Mastodon. A librarian approves you, and you get a post each time a copy comes back."],
  };

  window.RISE = {
    LANES, FLOWS, OVERVIEW, STATES, RELATIONS, PLAIN,
    PIECES: [
      { title: "The envelope's schema", text: "Every act that takes _rise refers to it by $ref, with hr:integrity, so the form itself says the act can answer later.", doc: ENVELOPE_SCHEMA },
      { title: "A thin hint", text: "What a subscriber's inbox receives: CloudEvents' own attributes, with the resource's version after the change as an etag extension.", doc: hint("evt-90211", T(15, 2, 44, 8), "t10") },
      { title: "A mapping, approved", text: "Kept by the digest pair, run with no model. Only JSON Pointers, value tables and constants.", doc: mappingApproved },
      { title: "The reply's form set", text: "The same bytes for every loan-standing reply: the target is a template filled from each reply's own id, so the digest never changes per request.", doc: forms9c },
    ],
  };
})();
