// Builds a conformance report for a HEART client: the shared conformance scenarios it runs, from the TRX files
// of one suite run (xUnit or any VSTest runner, steps as Reqnroll writes them) and its feature files.
//   node build-conformance-report.mjs OUT.html "Title" "Lede" FEATURES_DIR ORIGINALS_DIR TRX...
// FEATURES_DIR: the client's copies of the features; ORIGINALS_DIR: artie/implementations/conformance/features,
// so a scenario the client does not hold is named. Tests outside the features: a class named *Architecture* is an
// architecture rule; any other is a unit test.
import fs from "node:fs";
import path from "node:path";

const [out, pageTitle, pageLede, featuresDir, originalsDir, ...trxFiles] = process.argv.slice(2);

const unxml = (s) => s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, "&");
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const attrs = (tag) => Object.fromEntries([...tag.matchAll(/(\w+)="([^"]*)"/g)].map(([, k, v]) => [k, unxml(v)]));
const seconds = (d) => { const [h, m, s] = d.split(":").map(Number); return h * 3600 + m * 60 + s; };
const norm = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, "");

// Features: title, description, scenario headings in order, from the files the suite runs.
const features = fs.readdirSync(featuresDir).filter((f) => f.endsWith(".feature")).map((file) => {
  const text = fs.readFileSync(path.join(featuresDir, file), "utf8");
  const lines = text.split("\n");
  const title = lines.find((l) => /^Feature:/.test(l)).replace(/^Feature:\s*/, "").trim();
  const descr = [];
  for (let i = lines.findIndex((l) => /^Feature:/.test(l)) + 1; i < lines.length; i++) {
    if (/^\s*(Background|Scenario|Rule|@)/.test(lines[i])) break;
    if (lines[i].trim()) descr.push(lines[i].trim());
  }
  const headings = lines.map((l) => l.match(/^\s*Scenario(?: Outline)?:\s*(.*)$/)).filter(Boolean).map((m) => m[1].trim());
  return { file, title, descr: descr.join(" "), headings, cases: [] };
});
const byClass = new Map(features.map((f) => [norm(f.title) + "feature", f]));

// The originals' headings, to name any scenario the suite does not hold.
const left = [];
for (const f of features) {
  const original = path.join(originalsDir, f.file.replace(/([a-z])([A-Z])/g, "$1-$2").toLowerCase());
  if (!fs.existsSync(original)) continue;
  const heads = fs.readFileSync(original, "utf8").split("\n").map((l) => l.match(/^\s*Scenario(?: Outline)?:\s*(.*)$/)).filter(Boolean).map((m) => m[1].trim());
  heads.filter((h) => !f.headings.includes(h)).forEach((h) => left.push({ feature: f.title, file: path.basename(original), heading: h }));
}

// Results.
const arch = [];
let first = null, last = null;
for (const file of trxFiles) {
  const xml = fs.readFileSync(file, "utf8");
  const classes = new Map([...xml.matchAll(/<UnitTest name="[^"]*"[^>]*id="([^"]+)">\s*(?:<[^>]+>\s*)*?<TestMethod [^>]*className="([^"]+)"/g)].map((m) => [m[1], m[2].split(".").pop()]));
  for (const m of xml.matchAll(/<UnitTestResult ([^>]*[^/>])>([\s\S]*?)<\/UnitTestResult>|<UnitTestResult ([^>]*)\/>/g)) {
    const a = attrs(m[1] || m[3]);
    const body = m[2] || "";
    const start = new Date(a.startTime), end = new Date(a.endTime);
    if (!first || start < first) first = start;
    if (!last || end > last) last = end;
    const cls = classes.get(a.testId) || "";
    const feature = byClass.get(norm(cls));
    const stdout = unxml((body.match(/<StdOut>([\s\S]*?)<\/StdOut>/) || [, ""])[1]);
    const error = unxml((body.match(/<Message>([\s\S]*?)<\/Message>/) || [, ""])[1]);
    const result = { name: a.testName, outcome: a.outcome, secs: seconds(a.duration), stdout, error };
    if (!feature) { arch.push({ ...result, cls }); continue; }
    // An outline row: "Title <x>(x: "v", __pickleIndex: "8", exampleTags: [])".
    const row = a.testName.match(/^(.*?)\((.*)\)$/s);
    if (row && /__pickleIndex/.test(row[2])) {
      const params = Object.fromEntries([...row[2].matchAll(/(\w+): "((?:[^"\\]|\\.)*)"/g)].map(([, k, v]) => [k, v.replace(/\\"/g, '"')]));
      const pickle = Number(params.__pickleIndex);
      delete params.__pickleIndex;
      const heading = row[1].trim();
      const shown = heading.replace(/<(\w+)>/g, (t, k) => (k in params ? params[k] : t));
      feature.cases.push({ ...result, heading, shown, params, pickle });
    } else {
      feature.cases.push({ ...result, heading: a.testName, shown: a.testName, params: null, pickle: -1 });
    }
  }
}
for (const f of features) {
  const at = (h) => { const i = f.headings.indexOf(h); return i < 0 ? 999 : i; };
  f.cases.sort((x, y) => at(x.heading) - at(y.heading) || x.pickle - y.pickle);
}
features.sort((a, b) => b.cases.length - a.cases.length);

