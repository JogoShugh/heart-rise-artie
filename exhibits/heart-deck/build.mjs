#!/usr/bin/env node
// heart-deck: builds a deck as static HEART resources, and the page that reads them with heart-ts.
//   node exhibits/heart-deck/build.mjs <deck directory> [--artifact <file>]
//
// The deck directory holds:
//   deck.css        optional: the deck's own styles, after heart-deck's
//   deck.yaml       brand, tagline, description, out (repo-relative), media (dirs), roots (named source roots)
//   slides/*.yaml   one slide each, in file order: name, part, title, kicker, html, say, sources, fragments
//   refs/*.yaml     shared reference notes a slide shows as pills: name, kind, label, title, summary, quote, cite, href
//
// Each slide becomes deck/<name>.json, a HAL resource with next, prev, contents and alternate links. What a slide
// shows on demand travels with it under _embedded:
//   deck:ref       each reference note its HTML names with <deck-ref name="…">, as the note's own resource
//                  (refs/<name>.json), the same one on every slide that names it;
//   deck:fragment  each code fragment its HTML names with <deck-fragment name="…">, read from its file at build
//                  time (from the line holding `from`, to the line starting with `to`, or `count` lines), with a
//                  via link to the file and lines on GitHub.
// A fragment or a note whose marker matches nothing stops the build: a slide never shows code that has drifted.
// deck/contents.json embeds every slide. deck/plain/ holds the same slides as data only, no links, no embedded.
import { build } from "esbuild";
import { createHash } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "../..");
const deckDir = resolve(process.argv[2] ?? ".");
const deck = parse(readFileSync(join(deckDir, "deck.yaml"), "utf8"));
const out = join(repo, deck.out);
const PROFILE = "https://w3id.org/heart-rise/hal-schema-forms";
const BRAND = deck.brand;

const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
const json = (value) => `${JSON.stringify(value, null, 2)}\n`;
const integrity = (text) => `sha256-${createHash("sha256").update(text).digest("base64")}`;

// Named roots a fragment's source is read from: "artie:path" is this repository; others name their own checkout
// and the GitHub address its files are shown at.
const roots = { artie: { dir: repo, web: "https://github.com/JogoShugh/heart-rise-artie/blob/main/" }, ...(deck.roots ?? {}) };
for (const r of Object.values(roots)) r.dir = r.dir.replace(/^~/, process.env.HOME);

// The reference notes, by name.
const refs = new Map();
const refsDir = join(deckDir, "refs");
if (existsSync(refsDir)) {
  for (const f of readdirSync(refsDir).filter((f) => f.endsWith(".yaml")).sort()) {
    for (const r of [parse(readFileSync(join(refsDir, f), "utf8"))].flat()) {
      if (refs.has(r.name)) throw new Error(`two refs are named ${r.name}`);
      refs.set(r.name, r);
    }
  }
}

// A fragment, read from its file.
const fragment = (slide, f) => {
  const [rootName, path] = f.source.includes(":") ? f.source.split(/:(.*)/s) : ["artie", f.source];
  const root = roots[rootName];
  if (!root) throw new Error(`${slide.name}: no root named ${rootName}`);
  const lines = readFileSync(join(root.dir, path), "utf8").replace(/\n$/, "").split("\n");
  const start = f.from ? lines.findIndex((line) => line.includes(f.from)) : 0;
  if (start < 0) throw new Error(`${slide.name}/${f.name}: ${path} has no line holding "${f.from}"`);
  const end = f.count ? start + Number(f.count) - 1
    : f.to ? lines.findIndex((line, i) => i > start && line.trimStart().startsWith(f.to)) : lines.length - 1;
  if (end < start) throw new Error(`${slide.name}/${f.name}: ${path} has no end after "${f.from}"`);
  const taken = lines.slice(start, end + 1);
  const indent = Math.min(...taken.filter((l) => l.trim()).map((l) => l.match(/^ */)[0].length));
  const text = taken.map((l) => l.slice(indent)).join("\n");
  const lineRange = start === 0 && end === lines.length - 1 ? "" : `#L${start + 1}-L${end + 1}`;
  return {
    name: f.name, label: f.label, lang: f.lang ?? path.split(".").pop(), path: `${rootName === "artie" ? "" : rootName + ": "}${path}`,
    lines: `${start + 1}–${end + 1}`, of: lines.length, text,
    _links: {
      self: { href: `fragments/${slide.name}--${f.name}.json`, title: f.label },
      via: { href: `${root.web}${path}${lineRange}`, title: `${path}, lines ${start + 1}–${end + 1}` },
    },
  };
};

