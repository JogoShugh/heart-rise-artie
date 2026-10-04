"""Builds test-report.html from real result files. Nothing in the result
tables is typed by hand: counts, names, statuses and timings come from
JUnit XML, the cache-demo run log, the live hash check, and journal.html.

Does not run anything itself. Before running it:
  - ./gradlew test in server-spring, client, exhibits/koog-first-pass
  - save the cache-demo client's output to cache-demo.log (next to this file)
  - save the curl | shasum check of live content-addressed URLs to live-hash.txt
  - server-spring/demo.sh, for journal.html and run/

Usage: python3 scripts/test-report/build_report.py [input_dir]
input_dir holds the two logs and receives test-report.html; defaults to
this script's own directory. Explanatory prose below is hand-written and
can go stale when the code changes; the tables cannot."""
import glob, html, re, sys, xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRATCH = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent
esc = html.escape


def junit(project):
    cases = []
    for f in sorted(glob.glob(str(REPO / project / "build/test-results/test/*.xml"))):
        suite = ET.parse(f).getroot()
        for tc in suite.iter("testcase"):
            failed = tc.find("failure") is not None or tc.find("error") is not None
            skipped = tc.find("skipped") is not None
            cases.append({
                "cls": tc.get("classname", "").split(".")[-1],
                "name": tc.get("name"),
                "time": float(tc.get("time", 0)),
                "status": "fail" if failed else "skip" if skipped else "pass",
                "ts": suite.get("timestamp", ""),
            })
    return cases


def excerpt(rel, start, stop_line=None, max_lines=40):
    """Lines of a source file from the first line containing `start`
    through the first later line equal to `stop_line` (inclusive)."""
    lines = (REPO / rel).read_text().splitlines()
    i = next(n for n, l in enumerate(lines) if l.lstrip().startswith(start.lstrip()) and (start.startswith("//") or not l.startswith("//")))
    out = []
    for l in lines[i:i + max_lines]:
        out.append(l)
        if stop_line is not None and l == stop_line and len(out) > 1:
            break
    return i + 1, "\n".join(out)


def code(rel, start, stop_line=None, max_lines=40):
    line, text = excerpt(rel, start, stop_line, max_lines)
    return (f'<figure class="code"><figcaption>{esc(rel)}:{line}</figcaption>'
            f'<pre><code>{esc(text)}</code></pre></figure>')


def table(cases):
    rows = "".join(
        f'<tr><td><span class="st {c["status"]}">{c["status"].upper()}</span></td>'
        f'<td class="cls">{esc(c["cls"])}</td><td>{esc(c["name"])}</td>'
        f'<td class="num">{c["time"]:.3f}s</td></tr>' for c in cases)
    return f'<div class="tbl"><table><thead><tr><th>Result</th><th>Class</th><th>Test</th><th class="num">Time</th></tr></thead><tbody>{rows}</tbody></table></div>'


def tally(cases):
    p = sum(c["status"] == "pass" for c in cases)
    return p, len(cases)


# ---- collect ----------------------------------------------------------
spring = junit("server-spring")
client = junit("client")
koog = junit("exhibits/koog-first-pass")

cache_log = (SCRATCH / "cache-demo.log").read_text()
cache_checks = [(m.group(1), m.group(2).strip()) for m in re.finditer(r"^\s+(PASS|FAIL)\s+(.*)$", cache_log, re.M)]
cache_final = next((l for l in cache_log.splitlines() if "CHECKS PASSED" in l or "FAILED" in l), "")

live = [l.split() for l in (SCRATCH / "live-hash.txt").read_text().splitlines() if l.strip()]
journal = (REPO / "server-spring/journal.html").read_text()
j_ok = len(re.findall(r'class="verdict ok"', journal))
j_bug = len(re.findall(r'class="verdict bug"', journal))
j_entries = [(m.group(1), re.sub("<[^>]+>", "", m.group(2))) for m in
             re.finditer(r'<span class="entry-num">(\d+)</span><h2 class="entry-title">(.*?)</h2>', journal)]

set_pointer = (REPO / "server-spring/run/01-set-pointer.json").read_text().strip()
set_url = (REPO / "server-spring/run/01-set-url.txt").read_text().strip()

suites = [
    ("server-spring", "JUnit 5 + WebTestClient", spring),
    ("client", "Cucumber + JUnit 5", client),
    ("koog-first-pass", "JUnit 5 + Ktor MockEngine", koog),
]
all_cases = spring + client + koog
total_pass, total = tally(all_cases)
cache_pass = sum(s == "PASS" for s, _ in cache_checks)
live_match = sum(r[-1] == "MATCH" for r in live)

