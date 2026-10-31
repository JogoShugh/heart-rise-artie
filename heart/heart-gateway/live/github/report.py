#!/usr/bin/env python3
"""
Builds docs/reports/github-pulls/index.html from capture.py's captures and
the screenshots beside them: each pull request read two ways, GitHub's own
answer and HEART's through the gateway, side by side.

    python3 report.py REPORT_DIR
"""
import html
import json
import os
import sys

from jsonview import JsonView

# Every JSON example on the page: put on the page once, as data, and shown by json-view.js when opened.
VIEW = JsonView()

MEANT = {
    1: "clean", 2: "a draft", 3: "in conflict", 4: "merged", 5: "closed",
    6: "blocked by a required review", 7: "meant to be behind its base; GitHub says clean",
}


def esc(text):
    return html.escape(text, quote=False)
















def optimization(captures):
    """The collapsed section: the same reads at L1 and L2, the shared sets, and what they cost."""
    one = captures[0]
    l1, l2 = one["rungs"]["L1"], one["rungs"]["L2"]
    schema = l2["schemas"][0]
    rows = "".join(
        f"<tr{' class=\"shared\"' if c['rungs']['L1']['read']['response']['body']['_links']['hr:forms']['href'] == l1['read']['response']['body']['_links']['hr:forms']['href'] else ''}>"
        f"<th>#{c['number']}</th><td>{esc(offered(c))}</td>"
        f"<td><code>{esc(c['rungs']['L1']['read']['response']['body']['_links']['hr:forms']['href'].rsplit('/', 1)[-1])}</code></td>"
        f"<td class=\"num\">{c['sizes']['L0']:,}</td><td class=\"num\">{c['sizes']['L1']:,}</td><td class=\"num\">{c['sizes']['L1 set']:,}</td></tr>"
        for c in captures
    )
    return f"""
  <details class="advanced wide">
    <summary>Optimization: affordance sets and schemas, shared and cached</summary>
    <div class="adv">
      <p>Every answer above is HEART's first rung, L0: the state, its forms and their full schemas in one response, revalidated on every read. A client that asks for more with <code>Prefer</code> gets the same state with its forms moved out: at <b>L1</b> to an affordance set, at <b>L2</b> with each form's schema moved out again. Each is named by the hash of its own bytes, so it never changes, is cached for a year, and is checked against the integrity its link carries. Pull requests in the same state, with the same reasons, link the same set.</p>
      <div class="stack">
        {VIEW.pair(one["heart"]["response"]["body"], l1["read"]["response"]["body"], "L0: #1 with its forms",
                   "L1: the same read, forms by link", "L1: the same read of #1, its forms moved out to a shared set",
                   VIEW.request_line("HEART", "GET", l1["read"]["request"]["url"], headers={"Prefer": "forms=link"}))}
        {VIEW.pair(l1["set"]["response"]["body"], l2["set"]["response"]["body"], "the L1 set, schemas inline",
                   "the L2 set, schemas by link", "L2: the set's schemas by link too",
                   VIEW.request_line("HEART", "GET", l2["set"]["request"]["url"]))}
        {VIEW.one(schema["response"]["body"], "the schema", "Each schema, fetched once, by its address",
                  VIEW.request_line("HEART", "GET", schema["request"]["url"]))}
      </div>
      <p class="who">Which pull requests share a set, and what each read weighs</p>
      <div class="table-scroll">
        <table>
          <thead><tr><th>#</th><th>Offers</th><th>Affordance set</th><th class="num">L0 bytes</th><th class="num">L1 bytes</th><th class="num">Set bytes, once</th></tr></thead>
          <tbody>{rows}</tbody>
        </table>
      </div>
      <p>#1 and #7 are both open and clean, so they link one set: a client reading both fetches it once. #2, #3 and #6 all offer only close, but each leaves merge out for its own reason (a draft, a conflict, a review), and the reasons are part of what a set says, so each has its own. Across a real repository's pull requests, states and reasons repeat, and so do the sets.</p>
      <p>What it saves here is small: about a kilobyte per read, since GitHub's own data, some fifteen kilobytes of it, is most of every answer. The sets pay off where forms and schemas are large beside the state, and where a client reads many resources in few states: it carries the controls once, and every read after is the state and a link.</p>
    </div>
  </details>"""