const named = (html, tag) => [...new Set([...html.matchAll(new RegExp(`<${tag} name="([\\w-]+)"`, "g"))].map((m) => m[1]))];

const slides = readdirSync(join(deckDir, "slides")).filter((f) => f.endsWith(".yaml")).sort()
  .map((f) => parse(readFileSync(join(deckDir, "slides", f), "utf8")));
if (new Set(slides.map((s) => s.name)).size !== slides.length) throw new Error("two slides share a name");
// heart-ts picks an embedded item by its title, so titles are unique too.
if (new Set(slides.map((s) => s.title)).size !== slides.length) throw new Error("two slides share a title");

rmSync(join(out, "deck"), { recursive: true, force: true });
rmSync(join(out, "refs"), { recursive: true, force: true });
for (const d of ["deck/plain", "deck/fragments", "refs", "media"]) mkdirSync(join(out, d), { recursive: true });

// A reference note as its own resource; the same object is embedded wherever a slide names it.
const refResource = (r) => {
  const { name, kind, label, title, summary, quote, cite, href, date } = r;
  return {
    name, kind, label, title, summary, quote, cite, date,
    _links: {
      self: { href: `../refs/${name}.json`, title: label },
      ...(href ? { describedby: { href, title: cite } } : {}),
    },
  };
};
for (const r of refs.values()) {
  const { _links, ...state } = refResource(r);
  writeFileSync(join(out, "refs", `${r.name}.json`), json({ ...state, _links: { ...(_links.describedby ? { describedby: _links.describedby } : {}), self: { href: `${r.name}.json` }, profile: { href: PROFILE } } }));
}

// Every note in one resource, for the deck's "every pill, any time" overlay.
writeFileSync(join(out, "refs", "index.json"), json({
  title: "Every reference note", _links: { self: { href: "index.json" }, profile: { href: PROFILE } },
  _embedded: { "deck:ref": [...refs.values()].map(refResource) },
}));

const link = (s, extra = {}) => ({ href: `${s.name}.json`, title: s.title, ...extra });

// A slide as a plain page, for a reader with no client: the HTML, pills as their labels, fragments as code.
const plainHtml = (s, embedded) => s.html
  .replace(/<deck-ref name="([\w-]+)"[^>]*>(.*?)<\/deck-ref>/gs, (_, n, text) => `<a class="ref-plain" href="${esc(String(refs.get(n).href ?? "#").replace(/^#(.+)/, "deck/$1.html"))}">${text || esc(refs.get(n).label)}</a>`)
  .replace(/<deck-fragment name="([\w-]+)"[^>]*>(.*?)<\/deck-fragment>/gs, (_, n) => {
    const f = embedded["deck:fragment"].find((x) => x.name === n);
    return `<details class="fragment-plain"><summary>${esc(f.label)}</summary><pre><code>${esc(f.text)}</code></pre></details>`;
  });

const plainPage = (s, html, prev, next) => `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(s.title)} · ${esc(BRAND)}</title>
<base href="../">
<link rel="stylesheet" href="deck.css">${existsSync(join(deckDir, "deck.css")) ? '\n<link rel="stylesheet" href="deck-extra.css">' : ""}
</head><body>
<div class="deck" style="grid-template-columns: minmax(0, 1fr)"><main class="stage">
<article class="slide">${s.kicker ? `<header><span class="part">${esc(s.kicker)}</span></header>` : ""}
${html}</article>
<nav class="foot">${prev ? `<a href="deck/${prev.name}.html" rel="prev"><small>Back</small><b>${esc(prev.title)}</b></a>` : '<span class="off"><small>Back</small><b>—</b></span>'}
<span class="where"><a href="deck/contents.html">Contents</a> · <a href="index.html#${s.name}">with heart-ts</a></span>
${next ? `<a href="deck/${next.name}.html" rel="next"><small>Next</small><b>${esc(next.title)}</b></a>` : '<span class="off"><small>Next</small><b>—</b></span>'}</nav>
</main></div></body></html>
`;

