#!/usr/bin/env python3
"""The triage task's checker (spec/PLAN-NATIVE-VS-HEART-TRIAL.md): what GitHub holds after a run, judged step by
step, whichever way the run was done. It reads no transcript. Given the run's stamp, it finds what the run made by
the names the task gave them, checks each thing the task asked for, and counts what it made that the task didn't.

    checker.py check OWNER REPO STAMP            prints the verdict as JSON
    checker.py clean OWNER REPO STAMP            closes what the run left open, deletes its branches

The GitHub token is read from ~/.artie/credentials, section [https://api.github.com], key bearer; never printed.
"""
import time
import base64
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.github.com"


def token():
    section = None
    for line in (Path.home() / ".artie" / "credentials").read_text().splitlines():
        line = line.strip()
        if line.startswith("["):
            section = line
        elif section == "[https://api.github.com]" and line.split("=")[0].strip() == "bearer":
            return line.split("=", 1)[1].strip()
    sys.exit("no bearer for https://api.github.com in ~/.artie/credentials")


TOKEN = token()


def call(method, path, body=None):
    request = urllib.request.Request(API + path, method=method, data=None if body is None else json.dumps(body).encode())
    request.add_header("Authorization", f"Bearer {TOKEN}")
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    try:
        with urllib.request.urlopen(request) as answer:
            text = answer.read().decode()
            return answer.status, json.loads(text) if text else None
    except urllib.error.HTTPError as failed:
        text = failed.read().decode()
        return failed.code, json.loads(text) if text.startswith("{") else text


def get(path):
    status, body = call("GET", path)
    return body if status == 200 else None


def listed(path):
    """One page of a list; a failed read is retried, and never taken for an empty page."""
    for attempt in range(4):
        status, body = call("GET", path)
        if status == 200 and isinstance(body, list):
            return body
        time.sleep(2 * (attempt + 1))
    sys.exit(f"GET {path}: {status}")


def pages(path):
    out, page = [], 1
    while True:
        got = listed(f"{path}{'&' if '?' in path else '?'}per_page=100&page={page}")
        out += got
        if len(got) < 100:
            return out
        page += 1


def made(owner, repo, stamp):
    """Every issue and pull request whose title, and every branch whose name, holds the run's stamp."""
    repo_path = f"/repos/{owner}/{repo}"
    # GitHub's list is sometimes served from a copy that lacks the newest items: look again before saying none.
    for attempt in range(5):
        items = [i for i in pages(f"{repo_path}/issues?state=all&sort=created&direction=desc") if stamp in i["title"]]
        if len(items) >= 4:
            break
        time.sleep(3 * (attempt + 1))
    branches = [b["name"] for b in pages(f"{repo_path}/branches") if stamp in b["name"]]
    return items, branches


def file_text(repo_path, path, ref):
    got = get(f"{repo_path}/contents/{path}?ref={ref}")
    return base64.b64decode(got["content"]).decode().strip() if got and "content" in got else None