NOTES = {
    "Close #1": "GitHub has no close operation: a client must know that closing is a PATCH of state. HEART offered close as a form; after it, reopen and edit are offered.",
    "Reopen #1": "Reopened, the pull request offers close, but not merge yet: GitHub is working out mergeability again (mergeable: null), and the connector's rule says to read it again shortly.",
    "Merge #7": "The gateway fills merge's sha from the head the client's view had, so GitHub merges only that head. After it, #7 offers only edit, and says why reopen is absent.",
    "Merge #1 after another push": "Another client pushed to the branch after this one read #1. The read still matched (GitHub moved the head a moment later), so the gateway's own read just before the act caught it: 412, and GitHub was never asked to merge.",
    "Merge #6": "Merge was never offered on #6. A client that sends it anyway is refused 409 with the reason, and GitHub is never asked.",
    "Edit #1's title and body": "Edit is the same GitHub operation as close, with the client's title and body instead of a fixed state. The form asks for those two fields and nothing else.",
    "Request a review from nobody": "GitHub's own schema says a review request names reviewers or teams, at least one. The form keeps that rule, so the gateway refuses the empty request itself, and GitHub is never asked.",
    "Request a review from someone": "GitHub refuses a reviewer who isn't a collaborator. Its 422 names no field, so the gateway passes on GitHub's own sentence as the violation.",
    "Update #1's branch after a push": "A push to main put #1's branch behind. The gateway fills expected_head_sha from the head the client saw, so GitHub updates only that head.",
    "Open an issue from the repository": "GitHub's API has a create operation a client must know to POST. HEART reads the repository at GitHub's own path and offers open-issue as a form; the answer is 201, and Location names the new issue, which the client follows.",
    "Comment on the issue": "Commenting is an act the issue offers. GitHub's answer is the new comment; the gateway answers 201 with its address, at GitHub's own path for a comment.",
    "Follow the issue's comments link": "The issue links its comments by a relation of the profile, filled from its own URL. The client follows the link; it never builds the address.",
    "Edit the comment": "A comment is a resource too: its author, or anyone with push access, is offered edit.",
    "Edit the issue's title": "Edit is GitHub's issues/update with only the client's title and body. Shared by name with pull requests: one relation, either resource.",
    "Label it": "GitHub's add-labels body is oneOf several shapes; the form keeps the object one, the labels array.",
    "Take the label off": "GitHub names the label in its path, /labels/{name}. The form asks for name; the gateway puts it in the path, encoded, and sends no body.",
    "Assign the owner": "issues/add-assignees, with the client's assignees.",
    "Unassign the owner": "issues/remove-assignees: a DELETE with a body, as GitHub's description says.",
    "Lock the conversation": "issues/lock, with the client's lock_reason. Locked, the issue offers unlock, not lock.",
    "Lock it again": "Lock was not offered on a locked issue. Sent anyway, it is refused 409 with the reason, and GitHub is never asked.",
    "Unlock the conversation": "issues/unlock: a DELETE with nothing to say sends no body.",
    "Add the second issue as a sub-issue": "GitHub names a sub-issue by its id, not its number. The form says so in its description.",
    "Follow the issue's sub-issues link": "Sub-issues are issues, so the gateway serves the list itself, at GitHub's path, as a page of issue links.",
    "Remove the sub-issue": "issues/remove-sub-issue: a DELETE of /sub_issue with the id in its body.",
    "Close it after another change": "Someone changed the issue straight on GitHub after this client read it. GitHub's issue writes take no version, so the gateway's read just before acting is the guard: 412, and GitHub was never asked to close it.",
    "Close the second issue as not planned": "One of three ways to close, each GitHub's issues/update with its own fixed state_reason.",
    "Close the issue as completed": "close is shared with pull requests; on an issue its body is state closed, state_reason completed.",
    "Reopen it": "Reopened with state_reason reopened, as GitHub's own page does it.",
    "Close it as a duplicate": "close-duplicate takes duplicate_issue_id from the client, beside its fixed state and reason.",
    "Update #1's branch again": "Already up to date, GitHub refuses with 422. The connector says that status means not now, so the client gets 409 with GitHub's words.",
}


def note(name):
    return next((text for key, text in NOTES.items() if name.startswith(key)), "")


def act_steps(steps):
    """Each act: the request GitHub's API needs, the form HEART offered and what the act changed, collapsed."""
    return '<div class="stack">' + "".join(
        f'<div class="actstep"><p class="note">{esc(note(step["name"]))}</p>'
        + VIEW.act(step, f'<span class="st s{step["act"]["response"]["status"] // 100}">'
                         f'{step["act"]["response"]["status"]}</span> {esc(step["name"])}')
        + "</div>"
        for step in steps if step.get("plain")
    ) + "</div>"


