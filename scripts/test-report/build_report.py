"""Builds test-report.html from real result files. Nothing in the result
tables is typed by hand: counts, names, statuses and timings come from
JUnit XML, the cache-demo run log, the live hash check, and journal.html.

Does not run anything itself. Before running it:
  - ./gradlew test in server-spring, client, exhibits/koog-first-pass
  - save the cache-demo client's output to cache-demo.log (next to this file)
  - in server-spring: docker compose up --build -d, ./capture.sh, then
    python3 render_journal.py, for run/ and journal.html (demo.sh does the
    same and then opens the journal in a browser)
  - live-hash.sh (next to this file), with the server still up
  - scripts/live-client-check.sh, for client/build/test-results/liveTest

Usage: python3 scripts/test-report/build_report.py [input_dir]
input_dir holds the two logs and receives test-report.html; defaults to
this script's own directory. Explanatory prose below is hand-written and
can go stale when the code changes; the tables cannot."""
import glob, html, re, sys, xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRATCH = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent
esc = html.escape


def junit(project, task="test"):
    cases = []
    for f in sorted(glob.glob(str(REPO / project / f"build/test-results/{task}/*.xml"))):
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
                "suite": suite.get("name", ""),
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
# The @live scenarios show as skipped in the plain run; they are counted in client_live.
client = [c for c in junit("client") if c["status"] != "skip"]
koog = junit("exhibits/koog-first-pass")
# @live scenarios only; the rest of that run is skipped by its tag filter.
# liveTest also re-runs the plain JUnit classes; only the Cucumber scenarios count here.
client_live = [c for c in junit("client", "liveTest") if c["status"] != "skip" and "Cucumber" in c["suite"]]

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
    ("server-spring", "Cucumber + JUnit 5 + WebTestClient", spring),
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
summary_rows += f'<tr><td>client live</td><td>ARTIE over HTTP vs. server-spring + Postgres</td><td class="num">{tally(client_live)[0]} / {tally(client_live)[1]}</td></tr>'
summary_rows += f'<tr><td>server-spring live</td><td>journal verdicts</td><td class="num">{j_ok} / {j_ok + j_bug}</td></tr>'
summary_rows += f'<tr><td>server-spring live</td><td>served bytes vs. URL hash</td><td class="num">{live_match} / {len(live)}</td></tr>'
grand_ok = total_pass + cache_pass + j_ok + live_match + tally(client_live)[0]
grand = total + len(cache_checks) + j_ok + j_bug + len(live) + len(client_live)

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
  <div class="big"><b>{grand_ok} / {grand}</b><span class="muted">checks passing across 3 test suites and 4 live checks</span></div>
  <p class="muted">Every count, name and status on this page was read from result files by a script: JUnit XML from Gradle, the cache-demo run log, a live hash check against the running Spring server, and the verdicts in <code>journal.html</code>. The explanations were written by hand. Each code excerpt is copied from the current source, with its file and line number.</p>
</header>

<section>
  <div class="eyebrow">Summary</div>
  <div class="tbl"><table><thead><tr><th>Suite</th><th>Kind</th><th class="num">Passing</th></tr></thead><tbody>{summary_rows}</tbody></table></div>
</section>

