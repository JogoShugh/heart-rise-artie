// The Great Divorce, animated. Code on demand for one slide of Overheard at Riverside: the slide's resource links
// this script under deck:code, with its digest, and the deck's client loads it and calls what it registers here.
// It turns the slide's three stills into one stage and plays two cycles, each from Ana's page:
//   The Divorce: the page's information drifts left and becomes plain JSON; its controls drift right and become
//   OpenAPI.
//   The Return: the same page becomes one HAL Schema Forms answer, its information and controls kept together.
// Each value (data-token) is carried from where it was to where it will be. Then round again. With reduced motion
// it changes stills without movement, and waits for a cycle to be chosen.
(function () {
  "use strict";
  var me = document.currentScript && document.currentScript.src;
  var CYCLES = {
    apart: { head: "The Divorce of Information from Controls", label: "The Divorce" },
    return: { head: "The Return of Information and Controls", label: "The Return" },
  };
  var HOLD = { start: 2800, apart: 5600, return: 6000, page: 1500 };
  var MOVE = 1500, STAGGER = 70, FADE = 500;
  // What a carried value keeps of the place it left, so its ghost looks the same outside it.
  var KEPT = ["display", "paddingTop", "paddingRight", "paddingBottom", "paddingLeft", "textIndent", "overflowWrap",
    "fontFamily", "fontSize", "fontWeight", "fontStyle", "lineHeight", "letterSpacing", "color", "backgroundColor", "borderRadius"];

  var STYLE = [
    ".divorce.dv-live .dv-stage { position: relative; gap: 0; }",
    ".divorce.dv-live .dv-scene { grid-area: 1 / 1; opacity: 0; visibility: hidden; }",
    ".divorce.dv-live .dv-scene.on { opacity: 1; visibility: visible; }",
    ".divorce.dv-live .dv-title, .divorce.dv-live .dv-still { display: none; }",
    ".divorce.dv-live.dv-moving [data-token] { visibility: hidden; }",
    ".dv-layer { position: absolute; inset: 0; pointer-events: none; z-index: 1; }",
    ".dv-fly { position: absolute; left: 0; top: 0; }",
    ".dv-ghost { position: absolute; left: 0; top: 0; margin: 0; box-sizing: border-box; transform-origin: 0 0; }",
    ".dv-bar { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; }",
    ".dv-bar button { font: 600 0.76rem var(--mono); color: var(--muted); background: var(--surface); border: 1px solid var(--rule);",
    "  border-radius: 999px; padding: 0.3rem 0.75rem; cursor: pointer; }",
    ".dv-bar button[aria-pressed=\"true\"] { color: var(--leaf); background: var(--leaf-bg); border-color: var(--leaf); }",
    ".dv-bar .dv-play { margin-left: auto; }",
  ].join("\n");

  function ensureStyle() {
    if (document.getElementById("dv-style")) return;
    var style = document.createElement("style");
    style.id = "dv-style";
    style.textContent = STYLE;
    document.head.append(style);
  }

  function box(node, base) {
    var r = node.getBoundingClientRect();
    return { x: r.left - base.left, y: r.top - base.top, w: r.width, h: r.height };
  }

  function ghost(node, at) {
    var copy = node.cloneNode(true), seen = getComputedStyle(node);
    copy.removeAttribute("data-token");
    copy.classList.add("dv-ghost");
    KEPT.forEach(function (name) { copy.style[name] = seen[name]; });
    copy.style.width = at.w + "px";
    copy.style.height = at.h + "px";
    return copy;
  }

  var settled = function (runs) { return Promise.all(runs.map(function (run) { return run.finished; })); };

  function mount(slide) {
    var root = slide.querySelector(".divorce");
    if (!root || !root.animate) return null;
    ensureStyle();
    var stage = root.querySelector(".dv-stage");
    var scenes = {};
    ["page", "apart", "return"].forEach(function (name) { scenes[name] = stage.querySelector('[data-scene="' + name + '"]'); });
    var heading = slide.querySelector("h2"), named = heading.textContent;
    var layer = document.createElement("div");
    layer.className = "dv-layer";
    stage.append(layer);

    var calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var at = "page", cycle = "apart", playing = !calm, busy = false, timer = 0, runs = [], stopped = false;
    var wait = function (ms) { return new Promise(function (done) { timer = setTimeout(done, ms); }); };

    var bar = document.createElement("div");
    bar.className = "dv-bar";
    bar.setAttribute("role", "group");
    bar.setAttribute("aria-label", "The animation");
    Object.keys(CYCLES).forEach(function (name) {
      var button = document.createElement("button");
      button.type = "button";
      button.dataset.cycle = name;
      button.textContent = CYCLES[name].label;
      button.addEventListener("click", function () {
        if (busy) return;
        playing = false; mark(); clearTimeout(timer);
        play(name);
      });
      bar.append(button);
    });
    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "dv-play";
    toggle.addEventListener("click", function () {
      playing = !playing; mark();
      if (busy) return;  // the cycle under way finishes, and goes on or not as playing now says
      clearTimeout(timer);
      if (playing) next(HOLD.page);
    });
    bar.append(toggle);
    stage.after(bar);

    function mark() {
      bar.querySelectorAll("[data-cycle]").forEach(function (b) { b.setAttribute("aria-pressed", String(b.dataset.cycle === cycle)); });
      toggle.textContent = playing ? "❚❚ Pause" : "▶ Play";
    }

    function head(text) {
      if (heading.textContent === text) return;
      if (calm) { heading.textContent = text; return; }
      var out = heading.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 180, fill: "forwards" });
      out.finished.then(function () {
        if (stopped) return;
        heading.textContent = text;
        heading.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 260 });
        out.cancel();
      }, function () {});
    }

    // Back to the page, with no value carried: the end of one cycle, before the next.
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

    // From the page to a still: the frames cross-fade, and each value flies from where it was to where it will be,
    // its old form fading out as its new one sharpens in.
    function morph(to) {
      var from = scenes[at], into = scenes[to];
      if (calm) { into.classList.add("on"); from.classList.remove("on"); at = to; return Promise.resolve(); }
      var base = stage.getBoundingClientRect(), order = 0;
      into.classList.add("on");
      root.classList.add("dv-moving");
      runs = [from.animate([{ opacity: 1 }, { opacity: 0 }], { duration: MOVE * 0.5, fill: "forwards", easing: "ease-out" }),
        into.animate([{ opacity: 0 }, { opacity: 0, offset: 0.45 }, { opacity: 1 }], { duration: MOVE + 300, fill: "both" })];
      from.querySelectorAll("[data-token]").forEach(function (a) {
        var b = into.querySelector('[data-token="' + a.dataset.token + '"]');
        if (!b) return;
        var ra = box(a, base), rb = box(b, base);
        var fly = document.createElement("div"), old = ghost(a, ra), fresh = ghost(b, rb);
        fly.className = "dv-fly";
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
        root.classList.remove("dv-moving");
        runs.forEach(function (run) { run.cancel(); });
        runs = [];
        layer.textContent = "";
        at = to;
      });
    }

    // One cycle: its heading, the page (faded back to, if a cycle left it), a breath, then the page becomes the still.
    function play(name) {
      busy = true;
      cycle = name;
      mark();
      head(CYCLES[name].head);
      var ready = at === "page" ? Promise.resolve() : fade("page").then(function () { return wait(HOLD.page); });
      return ready.then(function () { return stopped ? null : morph(name); }).then(function () {
        busy = false;
        if (playing && !stopped) next(HOLD[name]);
      }, function () { busy = false; });
    }

    function next(hold) {
      clearTimeout(timer);
      timer = setTimeout(function () {
        if (stopped || !playing) return;
        if (document.hidden) return next(hold);
        play(at === "apart" ? "return" : "apart");
      }, hold);
    }

    root.classList.add("dv-live");
    scenes.page.classList.add("on");
    heading.textContent = CYCLES.apart.head;
    mark();
    if (playing) timer = setTimeout(function () { play("apart"); }, HOLD.start);

    return function stop() {
      stopped = true;
      clearTimeout(timer);
      runs.forEach(function (run) { run.cancel(); });
      bar.remove();
      layer.remove();
      root.classList.remove("dv-live", "dv-moving");
      Object.keys(scenes).forEach(function (name) { scenes[name].classList.remove("on"); });
      heading.textContent = named;
    };
  }

  (window.deckCode = window.deckCode || {})[me] = mount;
})();