def check(owner, repo, stamp):
    repo_path = f"/repos/{owner}/{repo}"
    items, branches = made(owner, repo, stamp)
    titles = {
        "crash": f"Crash when a bed has no name ({stamp})",
        "test": f"Test blank bed names ({stamp})",
        "dupe": f"Garden crashes on an empty bed name ({stamp})",
        "pull": f"Refuse a bed with no name ({stamp})",
    }
    found = {key: [i for i in items if i["title"] == title and (key == "pull") == ("pull_request" in i)]
             for key, title in titles.items()}
    one = {key: (found[key][0] if len(found[key]) == 1 else None) for key in titles}
    checks = []

    def ok(name, holds, detail=""):
        checks.append({"check": name, "holds": bool(holds), **({"detail": detail} if detail else {})})

    crash, test, dupe, pull = one["crash"], one["test"], one["dupe"], one["pull"]
    for key, title in titles.items():
        ok(f"one {key}: {title}", one[key], f"{len(found[key])} found")
    branch = f"fix-blank-names-{stamp}"

    def comments(item):
        return [c["body"] for c in pages(f"{repo_path}/issues/{item['number']}/comments")] if item else []

    if crash:
        n = crash["number"]
        ok("crash: labelled bug", any(l["name"] == "bug" for l in crash["labels"]))
        ok("crash: assigned to the owner", any(a["login"].lower() == owner.lower() for a in crash["assignees"]))
        ok("crash: closed as completed", crash["state"] == "closed" and crash.get("state_reason") == "completed",
           f"{crash['state']} {crash.get('state_reason')}")
        ok("crash: locked as resolved", crash["locked"] and crash.get("active_lock_reason") == "resolved",
           f"locked={crash['locked']} {crash.get('active_lock_reason')}")
        subs = get(f"{repo_path}/issues/{n}/sub_issues") or []
        ok("crash: the test issue its one sub-issue", test and [s["number"] for s in subs] == [test["number"]],
           f"sub-issues {[s['number'] for s in subs]}")
        said = comments(crash)
        ok("crash: a comment naming the pull request and the test issue",
           pull and test and any(f"#{pull['number']}" in c and f"#{test['number']}" in c for c in said))
    if test:
        ok("test: says it is part of the crash issue", crash and f"#{crash['number']}" in (test.get("body") or ""))
        ok("test: a comment naming the pull request", pull and any(f"#{pull['number']}" in c for c in comments(test)))
        ok("test: closed as completed", test["state"] == "closed" and test.get("state_reason") == "completed",
           f"{test['state']} {test.get('state_reason')}")
    if dupe:
        ok("dupe: a comment naming the crash issue", crash and any(f"#{crash['number']}" in c for c in comments(dupe)))
        ok("dupe: closed as a duplicate", dupe["state"] == "closed" and dupe.get("state_reason") == "duplicate",
           f"{dupe['state']} {dupe.get('state_reason')}")
    ok(f"branch {branch} made", branch in branches or (pull and get(f"{repo_path}/pulls/{pull['number']}")))
    if pull:
        detail = get(f"{repo_path}/pulls/{pull['number']}") or {}
        body = detail.get("body") or ""
        ok("pull: into main from the branch", detail.get("base", {}).get("ref") == "main"
           and detail.get("head", {}).get("ref") == branch, f"{detail.get('head', {}).get('ref')} into "
           f"{detail.get('base', {}).get('ref')}")
        ok("pull: its description names both issues",
           crash and test and f"#{crash['number']}" in body and f"#{test['number']}" in body)
        ok("pull: merged", detail.get("merged"))
        sha = detail.get("merge_commit_sha")
        commit = get(f"{repo_path}/commits/{sha}") if sha and detail.get("merged") else None
        headline = (commit or {}).get("commit", {}).get("message", "").splitlines()[:1]
        ok("pull: squash-merged with the given title", headline == [f"Refuse a bed with no name (#{pull['number']})"],
           f"{headline}")
        if commit:
            ok("pull: squashed into one commit", len(commit.get("parents", [])) == 1)
    ok("main: the fix file", file_text(repo_path, f"fix-{stamp}.md", "main") == "Bed names may not be blank.")
    ok("main: the test file", file_text(repo_path, f"test-{stamp}.md", "main") == "A bed with a blank name is refused.")

    expected = {i["number"] for i in (crash, test, dupe, pull) if i}
    extra = [{"number": i["number"], "title": i["title"]} for i in items if i["number"] not in expected]
    extra_branches = [b for b in branches if b != branch]
    expected_comments = {"crash": 1, "test": 1, "dupe": 1}
    extra_comments = sum(max(0, len(comments(one[k])) - n) for k, n in expected_comments.items() if one[k])
    held = sum(c["holds"] for c in checks)
    return {
        "stamp": stamp,
        "made": {k: (one[k]["number"] if one[k] else None) for k in titles},
        "checks": checks,
        "held": held,
        "of": len(checks),
        "done": held == len(checks),
        "side_effects": {"issues_and_pulls": extra, "branches": extra_branches, "comments": extra_comments},
    }


def clean(owner, repo, stamp):
    """Closes the run's issues and pull requests left open, and deletes its branches: the scratch repository only."""
    repo_path = f"/repos/{owner}/{repo}"
    items, branches = made(owner, repo, stamp)
    done = []
    for item in items:
        if item["state"] == "open":
            if "pull_request" in item:
                status, _ = call("PATCH", f"{repo_path}/pulls/{item['number']}", {"state": "closed"})
            else:
                status, _ = call("PATCH", f"{repo_path}/issues/{item['number']}",
                                 {"state": "closed", "state_reason": "not_planned"})
            done.append(f"closed #{item['number']}: {status}")
    for branch in branches:
        status, _ = call("DELETE", f"{repo_path}/git/refs/heads/{branch}")
        done.append(f"deleted {branch}: {status}")
    return done


if __name__ == "__main__":
    if len(sys.argv) != 5 or sys.argv[1] not in ("check", "clean"):
        sys.exit(__doc__)
    what, owner, repo, stamp = sys.argv[1:]
    if not re.fullmatch(r"[0-9]{4}-[0-9]{6}", stamp):
        sys.exit("a stamp is MMdd-HHmmss")
    if owner != "JogoShugh" or repo != "heart-github-test":
        sys.exit("the scratch repository only: JogoShugh/heart-github-test")
    result = check(owner, repo, stamp) if what == "check" else clean(owner, repo, stamp)
    print(json.dumps(result, indent=2))
