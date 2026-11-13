// heart-deck's client: heart-ts, reading a deck one of two ways, as the switch at the top says.
// HEART: the page knows one address, the contents. The outline comes from what the contents embeds, and every move
// follows a link the last resource offered (next, prev, contents, item); the page builds no slide address. What a
// slide shows on demand (deck:ref notes, deck:fragment code) came embedded in it: a click opens it, no request.
// Plain JSON: the same slides, data only. The page knows the list, builds each address and counts its way on; with
// nothing embedded, a pill is only its label.
(function () {
  "use strict";
  var KEY = (document.querySelector('meta[name="heart-deck"]') || {}).content || "deck";
  var BRAND = document.title;
  var root = document.documentElement;
  var slide = document.getElementById("slide");
  var frame = document.getElementById("frame");
  var list = document.getElementById("outline-list");
  var source = document.getElementById("source");
  var say = document.getElementById("say");
  var modeNote = document.getElementById("mode-note");
  var overlay = document.getElementById("overlay");
  var overlayBody = document.getElementById("overlay-body");
  var items = [], atContents = false, shown = null, client, way;

  var remember = function (name, value) { try { localStorage.setItem(KEY + "-" + name, value); } catch (e) { /* this visit only */ } };
  var recall = function (name) { try { return localStorage.getItem(KEY + "-" + name); } catch (e) { return null; } };

  // What the client asked and what came back, by address, to show a slide's own resource as it was read.
  var exchanges = new Map();
  var realFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    var url = new URL(typeof input === "string" ? input : input.url, document.baseURI).toString();
    return realFetch(input, init).then(function (response) {
      exchanges.set(url, response.clone().text().then(function (text) {
        var asked = [];
        new Headers((init && init.headers) || {}).forEach(function (value, name) { asked.push(name + ": " + value); });
        return { asked: asked, status: response.status, etag: response.headers.get("ETag"), text: text };
      }));
      return response;
    });
  };

  var esc = function (s) { return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); };
  var address = function (path) { return new URL(path, document.baseURI).toString(); };
  var itemNamed = function (name) { return items.find(function (i) { return i.name === name; }); };
  var linkOf = function (rep, rel) { return rep.links.find(function (l) { return l.rel === rel; }); };
  var withContents = function (step) { return (atContents ? Promise.resolve() : client.follow("contents").then(readContents)).then(step); };

  var WAYS = {
    heart: {
      bookmark: address("deck/contents.json"),
      client: function () { return new HeartTs.HeartClient(); },
      items: function (rep) {
        return rep.embedded.filter(function (e) { return e.rel === "item"; }).map(function (e) {
          return { name: e.state.name, title: e.title, part: e.state.part, href: new URL(e.href, rep.url).toString() };
        });
      },
      first: function () { return client.follow("first"); },
      to: function (item) { return withContents(function () { return client.follow("item", item.title); }); },
      neighbor: function (rep, rel) {
        var link = linkOf(rep, rel), href = link && new URL(link.href, rep.url).toString();
        return link && (items.find(function (i) { return i.href === href; }) || { name: "", title: link.title });
      },
      step: function (rel) { return client.follow(rel); },
      note: "<b>HEART</b> The page knows one address and follows the links each slide offers. Notes and code come embedded.",
      read: "heart-ts followed a link the last answer offered.",
    },
    plain: {
      bookmark: address("deck/plain/contents.json"),
      client: function () { return new HeartTs.HeartClient({ dialect: "json" }); },
      items: function (rep) { return rep.state.items.map(function (i) { return Object.assign({}, i); }); },
      first: function () { return WAYS.plain.to(items[0]); },
      to: function (item) { return client.enter(address("deck/plain/" + encodeURIComponent(item.name) + ".json")); },
      neighbor: function (rep, rel) {
        var at = items.findIndex(function (i) { return i.name === rep.state.name; });
        return items[at + (rel === "next" ? 1 : -1)];
      },
      step: function (rel) { return WAYS.plain.to(WAYS.plain.neighbor(shown, rel)); },
      note: "<b>Plain JSON</b> Data only: no links, nothing embedded. The page builds every address, and a pill is only its label.",
      read: "No links came with it: the page built this address from deck/plain/{name}.json.",
    },
  };

  function readContents(rep) {
    atContents = true;
    items = way.items(rep);
    var html = "", part = null;
    items.forEach(function (item, i) {
      if (item.part !== part) { part = item.part; html += '<li class="group">' + esc(part) + "</li>"; }
      html += '<li><a href="#' + esc(item.name) + '" data-name="' + esc(item.name) + '"><span class="n">' + (i + 1) + "</span><span>" +
        esc(item.title || item.name) + "</span></a></li>";
    });
    list.innerHTML = html;
  }

  function footLink(rep, rel, label) {
    var to = way.neighbor(rep, rel);
    if (!to) return '<span class="off" data-rel="' + rel + '"><small>' + label + "</small><b>—</b></span>";
    return '<a href="#' + esc(to.name) + '" data-rel="' + rel + '"><small>' + label + "</small><b>" + esc(to.title || to.name) + "</b></a>";
  }

  // What came embedded, by relation and name.
  function embeddedNamed(rep, rel, name) {
    return rep.embedded.find(function (e) { return e.rel === rel && e.state.name === name; });
  }

  // The slide's pills and code chips: each a button over what came embedded, or only its label when nothing did.
  function wire(rep) {
    slide.querySelectorAll("deck-ref").forEach(function (el) {
      var e = embeddedNamed(rep, "deck:ref", el.getAttribute("name"));
      var label = el.textContent.trim() || (e && e.state.label) || el.getAttribute("name");
      var b = document.createElement(e ? "button" : "span");
      b.className = "ref " + (e ? "kind-" + esc(e.state.kind) : "off");
      b.textContent = label;
      if (e) { b.type = "button"; b.title = e.state.title; b.addEventListener("click", function () { openRef(e); }); }
      el.replaceWith(b);
    });
    slide.querySelectorAll("deck-fragment").forEach(function (el) {
      var e = embeddedNamed(rep, "deck:fragment", el.getAttribute("name"));
      var b = document.createElement(e ? "button" : "span");
      b.className = "chip" + (e ? "" : " off");
      b.innerHTML = '<span class="chip-code">&lt;/&gt;</span>' + esc(el.textContent.trim() || (e && e.state.label) || el.getAttribute("name"));
      if (e) { b.type = "button"; b.addEventListener("click", function () { openFragment(e); }); }
      el.replaceWith(b);
    });
  }

  function open(html) {
    overlayBody.innerHTML = html;
    if (overlay.showModal) overlay.showModal(); else overlay.setAttribute("open", "");
    overlayBody.scrollTop = 0;
  }
  overlay.addEventListener("click", function (event) { if (event.target === overlay) overlay.close(); });

  function openRef(e) {
    var s = e.state, more = e.links.find(function (l) { return l.rel === "describedby"; });
    open('<p class="kicker kind-' + esc(s.kind) + '">' + esc(s.label) + "</p>" +
      '<h2 id="overlay-title">' + esc(s.title) + "</h2>" +
      (s.summary ? '<p class="summary">' + esc(s.summary) + "</p>" : "") +
      (s.quote ? '<blockquote class="whole">' + s.quote + "</blockquote>" : "") +
      '<p class="cite">' + (more ? '<a href="' + esc(more.href) + '" target="_blank" rel="noopener">' + esc(s.cite) + " ↗</a>" : esc(s.cite)) +
      (s.date ? " · " + esc(s.date) : "") + "</p>");
  }

  function openFragment(e) {
    var s = e.state, via = e.links.find(function (l) { return l.rel === "via"; });
    open('<p class="kicker">Code · ' + esc(s.lang) + "</p>" +
      '<h2 id="overlay-title">' + esc(s.label) + "</h2>" +
      '<pre class="fragment"><code>' + esc(s.text) + "</code></pre>" +
      '<p class="cite"><code>' + esc(s.path) + "</code>, lines " + esc(s.lines) + " of " + esc(s.of) +
      (via ? ' · <a href="' + esc(via.href) + '" target="_blank" rel="noopener">on GitHub ↗</a>' : "") + "</p>");
  }

  // Code on demand (deck:code), as in Overheard at Riverside: a script the slide links, run only if its digest matches.
  var running = null;
  function runCode(rep) {
    if (running) { running(); running = null; }
    var link = linkOf(rep, "deck:code");
    if (!link) return;
    var src = new URL(link.href, rep.url).toString();
    window.deckCode = window.deckCode || {};
    var start = function () { var mount = window.deckCode[src]; if (mount && shown === rep) running = mount(slide) || null; };
    if (window.deckCode[src]) return start();
    var script = document.createElement("script");
    script.src = src;
    if (link.integrity) script.integrity = link.integrity;
    script.onload = start;
    document.head.append(script);
  }

  function showSource(rep) {
    source.innerHTML = "";
    (exchanges.get(rep.url) || Promise.resolve(null)).then(function (seen) {
      if (shown !== rep || !seen) return;
      var lead = document.createElement("div");
      var asked = document.createElement("pre");
      asked.className = "asked";
      asked.textContent = "GET " + new URL(rep.url).pathname.split("/").slice(-2).join("/") + "\n" + seen.asked.join("\n") +
        "\n\n" + seen.status + (seen.etag ? "   ETag: " + seen.etag : "");
      var how = document.createElement("p");
      how.className = "note";
      how.textContent = way.read + " heart-ts read it as " + (rep.dialect === "json" ? "data only." : rep.dialect + ".");
      lead.append(how, asked);
      source.append(JsonView.box({ a: JSON.parse(seen.text), labelA: "the answer's body", lead: lead,
        summary: "This slide's resource <small>· what heart-ts asked, and read</small>" }));
      var alternate = linkOf(rep, "alternate");
      if (alternate) {
        var plain = document.createElement("a");
        plain.className = "plain";
        plain.href = new URL(alternate.href, rep.url).toString();
        plain.textContent = (alternate.title || "Plain page") + " ↗";
        source.append(plain);
      }
    });
  }

  function render(rep) {
    atContents = false;
    shown = rep;
    var s = rep.state;
    slide.dataset.tone = s.tone || "light";
    var sources = (s.sources || []).map(function (x) { return '<a href="' + esc(x.href) + '" target="_blank" rel="noopener">' + esc(x.label) + "</a>"; });
    slide.innerHTML = (s.kicker ? '<header><span class="part">' + esc(s.kicker) + "</span></header>" : "") + s.html +
      (sources.length ? '<p class="sources"><b>Sources</b> ' + sources.join(" · ") + "</p>" : "") +
      '<span class="num">' + s.number + "</span>";
    wire(rep);
    say.innerHTML = s.say ? "<b>Notes</b> " + esc(s.say) : "";
    document.querySelector(".foot").innerHTML = footLink(rep, "prev", "Back") +
      '<span class="where">' + s.number + " / " + s.of + "</span>" + footLink(rep, "next", "Next");
    list.querySelectorAll("a").forEach(function (a) {
      if (a.dataset.name === s.name) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
    });
    document.title = s.title + " · " + BRAND;
    showSource(rep);
    runCode(rep);
    fit();
  }

  function move(step, how) {
    slide.classList.add("loading");
    return step().then(function (rep) {
      render(rep);
      var hash = "#" + rep.state.name;
      if (how === "push") history.pushState(null, "", hash);
      else if (how === "replace") history.replaceState(null, "", hash);
    }).catch(function (error) {
      slide.innerHTML = '<h2>That could not be read</h2><p class="note">' + esc(error.message) + "</p>";
    }).finally(function () { slide.classList.remove("loading"); });
  }

  function goTo(name, how) {
    var item = itemNamed(name);
    if (!item || (shown && shown.state.name === name)) return Promise.resolve();
    return move(function () { return way.to(item); }, how);
  }

  function follow(rel) {
    if (!shown || !way.neighbor(shown, rel)) return;
    move(function () { return way.step(rel); }, "push");
  }

  function start(mode, wanted) {
    way = WAYS[mode];
    client = way.client();
    shown = null;
    document.querySelectorAll("[data-mode]").forEach(function (b) { b.setAttribute("aria-pressed", String(b.dataset.mode === mode)); });
    root.dataset.mode = mode;
    modeNote.innerHTML = way.note;
    remember("mode", mode);
    return client.enter(way.bookmark).then(function (rep) {
      readContents(rep);
      if (itemNamed(wanted)) return goTo(wanted, "replace");
      return move(function () { return way.first(); }, "replace");
    }).catch(function (error) {
      slide.innerHTML = '<h2>The deck could not be read</h2><p class="note">' + esc(error.message) +
        '</p><p><a href="deck/contents.html">Read it as plain pages</a></p>';
    });
  }

  // The outline: shown or hidden, remembered. Hidden, the slide takes the page.
  var panelToggle = document.getElementById("panel-toggle");
  function panel(shownNow) {
    root.dataset.panel = shownNow ? "shown" : "hidden";
    panelToggle.setAttribute("aria-expanded", String(shownNow));
    remember("panel", shownNow ? "shown" : "hidden");
    fit();
  }
  panelToggle.addEventListener("click", function () { panel(root.dataset.panel === "hidden"); });

  // Present: the slide alone, on a 16:9 canvas scaled to the window, as a slide deck shows it.
  var presentButton = document.getElementById("present");
  function present(on) {
    if (on) { root.dataset.present = "on"; if (root.requestFullscreen) root.requestFullscreen().catch(function () {}); }
    else { delete root.dataset.present; if (document.fullscreenElement) document.exitFullscreen().catch(function () {}); }
    fit();
  }
  presentButton.addEventListener("click", function () { present(root.dataset.present !== "on"); });
  document.addEventListener("fullscreenchange", function () { if (!document.fullscreenElement && root.dataset.present === "on") present(false); });

  var CANVAS_W = 1280, CANVAS_H = 720;
  function fit() {
    if (root.dataset.present !== "on") { slide.style.transform = ""; frame.style.height = ""; return; }
    slide.style.transform = "none";
    var tall = Math.max(CANVAS_H, slide.scrollHeight);
    var scale = Math.min(window.innerWidth / CANVAS_W, window.innerHeight / tall);
    slide.style.transform = "translate(-50%, -50%) scale(" + scale + ")";
  }
  window.addEventListener("resize", fit);

  var notesToggle = document.getElementById("notes-toggle");
  function notes(on) {
    say.hidden = !on;
    notesToggle.setAttribute("aria-pressed", String(on));
    remember("notes", on ? "on" : "off");
  }
  notesToggle.addEventListener("click", function () { notes(say.hidden); });

  document.querySelector(".foot").addEventListener("click", function (event) {
    var a = event.target.closest("a[data-rel]");
    if (!a) return;
    event.preventDefault();
    follow(a.dataset.rel);
  });
  document.addEventListener("keydown", function (event) {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    if (event.target.closest && event.target.closest("input, textarea, select")) return;
    if (overlay.open) return;
    if (event.key === "ArrowRight" || event.key === "PageDown" || (event.key === " " && root.dataset.present === "on")) { event.preventDefault(); follow("next"); }
    if (event.key === "ArrowLeft" || event.key === "PageUp") follow("prev");
    if (event.key === "o" || event.key === "O") panel(root.dataset.panel === "hidden");
    if (event.key === "p" || event.key === "P") present(root.dataset.present !== "on");
    if (event.key === "n" || event.key === "N") notes(say.hidden);
    if (event.key === "Escape" && root.dataset.present === "on") present(false);
  });
  window.addEventListener("hashchange", function () { goTo(decodeURIComponent(location.hash.slice(1))); });
  list.addEventListener("click", function () { if (window.innerWidth < 900) panel(false); });
  document.querySelectorAll("[data-mode]").forEach(function (button) {
    button.addEventListener("click", function () { if (WAYS[button.dataset.mode] !== way) start(button.dataset.mode, shown && shown.state.name); });
  });

  panel(recall("panel") !== "hidden" && window.innerWidth >= 900);
  notes(recall("notes") === "on");
  start(recall("mode") === "plain" ? "plain" : "heart", decodeURIComponent(location.hash.slice(1)));
})();
