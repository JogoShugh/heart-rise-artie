// Steps through a picture slide, for heart-deck: code on demand (deck:code). A picture is plain SVG and HTML that
// reads whole with no script. Each element with data-at="n m" shows from step n on, and stands out at the steps it
// names; the root's data-steps says how many steps, data-captions what each one says.
(function () {
  "use strict";
  const me = document.currentScript && document.currentScript.src;

  function one(root) {
    const n = +root.dataset.steps;
    let caps = [];
    try { caps = JSON.parse(root.dataset.captions || "[]"); } catch (e) { caps = []; }
    const items = [...root.querySelectorAll("[data-at]")].map((el) => {
      const steps = el.dataset.at.split(/\s+/).map(Number);
      return { el, steps, first: Math.min(...steps) };
    });
    const bar = document.createElement("div");
    bar.className = "pic-bar";
    bar.innerHTML = '<button type="button" class="pic-play" data-do="play">▶ Play</button>' +
      '<button type="button" data-do="back" aria-label="Back a step">‹</button>' +
      '<button type="button" data-do="next" aria-label="On a step">›</button>' +
      '<button type="button" data-do="all">All</button><span class="pic-where"></span><span class="pic-cap"></span>';
    root.prepend(bar);
    root.classList.add("pic-on");
    let at = 0, timer = null;
    const play = bar.querySelector(".pic-play");

    function show(k) {
      at = Math.max(0, Math.min(n, k));
      root.dataset.step = at;
      for (const it of items) {
        it.el.classList.toggle("pic-future", at < it.first);
        it.el.classList.toggle("pic-now", it.steps.includes(at));
      }
      bar.querySelector(".pic-where").textContent = at + " / " + n;
      bar.querySelector(".pic-cap").textContent = at ? caps[at - 1] || "" : root.dataset.intro || "";
    }
    function stop() { clearInterval(timer); timer = null; play.textContent = "▶ Play"; }
    function start() {
      if (timer) return stop();
      if (at >= n) show(0);
      play.textContent = "❚❚ Pause";
      timer = setInterval(() => { if (at >= n) stop(); else show(at + 1); }, 1900);
    }
    bar.addEventListener("click", (e) => {
      const b = e.target.closest("button");
      if (!b) return;
      if (b.dataset.do === "play") start();
      else { stop(); show(b.dataset.do === "all" ? n : at + (b.dataset.do === "next" ? 1 : -1)); }
    });
    show(0);
    return { root, bar, stop, start, step: (d) => { stop(); show(at + d); } };
  }

  function mount(slide) {
    const pics = [...slide.querySelectorAll(".pic[data-steps]")].map(one);
    if (!pics.length) return null;
    // Keys the deck does not use: space plays, , and . step.
    const keys = (e) => {
      if (!pics[0].root.isConnected || e.target.matches("input, select, textarea, button")) return;
      if (e.key === " ") { e.preventDefault(); pics.forEach((p) => p.start()); }
      if (e.key === ".") pics.forEach((p) => p.step(1));
      if (e.key === ",") pics.forEach((p) => p.step(-1));
    };
    document.addEventListener("keydown", keys);
    return function unmount() {
      document.removeEventListener("keydown", keys);
      for (const p of pics) {
        p.stop(); p.bar.remove(); p.root.classList.remove("pic-on"); delete p.root.dataset.step;
        p.root.querySelectorAll(".pic-future, .pic-now").forEach((el) => el.classList.remove("pic-future", "pic-now"));
      }
    };
  }

  window.deckCode = window.deckCode || {};
  window.deckCode[me] = mount;
})();
