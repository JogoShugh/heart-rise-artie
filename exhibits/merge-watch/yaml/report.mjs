// Local Models Below the Goal, in YAML: the merge watch again, its scripts generated as data, run by local models on
// the scratch repository. Built from runs/yaml/summary.yaml and the scripts themselves, beside the first report's look.
//   node exhibits/merge-watch/yaml/report.mjs      writes docs/reports/local-models-yaml/index.html
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "../../..");
const { parse } = createRequire(join(repo, "exhibits/overheard-at-riverside/package.json"))("yaml");
const read = (path) => readFileSync(join(repo, path), "utf8");
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
const n = (x) => Number(x).toLocaleString("en-US");

const runs = parse(read("exhibits/merge-watch/runs/yaml/summary.yaml")).runs;
const first = read("docs/reports/local-models/index.html");
const style = first.slice(first.indexOf("<style>"), first.indexOf("</style>") + "</style>".length);
const script = (path) => read(path).split("\n").filter((l) => !l.startsWith("#")).join("\n").trim();
const builds = { "qwen3:30b-a3b-instruct-2507-q4_K_M": "qwen3 30B-A3B instruct", "qwen3:30b-a3b": "qwen3 30B-A3B thinking" };
const of = (prefix) => runs.filter((r) => r.run.startsWith(prefix));
const range = (rs, key) => {
  const v = rs.map((r) => r[key]).filter((x) => x != null);
  const lo = Math.min(...v), hi = Math.max(...v);
  return lo === hi ? n(lo) : `${n(lo)}–${n(hi)}`;
};
const passed = (rs) => rs.filter((r) => r.merged && r.told).length;

const rows = runs.map((r) => `<tr><th scope="row">${esc(builds[r.model] ?? r.model)}, ${r.run.startsWith("kept") ? "kept" : "intent"}</th>` +
  `<td class="${r.merged ? "pass" : "fail"}">${r.merged ? "yes" : "no"}</td><td class="${r.told ? "pass" : "fail"}">${r.told ? "yes" : "no"}</td>` +
  `<td>${r.calls ?? "?"}</td><td>${r.output != null ? n(r.output) : "?"}</td><td>${n(r.seconds)}</td>` +
  `<td>${[r.stale ? `${r.stale} refused stale` : "", r.unclear ? `asked a question ${r.unclear === 1 ? "once" : `${r.unclear} times`}` : ""].filter(Boolean).join("; ") || "none"}</td></tr>`).join("\n");

const instruct = of("intent-instruct"), thinking = of("intent-thinking"), kept = of("kept");
const page = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Local Models Below the Goal, in YAML</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;600&family=JetBrains+Mono:wght@400&family=Source+Serif+4:ital,wght@0,400;0,600;1,400&display=swap">
${style}
</head><body>
<main>
<header class="masthead">
<p class="kicker">HEART and ARTIE · one laptop, no key · 5 November 2026</p>
<h1>Local Models Below the Goal, in YAML</h1>
<p class="dek">The merge watch again, with its scripts kept as data. Claude ran the goal once; <code>artie generate</code> wrote it as YAML; a 30B model on the laptop ran that YAML ${passed(runs)} times out of ${runs.length}, merging every bump and telling every draft.</p>
</header>

<h2>What changed since the first report</h2>
<ul class="obs">
<li><b>The scripts are data.</b> In <a href="../local-models/">the first report</a> (30 October) the intent and kept scripts were Kotlin, compiled on every run. Here they are YAML, run by <code>artie run</code> as data, with no compiler.</li>
<li><b>Generated, not mocked up.</b> The October goal run kept its watch only as Kotlin, so Claude ran the goal again, and <code>artie generate --format yaml</code> wrote both scripts from that run: <code>--intents</code> for the intent form.</li>
<li><b>The same scene.</b> The scratch repository, a fresh dependency bump that can be merged and a draft that can't, through the HEART gateway; the same two qwen3 30B-A3B builds through Ollama, at 1024 output tokens.</li>
</ul>

<h2>The scripts</h2>
<p>The goal as the person wrote it, and the two forms <code>artie generate</code> made from Claude's run of it. Open one to read it.</p>
<details class="src"><summary>The goal <small>plain text, merge.goal.md</small></summary>
<p>Claude planned it once, asking the model at 17 steps: it merged the bump, gave the draft three minutes, then labelled it and told its author why.</p>
<pre><code>${esc(read("exhibits/merge-watch/merge.goal.md").trim())}</code></pre>
</details>
<details class="src"><summary>The intent script <small>yaml/merge.intent.heart.yaml</small></summary>
<p>The goal's shape as data: the watch, the wait, the give-up. Each act is still in words, for the model to choose from what the gateway offers, on every run.</p>
<pre><code>${esc(script("exhibits/merge-watch/yaml/merge.intent.heart.yaml"))}</code></pre>
</details>
<details class="src"><summary>The kept script <small>yaml/merge.heart.yaml</small></summary>
<p>Every act pinned to what Claude's run chose. Only the two acts after a give-up stay in words, so the model is asked only when a bump can't be merged.</p>
<pre><code>${esc(script("exhibits/merge-watch/yaml/merge.heart.yaml"))}</code></pre>
</details>