const CURIES = [{ name: "deck", href: "../rels.html#{rel}", templated: true }];

// Code on demand (Fielding, dissertation §5.1.7): a slide may link a script that extends the client, an animation.
// The link carries the script's digest; the browser runs it only if the bytes match. The script is the deck's own
// (code/ in the deck directory) or heart-deck's shared one (code/ here). A client that skips the link shows the slide
// as written.
rmSync(join(out, "code"), { recursive: true, force: true });
mkdirSync(join(out, "code"), { recursive: true });
const codeLink = (s) => {
  if (!s.code) return {};
  const from = [join(deckDir, "code", s.code.file), join(here, "code", s.code.file)].find((f) => existsSync(f));
  if (!from) throw new Error(`${s.name}: no code file ${s.code.file}`);
  const bytes = readFileSync(from);
  copyFileSync(from, join(out, "code", s.code.file));
  return { "deck:code": { href: `../code/${s.code.file}`, type: "text/javascript", title: s.code.title, integrity: integrity(bytes) } };
};
slides.forEach((s, i) => {
  const prev = slides[i - 1], next = slides[i + 1];
  for (const n of named(s.html, "deck-ref")) if (!refs.has(n)) throw new Error(`${s.name}: no ref named ${n}`);
  const declared = new Map((s.fragments ?? []).map((f) => [f.name, f]));
  for (const n of named(s.html, "deck-fragment")) if (!declared.has(n)) throw new Error(`${s.name}: no fragment named ${n}`);
  const embedded = {};
  const usedRefs = named(s.html, "deck-ref");
  if (usedRefs.length) embedded["deck:ref"] = usedRefs.map((n) => refResource(refs.get(n)));
  if (declared.size) embedded["deck:fragment"] = [...declared.values()].map((f) => fragment(s, f));
  for (const f of embedded["deck:fragment"] ?? []) {
    const { _links, ...state } = f;
    writeFileSync(join(out, "deck", f._links.self.href), json({ ...state, _links: { ...f._links, self: { href: f._links.self.href.replace("fragments/", "") }, profile: { href: PROFILE } } }));
  }
  const resource = {
    name: s.name, title: s.title, part: s.part, kicker: s.kicker ?? null, tone: s.tone ?? "light",
    number: i + 1, of: slides.length, html: s.html, say: s.say ?? null, sources: (s.sources ?? []).map(([label, href]) => ({ label, href })),
    _links: {
      self: { href: `${s.name}.json` },
      profile: { href: PROFILE },
      contents: { href: "contents.json", title: "Contents" },
      ...(prev ? { prev: link(prev) } : {}),
      ...(next ? { next: link(next) } : {}),
      alternate: { href: `${s.name}.html`, type: "text/html", title: "This slide as a plain page" },
      ...(Object.keys(embedded).length || s.code ? { curies: CURIES } : {}),
      ...codeLink(s),
    },
    ...(Object.keys(embedded).length ? { _embedded: embedded } : {}),
  };
  writeFileSync(join(out, "deck", `${s.name}.json`), json(resource));
  writeFileSync(join(out, "deck", `${s.name}.html`), plainPage(s, plainHtml(s, embedded), prev, next));
  const { _links: _l, _embedded: _e, ...data } = resource;
  writeFileSync(join(out, "deck/plain", `${s.name}.json`), json(data));
});