<section>
  <div class="eyebrow">The ladder</div>
  <h2>L0 to L6: where each rung is proven</h2>
  <p class="muted">Server = server-spring. Client = ARTIE. "Live" means against server-spring and Postgres in Docker. HAL Schema Forms is the reference dialect for every rung; HAL-FORMS is the L0 on-ramp (SPEC 0.5 decision).</p>
  <div class="tbl"><table><thead><tr><th>Rung</th><th>Server</th><th>Client</th></tr></thead><tbody>
    <tr><td>L0 collapsed</td><td><code>rendering-ladder.feature</code>, journal 01</td><td><code>sync-l0.feature</code>; live full cycle at L0</td></tr>
    <tr><td>L1 set fetch</td><td><code>rendering-ladder.feature</code></td><td><code>l1-and-sharing.feature</code>; live full cycle at L1</td></tr>
    <tr><td>L2 schema fetch</td><td><code>rendering-ladder.feature</code>, live hash check</td><td><code>sync-l2.feature</code> (cache, 304, hash check); live full cycle and 304 at L2</td></tr>
    <tr><td>L3 caching proof</td><td><code>templated-targets.feature</code>, journal 04</td><td><code>l1-and-sharing.feature</code> incl. the concrete-profile counter-case; live: two clients, one cache, one set fetch</td></tr>
    <tr><td>L4 errors</td><td><code>conditional-acts.feature</code>, <code>layer1-revalidation.feature</code>, journal 06</td><td><code>conditional-acts.feature</code>; live 412 and 422</td></tr>
    <tr><td>L5 cross-dialect</td><td><code>cross-dialect.feature</code> incl. the on-ramp guard</td><td><code>hal-forms-on-ramp.feature</code>; live: same acts in both dialects on 4 beds, full cycle over HAL-FORMS</td></tr>
    <tr><td>L6 reasoning</td><td>n/a</td><td><code>reason-l6.feature</code> with a scripted reasoner and the Claude request shape; the real-model scenario has not been run (no API key here)</td></tr>
  </tbody></table></div>
</section>

<section>
  <div class="eyebrow">What changed to get here</div>
  <h2>Changes in this pass: SPEC 0.4 conformance, one step at a time</h2>
  <p class="muted">Each step started with a Gherkin feature in <code>server-spring/src/test/resources/features/</code>. The bdd-gates log (<code>.claude/tdd-events.log</code>) shows each one failing before the fix and passing after; the git pre-commit check refuses a commit that skips that order.</p>
  <ul class="plain">
    <li><b>Gates installed.</b> Claude Code hooks block production edits without a feature change, log every test run, run Detekt after each edit, and keep the build green before a turn ends. Konsist tests guard the domain boundary (server) and the Transport boundary (client).</li>
    <li><b>Step 1 · rendering ladder.</b> No <code>Prefer</code> now returns L0: everything in one response. <code>forms=cid</code> gives L1, <code>forms=cid, schema=cid</code> gives L2. L1 and L2 sets have different addresses, and Layer 2 no longer sends <code>Vary</code>. (§6.4, §6.5)</li>
    <li><b>Step 2 · Layer 1 caching.</b> <code>Cache-Control: no-cache</code>, ETags like <code>"seq-1.hsf.L2"</code> that differ per dialect and rung, and <code>304</code> on revalidation. Before, an L1 ETag revalidated an L2 request: a wrong cache hit. (§6.1, §6.2)</li>
    <li><b>Step 3 · conditional acts.</b> An act without <code>If-Match</code> gets <code>428</code>; a bad body gets <code>422</code>, not <code>400</code>; checks run in the order 428, 412, 409, 422. (§6.2)</li>
    <li><b>Step 4 · profiles.</b> <code>Link: rel="profile"</code> on every Layer 1 response; <code>Accept-Profile</code> negotiation; <code>406</code> for an unsatisfiable <code>Accept</code>; the media-type <code>profile</code> parameter is gone. (§6.1)</li>
    <li><b>Step 5 · templates.</b> <code>{{bedId}}</code> became <code>{{id}}</code>, the Layer 1 property it resolves from. Before, a rule-following client posted to <code>/beds/actions/water-cell</code> and got <code>404</code>. (§6.9.1)</li>
    <li><b>L5 guard.</b> Both dialects offer the same acts in every state, now checked over HTTP.</li>
    <li><b>Client C1 · real wire format.</b> ARTIE parsed a format no SPEC version defines. It now reads 0.4 <code>_forms</code>, checks <code>Link: rel="profile"</code>, and holds the ETag.</li>
    <li><b>Client C2 · conditional acts.</b> <code>If-Match</code> on every act; templates expanded by the <code>[0.5]</code> rule only; <code>412</code>, <code>409</code>, <code>428</code> and <code>422</code> each handled their own way.</li>
    <li><b>Client C3 · real HTTP.</b> <code>HttpTransport</code> and live scenarios against server-spring. The second live run found a server bug: the first snapshot (event 5) answered <code>500</code> after its event was committed. Fixed, with a Postgres test.</li>
    <li><b>One web stack.</b> Production ran Spring MVC on Tomcat (pulled in by the HATEOAS starter) while the tests ran WebFlux. Production now runs WebFlux on Netty, guarded by <code>runtime-stack.feature</code>.</li>
    <li><b>Spec decision.</b> HAL-FORMS is the L0 on-ramp; HAL Schema Forms is the reference dialect for L0–L6.</li>
    <li><b>Client L1, L3, L5, L6.</b> L1 walk; L3 shared-cache proof and its concrete counter-case; ARTIE reads HAL-FORMS; the Reason step, whose choice is checked against the manifest and schema before anything is sent.</li>
    <li><b>Client C4 · L2 walk.</b> Layer 1, set, schemas, through an HTTP cache that serves immutable layers without a request and revalidates Layer 1 to <code>304</code>. Bytes that do not hash to their address are refused.</li>
  </ul>
