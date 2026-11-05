// Builds the deck as HEART resources a static host can serve, and the page that reads them with heart-ts.
//   node build.mjs [--artifact <file>]
// slides/*.yaml, in file order, become deck/<name>.json: the slide's HTML and part as state, and links to the
// slides before and after, to the contents, and to a plain HTML page of itself. deck/contents.json embeds them
// all. A static host sends no Link header, so each names its profile in its own links (SPEC §6.1).
// deck/plain/ holds the same deck as an old-school JSON API: the data alone, and a list of names.
// --artifact also writes the page without its document skeleton, as an Artifact publishes it.
import { build } from "esbuild";
import { copyFileSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "../..");
const out = join(repo, "docs/reports/overheard-at-riverside");
const PROFILE = "https://w3id.org/heart-rise/hal-schema-forms";
const BRAND = "Overheard at Riverside";

const slides = readdirSync(join(here, "slides")).filter((f) => f.endsWith(".yaml")).sort()
  .map((f) => parse(readFileSync(join(here, "slides", f), "utf8")));
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

slides.forEach((s, i) => {
  const prev = slides[i - 1], next = slides[i + 1];
  const resource = {
    name: s.name, title: s.title, part: s.part, fiction: s.fiction === true, number: i + 1, of: slides.length, html: s.html,
    _links: {
      self: { href: `${s.name}.json` },
      profile: { href: PROFILE },
      contents: { href: "contents.json", title: "Contents" },
      ...(prev ? { prev: link(prev) } : {}),
      ...(next ? { next: link(next) } : {}),
      alternate: { href: `${s.name}.html`, type: "text/html", title: "This slide as a plain page" },
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
  items: slides.map((s) => ({ name: s.name, title: s.title, part: s.part, fiction: s.fiction === true })),
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
    item: slides.map((s) => ({ name: s.name, part: s.part, fiction: s.fiction === true, _links: { self: link(s) } })),
  },
}));
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
const media = join(repo, "docs/reports/the-web-that-wasnt/media");
const used = new Set(slides.flatMap((s) => [...s.html.matchAll(/media\/([\w.-]+)/g)].map((m) => m[1])));
for (const f of used) copyFileSync(join(media, f), join(out, "media", f));

const at = process.argv.indexOf("--artifact");
if (at > 0) {
  const page = readFileSync(join(here, "page/index.html"), "utf8")
    .replace(/<!doctype html>\s*<html[^>]*><head><meta charset="utf-8"><meta name="viewport"[^>]*>\s*/i, "")
    .replace(/<\/head><body>\s*/i, "\n").replace(/<\/body><\/html>\s*$/i, "");
  writeFileSync(process.argv[at + 1], page);
}
console.log(`${slides.length} slides, ${used.size} media files, into ${out}`);