def acting(steps, more, unsettled):
    if not steps:
        return ""
    later = ""
    if more:
        held = ""
        if unsettled:
            refused = [s for s in unsettled if s["act"]["response"]["status"] == 412]
            held = f"""
    <p>The first time, both branch updates were refused 412. Right after the push to main, GitHub was still working out #1's mergeability: the client read <code>mergeable: null</code>, and by the time it acted GitHub had filled it in, so the pull request had changed and the client's ETag no longer matched. That is the validator doing its job: the client acted on a view that was no longer current. The run below reads again until GitHub has settled, as the connector's own reason tells a client to (<a href="captures/acts-more-unsettled.json">the first run</a>, {len(refused)} refusals).</p>"""
        later = f"""
  <section class="wide acting">
    <h2>Editing, review requests and branch updates</h2>
    <p>Three more acts, each declared in the connector's resource file and nothing else, sent on the same repository the same way. Captured in <a href="captures/acts-more.json">captures/acts-more.json</a>.</p>{held}
    {act_steps(more)}
  </section>"""
    return f"""
  <section class="wide acting">
    <h2>Acting through the forms</h2>
    <p>Phase 10, on the same repository: each act is the form HEART offered, sent as any HEART client sends it, with the ETag of the read it came from. On the left, what a client of GitHub's API would have had to build itself. Captured in <a href="captures/acts.json">captures/acts.json</a>.</p>
    {act_steps(steps)}
  </section>{later}"""


def offered(capture):
    forms = capture["heart"]["response"]["body"].get("_forms", {})
    return ", ".join(k.split(":")[1] for k in forms) or "nothing"


def section(capture, report_dir):
    n = capture["number"]
    github, heart = capture["github"], capture["heart"]
    title = github["response"]["body"]["title"]
    shot = f"img/pr-{n}.png"
    reasons = (capture.get("explanation") or {}).get("response", {}).get("body", {}).get("unavailable", [])
    why = " ".join(f'No {r["title"].lower()}: {r["reason"]}' for r in reasons)
    return f"""
  <section class="pull wide" id="pull-{n}">
    <div class="head">
      <p class="eyebrow">Pull request #{n} · {esc(MEANT[n])}</p>
      <h2>{esc(title)}</h2>
      <p class="where">HEART offers <b>{esc(offered(capture))}</b>. {esc(why)}</p>
    </div>
    <figure class="shot">
      <a href="https://github.com/JogoShugh/heart-github-test/pull/{n}"><img src="{shot}" alt="GitHub's page for pull request #{n}, signed out: state and commits, no merge box"></a>
      <figcaption>GitHub's own page, signed out. Its HTML leaves out the merge box for a viewer who can't use it.</figcaption>
    </figure>
    <p class="note">GitHub's answer has state, and links to read more; nothing says what may be done now. HEART's is the same data, with a form for each act allowed now and a link to why the rest are absent: open the box to see exactly what it adds.</p>
    {VIEW.read(capture, "GitHub's answer, and what HEART's adds")}
    <p class="note">Full exchanges, unabridged: <a href="captures/pull-{n}.json">captures/pull-{n}.json</a>.</p>
  </section>"""


def issues(report_dir):
    """Issues the same way: an issue read both ways, its links followed, and every issue act through the forms."""
    folder = os.path.join(report_dir, "../github-gateway-status/captures")
    path = lambda name: os.path.join(folder, name)
    if not os.path.exists(path("issue-open.json")) or not os.path.exists(path("issues-acts.json")):
        return ""
    read = json.load(open(path("issue-open.json")))
    steps = json.load(open(path("issues-acts.json")))
    github, heart = read["github"], read["heart"]
    n = read["path"].rsplit("/", 1)[1]
    return f"""
  <section class="pull wide" id="issues">
    <div class="head">
      <p class="eyebrow">Issues · the same repository · 2026-10-17</p>
      <h2>Issues, the same way</h2>
      <p class="where">The same gateway, one more resource file and reader for issues, one for the repository, one for comments. An open issue, #{esc(n)}, read twice:</p>
    </div>
    {VIEW.read(read, "GitHub's answer, and what HEART's adds")}
    <p class="note">Besides the forms, HEART adds <code>github:issues-list-comments</code> and <code>github:issues-list-sub-issues</code>, the lists under the issue, linked from it. Full exchanges: <a href="../github-gateway-status/captures/issue-open.json">issue-open.json</a>, and the repository, a locked issue, three closed ones, a comment and both lists beside it.</p>
  </section>

  <section class="wide acting">
    <h2>Every issue act, through the forms</h2>
    <p>New things come from forms a resource offers: the repository's <code>open-issue</code>, the issue's <code>comment</code>, each answered 201 with Location. Then each of the issue's acts, sent with the ETag of the read it came from, after the client has read until GitHub settled. Captured by <code>live/github/issues.py</code> in <a href="../github-gateway-status/captures/issues-acts.json">issues-acts.json</a>.</p>
    {act_steps(steps)}
  </section>"""


