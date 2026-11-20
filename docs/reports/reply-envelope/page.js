// Reply envelope, end to end: the renderer. The data is in reply.js.
(() => {
  const R = window.REPLY;
  const host = document.getElementById("reply");
  const LABEL = { codified: "Codified", proposed: "Proposed", defacto: "De facto", thesis: "Thesis", ours: "Ours" };

  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  };

  // One status tag, e.g. "CODIFIED · RFC 9421".
  const tag = ({ k, note }) => el("span", `tag tag-${k}`, note ? `${LABEL[k]} · ${note}` : LABEL[k]);

  const byKey = new Map(R.STANDARDS.map((s) => [s.key, s]));
  const standard = (key) => {
    const s = byKey.get(key);
    if (!s) throw new Error(`no standard named ${key}`);
    return s;
  };

  const section = (title, lede) => {
    const sec = el("section", "block");
    sec.append(el("h2", null, title));
    if (lede) sec.append(el("p", "lede", lede));
    return sec;
  };

  const table = (head, rows) => {
    const wrap = el("div", "tbl-wrap");
    const t = el("table");
    const tr = el("tr");
    head.forEach((h) => tr.append(el("th", null, h)));
    const thead = el("thead");
    thead.append(tr);
    t.append(thead);
    const body = el("tbody");
    rows.forEach((cells) => {
      const row = el("tr");
      cells.forEach((c, i) => {
        const td = el("td");
        td.dataset.label = head[i];
        if (c instanceof Node) td.append(c);
        else td.textContent = c;
        row.append(td);
      });
      body.append(row);
    });
    t.append(body);
    wrap.append(t);
    return wrap;
  };

  // ---- What changed from the first idea.
  const changed = () => {
    const sec = section("What changed from the first idea");
    const ul = el("ul", "points");
    R.CHANGED.forEach((c) => ul.append(el("li", null, c)));
    sec.append(ul);
    return sec;
  };

  // ---- Every name, its status, its source.
  const standards = () => {
    const sec = section("Standards behind every name",
      "Each name carries its status. Read the status before reading the name: CODIFIED means a published standard, PROPOSED a draft, DE FACTO a convention without a standards-body document, THESIS a principle, OURS our own.");
    const legend = el("ul", "legend");
    R.LEGEND.forEach((l) => {
      const li = el("li");
      li.append(el("span", `tag tag-${l.k}`, l.t), el("span", null, ` ${l.d}`));
      legend.append(li);
    });
    sec.append(legend);
    const rows = R.STANDARDS.map((s) => {
      const name = el("code", null, s.key);
      const status = el("span");
      s.tags.forEach((t) => status.append(tag(t), " "));
      return [name, s.does, status, s.src];
    });
    sec.append(table(["Name", "What it does", "Status", "Source"], rows));
    return sec;
  };

  // ---- The steps: each exchange with its lines, its JSON folded, the standards it uses.
  const steps = () => {
    const sec = section("End to end",
      "Twelve steps: the happy path, a lost answer, a second answer, an expired reply, a forged delivery and an oversize template. Bodies are folded; open them to read.");
    const nav = el("nav", "chips");
    nav.setAttribute("aria-label", "Steps");
    R.STEPS.forEach((s, i) => {
      const a = el("a", "chip", String(i + 1));
      a.href = `#${s.id}`;
      nav.append(a);
    });
    const bar = el("div", "bar");
    const open = el("button", "btn", "Open every body");
    const close = el("button", "btn", "Close every body");
    open.type = close.type = "button";
    const setAll = (state) => document.querySelectorAll("details.jv").forEach((d) => { d.open = state; });
    open.addEventListener("click", () => setAll(true));
    close.addEventListener("click", () => setAll(false));
    bar.append(el("span", "sub", "Steps:"), open, close);
    sec.append(nav, bar);

    R.STEPS.forEach((s, i) => {
      const block = el("article", "step");
      block.id = s.id;
      block.append(el("h3", null, `${i + 1}. ${s.title}`), el("p", "lede", s.lede));
      s.exchanges.forEach((x) => {
        const ex = el("div", "exchange");
        ex.append(el("pre", "line", [x.line, ...(x.headers || [])].join("\n")));
        if (typeof x.body === "string") ex.append(el("p", "note", `Body: ${x.body}.`));
        else if (x.body !== undefined) ex.append(window.JsonView.box({ a: x.body, summary: x.bodyLabel || "Request body" }));
        if (x.res) {
          ex.append(el("pre", "line res", [x.res.line, ...(x.res.headers || [])].join("\n")));
          if (x.res.body !== undefined) ex.append(window.JsonView.box({ a: x.res.body, summary: "Response body" }));
        }
        block.append(ex);
      });
      if (s.exchanges.length === 0) block.append(el("p", "note", "No exchange."));
      if (s.std.length) {
        const here = el("p", "here");
        here.append(el("span", "here-label", "Standards here: "));
        s.std.forEach((key) => {
          const chip = el("span", "chip-std");
          chip.append(el("code", null, key), " ", tag(standard(key).tags[0]));
          here.append(chip);
        });
        block.append(here);
      }
      if (s.note) block.append(el("p", "note", s.note));
      sec.append(block);
    });
    return sec;
  };

  // ---- Loose coupling, against AsyncAPI.
  const coupling = () => {
    const sec = section("Loose coupling, and AsyncAPI");
    sec.append(el("p", null, "AsyncAPI describes channels and message payloads in one document that both sides share. That is its strength for documenting a system, and it is the coupling: rename a channel or change a payload, and the other side breaks until its copy of the document changes."));
    sec.append(el("p", null, "This reply design names no channel and no caller payload. The reply address is a link made per request. The echo is opaque. The only shared names are standards' names, which change on the standards' own schedule, not ours."));
    sec.append(el("p", null, "AsyncAPI 3.0 can also describe a reply channel. It fixes that channel in the document, at design time. Here the caller supplies the address per request. An AsyncAPI document can still describe the broker and the topics around this design; the reply does not need that document to work."));
    sec.append(table(["What", "Caller knows", "Responder knows", "Shared as a document"],
      R.COUPLING.map((c) => [c.what, c.caller, c.responder, c.shared])));
    return sec;
  };

  // ---- Fielding's constraints, one row each.
  const fielding = () => {
    const sec = section("Fielding's constraints",
      "Fielding's constraints come from a 2000 thesis (THESIS). They are principles, not a standard. The steps show where each one holds.");
    sec.append(table(["Constraint", "How this design meets it", "Steps"],
      R.FIELDING.map((f) => [f.c, f.how, f.steps])));
    return sec;
  };

  // ---- Bounds: the essentials.
  const bounds = () => {
    const sec = section("Bounds");
    const ul = el("ul", "points");
    R.BOUNDS.forEach((b) => {
      const li = el("li");
      li.append(el("span", null, b.text + " "));
      b.tags.forEach((t) => li.append(tag(t), " "));
      ul.append(li);
    });
    sec.append(ul);
    return sec;
  };

  // ---- Considered, not used.
  const rejected = () => {
    const sec = section("Considered and not used");
    sec.append(table(["Option", "Status", "Why not"],
      R.REJECTED.map((r) => {
        const status = el("span");
        r.tags.forEach((t) => status.append(tag(t), " "));
        return [r.name, status, r.why];
      })));
    return sec;
  };

  // ---- Open decisions.
  const open = () => {
    const sec = section("Open decisions", "Each has a recommendation. None is decided here.");
    const ol = el("ol", "points");
    R.OPEN.forEach((o) => ol.append(el("li", null, o)));
    sec.append(ol);
    return sec;
  };

  host.append(changed(), standards(), steps(), coupling(), fielding(), bounds(), rejected(), open());
})();
