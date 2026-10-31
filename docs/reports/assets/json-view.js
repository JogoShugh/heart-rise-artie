// One viewer for every JSON example in the reports. A <details class="jv"> names its documents by the ids of
// <script type="application/json"> blocks: data-a alone shows one document; data-a and data-b show what b
// changes of a, as a line diff with a few lines of context. Nothing renders until the reader opens it; the
// unchanged lines and the whole documents appear only on a click.
(() => {
  const CONTEXT = 3;   // unchanged lines kept around each change
  const PREVIEW = 14;  // lines of a lone document shown before "show all"
  const CHANGE = 24;   // a run of changed lines longer than this shows its first FIRST lines, the rest on a click
  const FIRST = 16;
  const cache = new Map();

  const linesOf = (value) => (typeof value === "string" ? value : JSON.stringify(value, null, 2)).split("\n");
  const doc = (id) => {
    if (!cache.has(id)) cache.set(id, linesOf(JSON.parse(document.getElementById(id).textContent)));
    return cache.get(id);
  };

  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text;
    return node;
  };

  // Lines of a and b, each marked " " (both), "-" (a only) or "+" (b only): a longest common subsequence
  // after trimming the common head and tail, which is most of a JSON answer.
  const diff = (a, b) => {
    let head = 0;
    while (head < a.length && head < b.length && a[head] === b[head]) head++;
    let tail = 0;
    while (tail < a.length - head && tail < b.length - head && a[a.length - 1 - tail] === b[b.length - 1 - tail]) tail++;
    const x = a.slice(head, a.length - tail), y = b.slice(head, b.length - tail);
    const n = x.length, m = y.length, w = m + 1;
    const table = new Uint32Array((n + 1) * w);
    for (let i = n - 1; i >= 0; i--)
      for (let j = m - 1; j >= 0; j--)
        table[i * w + j] = x[i] === y[j] ? table[(i + 1) * w + j + 1] + 1 : Math.max(table[(i + 1) * w + j], table[i * w + j + 1]);
    const out = a.slice(0, head).map((line) => [" ", line]);
    let i = 0, j = 0;
    while (i < n && j < m) {
      if (x[i] === y[j]) { out.push([" ", x[i]]); i++; j++; }
      else if (table[(i + 1) * w + j] >= table[i * w + j + 1]) out.push(["-", x[i++]]);
      else out.push(["+", y[j++]]);
    }
    while (i < n) out.push(["-", x[i++]]);
    while (j < m) out.push(["+", y[j++]]);
    return out.concat(a.slice(a.length - tail).map((line) => [" ", line]));
  };

  const row = ([mark, line]) => el("span", { " ": "ln", "-": "ln del", "+": "ln add" }[mark], `${mark} ${line}`);

  // The diff with each long run of unchanged lines folded into a button that unfolds it.
  const diffView = (a, b) => {
    const lines = diff(a, b), pre = el("pre", "jv-doc");
    if (!lines.some(([mark]) => mark !== " ")) pre.append(el("span", "ln note", "  (no difference)"));
    let k = 0;
    while (k < lines.length) {
      if (lines[k][0] !== " ") {
        let end = k;
        while (end < lines.length && lines[end][0] !== " ") end++;
        const run = lines.slice(k, end);
        if (run.length <= CHANGE) run.forEach((l) => pre.append(row(l)));
        else {
          run.slice(0, FIRST).forEach((l) => pre.append(row(l)));
          const rest = run.slice(FIRST);
          const more = el("button", "jv-gap", `⋯ ${rest.length} more changed lines`);
          more.type = "button";
          more.addEventListener("click", () => more.replaceWith(...rest.map(row)));
          pre.append(more);
        }
        k = end;
        continue;
      }
      let end = k;
      while (end < lines.length && lines[end][0] === " ") end++;
      const first = k === 0, last = end === lines.length;
      const keepHead = first ? 0 : CONTEXT, keepTail = last ? 0 : CONTEXT;
      if (end - k <= keepHead + keepTail + 1) { lines.slice(k, end).forEach((l) => pre.append(row(l))); }
      else {
        lines.slice(k, k + keepHead).forEach((l) => pre.append(row(l)));
        const hidden = lines.slice(k + keepHead, end - keepTail);
        const gap = el("button", "jv-gap", `⋯ ${hidden.length} unchanged lines`);
        gap.type = "button";
        gap.addEventListener("click", () => gap.replaceWith(...hidden.map(row)));
        pre.append(gap);
        lines.slice(end - keepTail, end).forEach((l) => pre.append(row(l)));
      }
      k = end;
    }
    return pre;
  };

  // A lone document: its first lines, the rest on a click.
  const docView = (lines) => {
    const pre = el("pre", "jv-doc");
    lines.slice(0, PREVIEW).forEach((line) => pre.append(el("span", "ln", line)));
    if (lines.length > PREVIEW) {
      const more = el("button", "jv-gap", `⋯ ${lines.length - PREVIEW} more lines`);
      more.type = "button";
      more.addEventListener("click", () => more.replaceWith(...lines.slice(PREVIEW).map((l) => el("span", "ln", l))));
      pre.append(more);
    }
    return pre;
  };

  // "Show all": a whole document, on demand.
  const fullButton = (label, lines, host) => {
    const button = el("button", "jv-full", `${label} in full (${lines.length} lines)`);
    button.type = "button";
    button.addEventListener("click", () => {
      const pre = el("pre", "jv-doc");
      lines.forEach((line) => pre.append(el("span", "ln", line)));
      button.disabled = true;
      host.append(el("p", "jv-label", `${label}, in full`), pre);
    });
    return button;
  };

  const render = (box) => {
    const body = el("div", "jv-body"), tools = el("div", "jv-tools");
    const a = doc(box.dataset.a), labelA = box.dataset.labelA || "document";
    if (box.dataset.b) {
      const b = doc(box.dataset.b), labelB = box.dataset.labelB || "after";
      body.append(el("p", "jv-label", `− ${labelA}   + ${labelB}`), diffView(a, b));
      tools.append(fullButton(labelA, a, body), fullButton(labelB, b, body));
    } else {
      body.append(el("p", "jv-label", labelA), docView(a));
    }
    // Before the box's tail (a nested box, such as why the rest are absent), if it has one.
    const tail = box.querySelector(":scope > .jv-tail");
    box.insertBefore(body, tail);
    if (tools.childElementCount) box.insertBefore(tools, tail);
  };

  // For other scripts (run-report.js): a box for documents already in hand, rendered when opened like any other.
  let made = 0;
  window.JsonView = {
    box({ a, b, labelA, labelB, summary, lead }) {
      const box = el("details", "jv");
      const id = (value) => { const key = `jvm${made++}`; cache.set(key, linesOf(value)); return key; };
      box.dataset.a = id(a);
      if (b !== undefined) box.dataset.b = id(b);
      if (labelA) box.dataset.labelA = labelA;
      if (labelB) box.dataset.labelB = labelB;
      const head = el("summary");
      head.innerHTML = summary;
      box.append(head);
      if (lead) box.append(lead);
      return box;
    },
  };

  // toggle doesn't bubble, but a capturing listener on the document sees every one.
  document.addEventListener("toggle", (event) => {
    const box = event.target;
    if (box.classList && box.classList.contains("jv") && box.open && !box.dataset.rendered) {
      box.dataset.rendered = "1";
      render(box);
    }
  }, true);
})();
