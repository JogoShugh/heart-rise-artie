#!/usr/bin/env python3
# The triage task as native-script.py does it, taking the care the HEART gateway takes, by hand: each read
# conditional (If-None-Match, a 304 for what hasn't changed); before each write, the target, the repository
# and the caller read, and the write checked against them (is it open, is it locked, may the caller push);
# GitHub's own sha guards where it has them (the branch, the merge); and after each write, the target read
# again to see the write landed. GitHub refuses If-Match on writes, so a write between the check and the act
# can't be caught, by this script or by the gateway. GITHUB_API points it at the gateway, passed through.
import sys, os, json, time, base64
import urllib.request, urllib.error

if len(sys.argv) != 5:
    sys.exit("usage: script.py OWNER REPO LABEL STAMP")
OWNER, REPO, LABEL, STAMP = sys.argv[1:5]
TOKEN = os.environ.get("GITHUB_TOKEN")
if not TOKEN:
    sys.exit("GITHUB_TOKEN is not set")

API = os.environ.get("GITHUB_API", "https://api.github.com")
PASSED_THROUGH = API != "https://api.github.com"
R = "/repos/%s/%s" % (OWNER, REPO)

# What each read last answered, with its ETag, by path.
copies = {}


def req(method, path, body=None, ok=(200, 201, 204)):
    print("%s %s" % (method, path), flush=True)
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(API + path, data=data, method=method)
    r.add_header("Authorization", "Bearer " + TOKEN)
    r.add_header("Accept", "application/vnd.github+json")
    r.add_header("X-GitHub-Api-Version", "2022-11-28")
    r.add_header("User-Agent", "triage-script")
    if PASSED_THROUGH:
        r.add_header("X-Gateway-Mode", "pass-through")
    held = copies.get(path) if method == "GET" else None
    if held:
        r.add_header("If-None-Match", held[0])
    if data is not None:
        r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r) as resp:
            status, raw, etag = resp.status, resp.read(), resp.headers.get("ETag")
    except urllib.error.HTTPError as e:
        status, raw, etag = e.code, e.read(), None
    if status == 304 and held:
        return held[1]
    if status not in ok:
        sys.exit("FAILED: %s %s -> HTTP %d: %s" % (method, path, status, raw.decode(errors="replace")[:500]))
    answer = None
    if raw:
        try:
            answer = json.loads(raw)
        except ValueError:
            answer = None
    if method == "GET" and etag:
        copies[path] = (etag, answer)
    return answer


def get(path, ok=(200,)):
    return req("GET", path, ok=ok)


def check(target, allowed, why):
    """The target as it is now, with the repository and the caller, as the gateway reads them to work out offers."""
    now = get(target) if target else None
    repo = get(R)
    me = get("/user")
    if not allowed(now, repo, me):
        sys.exit("refused before acting: " + why)
    return now


def landed(target, test, what):
    """The target read again after the write, as the gateway reads it to answer with the state the act left."""
    after = get(target)
    if not test(after):
        sys.exit("the write didn't land: " + what)
    return after


def may_push(_, repo, __):
    return repo["permissions"]["push"]


def open_issue(issue, repo, me):
    return issue["state"] == "open" and (issue["user"]["login"] == me["login"] or repo["permissions"]["triage"])


def unlocked(issue, repo, _):
    return not issue["locked"] or repo["permissions"]["push"]


def b64(s):
    return base64.b64encode(s.encode()).decode()


def open_new_issue(fields):
    check(None, lambda _, repo, __: repo["has_issues"] and not repo["archived"], "the repository takes no issues")
    made = req("POST", R + "/issues", fields)
    return landed(R + "/issues/%d" % made["number"], lambda i: i["state"] == "open" and i["title"] == fields["title"],
                  "issue %s" % fields["title"])


branch = "fix-blank-names-" + STAMP

# main sha
main = get(R + "/git/ref/heads/main")
main_sha = main["object"]["sha"]

# crash issue
crash = open_new_issue({
    "title": "Crash when a bed has no name (%s)" % STAMP,
    "body": "A bed with a blank name crashes the garden.",
    "labels": [LABEL],
    "assignees": [OWNER],
})
crash_no, crash_id = crash["number"], crash["id"]

# test issue
test = open_new_issue({
    "title": "Test blank bed names (%s)" % STAMP,
    "body": "This is part of #%d." % crash_no,
})
test_no, test_id = test["number"], test["id"]

# duplicate issue
dup = open_new_issue({
    "title": "Garden crashes on an empty bed name (%s)" % STAMP,
    "body": "This was seen on the demo garden.",
})
dup_no = dup["number"]