def page(captures, report_dir, steps=None, more=None, unsettled=None):
    rows = "".join(
        f"<tr><th>#{c['number']}</th><td><a href=\"#pull-{c['number']}\">{esc(c['github']['response']['body']['title'])}</a></td>"
        f"<td>{esc(c['github']['response']['body']['state'])}, {esc(str(c['github']['response']['body']['mergeable_state']))}</td>"
        f"<td>{esc(offered(c))}</td></tr>"
        for c in captures
    )
    sections = "".join(section(c, report_dir) for c in captures)
    with open(os.path.join(os.path.dirname(__file__), "report.css")) as css:
        style = css.read()
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="Seven real GitHub pull requests and their issues, each read two ways: GitHub's REST answer, with state and no controls, beside HEART's through the gateway, with forms for what is allowed now and reasons for the rest.">
<title>GitHub Pulls, Two Ways</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:opsz,wght@7..72,500;7..72,650&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>
{style}
</style>
{VIEW.assets()}
</head>
<body>
<main class="wrap">
  <header>
    <p class="eyebrow">Take HEART · Part 2, live · 2026-10-16</p>
    <h1>GitHub Pulls, Two Ways</h1>
    <p class="lede">Seven real pull requests on GitHub, each read twice with the same token: straight from GitHub's REST API, and through the HEART gateway, which knows GitHub only by its own OpenAPI description and one connector folder.</p>
  </header>

  <section>
    <p class="thesis">GitHub tells a client what a pull request <em>is</em>. HEART also tells it what it may <em>do</em> now, how, and why not the rest.</p>
    <p>The repository <a href="https://github.com/JogoShugh/heart-github-test">JogoShugh/heart-github-test</a> was seeded by <code>heart-gateway/live/github/seed.py</code> with a pull request in each state it can be in. Every exchange below is real and unedited but for the token, written <code>&lt;token&gt;</code>, and for the pull request's other data, the same on both sides, which is counted rather than shown. The reads are all <code>GET</code>s; the acts, last, changed the pull requests they name, on that repository only.</p>
  </section>

  <section class="wide">
    <div class="table-scroll">
      <table>
        <thead><tr><th>#</th><th>Pull request</th><th>GitHub says</th><th>HEART offers</th></tr></thead>
        <tbody>{rows}</tbody>
      </table>
    </div>
  </section>

  <section>
    <h2>How to read each pair</h2>
    <ul>
      <li><b>Left, GitHub.</b> Forty-odd fields of data, and <code>_links</code> to more data: the HTML page, comments, commits, statuses. Every link is something to read. Nothing in the answer is something to do.</li>
      <li><b>Right, HEART.</b> The same data, and, <span class="addkey">highlighted</span>, what HEART adds: a form for each act allowed now (its method, its target, the JSON Schema of its body, taken from GitHub's own description) and an <code>hr:unavailable</code> link to why the other acts are not.</li>
      <li><b>Above, GitHub's own page.</b> Signed out, its HTML shows no merge box at all: GitHub's web page already leaves out controls its viewer can't use. Its API never says either way.</li>
    </ul>
  </section>
{sections}
{acting(steps, more, unsettled)}
{issues(report_dir)}
{optimization(captures)}
  <section>
    <h2>Where the reasons come from</h2>
    <p>GitHub's API carries the facts, <code>draft</code>, <code>merged</code>, <code>mergeable</code>, <code>mergeable_state</code>, and the caller's <code>permissions</code> on the repository, but no decisions. The gateway's GitHub connector, YAML files in <code>heart-gateway/connectors/github</code>, writes those decisions down once, in order, with their reasons, and GitHub's own refusals stay the final word. Every client then reads them as forms and reasons instead of each one hard-coding GitHub's rules.</p>
  </section>
{VIEW.data()}
</main>
</body>
</html>
"""


def main():
    report_dir = sys.argv[1]
    folder = os.path.join(report_dir, "captures")
    captures = [json.load(open(os.path.join(folder, f"pull-{n}.json"))) for n in range(1, 8)]
    acts = os.path.join(folder, "acts.json")
    steps = json.load(open(acts)) if os.path.exists(acts) else []
    load = lambda name: json.load(open(os.path.join(folder, name))) if os.path.exists(os.path.join(folder, name)) else []
    with open(os.path.join(report_dir, "index.html"), "w") as out:
        out.write(page(captures, report_dir, steps, load("acts-more.json"), load("acts-more-unsettled.json")))
    print("wrote", os.path.join(report_dir, "index.html"))


if __name__ == "__main__":
    main()
