#!/usr/bin/env python3
"""
Builds docs/reports/github-gateway-status/index.html: what the GitHub
gateway does, tested against the fake GitHub and against real GitHub, what
each state allows and why not the rest, with the live exchanges as
collapsible JSON. Tables first; no narrative.

    python3 status-report.py REPO_ROOT
"""
import html
import json
import os
import sys

from jsonview import JsonView

ACTS = ["close", "reopen", "merge", "edit", "request-review", "update-branch"]
FAKE, REAL, NO = "fake", "real", "—"

# (area, capability, fake, real, evidence): fake = the features and conformance kit against the
# in-memory GitHub; real = heart-github-test through the gateway.
CAPABILITIES = [
    ("Load", "Start on GitHub's full description (13.5 MB), sliced to 7 operations", "✓", "✓", "1.1 s, ~300 MB; github.feature"),
    ("Read", "Get a pull request at /repos/{owner}/{repo}/pulls/{pull_number}", "✓", "✓", "captures/pull-N.json"),
    ("Read", "Search a repository's pull requests, filters state, sort, direction", "✓", "✓", "github.feature; live search"),
    ("Read", "Bookmark: search and lookup forms; home document; profile", "✓", "✓", "github.feature; live bookmark"),
    ("Read", "L1 / L2: affordance sets and schemas by link, shared, immutable", "✓", "✓", "conformance kit; captures rungs"),
    ("Act", "close (PATCH state closed)", "✓", "✓", "github-acts.feature; acts.json"),
    ("Act", "reopen (PATCH state open)", "offered", "✓", "acts.json"),
    ("Act", "merge, sha bound from /head/sha", "✓", "✓", "github-acts.feature; acts.json #7"),
    ("Act", "edit, title and body", "✓", "✓", "github-more-acts.feature; acts-more.json"),
    ("Act", "request-review, accepted", "✓", NO, "needs a second collaborator"),
    ("Act", "request-review naming nobody: 422 by the form, GitHub not asked", "✓", "✓", "acts-more.json"),
    ("Act", "request-review, non-collaborator: 422 in GitHub's words", "✓", "✓", "acts-more.json"),
    ("Act", "update-branch, expected_head_sha bound", "✓", "✓", "acts-more.json"),
    ("Act", "update-branch, up to date: 409 in GitHub's words", "✓", "✓", "acts-more.json"),
    ("Guard", "428 without If-Match", "✓", NO, "conformance kit"),
    ("Guard", "422 body outside the form's schema; GitHub not asked", "✓", "✓", "conformance kit; acts-more.json"),
    ("Guard", "412: ETag no longer matches (state changed since the read)", "✓", "✓", "acts-more-unsettled.json"),
    ("Guard", "412: gateway's re-read before acting finds the state changed", "✓", "✓", "acts.json #1"),
    ("Guard", "412: GitHub's own sha check (409) on merge", "✓", NO, "the re-read caught it first live"),
    ("Guard", "409: act not offered, with its reason; GitHub not asked", "✓", "✓", "acts.json #6"),
    ("Guard", "405 from GitHub on merge: 409 in GitHub's words", "✓", NO, "github-acts.feature"),
    ("Callers", "Each caller's own token forwarded (UPSTREAM_AUTH=caller)", "✓", "✓", "github-callers.feature; every live call"),
    ("Callers", "No credential: 401 before GitHub is asked", "✓", "✓", "phase 9 run"),
    ("Callers", "Token GitHub rejects: 401", "✓", NO, "github-callers.feature"),
    ("Callers", "Reads revalidated with If-None-Match; 304 costs no rate limit", "✓", "✓", "5 reads, 0 rate limit used"),
    ("Callers", "One caller's copy never answers another", "✓", NO, "github-callers.feature"),
    ("Kit", "HEART conformance kit through the gateway (19 scenarios)", "✓", NO, "RunGitHubTckTest"),
    ("Issues", "Get an issue; search a repository's issues, its filters; pull requests skipped", "✓", "✓",
     "github-issues.feature; captures/issue-*.json"),
    ("Issues", "Repository at /repos/{owner}/{repo} offers open-issue: 201 + Location", "✓", "✓",
     "github-open-issue.feature; issues-acts.json"),
    ("Issues", "open-issue absent when issues are off or the repository archived", "✓", NO, "github-open-issue.feature"),
    ("Issues", "close as completed, not planned, duplicate; reopen; edit", "✓", "✓", "github-issues.feature; issues-acts.json"),
    ("Issues", "lock, unlock; lock again not offered: 409", "✓", "✓", "issues-acts.json"),
    ("Issues", "label, unlabel ({name} in the path), assign, unassign", "✓", "✓", "github-issue-people.feature; issues-acts.json"),
    ("Issues", "set-milestone", "✓", NO, "github-issue-people.feature"),
    ("Issues", "Who may: author or triage closes; push edits others' and locks", "✓", NO,
     "github-issue-access.feature; live caller is the owner"),
    ("Issues", "Issue links its comments; comment resource; comment: 201 + Location", "✓", "✓",
     "github-issue-comments.feature; issues-acts.json"),
    ("Issues", "Edit a comment: author or push", "✓", "✓", "github-issue-comments.feature; issues-acts.json"),
    ("Issues", "Locked conversation: comment only with push", "✓", NO, "github-issue-comments.feature"),
    ("Issues", "Issue links its sub-issues, served as a list; add, remove by id", "✓", "✓",
     "github-sub-issues.feature; issues-acts.json"),
    ("Issues", "412: the gateway's re-read finds the issue changed on GitHub", "✓", "✓", "issues-acts.json"),
    ("Issues", "Offers follow the user's permissions, not a narrower token's", "—", "✓",
     "token without Issues write: offered, then 403 from GitHub"),
]