// Steps of one case, from Reqnroll's trace: each keyword line, its table, and the binding that ran it.
const steps = (stdout) => {
  const out = [];
  for (const line of stdout.split("\n")) {
    const kw = line.match(/^(Given|When|Then|And|But)\s+(.*)$/);
    if (kw) { out.push({ kw: kw[1], text: kw[2], table: [], doc: [], done: null }); continue; }
    const cur = out[out.length - 1];
    if (!cur) continue;
    const done = line.match(/^-> (done|error|skipped|pending)[^:]*:?\s*(.*?)(?:\s*\(([\d.]+)s\))?$/);
    if (done) { cur.done = { status: done[1], binding: done[2], secs: done[3] }; continue; }
    if (/^\s*\|/.test(line)) cur.table.push(line.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim()));
    else if (/--- (table|multiline) step argument ---/.test(line)) continue;
    else if (line.trim()) cur.doc.push(line.replace(/^\s{2}/, ""));
  }
  return out;
};

const cases = features.flatMap((f) => f.cases);
// A test method shown as words: CoreDependsOnNoFramework is "Core depends on no framework".
const words = (n) => { const w = n.split(".").pop().replace(/_/g, " ").replace(/([a-z0-9])([A-Z])/g, "$1 $2").toLowerCase(); return w.charAt(0).toUpperCase() + w.slice(1); };
const unit = arch.filter((a) => !/Architecture/.test(a.cls));
const rules = arch.filter((a) => /Architecture/.test(a.cls));
const passed = cases.filter((c) => c.outcome === "Passed").length + arch.filter((c) => c.outcome === "Passed").length;
const total = cases.length + arch.length;
const headingsRun = features.reduce((n, f) => n + new Set(f.cases.map((c) => c.heading)).size, 0);
const slow = [...cases].sort((a, b) => b.secs - a.secs).slice(0, 3);
const median = (() => { const s = cases.map((c) => c.secs).sort((a, b) => a - b); return s[Math.floor(s.length / 2)]; })();
const fmt = (s) => (s < 1 ? `${Math.round(s * 1000)} ms` : `${s.toFixed(1)} s`);
const when = first ? first.toISOString() : "";

