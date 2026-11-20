// RISE dynamic webhook, envisioned: the renderer. The data is in webhook.js.
(() => {
  const W = window.WEBHOOK;
  const host = document.getElementById("webhook");

  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  };

  const rule = (id) => el("span", `rule rule-${id[0]}`, id);

  // Numbers: one per step, in order. Variants carry their own id.
  const numbered = W.STEPS.map((s, i) => ({ ...s, n: s.id.startsWith("v") ? `V${s.id.slice(1)}` : String(i + 1) }));

  // ---- The sequence, drawn from the same exchanges: sender on the left, receiver on the right.
  const diagram = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Sequence"));
    const lines = [];
    numbered.forEach((s) => {
      if (!s.exchanges.length) {
        lines.push(`${s.n.padEnd(4)}(inside the library) ${s.title}`);
        return;
      }
      s.exchanges.forEach((x) => {
        lines.push(`${s.n.padEnd(4)}${x.from} --- ${x.line} --> ${x.to}`);
        lines.push(`${"".padEnd(4)}${x.to} --- ${x.res.line} --> ${x.from}`);
      });
    });
    sec.append(el("pre", "seq", lines.join("\n")));
    sec.append(el("p", "note", "Each numbered pair is one request, then its response. Read the pairs in order."));
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

  // ---- Each step: its pairs, headers and body, then the observations.
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
      if (!s.exchanges.length) {
        block.append(el("p", "note", s.note));
        sec.append(block);
        return;
      }
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

  // ---- What slice 1 leaves out.
  const outOfSlice = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Left out of slice 1"));
    const ul = el("ul", "points");
    W.OUT_OF_SLICE.forEach((t) => ul.append(el("li", null, t)));
    sec.append(ul);
    return sec;
  };

  host.append(diagram(), rules(), steps(), outOfSlice());
})();