ISSUE_ACTS = ["close", "close-not-planned", "close-duplicate", "reopen", "edit", "lock", "unlock", "label",
              "unlabel", "assign", "unassign", "set-milestone", "comment", "add-sub-issue", "remove-sub-issue"]
EVERY = set(ISSUE_ACTS)

# (state, GitHub's fields, capture name or None, what the connector allows, for a caller with push access)
ISSUE_STATES = [
    ("open", "state open, locked false", "open", EVERY - {"reopen", "unlock"}),
    ("open, locked", "state open, locked true", "locked", EVERY - {"reopen", "lock"}),
    ("closed as completed", "state closed, state_reason completed", "completed",
     EVERY - {"close", "close-not-planned", "close-duplicate", "unlock"}),
    ("closed as not planned", "state closed, state_reason not_planned", "not-planned",
     EVERY - {"close", "close-not-planned", "close-duplicate", "unlock"}),
    ("closed as a duplicate", "state closed, state_reason duplicate", "duplicate",
     EVERY - {"close", "close-not-planned", "close-duplicate", "unlock"}),
]

# Who may do what to an issue someone else opened, by the caller's access (fake GitHub only).
ISSUE_ACCESS = [
    ("author, read", {"close", "close-not-planned", "close-duplicate", "edit", "comment"}),
    ("not the author, read", {"comment"}),
    ("not the author, triage", {"close", "close-not-planned", "close-duplicate", "label", "unlabel", "assign", "unassign",
                                "set-milestone", "comment", "add-sub-issue", "remove-sub-issue"}),
    ("not the author, write", EVERY - {"reopen", "unlock"}),
]