</section>

<!-- 1. server-spring -->
<section>
  <div class="eyebrow">Suite 1 · server-spring · {tally(spring)[0]} / {tally(spring)[1]}</div>
  <h2>Server: SPEC 0.4 conformance scenarios, content addresses, form metadata</h2>
  <p class="muted"><code>RunCucumberTest</code> rows are Gherkin scenarios. They run the real controllers and the real <code>GardenBedService</code> against an in-memory event store, so no database is needed.</p>
  {table(spring)}
  <div class="example">
    <h3>Example: the first failing check decides the status code</h3>
    <dl>
      <dt>Rule</dt><dd>SPEC 0.5 §6.2: a server checks an act in a fixed order and returns the first failure: <code>428</code> no <code>If-Match</code>, <code>412</code> stale <code>If-Match</code>, <code>409</code> act not in the current manifest, <code>422</code> body fails the schema. The order matters because <code>412</code> tells a client to discard its act, while <code>422</code> says the act itself was malformed.</dd>
      <dt>How</dt><dd>Bed 2 is PLANTED, at state version 1. Each row sends a request that is wrong in its own way <i>and</i> in every later way: the <code>sow-seed</code> rows are illegal for a planted bed and also carry an empty <code>seedType</code>. Only the evaluation order can pick the code.</dd>
      <dt>Before</dt><dd>The first run after writing this feature failed 9 scenarios. Rows 1-3 got <code>400</code> instead of <code>428</code>, <code>412</code> and <code>409</code>: Spring's <code>@Valid @RequestBody</code> validated the body before the handler ever looked at <code>If-Match</code>. An act with no <code>If-Match</code> at all went through with <code>200</code>.</dd>
      <dt>After</dt><dd><code>GardenBedController.act()</code> checks the precondition and the manifest first. The body is bound and validated afterwards, by <code>ActBodyBinder</code>. All rows pass.</dd>
    </dl>
    {code("server-spring/src/test/resources/features/conditional-acts.feature", "Scenario Outline: The first failing check", None, 13)}
    {code("server-spring/src/main/kotlin/com/artie/gardenbed/web/GardenBedController.kt", "/** [0.5] precondition scope", "    }")}
  </div>
</section>

