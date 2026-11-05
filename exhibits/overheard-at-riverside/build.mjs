// Builds the deck as HEART resources a static host can serve, and the page that reads them with heart-ts.
//   node build.mjs [--artifact <file>]
// slides/*.yaml, in file order, become deck/<name>.json: the slide's HTML and part as state, and links to the
// slides before and after, to the contents, and to a plain HTML page of itself. deck/contents.json embeds them
// all. A static host sends no Link header, so each names its profile in its own links (SPEC §6.1).
// deck/plain/ holds the same deck as an old-school JSON API: the data alone, and a list of names.
// --artifact also writes the page without its document skeleton, as an Artifact publishes it.
import { build } from "esbuild";
import { createHash } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "../..");
const out = join(repo, "docs/reports/overheard-at-riverside");
const PROFILE = "https://w3id.org/heart-rise/hal-schema-forms";
const BRAND = "Overheard at Riverside";

// A scene, told two ways (deck.css, data-voice): its conversation under its scene title, or its points under a
// title that names the topic. Both are in the slide's HTML. A scene's first slide opens with a preamble: what it is
// like to be there, shown either way.
const shaped = (s) => {
  let html = s.html;
  if (s.topic) html = html.replace(/<h2>(.*?)<\/h2>/, (_, title) => `<h2 class="scene">${title}</h2><h2 class="topic">${s.topic}</h2>`);
  if (s.preamble) {
    const tone = s.preamble.tone ? ` ${s.preamble.tone}` : "";
    const aside = `<aside class="preamble${tone}"><span class="label">${s.preamble.label}</span><p>${s.preamble.text}</p></aside>\n`;
    const end = html.search(/<\/h2>(?!<h2)/) + "</h2>".length;
    html = `${html.slice(0, end)}\n${aside}${html.slice(end)}`;
  }
  if (s.points) {
    const list = `<ul class="points">${s.points.map((p) => `<li>${p}</li>`).join("")}</ul>\n`;
    const at = html.indexOf('<div class="script">');
    html = at < 0 ? html + list : html.slice(0, at) + list + html.slice(at);
  }
  return { ...s, html };
};
const slides = readdirSync(join(here, "slides")).filter((f) => f.endsWith(".yaml")).sort()
  .map((f) => shaped(parse(readFileSync(join(here, "slides", f), "utf8"))));
const names = new Set(slides.map((s) => s.name));
if (names.size !== slides.length) throw new Error("two slides share a name");
if (new Set(slides.map((s) => s.title)).size !== slides.length) throw new Error("two slides share a title");

const link = (s, extra = {}) => ({ href: `${s.name}.json`, title: s.title, ...extra });
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
const json = (value) => `${JSON.stringify(value, null, 2)}\n`;

rmSync(join(out, "deck"), { recursive: true, force: true });
mkdirSync(join(out, "deck/plain"), { recursive: true });
mkdirSync(join(out, "media"), { recursive: true });

// A slide as a plain page: the same HTML, its part, and plain links on. What a reader without the client follows.
const plainPage = (s, prev, next) => `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(s.title)} · ${BRAND}</title>
<base href="../">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:ital,opsz,wght@0,7..72,500;0,7..72,650;1,7..72,500&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500;600&display=swap">
<link rel="stylesheet" href="deck.css">
</head><body>
<header class="bar"><div class="bar-in"><div class="brand"><b>${BRAND}</b><span>A fictional conversation · plain page</span></div></div></header>
<div class="deck" style="grid-template-columns: minmax(0, 1fr)"><main class="stage">
<article class="slide"><header><span class="part">${esc(s.part)}</span>${s.fiction ? '<span class="fiction">Fiction</span>' : ""}</header>
${s.html}</article>
<nav class="foot">${prev ? `<a href="deck/${prev.name}.html" rel="prev"><small>Back</small><b>${esc(prev.title)}</b></a>` : '<span class="off"><small>Back</small><b>—</b></span>'}
<span class="where"><a href="deck/contents.html">Contents</a> · <a href="index.html#${s.name}">with heart-ts</a></span>
${next ? `<a href="deck/${next.name}.html" rel="next" data-rel="next"><small>Next</small><b>${esc(next.title)}</b></a>` : '<span class="off"><small>Next</small><b>—</b></span>'}</nav>
</main></div></body></html>
`;

// Code on demand (Fielding, dissertation §5.1.7): a slide may link a script that extends the client, here an
// animation. The link carries the script's digest; the browser runs it only if the bytes match. The page knows no
// script by name, and a client that skips the link (plain JSON has none) shows the slide as it is.
rmSync(join(out, "code"), { recursive: true, force: true });
mkdirSync(join(out, "code"), { recursive: true });
const codeLink = (s) => {
  if (!s.code) return {};
  const bytes = readFileSync(join(here, "code", s.code.file));
  copyFileSync(join(here, "code", s.code.file), join(out, "code", s.code.file));
  const integrity = `sha256-${createHash("sha256").update(bytes).digest("base64")}`;
  return {
    curies: [{ name: "deck", href: "../rels.html#{rel}", templated: true }],
    "deck:code": { href: `../code/${s.code.file}`, type: "text/javascript", title: s.code.title, integrity },
  };
};

