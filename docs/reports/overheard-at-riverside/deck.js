// The deck's client, heart-ts, reading the deck one of two ways, as the switch at the top says.
// HEART: the page knows one address, the contents. The outline comes from what the contents embeds, and every
// move follows a link the last resource offered (next, prev, contents, item); the page builds no slide address.
// Plain JSON: the same slides as an old-school JSON API, data only. heart-ts reads them as it reads any answer
// that names no profile: state, and no controls. So the page has to know the list, build each address from
// deck/plain/{name}.json, and find Back and Next by counting, as a client of forms.yaml would.
(function () {
  "use strict";
  var slide = document.getElementById("slide");
  var list = document.getElementById("outline-list");
  var source = document.getElementById("source");
  var modeNote = document.getElementById("mode-note");
  var items = [];          // the slides, in story order: { name, title, part, fiction, href }
  var atContents = false;  // whether the client holds the contents now, or a slide
  var shown = null;        // the slide on the stage
  var client, way;

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

  var esc = function (s) { return String(s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); };
  var address = function (path) { return new URL(path, document.baseURI).toString(); };
  var itemNamed = function (name) { return items.find(function (i) { return i.name === name; }); };
  var linkOf = function (rep, rel) { return rep.links.find(function (l) { return l.rel === rel; }); };
  var withContents = function (step) {
    var there = atContents ? Promise.resolve() : client.follow("contents").then(readContents);
    return there.then(step);
  };

  var WAYS = {
    heart: {
      bookmark: address("deck/contents.json"),
      client: function () { return new HeartTs.HeartClient(); },
      items: function (rep) {
        return rep.embedded.filter(function (e) { return e.rel === "item"; }).map(function (e) {
          return { name: e.state.name, title: e.title, part: e.state.part, fiction: e.state.fiction, href: new URL(e.href, rep.url).toString() };
        });
      },
      first: function () { return client.follow("first"); },
      to: function (item) { return withContents(function () { return client.follow("item", item.title); }); },
      neighbor: function (rep, rel) {
        var link = linkOf(rep, rel), href = link && new URL(link.href, rep.url).toString();
        return link && (items.find(function (i) { return i.href === href; }) || { name: "", title: link.title });
      },
      step: function (rel) { return client.follow(rel); },
      note: "<b>HEART</b> The page knows one address. It follows the links each slide offers.",
      read: "heart-ts followed a link the last answer offered.",
    },
    plain: {
      bookmark: address("deck/plain/contents.json"),
      client: function () { return new HeartTs.HeartClient({ dialect: "json" }); },
      items: function (rep) { return rep.state.items.map(function (i) { return Object.assign({}, i); }); },
      first: function () { return WAYS.plain.to(items[0]); },
      // No link came with it: the page builds the address from a pattern it knows.
      to: function (item) { return client.enter(address("deck/plain/" + encodeURIComponent(item.name) + ".json")); },
      neighbor: function (rep, rel) {
        var at = items.findIndex(function (i) { return i.name === rep.state.name; });
        return items[at + (rel === "next" ? 1 : -1)];
      },
      step: function (rel) { return WAYS.plain.to(WAYS.plain.neighbor(shown, rel)); },
      note: "<b>Plain JSON</b> Data only, no links. The page knows the list, builds every address and counts its way on.",
      read: "No links came with it: the page built this address from deck/plain/{name}.json, and found Back and Next by counting the list.",
    },
  };

  function readContents(rep) {
    atContents = true;
    items = way.items(rep);
    var html = "", part = null;
    items.forEach(function (item, i) {
      if (item.part !== part) { part = item.part; html += '<li class="group">' + esc(part) + "</li>"; }
      html += '<li><a href="#' + esc(item.name) + '" data-name="' + esc(item.name) + '"><span class="n">' + (i + 1) + "</span>" +
        "<span>" + esc(item.title) + "</span>" + (item.fiction ? '<span class="f">Fiction</span>' : "") + "</a></li>";
    });
    list.innerHTML = html;
  }

  function footLink(rep, rel, label) {
    var to = way.neighbor(rep, rel);
    if (!to) return '<span class="off" data-rel="' + rel + '"><small>' + label + "</small><b>—</b></span>";
    return '<a href="#' + esc(to.name) + '" data-rel="' + rel + '"><small>' + label + "</small><b>" + esc(to.title || to.name) + "</b></a>";
  }

  function showSource(rep) {
    source.innerHTML = "";
    (exchanges.get(rep.url) || Promise.resolve(null)).then(function (seen) {
      if (shown === rep) drawSource(rep, seen);
    });
  }

  function drawSource(rep, seen) {
    var alternate = linkOf(rep, "alternate");
    if (seen) {
      var lead = document.createElement("div");
      var asked = document.createElement("pre");
      asked.className = "asked";
      asked.textContent = "GET " + new URL(rep.url).pathname.split("/").slice(-3).join("/") + "\n" + seen.asked.join("\n") +
        "\n\n" + seen.status + (seen.etag ? "   ETag: " + seen.etag : "");
      var how = document.createElement("p");
      how.className = "note";
      how.textContent = way.read + " heart-ts read it as " + (rep.dialect === "json" ? "data only." : rep.dialect + ".");
      lead.append(how, asked);
      source.append(JsonView.box({ a: JSON.parse(seen.text), labelA: "the answer's body", lead: lead,
        summary: "This slide's resource <small>· what heart-ts asked, and read</small>" }));
    }
    if (alternate) {
      var plain = document.createElement("a");
      plain.className = "plain";
      plain.href = new URL(alternate.href, rep.url).toString();
      plain.textContent = (alternate.title || "Plain page") + " ↗";
      source.append(plain);
    }
  }

  function render(rep) {
    atContents = false;
    shown = rep;
    var s = rep.state;
    slide.innerHTML = '<header><span class="part">' + esc(s.part) + "</span>" + (s.fiction ? '<span class="fiction">Fiction</span>' : "") +
      "</header>" + s.html;
    document.querySelector(".foot").innerHTML = footLink(rep, "prev", "Back") +
      '<span class="where">' + s.number + " / " + s.of + "</span>" + footLink(rep, "next", "Next");
    list.querySelectorAll("a").forEach(function (a) {
      if (a.dataset.name === s.name) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
    });
    document.title = s.title + " · Overheard at Riverside";
    showSource(rep);
  }

  // One move: a request through heart-ts, then the slide it read on the stage, and its name in the address.
  function move(step, how) {
    slide.classList.add("loading");
    return step().then(function (rep) {
      render(rep);
      var hash = "#" + rep.state.name;
      if (how === "push") history.pushState(null, "", hash);
      else if (how === "replace") history.replaceState(null, "", hash);
      var top = document.querySelector(".stage").getBoundingClientRect().top;
      if (top < 0) window.scrollTo({ top: window.scrollY + top - 70 });
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

  // Enter at the way's bookmark; then the slide named, or the first.
  function start(mode, wanted) {
    way = WAYS[mode];
    client = way.client();
    shown = null;
    document.querySelectorAll("[data-mode]").forEach(function (b) { b.setAttribute("aria-pressed", String(b.dataset.mode === mode)); });
    document.documentElement.dataset.mode = mode;
    modeNote.innerHTML = way.note;
    try { localStorage.setItem("overheard-mode", mode); } catch (e) { /* kept for this visit only */ }
    return client.enter(way.bookmark).then(function (rep) {
      readContents(rep);
      if (itemNamed(wanted)) return goTo(wanted, "replace");
      return move(function () { return way.first(); }, "replace");
    }).catch(function (error) {
      slide.innerHTML = '<h2>The deck could not be read</h2><p class="note">' + esc(error.message) +
        '</p><p><a href="deck/contents.html">Read it as plain pages</a></p>';
    });
  }

  document.querySelector(".foot").addEventListener("click", function (event) {
    var a = event.target.closest("a[data-rel]");
    if (!a) return;
    event.preventDefault();
    follow(a.dataset.rel);
  });
  document.addEventListener("keydown", function (event) {
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    if (event.target.closest && event.target.closest("input, textarea, select")) return;
    if (event.key === "ArrowRight") follow("next");
    if (event.key === "ArrowLeft") follow("prev");
  });
  // The outline's links are plain anchors: the address changes, and the deck goes there.
  window.addEventListener("hashchange", function () { goTo(decodeURIComponent(location.hash.slice(1))); });
  document.querySelectorAll("[data-mode]").forEach(function (button) {
    button.addEventListener("click", function () {
      if (WAYS[button.dataset.mode] !== way) start(button.dataset.mode, shown && shown.state.name);
    });
  });

  var toggle = document.getElementById("outline-toggle"), outline = document.getElementById("outline");
  toggle.addEventListener("click", function () {
    var open = outline.classList.toggle("open");
    toggle.setAttribute("aria-expanded", String(open));
  });
  list.addEventListener("click", function () { outline.classList.remove("open"); toggle.setAttribute("aria-expanded", "false"); });

  // How each scene is told: its conversation, or its points as plain bullets. Every scene's HTML holds both.
  function voice(told) {
    document.documentElement.dataset.voice = told;
    document.querySelectorAll("button[data-voice]").forEach(function (b) {
      b.setAttribute("aria-pressed", String(b.dataset.voice === told));
    });
    try { localStorage.setItem("overheard-voice", told); } catch (e) { /* kept for this visit only */ }
  }
  document.querySelectorAll("button[data-voice]").forEach(function (button) {
    button.addEventListener("click", function () { voice(button.dataset.voice); });
  });
  var told = "talk";
  try { told = localStorage.getItem("overheard-voice") === "points" ? "points" : "talk"; } catch (e) { /* the conversation, then */ }
  voice(told);

  var kept = "heart";
  try { kept = localStorage.getItem("overheard-mode") === "plain" ? "plain" : "heart"; } catch (e) { /* HEART, then */ }
  start(kept, decodeURIComponent(location.hash.slice(1)));
})();