ISSUE_REASONS = [
    ("open", "reopen", "The issue is already open."),
    ("not locked", "unlock", "The conversation isn't locked."),
    ("locked", "lock", "The conversation is already locked."),
    ("closed", "close, close-not-planned, close-duplicate", "The issue is already closed."),
    ("not the author, no triage", "close kinds, reopen",
     "Only the issue's author or someone with triage access can close or reopen it."),
    ("not the author, no push", "edit", "Only the issue's author or someone with push access can edit it."),
    ("no triage", "label, unlabel, assign, unassign, set-milestone, add-sub-issue, remove-sub-issue",
     "You need triage access to this repository."),
    ("no push", "lock, unlock", "You need push access to lock or unlock the conversation."),
    ("locked, no push", "comment", "The conversation is locked; only collaborators can comment."),
    ("comment: not the author, no push", "edit", "Only the comment's author or someone with push access can edit it."),
    ("repository: issues off", "open-issue", "Issues are turned off for this repository."),
    ("repository: archived", "open-issue", "The repository is archived; it takes no issues."),
]

# (state, GitHub's fields, live pull request or None, capture file)
STATES = [
    ("open, clean", "state open, mergeable true, mergeable_state clean", 1, "pull-1.json"),
    ("draft", "draft true (mergeable_state clean)", 2, "pull-2.json"),
    ("in conflict", "mergeable false, mergeable_state dirty", 3, "pull-3.json"),
    ("still being checked", "mergeable null, mergeable_state unknown", 3, "pull-3-still-checking.json"),
    ("blocked", "mergeable_state blocked (required review)", 6, "pull-6.json"),
    ("behind", "mergeable_state behind (needs a required check)", None, None),
    ("merged", "state closed, merged true", 4, "pull-4.json"),
    ("closed", "state closed, merged false", 5, "pull-5.json"),
    ("no push permission", "repository permissions.push false", None, None),
]

# What the shipped connector allows in each state; the live column is checked against the captures.
ALLOWED = {
    "open, clean": {"close", "merge", "edit", "request-review", "update-branch"},
    "draft": {"close", "edit", "request-review", "update-branch"},
    "in conflict": {"close", "edit", "request-review"},
    "still being checked": {"close", "edit", "request-review", "update-branch"},
    "blocked": {"close", "edit", "request-review", "update-branch"},
    "behind": {"close", "edit", "request-review", "update-branch"},
    "merged": {"edit"},
    "closed": {"reopen", "edit"},
    "no push permission": set(),
}

REASONS = [
    ("open (any)", "reopen", "The pull request is already open."),
    ("draft", "merge", "A draft can't be merged; mark it ready for review first."),
    ("in conflict", "merge", "The branch has conflicts with its base; resolve them first."),
    ("in conflict", "update-branch", "The branch has conflicts with its base; resolve them first."),
    ("still being checked", "merge", "GitHub is still checking whether it can be merged; read it again shortly."),
    ("blocked", "merge", "Branch protection blocks the merge: a required check or review is missing."),
    ("behind", "merge", "The branch is behind its base; update it first."),
    ("merged", "reopen", "A merged pull request can't be reopened."),
    ("closed (any)", "close", "The pull request is already closed."),
    ("closed (any)", "merge", "A closed pull request can't be merged; reopen it first."),
    ("closed (any)", "request-review", "A closed pull request can't have reviews requested; reopen it first."),
    ("closed (any)", "update-branch", "A closed pull request's branch isn't updated; reopen it first."),
    ("no push permission", "every act", "You can't push to this repository."),
]

PHASES = [
    ("1", "heart-core: item paths", "done"),
    ("2", "heart-core: declared searches", "done; sub-collection links open"),
    ("3", "Gateway: slice GitHub's description", "done"),
    ("4", "Gateway: item paths, bare-array lists, Link paging", "done; Link paging not yet exercised"),
    ("5", "Gateway: acts from operations, bound fields", "done"),
    ("6", "Gateway: rules with reasons, facts, not known yet", "done"),
    ("7", "Gateway: each caller's credential, conditional reads", "done"),
    ("8", "Shipped connector, fake GitHub, conformance kit", "done"),
    ("9", "Live, read-only", "done"),
    ("10", "Live writes on the scratch repository", "done; + edit, review requests, branch updates"),
    ("11", "ARTIE and heart-mcp, asked in words; captured script", "not started"),
    ("I1", "Issues: read, search, skip pull requests", "done"),
    ("I2", "Issues: state acts, YAML only", "done"),
    ("I3", "Issues: labels and people; path variables from fields", "done"),
    ("I4", "Issues: who may do what (facts, richer rule conditions)", "done"),
    ("I5", "Issues: open-issue from the repository, 201 + Location", "done"),
    ("I6", "Issues: comments and sub-issues; heart-core sub-collection links", "done"),
    ("I7", "Issues: fake GitHub, matrices, conformance kit", "done"),
    ("I8", "Issues: live on the scratch repository; reports", "done"),
]


