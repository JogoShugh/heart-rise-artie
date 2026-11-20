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
            f'<td class="num">{cread:,}</td><td class="num">{cwrite:,}</td><td class="note"><span class="full">{esc(note)}</span></td></tr>')
    out.append("</tbody></table></div>")
    return "".join(out)


def tile(label, native, heart, unit=""):
    return (f'<div class="tile"><div class="label">{esc(label)}</div><div class="pair">'
            f'<div><span class="tag native">Native</span><b>{native}</b>{unit}</div>'
            f'<div><span class="tag heart">HEART</span><b>{heart}</b>{unit}</div></div></div>')


HERE = Path(__file__).parent

# What the native script leans on, and where it is weak: each a verdict, a claim, the code it is about, and why.
PYTHON_POINTS = [
    ("weak", "Every address and field is the model's memory of GitHub.",
     '"/git/ref/heads/main", "/git/refs", "/contents/…", "/pulls/%d/merge", "sub_issue_id", "state_reason"',
     "Nothing in the script was read from the service: each path, each field, each value of state_reason is what "
     "the model learned of GitHub's REST API. Move one path, rename one field, add one required field, and the "
     "script fails at that line, or worse, sends something the service quietly ignores. It can't tell which."),
    ("weak", "Where its memory runs out, it guesses, and a guess that is ignored passes.",
     '"duplicate_of": crash_id, "duplicate_of_issue_id": crash_id',
     "It sends two field names for the duplicate's original, hoping one is right. GitHub ignored both: the "
     "duplicate is closed as a duplicate of nothing. Its own check asks only for state_reason, so the script says "
     "done. A field the service doesn't know is never refused, only dropped."),
    ("weak", "No validators: every write is blind.",
     "req(…) sends no If-Match on any PATCH, PUT or POST",
     "It never meets a 412 because it never says what it read. That is why it never stalled on GitHub's late "
     "change to a new pull request; it is also why it would overwrite a change someone else made between its "
     "read and its write, and never know."),
    ("weak", "It reads GitHub's internals to decide when to merge, and merges past failing checks.",
     'mergeable_state in ("clean", "unstable", "has_hooks")',
     "GitHub's word for whether a pull request can be merged is a field the model interprets, not an action the "
     "service offers. \"unstable\" means a check is failing or still running (one the branch doesn't require): "
     "the script merges it anyway, as it would one with no checks at all."),
    ("weak", "It does what was already done, and code the task never needed.",
     'PATCH … {"state": "closed", "state_reason": "completed"} on the crash issue; the reopen branch after creating it',
     "The merge has already closed the crash issue; the script closes it again, as a fixed step. It reopens the "
     "crash issue if it isn't open just after making it, a case that can't happen. A script that can't see what "
     "is possible does everything, in case."),
    ("weak", "It stops half done, and running it again makes a second set.",
     'sys.exit("FAILED: …") at the first error; nothing checks what an earlier run left',
     "A failure leaves issues, a branch and maybe a pull request behind, with nothing to say which. Run again with "
     "the same stamp, it opens the issues again; there is no record of what it did beyond the lines it printed."),
    ("weak", "No retries, no rate limits, a pinned API version.",
     '"X-GitHub-Api-Version": "2022-11-28"; urlopen with no retry',
     "A 502 or a secondary rate limit ends the run. The API version header pins it to the GitHub the model "
     "remembers; a version that drops a field it uses is found by failing."),
    ("good", "It reads every number, id and sha from the answers.",
     'crash_no, crash_id = crash["number"], crash["id"]; "sha": pr_now["head"]["sha"]',
     "Nothing from the run that taught it is written in: issue numbers, ids, the head sha the merge is held to, "
     "all come from GitHub's answers as it goes. It merges only the head it saw."),
    ("good", "It goes straight to what it needs.",
     'GET /git/ref/heads/main, then POST /git/refs',
     "23 requests where the ARTIE script makes 58: no list to page through, no block to come back to. On a "
     "GitHub that matches its memory, that is why it is three times faster."),
    ("note", "It checks its end state, its own way.",
     "the three GETs at the end",
     "It reads the three issues back and checks state, state reason and the lock: a good habit, but only for what "
     "it thought to check, and not the duplicate's original, which it had guessed at."),
]

