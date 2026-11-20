// RISE core, the RESTful minimum: the renderer. The data is in core.js.
(() => {
  const C = window.CORE;
  const host = document.getElementById("core");

  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  };

  const rule = (id) => el("span", `rule rule-${id[0]}`, id);

  // ---- The rules, by id.
  const rules = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Rules"));
    const ul = el("ul", "legend");
    C.RULES.forEach(([id, text]) => {
      const li = el("li");
      li.append(rule(id), el("span", null, ` ${text}`));
      ul.append(li);
    });
    sec.append(ul);
    return sec;
  };

  // ---- Each interaction: the exchange, the JSON folded, the observations with their rules.
  const steps = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Interactions"));
    const nav = el("nav", "chips");
    nav.setAttribute("aria-label", "Interactions");
    C.STEPS.forEach((s) => {
      const a = el("a", "chip", s.id.toUpperCase());
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
    bar.append(el("span", "sub", "Bodies:"), open, close);
    sec.append(nav, bar);

    C.STEPS.forEach((s) => {
      const block = el("article", "step");
      block.id = s.id;
      block.append(el("h3", null, `${s.id.toUpperCase()}. ${s.title}`));
      s.exchanges.forEach((x) => {
        const ex = el("div", "exchange");
        ex.append(el("pre", "line", [x.line, ...(x.headers || [])].join("\n")));
        if (x.body !== undefined) ex.append(window.JsonView.box({ a: x.body, summary: x.bodyLabel || "Request body" }));
        ex.append(el("pre", "line res", [x.res.line, ...(x.res.headers || [])].join("\n")));
        ex.append(window.JsonView.box({ a: x.res.body, summary: "Response body" }));
        block.append(ex);
      });
      const obs = el("ul", "obs");
      s.obs.forEach((o) => {
        const li = el("li");
        li.append(el("span", null, o.t), " ");
        o.r.forEach((id) => li.append(rule(id), " "));
        obs.append(li);
      });
      block.append(obs);
      sec.append(block);
    });
    return sec;
  };

  // ---- The checks a client suite would run against this flow.
  const checks = () => {
    const sec = el("section", "block");
    sec.append(el("h2", null, "Checks"));
    const ul = el("ul", "points");
    C.CHECKS.forEach((c) => ul.append(el("li", null, c)));
    sec.append(ul);
    return sec;
  };

  host.append(rules(), steps(), checks());
})();