summary_rows = "".join(
    f'<tr><td>{esc(n)}</td><td>{esc(kind)}</td><td class="num">{tally(c)[0]} / {tally(c)[1]}</td></tr>'
    for n, kind, c in suites)
summary_rows += f'<tr><td>cache-demo</td><td>end-to-end run vs. server log</td><td class="num">{cache_pass} / {len(cache_checks)}</td></tr>'
summary_rows += f'<tr><td>server-spring live</td><td>journal verdicts</td><td class="num">{j_ok} / {j_ok + j_bug}</td></tr>'
summary_rows += f'<tr><td>server-spring live</td><td>served bytes vs. URL hash</td><td class="num">{live_match} / {len(live)}</td></tr>'
grand_ok = total_pass + cache_pass + j_ok + live_match
grand = total + len(cache_checks) + j_ok + j_bug + len(live)

run_ts = max(c["ts"] for c in all_cases)

# ---- page ---------------------------------------------------------------
page = f"""<title>ARTIE Test Baseline</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Familjen+Grotesk:wght@500;700&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>
/* Layout: single reading column; summary first, then one section per suite. */
:root {{
  --bg: #f4f6f5; --surface: #ffffff; --ink: #15211d; --muted: #5a6964; --line: #cfd8d4;
  --link: #2a4fd6; --link-soft: #e3e9fc; --ok: #2f7d4f; --ok-soft: #e0f0e6;
  --bad: #b3261e; --bad-soft: #fbe3e1; --code-bg: #eef2f0;
  --f-display: "Familjen Grotesk", "Helvetica Neue", Arial, sans-serif;
  --f-body: "IBM Plex Sans", system-ui, sans-serif;
  --f-mono: "IBM Plex Mono", ui-monospace, Menlo, monospace;
}}
@media (prefers-color-scheme: dark) {{ :root:not([data-theme="light"]) {{
  --bg: #111816; --surface: #18221f; --ink: #e4ece9; --muted: #98a8a2; --line: #2e3d38;
  --link: #8ea6ff; --link-soft: #1f2a4d; --ok: #6fca93; --ok-soft: #1b3326;
  --bad: #ff8a80; --bad-soft: #3d1a17; --code-bg: #1d2925; color-scheme: dark; }} }}
:root[data-theme="dark"] {{
  --bg: #111816; --surface: #18221f; --ink: #e4ece9; --muted: #98a8a2; --line: #2e3d38;
  --link: #8ea6ff; --link-soft: #1f2a4d; --ok: #6fca93; --ok-soft: #1b3326;
  --bad: #ff8a80; --bad-soft: #3d1a17; --code-bg: #1d2925; color-scheme: dark; }}
body {{ background: var(--bg); color: var(--ink); font: 16px/1.6 var(--f-body); }}
.wrap {{ max-width: 820px; margin: 0 auto; padding-inline: 20px; padding-block: 40px 72px; display: grid; gap: 52px; }}
h1, h2, h3 {{ font-family: var(--f-display); margin: 0; line-height: 1.2; text-wrap: balance; }}
h1 {{ font-size: clamp(1.9rem, 5vw, 2.6rem); }}
h2 {{ font-size: 1.45rem; }}
h3 {{ font-size: 1.05rem; }}
p {{ margin: 0; max-width: 68ch; }}
section {{ display: grid; gap: 14px; min-width: 0; }}
.eyebrow {{ font: 500 0.75rem var(--f-mono); letter-spacing: 0.08em; text-transform: uppercase; color: var(--link); }}
.muted {{ color: var(--muted); }}
code {{ font: 0.86em var(--f-mono); background: var(--code-bg); padding: 1px 5px; border-radius: 4px; overflow-wrap: anywhere; }}
.big {{ display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; }}
.big b {{ font: 700 3rem var(--f-display); color: var(--ok); font-variant-numeric: tabular-nums; }}
.tbl {{ overflow-x: auto; background: var(--surface); border: 1px solid var(--line); border-radius: 10px; }}
table {{ border-collapse: collapse; width: 100%; font-size: 0.9rem; }}
th, td {{ text-align: left; padding: 8px 12px; border-bottom: 1px solid var(--line); vertical-align: top; }}
tr:last-child td {{ border-bottom: 0; }}
th {{ font: 500 0.72rem var(--f-mono); letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }}
.num {{ text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }}
td.cls {{ font: 0.8rem var(--f-mono); color: var(--muted); }}
.st {{ font: 500 0.7rem var(--f-mono); padding: 1px 7px; border-radius: 99px; }}
.st.pass {{ background: var(--ok-soft); color: var(--ok); }}
.st.fail {{ background: var(--bad-soft); color: var(--bad); }}
.st.skip {{ background: var(--code-bg); color: var(--muted); }}
.example {{ background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 18px; display: grid; gap: 12px; min-width: 0; }}
.example h3 {{ color: var(--ink); }}
.example dl {{ margin: 0; display: grid; grid-template-columns: 7.5rem 1fr; gap: 6px 14px; }}
.example dt {{ font: 500 0.75rem var(--f-mono); text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); padding-top: 3px; }}
.example dd {{ margin: 0; min-width: 0; }}
figure.code {{ margin: 0; min-width: 0; }}
figure.code figcaption {{ font: 0.75rem var(--f-mono); color: var(--muted); margin-bottom: 4px; }}
pre {{ margin: 0; background: var(--code-bg); border-radius: 8px; padding: 12px 14px; overflow-x: auto; font: 0.8rem/1.5 var(--f-mono); }}
pre code {{ background: none; padding: 0; }}
.redgreen {{ border-left: 3px solid var(--bad); padding-left: 12px; font-size: 0.92rem; }}
.redgreen.ok {{ border-color: var(--ok); }}
ul.plain {{ margin: 0; padding-left: 18px; font-size: 0.95rem; }}
@media (max-width: 560px) {{ .example dl {{ grid-template-columns: 1fr; }} .example dt {{ padding-top: 6px; }} }}
</style>

<div class="wrap">

<header style="display:grid;gap:12px">
  <div class="eyebrow">~/repos/artie · baseline · results from run at {esc(run_ts)} UTC</div>
  <h1>Test baseline</h1>
  <div class="big"><b>{grand_ok} / {grand}</b><span class="muted">checks passing across 3 test suites and 3 live checks</span></div>
  <p class="muted">Every count, name and status on this page was read from result files by a script: JUnit XML from Gradle, the cache-demo run log, a live hash check against the running Spring server, and the verdicts in <code>journal.html</code>. The explanations were written by hand. Each code excerpt is copied from the current source, with its file and line number.</p>
</header>

<section>
  <div class="eyebrow">Summary</div>
  <div class="tbl"><table><thead><tr><th>Suite</th><th>Kind</th><th class="num">Passing</th></tr></thead><tbody>{summary_rows}</tbody></table></div>
</section>

<section>
  <div class="eyebrow">What changed to get here</div>
  <h2>Fixes made in this pass</h2>
  <ul class="plain">
    <li><b>Affordance-set URLs now match the bytes served.</b> The server used to hash the canonical JSON but send Jackson's output, which has keys in a different order. It now sends the canonical string. (SPEC §6.5)</li>
    <li><b>HAL-FORMS fields are fillable again.</b> <code>depthCm</code> and <code>milliliters</code> were marked <code>readOnly: true</code> because the request DTOs used <code>val</code>. They now use <code>var</code>.</li>
    <li><b>Client test tightened.</b> The "unknown action" test used to pass even if the client called the network. It now also checks that no request was sent.</li>
    <li><b>Koog exhibit compiles again.</b> Its tests used a removed <code>UniformArgs</code> wrapper, so they were updated to pass a plain <code>JsonObject</code>.</li>
    <li><b>The test that copied production code is gone.</b> It was replaced by real HTTP checks.</li>
    <li><b>Cache-demo fixtures regenerated.</b> They are now canonical JSON from a fixed random seed, so regenerating gives identical files.</li>
  </ul>
</section>

<!-- 1. server-spring -->
<section>
  <div class="eyebrow">Suite 1 · server-spring · {tally(spring)[0]} / {tally(spring)[1]}</div>
  <h2>Server: content addresses and form metadata</h2>
  {table(spring)}
  <div class="example">
    <h3>Example: each affordance-set URL is the sha256 of the bytes served at it</h3>
    <dl>
      <dt>Rule</dt><dd>SPEC §6.5: the hash in <code>/affordance-sets/sha256-…</code> identifies the actual bytes at that URL. A client or cache can verify a response by hashing it.</dd>
      <dt>How</dt><dd><code>@WebFluxTest</code> starts only the two controllers, with no database. For each of the 3 bed statuses, the test GETs the URL over the test HTTP client, hashes the raw response body, and compares that hash to the URL. It doesn't reuse any production hashing code, so the test can't agree with a bug just because both share the same helper.</dd>
      <dt>Real data</dt><dd>From the live capture, <code>{esc(set_url)}</code> serves:<pre><code>{esc(set_pointer)}</code></pre>The keys are sorted (<code>contentType</code> before <code>method</code>) with no whitespace. That is the RFC 8785 canonical form.</dd>
    </dl>
    {code("server-spring/src/test/kotlin/com/artie/gardenbed/web/ContentAddressHttpTest.kt", "fun `each affordance-set URL", "    }")}
    <p class="redgreen">Before the fix, this test failed (line 54). The server was sending Jackson's insertion-order JSON, and those bytes hashed to <code>dd9a1964d19f5485</code>, not the <code>14707229191a4566</code> in the URL.</p>
    <p class="redgreen ok">The fix: <code>AffordanceSetController</code> now returns <code>JsonSchemaDialectSupport.affordanceSetJson(...)</code>, the canonical string, instead of a Map. The test passes.</p>
  </div>
</section>

<!-- 2. client -->
<section>
  <div class="eyebrow">Suite 2 · client (the real ARTIE) · {tally(client)[0]} / {tally(client)[1]}</div>
  <h2>Client: enter, act, and refusing unknown actions</h2>
  <p class="muted">Cucumber names Scenario Outline rows "Example #1.1" and so on. Those rows come from <code>media_type.feature</code>.</p>
  {table(client)}
  <div class="example">
    <h3>Example: acting on an action absent from the manifest raises an error</h3>
    <dl>
      <dt>Rule</dt><dd>This is H.E.A.R.T.'s core guarantee: the agent can only act on what the server offered. Here, the server offered <code>transition</code> and the caller asks for <code>nonexistent</code>.</dd>
      <dt>How</dt><dd>A fake transport returns one canned response for <code>enter</code>. The test then calls <code>act("nonexistent", …)</code> and checks two things: an error was raised, and the fake transport recorded zero submits.</dd>
      <dt>Why both</dt><dd>The fake transport throws when its queue is empty. So "an error was raised" alone would also pass if the client had wrongly tried the network. The second check closes that gap.</dd>
    </dl>
    {code("client/src/test/resources/features/act-unknown.feature", "Scenario:", None, 8)}
    {code("client/src/main/kotlin/heart/ArtieClient.kt", "override fun act", "    }")}
    <p class="redgreen">Mutation check: I temporarily made <code>act</code> call the transport before checking the manifest. The scenario failed with <code>Expected no submit, but got [POST https://example.com/nonexistent]</code>. I then restored the original code.</p>
  </div>
</section>

<!-- 3. koog -->
<section>
  <div class="eyebrow">Suite 3 · koog-first-pass (the "before" exhibit) · {tally(koog)[0]} / {tally(koog)[1]}</div>
  <h2>Koog agent tool: schema becomes a tool, and payloads are validated</h2>
  {table(koog)}
  <div class="example">
    <h3>Example: an invalid payload throws before the network call</h3>
    <dl>
      <dt>Rule</dt><dd>When the LLM fills in a form, the tool checks the arguments against the server's JSON Schema before sending anything. A missing required field is rejected in the agent, not by the server.</dd>
      <dt>How</dt><dd>The tool is built from a <code>sowSeed</code> schema where <code>plantCultivar</code> and <code>count</code> are both required. The test leaves out <code>count</code> and expects <code>IllegalArgumentException</code>. The Ktor <code>MockEngine</code> always answers "success", so the exception can only come from the validator.</dd>
    </dl>
    {code("exhibits/koog-first-pass/src/test/kotlin/HeartToolTests.kt", "    fun `execute with invalid payload", "    }")}
  </div>
</section>

<!-- 4. cache-demo -->
<section>
  <div class="eyebrow">Suite 4 · cache-demo · {cache_pass} / {len(cache_checks)}</div>
  <h2>Caching: shared URLs are fetched once</h2>
  <p>The Kotlin client (Ktor with <code>HttpCache</code>) syncs 12 beds plus bed-1 again. Afterwards it reads the server's own request log at <code>/__stats</code>, so these checks count requests the server actually received.</p>
  <div class="tbl"><table><thead><tr><th>Result</th><th>Check</th></tr></thead><tbody>{"".join(f'<tr><td><span class="st {s.lower()}">{s}</span></td><td><code>{esc(t)}</code></td></tr>' for s, t in cache_checks)}</tbody></table></div>
  <p class="muted">Final line of the run: <code>{esc(cache_final)}</code></p>
  <div class="example">
    <h3>Example: four beds, one affordance-set fetch</h3>
    <dl>
      <dt>Rule</dt><dd>SPEC §6.6: a content-addressed URL is <code>immutable</code>, so once it's cached the client never asks the server for it again.</dd>
      <dt>Setup</dt><dd>bed-1, bed-2, bed-5 and bed-11 all link to the same 2-action set. The fixture generator decides that, and <code>fixtures/manifest.txt</code> records it.</dd>
      <dt>Result</dt><dd>The server log shows that set's URL requested exactly once, even though four different beds pointed at it. bed-1's own resource is <code>no-cache</code>, so it is revalidated: it returns <code>200</code> the first time and <code>304 Not Modified</code> the second.</dd>
    </dl>
    {code("exhibits/cache-demo/kotlin-client/src/main/kotlin/ArtieCacheDemo.kt", "val contentAddressed = stats.counts", None, 9)}
  </div>
</section>

<!-- 5. live spring -->
<section>
  <div class="eyebrow">Suite 5 · server-spring live (Docker: Postgres + app) · {j_ok + live_match} / {j_ok + j_bug + len(live)}</div>
  <h2>Live server: journal verdicts and served-byte hashes</h2>
  <p><code>demo.sh</code> builds and starts the stack, resets the database, records a real HTTP walkthrough in <code>run/</code>, and renders <code>journal.html</code>. The journal's verdicts are computed from that captured data. Separately, each content-addressed URL in the capture was fetched again with <code>curl</code> and its body hashed with <code>shasum</code>.</p>
  <div class="tbl"><table><thead><tr><th>Result</th><th>URL</th><th>sha256 of served body (16 hex)</th></tr></thead><tbody>{"".join(f'<tr><td><span class="st {"pass" if r[-1]=="MATCH" else "fail"}">{"PASS" if r[-1]=="MATCH" else "FAIL"}</span></td><td><code>{esc(r[0])}</code></td><td><code>{esc(r[1].removeprefix("served="))}</code></td></tr>' for r in live)}</tbody></table></div>
  <p class="muted">Journal: {j_ok} correct, {j_bug} failing, across entries {", ".join(esc(n + " " + t) for n, t in j_entries)}.</p>
  <div class="example">
    <h3>Example: journal entry 02 — the watering form must be fillable</h3>
    <dl>
      <dt>Rule</dt><dd>In the HAL-FORMS dialect, a field marked <code>readOnly: true</code> tells clients not to fill it in. If <code>milliliters</code> can't be filled in, the agent can't water the bed.</dd>
      <dt>Cause</dt><dd>Spring HATEOAS reads a Kotlin <code>val</code> (getter but no setter) as read-only (Spring HATEOAS issue #1310).</dd>
      <dt>Before</dt><dd>The journal committed in July already showed this entry as <b>FAIL — regression detected</b>, and the first run in this pass reproduced it. The new unit test <code>RequestDtoMetadataTest</code> failed for both DTOs: <code>WaterCellRequest.milliliters is readOnly</code> and <code>SowSeedRequest.depthCm is readOnly</code>. The second one was never covered by the journal.</dd>
      <dt>After</dt><dd>With the DTOs changed to <code>var</code>, the unit test passes, no captured response contains <code>readOnly</code>, and journal entry 02 shows Correct.</dd>
    </dl>
    {code("server-spring/src/main/kotlin/com/artie/gardenbed/web/dto/Dtos.kt", "// Request DTOs use", None, 16)}
  </div>
</section>

<section>
  <div class="eyebrow">Limits</div>
  <h2>What this baseline does not cover</h2>
  <ul class="plain">
    <li>The client suite runs against a fake transport, not a real server. No test yet runs <code>client/</code> against <code>server-spring</code>.</li>
    <li>The Docker build of <code>Dockerfile.origin</code> was verified on 3 Oct, before the fixed random seed. The cache-demo run above used the local <code>server.py</code> with the regenerated fixtures.</li>
    <li><code>server-node</code> has no tests.</li>
    <li>There are no BDD gates yet. That layer comes next.</li>
  </ul>
</section>

</div>
"""
(SCRATCH / "test-report.html").write_text(page)
print("wrote", SCRATCH / "test-report.html", grand_ok, "/", grand)
