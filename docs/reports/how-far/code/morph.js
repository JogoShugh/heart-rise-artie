// Stills that become one another, animated. Grown from Overheard at Riverside's; shared by heart-deck decks: a slide's resource links this
// script under deck:code, with its digest, and the deck's client loads it once and calls what it registers here on
// each slide that links it. The script knows no slide. Each slide's own HTML says what moves:
//   <div class="morph" data-start="page" data-cycles='[{"to": "apart", "label": "…", "head": "…"}, …]'>
//     <div class="mo-stage"><section class="mo-scene" data-scene="page">…</section> …</div>
// Every cycle starts from the start still and becomes its own; each value marked data-token is carried from where it
// was to where it will be, and the slide's heading becomes the cycle's. Then the next cycle, round and round. With
// reduced motion it changes stills without movement, and waits for a cycle to be chosen.
(function () {
  "use strict";
  var me = document.currentScript && document.currentScript.src;
  var HOLD = { start: 2800, end: 6000, again: 1500 };
  var MOVE = 1500, STAGGER = 70, FADE = 500;
  // What a carried value keeps of the place it left, so its ghost looks the same outside it.
  var KEPT = ["display", "paddingTop", "paddingRight", "paddingBottom", "paddingLeft", "textIndent", "overflowWrap",
    "fontFamily", "fontSize", "fontWeight", "fontStyle", "lineHeight", "letterSpacing", "color", "backgroundColor",
    "borderRadius", "borderTop", "borderRight", "borderBottom", "borderLeft", "boxShadow", "textAlign"];

  var STYLE = [
    ".morph.mo-live .mo-stage { position: relative; gap: 0; }",
    ".morph.mo-live .mo-scene { grid-area: 1 / 1; opacity: 0; visibility: hidden; }",
    ".morph.mo-live .mo-scene.on { opacity: 1; visibility: visible; }",
    ".morph.mo-live .mo-title, .morph.mo-live .mo-still { display: none; }",
    ".morph.mo-live.mo-moving .mo-carried { visibility: hidden; }",
    ".mo-layer { position: absolute; inset: 0; pointer-events: none; z-index: 1; }",
    ".mo-fly { position: absolute; left: 0; top: 0; }",
    ".mo-ghost { position: absolute; left: 0; top: 0; margin: 0; box-sizing: border-box; transform-origin: 0 0; }",
    ".mo-bar { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; }",
    ".mo-bar button { font: 600 0.76rem var(--mono); color: var(--muted); background: var(--surface); border: 1px solid var(--rule);",
    "  border-radius: 999px; padding: 0.3rem 0.75rem; cursor: pointer; }",
    ".mo-bar button[aria-pressed=\"true\"] { color: var(--leaf); background: var(--leaf-bg); border-color: var(--leaf); }",
    ".mo-bar .mo-play { margin-left: auto; }",
  ].join("\n");

  function ensureStyle() {
    if (document.getElementById("mo-style")) return;
    var style = document.createElement("style");
    style.id = "mo-style";
    style.textContent = STYLE;
    document.head.append(style);
  }

  // Where a node sits in the stage's own coordinates: a deck shown scaled (present mode) measures in screen pixels,
  // so each measure is divided by the stage's scale.
  function box(node, base) {
    var r = node.getBoundingClientRect(), k = base.k || 1;
    return { x: (r.left - base.left) / k, y: (r.top - base.top) / k, w: r.width / k, h: r.height / k };
  }

  function ghost(node, at) {
    var copy = node.cloneNode(true), seen = getComputedStyle(node);
    copy.removeAttribute("data-token");
    copy.classList.remove("mo-carried");
    copy.classList.add("mo-ghost");
    KEPT.forEach(function (name) { copy.style[name] = seen[name]; });
    copy.style.width = at.w + "px";
    copy.style.height = at.h + "px";
    return copy;
  }

  var settled = function (runs) { return Promise.all(runs.map(function (run) { return run.finished; })); };

  function mount(slide) {
    var root = slide.querySelector(".morph");
    if (!root || !root.animate) return null;
    var cycles;
    try { cycles = JSON.parse(root.dataset.cycles); } catch (e) { return null; }
    ensureStyle();
    var stage = root.querySelector(".mo-stage");
    var scenes = {};
    stage.querySelectorAll(".mo-scene").forEach(function (scene) { scenes[scene.dataset.scene] = scene; });
    var start = root.dataset.start;
    var heading = slide.querySelector("h2"), named = heading.textContent;
    var layer = document.createElement("div");
    layer.className = "mo-layer";
    stage.append(layer);

    var calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var at = start, cycle = 0, playing = !calm, busy = false, timer = 0, runs = [], stopped = false;
    var wait = function (ms) { return new Promise(function (done) { timer = setTimeout(done, ms); }); };

    var bar = document.createElement("div");
    bar.className = "mo-bar";
    bar.setAttribute("role", "group");
    bar.setAttribute("aria-label", "The animation");
    cycles.forEach(function (one, i) {
      var button = document.createElement("button");
      button.type = "button";
      button.dataset.cycle = String(i);
      button.textContent = one.label;
      button.addEventListener("click", function () {
        if (busy) return;
        playing = false; mark(); clearTimeout(timer);
        play(i);
      });
      bar.append(button);
    });
    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "mo-play";
    toggle.addEventListener("click", function () {
      playing = !playing; mark();
      if (busy) return;  // the cycle under way finishes, and goes on or not as playing now says
      clearTimeout(timer);
      if (playing) next(HOLD.again);
    });
    bar.append(toggle);
    stage.after(bar);

    function mark() {
      bar.querySelectorAll("[data-cycle]").forEach(function (b) { b.setAttribute("aria-pressed", String(Number(b.dataset.cycle) === cycle)); });
      toggle.textContent = playing ? "❚❚ Pause" : "▶ Play";
    }

    function head(text) {
      if (!text || heading.textContent === text) return;
      if (calm) { heading.textContent = text; return; }
      var out = heading.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 180, fill: "forwards" });
      out.finished.then(function () {
        if (stopped) return;
        heading.textContent = text;
        heading.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 260 });
        out.cancel();
      }, function () {});
    }

    // Back to the start, with no value carried: the end of one cycle, before the next.
    function fade(to) {
      var from = scenes[at], into = scenes[to];
      if (from === into) return Promise.resolve();
      into.classList.add("on");
      if (calm) { from.classList.remove("on"); at = to; return Promise.resolve(); }
      runs = [from.animate([{ opacity: 1 }, { opacity: 0 }], { duration: FADE, fill: "forwards" }),
        into.animate([{ opacity: 0 }, { opacity: 1 }], { duration: FADE, fill: "both" })];
      return settled(runs).then(function () {
        from.classList.remove("on");
        runs.forEach(function (run) { run.cancel(); });
        runs = [];
        at = to;
      });
    }

    // From the start to a still: the frames cross-fade, and each value flies from where it was to where it will be,
    // its old form fading out as its new one sharpens in.
    function morph(to) {
      var from = scenes[at], into = scenes[to];
      if (calm) { into.classList.add("on"); from.classList.remove("on"); at = to; return Promise.resolve(); }
      var rect = stage.getBoundingClientRect(), order = 0;
      var base = { left: rect.left, top: rect.top, k: stage.offsetWidth ? rect.width / stage.offsetWidth : 1 };
      into.classList.add("on");
      root.classList.add("mo-moving");
      runs = [from.animate([{ opacity: 1 }, { opacity: 0 }], { duration: MOVE * 0.5, fill: "forwards", easing: "ease-out" }),
        into.animate([{ opacity: 0 }, { opacity: 0, offset: 0.45 }, { opacity: 1 }], { duration: MOVE + 300, fill: "both" })];
      from.querySelectorAll("[data-token]").forEach(function (a) {
        var b = into.querySelector('[data-token="' + a.dataset.token + '"]');
        if (!b) return;  // a value with nowhere to go fades with its still
        var ra = box(a, base), rb = box(b, base);
        a.classList.add("mo-carried");
        b.classList.add("mo-carried");
        var fly = document.createElement("div"), old = ghost(a, ra), fresh = ghost(b, rb);
        fly.className = "mo-fly";
        fly.append(old, fresh);
        layer.append(fly);
        var sx = rb.w / ra.w, sy = rb.h / ra.h;
        var timing = { duration: MOVE, delay: order++ * STAGGER, easing: "cubic-bezier(0.65, 0, 0.35, 1)", fill: "both" };
        runs.push(fly.animate([{ transform: "translate(" + ra.x + "px, " + ra.y + "px)" },
          { transform: "translate(" + rb.x + "px, " + rb.y + "px)" }], timing));
        runs.push(old.animate([{ transform: "scale(1, 1)" }, { transform: "scale(" + sx + ", " + sy + ")" }], timing));
        runs.push(fresh.animate([{ transform: "scale(" + 1 / sx + ", " + 1 / sy + ")" }, { transform: "scale(1, 1)" }], timing));
        runs.push(old.animate([{ opacity: 1, filter: "blur(0px)" }, { opacity: 0, filter: "blur(5px)", offset: 0.55 },
          { opacity: 0, filter: "blur(5px)" }], timing));
        runs.push(fresh.animate([{ opacity: 0, filter: "blur(7px)" }, { opacity: 0, filter: "blur(7px)", offset: 0.35 },
          { opacity: 1, filter: "blur(0px)", offset: 0.85 }, { opacity: 1, filter: "blur(0px)" }], timing));
      });
      return settled(runs).then(function () {
        from.classList.remove("on");
        root.classList.remove("mo-moving");
        root.querySelectorAll(".mo-carried").forEach(function (node) { node.classList.remove("mo-carried"); });
        runs.forEach(function (run) { run.cancel(); });
        runs = [];
        layer.textContent = "";
        at = to;
      });
    }

    // One cycle: its heading, the start (faded back to, if a cycle left it), a breath, then the start becomes its still.
    function play(i) {
      busy = true;
      cycle = i;
      mark();
      head(cycles[i].head);
      var ready = at === start ? Promise.resolve() : fade(start).then(function () { return wait(HOLD.again); });
      return ready.then(function () { return stopped ? null : morph(cycles[i].to); }).then(function () {
        busy = false;
        if (playing && !stopped) next(HOLD.end);
      }, function () { busy = false; });
    }

    function next(hold) {
      clearTimeout(timer);
      timer = setTimeout(function () {
        if (stopped || !playing) return;
        if (document.hidden) return next(hold);
        play(at === start ? cycle : (cycle + 1) % cycles.length);
      }, hold);
    }

    root.classList.add("mo-live");
    scenes[start].classList.add("on");
    if (cycles[0].head) heading.textContent = cycles[0].head;
    mark();
    if (playing) timer = setTimeout(function () { play(0); }, HOLD.start);

    return function stop() {
      stopped = true;
      clearTimeout(timer);
      runs.forEach(function (run) { run.cancel(); });
      bar.remove();
      layer.remove();
      root.classList.remove("mo-live", "mo-moving");
      Object.keys(scenes).forEach(function (name) { scenes[name].classList.remove("on"); });
      heading.textContent = named;
    };
  }

  (window.deckCode = window.deckCode || {})[me] = mount;
})();