KOTLIN_POINTS = [
    ("good", "Relations, not addresses.",
     'act("github:open-issue", …), follow("github:repos-list-branches"), act("github:close-duplicate", …)',
     "Each step names what the service offers; ARTIE finds the form or link in the representation it holds and "
     "builds the request from it. A moved path, a new required field, is the service's to say."),
    ("good", "Held to what was offered.",
     "heart-script.pins.yaml: each form's integrity, checked before every act",
     "A form that changed, or is no longer offered, stops the script before it sends anything: it refuses before "
     "its harm, never after."),
    ("good", "Every write says what it read.",
     "If-Match on every act; sync() after making a resource; a 412 decided again once",
     "It can't overwrite what it didn't see. Where GitHub changes something on its own, it reads again and "
     "decides again, within what its lock file allows."),
    ("good", "It waits on the service's word.",
     'until(tries = 20) { offers("github:merge") }',
     "Whether the pull request can be merged is whether merge is offered, not a field it interprets."),
    ("good", "Every value has a source.",
     "issueNumber = state.long(\"number\"); heart-script.derivation.yaml",
     "Each variable is read where the run found it, and the derivation file says, for each value, where it came "
     "from and by which rule."),
    ("weak", "It replays the run's route, not the shortest.",
     'follow("github:repos-list-branches") { follow("item", "main") { … } }',
     "The planner got to main by the branch list, so the script does too: 58 steps to the Python's 23, and the list "
     "pages as it grows. It is slower for every request going through the gateway."),
    ("weak", "It leans on what the service did on its own.",
     "no close of the crash issue",
     "The run made no act at the crash issue's close: the merge had closed it. The script relies on that, and on "
     "its check to catch it if GitHub ever doesn't."),
    ("note", "Its words can carry a value from the run.",
     'intent = "close it as a duplicate of the issue with id ${issueId} (#215)"',
     "An intent's words are only words: the act's values are its fields, here issueId, read fresh. But the "
     "words still say #215, the run's number."),
]


def points(items):
    return "<dl class=\"points\">" + "".join(
        f'<div><dt><span class="verdict {v}">{ {"weak": "weak", "good": "strong", "note": "note"}[v] }</span>'
        f'{esc(claim)}</dt><dd class="full"><code>{esc(code)}</code></dd><dd class="full">{esc(why)}</dd></div>'
        for v, claim, code, why in items) + "</dl>"


STRICTNESS = [
    ("Is the act allowed now?", "The form must be in the representation held, or it refuses before sending.",
     "Sends regardless; learns from the status."),
    ("Is the body valid?", "Each field checked against the form's JSON Schema before sending; one it doesn't name is refused.",
     "Sends any field; GitHub drops the ones it doesn't know, quietly (duplicate_of)."),
    ("Has the form changed since the script was made?", "The lock file pins each form's integrity, checked before every act.",
     "Can't tell."),
    ("Has the resource changed since it was read?", "If-Match on every act; a 412 is read again and decided again.",
     "Writes blind."),
    ("What does a refusal mean?", "409 not allowed here, 412 stale, 422 invalid or a rule said no, 428 precondition "
     "wanted: each its own handling.", "Anything but 200, 201 or 204 ends it; within those it goes on."),
    ("Was something made?", "201 with a Location is told from 200: what was made is held, and named.",
     "Any success is the same."),
    ("After a block", "The resource the block began at is read again (\"return to\"): the next step acts on what is.",
     "Acts on what it last saw."),
    ("Links", "Followed only where offered.", "Builds every address itself."),
]


