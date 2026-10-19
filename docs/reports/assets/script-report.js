// The line-by-line report (spec/PLAN-LINE-BY-LINE-REPORT.md): the script first, coloured, each line that ran marked
// with its steps; then one row per step, in the order the run took them: the statement, how it ended, the
// affordance chosen and the values passed, and one terse line of what it took. Everything else is one click
// deeper, or a toggle on top: the words the model was asked, the request and its guard, what was offered, where
// each value came from, the model's calls, every HEART exchange and, under each, the upstream calls the gateway
// made for it (tied by traceparent). Reads, all as YAML text in the page: the script, the run's capture (one
// document for the run, one per step; a one-document JSON trace reads too), and the gateway's record.
(() => {
  const text = (id) => (document.getElementById(id) || {}).textContent || "";
  const all = (id) => { const t = text(id).trim(); return t ? window.jsyaml.loadAll(t).filter((d) => d != null) : []; };
  const documents = all("run-trace");
  const steps = documents.length === 1 && documents[0].steps ? documents[0].steps : documents.slice(1);
  const run = (documents[0] && documents[0].run) || {};
  const script = text("script-text").replace(/\n$/, "").split("\n");
  const upstream = all("upstream-record");
  const byTrace = {};
  upstream.forEach((call) => { if (call.traceparent) (byTrace[call.traceparent] = byTrace[call.traceparent] || []).push(call); });

  const el = (tag, cls, content) => { const n = document.createElement(tag); if (cls) n.className = cls; if (content !== undefined) n.textContent = content; return n; };
  const kotlin = (t) => el("code", "kotlin", t);
  const fmt = (v) => (typeof v === "string" ? v : JSON.stringify(v));
  const short = (u) => String(u || "").replace(/^[a-z]+:\/\/[^/]+/, "") || "/";
  const plural = (n, w) => `${n} ${w}${n === 1 ? "" : "s"}`;
  const header = (headers, name) => { const k = Object.keys(headers || {}).find((h) => h.toLowerCase() === name); return k ? headers[k] : undefined; };
  const sentOf = (step) => (step.exchanges || []);
  const upOf = (exchange) => byTrace[header(exchange.request.headers, "traceparent")] || [];
  const upAll = (step) => sentOf(step).flatMap(upOf);
  const writes = (calls) => calls.filter((c) => c.request.method !== "GET").length;
  const lineOf = (n) => (n ? (script[n - 1] || "").trim() : "");

  // Theme: light unless the reader chose dark.
  const setTheme = (t) => { document.documentElement.dataset.theme = t; try { localStorage.setItem("sr-theme", t); } catch (e) { /* none kept */ }
    document.querySelectorAll(".theme button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.th === t))); };
  document.querySelectorAll(".theme button").forEach((b) => b.addEventListener("click", () => setTheme(b.dataset.th)));
  setTheme(document.documentElement.dataset.theme || "light");

  // What a step is, by what it said and the line it came from.
  const kindOf = (s) => {
    if (s.say.startsWith("return to")) return lineOf(s.line).startsWith("until(") ? "reread" : "end";
    if (s.say === "until") return "until";
    if (s.say === "check") return "check";
    if (s.say.startsWith("enter")) return "enter";
    return s.mode === "intent" ? "reason" : "select";
  };

  // The facts strip.
  const last = steps[steps.length - 1] || {};
  const secs = steps.length ? (new Date(last.at) - new Date(steps[0].at) + (last.ms || 0)) / 1000 : 0;
  const heartCalls = steps.reduce((n, s) => n + sentOf(s).length, 0);
  const upCalls = steps.flatMap(upAll);
  const facts = [
    `<span><b>${steps.length}</b> steps</span>`,
    `<span><b>${steps.filter((s) => s.mode === "intent").length}</b> reason steps</span>`,
    heartCalls ? `<span><b>${heartCalls}</b> HEART calls</span>` : "",
    upCalls.length ? `<span><b>${upCalls.length}</b> upstream calls, <b>${writes(upCalls)}</b> writes</span>` : "",
    `<span><b>${secs.toFixed(1)} s</b></span>`,
    `<span>${steps.some((s) => s.outcome === "refused" || s.outcome === "failed") ? "stopped" : "finished"}</span>`,
  ];
  const inputs = Object.entries(run.inputs || {}).map(([k, v]) => `${k} = ${v && v.value}`).join(", ");
  if (inputs) facts.push(`<span>inputs: ${inputs.replace(/</g, "&lt;")}</span>`);
  document.getElementById("facts").innerHTML = facts.filter(Boolean).join("");

  // The script, each line that ran marked with its steps.
  const byLine = {};
  steps.forEach((s, i) => { if (s.line) (byLine[s.line] = byLine[s.line] || []).push(i + 1); });
  const code = document.getElementById("code");
  script.forEach((t, i) => {
    const row = el("div", "ln" + (byLine[i + 1] ? " ran" : ""));
    row.append(el("span", "no", String(i + 1)));
    const marks = el("span", "marks");
    (byLine[i + 1] || []).forEach((n) => { const a = el("a", "", `#${n}`); a.href = `#s${n}`; marks.append(a); });
    row.append(marks, kotlin(t || " "));
    code.append(row);
  });

  // One exchange as lines, its bodies in the shared viewer; a HEART exchange with its upstream calls under it.
  const keep = /^(if-match|if-none-match|accept|accept-profile|prefer|content-type|location|etag|content-location|authorization|traceparent)$/i;
  const lines = (h) => Object.entries(h || {}).filter(([k]) => keep.test(k)).map(([k, v]) => `${k}: ${v}`).join("\n");
  const exchangeBox = (x, withUpstream) => {
    const box = el("div", "ex");
    box.dataset.kind = "heart";
    const w = x.request.method !== "GET";
    box.append(el("div", "line" + (w ? " w" : ""), `${x.request.method} ${short(x.request.url)}  →  ${x.response.status}  ·  ${x.ms} ms`));
    const h = [lines(x.request.headers), lines(x.response.headers) && `— answer —\n${lines(x.response.headers)}`].filter(Boolean).join("\n");
    if (h) box.append(el("div", "hdrs", h));
    if (x.request.body != null) box.append(window.JsonView.box({ a: x.request.body, labelA: "request body", summary: "Request body" }));
    if (x.response.body != null && x.response.body !== "") box.append(window.JsonView.box({ a: x.response.body, labelA: "answer body", summary: "Answer body" }));
    const ups = withUpstream ? upOf(x) : [];
    if (ups.length) {
      const d = drawer(`upstream calls for it (${ups.length}${writes(ups) ? `, ${writes(ups)} write` : ""})`, (into) => {
        const list = el("div", "up");
        ups.forEach((u) => list.append(exchangeBox(u, false)));
        into.append(list);
      }, "upstream");
      box.append(d);
    }
    return box;
  };
  // Whether a toggle on top is on; a drawer filled late opens what inside it a toggle asks for.
  const shown = (k) => { const box = document.querySelector(`[data-show="${k}"]`); return !!(box && box.checked); };
  const drawer = (title, fill, kind) => {
    const d = el("details", "d"); d.dataset.kind = kind || "";
    d.append(el("summary", "", title));
    d.addEventListener("toggle", () => {
      if (!d.open || d.dataset.f) return;
      d.dataset.f = 1;
      fill(d);
      d.querySelectorAll("details.d").forEach((inner) => { inner.open = shown(inner.dataset.kind); });
    });
    return d;
  };
  const sources = (from) => (from || []).map((f) => {
    if (typeof f === "string") return f;
    if (f.input) return `input ${f.input}`;
    if (f.step) return `step ${f.step} ${f.pointer}`;
    if (f.parts) return "parts: " + Object.entries(f.parts).map(([p, s]) => `${p} from ${sources([s])}`).join("; ");
    return JSON.stringify(f);
  }).join(", ");

  const root = document.getElementById("steps");
  steps.forEach((s, i) => {
    const n = i + 1, kind = kindOf(s), c = s.chose;
    const plumbing = kind === "end" || kind === "reread";
    const card = el("section", "st" + (plumbing || kind === "enter" ? " quiet" : "")); card.id = `s${n}`;
    card.dataset.kind = plumbing ? "plumbing" : "step";
    const num = el("div", "num"); num.append(el("b", "", `#${n}`), document.createTextNode(s.line ? `L${s.line}` : ""));
    const body = el("div", "body"); card.append(num, body);

    // Line 1: the statement, and how it ended.
    const top = el("div", "top");
    const stmt = el("div", "stmt");
    if (kind === "end") stmt.append(document.createTextNode(`end of the block at line ${s.line}: back to ${short(s.address)}`));
    else if (kind === "reread") stmt.append(document.createTextNode(`until: read ${short(s.address)} again`));
    else stmt.append(kotlin(lineOf(s.line) || s.say));
    const status = sentOf(s).length ? sentOf(s)[sentOf(s).length - 1].response.status : "";
    const changed = c && c.method !== "GET";
    const [mark, word, cls] = s.outcome === "new resource" ? [status || "", "Created", "w"]
      : s.outcome === "held" ? ["✓", "held", "ok"]
      : s.outcome === "failed" || s.outcome === "refused" ? ["✗", s.outcome, "bad"]
      : [status || "", changed ? "Done" : "Read", changed ? "w" : "ok"];
    const out = el("div", "out " + cls); out.append(el("b", "", String(mark)), document.createTextNode(` ${word}`));
    top.append(stmt, out); body.append(top);

    // Line 2: what was chosen and passed; a check's facts; what an until waited for.
    if (c) {
      const sel = el("div", "sel " + c.kind);
      if (s.mode === "intent") sel.append(el("span", "kind claude", "Claude chose"));
      sel.append(el("span", "kind", c.kind === "form" ? (c.method === "GET" ? "lookup" : "form") : "link"), el("span", "rel", c.relation));
      const sent = Object.entries(s.params || {}).filter(([k]) => k !== "relation");
      if (sent.length) {
        const v = el("span", "pv");
        sent.forEach(([k, x], j) => { v.append(el("span", "k", k), document.createTextNode(` = ${fmt(x)}`)); if (j < sent.length - 1) v.append(document.createTextNode("  ·  ")); });
        sel.append(v);
      }
      body.append(sel);
    } else if (kind === "check" && s.params && s.params.facts) {
      const f = el("div", "sel facts"); f.append(el("span", "kind", "facts"),
        el("span", "pv", s.params.facts.map((x) => `${x.member} ${x.test}: ${x.held} ${x.holds ? "✓" : "✗"}`).join("  ·  ")));
      body.append(f);
    } else if (kind === "until" && s.waited) {
      const f = el("div", "sel waited"); f.append(el("span", "kind", "waited"),
        el("span", "pv", `${s.waited.for || "the condition"} · ${plural(s.waited.reads, "read")}`));
      body.append(f);
    }

    // What a lenient guard went past, on the row itself: a changed set, or a relation the lock file doesn't pin.
    if (s.guard && (s.guard.warning || s.guard.unpinned)) {
      const w = el("div", "sel warn"); w.append(el("span", "kind", "guard"),
        el("span", "pv", s.guard.warning || `${s.chose ? s.chose.relation : "this"} is not in the lock file`));
      body.append(w);
    }

    // Line 3: what it took.
    const ups = upAll(s);
    const tally = [sentOf(s).length ? plural(sentOf(s).length, "HEART call") : (kind === "check" || kind === "until" ? "no request" : ""),
      ups.length ? plural(ups.length, "upstream call") + (writes(ups) ? ` (${writes(ups)} write)` : "") : "",
      s.model && s.model.length ? plural(s.model.length, "model call") : "",
      `${((s.ms || 0) / 1000).toFixed(1)} s`].filter(Boolean).join("  ·  ");
    const t = el("button", "tally", tally + "  ▸"); t.type = "button"; body.append(t);

    // Everything else, one click deeper.
    const deep = el("div", "deep"); deep.hidden = true; body.append(deep);
    let filled = false;
    const fill = () => {
      if (filled) return; filled = true;
      if (s.mode === "intent") { const p = el("div", "said"); p.append(el("span", "who", "Claude was asked"), document.createTextNode(`“${s.say}”`)); deep.append(p); }
      const kv = el("dl", "kv");
      const row = (k, v) => { if (v !== undefined && v !== null && v !== "") kv.append(el("dt", "", k), el("dd", "", fmt(v))); };
      if (c) {
        row("request", `${c.method} ${c.address}`);
        if (c.target !== c.address) row("as offered", c.target);
        row("picked by", c.picked);
        if (c["not sent"]) row("not sent", c["not sent"].join(", "));
      }
      if (s.guard) {
        row("rung", s.guard.rung);
        if (s.guard.set) row("set", `${s.guard.set.href}  ${s.guard.set.integrity || ""}`);
        row("form integrity", s.guard.form);
        if (s.guard.schema) row("schema", `${s.guard.schema.href}  ${s.guard.schema.integrity || ""}`);
        row("If-Match", s.guard["if-match"]);
      }
      Object.entries(s.values || {}).forEach(([k, v]) => row(`${k} from`, sources(v.from)));
      if (s.reason) {
        row("template", s.reason.template);
        Object.entries(s.reason.slots || {}).forEach(([k, v]) => row(`slot ${k}`, `${v.value} — ${sources(v.from)}`));
      }
      if (s.mode === "intent") row("judgement", s.judgement ? "yes: a value its words didn't give" : "no");
      Object.entries(s.keeps || {}).forEach(([p, by]) => row(`${p} read by`, by.map((x) => `#${x}`).join(", ")));
      if (s.refused) row("refused", `${s.refused.kind}, by ${s.refused.by}: ${s.refused.detail}`);
      if (kv.childElementCount) deep.append(kv);
      const more = el("div", "stack");
      if (s.offered) more.append(drawer(`offered here: ${plural(s.offered.forms.length, "form")}, ${plural(s.offered.links.length, "link")}`, (d) => {
        const o = el("div", "offer"); [...s.offered.forms, ...s.offered.links].forEach((r) => o.append(el("span", c && r === c.relation ? "chosen" : "", r))); d.append(o); }, "detail"));
      if (s.model && s.model.length) more.append(drawer(`model calls (${s.model.length})`, (d) => s.model.forEach((m) => {
        d.append(el("div", "left", m.model || ""), window.JsonView.box({ a: m.asked, labelA: "asked", summary: "What it was asked" }));
        let answered = m.answered; try { answered = JSON.parse(m.answered); } catch (e) { /* text */ }
        d.append(window.JsonView.box({ a: answered, labelA: "answered", summary: "What it answered" }));
      }), "model"));
      if (sentOf(s).length) more.append(drawer(`HEART exchanges (${sentOf(s).length})`, (d) => sentOf(s).forEach((x) => d.append(exchangeBox(x, true))), "heart"));
      const lastBody = sentOf(s).length ? sentOf(s)[sentOf(s).length - 1].response.body : null;
      const alt = lastBody && lastBody._links && lastBody._links.alternate;
      const altHref = alt && (Array.isArray(alt) ? alt[0] : alt).href;
      if (altHref) { const p = el("div", "left"); const a = el("a", "", altHref); a.href = altHref; p.append(document.createTextNode("on the upstream: "), a); more.append(p); }
      if (more.childElementCount) deep.append(more);
    };
    card.open = (on) => { if (on) fill(); deep.hidden = !on; t.textContent = tally + (on ? "  ▾" : "  ▸"); };
    t.addEventListener("click", () => card.open(deep.hidden));
    root.append(card);
  });

  // The toggles: open every step's details, or one kind of drawer everywhere; show block ends and re-reads.
  const on = shown;
  const apply = () => document.querySelectorAll(".st").forEach((c) => {
    c.hidden = c.dataset.kind === "plumbing" && !on("plumbing");
    const any = on("detail") || on("heart") || on("upstream") || on("model");
    c.open(any);
    c.querySelectorAll("details.d").forEach((d) => {
      d.open = on(d.dataset.kind) || (d.dataset.kind === "heart" && on("upstream"));
      if (d.open) d.querySelectorAll("details.d").forEach((inner) => { inner.open = on(inner.dataset.kind); });
    });
  });
  document.querySelectorAll("[data-show]").forEach((i) => i.addEventListener("change", apply));
  apply();
})();
