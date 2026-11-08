// Draws window.RISE (flows.js): the overview, a chip per flow, and the chosen flow as a lane diagram above its
// messages, each with its lines and its body in the shared JSON viewer (json-view.js).
(() => {
  const { LANES, FLOWS, OVERVIEW, STATES, RELATIONS, PIECES, PLAIN } = window.RISE;
  const byId = Object.fromEntries(FLOWS.map((f) => [f.id, f]));
  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text;
    return node;
  };
  const lines = (value) => (typeof value === "string" ? value : JSON.stringify(value, null, 2)).split("\n").length;
  const isAnswer = (line) => /^\d{3} /.test(line);
  const tone = (line) => {
    const status = Number(line.slice(0, 3));
    return status >= 400 ? "bad" : status >= 300 ? "safe" : "good";
  };
  const amqpStatus = (step) => step.body && step.body.properties && step.body.properties.headers && step.body.properties.headers["heart-status"];
  const lane = (flow, key) => ({ ...LANES[key], ...((flow.names || {})[key] || {}) });

  // Each message's number, and each keyed body's number, so a diff can name the message it compares with.
  const numbered = (flow) => {
    let n = 0;
    const keys = {};
    const steps = flow.steps.map((step) => {
      if (!step.from) return { ...step };
      n += 1;
      if (step.key) keys[step.key] = { n, body: step.body };
      return { ...step, n };
    });
    return { steps, keys, count: n };
  };

  // ---- The overview table, always on top.
  const overview = () => {
    const wrap = el("div", "scroll");
    const table = el("table", "overview");
    table.innerHTML = "<thead><tr><th>Flow</th><th>Who hands over the address</th><th>Who keeps it</th><th>How it travels</th><th>Builds on</th></tr></thead>";
    const body = el("tbody");
    let group = "";
    OVERVIEW.forEach(([id, who, keeps, how, builds]) => {
      const flow = byId[id];
      if (flow.group !== group) {
        group = flow.group;
        const row = el("tr", "grp");
        const cell = el("th", null, group);
        cell.colSpan = 5;
        row.append(cell);
        body.append(row);
      }
      const row = el("tr");
      const first = el("td");
      const a = el("a", null, flow.short);
      a.href = `#${id}`;
      first.append(a, el("span", "count", ` ${numbered(flow).count} messages`));
      row.append(first);
      [who, keeps, how, builds].forEach((text) => row.append(el("td", text ? null : "dash", text || "—")));
      body.append(row);
    });
    table.append(body);
    wrap.append(table);
    return wrap;
  };

  // ---- Each flow in plain English, a row each, grouped as the flows are.
  const plain = () => {
    const section = el("section", "plain");
    section.append(el("h2", null, "In plain English"),
      el("p", "lede", "Every variation in a few short sentences, no jargon: a library, a book, and people who want to know when something happens."));
    let group = "", rows;
    FLOWS.forEach((flow) => {
      if (flow.alias || !PLAIN[flow.id]) return;
      if (flow.group !== group) {
        group = flow.group;
        section.append(el("h4", null, group));
        rows = el("div", "rows");
        const head = el("div", "row row-head");
        ["Variation", "Plain English"].forEach((h) => head.append(el("span", null, h)));
        rows.append(head);
        section.append(rows);
      }
      const [headline, text] = PLAIN[flow.id];
      const row = el("div", "row");
      const name = el("a", "row-flow");
      name.href = `#${flow.id}`;
      name.append(el("span", "card-flow", flow.short), el("b", null, headline));
      row.append(name, el("p", "plain-text", text));
      rows.append(row);
    });
    return section;
  };

  // ---- The chips: one per flow, grouped, and the shared pieces.
  const chips = () => {
    const bar = el("nav", "chips");
    bar.setAttribute("aria-label", "Flows");
    let group = "", row;
    FLOWS.forEach((flow) => {
      if (flow.group !== group) {
        group = flow.group;
        row = el("div", "chip-group");
        row.append(el("span", "chip-label", group));
        bar.append(row);
      }
      const a = el("a", "chip", flow.short);
      a.href = `#${flow.alias || flow.id}`;
      a.dataset.for = flow.alias || flow.id;
      row.append(a);
    });
    const last = el("div", "chip-group");
    last.append(el("span", "chip-label", "Shared"));
    const pieces = el("a", "chip", "States, relations and documents");
    pieces.href = "#pieces";
    pieces.dataset.for = "pieces";
    last.append(pieces);
    bar.append(last);
    return bar;
  };

  // ---- The lane diagram.
  const diagram = (flow, steps) => {
    const n = flow.lanes.length;
    const at = (key) => flow.lanes.indexOf(key);
    const center = (k) => ((k + 0.5) / n) * 100;
    const scroll = el("div", "seq-scroll");
    const seq = el("div", "seq");
    seq.style.setProperty("--n", n);
    seq.style.minWidth = `${n * 150}px`;
    const head = el("div", "seq-head");
    flow.lanes.forEach((key) => {
      const info = lane(flow, key);
      const cell = el("div", "lane-h");
      cell.append(el("b", null, info.name), el("small", null, info.host));
      head.append(cell);
    });
    const body = el("div", "seq-body");
    flow.lanes.forEach((_, k) => {
      const life = el("div", "life");
      life.style.left = `${center(k)}%`;
      body.append(life);
    });
    steps.forEach((step, i) => {
      if (step.section) {
        body.append(el("div", "seq-sec", step.section));
        return;
      }
      if (step.at) {
        const row = el("div", "seq-note-row");
        const note = el("button", "seq-note", step.note);
        note.type = "button";
        note.title = step.note;
        note.style.gridColumn = `${at(step.at) + 1}`;
        note.addEventListener("click", () => jump(flow, i));
        row.append(note);
        body.append(row);
        return;
      }
      const from = at(step.from), to = at(step.to);
      const left = Math.min(from, to), right = Math.max(from, to);
      const row = el("button", "seq-msg");
      row.type = "button";
      const answer = isAnswer(step.line);
      const status = amqpStatus(step);
      const arrow = el("div", `arrow ${to > from ? "right" : "left"}${answer ? " answer" : ""}${step.kind ? ` ${step.kind}` : ""}`);
      arrow.style.left = `${center(left)}%`;
      arrow.style.width = `${center(right) - center(left)}%`;
      const label = el("span", `lbl${answer ? ` ${tone(step.line)}` : status ? ` ${tone(String(status) + " ")}` : ""}`);
      label.append(el("span", "n", String(step.n)), document.createTextNode(step.line));
      label.title = step.line;
      arrow.append(label);
      row.append(arrow);
      row.setAttribute("aria-label", `Message ${step.n}: ${lane(flow, step.from).name} to ${lane(flow, step.to).name}, ${step.line}`);
      row.addEventListener("click", () => jump(flow, i));
      body.append(row);
    });
    seq.append(head, body);
    scroll.append(seq);
    return scroll;
  };

  const jump = (flow, i) => {
    const target = document.getElementById(`m-${flow.id}-${i}`);
    if (!target) return;
    target.scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
    target.classList.remove("flash");
    void target.offsetWidth;
    target.classList.add("flash");
  };

  // ---- The messages, in order, each with its lines, its note and its body.
  const message = (flow, step, i, keys) => {
    const item = el("li", "msg");
    item.id = `m-${flow.id}-${i}`;
    const head = el("div", "msg-head");
    head.append(el("span", "num", String(step.n)));
    head.append(el("span", "who", `${lane(flow, step.from).name} → ${lane(flow, step.to).name}`));
    if (step.kind === "amqp") head.append(el("span", "tag safe", "AMQP frame"));
    if (step.kind === "call") head.append(el("span", "tag warn", "model call"));
    item.append(head);
    const pre = el("pre", "lines");
    const first = el("b", isAnswer(step.line) ? `st ${tone(step.line)}` : null, step.line);
    pre.append(first);
    Object.entries(step.h || {}).forEach(([name, value]) => pre.append(document.createTextNode(`\n${name}: ${value}`)));
    item.append(pre);
    if (step.note) item.append(el("p", "note", step.note));
    if (step.body !== undefined) {
      const what = step.kind === "amqp" ? "Frame" : step.kind === "call" ? "Call" : isAnswer(step.line) ? "Answer body" : "Request body";
      const plain = typeof step.body === "string";
      let box;
      if (step.diff && keys[step.diff]) {
        const was = keys[step.diff];
        box = window.JsonView.box({
          a: was.body, b: step.body, labelA: `#${was.n}`, labelB: `#${step.n}`,
          summary: `${what}, as a change from #${was.n} <span class="sub">· ${lines(step.body)} lines</span>`,
        });
      } else {
        box = window.JsonView.box({
          a: step.body, labelA: plain ? "as sent" : what.toLowerCase(),
          summary: `${what}${plain ? ", as sent" : ""} <span class="sub">· ${lines(step.body)} lines</span>`,
        });
      }
      item.append(box);
    }
    return item;
  };

  const flowView = (flow) => {
    const { steps, keys, count } = numbered(flow);
    const view = el("section", "flow");
    view.append(el("h2", null, flow.title), el("p", "lede", flow.lede));
    const points = el("ul", "points");
    flow.points.forEach((p) => points.append(el("li", null, p)));
    view.append(points);
    if (flow.settle) {
      const settle = el("p", "settle");
      settle.append(el("b", null, "To settle: "), document.createTextNode(flow.settle));
      view.append(settle);
    }
    view.append(diagram(flow, steps));
    const bar = el("div", "bar");
    bar.append(el("span", "sub", `${count} messages. Select one in the diagram to go to it.`));
    const open = el("button", "btn", "Open every body");
    const close = el("button", "btn", "Close every body");
    [open, close].forEach((b) => { b.type = "button"; });
    open.addEventListener("click", () => view.querySelectorAll("details.jv").forEach((d) => { d.open = true; }));
    close.addEventListener("click", () => view.querySelectorAll("details.jv").forEach((d) => { d.open = false; }));
    bar.append(open, close);
    view.append(bar);
    const list = el("ol", "msgs");
    steps.forEach((step, i) => {
      if (step.section) {
        const sec = el("li", "msg-sec");
        sec.id = `m-${flow.id}-${i}`;
        sec.append(el("h3", null, step.section));
        list.append(sec);
      } else if (step.at) {
        const note = el("li", "msg-note");
        note.id = `m-${flow.id}-${i}`;
        note.append(el("span", "who", lane(flow, step.at).name), el("p", null, step.note));
        list.append(note);
      } else {
        list.append(message(flow, step, i, keys));
      }
    });
    view.append(list);
    return view;
  };

  // ---- The pieces every flow shares.
  const table = (head, rows, mono = [0]) => {
    const wrap = el("div", "scroll");
    const t = el("table");
    const thead = el("thead"), tr = el("tr");
    head.forEach((h) => tr.append(el("th", null, h)));
    thead.append(tr);
    const tbody = el("tbody");
    rows.forEach((row) => {
      const r = el("tr");
      row.forEach((cell, i) => r.append(el("td", mono.includes(i) ? "mono" : null, cell)));
      tbody.append(r);
    });
    t.append(thead, tbody);
    wrap.append(t);
    return wrap;
  };

  const piecesView = () => {
    const view = el("section", "flow");
    view.append(el("h2", null, "States, relations and documents"), el("p", "lede", "What every flow above is made of: the resources RISE brings into being, with their states and what each state offers; the relations; and the shared documents."));
    view.append(el("h3", null, "Resources and their states"), table(["Resource", "Served by", "States", "Offers, by state"], STATES, []));
    view.append(el("h3", null, "Relations RISE adds"), table(["Relation", "Is a", "Where"], RELATIONS));
    view.append(el("h3", null, "Shared documents"));
    const stack = el("div", "stack");
    PIECES.forEach((piece) => {
      const lead = el("p", "piece-lead", piece.text);
      const box = window.JsonView.box({ a: piece.doc, labelA: piece.title.toLowerCase(), summary: `${piece.title} <span class="sub">· ${lines(piece.doc)} lines</span>`, lead });
      stack.append(box);
    });
    view.append(stack);
    return view;
  };

  // ---- Routing by bare #token.
  const host = document.getElementById("rise");
  host.append(plain(), el("h2", "over-h", "Every variation at a glance"), overview());
  const nav = chips();
  host.append(nav);
  const slot = el("div", "slot");
  host.append(slot);

  const show = (id) => {
    const target = id === "pieces" ? "pieces" : byId[id] && !byId[id].alias ? id : byId[id] && byId[id].alias ? byId[id].alias : "webhook";
    slot.replaceChildren(target === "pieces" ? piecesView() : flowView(byId[target]));
    nav.querySelectorAll(".chip").forEach((c) => {
      if (c.dataset.for === target) c.setAttribute("aria-current", "page");
      else c.removeAttribute("aria-current");
    });
    slot.querySelectorAll("details.jv").forEach((d) => { d.open = true; });
    return target;
  };
  const route = (scroll) => {
    show(location.hash.slice(1));
    if (scroll) slot.scrollIntoView({ block: "start" });
  };
  window.addEventListener("hashchange", () => route(true));
  route(false);
})();