<h2>The runs</h2>
<div class="table"><table>
<thead><tr><th scope="col">Model and script</th><th scope="col">Merged</th><th scope="col">Told</th><th scope="col">Model calls</th><th scope="col">Output tokens</th><th scope="col">Seconds</th><th scope="col">Along the way</th></tr></thead>
<tbody>
${rows}
<tr class="ref"><th scope="row">Claude, the goal</th><td>yes</td><td>yes</td><td>17 asked</td><td>not recorded</td><td>589</td><td>the run that wrote the scripts</td></tr>
</tbody></table></div>
<ul class="obs">
<li><b>"Refused stale"</b> is the gateway at work: the pull request changed between ARTIE's read and its act, so the act was refused (412), and ARTIE read again.</li>
<li><b>"Asked a question"</b> is the instruct build answering a label turn with a question instead of a choice. ARTIE refuses an answer in words and looks again next time; on the next look it labelled and told. The thinking build didn't.</li>
</ul>

<h2>Against the first report</h2>
<div class="table"><table>
<thead><tr><th scope="col">Model and script</th><th scope="col">Kotlin, 30 Oct</th><th scope="col">YAML, 5 Nov</th><th scope="col">Calls, Kotlin → YAML</th><th scope="col">Output tokens, Kotlin → YAML</th></tr></thead>
<tbody>
<tr><th scope="row">qwen3 30B-A3B instruct, intent</th><td>9 of 11</td><td class="pass">${passed(instruct)} of ${instruct.length}</td><td>6–11 → ${range(instruct, "calls")}</td><td>320–550 → ${range(instruct, "output")}</td></tr>
<tr><th scope="row">qwen3 30B-A3B thinking, intent</th><td>7 of 9</td><td class="pass">${passed(thinking)} of ${thinking.length}</td><td>6–10 → ${range(thinking, "calls")}</td><td>2,200–4,000 → ${range(thinking, "output")}</td></tr>
<tr><th scope="row">qwen3 30B-A3B instruct, kept</th><td>1 of 1</td><td class="pass">${passed(kept)} of ${kept.length}</td><td>→ ${range(kept, "calls")}</td><td>→ ${range(kept, "output")}</td></tr>
</tbody></table></div>
<p>The model's share is unchanged: the same few calls and the same output per run, and the same one habit of the instruct build after a stale refusal. Runs took longer (${range(runs, "seconds")} seconds against 276–430) because the scratch repository now holds three pages of open bump pull requests, which the watch walks on every look; the format didn't add the time.</p>

<div class="bottom">
<p><b>Intent YAML:</b> ${passed([...instruct, ...thinking])} of ${instruct.length + thinking.length} runs fully passed on the laptop's 30B models.</p>
<p><b>Kept YAML:</b> ${passed(kept)} of ${kept.length}, the model asked only for the label and the sentence to the author.</p>
<p><b>The split still holds, now as data:</b> Claude finds the way once, <code>artie generate</code> keeps it as YAML, and a free local model does the reasoner's part of every run after.</p>
</div>

<h2>Limits, and what the runs turned up</h2>
<ul class="obs">
<li><b>Three runs each.</b> Enough to see the shape again, not to rank the builds.</li>
<li><b>ARTIE looks for the lock file in the wrong place.</b> Its help says NAME.pins.yaml beside the script; it looks in the working folder. Run from <code>exhibits/merge-watch</code>, the YAML scripts held to the October Kotlin pins until the runs named theirs with <code>--pins</code>. A fix, test first, is open.</li>
<li><b>Not counted:</b> a first batch lost to GitHub answering 500 to writes for a few minutes, and runs held to the wrong pins. Their outputs are kept beside these, in <code>runs/yaml/first-batch</code> and <code>runs/yaml/spurious</code>.</li>
<li><b>Claude's token use</b> for the goal run wasn't recorded: the run was stopped once its watch's issue was closed, before it wrote its totals.</li>
</ul>

<footer class="sources">
<b>Sources</b>
<ul>
<li>The first report: <a href="../local-models/">Local Models Below the Goal</a></li>
<li>The scripts and their pins: <a href="https://github.com/JogoShugh/heart-rise-artie/tree/claude/auth-branch-4bnmhc/exhibits/merge-watch/yaml">exhibits/merge-watch/yaml</a></li>
<li>Every run's output, trace and the summary: <a href="https://github.com/JogoShugh/heart-rise-artie/tree/claude/auth-branch-4bnmhc/exhibits/merge-watch/runs/yaml">exhibits/merge-watch/runs/yaml</a></li>
<li>The plan and the October results: <a href="https://github.com/JogoShugh/heart-rise-artie/blob/claude/auth-branch-4bnmhc/spec/PLAN-LOCAL-MODEL.md">spec/PLAN-LOCAL-MODEL.md</a></li>
</ul>
</footer>
</main>
</body></html>
`;
mkdirSync(join(repo, "docs/reports/local-models-yaml"), { recursive: true });
writeFileSync(join(repo, "docs/reports/local-models-yaml/index.html"), page);
console.log(`${runs.length} runs, ${passed(runs)} passed: docs/reports/local-models-yaml/index.html`);
