#!/usr/bin/env python3
"""
Seeds a scratch GitHub repository with pull requests in each state a pull
request can be in, for the gateway's live runs (spec/PLAN-GITHUB-PULLS.md,
phases 9 and 10). Only GitHub's REST API, no clone: the token is read from
ARTIE's credentials (~/.artie/credentials, [https://api.github.com] bearer)
and never printed or put on a command line.

    python3 seed.py OWNER/REPO            # what it would do, nothing written
    python3 seed.py OWNER/REPO --write    # do it

It is safe to run again: a pull request whose title is already there is
left alone. It writes only to the repository it is given.

The states, each its own pull request, each changing its own file so they
don't collide, except "in conflict", which collides on purpose:
  clean       an ordinary change, open
  draft       the same, as a draft
  conflict    the branch and its base change the same line
  merged      opened, then merged
  closed      opened, then closed
  blocked     into `protected-review`, a base whose protection asks for an
              approving review, which the author can't give
  behind      into `protected-strict`, a base whose protection asks for
              branches to be up to date, then the base moves on
GitHub's `unknown` can't be made: it is what GitHub says while it works out
mergeability, just after a push. The live run may catch it.
"""
import base64
import configparser
import json
import os
import sys
import urllib.error
import urllib.request

API = "https://api.github.com"


def token():
    credentials = configparser.ConfigParser()
    credentials.read(os.path.expanduser("~/.artie/credentials"))
    return credentials[API]["bearer"].strip()


class GitHub:
    def __init__(self, repo, write):
        self.repo, self.write, self._token = repo, write, token()

    def call(self, method, path, body=None):
        if method != "GET" and not self.write:
            print(f"  would {method} {path} {json.dumps(body) if body else ''}")
            return {}
        request = urllib.request.Request(
            f"{API}/repos/{self.repo}{path}",
            method=method,
            data=json.dumps(body).encode() if body is not None else None,
            headers={
                "Authorization": f"Bearer {self._token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as answer:
                text = answer.read()
                return json.loads(text) if text else {}
        except urllib.error.HTTPError as refusal:
            detail = refusal.read().decode(errors="replace")[:300]
            sys.exit(f"{method} {path}: {refusal.code} {detail}")

    def head(self, branch):
        return self.call("GET", f"/git/ref/heads/{branch}")["object"]["sha"]

    def branch(self, name, base):
        existing = self.call("GET", "/git/matching-refs/heads/" + name)
        if not any(ref["ref"] == f"refs/heads/{name}" for ref in existing):
            self.call("POST", "/git/refs", {"ref": f"refs/heads/{name}", "sha": self.head(base) if self.write else "?"})

    def put(self, path, text, message, branch):
        current = self.call("GET", f"/contents/{path}?ref={branch}") if self.exists(path, branch) else {}
        body = {"message": message, "content": base64.b64encode(text.encode()).decode(), "branch": branch}
        if current.get("sha"):
            body["sha"] = current["sha"]
        self.call("PUT", f"/contents/{path}", body)

    def exists(self, path, branch):
        if not self.write and branch != "main":
            return False
        request_path = f"/contents/{path}?ref={branch}"
        try:
            urllib.request.urlopen(urllib.request.Request(
                f"{API}/repos/{self.repo}{request_path}",
                headers={"Authorization": f"Bearer {self._token}", "Accept": "application/vnd.github+json"},
            ), timeout=30)
            return True
        except urllib.error.HTTPError:
            return False

    def pull(self, title, head, base, draft=False):
        found = [p for p in self.call("GET", "/pulls?state=all&per_page=100") if p["title"] == title]
        if found:
            print(f"  #{found[0]['number']} {title}: already there")
            return found[0]
        return self.call("POST", "/pulls", {"title": title, "head": head, "base": base, "draft": draft,
                                             "body": "Seeded for the HEART gateway's live runs."})


def seed(github):
    print("bases")
    github.put("garden.txt", "tomatoes: water daily\nbeans: water weekly\n", "A garden to change", "main")
    for base in ("protected-review", "protected-strict"):
        github.branch(base, "main")

    print("clean")
    github.branch("clean", "main")
    github.put("clean.txt", "Mulch the beds.\n", "Mulch the beds", "clean")
    github.pull("Mulch the beds", "clean", "main")

    print("draft")
    github.branch("draft", "main")
    github.put("draft.txt", "Plan the autumn planting.\n", "Plan the autumn planting", "draft")
    github.pull("Plan the autumn planting", "draft", "main", draft=True)

    print("conflict")
    github.branch("conflict", "main")
    github.put("garden.txt", "tomatoes: water twice daily\nbeans: water weekly\n", "Water the tomatoes more", "conflict")
    github.pull("Water the tomatoes more", "conflict", "main")
    github.put("garden.txt", "tomatoes: water every other day\nbeans: water weekly\n", "Water the tomatoes less", "main")

    print("merged")
    github.branch("merged", "main")
    github.put("merged.txt", "Prune the roses.\n", "Prune the roses", "merged")
    merged = github.pull("Prune the roses", "merged", "main")
    if merged.get("number") and not merged.get("merged_at"):
        github.call("PUT", f"/pulls/{merged['number']}/merge", {"merge_method": "squash"})

    print("closed")
    github.branch("closed", "main")
    github.put("closed.txt", "Pave the lawn.\n", "Pave the lawn", "closed")
    closed = github.pull("Pave the lawn", "closed", "main")
    if closed.get("number") and closed.get("state") == "open":
        github.call("PATCH", f"/pulls/{closed['number']}", {"state": "closed"})

    print("blocked: protection asks for an approving review")
    github.call("PUT", "/branches/protected-review/protection", {
        "required_status_checks": None, "enforce_admins": True, "restrictions": None,
        "required_pull_request_reviews": {"required_approving_review_count": 1},
    })
    github.branch("blocked", "protected-review")
    github.put("blocked.txt", "Build a greenhouse.\n", "Build a greenhouse", "blocked")
    github.pull("Build a greenhouse", "blocked", "protected-review")

    print("behind: protection asks for branches to be up to date, then the base moves on")
    github.call("PUT", "/branches/protected-strict/protection", {
        "required_status_checks": {"strict": True, "contexts": []}, "enforce_admins": True,
        "restrictions": None, "required_pull_request_reviews": None,
    })
    github.branch("behind", "protected-strict")
    github.put("behind.txt", "Add a compost bin.\n", "Add a compost bin", "behind")
    github.pull("Add a compost bin", "behind", "protected-strict")
    github.put("strict.txt", "The base moved on.\n", "The base moves on", "protected-strict")


if __name__ == "__main__":
    if len(sys.argv) < 2 or "/" not in sys.argv[1]:
        sys.exit("usage: seed.py OWNER/REPO [--write]")
    seed(GitHub(sys.argv[1], write="--write" in sys.argv))