writeFileSync(join(out, "deck/plain/contents.json"), json({
  title: BRAND, items: slides.map((s) => ({ name: s.name, title: s.title, part: s.part })),
}));
writeFileSync(join(out, "deck/contents.json"), json({
  title: BRAND, tagline: deck.tagline,
  _links: {
    self: { href: "contents.json" }, profile: { href: PROFILE }, first: link(slides[0]),
    alternate: { href: "contents.html", type: "text/html", title: "The deck as plain pages" },
  },
  _embedded: { item: slides.map((s) => ({ name: s.name, part: s.part, _links: { self: link(s) } })) },
}));
writeFileSync(join(out, "deck/contents.html"), `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Contents · ${esc(BRAND)}</title><base href="../"><link rel="stylesheet" href="deck.css"></head><body>
<div class="deck" style="grid-template-columns: minmax(0, 1fr)"><main class="stage"><article class="slide">
<h1>${esc(BRAND)}</h1><p class="dek">${esc(deck.tagline)}. As plain pages; <a href="index.html">read it with heart-ts</a>.</p>
<ol>${slides.map((s) => `<li><a href="deck/${s.name}.html">${esc(s.title)}</a> <span class="note">${esc(s.part)}</span></li>`).join("")}</ol>
</article></main></div></body></html>
`);

writeFileSync(join(out, "rels.html"), `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Relations · ${esc(BRAND)}</title><link rel="stylesheet" href="deck.css"></head><body>
<div class="deck" style="grid-template-columns: minmax(0, 1fr)"><main class="stage"><article class="slide">
<h1>The deck's relations</h1>
<h2 id="ref">deck:ref</h2>
<p>A reference note the slide shows as a pill: a source's own words, quoted whole, with where they come from (<code>describedby</code>). Each note is one resource, under <code>refs/</code>, embedded whole in every slide that names it, so a client shows it with no further request.</p>
<h2 id="code">deck:code</h2>
<p>A script the client may run to extend what it can show for this slide: code on demand, REST's one optional constraint (Fielding, dissertation, §5.1.7). <code>integrity</code> is the script's SHA-256; a browser runs it only if the bytes match.</p>
<h2 id="fragment">deck:fragment</h2>
<p>A fragment of source code the slide shows on demand, read from its file when the deck was built. <code>via</code> links the file and lines on GitHub.</p>
</article></main></div></body></html>
`);

await build({
  entryPoints: [join(repo, "implementations/heart-ts/src/index.ts")], bundle: true, format: "iife", globalName: "HeartTs",
  platform: "browser", target: "es2020", minify: true, legalComments: "none",
  alias: { "node:crypto": join(here, "browser/crypto.js") }, inject: [join(here, "browser/buffer.js")],
  banner: { js: "// heart-ts (implementations/heart-ts), bundled for the browser by exhibits/heart-deck/build.mjs" },
  outfile: join(out, "heart-ts.js"), logLevel: "warning",
});

const page = readFileSync(join(here, "page/index.html"), "utf8")
  .replaceAll("{{brand}}", esc(BRAND)).replaceAll("{{tagline}}", esc(deck.tagline)).replaceAll("{{description}}", esc(deck.description))
  .replaceAll("{{key}}", esc(deck.name))
  .replaceAll("{{extraCss}}", existsSync(join(deckDir, "deck.css")) ? '<link rel="stylesheet" href="deck-extra.css">' : "");
if (existsSync(join(deckDir, "deck.css"))) copyFileSync(join(deckDir, "deck.css"), join(out, "deck-extra.css"));
writeFileSync(join(out, "index.html"), page);
for (const f of ["deck.css", "deck.js"]) copyFileSync(join(here, "page", f), join(out, f));
for (const f of ["json-view.js", "json-view.css", "theme-switch.js"]) copyFileSync(join(repo, "docs/reports/assets", f), join(out, f));

const mediaDirs = (deck.media ?? ["media"]).map((d) => resolve(deckDir, d));
const used = new Set(slides.flatMap((s) => [...s.html.matchAll(/media\/([\w.-]+)/g)].map((m) => m[1])));
for (const f of used) {
  const dir = mediaDirs.find((d) => existsSync(join(d, f)));
  if (!dir) throw new Error(`no media file ${f}`);
  copyFileSync(join(dir, f), join(out, "media", f));
}

const at = process.argv.indexOf("--artifact");
if (at > 0) {
  writeFileSync(process.argv[at + 1], page.replace(/<!doctype html>\s*<html[^>]*><head><meta charset="utf-8"><meta name="viewport"[^>]*>\s*/i, "")
    .replace(/<\/head><body>\s*/i, "\n").replace(/<\/body><\/html>\s*$/i, ""));
}
console.log(`${slides.length} slides, ${refs.size} refs, ${used.size} media files, into ${out}`);