slides.forEach((s, i) => {
  const prev = slides[i - 1], next = slides[i + 1];
  const resource = {
    name: s.name, title: s.title, ...(s.topic ? { topic: s.topic } : {}), part: s.part, fiction: s.fiction === true,
    number: i + 1, of: slides.length, html: s.html,
    _links: {
      self: { href: `${s.name}.json` },
      profile: { href: PROFILE },
      contents: { href: "contents.json", title: "Contents" },
      ...(prev ? { prev: link(prev) } : {}),
      ...(next ? { next: link(next) } : {}),
      alternate: { href: `${s.name}.html`, type: "text/html", title: "This slide as a plain page" },
      ...codeLink(s),
    },
  };
  writeFileSync(join(out, "deck", `${s.name}.json`), json(resource));
  writeFileSync(join(out, "deck", `${s.name}.html`), plainPage(s, prev, next));
  // The same slide as an old-school JSON API serves it: the data, and no links, no profile.
  const { _links: _, ...data } = resource;
  writeFileSync(join(out, "deck/plain", `${s.name}.json`), json(data));
});

// The old-school list: what a plain JSON client needs to know the slides exist, in order. Where each one
// is (deck/plain/{name}.json) it has to know from elsewhere, as a client of forms.yaml does.
writeFileSync(join(out, "deck/plain/contents.json"), json({
  title: BRAND,
  items: slides.map((s) => ({ name: s.name, title: s.title, ...(s.topic ? { topic: s.topic } : {}), part: s.part, fiction: s.fiction === true })),
}));

writeFileSync(join(out, "deck/contents.json"), json({
  title: BRAND,
  fiction: "A fictional conversation. The people are invented; the library, its rules, every screen and every number come from running demos.",
  _links: {
    self: { href: "contents.json" },
    profile: { href: PROFILE },
    first: link(slides[0]),
    alternate: { href: "contents.html", type: "text/html", title: "The deck as plain pages" },
  },
  _embedded: {
    item: slides.map((s) => ({ name: s.name, ...(s.topic ? { topic: s.topic } : {}), part: s.part, fiction: s.fiction === true, _links: { self: link(s) } })),
  },
}));
// What the deck's own relation means, where its CURIE points.
writeFileSync(join(out, "rels.html"), `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Relations · ${BRAND}</title><link rel="stylesheet" href="deck.css"></head><body>
<div class="deck" style="grid-template-columns: minmax(0, 1fr)"><main class="stage"><article class="slide">
<h1>The deck's relations</h1>
<h2 id="code">deck:code</h2>
<p>A script the client may run to extend what it can show for this slide: code on demand, REST's one optional constraint (Fielding, dissertation, §5.1.7). <code>integrity</code> is the script's SHA-256; a browser runs it only if the bytes match. A client that does not run it shows the slide as written.</p>
</article></main></div></body></html>
`);
writeFileSync(join(out, "deck/contents.html"), `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Contents · ${BRAND}</title><base href="../"><link rel="stylesheet" href="deck.css"></head><body>
<div class="deck" style="grid-template-columns: minmax(0, 1fr)"><main class="stage"><article class="slide">
<h1>${BRAND}</h1><p class="dek">A fictional conversation, as plain pages. <a href="index.html">Read it with heart-ts</a>.</p>
<ol>${slides.map((s) => `<li><a href="deck/${s.name}.html">${esc(s.title)}</a> <span class="note">${esc(s.part)}${s.fiction ? " · fiction" : ""}</span></li>`).join("")}</ol>
</article></main></div></body></html>
`);

// heart-ts, as written, for the browser: its two Node-only needs met by small shims.
await build({
  entryPoints: [join(repo, "implementations/heart-ts/src/index.ts")], bundle: true, format: "iife", globalName: "HeartTs",
  platform: "browser", target: "es2020", minify: true, legalComments: "none",
  alias: { "node:crypto": join(here, "browser/crypto.js") }, inject: [join(here, "browser/buffer.js")],
  banner: { js: "// heart-ts (implementations/heart-ts), bundled for the browser by exhibits/overheard-at-riverside/build.mjs" },
  outfile: join(out, "heart-ts.js"), logLevel: "warning",
});

for (const f of ["index.html", "deck.css", "deck.js"]) copyFileSync(join(here, "page", f), join(out, f));
for (const f of ["json-view.js", "json-view.css", "theme-switch.js"]) copyFileSync(join(repo, "docs/reports/assets", f), join(out, f));
// The deck's own screens, then the ones it shares with the report.
const media = [join(here, "media"), join(repo, "docs/reports/the-web-that-wasnt/media")];
const used = new Set(slides.flatMap((s) => [...s.html.matchAll(/media\/([\w.-]+)/g)].map((m) => m[1])));
for (const f of used) copyFileSync(join(media.find((dir) => existsSync(join(dir, f))) ?? media[0], f), join(out, "media", f));

const at = process.argv.indexOf("--artifact");
if (at > 0) {
  const page = readFileSync(join(here, "page/index.html"), "utf8")
    .replace(/<!doctype html>\s*<html[^>]*><head><meta charset="utf-8"><meta name="viewport"[^>]*>\s*/i, "")
    .replace(/<\/head><body>\s*/i, "\n").replace(/<\/body><\/html>\s*$/i, "");
  writeFileSync(process.argv[at + 1], page);
}
console.log(`${slides.length} slides, ${used.size} media files, into ${out}`);