# branch: GitHub's own guard is the sha it starts from
check(None, may_push, "the caller can't push")
req("POST", R + "/git/refs", {"ref": "refs/heads/" + branch, "sha": main_sha})
landed(R + "/git/ref/heads/" + branch, lambda ref: ref["object"]["sha"] == main_sha, "the branch")

# commits
for name, message, text in [
    ("fix-%s.md" % STAMP, "Refuse a bed with no name", "Bed names may not be blank.\n"),
    ("test-%s.md" % STAMP, "Test that a blank bed name is refused", "A bed with a blank name is refused.\n"),
]:
    check(None, may_push, "the caller can't push")
    req("PUT", R + "/contents/" + name, {"message": message, "content": b64(text), "branch": branch})
    landed(R + "/contents/%s?ref=%s" % (name, branch), lambda f: f["content"].replace("\n", "") == b64(text),
           name)

# pull request
check(None, may_push, "the caller can't push")
pr = req("POST", R + "/pulls", {
    "title": "Refuse a bed with no name (%s)" % STAMP,
    "head": branch,
    "base": "main",
    "body": "Fixes #%d." % crash_no,
})
pr_no = pr["number"]
PULL = R + "/pulls/%d" % pr_no
landed(PULL, lambda p: p["state"] == "open", "the pull request")
body = "Fixes #%d and is tested by #%d." % (crash_no, test_no)
check(PULL, lambda p, repo, me: p["state"] == "open" and (p["user"]["login"] == me["login"] or
                                                       repo["permissions"]["push"]), "the pull request isn't open")
req("PATCH", PULL, {"body": body})
landed(PULL, lambda p: p["body"] == body, "the pull request's body")

# wait for mergeable
pr_now = None
for _ in range(30):
    pr_now = get(PULL)
    if pr_now.get("mergeable") is True and pr_now.get("mergeable_state") in ("clean", "unstable", "has_hooks"):
        break
    if pr_now.get("mergeable") is False:
        sys.exit("pull request is not mergeable (state %s)" % pr_now.get("mergeable_state"))
    time.sleep(2)
else:
    sys.exit("pull request did not become mergeable in time")

# merge: GitHub's own guard is the head sha
check(PULL, lambda p, repo, _: p["state"] == "open" and not p["merged"] and repo["permissions"]["push"],
      "the pull request can't be merged")
m = req("PUT", PULL + "/merge", {
    "merge_method": "squash",
    "commit_title": "Refuse a bed with no name (#%d)" % pr_no,
    "sha": pr_now["head"]["sha"],
})
if not m.get("merged"):
    sys.exit("merge response did not say merged")
landed(PULL, lambda p: p["merged"], "the merge")

CRASH, TEST, DUP = (R + "/issues/%d" % n for n in (crash_no, test_no, dup_no))

# crash issue: sub-issue, comment, close, lock
# The merge may already have closed the crash issue ("Fixes #N"): a sub-issue needs only triage access.
check(CRASH, lambda _, repo, __: repo["permissions"]["triage"], "the caller can't triage")
req("POST", CRASH + "/sub_issues", {"sub_issue_id": test_id})
landed(CRASH + "/sub_issues", lambda subs: any(s["id"] == test_id for s in subs), "the sub-issue")


def comment(target, text):
    before = check(target, unlocked, "the conversation is locked")
    req("POST", target + "/comments", {"body": text})
    landed(target, lambda i: i["comments"] == before["comments"] + 1, "the comment")


def close(target, fields):
    now = check(target, lambda i, repo, me: i["state"] == "closed" or open_issue(i, repo, me), "it can't be closed")
    if now["state"] == "closed" and now["state_reason"] == fields["state_reason"]:
        return  # already closed as asked, by the merge's "Fixes #N": nothing to do
    req("PATCH", target, fields)
    landed(target, lambda i: i["state"] == "closed" and i["state_reason"] == fields["state_reason"], "the close")


comment(CRASH, "Fixed in #%d, tested in #%d." % (pr_no, test_no))
close(CRASH, {"state": "closed", "state_reason": "completed"})
check(CRASH, lambda i, repo, _: not i["locked"] and repo["permissions"]["push"], "it can't be locked")
req("PUT", CRASH + "/lock", {"lock_reason": "resolved"}, ok=(204,))
landed(CRASH, lambda i: i["locked"], "the lock")

# test issue
comment(TEST, "The test went in with #%d." % pr_no)
close(TEST, {"state": "closed", "state_reason": "completed"})

# duplicate issue
comment(DUP, "Same crash as #%d." % crash_no)
close(DUP, {
    "state": "closed",
    "state_reason": "duplicate",
    "duplicate_of": crash_id,
    "duplicate_of_issue_id": crash_id,
})

print("done: crash #%d, test #%d, duplicate #%d, pull request #%d" % (crash_no, test_no, dup_no, pr_no))