const stepHtml = (s) => {
  const table = s.table.length ? `<table class="gt">${s.table.map((r, i) => `<tr>${r.map((c) => (i === 0 ? `<th>${esc(c)}</th>` : `<td>${esc(c)}</td>`)).join("")}</tr>`).join("")}</table>` : "";
  const doc = s.doc.length ? `<pre class="gd">${esc(s.doc.join("\n"))}</pre>` : "";
  const st = s.done ? s.done.status : "unknown";
  const text = esc(s.text).replace(/&quot;(.*?)&quot;/g, '<span class="gs">&quot;$1&quot;</span>');
  return `<li class="st ${st}"><span class="kw">${s.kw}</span> ${text}${s.done && s.done.binding ? `<span class="bind" title="the step definition that ran it">${esc(s.done.binding)}</span>` : ""}${table}${doc}</li>`;
};
const caseHtml = (c) => {
  const ok = c.outcome === "Passed";
  const params = c.params && Object.keys(c.params).filter((k) => k !== "exampleTags").length
    ? `<div class="params">${Object.entries(c.params).filter(([k]) => k !== "exampleTags").map(([k, v]) => `<span><b>${esc(k)}</b> ${esc(v)}</span>`).join("")}</div>` : "";
  return `<details class="case ${ok ? "ok" : "bad"}"><summary><span class="badge">${ok ? "✓ passed" : "✗ " + esc(c.outcome.toLowerCase())}</span><span class="cn">${esc(c.shown)}</span><span class="dur">${fmt(c.secs)}</span></summary>${params}<ol class="steps">${steps(c.stdout).map(stepHtml).join("")}</ol>${c.error ? `<pre class="err">${esc(c.error)}</pre>` : ""}</details>`;
};
const featureHtml = (f) => {
  const ok = f.cases.every((c) => c.outcome === "Passed");
  const heads = new Set(f.cases.map((c) => c.heading)).size;
  return `<details class="feature ${ok ? "ok" : "bad"}" id="${esc(norm(f.title))}"><summary><span class="fbadge">${ok ? "✓" : "✗"}</span><span class="ft">${esc(f.title)}</span><span class="fc">${heads} scenario${heads === 1 ? "" : "s"} · ${f.cases.length} case${f.cases.length === 1 ? "" : "s"} · <code>${esc(f.file)}</code></span></summary><p class="fd">${esc(f.descr)}</p>${f.cases.map(caseHtml).join("")}</details>`;
};

