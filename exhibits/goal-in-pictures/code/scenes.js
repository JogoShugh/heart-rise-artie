// Two animated scenes for heart-deck slides: code on demand (deck:code).
//   .sra    sync, reason, act, played over pull request 501's own representation (the desk's trace of the run)
//   .city   the release as a city: servers are districts, resources buildings, links streets; ARTIE walks them
// Each slide's HTML reads whole with no script; mount replaces it with the scene and puts it back on unmount.
(function () {
  "use strict";
  const me = document.currentScript && document.currentScript.src;
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  const NS = "http://www.w3.org/2000/svg";

  // A run that can be cancelled: every wait checks it.
  function runner() {
    let gen = 0;
    const timers = new Set();
    return {
      start() { gen++; timers.forEach(clearTimeout); timers.clear(); return gen; },
      live(g) { return g === gen; },
      wait(g, ms) {
        return new Promise((ok, no) => {
          const t = setTimeout(() => { timers.delete(t); gen === g ? ok() : no(new Error("cancelled")); }, ms);
          timers.add(t);
        });
      },
      stop() { gen++; timers.forEach(clearTimeout); timers.clear(); },
    };
  }

  // ------------------------------------------------------------------ sync, reason, act
  // Pull request 501 as the forge answered it, a moment before the merge and just after (trimmed; values as served).
  const FORMS_BEFORE = ["scm:close", "scm:merge", "scm:comment", "scm:label", "scm:edit", "scm:request-review", "scm:update-branch"];
  const FORMS_AFTER = ["scm:comment", "scm:label", "scm:edit"];
  function sraLines(after) {
    const L = [];
    const v = (k, val, cls) => L.push({ k, html: `  <span class="k">"${k}"</span>: <span class="v${cls ? " " + cls : ""}">${esc(val)}</span>,` });
    L.push({ html: "{" });
    v("number", "501");
    v("title", '"Renewals: two weeks for short loans"');
    v("state", after ? '"closed"' : '"open"', "st");
    v("mergeable_state", after ? '"unknown"' : '"clean"', "st");
    v("merged", after ? "true" : "false", "st");
    L.push({ html: '  <span class="c">… more fields, as GitHub has them</span>' });
    L.push({ html: '  <span class="k">"_links"</span>: { <span class="r">"self"</span>, <span class="r">"scm:commits"</span>, <span class="r">"scm:diff"</span>, <span class="c">… and more</span> },' });
    L.push({ html: '  <span class="k">"_forms"</span>: {' });
    for (const f of after ? FORMS_AFTER : FORMS_BEFORE) {
      if (f === "scm:merge") {
        L.push({ form: f, html: '    <span class="r">"scm:merge"</span>: { <span class="k">"method"</span>: "PUT",' });
        L.push({ form: f, html: '      <span class="k">"target"</span>: "/repos/acme/web/pulls/501/actions/merge",' });
        L.push({ form: f, html: '      <span class="k">"schema"</span>: { … <span class="k">"enum"</span>: [<span class="opt">"merge"</span>, <span class="opt sq">"squash"</span>, <span class="opt">"rebase"</span>] … },' });
        L.push({ form: f, integrity: true, html: '      <span class="k">"hr:integrity"</span>: "sha256-sGUdrPfH…" },' });
      } else L.push({ form: f, html: `    <span class="r">"${f}"</span>: { … },` });
    }
    L.push({ html: "  }" });
    L.push({ html: "}" });
    return L;
  }

  function mountSra(root) {
    const fallback = root.innerHTML;
    const R = runner();
    let mode = "walk";
    root.innerHTML = `
      <div class="sra-bar">
        <button type="button" data-mode="walk" aria-pressed="true">The walk, with Claude</button>
        <button type="button" data-mode="replay" aria-pressed="false">The replay, no model</button>
        <span class="sra-cap" aria-live="polite"></span>
      </div>
      <div class="sra-grid">
        <div class="sra-pane">
          <p class="sra-req"></p>
          <pre class="sra-body"></pre>
        </div>
        <div class="sra-side">
          <ol class="sra-ring">
            <li data-ph="sync"><b>Sync</b><span>read the resource as it is now</span></li>
            <li data-ph="reason"><b>Reason</b><span>which offer serves the goal?</span></li>
            <li data-ph="act"><b>Act</b><span>fill the form, send it</span></li>
          </ol>
          <p class="sra-goal">Goal: <q>Once {watch} is answered, at {pr}, merge it by squashing</q></p>
          <div class="sra-who"></div>
          <pre class="sra-act"></pre>
          <p class="sra-meter"></p>
        </div>
      </div>`;
    const $ = (s) => root.querySelector(s);
    const body = $(".sra-body"), req = $(".sra-req"), who = $(".sra-who"), act = $(".sra-act"), cap = $(".sra-cap"), meter = $(".sra-meter");
    const phase = (p) => root.querySelectorAll(".sra-ring li").forEach((li) => li.classList.toggle("on", li.dataset.ph === p));
    const draw = (lines) => {
      body.innerHTML = lines.map((l, i) => `<span class="ln${l.form ? " form" : ""}" data-i="${i}"${l.form ? ` data-form="${l.form}"` : ""}${l.integrity ? " data-integrity" : ""}>${l.html}</span>`).join("\n");
      return [...body.querySelectorAll(".ln")];
    };

    async function play() {
      const g = R.start();
      const w = (ms) => R.wait(g, ms);
      try {
        for (;;) {
          root.dataset.mode = mode;
          // Sync
          phase("sync"); who.innerHTML = ""; act.textContent = ""; act.className = "sra-act"; meter.innerHTML = "";
          cap.textContent = "Sync: ARTIE gets pull request 501 and reads what it is, and what it offers now.";
          req.innerHTML = '<b>GET</b> /repos/acme/web/pulls/501 <span class="h">Accept: application/hal+json · profile hal-schema-forms</span>';
          const lines = draw(sraLines(false));
          lines.forEach((l) => l.classList.add("hide"));
          for (const l of lines) { l.classList.remove("hide"); await w(70); }
          req.innerHTML += ' <span class="ok">200</span>';
          await w(500);
          const integ = body.querySelector("[data-integrity]");
          integ.classList.add("checked");
          await w(900);
          // Reason
          phase("reason");
          const forms = [...new Set(lines.filter((l) => l.dataset.form).map((l) => l.dataset.form))];
          const lit = (f, cls) => lines.filter((l) => l.dataset.form === f).forEach((l) => l.classList.add(cls));
          if (mode === "walk") {
            cap.textContent = "Reason: Claude reads the goal's line and the seven forms on offer.";
            who.innerHTML = '<span class="claude">Claude</span> weighs the offers…';
            meter.innerHTML = '<span class="tok">asks the model</span>';
            for (const f of forms) {
              lines.forEach((l) => l.classList.remove("scan"));
              lit(f, "scan");
              await w(380);
            }
            lines.forEach((l) => l.classList.remove("scan"));
            forms.filter((f) => f !== "scm:merge").forEach((f) => lit(f, "dim"));
            lit("scm:merge", "chosen");
            who.innerHTML = '<span class="claude">Claude</span> chose <code>scm:merge</code>, method <code>squash</code>: the goal said “by squashing”.';
          } else {
            cap.textContent = "Reason, kept: the script already says scm:merge. ARTIE holds the offer to its pin.";
            who.innerHTML = '<span class="pin">Kept script</span> <code>act: scm:merge</code> · <code>method: squash</code>';
            await w(600);
            forms.filter((f) => f !== "scm:merge").forEach((f) => lit(f, "dim"));
            lit("scm:merge", "chosen");
            who.innerHTML += '<br><span class="pin">Pin</span> <code>sha256-sGUdrPfH…</code> = offered <span class="ok">✓</span>';
            meter.innerHTML = '<span class="zero">0 model calls</span>';
          }
          body.querySelector(".opt.sq").classList.add("pick");
          await w(1500);
          // Act
          phase("act");
          cap.textContent = "Act: fill the form the server offered and send it to its target.";
          const head = "PUT /repos/acme/web/pulls/501/actions/merge\n", open = '{ "method": "', word = "squash", close = '" }';
          act.textContent = head;
          await w(400);
          act.textContent += open;
          for (const ch of word) { act.textContent += ch; await w(110); }
          act.textContent += close;
          await w(300);
          act.classList.add("fly");
          await w(900);
          act.classList.add("answered");
          act.textContent += "\n← 200";
          await w(900);
          // Sync again: the answer is the next state
          phase("sync");
          cap.textContent = "Sync again: 501 is merged. scm:merge is no longer offered; close, merge, review and update are gone.";
          req.innerHTML = '<b>GET</b> /repos/acme/web/pulls/501 <span class="ok">200</span>';
          const after = draw(sraLines(true));
          after.forEach((l) => { if (l.querySelector(".st")) l.classList.add("changed"); });
          await w(4200);
        }
      } catch (e) { /* cancelled */ }
    }

    root.addEventListener("click", (e) => {
      const b = e.target.closest("[data-mode]");
      if (!b || b === root) return;
      mode = b.dataset.mode;
      root.querySelectorAll(".sra-bar [data-mode]").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
      play();
    });
    play();
    return () => { R.stop(); root.innerHTML = fallback; delete root.dataset.mode; };
  }

  // ------------------------------------------------------------------ the city
  const S = 31, OX = 300, OY = 70;
  const P = (x, y, z = 0) => [OX + (x - y) * S * 0.866, OY + (x + y) * S * 0.5 - z * S];
  const pts = (a) => a.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  const DISTRICTS = [
    { name: "Forge gateway", hue: "forge", x: 0, y: 0, w: 5.4, d: 4.8 },
    { name: "Watcher", hue: "watch", x: 0, y: 6, w: 5.4, d: 3.4 },
    { name: "CI wrapper · Jenkins", hue: "ci", x: 6.6, y: 0, w: 4.8, d: 4.8 },
    { name: "Preview · the library", hue: "preview", x: 6.6, y: 6, w: 4.8, d: 4.6 },
    { name: "Dev", hue: "dev", x: 12.6, y: 0, w: 4.8, d: 4.8 },
  ];
  const B = {
    forge: { x: 0.8, y: 0.8, h: 0.84, label: "forge index" },
    pr: { x: 3.2, y: 0.8, h: 1.82, label: "pull request 501" },
    comment: { x: 3.2, y: 3.0, h: 0.49, label: "a comment", dim: true },
    watch: { x: 0.8, y: 6.8, h: 1.12, label: "watcher index" },
    reply1: { x: 3.2, y: 6.8, h: 0.63, label: "watch reply" },
    ci: { x: 7.2, y: 0.8, h: 1.05, label: "jenkins index" },
    reply2: { x: 9.4, y: 0.8, h: 0.63, label: "build reply" },
    build: { x: 9.4, y: 3.0, h: 1.4, label: "build 1" },
    smoke: { x: 7.2, y: 3.0, h: 0.7, label: "smoke script" },
    preview: { x: 9.4, y: 6.8, h: 1.54, label: "the library" },
    loans: { x: 7.2, y: 6.8, h: 0.91, label: "loans" },
    loan: { x: 7.2, y: 8.8, h: 0.63, label: "loan 3311" },
    dev: { x: 13.2, y: 0.8, h: 0.91, label: "dev index" },
    env: { x: 13.2, y: 3.0, h: 1.33, label: "environment 1" },
    promo: { x: 15.4, y: 3.0, h: 0.7, label: "promotion" },
  };
  const BW = 0.9;
  const centre = (k) => [B[k].x + BW / 2, B[k].y + BW / 2];
  const STREETS = [
    ["forge", "pr", "scm:pulls-get"], ["pr", "comment", "scm:comment", true], ["watch", "reply1", "watcher:watch"],
    ["ci", "reply2", "ci:build"], ["reply2", "build", "hr:in-reply-to", false, -14], ["build", "smoke", "ci:smoke-test", false, 16],
    ["build", "preview", "ci:deployment"], ["preview", "loans", "library:search-loans"], ["loans", "loan", "item", false, -12],
    ["dev", "env", "env:get-environment"], ["env", "promo", "env:promote"],
  ];
  // The route, as the kept script goes. think: a choice among offers (Claude on the walk, a pin on the replay).
  const ROUTE = [
    { at: "forge", say: "Starts at the forge: a bookmark, the goal's {scm}." },
    { to: "pr", rel: "scm:pulls-get", think: true, say: "Open pull request 501: scm:pulls-get." },
    { hop: "watch", say: "Enters the watcher by bookmark, {watcher}." },
    { to: "reply1", rel: "watcher:watch", think: true, say: "Asks to be told when 501 offers scm:merge." },
    { wait: "reply1", say: "Rests at its reply until the watcher answers." },
    { hop: "pr", say: "Answered: back to 501." },
    { act: "pr", rel: "scm:merge", think: true, say: "Merges it by squashing: scm:merge." },
    { hop: "ci", say: "Enters the CI wrapper by bookmark, {jenkins}." },
    { to: "reply2", rel: "ci:build", think: true, say: "Asks for a build of the merged commit: ci:build." },
    { wait: "reply2", say: "Rests while Jenkins builds and deploys the preview." },
    { to: "build", rel: "hr:in-reply-to", think: true, say: "Follows the answer to the build: hr:in-reply-to." },
    { to: "smoke", rel: "ci:smoke-test", think: true, say: "Picks up the smoke script the build links." },
    { to: "build", say: "" },
    { to: "preview", rel: "ci:deployment", think: true, say: "Runs it on the deployment the build links." },
    { to: "loans", rel: "library:search-loans", say: "The smoke test walks the library: search loans…" },
    { to: "loan", rel: "item", say: "…open a loan, renew it…" },
    { to: "loans", say: "" },
    { to: "preview", say: "…5 of 5 scenarios pass." },
    { hop: "dev", say: "Enters Dev by bookmark, {dev}." },
    { to: "env", rel: "env:get-environment", think: true, say: "Opens environment 1." },
    { to: "promo", rel: "env:promote", think: true, say: "Promotes the merged commit, citing 5 of 5." },
  ];

  function citySvg() {
    const out = [];
    for (const d of DISTRICTS) {
      const c = [P(d.x, d.y), P(d.x + d.w, d.y), P(d.x + d.w, d.y + d.d), P(d.x, d.y + d.d)];
      const [lx, ly] = P(d.x + 0.2, d.y + d.d - 0.1);
      out.push(`<g class="district ${d.hue}"><polygon points="${pts(c)}"/><text x="${lx}" y="${ly + 14}" transform="rotate(-30 ${lx} ${ly + 14})">${esc(d.name)}</text></g>`);
    }
    for (const [a, b, rel, alt] of STREETS) {
      const [x1, y1] = P(...centre(a)), [x2, y2] = P(...centre(b));
      out.push(`<g class="street${alt ? " alt" : ""}" data-rel="${rel}"><line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"/><line class="paint" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"/></g>`);
    }
    // Street names go on top of the buildings, so none is hidden.
    const names = STREETS.map(([a, b, rel, alt, dy = 0]) => {
      const [x1, y1] = P(...centre(a)), [x2, y2] = P(...centre(b));
      return `<g class="street-name${alt ? " alt" : ""}" data-rel="${rel}"><text x="${(x1 + x2) / 2}" y="${(y1 + y2) / 2 + 4 + dy}" text-anchor="middle">${esc(rel)}</text></g>`;
    });
    const order = Object.keys(B).sort((a, b) => (B[a].x + B[a].y) - (B[b].x + B[b].y));
    for (const k of order) {
      const { x, y, h, label, dim } = B[k];
      const top = [P(x, y, h), P(x + BW, y, h), P(x + BW, y + BW, h), P(x, y + BW, h)];
      const left = [P(x, y + BW), P(x + BW, y + BW), P(x + BW, y + BW, h), P(x, y + BW, h)];
      const right = [P(x + BW, y), P(x + BW, y + BW), P(x + BW, y + BW, h), P(x + BW, y, h)];
      const [tx, ty] = P(x + BW / 2, y + BW / 2, h);
      out.push(`<g class="bldg${dim ? " dim" : ""}" data-b="${k}"><polygon class="r" points="${pts(right)}"/><polygon class="l" points="${pts(left)}"/><polygon class="t" points="${pts(top)}"/>` +
        `<text x="${tx}" y="${ty - 8}" text-anchor="middle">${esc(label)}</text></g>`);
    }
    out.push(...names);
    out.push('<path class="hop-arc" d=""/>');
    out.push('<g class="walker"><ellipse class="shadow" rx="7" ry="3.5"/><g class="body"><circle class="head" r="5" cy="-20"/><path d="M-5 -14 h10 l2 12 h-14 z"/></g>' +
      '<g class="bubble"><rect x="8" y="-52" width="66" height="22" rx="11"/><text x="41" y="-37" text-anchor="middle"></text></g></g>');
    return `<svg class="city-svg" viewBox="0 0 790 425" role="img" aria-label="The release as a city: ARTIE walking from resource to resource">${out.join("")}</svg>`;
  }

  function mountCity(root) {
    const fallback = root.innerHTML;
    const R = runner();
    root.innerHTML = `<div class="city-bar"><button type="button" data-run="walk">▶ The walk, with Claude</button><button type="button" data-run="replay">▶ The replay, no model</button><span class="city-cap" aria-live="polite">Press a button: ARTIE walks the release.</span></div>${citySvg()}<p class="city-tally"></p>`;
    const svg = root.querySelector("svg"), walker = svg.querySelector(".walker"), body = walker.querySelector(".body"), shadow = walker.querySelector(".shadow");
    const bubble = walker.querySelector(".bubble"), bubbleText = bubble.querySelector("text"), arc = svg.querySelector(".hop-arc");
    const cap = root.querySelector(".city-cap"), tally = root.querySelector(".city-tally");
    let pos = centre("forge");
    const place = ([x, y], z = 0) => {
      const [gx, gy] = P(x, y), [bx, by] = P(x, y, z);
      shadow.setAttribute("cx", gx); shadow.setAttribute("cy", gy);
      body.setAttribute("transform", `translate(${bx} ${by})`);
      bubble.setAttribute("transform", `translate(${bx} ${by})`);
    };
    const say = (t, cls) => {
      bubble.setAttribute("class", "bubble on " + (cls || "")); bubbleText.textContent = t;
      const w = Math.max(48, t.length * 6.6 + 18);
      bubble.querySelector("rect").setAttribute("width", w); bubbleText.setAttribute("x", 8 + w / 2);
    };
    const hush = () => bubble.setAttribute("class", "bubble");
    place(pos);
    function tween(g, from, to, ms, hop) {
      return new Promise((ok, no) => {
        const t0 = performance.now();
        const step = (now) => {
          if (!R.live(g)) return no(new Error("cancelled"));
          const t = Math.min(1, (now - t0) / ms), e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
          const p = [from[0] + (to[0] - from[0]) * e, from[1] + (to[1] - from[1]) * e];
          place(p, hop ? Math.sin(Math.PI * t) * 3.2 : 0);
          if (t < 1) requestAnimationFrame(step); else ok();
        };
        requestAnimationFrame(step);
      });
    }
    async function run(mode) {
      const g = R.start();
      const w = (ms) => R.wait(g, ms);
      const fast = mode === "replay" ? 0.55 : 1;
      root.dataset.run = mode;
      svg.querySelectorAll(".walked, .acted, .lit").forEach((el) => el.classList.remove("walked", "acted", "lit"));
      tally.textContent = ""; hush(); arc.setAttribute("d", "");
      pos = centre("forge"); place(pos);
      let choices = 0;
      try {
        for (const s of ROUTE) {
          if (s.say) cap.textContent = s.say;
          const target = s.to || s.hop || s.act || s.at || s.wait;
          if (s.think) {
            choices++;
            if (mode === "walk") { say("Claude?", "think"); await w(900); say(s.rel, "think"); await w(600); }
            else { say("pin ✓", "pin"); await w(320); }
            hush();
          }
          if (s.at) { svg.querySelector(`[data-b="${s.at}"]`).classList.add("lit"); await w(900 * fast); continue; }
          if (s.wait) { say("zZ", "zzz"); await w(1500 * fast); say("✉ answered", "pin"); await w(700 * fast); hush(); continue; }
          if (s.act) { svg.querySelector(`[data-b="${s.act}"]`).classList.add("acted"); say("merged", "pin"); await w(900 * fast); hush(); continue; }
          const to = centre(target);
          if (s.hop) {
            const [x1, y1] = P(...pos), [x2, y2] = P(...to), [mx, my] = P((pos[0] + to[0]) / 2, (pos[1] + to[1]) / 2, 4.4);
            arc.setAttribute("d", `M${x1} ${y1} Q${mx} ${my} ${x2} ${y2}`);
            await tween(g, pos, to, 1100 * fast, true);
          } else {
            if (s.rel) svg.querySelectorAll(`[data-rel="${s.rel}"]`).forEach((el) => el.classList.add("walked"));
            await tween(g, pos, to, 900 * fast, false);
          }
          pos = to;
          svg.querySelector(`[data-b="${target}"]`).classList.add("lit");
        }
        say("released", "pin");
        cap.textContent = mode === "walk"
          ? "Walked once: Claude chose at every crossing, " + choices + " choices. In HAL-FORMS that walk took 28 model calls."
          : "Replayed: the same streets, each offer held to its pin, " + choices + " choices made with no model.";
        tally.innerHTML = mode === "walk" ? '<span class="tok">asks the model at each crossing</span>' : '<span class="zero">0 model calls</span>';
      } catch (e) { /* cancelled */ }
    }
    root.addEventListener("click", (e) => { const b = e.target.closest("[data-run]"); if (b && b !== root) run(b.dataset.run); });
    return () => { R.stop(); root.innerHTML = fallback; delete root.dataset.run; };
  }

  function mount(slide) {
    const stops = [];
    slide.querySelectorAll(".sra").forEach((el) => stops.push(mountSra(el)));
    slide.querySelectorAll(".city").forEach((el) => stops.push(mountCity(el)));
    return stops.length ? () => stops.forEach((s) => s()) : null;
  }
  window.deckCode = window.deckCode || {};
  window.deckCode[me] = mount;
})();
