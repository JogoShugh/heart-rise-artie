// The derivation report: the run's script beside the one artie generate wrote from its capture, row by row (each
// row begins where a step of the run begins in both), with every derivation of NAME.derivation.yaml marked where
// it stands on both sides, numbered, and its note one click away. Reads the three texts the page embeds.
(() => {
  const text = (id) => document.getElementById(id).textContent.replace(/\n$/, "");
  const source = text("source-text").split("\n");
  const generated = text("generated-text").split("\n");
  const record = jsyaml.load(text("derivation")) || {};
  const derivations = record.derivations || [];
  const byN = new Map(derivations.map((d) => [d.n, d]));
  const KINDS = { input: "input", words: "words, as written", paraphrase: "paraphrase", answer: "read from an answer",
    template: "names put back", model: "the reasoner's choice", relation: "the relation chosen", kept: "kept",
    until: "copied", check: "copied", constant: "constant" };
  const colour = (kind) => `var(--k-${kind === "until" || kind === "check" ? "copied" : kind})`;
  const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

  // Rows: where each step begins in both, kept only while both go forward, so every line shows once, in order.
  const cuts = [[1, 1]];
  (record.rows || []).filter((r) => r.source != null).sort((a, b) => a.generated - b.generated).forEach((r) => {
    const [s, g] = cuts[cuts.length - 1];
    if (r.source > s && r.generated > g) cuts.push([r.source, r.generated]);
  });

  // The marks on each line of each side: [column, length, n], in order.
  const marks = { source: new Map(), generated: new Map() };
  derivations.forEach((d) => ["source", "generated"].forEach((side) => (d[side] || []).forEach((span) => {
    const at = marks[side].get(span.line) || [];
    at.push([span.column - 1, span.text.length, d.n]);
    marks[side].set(span.line, at.sort((a, b) => a[0] - b[0]));
  })));

  // One line, coloured as Kotlin, each derivation's text marked and its number after it.
  const line = (lines, side, number) => {
    const raw = lines[number - 1] ?? "";
    const cls = window.KotlinView.classes(raw);
    const on = marks[side].get(number) || [];
    let html = `<span class="ln">${number}</span>`;
    let i = 0;
    const run = (from, to) => {
      let out = "";
      for (let j = from; j < to;) {
        let k = j;
        while (k < to && cls[k] === cls[j]) k++;
        const t = esc(raw.slice(j, k));
        out += cls[j] ? `<span class="${cls[j]}">${t}</span>` : t;
        j = k;
      }
      return out;
    };
    on.forEach(([column, length, n]) => {
      if (column < i) return;
      const k = colour(byN.get(n).kind);
      html += run(i, column);
      html += `<span class="m" data-n="${n}" style="--k:${k}">${run(column, column + length)}</span>`;
      html += `<button class="b" type="button" data-n="${n}" style="--k:${k}" aria-label="derivation ${n}">${n}</button>`;
      i = column + length;
    });
    return html + run(i, raw.length);
  };

  const diff = document.getElementById("diff");
  diff.querySelector(".cols").innerHTML =
    `<div>The run's script: ${esc(record.source || "")}</div><div>Generated: ${esc(record.script || "")}</div>`;
  cuts.forEach(([s, g], i) => {
    const [s2, g2] = cuts[i + 1] || [source.length + 1, generated.length + 1];
    const lines = (all, side, from, to) => {
      const out = [];
      for (let n = from; n < to; n++) out.push(line(all, side, n));
      return out.join("\n");
    };
    const row = document.createElement("div");
    row.className = "row";
    row.innerHTML = `<pre class="l">${lines(source, "source", s, s2)}</pre>` +
      `<pre class="r">${lines(generated, "generated", g, g2)}</pre>`;
    const shown = [...new Set([...row.querySelectorAll(".m")].map((m) => +m.dataset.n))].sort((a, b) => a - b);
    const notes = document.createElement("div");
    notes.className = "notes";
    notes.hidden = true;
    shown.forEach((n) => {
      const d = byN.get(n);
      const note = document.createElement("div");
      note.className = "note";
      note.dataset.n = n;
      note.hidden = true;
      note.style.setProperty("--k", colour(d.kind));
      note.innerHTML = `<span class="b">${n}</span><div><span class="kind">${esc(KINDS[d.kind] || d.kind)}</span> ` +
        `<b>${esc(d.title)}</b> <span class="rule">rule ${esc(d.rule)}</span><p>${esc(d.why)}</p></div>`;
      notes.append(note);
    });
    row.append(notes);
    diff.append(row);
  });

  // The facts, from the texts and the record.
  // A reason step: an act with only its intent, no relation (the older reason(…) too, in pages built before).
  const reasons = (lines) => lines.filter((l) => /\bact\(\s*intent\s*=|\breason\(/.test(l)).length;
  const reviews = generated.filter((l) => /\/\/ review:/.test(l)).length;
  document.getElementById("facts").innerHTML = [
    `<span><b>${reasons(source)}</b> reason steps → <b>${reasons(generated)}</b></span>`,
    `<span><b>${derivations.length}</b> derivations</span>`,
    `<span><b>${reviews}</b> ${reviews === 1 ? "value" : "values"} to review</span>`,
    `<span>capture: ${esc(record.capture || "")}</span>`,
  ].join("");
  const kinds = [...new Set(derivations.map((d) => KINDS[d.kind] || d.kind))];
  document.getElementById("key").innerHTML = kinds.map((label) => {
    const kind = Object.keys(KINDS).find((k) => KINDS[k] === label) || label;
    return `<span style="--k:${colour(kind)}">${esc(label)}</span>`;
  }).join("");

  // Toggles: a number opens its notes in every row it marks, and lights its marks on both sides.
  const sync = () => document.querySelectorAll(".notes").forEach((ns) => {
    ns.hidden = ![...ns.children].some((n) => !n.hidden);
  });
  const lit = (n, on) => document.querySelectorAll(`.m[data-n="${n}"]`).forEach((m) => m.classList.toggle("on", on));
  diff.addEventListener("click", (e) => {
    const t = e.target.closest(".m, .b");
    if (!t || !t.dataset.n) return;
    const n = t.dataset.n;
    const notes = document.querySelectorAll(`.note[data-n="${n}"]`);
    const open = [...notes].some((x) => x.hidden);
    notes.forEach((x) => { x.hidden = !open; });
    lit(n, open);
    sync();
  });
  document.getElementById("all").addEventListener("change", (e) => {
    document.querySelectorAll(".note").forEach((x) => { x.hidden = !e.target.checked; });
    derivations.forEach((d) => lit(d.n, e.target.checked));
    sync();
  });
  const theme = (t) => {
    document.documentElement.dataset.theme = t;
    document.querySelectorAll(".theme button").forEach((x) => x.setAttribute("aria-pressed", String(x.dataset.th === t)));
    try { localStorage.setItem("dr-theme", t); } catch (e) { /* the page works without it */ }
  };
  document.querySelectorAll(".theme button").forEach((b) => b.addEventListener("click", () => theme(b.dataset.th)));
  theme(document.documentElement.dataset.theme || "light");
})();