const leftHtml = left.length ? `<section class="left"><h2>Left out of the suite</h2><p class="sub">In the shared conformance files, not in heart-cs: the feature linter (gplint) refuses them as written, a When after a Then.</p><ul>${left.map((l) => `<li><b>${esc(l.heading)}</b> <span class="muted">(${esc(l.file)})</span></li>`).join("")}</ul></section>` : "";

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(pageTitle)}</title>
<meta name="description" content="${esc(pageLede)}">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:opsz,wght@7..72,650&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>
:root { --bg:#f6f5f1; --card:#fff; --ink:#1d211f; --muted:#5e6762; --line:#e2e0d8; --pass-bg:#dcefe0; --pass-edge:#2e7d4f;
  --fail-bg:#f8d7dc; --fail-edge:#b3261e; --chip:#ecebe5; --kw:#8a3f8f; --str:#2f6f3f; --code:#f0efe9; color-scheme: light; }
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) { --bg:#141716; --card:#1c201e; --ink:#e6e9e7; --muted:#9aa39e; --line:#2c312e;
  --pass-bg:#1d3326; --pass-edge:#5cc28a; --fail-bg:#3d1d20; --fail-edge:#ef7a73; --chip:#262b28; --kw:#d59ad9; --str:#8fd19e; --code:#202522; color-scheme: dark; } }
:root[data-theme="dark"] { --bg:#141716; --card:#1c201e; --ink:#e6e9e7; --muted:#9aa39e; --line:#2c312e;
  --pass-bg:#1d3326; --pass-edge:#5cc28a; --fail-bg:#3d1d20; --fail-edge:#ef7a73; --chip:#262b28; --kw:#d59ad9; --str:#8fd19e; --code:#202522; color-scheme: dark; }
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--ink); font:15px/1.5 "IBM Plex Sans", system-ui, sans-serif; }
main { max-width:1180px; margin:0 auto; padding:32px 16px 64px; }
h1 { font-family:Literata, Georgia, serif; font-size:32px; margin:0 0 6px; }
h2 { font-family:Literata, Georgia, serif; font-size:19px; margin:28px 0 4px; }
.lede, .sub, .muted { color:var(--muted); } .lede { max-width:680px; margin:0 0 12px; } .sub { margin:0 0 12px; font-size:13px; }
code, .bind, .dur, pre { font-family:"IBM Plex Mono", ui-monospace, monospace; }
.bar { display:flex; justify-content:flex-end; gap:6px; align-items:center; font-size:12px; margin:8px 0; flex-wrap:wrap; }
.bar span { letter-spacing:.08em; text-transform:uppercase; color:var(--muted); margin-right:4px; font-weight:600; font-size:11px; }
.bar button { font:inherit; border:1px solid var(--line); background:var(--card); color:var(--ink); border-radius:6px; padding:4px 10px; cursor:pointer; }
.bar button[aria-pressed="true"] { background:var(--ink); color:var(--bg); }
.tiles { display:grid; grid-template-columns:repeat(auto-fit, minmax(170px, 1fr)); gap:10px; }
.tile { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:12px 14px; }
.tile.pass { background:var(--pass-bg); border-left:4px solid var(--pass-edge); }
.tile.fail { background:var(--fail-bg); border-left:4px solid var(--fail-edge); }
.tile .k { font-size:11px; letter-spacing:.08em; text-transform:uppercase; font-weight:600; color:var(--muted); }
.tile .v { font-family:Literata, Georgia, serif; font-size:24px; font-weight:650; margin:2px 0; }
.tile .n { font-size:13px; color:var(--muted); }
.tile .x { font-size:12px; border-top:1px solid var(--line); margin-top:8px; padding-top:6px; }
.chips { display:flex; flex-wrap:wrap; gap:4px; margin-top:4px; } .chips a { font:12px "IBM Plex Mono", monospace; background:var(--chip); border-radius:4px; padding:1px 6px; color:var(--ink); text-decoration:none; }
details > summary { cursor:pointer; list-style:none; } details > summary::-webkit-details-marker { display:none; }
.feature { background:var(--card); border:1px solid var(--line); border-left:4px solid var(--pass-edge); border-radius:8px; margin:10px 0; }
.feature.bad { border-left-color:var(--fail-edge); }
.feature > summary { display:flex; gap:10px; align-items:baseline; padding:12px 14px; flex-wrap:wrap; }
.fbadge { color:var(--pass-edge); font-weight:600; } .feature.bad .fbadge { color:var(--fail-edge); }
.ft { font-weight:600; flex:1 1 360px; } .fc { font-size:13px; color:var(--muted); }
.fd { margin:0 14px 10px; font-size:13px; color:var(--muted); max-width:860px; }
.case { border-top:1px solid var(--line); }
.case > summary { display:flex; gap:10px; align-items:baseline; padding:8px 14px; }
.case > summary:hover { background:var(--code); }
.badge { font:12px "IBM Plex Mono", monospace; background:var(--pass-bg); color:var(--pass-edge); border-radius:4px; padding:1px 6px; white-space:nowrap; }
.case.bad .badge { background:var(--fail-bg); color:var(--fail-edge); }
.cn { flex:1; min-width:0; overflow-wrap:anywhere; } .dur { font-size:12px; color:var(--muted); white-space:nowrap; }
.params { display:flex; flex-wrap:wrap; gap:6px; padding:4px 14px 0 48px; font-size:12px; }
.params span { background:var(--chip); border-radius:4px; padding:1px 6px; overflow-wrap:anywhere; } .params b { font-weight:600; margin-right:4px; }
.steps { margin:6px 14px 12px 48px; padding:0; list-style:none; font-size:14px; }
.st { padding:3px 0; overflow-wrap:anywhere; } .kw { color:var(--kw); font-weight:600; } .gs { color:var(--str); }
.st.error .kw, .st.error { color:var(--fail-edge); }
.bind { display:block; font-size:11px; color:var(--muted); margin-left:2px; }
.gt { border-collapse:collapse; margin:4px 0 4px 2px; font:12px "IBM Plex Mono", monospace; display:block; overflow-x:auto; max-width:100%; }
.gt th, .gt td { border:1px solid var(--line); padding:2px 8px; text-align:left; white-space:nowrap; } .gt th { background:var(--code); }
.gd, .err { background:var(--code); border-radius:6px; padding:8px; font-size:12px; overflow-x:auto; margin:4px 0; }
.err { color:var(--fail-edge); }
.left ul { margin:0; padding-left:20px; } .left li { margin:4px 0; }
.arch { font-size:14px; } .arch li { margin:2px 0; }
@media (max-width:600px) { .steps, .params { margin-left:14px; padding-left:0; } h1 { font-size:26px; } }
</style>
</head>
<body>
<main>
<h1>${esc(pageTitle)}</h1>
<p class="lede">${esc(pageLede)}</p>
<div class="bar"><span>Features</span><button data-open="1">Open all</button><button data-open="0">Close all</button><span style="margin-left:12px">Theme</span><button data-theme-set="light">Light</button><button data-theme-set="dark">Dark</button><button data-theme-set="system">System</button></div>
<h2>Test results</h2>
<p class="sub">Judged by the test runner: green when every case passed.</p>
<div class="tiles">
  <div class="tile ${passed === total ? "pass" : "fail"}"><div class="k">Outcome</div><div class="v">${passed === total ? "✓" : "✗"} ${passed} / ${total}</div><div class="n">tests passed</div><div class="x">${cases.length} scenario cases, ${rules.length} architecture rules, ${unit.length} unit test${unit.length === 1 ? "" : "s"}</div></div>
  <div class="tile ${left.length ? "" : "pass"}"><div class="k">Coverage of the shared suite</div><div class="v">${headingsRun} / ${headingsRun + left.length}</div><div class="n">scenarios, word for word</div><div class="x">${left.length ? `${left.length} left out, refused by the feature linter (below)` : "none left out"}</div></div>
  <div class="tile ${rules.every((a) => a.outcome === "Passed") ? "pass" : "fail"}"><div class="k">Architecture</div><div class="v">${rules.filter((a) => a.outcome === "Passed").length} / ${rules.length}</div><div class="n">rules held</div><div class="x">${rules.map((a) => esc(words(a.name))).join("; ")}</div></div>
