// The release, message by message, for a heart-deck slide: code on demand (deck:code). build-sequence.sh puts the
// runs' messages (window.SEQ_RUNS, from the desk's traces) in front of this file. The slide's HTML holds
// <div class="seqx"></div>; mount draws into it and returns how to stop.
// Each party's state is read from the messages alone:
//   waiting   a RISE party asked and not yet answered: a clock, pulsing
//   sending   the party answers its reply now: an envelope, then the envelope flies along the arrow
//   working   Jenkins between being started and saying it is done: a turning gear
//   resting   a reply resource, once made: Zzz, slowly; it wakes for a moment when an answer lands on it
(function () {
  "use strict";
  const me = document.currentScript && document.currentScript.src;
  const RUNS = window.SEQ_RUNS || {};
  const NS = "http://www.w3.org/2000/svg";
  const PARTIES = [
    ["artie", "Desk · ARTIE"], ["replies", "Desk replies"], ["forge", "Forge gateway"], ["watch", "Watcher"],
    ["ci", "CI wrapper"], ["jenkins", "Jenkins"], ["preview", "Preview"], ["dev", "Dev"],
  ];
  const CONSTRAINTS = [
    ["identification", "Identification"], ["representations", "Representations"], ["selfDescriptive", "Self-descriptive"],
    ["hypermedia", "Hypermedia"], ["stateless", "Stateless"], ["cache", "Cache"], ["layered", "Layered"],
  ];
  const SPECIFIC = ["cache", "layered", "hypermedia", "stateless", "representations", "selfDescriptive", "identification"];
  const COL = 96, X0 = 50, ROW = 44, TOP = 18, W = COL * PARTIES.length;
  const x = (p) => X0 + COL * Math.max(0, PARTIES.findIndex(([k]) => k === p));
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  const COLOR = { heart: "var(--sq-heart)", rise: "var(--sq-rise)", own: "var(--sq-own)" };

  const STYLE = `
  .seqx { --sq-heart: #a80036; --sq-rise: #1b7f3b; --sq-own: #1f4fb8; --sq-now: #c8102e; --sq-wait: #9a5b00;
    display: grid; gap: .5rem; font: 13px/1.45 var(--body); }
  :root[data-theme="dark"] .seqx, .slide[data-tone="dark"] .seqx { --sq-heart: #ff8a9b; --sq-rise: #7cc4a0; --sq-own: #8db8ec; --sq-now: #ff7a85; --sq-wait: #e8ad57; }
  @media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) .seqx { --sq-heart: #ff8a9b; --sq-rise: #7cc4a0; --sq-own: #8db8ec; --sq-now: #ff7a85; --sq-wait: #e8ad57; } }
  .seqx .sq-bar { display: flex; flex-wrap: wrap; gap: .35rem; align-items: center; }
  .seqx .sq-bar button, .seqx .sq-bar select { font: 600 12px var(--mono); padding: .25rem .6rem; border: 1px solid var(--rule);
    border-radius: 999px; background: var(--surface); color: var(--fg); cursor: pointer; }
  .seqx .sq-bar .sq-play { border-color: var(--leaf); min-width: 5.6rem; }
  .seqx .sq-bar input[type=range] { flex: 1 1 120px; accent-color: var(--sq-now); }
  .seqx .sq-where { font: 12px var(--mono); color: var(--muted); min-width: 4rem; font-variant-numeric: tabular-nums; }
  .seqx .sq-grid { display: grid; grid-template-columns: minmax(0, 2.3fr) minmax(0, 1fr); gap: .7rem; align-items: start; }
  @media (max-width: 760px) { .seqx .sq-grid { grid-template-columns: 1fr; } }
  .seqx .sq-pane { position: relative; background: var(--paper); border: 1px solid var(--rule); border-radius: 10px; overflow: hidden; min-width: 0; }
  .seqx .sq-heads { border-bottom: 1px solid var(--rule); overflow: hidden; }
  .seqx .sq-scroll { height: 360px; overflow: auto; }
  .seqx svg { display: block; width: 100%; min-width: 700px; }
  .seqx svg text { fill: currentColor; font: 11.5px var(--body); }
  .seqx svg text.href { font: 10.5px var(--mono); transition: opacity .3s; }
  .seqx svg text.href.pending { opacity: 0; }
  .seqx .m { transition: opacity .25s; cursor: pointer; }
  .seqx .m.future { opacity: 0; pointer-events: none; }
  .seqx .m.past { opacity: .38; }
  .seqx .m.now line { stroke-width: 2.6; stroke-dasharray: 8 5; animation: sq-flow .8s linear infinite; }
  .seqx .m.now text.rel { font-weight: 700; }
  @keyframes sq-flow { to { stroke-dashoffset: -13; } }
  .seqx .chip-r { transition: opacity .25s; }
  .seqx .chip-r.future { opacity: 0; }
  /* Party states */
  .seqx .badge > g { display: none; }
  .seqx .badge[data-s="waiting"] .b-clock, .seqx .badge[data-s="sending"] .b-mail, .seqx .badge[data-s="working"] .b-gear,
  .seqx .badge[data-s="resting"] .b-zzz, .seqx .badge[data-s="woken"] .b-mail, .seqx .badge[data-s="done"] .b-done { display: inline; }
  .seqx .b-clock { color: var(--sq-wait); animation: sq-pulse 1.8s ease-in-out infinite; transform-box: fill-box; transform-origin: center; }
  .seqx .b-gear { color: var(--sq-own); }
  .seqx .b-mail { color: var(--sq-rise); animation: sq-pop .9s ease-out 1; transform-box: fill-box; transform-origin: center; }
  .seqx .b-done { color: var(--sq-rise); }
  .seqx .b-zzz text, .seqx .zz text { font: 700 10px var(--mono); fill: var(--muted); animation: sq-zzz 3.2s ease-in-out infinite; }
  .seqx .b-zzz text:nth-child(2), .seqx .zz text:nth-child(2) { animation-delay: .5s; }
  .seqx .b-zzz text:nth-child(3), .seqx .zz text:nth-child(3) { animation-delay: 1s; }
  .seqx .head-box { transition: stroke .3s; }
  .seqx .head-box[data-s="waiting"] { stroke: var(--sq-wait); }
  .seqx .head-box[data-s="sending"], .seqx .head-box[data-s="woken"] { stroke: var(--sq-rise); stroke-width: 2.2; }
  .seqx .head-box[data-s="working"] { stroke: var(--sq-own); }
  @keyframes sq-pulse { 0%, 100% { opacity: .55; transform: scale(.92); } 50% { opacity: 1; transform: scale(1.06); } }
  @keyframes sq-pop { 0% { transform: scale(.4); opacity: 0; } 60% { transform: scale(1.2); opacity: 1; } 100% { transform: scale(1); } }
  @keyframes sq-zzz { 0%, 100% { opacity: .25; transform: translateY(1px); } 50% { opacity: .9; transform: translateY(-2px); } }
  .seqx .flying { pointer-events: none; color: var(--sq-rise); }
  @media (prefers-reduced-motion: reduce) {
    .seqx .m.now line, .seqx .b-clock, .seqx .b-zzz text, .seqx .zz text, .seqx .b-mail { animation: none; }
  }
  .seqx .sq-legend { display: flex; flex-wrap: wrap; gap: .2rem .9rem; padding: .35rem .6rem; font-size: 11.5px; color: var(--muted); border-top: 1px solid var(--rule); }
  .seqx .sq-legend i { display: inline-block; width: 16px; height: 3px; vertical-align: middle; margin-right: .3rem; }
  .seqx .sq-detail { background: var(--paper); border: 1px solid var(--rule); border-radius: 10px; padding: .6rem .75rem; min-width: 0; display: grid; gap: .45rem; align-content: start; }
  .seqx .sq-detail h4 { margin: 0; font: 650 1rem/1.3 var(--display); }
  .seqx .sq-detail .note { color: var(--muted); margin: 0; font-size: 12.5px; }
  .seqx .sq-msg { font: 11.5px/1.45 var(--mono); background: var(--code-bg); border-radius: 6px; padding: .45rem .55rem; overflow-x: auto; white-space: pre; margin: 0; }
  .seqx .sq-chips { display: flex; flex-wrap: wrap; gap: .25rem; }
  .seqx .sq-chip { font: 600 11px var(--body); padding: .1rem .5rem; border-radius: 999px; background: transparent; color: var(--muted); border: 1px solid var(--rule); cursor: pointer; }
  .seqx .sq-chip.on { background: var(--leaf-bg); border-color: var(--leaf); color: var(--fg); }
  .seqx .sq-chip.picked { outline: 2px solid var(--sq-now); outline-offset: 1px; }
  .seqx .sq-why { margin: 0; font-size: 12.5px; }
  .seqx .sq-float { position: absolute; z-index: 3; max-width: min(92%, 420px); background: var(--surface); border: 1px solid var(--rule);
    border-radius: 8px; box-shadow: 0 6px 24px rgb(0 0 0 / .25); padding: .35rem .5rem; opacity: 0; transform: translateY(6px);
    transition: opacity .3s, transform .3s; pointer-events: none; }
  .seqx .sq-float.shown { opacity: 1; transform: none; }
  .seqx .sq-float .from { font-size: 11px; color: var(--muted); }
  .seqx .sq-float pre { margin: 0; font: 11px/1.4 var(--mono); white-space: pre-wrap; overflow-wrap: anywhere; }
  .seqx .sq-float mark { background: transparent; color: var(--sq-now); font-weight: 700; animation: sq-flash .45s ease-in-out 3 alternate; }
  @keyframes sq-flash { to { background: color-mix(in srgb, var(--sq-now) 28%, transparent); } }
  .seqx .sq-float .hrefsrc { color: var(--sq-rise); font-weight: 600; }
  .seqx .sq-fly { position: absolute; z-index: 4; font: 600 11px var(--mono); color: var(--sq-now); background: var(--surface);
    border: 1px solid var(--sq-now); border-radius: 4px; padding: 0 .25rem; pointer-events: none; white-space: nowrap;
    transition: transform .8s cubic-bezier(.3, .7, .2, 1); }
  `;

  // Glyphs, each drawn in a 16 × 16 box at the origin.
  const CLOCK = `<g class="b-clock"><circle cx="8" cy="8" r="7" fill="none" stroke="currentColor" stroke-width="1.6"/>` +
    `<line x1="8" y1="8" x2="8" y2="4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>` +
    `<g class="hand"><line x1="8" y1="8" x2="11.5" y2="8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>SPIN3</g></g>`;
  const MAIL = `<g class="b-mail"><rect x="1" y="3.5" width="14" height="9.5" rx="1.5" fill="var(--surface)" stroke="currentColor" stroke-width="1.6"/>` +
    `<path d="M1.6 4.4 8 9.2l6.4-4.8" fill="none" stroke="currentColor" stroke-width="1.5"/></g>`;
  const GEAR = `<g class="b-gear"><path d="M8 2.2l1 1.6 1.8-.5.4 1.9 1.8.7-.6 1.8 1.2 1.4-1.4 1.2.3 1.9-1.9.2-.7 1.8-1.7-.9L8 14l-1-1.6-1.8.5-.4-1.9-1.8-.7.6-1.8L2.4 7.1l1.4-1.2-.3-1.9 1.9-.2.7-1.8 1.7.9z M8 6a2 2 0 1 0 0 4 2 2 0 1 0 0-4z" fill="currentColor" fill-rule="evenodd"/>SPIN2</g>`;
  const ZZZ = `<g class="b-zzz"><text x="0" y="14">z</text><text x="5" y="10">z</text><text x="10" y="6">Z</text></g>`;
  const DONE = `<g class="b-done"><path d="M2.5 8.5l3.5 3.5 7-8" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></g>`;

  // A turn about the glyph's centre, as SMIL, so it turns in the glyph's own coordinates; none with reduced motion.
  const spin = (s) => (window.matchMedia("(prefers-reduced-motion: reduce)").matches ? ""
    : `<animateTransform attributeName="transform" type="rotate" from="0 8 8" to="360 8 8" dur="${s}s" repeatCount="indefinite"/>`);
  const glyphs = () => CLOCK.replace("SPIN3", spin(3)) + MAIL + GEAR.replace("SPIN2", spin(2.4)) + ZZZ + DONE;

  /** Each party's state at each message, from the messages alone; and each reply's chip. */
  function states(ms) {
    const per = PARTIES.map(() => ms.map(() => "idle"));
    const set = (p, from, to, s) => { const k = PARTIES.findIndex(([n]) => n === p); for (let i = from; i <= to && i < ms.length; i++) per[k][i] = s; };
    const chips = [];
    ms.forEach((m, i) => {
      // An ask to a RISE party: it waits from here until the message where it answers its reply.
      if (m.kind === "heart" && m.method === "POST" && (m.to === "watch" || m.to === "ci")) {
        const answered = ms.findIndex((n, j) => j > i && n.kind === "rise" && n.from === m.to);
        const end = answered < 0 ? ms.length - 1 : answered;
        set(m.to, i, end - 1, "waiting");
        if (answered >= 0) { set(m.to, answered, answered, "sending"); set(m.to, answered + 1, ms.length - 1, "done"); }
      }
      if (m.kind === "own" && m.from === "ci" && m.to === "jenkins") {
        const done = ms.findIndex((n, j) => j > i && n.kind === "own" && n.from === "jenkins" && /COMPLETED/.test(n.label));
        set("jenkins", i, done < 0 ? ms.length - 1 : done, "working");
      }
      if (m.kind === "rise" && m.inside) {
        // A reply, made in the desk: resting from here on, woken for a moment when its answer lands.
        const asked = ms.findIndex((n, j) => j > i && n.kind === "heart");
        const party = asked >= 0 ? ms[asked].to : null;
        const answered = ms.findIndex((n, j) => j > i && n.kind === "rise" && n.from === party && n.to === "replies");
        chips.push({ at: i, label: `r${chips.length + 1}`, answered });
      }
    });
    const k = PARTIES.findIndex(([n]) => n === "replies");
    ms.forEach((m, i) => {
      if (chips.some((c) => c.at <= i)) per[k][i] = "resting";
      if (m.kind === "rise" && m.to === "replies" && !m.inside && m.from !== "artie") per[k][i] = "woken";
    });
    return { per, chips };
  }

  function mount(slide) {
    const root = slide.querySelector(".seqx");
    if (!root) return null;
    if (!document.getElementById("seqx-style")) {
      const style = document.createElement("style");
      style.id = "seqx-style"; style.textContent = STYLE; document.head.append(style);
    }
    const names = (root.dataset.runs || Object.keys(RUNS).join(",")).split(",").filter((n) => RUNS[n]);
    const label = JSON.parse(root.dataset.labels || "{}");
    const fallback = root.innerHTML;
    root.innerHTML = `
      <div class="sq-bar" role="group" aria-label="Playback">
        <button type="button" data-do="first" aria-label="First message">⏮</button>
        <button type="button" data-do="back" aria-label="Back one message">◀</button>
        <button type="button" class="sq-play" data-do="play">▶ Play</button>
        <button type="button" data-do="next" aria-label="Forward one message">▶|</button>
        <input type="range" min="1" value="1" aria-label="Message">
        <span class="sq-where"></span>
        <select class="sq-run" aria-label="Run">${names.map((n) => `<option value="${n}">${esc(label[n] || n)}</option>`).join("")}</select>
        <select class="sq-speed" aria-label="Speed"><option value="1500">½×</option><option value="750" selected>1×</option><option value="380">2×</option></select>
      </div>
      <div class="sq-grid">
        <div class="sq-pane">
          <div class="sq-float" hidden aria-hidden="true"></div>
          <div class="sq-heads"><svg class="sq-h" role="presentation"></svg></div>
          <div class="sq-scroll"><svg class="sq-s" role="img" aria-label="The release's messages between ARTIE and the parties, up to the one playing."></svg></div>
          <div class="sq-legend">
            <span><i style="background:var(--sq-heart)"></i>HEART: ARTIE's requests</span>
            <span><i style="background:var(--sq-rise)"></i>RISE: replies and their signed answers</span>
            <span><i style="background:var(--sq-own)"></i>The parties' own, unseen by ARTIE</span>
          </div>
        </div>
        <div class="sq-detail" aria-live="polite"></div>
      </div>`;
    const $ = (sel) => root.querySelector(sel);
    const heads = $(".sq-h"), seq = $(".sq-s"), scroll = $(".sq-scroll"), detail = $(".sq-detail"), pane = $(".sq-pane");
    const slider = $("input[type=range]"), speed = $(".sq-speed"), playButton = $(".sq-play"), box = $(".sq-float");
    const calm = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let run = names[0], ms = [], st = null, at = 0, timer = null, picked = null, steps = [], flights = [];

    function drawHeads() {
      heads.setAttribute("viewBox", `0 -12 ${W} 66`);
      heads.innerHTML = PARTIES.map(([k, name]) =>
        `<rect class="head-box" data-p="${k}" x="${x(k) - 45}" y="16" width="90" height="32" rx="6" fill="var(--surface)" stroke="var(--rule)" stroke-width="1.5"/>` +
        `<text x="${x(k)}" y="36" text-anchor="middle" font-weight="600">${esc(name)}</text>` +
        `<g class="badge" data-p="${k}" transform="translate(${x(k) + 28} -9) scale(1.3)">${glyphs()}</g>`).join("");
    }

    function draw() {
      ms = RUNS[run]; st = states(ms);
      const h = TOP + ROW * ms.length + 16;
      seq.setAttribute("viewBox", `0 0 ${W} ${h}`);
      const lines = PARTIES.map(([k]) => `<line x1="${x(k)}" y1="0" x2="${x(k)}" y2="${h}" stroke="var(--rule)" stroke-width="1.5" stroke-dasharray="4 4"/>`).join("");
      const defs = `<defs>${Object.entries(COLOR).map(([k, c]) =>
        `<marker id="sq-${k}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" style="fill:${c}"/></marker>`).join("")}</defs>`;
      const rows = ms.map((m, i) => {
        const y = TOP + ROW * i + 18, x1 = x(m.from), x2 = x(m.to), dir = x2 > x1 ? -2 : 2;
        const mid = Math.min(x1, x2) + 8, c = COLOR[m.kind];
        const named = m.rel ? `${m.n}  ${m.rel}` : m.n ? `${m.n}  ${m.method || ""}` : m.label;
        const text = `${named}${m.status ? ` · ${m.status}` : ""}`;
        const href = m.path ? `<text class="href" x="${mid}" y="${y + 13}" style="fill:${c}">${esc(`${m.method} ${m.path}`)}</text>` : "";
        const flat = x1 === x2;
        const line = flat
          ? `<path d="M${x1} ${y - 6} h22 v12 h-20" fill="none" style="stroke:${c}" stroke-width="1.6" stroke-dasharray="3 3" marker-end="url(#sq-${m.kind})"/>`
          : `<line x1="${x1}" y1="${y}" x2="${x2 + dir}" y2="${y}" style="stroke:${c}" stroke-width="1.6"${m.kind === "own" || m.inside ? ' stroke-dasharray="3 3"' : ""} marker-end="url(#sq-${m.kind})"/>`;
        return `<g class="m" data-i="${i}">${line}<text class="rel" x="${flat ? x1 + 28 : mid}" y="${y - 6}" style="fill:${c}">${esc(text)}</text>${href}</g>`;
      }).join("");
      // Each reply resource, where it was made: a chip on the replies' lifeline that rests, Zzz.
      const xr = x("replies");
      const chips = st.chips.map((c) => {
        const y = TOP + ROW * c.at + 18;
        return `<g class="chip-r" data-at="${c.at}"><rect x="${xr - 13}" y="${y + 6}" width="26" height="15" rx="4" fill="var(--surface)" stroke="var(--sq-rise)" stroke-width="1.3"/>` +
          `<text x="${xr}" y="${y + 17}" text-anchor="middle" style="font: 600 9.5px var(--mono); fill: var(--sq-rise)">${c.label}</text>` +
          `<g class="zz" transform="translate(${xr + 15} ${y + 2})"><text x="0" y="14">z</text><text x="5" y="10">z</text><text x="10" y="6">Z</text></g></g>`;
      }).join("");
      seq.innerHTML = defs + lines + rows + chips;
      seq.querySelectorAll(".m").forEach((g) => g.addEventListener("click", () => { stop(); show(+g.dataset.i); }));
      slider.max = ms.length;
    }

    function badges() {
      PARTIES.forEach(([k], j) => {
        const s = st.per[j][at];
        heads.querySelector(`.badge[data-p="${k}"]`).dataset.s = s;
        heads.querySelector(`.head-box[data-p="${k}"]`).dataset.s = s;
      });
      seq.querySelectorAll(".chip-r").forEach((g) => g.classList.toggle("future", +g.dataset.at > at));
    }

    // A RISE party answering: its envelope leaves its lifeline and travels the arrow to the reply.
    function fly(m) {
      flights.forEach((f) => f.remove()); flights = [];
      if (calm() || m.kind !== "rise" || m.inside || m.from === "artie") return;
      const y = TOP + ROW * at + 18, x1 = x(m.from), x2 = x(m.to);
      const g = document.createElementNS(NS, "g");
      g.setAttribute("class", "flying");
      g.innerHTML = MAIL.replace('class="b-mail"', 'class="fl"');
      seq.append(g); flights.push(g);
      const s = +speed.value / 750;
      g.animate([{ transform: `translate(${x1 - 8}px, ${y - 8}px)` }, { transform: `translate(${x2 - 8}px, ${y - 8}px)` }],
        { duration: 900 * s, easing: "cubic-bezier(.3,.7,.2,1)", fill: "forwards" });
      steps.push(setTimeout(() => g.remove(), 1100 * s));
    }

    function chipsHtml(m) {
      const lit = m.why || {};
      const best = picked && lit[picked] ? picked : SPECIFIC.find((k) => lit[k]);
      const chip = ([k, name]) => `<button type="button" class="sq-chip${lit[k] ? " on" : ""}${k === best ? " picked" : ""}" data-k="${k}" aria-pressed="${k === best}">${esc(name)}</button>`;
      return `<div class="sq-chips">${CONSTRAINTS.map(chip).join("")}</div>` + (best ? `<p class="sq-why">${esc(lit[best])}</p>` : `<p class="note">ARTIE takes no part in this message.</p>`);
    }

    function stateLine() {
      const said = { waiting: "waits", sending: "answers its reply", working: "is building", resting: "rest", woken: "takes an answer" };
      const out = [];
      PARTIES.forEach(([k, name], j) => { const s = st.per[j][at]; if (said[s]) out.push(`<b>${esc(name)}</b> ${said[s]}`); });
      return out.length ? `<p class="note">${out.join(" · ")}</p>` : "";
    }

    function show(i, instant) {
      at = Math.max(0, Math.min(ms.length - 1, i));
      const m = ms[at];
      seq.querySelectorAll(".m").forEach((g) => {
        const j = +g.dataset.i;
        g.classList.toggle("future", j > at); g.classList.toggle("past", j < at); g.classList.toggle("now", j === at);
      });
      slider.value = at + 1;
      $(".sq-where").textContent = `${at + 1} / ${ms.length}`;
      const head = m.n ? `Step ${m.n} · ${esc(m.say)}` : esc(m.label);
      const answered = m.answer && m.answer.length ? `\n\n${esc(m.answer.join("\n"))}` : "";
      const exchange = m.asked ? `<pre class="sq-msg">${esc(m.asked.join("\n"))}${answered}</pre>` : "";
      detail.innerHTML = `<h4>${head}</h4>${m.note ? `<p class="note">${esc(m.note)}</p>` : ""}${stateLine()}${exchange}${chipsHtml(m)}`;
      detail.querySelectorAll(".sq-chip").forEach((b) => b.addEventListener("click", () => { picked = b.dataset.k; show(at); }));
      const scale = seq.getBoundingClientRect().width / W, row = (TOP + ROW * at) * scale;
      if (row < scroll.scrollTop + 40 || row > scroll.scrollTop + scroll.clientHeight - 90) {
        scroll.scrollTo({ top: row - scroll.clientHeight / 2, behavior: instant || calm() ? "auto" : "smooth" });
      }
      badges();
      steps.forEach(clearTimeout); steps = [];
      fly(m);
      offer(m);
    }

    // The offer a message followed: the bit of JSON ARTIE held where its relation appears, flashed; then the href it
    // handed over lifted out and put onto the arrow.
    function offer(m) {
      pane.querySelectorAll(".sq-fly").forEach((f) => f.remove());
      box.hidden = true; box.classList.remove("shown");
      const g = seq.querySelector(`.m[data-i="${at}"]`), target = g && g.querySelector(".href");
      if (!m.offer || !target || calm()) return;
      const json = esc(m.offer.json)
        .replace(`&quot;${esc(m.rel)}&quot;`, `<mark>&quot;${esc(m.rel)}&quot;</mark>`)
        .replace(`&quot;${esc(m.offer.href)}&quot;`, `<span class="hrefsrc">&quot;${esc(m.offer.href)}&quot;</span>`);
      box.innerHTML = `<div class="from">held: ${m.offer.kind === "form" ? "a form" : "a link"} at <code>${esc(m.offer.where)}</code></div><pre>${json}</pre>`;
      target.classList.add("pending");
      const s = +speed.value / 750;
      steps.push(setTimeout(() => {
        const p = pane.getBoundingClientRect(), r = g.querySelector(".rel").getBoundingClientRect();
        box.hidden = false;
        box.style.left = `${Math.max(8, Math.min(p.width - box.offsetWidth - 8, r.left - p.left))}px`;
        box.style.top = `${Math.max(50, r.top - p.top - box.offsetHeight - 10)}px`;
        requestAnimationFrame(() => box.classList.add("shown"));
      }, 0));
      steps.push(setTimeout(() => {
        const src = box.querySelector(".hrefsrc");
        if (!src) return;
        const p = pane.getBoundingClientRect(), a = src.getBoundingClientRect(), b = target.getBoundingClientRect();
        const f = document.createElement("span");
        f.className = "sq-fly"; f.textContent = m.offer.href;
        f.style.left = `${a.left - p.left}px`; f.style.top = `${a.top - p.top}px`;
        pane.append(f);
        requestAnimationFrame(() => { f.style.transform = `translate(${b.left - a.left}px, ${b.top - a.top}px)`; });
      }, 1000 * s));
      steps.push(setTimeout(() => {
        target.classList.remove("pending");
        pane.querySelectorAll(".sq-fly").forEach((f) => f.remove());
        box.classList.remove("shown");
      }, 1900 * s));
      steps.push(setTimeout(() => { box.hidden = true; }, 2300 * s));
    }

    const OFFER_MS = 2600;
    const wait = () => +speed.value + (ms[at].offer ? OFFER_MS * (+speed.value / 750) : 0);
    function stop() { clearTimeout(timer); timer = null; playButton.textContent = "▶ Play"; }
    function tick() { if (at >= ms.length - 1) return stop(); show(at + 1); timer = setTimeout(tick, wait()); }
    function play() {
      if (timer) return stop();
      if (at >= ms.length - 1) show(0);
      playButton.textContent = "⏸ Pause";
      timer = setTimeout(tick, wait());
    }
    root.querySelector('[data-do="play"]').addEventListener("click", play);
    root.querySelector('[data-do="first"]').addEventListener("click", () => { stop(); show(0); });
    root.querySelector('[data-do="back"]').addEventListener("click", () => { stop(); show(at - 1); });
    root.querySelector('[data-do="next"]').addEventListener("click", () => { stop(); show(at + 1); });
    slider.addEventListener("input", () => { stop(); show(slider.value - 1); });
    speed.addEventListener("change", () => { if (timer) { stop(); play(); } });
    $(".sq-run").addEventListener("change", (e) => { stop(); run = e.target.value; picked = null; draw(); show(0); });
    scroll.addEventListener("scroll", () => { heads.parentElement.scrollLeft = scroll.scrollLeft; });
    // Keys the deck does not use: space plays, , and . step.
    const keys = (e) => {
      if (!root.isConnected || e.target.matches("input, select, textarea")) return;
      if (e.key === " ") { e.preventDefault(); play(); }
      if (e.key === ".") { stop(); show(at + 1); }
      if (e.key === ",") { stop(); show(at - 1); }
    };
    document.addEventListener("keydown", keys);

    // A link may start at a message: ?seq=12 (and ?run=gl501).
    const asked = new URLSearchParams(location.search);
    if (RUNS[asked.get("run")] && names.includes(asked.get("run"))) { run = asked.get("run"); $(".sq-run").value = run; }
    drawHeads(); draw(); show((+asked.get("seq") || 1) - 1, true);
    return function unmount() {
      stop(); steps.forEach(clearTimeout); document.removeEventListener("keydown", keys);
      root.innerHTML = fallback;
    };
  }

  window.deckCode = window.deckCode || {};
  window.deckCode[me] = mount;
})();