<!-- 2. client -->
<section>
  <div class="eyebrow">Suite 2 · client (the real ARTIE) · {tally(client)[0]} / {tally(client)[1]}</div>
  <h2>Client: sync at L0 and L2, conditional acts, refusals, the cache</h2>
  <p class="muted">These scenarios run against a scripted server written as raw HTTP responses. The same client code runs against the real server in <code>live-server.feature</code> (summary row "client live").</p>
  {table(client)}
  <div class="example">
    <h3>Example: a stale act is dropped, not resent</h3>
    <dl>
      <dt>Rule</dt><dd>SPEC §6.3: on <code>412</code> the client re-syncs and discards the pending act. The user's intent may mean something else now, so resending it could overwrite someone else's write.</dd>
      <dt>How</dt><dd>The scripted server answers the act with <code>412</code>, then answers the re-sync GET with a newer bed. The scenario checks the exact requests the client sent after entering: one POST, one GET, and no second POST. It also checks that the client now holds the newer ETag.</dd>
      <dt>Mutation check</dt><dd>Treating <code>412</code> like <code>428</code> (re-sync, then resend) made this scenario fail, because the resent act was not in the new manifest. The change was reverted. The same rule passes live: <code>live-server.feature</code> has a second client write first, and ARTIE gets <code>StaleAct</code> from the real server.</dd>
    </dl>
    {code("client/src/test/resources/features/conditional-acts.feature", "Scenario: 412 means the world moved", None, 34)}
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
  <p><code>docker compose up --build</code> starts Postgres and the app. <code>capture.sh</code> resets the database, records a real HTTP walkthrough in <code>run/</code> using the 0.4 wire format, and <code>render_journal.py</code> computes each journal verdict from that captured data. Then <code>live-hash.sh</code> fetches every content-addressed URL in the capture again and hashes the body with <code>shasum</code>.</p>
  <div class="tbl"><table><thead><tr><th>Result</th><th>URL</th><th>sha256 of served body (16 hex)</th></tr></thead><tbody>{"".join(f'<tr><td><span class="st {"pass" if r[-1]=="MATCH" else "fail"}">{"PASS" if r[-1]=="MATCH" else "FAIL"}</span></td><td><code>{esc(r[0])}</code></td><td><code>{esc(r[1].removeprefix("served="))}</code></td></tr>' for r in live)}</tbody></table></div>
  <p class="muted">Journal: {j_ok} correct, {j_bug} failing, across entries {", ".join(esc(n + " " + t) for n, t in j_entries)}.</p>
  <div class="example">
    <h3>Example: journal entry 06 — each failure gets its own code, against Postgres</h3>
    <dl>
      <dt>Rule</dt><dd>The same rule as the Suite 1 example, now checked on the real stack: Spring Boot over R2DBC and Postgres, in Docker.</dd>
      <dt>How</dt><dd><code>capture.sh</code> sends six requests. An act with no <code>If-Match</code>; one with a stale tag; a <code>sow-seed</code> on a bed that is ready to harvest, with that bed's current ETag; a <code>water-cell</code> with 0 ml and a current ETag; a request for an unknown schema; and a revalidation of bed 2 with the ETag just received. Each status code is saved to a file in <code>run/</code>.</dd>
      <dt>Result</dt><dd><code>428</code>, <code>412</code>, <code>409</code>, <code>422</code>, <code>404</code>, <code>304</code>. The journal computes its verdict by comparing those files to the expected codes.</dd>
    </dl>
    {code("server-spring/render_journal.py", "def entry_06", "    ]")}
  </div>
</section>

<section>
  <div class="eyebrow">Limits</div>
  <h2>What this baseline does not cover</h2>
  <ul class="plain">
    <li>L6 has not been run against a real model: no <code>ANTHROPIC_API_KEY</code> in this environment. <code>./gradlew llmTest</code> runs it when one is set.</li>
    <li>RISE is not implemented. ARTIE fetches schemas one after another, not in parallel.</li>
    <li>HAL-FORMS is served at L0 only. Its Layer 2/3 addressing needs a SPEC decision first; see <code>spec/CONFORMANCE-GAPS.md</code>.</li>
    <li>The cache-demo exhibit still uses its own 0.3-era fixtures (<code>{{bedId}}</code>, no <code>Prefer</code>). It demonstrates the caching mechanism, not 0.4 conformance.</li>
    <li>The Docker build of <code>Dockerfile.origin</code> was verified on 3 Oct, before the fixed random seed. The cache-demo run above used the local <code>server.py</code> with the regenerated fixtures.</li>
    <li><code>server-node</code> has no tests.</li>
    <li>The bdd-gates pre-commit check runs only after <code>git config core.hooksPath .githooks</code>. Until then, commits in this pass ran it by hand first.</li>
  </ul>
</section>

</div>
"""
(SCRATCH / "test-report.html").write_text(page)
print("wrote", SCRATCH / "test-report.html", grand_ok, "/", grand)