</div>
<h2>Statistics</h2>
<p class="sub">Facts about the run; nothing to pass or fail.</p>
<div class="tiles">
  <div class="tile"><div class="k">Features</div><div class="v">${features.length}</div><div class="chips">${features.map((f) => `<a href="#${esc(norm(f.title))}">${esc(f.file.replace(".feature", ""))} ${f.cases.length}</a>`).join("")}</div></div>
  <div class="tile"><div class="k">Cases</div><div class="v">${cases.length}</div><div class="n">${headingsRun} scenarios, outlines counted by their Examples rows</div></div>
  <div class="tile"><div class="k">Duration</div><div class="v">${first && last ? fmt((last - first) / 1000) : "?"}</div><div class="n"><time datetime="${when}" class="local">${when}</time></div></div>
  <div class="tile"><div class="k">Per case</div><div class="v">median ${fmt(median)}</div><div class="n">slowest: ${slow.map((c) => `${esc(c.shown.slice(0, 60))}${c.shown.length > 60 ? "…" : ""} (${fmt(c.secs)})`).join("; ")}</div></div>
</div>
<h2>Scenarios, by feature</h2>
<p class="sub">Largest first. A feature opens to its cases; a case opens to its steps.</p>
${features.map(featureHtml).join("\n")}
${leftHtml}
<section><h2>Architecture rules and unit tests</h2><ul class="arch">${[...rules, ...unit].map((a) => `<li>${a.outcome === "Passed" ? "✓" : "✗"} ${esc(words(a.name))} <span class="muted">(${esc(a.cls)})</span></li>`).join("")}</ul></section>
</main>
<script>
(() => {
  const root = document.documentElement;
  const store = { get: () => { try { return localStorage.getItem("hcs-theme"); } catch { return null; } }, set: (v) => { try { localStorage.setItem("hcs-theme", v); } catch {} } };
  const apply = (v) => { if (v === "light" || v === "dark") root.dataset.theme = v; else delete root.dataset.theme;
    document.querySelectorAll("[data-theme-set]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.themeSet === (v || "system")))); };
  apply(store.get() || "light");
  document.querySelectorAll("[data-theme-set]").forEach((b) => b.addEventListener("click", () => { store.set(b.dataset.themeSet); apply(b.dataset.themeSet); }));
  document.querySelectorAll("[data-open]").forEach((b) => b.addEventListener("click", () => document.querySelectorAll("details.feature").forEach((d) => { d.open = b.dataset.open === "1"; })));
  document.querySelectorAll("time.local").forEach((t) => { const d = new Date(t.dateTime); if (!isNaN(d)) t.textContent = d.toLocaleString(undefined, { year: "numeric", month: "short", day: "numeric", hour: "numeric", minute: "2-digit", timeZoneName: "short" }); });
})();
</script>
</body>
</html>
`;
fs.writeFileSync(out, html);
console.log(`wrote ${out}: ${features.length} features, ${cases.length} cases, ${arch.length} architecture, ${left.length} left out, ${passed}/${total} passed`);
for (const f of features) { const un = f.cases.filter((c) => !c.stdout).length; if (un) console.log(`  ${f.file}: ${un} cases without steps`); }
const unmatched = arch.filter((a) => !/Architecture/.test(a.cls)); if (unmatched.length) console.log("  unit test classes:", [...new Set(unmatched.map((a) => a.cls))].join(", "));