# What the Python would have to build, against GitHub's own API, to do what ARTIE does in its lenient mode: the
# check, what ARTIE has from HEART, what the Python would need, and whether GitHub lets it at all.
MATCHING = [
    ("The act allowed now",
     "Offered or not, in the representation held, by the server's own rules.",
     "Read the resource first, then re-implement GitHub's rules for each act: an issue's state and lock, the "
     "caller's permission (GET /repos → permissions), branch protection (GET /branches/{b}/protection), required "
     "checks and reviews before a merge. Server logic, copied into every client, drifting from GitHub's.",
     "partly"),
    ("The body valid",
     "Each form carries its JSON Schema; ARTIE checks the body against it, and a field it doesn't name is refused.",
     "Fetch GitHub's OpenAPI description (github/rest-api-description, megabytes), find the operation by path and "
     "method, resolve its $refs, and validate with a JSON Schema library (not in Python's standard library). It "
     "still wouldn't refuse an unknown field: GitHub's schemas don't close them, so that list is hand-kept.",
     "partly"),
    ("The form unchanged since the script was made",
     "The lock file pins each form's integrity (a hash of its definition), checked before every act.",
     "Hash each operation's schema from the description at record time; at run time fetch the description again "
     "and compare. The description changes often; X-GitHub-Api-Version pins behaviour, not what a hash can check.",
     "partly"),
    ("The resource unchanged since it was read",
     "If-Match on every act; the gateway compares it with what it reads upstream, and answers 412.",
     "GitHub's REST API takes no If-Match on writes: ETags are for conditional GETs. The script can read, compare "
     "updated_at or the ETag, and write, leaving a race between; only refs, contents and merges take a sha to hold "
     "to. The gateway narrows the same gap; it can't close it either, upstream doesn't let it.",
     "no"),
    ("What a refusal means",
     "409 not allowed now, 412 stale, 422 invalid or a rule said no, 428 precondition wanted, each with HEART's "
     "meaning and the server's detail.",
     "A table of GitHub's own: 404 for no permission, 422 for most things, 405 for a merge not allowed, 409 for a "
     "conflict, 403 for rate limits and abuse detection; the message text parsed to tell them apart.",
     "partly"),
    ("What was made",
     "201 with a Location: what was made is held and named, and its address is the server's.",
     "Read the number and url from each body, operation by operation; some answer 201 with a Location, some 200.",
     "yes"),
    ("Reading again after a block",
     "The resource the block began at is read again, so the next step acts on what is.",
     "A GET after each nested sequence, by an address the script builds.",
     "yes"),
    ("Links followed only where offered",
     "Addresses come from the representation; the script names relations.",
     "Follow GitHub's *_url fields instead of building paths: most are RFC 6570 templates ({/sha}), so a template "
     "expander is needed; they say where, never whether it is allowed now.",
     "partly"),
    ("Lists paged on",
     "next links, followed until the titled link is found.",
     "Parse the Link header for rel=\"next\", page by page.",
     "yes"),
    ("Waiting on the service's word",
     "until { offers(\"github:merge\") }: merge is offered or it isn't.",
     "Poll mergeable and interpret mergeable_state, knowing which states are safe (the trial's script took "
     "\"unstable\").",
     "partly"),
    ("Deciding again after a 412",
     "Read again; if the form is still offered and the lock file holds, the act once more.",
     "With no 412 to get, nothing to decide again; a write that raced is simply applied.",
     "no"),
    ("A record of what it did and why",
     "The capture: each step, each value's source, the exchanges; the lock file; the derivation.",
     "Logging written for it: requests, answers, where each value came from.",
     "yes"),
]


def matching():
    words = {"yes": "GitHub allows it", "partly": "partly", "no": "GitHub doesn't allow it"}
    rows = "".join(
        f'<tr><td>{esc(c)}</td><td>{esc(a)}</td><td>{esc(p)}</td>'
        f'<td><span class="verdict {"good" if g == "yes" else "note" if g == "partly" else "weak"}">'
        f'{esc(words[g])}</span></td></tr>'
        for c, a, p, g in MATCHING)
    return ('<div class="wrap"><table class="match"><thead><tr><th>Check</th><th><span class="tag heart">HEART</span> '
            'what ARTIE has</th><th><span class="tag native">Native</span> what the Python would have to build</th>'
            f'<th>Against GitHub</th></tr></thead><tbody>{rows}</tbody></table></div>')


