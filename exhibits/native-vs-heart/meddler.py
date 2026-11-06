#!/usr/bin/env python3
"""A teammate who moves the world while a triage run is under way (exhibits/native-vs-heart, "the meddler").

Usage: meddler.py OWNER REPO STAMP MOVES LOG
  MOVES: a comma list of M1..M5; LOG: where to write what it did, as JSON.

It watches the repository by reading each next issue number directly (GitHub's lists lag its writes), and
knows the run's items by their titles, which carry STAMP. Once the run's pull request is open, it makes its
moves, each on the state it reads first, as a teammate would:

  M1  closes the crash issue as not planned, and says why
  M2  locks the crash issue's conversation as off-topic
  M3  closes the duplicate as completed: it is a different crash, and says so
  M4  converts the pull request to a draft: it needs a migration note first
  M5  adds a reviewer's note to the pull request's description

A move whose target the run already changed is logged "late": that run says nothing about the move.
Writes only to the repository it is given; the scratch repository is the only one it is run against.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

if len(sys.argv) != 6:
    sys.exit(__doc__)
OWNER, REPO, STAMP, MOVES, LOG = sys.argv[1:6]
MOVES = [m.strip() for m in MOVES.split(",") if m.strip()]
TOKEN = os.environ["GITHUB_TOKEN"]
API = "https://api.github.com"
R = "/repos/%s/%s" % (OWNER, REPO)
TITLES = {
    "crash": "Crash when a bed has no name (%s)" % STAMP,
    "test": "Test blank bed names (%s)" % STAMP,
    "dup": "Garden crashes on an empty bed name (%s)" % STAMP,
    "pr": "Refuse a bed with no name (%s)" % STAMP,
}
NOTE = "Reviewer: please add a migration note before this goes in."
POLL = 0.5
GIVE_UP = 400


def call(method, path, body=None, url=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url or API + path, data=data, method=method)
    r.add_header("Authorization", "Bearer " + TOKEN)
    r.add_header("Accept", "application/vnd.github+json")
    r.add_header("X-GitHub-Api-Version", "2022-11-28")
    r.add_header("User-Agent", "triage-meddler")
    if data is not None:
        r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, None


def newest():
    numbers = [0]
    for kind in ("issues", "pulls"):
        status, items = call("GET", R + "/%s?state=all&sort=created&direction=desc&per_page=5" % kind)
        if status == 200:
            numbers += [i["number"] for i in items]
    return max(numbers)


log = {"stamp": STAMP, "moves": [], "numbers": {}, "started": time.time()}


def write_log():
    with open(LOG, "w") as f:
        json.dump(log, f, indent=2)


def moved(move, landed, why, **more):
    entry = {"move": move, "landed": landed, "why": why, "at": time.time()}
    entry.update(more)
    log["moves"].append(entry)
    write_log()
    print("%s %s: %s" % (move, "landed" if landed else "late", why), flush=True)


def issue(n):
    return call("GET", R + "/issues/%d" % n)[1]


def m1(n):
    crash = issue(n["crash"])
    if crash["state"] != "open":
        return moved("M1", False, "the crash issue was already closed, as %s" % crash["state_reason"])
    status, _ = call("PATCH", R + "/issues/%d" % n["crash"], {"state": "closed", "state_reason": "not_planned"})
    call("POST", R + "/issues/%d/comments" % n["crash"],
         {"body": "Closing here: this is tracked on the roadmap instead."})
    moved("M1", status == 200, "closed the crash issue as not planned (HTTP %d)" % status)


def m2(n):
    crash = issue(n["crash"])
    if crash["locked"]:
        return moved("M2", False, "the crash issue was already locked, as %s" % crash["active_lock_reason"])
    status, _ = call("PUT", R + "/issues/%d/lock" % n["crash"], {"lock_reason": "off-topic"})
    moved("M2", status == 204, "locked the crash issue as off-topic (HTTP %d)" % status)


def m3(n):
    dup = issue(n["dup"])
    if dup["state"] != "open":
        return moved("M3", False, "the duplicate was already closed, as %s" % dup["state_reason"])
    status, _ = call("PATCH", R + "/issues/%d" % n["dup"], {"state": "closed", "state_reason": "completed"})
    call("POST", R + "/issues/%d/comments" % n["dup"],
         {"body": "Not the same crash: this one comes from the demo garden's import. Fixed there."})
    moved("M3", status == 200, "closed the duplicate as completed (HTTP %d)" % status)


def m4(n):
    _, pull = call("GET", R + "/pulls/%d" % n["pr"])
    if pull["merged"] or pull["state"] != "open":
        return moved("M4", False, "the pull request was already %s" % ("merged" if pull["merged"] else "closed"))
    query = "mutation($id: ID!) { convertPullRequestToDraft(input: {pullRequestId: $id}) { pullRequest { isDraft } } }"
    status, answer = call("POST", None, {"query": query, "variables": {"id": pull["node_id"]}},
                          url=API + "/graphql")
    draft = status == 200 and not (answer or {}).get("errors")
    moved("M4", draft, "converted the pull request to a draft (HTTP %d)" % status)


def m5(n):
    _, pull = call("GET", R + "/pulls/%d" % n["pr"])
    body = pull.get("body") or ""
    if "tested by" in body or pull["merged"]:
        return moved("M5", False, "the run had already written the description")
    status, _ = call("PATCH", R + "/pulls/%d" % n["pr"], {"body": body + "\n\n" + NOTE})
    moved("M5", status == 200, "added a reviewer's note to the description (HTTP %d)" % status, before=body)


# The order a teammate on a deadline would take: the description and the draft first, they race the run's
# next writes; then the issues, which the run reaches only after the merge.
ORDER = [("M5", m5), ("M4", m4), ("M1", m1), ("M2", m2), ("M3", m3)]

found = log["numbers"]
next_number = newest() + 1
write_log()
deadline = time.time() + GIVE_UP
while time.time() < deadline and "pr" not in found:
    status, item = call("GET", R + "/issues/%d" % next_number)
    if status != 200:
        time.sleep(POLL)
        continue
    for name, title in TITLES.items():
        if item["title"] == title and (name == "pr") == ("pull_request" in item):
            found[name] = next_number
            print("saw %s #%d" % (name, next_number), flush=True)
    next_number += 1
write_log()
if "pr" not in found:
    sys.exit("the run's pull request never appeared")
for name, move in ORDER:
    if name in MOVES:
        try:
            move(found)
        except Exception as e:  # a teammate's move that fails says so, and the rest go on
            moved(name, False, "failed: %s" % e)
log["finished"] = time.time()
write_log()
