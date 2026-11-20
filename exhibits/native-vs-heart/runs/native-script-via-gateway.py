#!/usr/bin/env python3
import sys, os, json, time, base64
import urllib.request, urllib.error

if len(sys.argv) != 5:
    sys.exit("usage: script.py OWNER REPO LABEL STAMP")
OWNER, REPO, LABEL, STAMP = sys.argv[1:5]
TOKEN = os.environ.get("GITHUB_TOKEN")
if not TOKEN:
    sys.exit("GITHUB_TOKEN is not set")

# Through the HEART gateway, passed through untouched (spec/ideas/gateway-pass-through.md).
API = os.environ.get("GITHUB_API", "http://localhost:8392")
R = "/repos/%s/%s" % (OWNER, REPO)


def req(method, path, body=None, ok=(200, 201, 204)):
    print("%s %s" % (method, path), flush=True)
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(API + path, data=data, method=method)
    r.add_header("Authorization", "Bearer " + TOKEN)
    r.add_header("Accept", "application/vnd.github+json")
    r.add_header("X-GitHub-Api-Version", "2022-11-28")
    r.add_header("User-Agent", "triage-script")
    r.add_header("X-Gateway-Mode", "pass-through")
    if data is not None:
        r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r) as resp:
            status = resp.status
            raw = resp.read()
    except urllib.error.HTTPError as e:
        status = e.code
        raw = e.read()
    if status not in ok:
        sys.exit("FAILED: %s %s -> HTTP %d: %s" % (method, path, status, raw.decode(errors="replace")[:500]))
    if raw:
        try:
            return json.loads(raw)
        except ValueError:
            return None
    return None


def b64(s):
    return base64.b64encode(s.encode()).decode()


branch = "fix-blank-names-" + STAMP

# main sha
main = req("GET", R + "/git/ref/heads/main")
main_sha = main["object"]["sha"]

# crash issue
crash = req("POST", R + "/issues", {
    "title": "Crash when a bed has no name (%s)" % STAMP,
    "body": "A bed with a blank name crashes the garden.",
    "labels": [LABEL],
    "assignees": [OWNER],
})
crash_no, crash_id = crash["number"], crash["id"]
if crash.get("state") != "open":
    crash = req("PATCH", R + "/issues/%d" % crash_no, {"state": "open"})
    if crash.get("state") != "open":
        sys.exit("crash issue is not open")

# test issue
test = req("POST", R + "/issues", {
    "title": "Test blank bed names (%s)" % STAMP,
    "body": "This is part of #%d." % crash_no,
})
test_no, test_id = test["number"], test["id"]

# duplicate issue
dup = req("POST", R + "/issues", {
    "title": "Garden crashes on an empty bed name (%s)" % STAMP,
    "body": "This was seen on the demo garden.",
})
dup_no = dup["number"]

# branch
req("POST", R + "/git/refs", {"ref": "refs/heads/" + branch, "sha": main_sha})

# commits
req("PUT", R + "/contents/fix-%s.md" % STAMP, {
    "message": "Refuse a bed with no name",
    "content": b64("Bed names may not be blank.\n"),
    "branch": branch,
})
req("PUT", R + "/contents/test-%s.md" % STAMP, {
    "message": "Test that a blank bed name is refused",
    "content": b64("A bed with a blank name is refused.\n"),
    "branch": branch,
})

# pull request
pr = req("POST", R + "/pulls", {
    "title": "Refuse a bed with no name (%s)" % STAMP,
    "head": branch,
    "base": "main",
    "body": "Fixes #%d." % crash_no,
})
pr_no = pr["number"]
req("PATCH", R + "/pulls/%d" % pr_no, {
    "body": "Fixes #%d and is tested by #%d." % (crash_no, test_no),
})

# wait for mergeable
pr_now = None
for _ in range(30):
    pr_now = req("GET", R + "/pulls/%d" % pr_no)
    if pr_now.get("mergeable") is True and pr_now.get("mergeable_state") in ("clean", "unstable", "has_hooks"):
        break
    if pr_now.get("mergeable") is False:
        sys.exit("pull request is not mergeable (state %s)" % pr_now.get("mergeable_state"))
    time.sleep(2)
else:
    sys.exit("pull request did not become mergeable in time")

# merge
m = req("PUT", R + "/pulls/%d/merge" % pr_no, {
    "merge_method": "squash",
    "commit_title": "Refuse a bed with no name (#%d)" % pr_no,
    "sha": pr_now["head"]["sha"],
})
if not m.get("merged"):
    sys.exit("merge response did not say merged")
req("GET", R + "/pulls/%d/merge" % pr_no, ok=(204,))

# crash issue: sub-issue, comment, close, lock
req("POST", R + "/issues/%d/sub_issues" % crash_no, {"sub_issue_id": test_id})
req("POST", R + "/issues/%d/comments" % crash_no,
    {"body": "Fixed in #%d, tested in #%d." % (pr_no, test_no)})
req("PATCH", R + "/issues/%d" % crash_no, {"state": "closed", "state_reason": "completed"})
req("PUT", R + "/issues/%d/lock" % crash_no, {"lock_reason": "resolved"}, ok=(204,))

# test issue
req("POST", R + "/issues/%d/comments" % test_no,
    {"body": "The test went in with #%d." % pr_no})
req("PATCH", R + "/issues/%d" % test_no, {"state": "closed", "state_reason": "completed"})

# duplicate issue
req("POST", R + "/issues/%d/comments" % dup_no,
    {"body": "Same crash as #%d." % crash_no})
req("PATCH", R + "/issues/%d" % dup_no, {
    "state": "closed",
    "state_reason": "duplicate",
    "duplicate_of": crash_id,
    "duplicate_of_issue_id": crash_id,
})

# verification
c = req("GET", R + "/issues/%d" % crash_no)
if not (c["state"] == "closed" and c["state_reason"] == "completed" and c["locked"]):
    sys.exit("crash issue check failed: state=%s reason=%s locked=%s" % (c["state"], c["state_reason"], c["locked"]))
t = req("GET", R + "/issues/%d" % test_no)
if t["state"] != "closed":
    sys.exit("test issue is not closed")
d = req("GET", R + "/issues/%d" % dup_no)
if not (d["state"] == "closed" and d["state_reason"] == "duplicate"):
    sys.exit("duplicate issue check failed: state=%s reason=%s" % (d["state"], d["state_reason"]))

print("done: crash #%d, test #%d, duplicate #%d, pull request #%d" % (crash_no, test_no, dup_no, pr_no))
