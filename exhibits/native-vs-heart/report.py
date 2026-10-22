#!/usr/bin/env python3
"""The native trial's report (spec/PLAN-NATIVE-VS-HEART-TRIAL.md): every run of both arms, as the checker judged it
and as each run counted its time and its model's tokens, in one page.

    report.py OUT.html

The numbers are the runs' own, kept in RUNS below as each run printed them; the checker's verdicts were taken as
each run ended (runs/*.yaml keep the transcripts and captures).
"""
import html
import sys
from pathlib import Path

# arm, phase, run, stamp, held, of, seconds, requests or steps, model calls, tokens in, out, cache read, cache write,
# note
RUNS = [
    ("native", "reasoned", 1, "1022-062745", 23, 23, 70.3, 22, 14, 30, 5062, 279947, 48608, ""),
    ("native", "reasoned", 2, "1022-064648", 23, 23, 69.2, 24, 12, 26, 4662, 274290, 52541, ""),
    ("native", "reasoned", 3, "1022-070245", 23, 23, 71.4, 24, 12, 26, 4858, 275123, 52726, ""),
    ("heart", "reasoned", 1, "1022-062915", 6, 18, 146, None, 41, 77733, 8050, 51064, 3987,
     "before the fix: went back from the branch list again and again; the step ran out of moves"),
    ("heart", "reasoned", 2, "1022-063415", 7, 18, 139, None, 42, 77796, 8061, 53560, 3204,
     "before the fix: the same"),
    ("heart", "reasoned", 3, "1022-064219", 23, 23, 246, 55, 77, 234895, 11426, 101061, 5733, ""),
    ("heart", "reasoned", 4, "1022-065759", 23, 23, 270, 55, 77, 235189, 11293, 101061, 5733, ""),
    ("heart", "reasoned", 5, "1022-070410", 23, 23, 266, 55, 77, 236909, 11359, 105166, 1628, ""),
    ("native", "scripted", 1, "1022-071000", 23, 23, 22.6, 23, 0, 0, 0, 0, 0, ""),
    ("native", "scripted", 2, "1022-071143", 23, 23, 22.0, 23, 0, 0, 0, 0, 0, ""),
    ("native", "scripted", 3, "1022-071244", 23, 23, 21.2, 23, 0, 0, 0, 0, 0, ""),
    ("heart", "scripted", 1, "1022-071029", 23, 23, 68.0, 58, 0, 0, 0, 0, 0, "before the fixes"),
    ("heart", "scripted", 2, "1022-071213", 7, 18, 26.4, None, 0, 0, 0, 0, 0,
     "before the fixes: the new pull request's edit refused 412, GitHub having changed it a moment after making it"),
    ("heart", "scripted", 3, "1022-071312", 3, 13, 18.4, None, 0, 0, 0, 0, 0,
     "before the fixes: main had moved to the branch list's second page"),
    ("heart", "scripted", 4, "1022-072058", 23, 23, 68.7, 58, 0, 0, 0, 0, 0, ""),
    ("heart", "scripted", 5, "1022-072213", 23, 23, 70.1, 58, 0, 0, 0, 0, 0, ""),
    ("heart", "scripted", 6, "1022-072329", 23, 23, 66.2, 58, 0, 0, 0, 0, 0, ""),
]

FIXES = [
    ("1079117, 0ca4df2", "The planner says why before each move.",
     "At the branch list it went back about one time in four instead of on to main; each wasted pair of moves ran "
     "its step out. Asked for its reason first, it went on to main every time. HEART reasoned runs 1 and 2 are "
     "from before this."),
    ("4b75a7c", "A follow reads a list's next pages to find a titled link.",
     "The script followed the branch list to main as the run had; once the list grew, main was on its second page. "
     "The list offers next links; ARTIE follows them, building no address."),
    ("4b75a7c", "A script's act refused 412 is decided again once.",
     "GitHub changes a new pull request a moment after making it; the script's edit, sent with the ETag it had "
     "just read, was refused. ARTIE has read it again; when the form is still offered and the lock file still "
     "holds, the act is sent once more. A second 412 stops the script."),
]


def esc(value):
    return html.escape(str(value))


