// A run report: what a client asked, step by step, and what came back. Reads the trace in
// <script type="application/json" id="run-trace"> (format: heart-run-report skill) and renders one row
// per step: when, what was asked (an intent in words, or a scripted relation request) with its parameter
// values marked, and the answer in one sentence. Each row opens to the request and answer lines, and
// those to the documents, through json-view.js. Toggles at the top open or close each kind at once.
(() => {
  const trace = JSON.parse(document.getElementById("run-trace").textContent);
  const root = document.getElementById("run-report");
  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text;
    return node;
  };

  // Absolute time, in the reader's own time zone, named.
  const when = new Intl.DateTimeFormat(undefined, {
    year: "numeric", month: "short", day: "numeric",
    hour: "2-digit", minute: "2-digit", second: "2-digit", timeZoneName: "short",
  });
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  // The sentence, each {name} replaced by its value, marked.
  const sentence = (step) => {
    const out = el("span", "rr-say");
    const parts = (step.say || "").split(/(\{[A-Za-z0-9_]+\})/);
    parts.forEach((part) => {
      const name = /^\{([A-Za-z0-9_]+)\}$/.exec(part);
      if (name && step.params && name[1] in step.params) {
        const value = step.params[name[1]];
        const mark = el("mark", "rr-param", typeof value === "string" ? value : JSON.stringify(value));
        mark.title = name[1];
        out.append(mark);
      } else if (part) out.append(document.createTextNode(part));
    });
    return out;
  };

  // The answer in one sentence: what happened, and what the representation holds.
  const header = (response, name) => {
    const key = Object.keys(response.headers || {}).find((k) => k.toLowerCase() === name.toLowerCase());
    return key ? response.headers[key] : undefined;
  };
  // SPEC §6.1: the state is the body without exactly these four members; any other member is data.
  const CONTROLS = ["_links", "_embedded", "_forms", "_templates"];
  const perRel = (group, skip) => Object.entries(group || {}).filter(([rel]) => rel !== skip)
    .reduce((n, [, v]) => n + (Array.isArray(v) ? v.length : 1), 0);
  const counts = (body) => {
    if (!body || typeof body !== "object" || Array.isArray(body)) return "";
    const data = Object.keys(body).filter((k) => !CONTROLS.includes(k)).length;
    const forms = Object.keys(body._forms || body._templates || {}).length;
    const links = perRel(body._links, "curies");
    const embedded = perRel(body._embedded);
    const plural = (n, one) => `${n} ${one}${n === 1 ? "" : "s"}`;
    const also = embedded ? `, ${embedded} embedded` : "";
    return `${plural(data, "data field")}, ${plural(forms, "form")}, ${plural(links, "link")}${also}.`;
  };
  // RFC 9457: a problem document says type, title and status.
  const problem = (body) => !!body && typeof body === "object" && "type" in body && "title" in body && "status" in body;

  // Two kinds of trace. An HTTP trace (live/github scripts) holds each exchange: request, response, status.
  // A neutral trace (heart-script's, any binding) holds HEART's outcome by meaning: "done", "new resource",
  // "refused" with its kind and side, "held" or "failed" for a check; the address the client was then at;
  // and what it held there (now: state, forms, links). These accessors read either.
  const neutral = (step) => !step.response;
  const verbOf = (step) => (step.say || "").split(/[\s,]/)[0];
  const reads = (step) => (neutral(step) ? ["enter", "follow", "return"].includes(verbOf(step)) : step.request.method === "GET");
  const urlOf = (step) => (neutral(step) ? step.address || "" : step.request.url);
  // What the step answered with, as a HAL-shaped body so counts and forms read it alike.
  const bodyOf = (step) => {
    if (!neutral(step)) return step.response.body;
    if (!step.now) return undefined;
    const forms = Object.fromEntries((step.now.forms || []).map((f) => [f, {}]));
    const links = Object.fromEntries(Object.entries(step.now.links || {}).map(([rel, titles]) =>
      [rel, titles.map((title) => (title === null ? {} : { title }))]));
    const embedded = Object.fromEntries(Object.entries(step.now.embedded || {}).map(([rel, resources]) =>
      [rel, resources.map((r) => ({ ...r.state, _links: { self: r.title === null ? {} : { title: r.title } } }))]));
    return { ...step.now.state, _forms: forms, _links: links, _embedded: embedded };
  };
  // The answer as one key: the status for HTTP, the outcome (and refusal kind) for a neutral trace.
  const answerKey = (step) => (neutral(step)
    ? (step.refused ? `refused: ${step.refused.kind}` : step.outcome)
    : String(step.response.status));
  // What the step expected: a status or "2xx" for HTTP; words for a neutral trace. A step that says nothing
  // expects any success, as "2xx" does over HTTP: done, a new resource, or a check that held.
  const SUCCESS = "success";
  const wantOf = (step) => (neutral(step)
    ? step.expect || SUCCESS
    : step.expect === undefined || step.expect === "2xx" ? "2xx" : Number(step.expect));
  const NEUTRAL_MADE = [SUCCESS, "done", "new resource", "unchanged"];
  const wantsRefusal = (step) => (neutral(step) ? !NEUTRAL_MADE.includes(wantOf(step))
    : typeof wantOf(step) === "number" && wantOf(step) >= 400);
  const wantsCreation = (step) => (neutral(step) ? wantOf(step) === "new resource" : wantOf(step) === 201);
  // A neutral "done" also meets "unchanged" here: the test add-on judged that when the run happened.
  const neutralMet = (step) => {
    const want = wantOf(step);
    if (step.refused) return want === step.refused.kind;
    if (want === SUCCESS) return ["done", "new resource", "held"].includes(step.outcome);
    if (want === "unchanged") return step.outcome === "done";
    return want === step.outcome;
  };

  const summary = (step) => {
    if (step.summary) return step.summary;
    if (neutral(step)) {
      const planned = step.expect && expected(step) ? " as planned" : "";
      if (step.refused) return `Refused${planned} by ${step.refused.by}, ${step.refused.kind}: ${step.refused.detail}`;
      if (step.outcome === "held" || step.outcome === "failed") return step.outcome === "held" ? "The check held." : "The check failed.";
      const what = step.outcome === "new resource" ? `A new resource, ${pathOf(step.address)}.`
        : reads(step) ? "Read." : "Done.";
      return `${what} ${counts(bodyOf(step))}`.trim();
    }
    const res = step.response, body = res.body, method = step.request.method;
    if (problem(body)) {
      const planned = typeof step.expect === "number" && step.expect === res.status;
      return `Refused${planned ? " as planned" : ""}, ${res.status}: ${body.detail || body.title}`;
    }
    const location = header(res, "Location");
    const what = res.status === 201 ? `Created ${location || "a resource"}.`
      : res.status === 304 ? "Not modified; the copy held is current."
      : res.status < 300 ? (method === "GET" ? "Read." : "Done.")
      : `Answered ${res.status}.`;
    return `${what} ${counts(body)}`.trim();
  };

  // A status is coloured by whether the step expected it, not by its class: a planned 409 is a pass.
  const statusClass = (step) => `rr-st ${expected(step) ? "ok" : "bad"}`;

  const line = (who, text) => {
    const p = el("p", "rr-line");
    p.append(el("span", "rr-who", who), el("code", null, text));
    return p;
  };
  const requestText = (req) => {
    const headers = Object.entries(req.headers || {}).filter(([k]) => /^(if-match|if-none-match|prefer|accept)$/i.test(k));
    return [`${req.method} ${req.url}`, ...headers.map(([k, v]) => `${k}: ${v}`)].join("   ");
  };
  const answerText = (res) => {
    const kept = Object.entries(res.headers || {}).filter(([k]) => /^(location|content-location|etag)$/i.test(k));
    return [`HTTP ${res.status}`, ...kept.map(([k, v]) => `${k}: ${v}`)].join("   ");
  };

  // One kind of drill-down, so the toggles can find it.
  const box = (kind, options) => {
    const node = window.JsonView.box(options);
    node.dataset.kind = kind;
    return node;
  };

  // A neutral step's drill-down: where the client was, the values it gave, and what it then held, as what
  // changed when it held the same resource the step before.
  const neutralDetail = (step, index) => {
    const body = el("div", "rr-detail");
    const lines = el("div", "rr-lines");
    lines.dataset.kind = "lines";
    const values = Object.entries(step.params || {}).map(([k, v]) => `${k}: ${typeof v === "string" ? v : JSON.stringify(v)}`);
    lines.append(line("asked", [verbOf(step), ...values].join("   ")),
      line("answer", [answerKey(step), step.address].filter(Boolean).join("   ")));
    body.append(lines);
    const fields = Object.fromEntries(Object.entries(step.params || {}).filter(([k]) => k !== "relation"));
    if (Object.keys(fields).length) body.append(box("request", { a: fields, labelA: "the values given", summary: "Values given" }));
    if (step.now) {
      const prior = trace.steps.slice(0, index).reverse().find((s) => s.now && s.address === step.address);
      body.append(box("response", prior
        ? { a: prior.now.state, b: step.now.state, labelA: "the state before", labelB: "the state now",
            summary: "State, as what it changed" }
        : { a: step.now.state, labelA: "the state", summary: "State held" }));
    }
    return body;
  };

  const detail = (step, index) => {
    if (neutral(step)) return neutralDetail(step, index);
    const body = el("div", "rr-detail");
    const lines = el("div", "rr-lines");
    lines.dataset.kind = "lines";
    lines.append(line("request", requestText(step.request)), line("answer", answerText(step.response)));
    body.append(lines);
    if (step.request.body !== undefined && step.request.body !== null) {
      body.append(box("request", { a: step.request.body, labelA: "the request's body", summary: "Request body" }));
    }
    if (step.response.body !== undefined && step.response.body !== null) {
      const before = step.before && typeof step.response.body === "object" && !problem(step.response.body);
      body.append(box("response", before
        ? { a: step.before, b: step.response.body, labelA: "the state before", labelB: "the answer",
            summary: "Answer, as what it changed" }
        : { a: step.response.body, labelA: "the answer", summary: "Answer body" }));
    }
    if (step.explanation) {
      body.append(box("explanation", { a: step.explanation, labelA: "hr:unavailable", summary: "Why the rest are absent" }));
    }
    if (step.compare) {
      const label = step.compare.label || "the same, another way";
      const lead = el("div", "rr-lines");
      if (step.compare.request) lead.append(line("request", requestText(step.compare.request)));
      if (step.compare.response) lead.append(line("answer", answerText(step.compare.response)));
      const sent = step.compare.response ? step.compare.response.body : step.compare.request.body;
      const a = sent === undefined || sent === null ? "(no body)" : sent;
      const b = step.compare.response && step.compare.diff !== false ? step.response.body : undefined;
      body.append(box("compare", { a, b, labelA: label, labelB: "HEART's answer", summary: `Compared: ${label}`, lead }));
    }
    return body;
  };

  const row = (step, index) => {
    const item = el("details", "rr-step");
    const head = el("summary", "rr-row");
    const at = step.at ? new Date(step.at) : null;
    const time = el("time", "rr-time", at ? when.format(at) : "");
    if (at) time.dateTime = step.at;
    const ask = el("div", "rr-ask");
    ask.append(el("span", `rr-mode ${step.mode === "intent" ? "intent" : "script"}`, step.mode === "intent" ? "intent" : "script"), sentence(step));
    const answer = el("div", "rr-answer");
    const badge = el("span", statusClass(step), neutral(step) ? (step.refused ? "refused" : step.outcome) : String(step.response.status));
    badge.title = expected(step) ? "as the step expected" : `the step expected ${wantOf(step)}`;
    answer.append(badge, el("span", null, ` ${summary(step)}`));
    head.append(el("span", "rr-n", String(index + 1)), time, ask, answer);
    item.append(head);
    item.addEventListener("toggle", () => {
      if (item.open && !item.dataset.rendered) { item.dataset.rendered = "1"; item.append(detail(step, index)); applyAll(item); }
    });
    return item;
  };

  // The toggles: each opens or closes one kind of detail everywhere.
  const kinds = [
    ["steps", "Steps"], ["lines", "Request and answer lines"], ["request", "Request bodies"],
    ["response", "Answer bodies"], ["explanation", "Why absent"], ["compare", "Comparisons"],
  ];
  const state = Object.fromEntries(kinds.map(([k]) => [k, false]));
  state.lines = true;
  const apply = (scope, kind) => {
    if (kind === "lines") scope.querySelectorAll('[data-kind="lines"]').forEach((n) => { n.hidden = !state.lines; });
    else scope.querySelectorAll(`details.jv[data-kind="${kind}"]`).forEach((n) => { n.open = state[kind]; });
  };
  const applyAll = (scope) => kinds.forEach(([k]) => k !== "steps" && apply(scope, k));

  const bar = el("div", "rr-toggles");
  kinds.forEach(([kind, label]) => {
    const wrap = el("label", "rr-toggle"), input = el("input");
    input.type = "checkbox";
    input.checked = state[kind];
    input.addEventListener("change", () => {
      state[kind] = input.checked;
      if (kind === "steps") root.querySelectorAll("details.rr-step").forEach((n) => { n.open = state.steps; });
      else {
        // Opening a kind of detail opens the steps that hold it.
        if (state[kind] && kind !== "lines") root.querySelectorAll("details.rr-step").forEach((n) => { n.open = true; });
        apply(root, kind);
      }
    });
    wrap.append(input, document.createTextNode(` ${label}`));
    bar.append(wrap);
  });

  // ---- Summary tiles: the run at a glance; a tile that can filters the rows to its steps. ----
  const pathOf = (url) => { try { const u = new URL(url, "http://x"); return u.pathname + u.search; } catch { return url; } };
  function expected(step) {
    if (neutral(step)) return neutralMet(step);
    return step.expect === undefined || step.expect === "2xx"
      ? step.response.status < 300 : step.response.status === Number(step.expect);
  }
  const refused = (step) => (neutral(step) ? !!step.refused : step.response.status >= 400);
  const created = (step) => (neutral(step) ? step.outcome === "new resource" : step.response.status === 201);
  const relationOf = (step) => (step.params && step.params.relation)
    || (neutral(step) ? verbOf(step) : step.request.method === "GET" ? "follow" : step.request.method);
  const resourceOf = (step) => pathOf(urlOf(step)).split("?")[0].split("/actions/")[0];

  // Every address an answer hands the client: hrefs and form targets anywhere in it, and Location headers.
  const addresses = (body, into) => {
    if (Array.isArray(body)) body.forEach((v) => addresses(v, into));
    else if (body && typeof body === "object") {
      Object.entries(body).forEach(([k, v]) => {
        if (k === "href" && typeof v === "string") into.add(pathOf(v));
        else addresses(v, into);
      });
    }
    return into;
  };
  const fits = (url, known) => {
    const path = pathOf(url);
    if (known.has(path) || known.has(path.split("?")[0])) return true;
    // A templated target (RFC 6570, as forms use them) fits when the URL fills it.
    return [...known].some((t) => t.includes("{") && new RegExp("^" + t.replace(/[.*+^$()|[\]\\]/g, "\\$&")
      .replace(/\{\?[^}]*\}/g, "(\\?.*)?").replace(/\{[^}]*\}/g, "[^/?]+") + "$").test(path));
  };
  // Which steps' requests came from the server (a link, a form, a Location), and which addresses the run
  // knew beforehand: its entry points.
  const provenance = () => {
    const known = new Set(), entries = new Set(), fromServer = new Set();
    // A neutral trace's selections are by relation, on what the client holds: only an entry is an address
    // the script knew. Every other step's address came from the server, or was one it already held.
    if (trace.steps.every(neutral)) {
      trace.steps.forEach((step, i) => {
        if (verbOf(step) === "enter") entries.add(String((step.params || {}).entry || step.address));
        else fromServer.add(i);
      });
      return { entries, fromServer };
    }
    trace.steps.forEach((step, i) => {
      if (step.held) {
        if (!fits(step.held.url, known)) entries.add(pathOf(step.held.url));
        known.add(pathOf(step.held.url));
        addresses(step.held.body, known);
      }
      if (fits(step.request.url, known)) fromServer.add(i);
      else entries.add(pathOf(step.request.url));
      addresses(step.response.body, known);
      Object.entries(step.response.headers || {}).forEach(([k, v]) => {
        if (/^(content-)?location$/i.test(k)) known.add(pathOf(v));
      });
    });
    return { entries, fromServer };
  };

  const filterBar = el("div", "rr-filter");
  filterBar.hidden = true;
  let rows = [];
  const filterBy = (label, test) => {
    let shown = 0;
    rows.forEach((r, i) => { r.hidden = !test(trace.steps[i], i); if (!r.hidden) shown++; });
    filterBar.replaceChildren(el("span", null, `${label}: showing ${shown} of ${rows.length}`));
    const clear = el("button", "rr-clear", "clear ✕");
    clear.type = "button";
    clear.addEventListener("click", () => { rows.forEach((r) => { r.hidden = false; }); filterBar.hidden = true; });
    filterBar.append(clear);
    filterBar.hidden = false;
  };
  const tile = (label, value, note, filter) => {
    // A div acting as a button: some tiles hold buttons of their own (status keys, relation keys).
    const node = el("div", "rr-tile can");
    node.setAttribute("role", "button");
    node.tabIndex = 0;
    node.dataset.label = label;
    node._filter = filter;
    node.addEventListener("click", () => explainTile(node));
    node.addEventListener("keydown", (e) => {
      if (e.target === node && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); explainTile(node); }
    });
    node.append(el("span", "rr-tile-label", label), el("span", "rr-tile-value", value));
    if (note !== undefined) node.append(typeof note === "string" ? el("span", "rr-tile-note", note) : note);
    return node;
  };
  // A judged tile says what was expected, in words, and is shaded by how much of it failed: a thin green
  // when nothing did; else one of five reds, light to deep, by the failed share rounded up to a fifth.
  const judge = (node, fails, of, expectation) => {
    node.dataset.judged = "1";
    node.append(el("span", "rr-tile-expect", expectation));
    const quintile = of ? Math.min(5, Math.ceil((fails / of) * 5)) : 0;
    node.classList.add(fails ? `fail-${Math.max(1, quintile)}` : "pass");
    node.title = fails ? `${fails} of ${of} failed` : "all as expected";
    return node;
  };
  // The first few, and how many more: a tile stays short however a run went.
  const firstFew = (items, show = 4) =>
    items.slice(0, show).join(", ") + (items.length > show ? ` +${items.length - show} more` : "");
  const numbered = (list) => firstFew(list.map((i) => `#${i + 1}`));
  const plural = (n, one, many) => `${n} ${n === 1 ? one : many || one + "s"}`;
  const seconds = (ms) => ms >= 60000 ? `${Math.floor(ms / 60000)} m ${Math.round((ms % 60000) / 1000)} s`
    : ms >= 1000 ? `${(ms / 1000).toFixed(1)} s` : `${ms} ms`;
  const clock = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit", timeZoneName: "short" });

  const facts = {};
  const tiles = () => {
    const steps = trace.steps, n = steps.length, box = el("div", "rr-tiles");
    facts.n = n;
    // Outcome
    const indexes = (test) => steps.map((st, i) => (test(st) ? i : -1)).filter((i) => i >= 0);
    const misses = indexes((st) => !expected(st));
    const wantStatus = wantOf;
    const wantRefused = indexes(wantsRefusal);
    const wantCreated = indexes(wantsCreation);
    Object.assign(facts, { misses: misses.length, wantRefused: wantRefused.length, wantCreated: wantCreated.length });
    const outcome = tile("Outcome", misses.length ? `✗ ${misses.length} of ${n}` : `✓ ${n} / ${n}`,
      misses.length ? `unexpected: ${numbered(misses)}` : "as expected",
      misses.length ? () => filterBy("Unexpected", (st) => !expected(st)) : undefined);
    box.append(judge(outcome, misses.length, n,
      `expected: ${n - wantRefused.length - wantCreated.length} succeed, ${wantCreated.length} create, ` +
      `${wantRefused.length} refused on purpose`));
    // Steps
    const follows = steps.filter(reads).length;
    const intents = steps.filter((s) => s.mode === "intent").length;
    const checks = steps.filter((s) => neutral(s) && verbOf(s) === "check").length;
    Object.assign(facts, { follows, intents });
    box.append(tile("Steps", String(n), `${plural(n - follows - checks, "act")} · ${plural(follows, "follow")} · ` +
      (checks ? `${plural(checks, "check")} · ` : "") + `${intents} intent / ${n - intents} script`));
    // Duration
    const times = steps.map((s) => s.at && new Date(s.at)).filter(Boolean);
    if (times.length) {
      const first = times[0], last = times[times.length - 1];
      box.append(tile("Duration", seconds(last - first), `${clock.format(first)} → ${clock.format(last)}`));
    }
    // Answers: a stacked bar, one segment per status, each a filter.
    const byStatus = {};
    steps.forEach((s) => { byStatus[answerKey(s)] = (byStatus[answerKey(s)] || 0) + 1; });
    const bar = el("span", "rr-bar"), legend = el("span", "rr-tile-note");
    const order = ([a], [b]) => (isNaN(a) || isNaN(b) ? a.localeCompare(b) : a - b);
    Object.entries(byStatus).sort(order).forEach(([status, count]) => {
      const sameKey = (s) => answerKey(s) === status;
      const fine = steps.every((st) => !sameKey(st) || expected(st));
      const planned = steps.some((st) => sameKey(st) && refused(st)) && fine;
      const seg = el("button", `rr-seg ${fine ? "ok" : "bad"}`);
      seg.type = "button";
      seg.style.flexGrow = count;
      seg.title = `${status} × ${count}`;
      seg.addEventListener("click", (e) => { e.stopPropagation(); filterBy(status, sameKey); });
      bar.append(seg);
      const key = el("button", `rr-key ${fine ? "ok" : "bad"}`, `${status} ×${count}${planned ? " as planned" : fine ? "" : " unexpected"}`);
      key.type = "button";
      key.addEventListener("click", (e) => { e.stopPropagation(); filterBy(status, sameKey); });
      legend.append(key);
    });
    const answers = tile("Answers", "", legend);
    answers.querySelector(".rr-tile-value").replaceWith(bar);
    answers.classList.add("wide");
    box.append(judge(answers, misses.length, n, misses.length
      ? `unexpected: ${firstFew(misses.map((i) => `#${i + 1} got ${answerKey(steps[i])}, expected ${wantStatus(steps[i])}`))}`
      : "expected: every answer as the run said"));
    // Created
    const made = steps.filter(created);
    const madeAt = indexes(created);
    facts.created = madeAt.length;
    // A creation counts against the run only when the step expected something else, in so many words.
    const createMisses = wantCreated.filter((i) => !madeAt.includes(i)).length +
      madeAt.filter((i) => !wantCreated.includes(i) && steps[i].expect !== undefined).length;
    box.append(judge(tile("Created", String(made.length),
      made.map((st) => {
        const loc = (neutral(st) ? st.address : header(st.response, "Location")) || "";
        return loc.split("/").slice(-2).join("/");
      }).join(", ") || "nothing",
      made.length ? () => filterBy("Created", created) : undefined),
      createMisses, Math.max(wantCreated.length, madeAt.length, 1),
      `expected ${wantCreated.length} created${wantCreated.length ? ` (${numbered(wantCreated)})` : ""} · got ${madeAt.length}`));
    // Refused
    const no = steps.filter(refused);
    const why = {};
    no.forEach((s) => {
      if (neutral(s)) {
        const k = `${s.refused.kind}, by ${s.refused.by}`;
        why[k] = (why[k] || 0) + 1;
        return;
      }
      const kind = s.response.status === 409 ? "not offered" : s.response.status === 412 ? "stale"
        : s.response.status === 428 ? "no validator" : s.response.status === 422 ? "invalid" : String(s.response.status);
      why[`${s.response.status} ${kind}`] = (why[`${s.response.status} ${kind}`] || 0) + 1;
    });
    const refusedAt = indexes(refused);
    const surprise = refusedAt.filter((i) => !wantRefused.includes(i));
    const missing = wantRefused.filter((i) => !refusedAt.includes(i));
    const verdict = [surprise.length && `${surprise.length} unexpected`, missing.length && `${missing.length} didn't happen`]
      .filter(Boolean).join(", ") || (refusedAt.length === 1 ? "expected" : refusedAt.length ? "all expected" : "");
    Object.assign(facts, { refused: refusedAt.length, surprise: surprise.length, missing: missing.length });
    const refusedTile = tile("Refused", verdict ? `${refusedAt.length} · ${verdict}` : "0",
      Object.entries(why).map(([k, c]) => c > 1 ? `${k} ×${c}` : k).join(" · ") || "none",
      no.length ? () => filterBy("Refused", refused) : undefined);
    box.append(judge(refusedTile, surprise.length + missing.length, n,
      wantRefused.length
        ? `expected on purpose: ${firstFew(wantRefused.map((i) => `#${i + 1} ${wantStatus(steps[i])}`))}` +
          (missing.length ? ` · not refused: ${numbered(missing)}` : "")
        : "expected: none"));
    // Relations: the distinct relations asked for, the most used each a filter.
    const rels = {};
    steps.forEach((s) => { const r = relationOf(s); rels[r] = (rels[r] || 0) + 1; });
    const top = el("span", "rr-tile-note");
    Object.entries(rels).sort((a, b) => b[1] - a[1]).slice(0, 4).forEach(([rel, count]) => {
      const key = el("button", "rr-key", `${rel.replace(/^[a-z]+:/, "")} ×${count}`);
      key.type = "button";
      key.addEventListener("click", (e) => { e.stopPropagation(); filterBy(rel, (s) => relationOf(s) === rel); });
      top.append(key);
    });
    facts.relations = Object.keys(rels).length;
    box.append(tile("Relations", `${Object.keys(rels).length} distinct`, top));
    // Resources
    const resources = new Set(steps.map(resourceOf));
    facts.resources = resources.size;
    facts.resourceList = [...resources];
    box.append(tile("Resources", String(resources.size), "touched, by address"));
    // Hypermedia: requests whose address came from the server.
    const { entries, fromServer } = provenance();
    Object.assign(facts, { fromServer: fromServer.size, entries: [...entries] });
    // A script's own entry is its bookmark, the one address it may know: not judged.
    const entering = steps.filter((s) => neutral(s) && verbOf(s) === "enter").length;
    const judged = n - entering;
    facts.hyperOf = judged;
    const hyper = tile("Hypermedia", `${fromServer.size} / ${judged}`,
      `from the server · ${plural(entries.size, "entry address", "entry addresses")}`,
      fromServer.size < judged ? () => filterBy("Not from the server", (st, i) => !fromServer.has(i)) : undefined);
    box.append(judge(hyper, judged - fromServer.size, judged,
      entering ? "expected: every address after the entry from the server" : "expected: every address from a link, a form or a Location"));
    // Controls seen
    // Where the most and the fewest were offered: a step and the resource it answered with.
    const formsOf = (st) => {
      const b = bodyOf(st);
      return b && typeof b === "object" ? Object.keys(b._forms || b._templates || {}).length : null;
    };
    const formCounts = steps.map(formsOf).filter((c) => c !== null);
    const explained = steps.filter((s) => s.explanation).length;
    facts.formsMin = formCounts.length ? Math.min(...formCounts) : 0;
    facts.formsMax = formCounts.length ? Math.max(...formCounts) : 0;
    const where = (count) => {
      const i = steps.findIndex((st) => formsOf(st) === count);
      const at = neutral(steps[i] || {}) ? urlOf(steps[i]) : i >= 0 && (header(steps[i].response, "Content-Location") || steps[i].request.url);
      return i < 0 ? "" : `step #${i + 1}, ${pathOf(at).split("/actions/")[0]}`;
    };
    facts.formsMaxAt = where(facts.formsMax);
    facts.formsMinAt = where(facts.formsMin);
    facts.explained = explained;
    box.append(tile("Forms offered", formCounts.length ? `${facts.formsMin}–${facts.formsMax} per answer` : "none",
      `${facts.formsMax} at most, ${facts.formsMin} at least · reasons fetched ${explained}×`));
    // Latency
    const timed = steps.map((s, i) => [s.ms, i]).filter(([ms]) => typeof ms === "number");
    if (timed.length) {
      const sorted = timed.map(([ms]) => ms).sort((a, b) => a - b);
      const median = sorted[Math.floor((sorted.length - 1) / 2)];
      const [maxMs, maxAt] = timed.reduce((m, t) => (t[0] > m[0] ? t : m));
      Object.assign(facts, { median: seconds(median), slowest: seconds(maxMs), slowestAt: maxAt + 1 });
      box.append(tile("Latency", `median ${seconds(median)}`, `slowest ${seconds(maxMs)} (#${maxAt + 1})`,
        () => filterBy("Slowest", (s, i) => i === maxAt)));
    } else {
      box.append(tile("Latency", "—", "not recorded in this trace"));
    }
    return box;
  };

  // What each tile means: what it counts, how, and what this run's numbers say. Shown in the overlay a tap
  // opens, and beside each tile in the list layout.
  const ABOUT = {
    "Outcome": (f) => [
      "Did every step go the way the script said it would?",
      "Before a step runs, the script writes down the answer it expects: a success (any 2xx), a creation (201), or a refusal it is provoking on purpose (409: the act isn't offered now; 412: the state changed since the client read it). Outcome counts the steps whose answer matched.",
      f.misses ? `Here ${f.misses} of ${f.n} steps got an answer the script didn't expect; the tile names them.`
        : `Here all ${f.n} matched: ${f.n - f.wantCreated - f.wantRefused} succeeded, ${f.wantCreated} created something, and ${f.wantRefused} were refused, as intended.`,
      "Green: everything as expected. Pink to deep red: some steps weren't, darker the larger their share."],
    "Steps": (f) => [
      "How many requests the run made, and of what kind.",
      "An act changes something: the client sends a form the server offered. A follow only reads: the client follows a link. An intent step came from words (a person, or the reasoner choosing); a script step was written in advance.",
      `Here: ${f.n - f.follows} acts and ${f.follows} follows; ${f.intents} from words, ${f.n - f.intents} scripted.`,
      "Not judged: it is a fact about the run, not a pass or fail."],
    "Duration": () => [
      "How long the run took, from its first step to its last, by the clock.",
      "Times are absolute and shown in your own time zone. Waits between steps count too, such as a client reading again until the server has settled.",
      "Not judged."],
    "Answers": (f) => [
      "Every answer, as a bar: the wider a segment, the more steps got that answer. An HTTP trace shows statuses; a script's trace shows HEART's outcomes, which name no protocol.",
      "2xx or done: done (200 read or acted, 201 or new resource: created). 4xx or refused: refused (409 or not offered, 412 or stale, 422 or invalid input, 428 no validator sent). A check holds or fails.",
      f.misses ? `Here ${f.misses} statuses differ from what their steps expected.` : "Here every status is the one its step expected.",
      "Tap a status to show only those steps."],
    "Created": (f) => [
      "How many acts brought a new resource into being.",
      "The server answers such an act 201 Created and names the new resource in its Location header; the client follows that address, never builds it. The tile compares the steps that expected 201 with those that got it.",
      `Here: ${f.wantCreated} expected, ${f.created} created.`],
    "Refused": (f) => [
      "How many acts the server refused, and whether that was the point.",
      "A refusal can be exactly what a step tests: 409 means the act isn't offered in the resource's state now (the server says why), 412 means the resource changed since the client read it, so the client's view is stale. A refusal the script didn't plan, or a planned one that didn't happen, is a failure.",
      `Here: ${f.refused} refused; ${f.wantRefused} planned; ${f.surprise} unplanned; ${f.missing} planned but not refused.`],
    "Relations": (f) => [
      "Which forms and links the run used, by name.",
      "These names are the API's vocabulary (its profile): what a script knows in advance. It never knows URLs; the server hands those over in each answer.",
      `Here ${f.relations} different names were used; tap one to show its steps.`,
      "Not judged."],
    "Resources": (f) => [
      "How many different resources the run read or acted on, counted by address.",
      `Here ${f.resources}: ${firstFew(f.resourceList, 6)}.`,
      "Not judged."],
    "Hypermedia": (f) => [
      "Did the client get every address from the server?",
      "HEART's rule: a client starts from a known entry address and from then on uses only addresses the server hands it, in a link, a form's target, or a Location header. This counts the requests whose address came from an earlier answer.",
      `Here ${f.fromServer} of ${f.hyperOf}, from ${f.entries.length} entry address${f.entries.length === 1 ? "" : "es"}: ${f.entries.join(", ")}.`,
      "Green: no address was built or guessed by the client."],
    "Forms offered": (f) => [
      "How many acts each answer offered, as forms: the things the client may do now.",
      `A resource offers only the acts its state allows, so the count changes with the resource and its state. Here the most was ${f.formsMax} (${f.formsMaxAt}) and the fewest ${f.formsMin} (${f.formsMinAt}).`,
      `"Reasons fetched" counts the times the client followed hr:unavailable to read why an act was absent: ${f.explained} here.`,
      "Not judged."],
    "Latency": (f) => f.median ? [
      "How long each exchange took, as the client measured it.",
      "It includes whatever the server does before it answers. A gateway, for one, reads its upstream again just before acting, so an act through it costs at least two upstream round trips.",
      `Here the median was ${f.median}; the slowest ${f.slowest}, step #${f.slowestAt}.`,
      "Not judged. Tap \"Show these steps\" to see the slowest."] : ["This trace recorded no timings."],
  };

  // The overlay: one tile explained, its own value repeated, and its filter offered when it has one.
  const overlay = el("div", "rr-overlay");
  overlay.hidden = true;
  const closeOverlay = () => { overlay.hidden = true; };
  overlay.addEventListener("click", (e) => { if (e.target === overlay) closeOverlay(); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeOverlay(); });
  const aboutOf = (label) => (ABOUT[label] ? ABOUT[label](facts) : []);
  function explainTile(node) {
    const panel = el("div", "rr-panel");
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-label", node.dataset.label);
    const copy = node.cloneNode(true);
    copy.removeAttribute("role");
    copy.removeAttribute("tabindex");
    copy.classList.remove("can");
    copy.classList.add("copy");
    panel.append(copy);
    aboutOf(node.dataset.label).forEach((text, i) => panel.append(el("p", i === 0 ? "rr-about-lead" : "rr-about", text)));
    const actions = el("div", "rr-panel-actions");
    if (node._filter) {
      const show = el("button", "rr-action", "Show these steps");
      show.type = "button";
      show.addEventListener("click", () => { closeOverlay(); node._filter(); });
      actions.append(show);
    }
    const close = el("button", "rr-action quiet", "Close");
    close.type = "button";
    close.addEventListener("click", closeOverlay);
    actions.append(close);
    panel.append(actions);
    overlay.replaceChildren(panel);
    overlay.hidden = false;
    close.focus();
  }

  // Each tile in a card, its explanation beside it; shown only in the list layout.
  const cards = (box) => {
    [...box.children].forEach((node) => {
      const card = el("div", "rr-card");
      if (node.classList.contains("wide")) card.classList.add("wide");
      const about = el("div", "rr-card-about");
      aboutOf(node.dataset.label).forEach((text, i) => about.append(el("p", i === 0 ? "rr-about-lead" : "rr-about", text)));
      box.insertBefore(card, node);
      card.append(node, about);
    });
    return box;
  };

  // Page controls: the tiles as a grid or as a list with explanations, and the page's theme.
  const remember = (key, value) => { try { localStorage.setItem(key, value); } catch { /* a private window */ } };
  const recall = (key) => { try { return localStorage.getItem(key); } catch { return null; } };
  const switchOf = (label, choices, current, onPick) => {
    const group = el("div", "rr-switch");
    group.setAttribute("role", "group");
    group.setAttribute("aria-label", label);
    group.append(el("span", "rr-switch-label", label));
    choices.forEach(([value, text]) => {
      const b = el("button", "rr-switch-choice", text);
      b.type = "button";
      b.setAttribute("aria-pressed", String(value === current));
      b.addEventListener("click", () => {
        group.querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
        onPick(value);
      });
      group.append(b);
    });
    return group;
  };
  const setTheme = (theme) => {
    if (theme === "system") delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = theme;
    remember("rr-theme", theme);
  };
  const theme = recall("rr-theme") || "system";
  setTheme(theme);
  let tileBox;
  const setLayout = (layout) => {
    tileBox.querySelectorAll(".rr-tiles").forEach((grid) => grid.classList.toggle("linear", layout === "list"));
    remember("rr-tiles", layout);
  };
  const layout = recall("rr-tiles") || "grid";
  const controls = el("div", "rr-controls");
  controls.append(
    switchOf("Tiles", [["grid", "Grid"], ["list", "List with explanations"]], layout, setLayout),
    switchOf("Theme", [["system", "System"], ["light", "Light"], ["dark", "Dark"]], theme, setTheme),
  );

  const head = el("div", "rr-head");
  head.append(el("span", "rr-n", "#"), el("span", null, `When (${zone})`), el("span", null, "Asked"), el("span", null, "Answer"));
  const list = el("div", "rr-steps");
  rows = trace.steps.map((step, i) => row(step, i));
  list.append(...rows);
  // Two sections: the test results (judged against what the run expected) and the run's statistics (facts).
  const all = tiles();
  const results = el("div", "rr-tiles"), stats = el("div", "rr-tiles");
  [...all.children].forEach((node) => (node.dataset.judged ? results : stats).append(node));
  tileBox = el("div", "rr-sections");
  const section = (title, note, grid) => {
    const part = el("section", "rr-section");
    const head = el("h2", "rr-section-title", title);
    part.append(head, el("p", "rr-section-note", note), cards(grid));
    return part;
  };
  tileBox.append(
    section("Test results", "Each judged against what the run said it expected: green when all as expected, pink to red as more failed.", results),
    section("Statistics", "Facts about the run; nothing to pass or fail.", stats),
  );
  setLayout(layout);
  root.append(controls, tileBox, bar, filterBar, head, list, overlay);
})();