def esc(text):
    return html.escape(str(text), quote=False)


def mark(value):
    css = {"✓": "yes", NO: "no"}.get(value, "part")
    return f'<td class="{css}">{esc(value)}</td>'


def page(root):
    report = os.path.join(root, "docs/reports/github-gateway-status")
    caps = os.path.join(report, "captures")
    acts_dir = os.path.join(root, "docs/reports/github-pulls/captures")
    load = lambda path: json.load(open(path)) if os.path.exists(path) else None
    view = JsonView()

    cap_rows = "".join(
        f"<tr><td class=\"area\">{esc(a)}</td><th>{esc(c)}</th>{mark(f)}{mark(r)}<td class=\"ev\">{esc(e)}</td></tr>"
        for a, c, f, r, e in CAPABILITIES
    )

    state_rows, state_details = [], []
    for name, fields, number, file in STATES:
        allowed = ALLOWED[name]
        live = None
        if file:
            capture = load(os.path.join(caps, file))
            live = {k.split(":")[1] for k in capture["heart"]["response"]["body"].get("_forms", {})}
            assert live == allowed, f"{name}: live {live} != connector {allowed}"
        cells = "".join(f'<td class="{"yes" if a in allowed else "no"}">{"✓" if a in allowed else "—"}</td>' for a in ACTS)
        tested = f"fake, real #{number}" if number else "fake"
        state_rows.append(f"<tr><th>{esc(name)}</th><td class=\"ev\">{esc(fields)}</td>{cells}<td class=\"ev\">{esc(tested)}</td></tr>")
        if file:
            offers = ", ".join(a for a in ACTS if a in allowed) or "nothing"
            state_details.append(view.read(
                load(os.path.join(caps, file)),
                f'<b>{esc(name)}</b> · #{number} · offers {esc(offers)} · <a href="captures/{file}">capture</a>',
            ))

    reason_rows = "".join(
        f"<tr><th>{esc(s)}</th><td><code>{esc(a)}</code></td><td>{esc(r)}</td></tr>" for s, a, r in REASONS
    )

    act_details = []
    for file in ("acts.json", "acts-more.json", "acts-more-unsettled.json"):
        steps = load(os.path.join(acts_dir, file)) or []
        for step in steps:
            if step.get("plain") is None:
                continue
            status = step["act"]["response"]["status"]
            label = step["name"] + (" (first try, GitHub not yet settled)" if file == "acts-more-unsettled.json" else "")
            act_details.append(view.act(step, f'<span class="st s{status // 100}">{status}</span> {esc(label)}'))

    issue_rows, issue_details = [], []
    for name, fields, capture_name, allowed in ISSUE_STATES:
        capture = load(os.path.join(caps, f"issue-{capture_name}.json")) if capture_name else None
        if capture:
            live = {k.split(":")[1] for k in capture["heart"]["response"]["body"].get("_forms", {})}
            assert live == allowed, f"{name}: live {sorted(live)} != connector {sorted(allowed)}"
        cells = "".join(f'<td class="{"yes" if a in allowed else "no"}">{"✓" if a in allowed else "—"}</td>'
                        for a in ISSUE_ACTS)
        tested = f"fake, real #{capture['path'].rsplit('/', 1)[1]}" if capture else "fake"
        issue_rows.append(f"<tr><th>{esc(name)}</th><td class=\"ev\">{esc(fields)}</td>{cells}"
                          f"<td class=\"ev\">{esc(tested)}</td></tr>")
    access_rows = "".join(
        f"<tr><th>{esc(who)}</th>" + "".join(
            f'<td class="{"yes" if a in allowed else "no"}">{"✓" if a in allowed else "—"}</td>' for a in ISSUE_ACTS
        ) + "</tr>" for who, allowed in ISSUE_ACCESS
    )
    issue_reason_rows = "".join(
        f"<tr><th>{esc(s)}</th><td><code>{esc(a)}</code></td><td>{esc(r)}</td></tr>" for s, a, r in ISSUE_REASONS
    )
    for capture_name in ("repository", "open", "locked", "completed", "not-planned", "duplicate",
                         "comment", "comments", "sub-issues"):
        capture = load(os.path.join(caps, f"issue-{capture_name}.json"))
        if not capture:
            continue
        body = capture["heart"]["response"]["body"]
        offers = ", ".join(sorted(k.split(":")[1] for k in body.get("_forms", {}))) or "nothing"
        issue_details.append(view.read(
            capture,
            f'<b>{esc(capture_name)}</b> · {esc(capture["path"])} · offers {esc(offers)} · '
            f'<a href="captures/issue-{capture_name}.json">capture</a>',
        ))
    for step in load(os.path.join(caps, "issues-acts.json")) or []:
        status = step["act"]["response"]["status"]
        issue_details.append(view.act(step, f'<span class="st s{status // 100}">{status}</span> {esc(step["name"])}'))

    phase_rows = "".join(f"<tr><th>{esc(n)}</th><td>{esc(w)}</td><td>{esc(s)}</td></tr>" for n, w, s in PHASES)
    with open(os.path.join(os.path.dirname(__file__), "report.css")) as css:
        style = css.read()

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="The HEART gateway in front of GitHub's pull requests and issues: what works, tested against a fake GitHub and real GitHub, what each state allows and why not the rest, with the live JSON.">
<title>GitHub Gateway Status</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:opsz,wght@7..72,500;7..72,650&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>
{style}
/* A status sheet: tables first, JSON behind each row's disclosure. */
.wrap {{ gap: 2rem; }}
td.yes {{ color: var(--leaf); font-weight: 600; text-align: center; }}
td.no {{ color: var(--muted); text-align: center; }}
td.part {{ color: var(--gate); font-weight: 600; text-align: center; }}
td.area {{ font: 600 0.72rem var(--body); letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }}
.ev {{ color: var(--muted); font-size: 0.86rem; }}
th.c {{ text-align: center; }}
details {{ background: var(--surface); border: 1px solid var(--rule); border-radius: 8px; padding: 0.55rem 0.8rem; }}
details > summary {{ cursor: pointer; }}
details[open] > summary {{ margin-bottom: 0.7rem; }}
details .pair > div {{ display: grid; gap: 0.4rem; min-width: 0; }}
.stack {{ display: grid; gap: 0.5rem; }}
.st {{ font: 600 0.78rem var(--mono); padding: 0.05rem 0.4rem; border-radius: 4px; background: var(--code-bg); }}
.s2 {{ color: var(--leaf); }} .s4 {{ color: var(--gate); }}
.meta {{ display: flex; flex-wrap: wrap; gap: 0.4rem 1.4rem; font-size: 0.9rem; color: var(--muted); }}
.meta b {{ color: var(--fg); font-weight: 600; }}
</style>
{view.assets()}
</head>
<body>
<main class="wrap">
  <header>
    <p class="eyebrow">Take HEART · Part 2 · status</p>
    <h1>GitHub Gateway Status</h1>
    <div class="meta">
      <span><b>Upstream</b> GitHub REST, description 1.1.4 (api.github.com.json)</span>
      <span><b>Connector</b> heart-gateway/connectors/github</span>
      <span><b>Real</b> JogoShugh/heart-github-test, 2026-10-16 and 17</span>
      <span><b>Fake</b> in-memory GitHub on GitHub's own example JSON</span>
      <span><b>Tests</b> heart 660 passed, 0 failed</span>
    </div>
  </header>

  <section class="wide">
    <h2>What works</h2>
    <div class="table-scroll"><table>
      <thead><tr><th>Area</th><th>Capability</th><th class="c">Fake GitHub</th><th class="c">Real GitHub</th><th>Evidence</th></tr></thead>
      <tbody>{cap_rows}</tbody>
    </table></div>
  </section>

  <section class="wide">
    <h2>Allowed now, by state</h2>
    <div class="table-scroll"><table>
      <thead><tr><th>State</th><th>GitHub says</th>{"".join(f'<th class="c">{a}</th>' for a in ACTS)}<th>Tested</th></tr></thead>
      <tbody>{"".join(state_rows)}</tbody>
    </table></div>
    <p class="ev">Real rows are checked against the live captures when this page is built; a mismatch stops the build.</p>
  </section>

  <section class="wide">
    <h2>Not allowed, and why</h2>
    <div class="table-scroll"><table>
      <thead><tr><th>State</th><th>Act</th><th>Reason, as the gateway gives it</th></tr></thead>
      <tbody>{reason_rows}</tbody>
    </table></div>
  </section>

  <section class="wide">
    <h2>Live states, as JSON</h2>
    <div class="stack">{"".join(state_details)}</div>
  </section>

  <section class="wide">
    <h2>Live acts, as JSON</h2>
    <div class="stack">{"".join(act_details)}</div>
  </section>

  <section class="wide">
    <h2>Issues: allowed now, by state</h2>
    <div class="table-scroll"><table>
      <thead><tr><th>State</th><th>GitHub says</th>{"".join(f'<th class="c">{a}</th>' for a in ISSUE_ACTS)}<th>Tested</th></tr></thead>
      <tbody>{"".join(issue_rows)}</tbody>
    </table></div>
    <p class="ev">For a caller with push access. Real rows are checked against the live captures when this page is built.</p>
  </section>

  <section class="wide">
    <h2>Issues: allowed now, by who asks (an open issue)</h2>
    <div class="table-scroll"><table>
      <thead><tr><th>Caller</th>{"".join(f'<th class="c">{a}</th>' for a in ISSUE_ACTS)}</tr></thead>
      <tbody>{access_rows}</tbody>
    </table></div>
    <p class="ev">Fake GitHub only: the live token is the owner's.</p>
  </section>

  <section class="wide">
    <h2>Issues: not allowed, and why</h2>
    <div class="table-scroll"><table>
      <thead><tr><th>When</th><th>Act</th><th>Reason, as the gateway gives it</th></tr></thead>
      <tbody>{issue_reason_rows}</tbody>
    </table></div>
  </section>

  <section class="wide">
    <h2>Issues live, as JSON</h2>
    <div class="stack">{"".join(issue_details)}</div>
  </section>

  <section class="wide">
    <h2>Phases</h2>
    <div class="table-scroll"><table>
      <thead><tr><th>#</th><th>What</th><th>Status</th></tr></thead>
      <tbody>{phase_rows}</tbody>
    </table></div>
    <p class="ev">Configuration: <code>connectors/github</code> (connector.yaml; pull, issue, repository and comment resource and reader files), explained in the report Gateway Connectors. Side-by-side walkthrough: GitHub Pulls, Two Ways.</p>
  </section>
{view.data()}
</main>
</body>
</html>
"""


if __name__ == "__main__":
    root = sys.argv[1]
    out = os.path.join(root, "docs/reports/github-gateway-status/index.html")
    with open(out, "w") as file:
        file.write(page(root))
    print("wrote", out)