def mean(values):
    values = list(values)
    return sum(values) / len(values) if values else 0


def rows(arm, phase):
    return [r for r in RUNS if r[0] == arm and r[1] == phase]


def final(arm, phase):
    """The runs on the code as it ended: those with no note saying they came before a fix."""
    return [r for r in rows(arm, phase) if not r[13].startswith("before")]


def table(phase):
    out = ['<div class="wrap"><table><thead><tr><th>Arm</th><th>Run</th><th>Checks</th><th>Seconds</th>'
           '<th>Requests / steps</th><th>Model calls</th><th>Tokens in</th><th>Out</th><th>Cache read</th>'
           '<th>Cache written</th><th>Note</th></tr></thead><tbody>']
    for r in [x for x in RUNS if x[1] == phase]:
        arm, _, n, stamp, held, of, seconds, steps, calls, tin, tout, cread, cwrite, note = r
        ok = held == of
        out.append(
            f'<tr class="{arm}{"" if ok else " failed"}"><td><span class="tag {arm}">{"Native" if arm == "native" else "HEART"}'
            f'</span></td><td>{n}<div class="stamp">{stamp}</div></td>'
            f'<td class="num"><span class="pill {"ok" if ok else "bad"}">{held}/{of}</span></td>'
            f'<td class="num">{seconds}</td><td class="num">{"" if steps is None else steps}</td>'
            f'<td class="num">{calls or "–"}</td><td class="num">{tin:,}</td><td class="num">{tout:,}</td>'
            f'<td class="num">{cread:,}</td><td class="num">{cwrite:,}</td><td class="note">{esc(note)}</td></tr>')
    out.append("</tbody></table></div>")
    return "".join(out)


def tile(label, native, heart, unit=""):
    return (f'<div class="tile"><div class="label">{esc(label)}</div><div class="pair">'
            f'<div><span class="tag native">Native</span><b>{native}</b>{unit}</div>'
            f'<div><span class="tag heart">HEART</span><b>{heart}</b>{unit}</div></div></div>')


def main():
    out = Path(sys.argv[1])
    nr, hr = final("native", "reasoned"), final("heart", "reasoned")
    ns, hs = final("native", "scripted"), final("heart", "scripted")
    passed = lambda rs: f"{sum(r[4] == r[5] for r in rs)} of {len(rs)}"
    tiles = "".join([
        tile("Reasoned runs that did the task", passed(nr), passed(hr)),
        tile("Reasoned, mean time", f"{mean(r[6] for r in nr):.0f}", f"{mean(r[6] for r in hr):.0f}", " s"),
        tile("Reasoned, model calls a run", f"{mean(r[8] for r in nr):.0f}", f"{mean(r[8] for r in hr):.0f}"),
        tile("Reasoned, tokens out a run", f"{mean(r[10] for r in nr):,.0f}", f"{mean(r[10] for r in hr):,.0f}"),
        tile("Scripted runs that did the task", passed(ns), passed(hs)),
        tile("Scripted, mean time", f"{mean(r[6] for r in ns):.0f}", f"{mean(r[6] for r in hs):.0f}", " s"),
    ])
    fixes = "".join(f'<li><b>{esc(t)}</b> <span class="commit">{esc(c)}</span><p>{esc(why)}</p></li>'
                    for c, t, why in FIXES)
    page = TEMPLATE.replace("@TILES@", tiles).replace("@REASONED@", table("reasoned")) \
        .replace("@SCRIPTED@", table("scripted")).replace("@FIXES@", fixes)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page)
    print(f"wrote {out}")


TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Native and HEART, Measured</title>
<meta name="claude:viewport-fit-sides" content="cover">
<meta name="description" content="The GitHub triage task, run by Claude alone and by Claude through ARTIE and HEART, three times each, reasoned and scripted, judged by one checker.">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:opsz,wght@7..72,650&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&display=swap">
<script>try { document.documentElement.dataset.theme = localStorage.getItem("nt-theme") || "light"; } catch (e) { document.documentElement.dataset.theme = "light"; }</script>
<style>
/* One reading column; tiles, then a table a phase, then what HEART needed fixed. Rust is Claude alone, teal is HEART. */
:root {
  --bg: #f3f6f5; --surface: #ffffff; --fg: #17211d; --muted: #55645d; --rule: #d3ddd8; --link: #2f5f8f;
  --native: #a4502c; --native-soft: #f6e7df; --heart: #1f6f6b; --heart-soft: #dcefec;
  --ok: #2f6f3f; --ok-soft: #e1f0e3; --bad: #b4403a; --bad-soft: #f8e1df;
  --display: "Literata", Georgia, serif; --body: "IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif;
  --mono: "IBM Plex Mono", ui-monospace, Menlo, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #111816; --surface: #18211e; --fg: #e2ebe6; --muted: #98a9a1; --rule: #2a3732; --link: #8ab8e6;
  --native: #e39a76; --native-soft: #33231c; --heart: #7fd0c8; --heart-soft: #16302d;
  --ok: #9fd6ac; --ok-soft: #1b2e21; --bad: #e58a84; --bad-soft: #3a1f1d; color-scheme: dark; } }
:root[data-theme="dark"] {
  --bg: #111816; --surface: #18211e; --fg: #e2ebe6; --muted: #98a9a1; --rule: #2a3732; --link: #8ab8e6;
  --native: #e39a76; --native-soft: #33231c; --heart: #7fd0c8; --heart-soft: #16302d;
  --ok: #9fd6ac; --ok-soft: #1b2e21; --bad: #e58a84; --bad-soft: #3a1f1d; color-scheme: dark; }
body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.6 var(--body); }
main { max-width: 74rem; margin: 0 auto; padding-block: 2rem 4rem; padding-left: max(16px, env(safe-area-inset-left, 0px));
  padding-right: max(16px, env(safe-area-inset-right, 0px)); display: grid; gap: 2rem; }
header { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 1rem; align-items: start; }
h1 { font: 650 2.2rem/1.15 var(--display); margin: 0 0 .5rem; text-wrap: balance; }
h2 { font: 650 1.35rem/1.25 var(--display); margin: 0 0 .7rem; }
p { margin: 0; } .lede, .sub { color: var(--muted); max-width: 66ch; } .sub { margin: 0 0 .8rem; }
.seg { display: flex; align-items: center; gap: .4rem; }
.seg-label { font: 600 .7rem var(--body); letter-spacing: .07em; text-transform: uppercase; color: var(--muted); }
.seg button { font: 500 .8rem var(--body); background: var(--surface); color: var(--fg); border: 1px solid var(--rule); border-radius: 6px; padding: .25rem .6rem; cursor: pointer; }
.seg button[aria-pressed="true"] { background: var(--fg); color: var(--bg); }
.tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr)); gap: .8rem; }
.tile { background: var(--surface); border: 1px solid var(--rule); border-radius: 10px; padding: .7rem .9rem; display: grid; gap: .4rem; }
.tile .label { font: 600 .74rem var(--body); letter-spacing: .06em; text-transform: uppercase; color: var(--muted); }
.tile .pair { display: grid; gap: .2rem; } .tile b { font: 600 1.25rem var(--body); margin-left: .4rem; font-variant-numeric: tabular-nums; }
.tag { display: inline-block; font: 600 .7rem var(--body); letter-spacing: .05em; text-transform: uppercase; border-radius: 4px; padding: .05rem .4rem; }
.tag.native { background: var(--native-soft); color: var(--native); } .tag.heart { background: var(--heart-soft); color: var(--heart); }
.wrap { overflow-x: auto; border: 1px solid var(--rule); border-radius: 10px; background: var(--surface); }
table { border-collapse: collapse; width: 100%; min-width: 58rem; font-size: .9rem; }
th, td { text-align: left; vertical-align: top; padding: .5rem .7rem; border-bottom: 1px solid var(--rule); }
tr:last-child td { border-bottom: 0; }
thead th { font: 600 .7rem var(--body); letter-spacing: .06em; text-transform: uppercase; color: var(--muted); background: var(--bg); }
td.num { font-variant-numeric: tabular-nums; white-space: nowrap; } td.note { color: var(--muted); max-width: 26rem; }
tr.failed td { background: var(--bad-soft); }
.stamp { font: .75rem var(--mono); color: var(--muted); }
.pill { font: 600 .82rem var(--body); border-radius: 999px; padding: .05rem .5rem; }
.pill.ok { background: var(--ok-soft); color: var(--ok); } .pill.bad { background: var(--bad-soft); color: var(--bad); }
.setup { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 22rem), 1fr)); gap: 1rem 2rem; }
ul { margin: .3rem 0 0; padding-left: 1.2rem; display: grid; gap: .5rem; } li { max-width: 68ch; } li p { color: var(--muted); }
.commit { font: .75rem var(--mono); color: var(--muted); }
.close { max-width: 66ch; font-family: var(--display); font-size: 1.1rem; line-height: 1.5; }
</style>
</head>
<body>
<main>
  <header>
    <div><h1>Native and HEART, Measured</h1>
    <p class="lede">The GitHub triage task, 29 steps, done by Claude alone from what it knows of GitHub's REST API, and by Claude through ARTIE and the HEART gateway. Three runs each with a model choosing every move, then three each by a script made from a run, with no model. One checker judged every run by what GitHub held after it: 23 checks.</p></div>
    <div class="seg"><span class="seg-label" id="theme-label">Theme</span><div role="group" aria-labelledby="theme-label" style="display:flex;gap:.3rem"><button type="button" data-th="light">Light</button><button type="button" data-th="dark">Dark</button></div></div>
  </header>

  <section class="tiles" aria-label="Summary, on the code as it ended">@TILES@</section>

  <section class="setup">
    <div><h2>The arms</h2><ul>
      <li><span class="tag native">Native</span> <code>claude-sonnet-5-5</code>, one <code>http</code> tool aimed at api.github.com, no documentation, no gh, no MCP server. The task with HEART's marks put in words.</li>
      <li><span class="tag heart">HEART</span> the same model, as ARTIE's planner and reasoner, through the gateway in front of GitHub. The task as the goal file, <code>github-triage.goal.md</code>.</li>
    </ul></div>
    <div><h2>Made repeatable</h2><ul>
      <li><span class="tag native">Native</span> one model call: its own run's transcript in, a 162-line Python script out (53,306 tokens in, 3,471 out).</li>
      <li><span class="tag heart">HEART</span> <code>artie generate</code> from one run's capture: no model, a script and its lock file.</li>
    </ul></div>
  </section>

  <section><h2>Reasoned: the model chooses every move</h2>
  <p class="sub">Native's input tokens are nearly all read from its prompt cache: one conversation that grows. HEART sends each call a fresh view of the resource held, and makes two calls a move, the planner's and the reasoner's.</p>
  @REASONED@</section>

  <section><h2>Scripted: no model</h2>
  <p class="sub">HEART's script runs 1 to 3 came before the fixes below; 4 to 6 after.</p>
  @SCRIPTED@</section>

  <section><h2>What HEART needed, found by these runs</h2>
  <p class="sub">Native needed nothing changed. Each fix below is in ARTIE, test first, and the runs after it are on the fixed code.</p>
  <ul>@FIXES@</ul></section>

  <section><h2>What this trial does not test</h2><ul>
    <li><b>A service that changes.</b> Today's GitHub is the GitHub the native model learned. New features, moved paths, renamed fields in the gateway: where knowledge from training goes stale and what the service offers doesn't. That is the next trial.</li>
    <li><b>The duplicate's link.</b> Both arms closed the duplicate as a duplicate; native sent the original's id and GitHub kept no link to it. The checker asks for the reason, not the link.</li>
  </ul></section>

  <p class="close">On a GitHub it knows, Claude alone was faster, needed fewer model calls, and got it right every time from the start. HEART got there too, after the three fixes these runs found, and is slower for the gateway and its two calls a move. The case for HEART is the service that moves, which this trial left still.</p>
</main>
<script>
(() => {
  const theme = (t) => {
    document.documentElement.dataset.theme = t;
    document.querySelectorAll("[data-th]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.th === t)));
    try { localStorage.setItem("nt-theme", t); } catch (e) { /* the page works without it */ }
  };
  document.querySelectorAll("[data-th]").forEach((b) => b.addEventListener("click", () => theme(b.dataset.th)));
  theme(document.documentElement.dataset.theme || "light");
})();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
