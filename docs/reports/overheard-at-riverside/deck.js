// The deck's client. It knows one address, the contents, and reads everything else through heart-ts: the outline
// from what the contents embeds, each slide by following a link the last resource offered (next, prev, contents,
// item). The page holds no list of slides and builds no slide address of its own.
(function () {
  "use strict";
  var BOOKMARK = new URL("deck/contents.json", document.baseURI).toString();
  var client = new HeartTs.HeartClient();
  var slide = document.getElementById("slide");
  var list = document.getElementById("outline-list");
  var source = document.getElementById("source");
  var where = document.getElementById("where");
  var items = [];          // what the contents embeds: one per slide, in story order
  var atContents = false;  // whether the client holds the contents now, or a slide
  var shown = null;        // the slide on the stage

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
  var resolved = function (href, base) { return new URL(href, base).toString(); };
  var itemNamed = function (name) { return items.find(function (i) { return i.state.name === name; }); };
  var itemAt = function (url) { return items.find(function (i) { return resolved(i.href, BOOKMARK) === url; }); };
  var linkOf = function (rep, rel) { return rep.links.find(function (l) { return l.rel === rel; }); };

  function readContents(rep) {
    atContents = true;
    items = rep.embedded.filter(function (e) { return e.rel === "item"; });
    var html = "", part = null;
    items.forEach(function (item, i) {
      if (item.state.part !== part) { part = item.state.part; html += '<li class="group">' + esc(part) + "</li>"; }
      html += '<li><a href="#' + esc(item.state.name) + '" data-name="' + esc(item.state.name) + '"><span class="n">' + (i + 1) + "</span>" +
        "<span>" + esc(item.title) + "</span>" + (item.state.fiction ? '<span class="f">Fiction</span>' : "") + "</a></li>";
    });
    list.innerHTML = html;
  }

  function footLink(rep, rel, label) {
    var link = linkOf(rep, rel), item = link && itemAt(resolved(link.href, rep.url));
    if (!link) return '<span class="off" data-rel="' + rel + '"><small>' + label + "</small><b>—</b></span>";
    var name = item ? item.state.name : "";
    return '<a href="#' + esc(name) + '" data-rel="' + rel + '"><small>' + label + "</small><b>" + esc(link.title || name) + "</b></a>";
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
      var lead = document.createElement("pre");
      lead.className = "asked";
      lead.textContent = "GET " + new URL(rep.url).pathname.split("/").slice(-2).join("/") + "\n" + seen.asked.join("\n") +
        "\n\n" + seen.status + (seen.etag ? "   ETag: " + seen.etag : "");
      source.append(JsonView.box({ a: JSON.parse(seen.text), labelA: "the answer's body", lead: lead,
        summary: "This slide's resource <small>· what heart-ts asked, and read</small>" }));
    }
    if (alternate) {
      var plain = document.createElement("a");
      plain.className = "plain";
      plain.href = resolved(alternate.href, rep.url);
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
      '<span class="where" id="where">' + s.number + " / " + s.of + "</span>" + footLink(rep, "next", "Next");
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

  // To a slide by name: back to the contents, if the client isn't there, then its item.
  function goTo(name, how) {
    var item = itemNamed(name);
    if (!item || (shown && shown.state.name === name)) return Promise.resolve();
    return move(function () {
      var there = atContents ? Promise.resolve() : client.follow("contents").then(readContents);
      return there.then(function () { return client.follow("item", item.title); });
    }, how);
  }

  function follow(rel) {
    if (!shown || !linkOf(shown, rel)) return;
    move(function () { return client.follow(rel); }, "push");
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
  // The outline's links are plain anchors: the address changes, and the deck follows it there.
  window.addEventListener("hashchange", function () { goTo(decodeURIComponent(location.hash.slice(1))); });

  var toggle = document.getElementById("outline-toggle"), outline = document.getElementById("outline");
  toggle.addEventListener("click", function () {
    var open = outline.classList.toggle("open");
    toggle.setAttribute("aria-expanded", String(open));
  });
  list.addEventListener("click", function () { outline.classList.remove("open"); toggle.setAttribute("aria-expanded", "false"); });

  // Enter at the bookmark; then the slide the address names, or the first.
  client.enter(BOOKMARK).then(function (rep) {
    readContents(rep);
    var wanted = decodeURIComponent(location.hash.slice(1));
    if (itemNamed(wanted)) return goTo(wanted, "replace");
    return move(function () { return client.follow("first"); }, "replace");
  }).catch(function (error) {
    slide.innerHTML = '<h2>The deck could not be read</h2><p class="note">' + esc(error.message) +
      '</p><p><a href="deck/contents.html">Read it as plain pages</a></p>';
  });
})();