def strictness():
    rows = "".join(f'<tr><td>{esc(q)}</td><td>{esc(a)}</td><td>{esc(p)}</td></tr>' for q, a, p in STRICTNESS)
    return ('<div class="wrap"><table class="strict"><thead><tr><th>Check</th><th><span class="tag heart">HEART</span> '
            'ARTIE script</th><th><span class="tag native">Native</span> Python script</th></tr></thead>'
            f'<tbody>{rows}</tbody></table></div>')


def scripts():
    python = (HERE / "runs" / "native-script.py").read_text()
    kotlin = (HERE / "runs" / "heart-script.kts").read_text()
    return f"""
  <section><h2>Two scripts, one task</h2>
  <p class="sub full">Both do the triage task with no model; both passed every run made on the code as it ended. Claude
  wrote the Python in one call from its own run's transcript. <code>artie generate</code> wrote the Kotlin from one
  HEART run's capture, with a lock file beside it.</p>
  <div class="pair2">
    <div class="code"><div class="head"><span><span class="tag native">Native</span> native-script.py</span>
      <span>{python.count(chr(10))} lines · 23 requests · 22 s</span></div>
      <pre><code class="language-python">{esc(python)}</code></pre></div>
    <div class="code"><div class="head"><span><span class="tag heart">HEART</span> heart-script.kts</span>
      <span>{kotlin.count(chr(10))} lines · 58 steps · 68 s</span></div>
      <pre><code class="language-kotlin">{esc(kotlin)}</code></pre></div>
  </div></section>

  <section><h2>What each checks, step by step</h2>
  <p class="sub full">Much of the time between them is strictness. Before and after each act, ARTIE checks what
  the Python never does: it won't send what it can't stand behind, and a refusal stops it before its harm. The
  Python is fast because it trusts its memory and takes any 2xx as success, which is how an ignored field passed.</p>
  {strictness()}</section>

  <section><h2>To do what ARTIE does, the Python would have to…</h2>
  <p class="sub full">Against GitHub's own API, with no gateway: what the Python would have to build, check by
  check, to match ARTIE in its lenient mode. Some of it GitHub doesn't allow at all: it takes no validator on a
  write, so no client can be sure a write didn't race. The rest is GitHub's own rules and descriptions, rebuilt in
  every client, per API; HEART puts them in one place, the server, and every client reads them there.</p>
  {matching()}
  <p class="full">Tallied: of twelve, GitHub allows four outright, six in part, and two not at all. Doing the six
  in part means an OpenAPI description, a schema validator, a template expander, and GitHub's rules per act,
  kept up to date by hand. That is a gateway, built inside one client.</p></section>

  <section><h2>The Python, read closely</h2>
  <p class="sub full">It is good code for what it is: what one model remembers of one API, written down. Its weaknesses
  are the weaknesses of that.</p>
  {points(PYTHON_POINTS)}</section>

  <section><h2>The Kotlin, for fairness</h2>
  {points(KOTLIN_POINTS)}</section>

  <p class="close">The Python is faster because it knows GitHub. The Kotlin is slower because it asks. The trial
  ran against the GitHub the model knows; the next one changes it.</p>"""


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
    fixes = "".join(f'<li><b>{esc(t)}</b> <span class="commit">{esc(c)}</span><p class="full">{esc(why)}</p></li>'
                    for c, t, why in FIXES)
    page = TEMPLATE.replace("@TILES@", tiles).replace("@REASONED@", table("reasoned")) \
        .replace("@SCRIPTED@", table("scripted")).replace("@FIXES@", fixes).replace("@SCRIPTS@", scripts())
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
  --kw: #8a3f8f; --str: #2f6f3f; --num: #a0522d; --fn: #1f5a8a; --tpl: #b0641c;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #111816; --surface: #18211e; --fg: #e2ebe6; --muted: #98a9a1; --rule: #2a3732; --link: #8ab8e6;
  --native: #e39a76; --native-soft: #33231c; --heart: #7fd0c8; --heart-soft: #16302d;
  --ok: #9fd6ac; --ok-soft: #1b2e21; --bad: #e58a84; --bad-soft: #3a1f1d;
  --kw: #d79ad9; --str: #9fd6ac; --num: #e0a080; --fn: #8cc0ea; --tpl: #e6a35c; color-scheme: dark; } }
