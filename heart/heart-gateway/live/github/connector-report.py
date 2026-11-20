#!/usr/bin/env python3
"""
Builds docs/reports/gateway-connectors/index.html: how the HEART gateway is
configured, the standard approach every connector follows, and GitHub's
connector as the worked example. The connector files are read from
heart-gateway/connectors/github, so the page shows exactly what ships.

    python3 connector-report.py REPO_ROOT
"""
import html
import os
import sys

from jsonview import JsonView


def esc(text):
    return html.escape(text, quote=False)


def page(root):
    connectors = os.path.join(root, "heart/heart-gateway/connectors/github")
    names = ["connector.yaml"] + [f"{noun}.{kind}.yaml" for noun in ("pull", "issue", "repository", "comment")
                                  for kind in ("resource", "reader")]
    view = JsonView()
    shipped = '<div class="stack">' + "".join(
        view.one(open(os.path.join(connectors, name)).read(), name, f"<code>connectors/github/{name}</code>")
        for name in names
    ) + "</div>"
    with open(os.path.join(os.path.dirname(__file__), "report.css")) as css:
        style = css.read()
    return TEMPLATE.format(style=style, shipped=shipped, assets=view.assets(), data=view.data())


TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="How the HEART gateway is configured: a generic engine in code, and one folder of YAML per API. The standard approach, every key, and GitHub's connector as the worked example.">
<title>Gateway Connectors</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:opsz,wght@7..72,500;7..72,650&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>
{style}
/* This page: a reference. Key tables, the shipped files, the steps of the approach. */
.steps {{ list-style: none; margin: 0; padding: 0; display: grid; gap: 0.7rem; counter-reset: s; }}
.steps > li {{ counter-increment: s; display: grid; grid-template-columns: 2.2rem minmax(0, 1fr); gap: 0.2rem 0.8rem; background: var(--surface); border: 1px solid var(--rule); border-radius: 10px; padding: 0.85rem 1rem; max-width: none; }}
.steps > li::before {{ content: counter(s); grid-row: span 2; font: 650 1.3rem/1 var(--display); color: var(--leaf); }}
.steps b {{ font-family: var(--display); font-size: 1.04rem; }}
.files {{ display: grid; gap: 1rem; }}
.file {{ display: grid; gap: 0.5rem; min-width: 0; }}
.file .name {{ font: 600 0.82rem var(--mono); color: var(--leaf); }}
td code, th code {{ white-space: nowrap; }}
.split {{ display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 1rem; align-items: start; }}
@media (max-width: 52rem) {{ .split {{ grid-template-columns: minmax(0, 1fr); }} }}
.box {{ display: grid; gap: 0.5rem; min-width: 0; background: var(--surface); border: 1px solid var(--rule); border-radius: 10px; padding: 0.9rem; }}
.box.code-side {{ border-top: 4px solid var(--link); }}
.box.conf-side {{ border-top: 4px solid var(--leaf); }}
.box ul {{ font-size: 0.94rem; }}
h3 {{ font-family: var(--display); font-weight: 650; font-size: 1.15rem; margin: 0; }}
</style>
{assets}
</head>
<body>
<main class="wrap">
  <header>
    <p class="eyebrow">Take HEART · Part 2 · configuring the gateway</p>
    <h1>Gateway Connectors</h1>
    <p class="lede">The HEART gateway serves an API that publishes only OpenAPI as a HEART server: forms for what each resource allows now, reasons for the rest. Everything it knows about one API sits in that API's connector, a folder of YAML. GitHub's is the worked example.</p>
  </header>

  <section>
    <p class="thesis">The engine is code, written once for every API. An API is configuration: one folder, three kinds of file, no code.</p>
  </section>

  <section class="wide">
    <div class="split">
      <div class="box code-side">
        <p class="who">Code, the same for every API</p>
        <ul>
          <li>Read an OpenAPI description, keeping only the operations a connector includes, before parsing it.</li>
          <li>Turn a resource file into a HEART resource type: its address, its search, its forms.</li>
          <li>Turn an act into a call: the fixed body, the client's fields, the fields bound from state.</li>
          <li>Decide what an answer allows: the reader's signal, then its rules, each with its reason.</li>
          <li>Call the upstream as each caller, and revalidate what it read with ETags.</li>
          <li>Serve all of it as HEART, through heart-core and heart-http4k.</li>
        </ul>
        <p class="note">Kotlin, in <code>heart-gateway/src/main</code>. It names no API.</p>
      </div>
      <div class="box conf-side">
        <p class="who">Configuration, one folder per API</p>
        <ul>
          <li><code>connector.yaml</code>: which operations to read, headers for every call, and which files follow.</li>
          <li><code>&lt;name&gt;.resource.yaml</code>: one resource of the API, its acts and the other answers it reads.</li>
          <li><code>&lt;name&gt;.reader.yaml</code>: how to tell what an answer allows now, and why not the rest.</li>
          <li>Optionally a <code>.jsonata</code> file, for an API whose signal no built-in reader understands.</li>
        </ul>
        <p class="note">GitHub's is <code>heart-gateway/connectors/github</code>, shipped in the gateway's distribution.</p>
      </div>
    </div>
  </section>

  <section>
    <h2>The standard approach</h2>
    <p>Every connector is written the same way, in this order. Each step is a file or a key; none is code.</p>
    <ol class="steps">
      <li><b>Slice the description.</b><span>In <code>connector.yaml</code>, list under <code>include</code> only the operations the connector uses. The gateway cuts the rest out before parsing: GitHub's 13.5 MB description loads in about a second. Add any <code>headers</code> every call needs.</span></li>
      <li><b>Name each resource by its own paths.</b><span>In a resource file, say which operation reads one (<code>read</code>) and which lists them (<code>search</code>), and give a JSON Pointer into the resource's state for each variable of the read's path (<code>pointers</code>). An upstream whose paths are already HEART's, <code>/{{collection}}/{{id}}</code> with acts at <code>/actions/{{name}}</code>, needs no resource file at all.</span></li>
      <li><b>Name acts after what people do, not after operations.</b><span>Each act is one operation with a <code>body</code> fixed by the connector, the <code>fields</code> the client fills, and the fields bound from the state the client's validator matched (<code>bind</code>). GitHub's close and reopen are the same operation with a different fixed body.</span></li>
      <li><b>Bind the upstream's own concurrency checks.</b><span>Where an operation takes the version it expects, bind it from the state, so the upstream refuses a write on a view that's out of date. Merge's <code>sha</code> and update-branch's <code>expected_head_sha</code> both come from <code>/head/sha</code>.</span></li>
      <li><b>Say what the upstream's refusals mean.</b><span><code>stale</code> names the status that means the client's view is out of date (answered 412); <code>refused</code> the one that means not now (answered 409, in the upstream's words). Anything else passes through as the problem it is.</span></li>
      <li><b>Read what's allowed, standards first.</b><span>An answer with HEART's affordance <code>Link</code> or OData's advertised actions needs no reader. Otherwise a reader names one signal: <code>names</code>, <code>booleans</code>, <code>links</code>, <code>odata</code>, a <code>table</code> by a state field, or a <code>jsonata</code> file.</span></li>
      <li><b>Refine with rules, each with its reason.</b><span>After the signal, a reader's <code>rules</code>, in order: when every condition holds, the acts named are absent, with that reason. The first reason given for an act stands. A condition may read a <code>fact</code>, another of the upstream's answers, such as the repository's permissions.</span></li>
      <li><b>Leave the last word to the upstream.</b><span>Rules only say early, and why. The gateway reads the resource again just before acting, and the upstream's own refusal of an act always reaches the client.</span></li>
    </ol>
  </section>

  <section class="wide">
    <h2>Running it</h2>
    <div class="table-scroll">
      <table>
        <thead><tr><th>Setting</th><th>What it is</th><th>GitHub</th></tr></thead>
        <tbody>
          <tr><th><code>UPSTREAM_URL</code></th><td>Where the upstream is.</td><td><code>https://api.github.com</code></td></tr>
          <tr><th><code>UPSTREAM_SPEC</code></th><td>Its OpenAPI description, a URL or a mounted file; by default <code>UPSTREAM_URL/openapi.json</code>.</td><td>GitHub's <code>api.github.com.json</code>, from github/rest-api-description</td></tr>
          <tr><th><code>GATEWAY_CONNECTOR</code></th><td>The connector's folder.</td><td><code>connectors/github</code></td></tr>
          <tr><th><code>GATEWAY_PROFILE</code></th><td>The profile relations are named in, when there is no connector to name one.</td><td>not needed: the connector says <code>github</code></td></tr>
          <tr><th><code>UPSTREAM_AUTH</code></th><td><code>none</code>, <code>bearer</code> (<code>UPSTREAM_TOKEN</code>), <code>basic</code> (<code>UPSTREAM_USER</code>, <code>UPSTREAM_PASSWORD</code>), or <code>caller</code>: forward each caller's own credential, and let the upstream judge it.</td><td><code>caller</code></td></tr>
          <tr><th><code>HEART_AUTH_BASIC_USERS</code>, <code>HEART_AUTH_BEARER_TOKENS</code></th><td>Who may call the gateway, as on every HEART server; not needed with <code>caller</code>, though a request with no credential is still refused.</td><td>not needed</td></tr>
          <tr><th><code>PORT</code></th><td>Where the gateway listens; 8090 by default.</td><td>any</td></tr>
        </tbody>
      </table>
    </div>
  </section>

  <section class="wide">
    <h2>Every key</h2>
    <h3><code>connector.yaml</code></h3>
    <div class="table-scroll">
      <table>
        <thead><tr><th>Key</th><th>Says</th></tr></thead>
        <tbody>
          <tr><th><code>profile</code></th><td>The profile the API's relations are named in: forms are keyed <code>github:merge</code>, described at <code>/profiles/github</code>.</td></tr>
          <tr><th><code>include</code></th><td>The operations to read, by operationId. Without it, every operation is read.</td></tr>
          <tr><th><code>headers</code></th><td>Headers every upstream call carries.</td></tr>
          <tr><th><code>resources</code></th><td>The resource files, in order.</td></tr>
          <tr><th><code>readers</code></th><td>The reader files, in order: the first that recognizes an answer reads it, so the API's default reader comes last. A reader file in the folder but not listed stops the gateway.</td></tr>
        </tbody>
      </table>
    </div>
    <h3><code>&lt;name&gt;.resource.yaml</code></h3>
    <div class="table-scroll">
      <table>
        <thead><tr><th>Key</th><th>Says</th></tr></thead>
        <tbody>
          <tr><th><code>noun</code>, <code>collection</code></th><td>What one is called, and the collection's name.</td></tr>
          <tr><th><code>read</code></th><td>The operation that reads one. Its path is where the resource lives in HEART too.</td></tr>
          <tr><th><code>search</code></th><td>The operation that lists them. A bare JSON array, paged by <code>Link: rel="next"</code>, is followed to the end.</td></tr>
          <tr><th><code>pointers</code></th><td>For each variable of the read's path, a JSON Pointer into the state: <code>pull_number: /number</code>.</td></tr>
          <tr><th><code>filters</code></th><td>The list's query parameters the search form offers.</td></tr>
          <tr><th><code>title</code></th><td>Where a listed item's title is, for the search page's item links.</td></tr>
          <tr><th><code>address</code></th><td>Where the item's own URL is, when its state names its parts only there, as an issue's <code>/url</code> does: the read's path variables are read from it and kept in the state as <code>hr:address</code>. Instead of <code>pointers</code>.</td></tr>
          <tr><th><code>skip</code></th><td>Conditions under which a listed item is not one of these resources: GitHub lists pull requests among issues (<code>/pull_request: {{present: true}}</code>).</td></tr>
          <tr><th><code>lists</code></th><td>Other list operations whose members are these resources, each served at its own path and linked from the resource: an issue's sub-issues.</td></tr>
          <tr><th><code>links</code></th><td>List operations under the resource that another resource file searches by, linked from it: an issue's comments.</td></tr>
          <tr><th><code>acts</code></th><td>The acts, each by name: <code>operation</code>, <code>title</code>, <code>description</code>, <code>body</code> (fixed), <code>fields</code> (the client's), <code>bind</code> (from state), <code>stale</code> and <code>refused</code> (what the upstream's statuses mean), and <code>creates</code> (the noun of the resource the operation's answer is: the gateway answers 201 with its address in <code>Location</code>). A field the state doesn't fill but the operation's path names goes in the path: remove-label's <code>{{name}}</code>.</td></tr>
          <tr><th><code>facts</code></th><td>Other answers a reader's rules may read, each by name: an <code>operation</code>, and <code>pointers</code> filling its path from the resource's state.</td></tr>
        </tbody>
      </table>
    </div>
    <h3><code>&lt;name&gt;.reader.yaml</code></h3>
    <div class="table-scroll">
      <table>
        <thead><tr><th>Key</th><th>Says</th></tr></thead>
        <tbody>
          <tr><th><code>matches</code></th><td>Which answers this reader reads: by members of the <code>body</code>, its <code>mediaType</code>, or the description's <code>path</code> template it was asked at. None means every answer: the default reader.</td></tr>
          <tr><th><code>allowed</code></th><td>One signal: <code>names</code> (a list of action names), <code>booleans</code> (a map of capabilities), <code>links</code> (an array of links by rel), <code>odata</code> (OData's advertised actions), <code>table</code> (a state field, with <code>rows</code> of <code>allow</code> and <code>absent</code> reasons), <code>always</code> (the same acts in every state, for a resource with no state to choose by, as a comment), or <code>jsonata</code> (a file). <code>map</code> renames the API's words to operationIds.</td></tr>
          <tr><th><code>allowed.rules</code></th><td>In order: <code>when</code> (each JSON Pointer, or <code>fact:/pointer</code>, against a condition: a JSON value it must equal, <code>null</code> matching a member that is there and null; <code>{{present: true}}</code>; <code>{{not: value}}</code>; <code>{{same-as: term}}</code> or <code>{{not-same-as: term}}</code>, another pointer or fact, as an issue's author and the caller) and <code>absent</code> (act to reason). Every condition of a rule must hold.</td></tr>
          <tr><th><code>hide</code></th><td>Members that are control, not state, left out of the HEART state.</td></tr>
        </tbody>
      </table>
    </div>
  </section>

  <section>
    <h2>Two kinds of upstream</h2>
    <p>An API whose answers already say what is allowed needs very little. The door service the gateway's tests run against lists the names of the actions allowed now, and its whole connector is this:</p>
    <div class="files">
      <div class="file"><p class="name">doors-names/connector.yaml</p><pre><code>profile: doors
readers:
  - door.reader.yaml</code></pre></div>
      <div class="file"><p class="name">doors-names/door.reader.yaml</p><pre><code>allowed:
  names: /allowedActions
hide: [/allowedActions]</code></pre></div>
    </div>
    <p>GitHub says nothing about what is allowed. Its answers carry the facts, <code>state</code>, <code>draft</code>, <code>merged</code>, <code>mergeable</code>, <code>mergeable_state</code>, and the repository's <code>permissions</code>, but no decisions, and its paths aren't HEART's. So its connector writes the decisions down, once, with their reasons, and names its resource by its own paths. That is the most a connector has to say.</p>
  </section>

  <section class="wide">
    <h2>GitHub's connector, as shipped</h2>
    <p>Four resources: a pull request, an issue, a repository (where new issues come from) and an issue's comment. How each pull request act maps to GitHub:</p>
    <div class="table-scroll">
      <table>
        <thead><tr><th>Act</th><th>GitHub operation</th><th>Fixed body</th><th>Client fills</th><th>Bound from state</th><th>GitHub's statuses</th></tr></thead>
        <tbody>
          <tr><th>close</th><td><code>PATCH /repos/{{owner}}/{{repo}}/pulls/{{pull_number}}</code></td><td><code>{{"state": "closed"}}</code></td><td></td><td></td><td></td></tr>
          <tr><th>reopen</th><td>same</td><td><code>{{"state": "open"}}</code></td><td></td><td></td><td></td></tr>
          <tr><th>edit</th><td>same</td><td></td><td>title, body</td><td></td><td></td></tr>
          <tr><th>merge</th><td><code>PUT …/merge</code></td><td></td><td>merge_method, commit_title, commit_message</td><td><code>sha</code> from <code>/head/sha</code></td><td>409 stale, 405 refused</td></tr>
          <tr><th>update-branch</th><td><code>PUT …/update-branch</code></td><td></td><td></td><td><code>expected_head_sha</code> from <code>/head/sha</code></td><td>422 refused</td></tr>
          <tr><th>request-review</th><td><code>POST …/requested_reviewers</code></td><td></td><td>reviewers, team_reviewers (at least one)</td><td></td><td></td></tr>
        </tbody>
      </table>
    </div>
    <p>How each issue, repository and comment act maps to GitHub. Act names close, reopen and edit are shared with pull requests: one relation in the profile, whichever resource does it.</p>
    <div class="table-scroll">
      <table>
        <thead><tr><th>Resource</th><th>Act</th><th>GitHub operation</th><th>Fixed body</th><th>Client fills</th></tr></thead>
        <tbody>
          <tr><th>issue</th><td>close</td><td><code>PATCH /repos/{{owner}}/{{repo}}/issues/{{issue_number}}</code></td><td><code>{{"state": "closed", "state_reason": "completed"}}</code></td><td></td></tr>
          <tr><th>issue</th><td>close-not-planned</td><td>same</td><td><code>{{"state": "closed", "state_reason": "not_planned"}}</code></td><td></td></tr>
          <tr><th>issue</th><td>close-duplicate</td><td>same</td><td><code>{{"state": "closed", "state_reason": "duplicate"}}</code></td><td>duplicate_issue_id</td></tr>
          <tr><th>issue</th><td>reopen</td><td>same</td><td><code>{{"state": "open", "state_reason": "reopened"}}</code></td><td></td></tr>
          <tr><th>issue</th><td>edit</td><td>same</td><td></td><td>title, body</td></tr>
          <tr><th>issue</th><td>set-milestone</td><td>same</td><td></td><td>milestone</td></tr>
          <tr><th>issue</th><td>lock, unlock</td><td><code>PUT</code>, <code>DELETE …/lock</code></td><td></td><td>lock_reason (lock)</td></tr>
          <tr><th>issue</th><td>label, unlabel</td><td><code>POST …/labels</code>, <code>DELETE …/labels/{{name}}</code></td><td></td><td>labels; name, put in the path</td></tr>
          <tr><th>issue</th><td>assign, unassign</td><td><code>POST</code>, <code>DELETE …/assignees</code></td><td></td><td>assignees</td></tr>
          <tr><th>issue</th><td>comment</td><td><code>POST …/comments</code>, creates a comment: 201</td><td></td><td>body</td></tr>
          <tr><th>issue</th><td>add-sub-issue, remove-sub-issue</td><td><code>POST …/sub_issues</code>, <code>DELETE …/sub_issue</code></td><td></td><td>sub_issue_id (replace_parent)</td></tr>
          <tr><th>repository</th><td>open-issue</td><td><code>POST /repos/{{owner}}/{{repo}}/issues</code>, creates an issue: 201</td><td></td><td>title, body, assignees, labels, milestone</td></tr>
          <tr><th>comment</th><td>edit</td><td><code>PATCH /repos/{{owner}}/{{repo}}/issues/comments/{{comment_id}}</code></td><td></td><td>body</td></tr>
        </tbody>
      </table>
    </div>
    {shipped}
  </section>

  <section>
    <h2>What the gateway checks at start</h2>
    <p>A connector that doesn't fit the description stops the gateway before it serves anything, naming the file: an included operation the description lacks, a resource or reader naming an operation or path it lacks, a field an act asks for that its operation's body doesn't have, a reader file present but not listed, a listed file that isn't there, a key that isn't a JSON Pointer.</p>
  </section>

  <section>
    <h2>Testing a connector</h2>
    <ul>
      <li><b>A small upstream in memory</b>, answering as the real one does. GitHub's serves GitHub's own example answers, from its description, set to each state.</li>
      <li><b>Features in tables</b>: what each state offers, as one matrix of state by act, and why each act is absent, as rows of state, act and reason.</li>
      <li><b>HEART's conformance kit</b>, run through the gateway in front of that upstream: the same scenarios every HEART server passes.</li>
      <li><b>A live run</b> on a scratch repository: <code>live/github/seed.py</code> makes the states, <code>capture.py</code> reads them both ways, <code>act.py</code> acts through the forms.</li>
    </ul>
    <p>GitHub's connector, as of 2026-10-17: every act tested against the fake GitHub. Against real GitHub: every pull request act but an accepted review request (the repository has one collaborator), and every state but behind and no push permission; every issue, repository and comment act but set-milestone, through the forms, with the issues opened and commented on by the forms themselves (<code>live/github/issues.py</code>, <code>capture-issues.py</code>). Who may do what by role is tested against the fake only: the live token is the owner's. The report GitHub Gateway Status has the full tables and the live JSON.</p>
  </section>

  <section>
    <h2>Not yet</h2>
    <ul>
      <li>Conditions say equal, present, not and same-as; a rule can't yet say "more than".</li>
      <li>One read path per resource.</li>
      <li>A fine-grained token narrower than its user's permissions: the gateway offers what the user may do, and the upstream's 403 has the last word. Nothing in GitHub's answers says what the token may do.</li>
      <li>OpenAPI 3.0's <code>nullable</code> isn't translated to JSON Schema 2020-12 in act schemas; no field GitHub's acts use needs it.</li>
    </ul>
  </section>
{data}
</main>
</body>
</html>
"""


if __name__ == "__main__":
    root = sys.argv[1]
    out = os.path.join(root, "docs/reports/gateway-connectors")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "index.html"), "w") as file:
        file.write(page(root))
    print("wrote", os.path.join(out, "index.html"))
