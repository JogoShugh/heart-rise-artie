// RISE minimal, end to end: the renderer. The data is in minimal.js.
(() => {
  const W = window.MINIMAL;
  const host = document.getElementById("minimal");

  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  };

  const rule = (id) => el("span", `rule rule-${id[0]}`, id);

  // Numbers: one per step, in order.
  const numbered = W.STEPS.map((s, i) => ({ ...s, n: String(i + 1) }));

  // ---- The sequence, drawn as lanes: the caller on the left, the target on the right. Each message is a row.
  // A request's arrow runs from its sender's lane to its receiver's; the response runs back, dashed.
  const lane = (s, text, from, to, response) => {
    const row = el("div", `lane-row${response ? " res" : ""}`);
    const num = el("a", "lane-n", s.n);
    num.href = `#${s.id}`;
    const track = el("div", "lane-track");
    if (from === to) {
      track.append(el("span", `lane-label self-${from === "Caller" ? "left" : "right"}`, text));
    } else {
      track.append(el("span", "lane-label", text));
      const arrow = el("span", `arrow dir-${from === "Caller" ? "right" : "left"}`);
      track.append(arrow);
    }
    row.append(num, track);
    return row;
  };

  const diagram = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Sequence"));
    const grid = el("div", "lanes");
    const head = el("div", "lane-row lane-head");
    const names = el("div", "lane-names");
    names.append(el("span", null, "Caller"), el("span", null, "Target"));
    head.append(el("span", "lane-n"), names);
    grid.append(head);
    numbered.forEach((s) => {
      s.exchanges.forEach((x) => {
        grid.append(lane(s, x.line, x.from, x.to, false));
        grid.append(lane(s, x.res.line, x.to, x.from, true));
      });
    });
    sec.append(grid);
    sec.append(el("p", "note", "Solid arrow: request. Dashed arrow: response. Each number links to its step below."));
    return sec;
  };

  // ---- The rules, by id.
  const rules = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Rules"));
    const ul = el("ul", "legend");
    W.RULES.forEach(([id, text]) => {
      const li = el("li");
      li.append(rule(id), el("span", null, ` ${text}`));
      ul.append(li);
    });
    sec.append(ul);
    return sec;
  };

  // ---- Each step: its note, its pairs, headers and body, then the observations.
  const steps = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Messages, in order"));
    const bar = el("div", "bar");
    const open = el("button", "btn", "Open every body");
    const close = el("button", "btn", "Close every body");
    open.type = close.type = "button";
    const setAll = (state) => document.querySelectorAll("details.jv").forEach((d) => { d.open = state; });
    open.addEventListener("click", () => setAll(true));
    close.addEventListener("click", () => setAll(false));
    bar.append(el("span", "sub", "Bodies:"), open, close);
    sec.append(bar);

    numbered.forEach((s) => {
      const block = el("article", "step");
      block.id = s.id;
      block.append(el("h3", null, `${s.n}. ${s.title}`));
      if (s.note) block.append(el("p", "note", s.note));
      s.exchanges.forEach((x) => {
        const ex = el("div", "exchange");
        ex.append(el("p", "dir", `Request: ${x.from} to ${x.to}`));
        ex.append(el("pre", "line", [x.line, ...(x.headers || [])].join("\n")));
        if (x.body !== undefined) ex.append(window.JsonView.box({ a: x.body, summary: x.bodyLabel || "Request body" }));
        ex.append(el("p", "dir", `Response: ${x.to} to ${x.from}`));
        ex.append(el("pre", "line res", [x.res.line, ...(x.res.headers || [])].join("\n")));
        ex.append(window.JsonView.box({ a: x.res.body, summary: "Response body" }));
        block.append(ex);
      });
      if (s.obs.length) {
        const obs = el("ul", "obs");
        s.obs.forEach((o) => {
          const li = el("li");
          li.append(el("span", null, o.t), " ");
          o.r.forEach((id) => li.append(rule(id), " "));
          obs.append(li);
        });
        block.append(obs);
      }
      sec.append(block);
    });
    return sec;
  };

  // ---- What is still open, in the order the messages raise it.
  const decisions = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Open decisions"));
    const ul = el("ul", "points");
    W.OPEN.forEach((t) => ul.append(el("li", null, t)));
    sec.append(ul);
    return sec;
  };

  // ---- What slice 1 leaves out.
  const outOfSlice = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Left out of slice 1"));
    const ul = el("ul", "points");
    W.OUT_OF_SLICE.forEach((t) => ul.append(el("li", null, t)));
    sec.append(ul);
    return sec;
  };

  // ---- The long-form review: our critique, quoting the post, in paragraphs.
  const review = () => {
    const sec = el("section", "block review");
    sec.append(el("h2", null, W.REVIEW.heading));
    sec.append(el("p", "note", W.REVIEW.lede));
    W.REVIEW.paras.forEach((t) => sec.append(el("p", "long", t)));
    return sec;
  };

  host.append(diagram(), rules(), steps(), decisions(), review(), outOfSlice());
})();