:root[data-theme="dark"] {
  --bg: #111816; --surface: #18211e; --fg: #e2ebe6; --muted: #98a9a1; --rule: #2a3732; --link: #8ab8e6;
  --native: #e39a76; --native-soft: #33231c; --heart: #7fd0c8; --heart-soft: #16302d;
  --ok: #9fd6ac; --ok-soft: #1b2e21; --bad: #e58a84; --bad-soft: #3a1f1d;
  --kw: #d79ad9; --str: #9fd6ac; --num: #e0a080; --fn: #8cc0ea; --tpl: #e6a35c; color-scheme: dark; }
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
.seg button:focus-visible { outline: 2px solid var(--link); outline-offset: 2px; }
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
.tabs { display: flex; gap: .4rem; border-bottom: 1px solid var(--rule); }
.tabs button { font: 600 .95rem var(--body); background: none; border: 0; border-bottom: 2px solid transparent; color: var(--muted); padding: .4rem .2rem .6rem; margin-right: 1rem; cursor: pointer; }
.tabs button[aria-selected="true"] { color: var(--fg); border-bottom-color: var(--fg); }
.tabs button:focus-visible { outline: 2px solid var(--link); outline-offset: 2px; }
.tab { display: grid; gap: 2rem; }
[hidden] { display: none !important; }
:root:not([data-detail="full"]) .full { display: none !important; }
.toggles { display: flex; flex-wrap: wrap; gap: .6rem 1rem; align-items: center; }
.code { border: 1px solid var(--rule); border-radius: 10px; background: var(--surface); overflow: hidden; }
.code .head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: .4rem 1rem; padding: .55rem .9rem; border-bottom: 1px solid var(--rule); background: var(--bg); font-size: .85rem; color: var(--muted); }
.code pre { margin: 0; padding: .8rem .9rem; overflow: auto; max-height: 34rem; font: .78rem/1.55 var(--mono); }
.code pre code { background: none; padding: 0; }
.hljs-keyword, .hljs-built_in, .hljs-literal { color: var(--kw); } .hljs-string { color: var(--str); }
.hljs-comment { color: var(--muted); font-style: italic; } .hljs-number { color: var(--num); }
.hljs-title, .hljs-function .hljs-title { color: var(--fn); } .hljs-subst, .hljs-variable, .hljs-template-variable { color: var(--tpl); }
table.match { min-width: 60rem; } table.match td:first-child { font-weight: 600; width: 16%; }
table.match td:nth-child(2) { width: 26%; } table.match td:last-child { white-space: nowrap; }
table.strict { min-width: 44rem; } table.strict td:first-child { font-weight: 600; width: 22%; }
.pair2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 34rem), 1fr)); gap: 1rem; }
.pair2 > * { min-width: 0; }
dl.points { display: grid; gap: .9rem; margin: 0; } dl.points dt { font-weight: 600; } dl.points dd { margin: .2rem 0 0; color: var(--muted); max-width: 72ch; }
dl.points code, .tab p code, li code { font: .85em var(--mono); color: var(--fg); }
.verdict { display: inline-block; font: 600 .68rem var(--body); letter-spacing: .05em; text-transform: uppercase; border-radius: 4px; padding: .05rem .35rem; margin-right: .4rem; vertical-align: .1em; }
.verdict.weak { background: var(--bad-soft); color: var(--bad); } .verdict.good { background: var(--ok-soft); color: var(--ok); } .verdict.note { background: var(--bg); color: var(--muted); border: 1px solid var(--rule); }
.close { max-width: 66ch; font-family: var(--display); font-size: 1.1rem; line-height: 1.5; }
</style>
</head>
<body>
<main>
  <header>
    <div><h1>Native and HEART, Measured</h1>
    <p class="lede">The GitHub triage task, 29 steps, done by Claude alone from what it knows of GitHub's REST API, and by Claude through ARTIE and the HEART gateway. Three runs each with a model choosing every move, then three each by a script made from a run, with no model. One checker judged every run by what GitHub held after it: 23 checks.</p></div>
    <div class="toggles"><div class="seg"><span class="seg-label" id="detail-label">Detail</span><div role="group" aria-labelledby="detail-label" style="display:flex;gap:.3rem"><button type="button" data-detail="brief">Brief</button><button type="button" data-detail="full">Full</button></div></div>
    <div class="seg"><span class="seg-label" id="theme-label">Theme</span><div role="group" aria-labelledby="theme-label" style="display:flex;gap:.3rem"><button type="button" data-th="light">Light</button><button type="button" data-th="dark">Dark</button></div></div></div>
  </header>

  <nav class="tabs" role="tablist" aria-label="Report">
    <button type="button" role="tab" id="tab-results" aria-controls="results" data-tab="results">Results</button>
    <button type="button" role="tab" id="tab-scripts" aria-controls="scripts" data-tab="scripts">The scripts</button>
  </nav>

  <div class="tab" id="results" role="tabpanel" aria-labelledby="tab-results">
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
  <p class="sub full">Native's input tokens are nearly all read from its prompt cache: one conversation that grows. HEART sends each call a fresh view of the resource held, and makes two calls a move, the planner's and the reasoner's.</p>
  @REASONED@</section>

  <section><h2>Scripted: no model</h2>
  <p class="sub full">HEART's script runs 1 to 3 came before the fixes below; 4 to 6 after.</p>
  @SCRIPTED@</section>

  <section><h2>What HEART needed, found by these runs</h2>
  <p class="sub full">Native needed nothing changed. Each fix below is in ARTIE, test first, and the runs after it are on the fixed code.</p>
  <ul>@FIXES@</ul></section>

  <section><h2>What this trial does not test</h2><ul>
    <li><b>A service that changes.</b> Today's GitHub is the GitHub the native model learned. New features, moved paths, renamed fields in the gateway: where knowledge from training goes stale and what the service offers doesn't. That is the next trial.</li>
    <li><b>The duplicate's link.</b> Both arms closed the duplicate as a duplicate; native sent the original's id and GitHub kept no link to it. The checker asks for the reason, not the link.</li>
  </ul></section>

  <p class="close">On a GitHub it knows, Claude alone was faster, needed fewer model calls, and got it right every time from the start. HEART got there too, after the three fixes these runs found, and is slower for the gateway and its two calls a move. The case for HEART is the service that moves, which this trial left still.</p>
  </div>

  <div class="tab" id="scripts" role="tabpanel" aria-labelledby="tab-scripts" hidden>
@SCRIPTS@
  </div>
</main>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
<script>
(() => {
  try { document.querySelectorAll("pre code").forEach((c) => hljs.highlightElement(c)); } catch (e) { /* plain text then */ }
  const tabs = document.querySelectorAll("[data-tab]");
  const show = (id) => {
    tabs.forEach((b) => b.setAttribute("aria-selected", String(b.dataset.tab === id)));
    document.querySelectorAll(".tab").forEach((t) => { t.hidden = t.id !== id; });
  };
  tabs.forEach((b) => b.addEventListener("click", () => show(b.dataset.tab)));
  show(location.hash === "#scripts" ? "scripts" : "results");
  const detail = (d) => {
    document.documentElement.dataset.detail = d;
    document.querySelectorAll("[data-detail]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.detail === d)));
    try { localStorage.setItem("nt-detail", d); } catch (e) { /* the page works without it */ }
  };
  document.querySelectorAll("[data-detail]").forEach((b) => b.addEventListener("click", () => detail(b.dataset.detail)));
  let kept = "brief";
  try { kept = localStorage.getItem("nt-detail") || "brief"; } catch (e) { /* brief */ }
  detail(kept);
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
