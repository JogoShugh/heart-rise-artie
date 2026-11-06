#!/usr/bin/env python3
"""
Phase 10 of spec/PLAN-GITHUB-PULLS.md: acts through the HEART gateway on a
seeded scratch repository, each exchange kept as it went (the token written
<token>), with the plain GitHub request a client would have had to build
itself. Writes only to the repository it is given.

    python3 act.py OWNER/REPO GATEWAY_URL OUT_FILE [more]

Steps: close #1 and reopen it; merge #7; a stale merge of #1 after another
push to its branch, refused 412; a merge of #6, which isn't offered, refused
409. With `more`: edit #1's title and body; a review request naming nobody,
refused by the form; one naming someone who isn't a collaborator, refused
by GitHub in its words; a push to main, then #1's branch updated through its
form; and updated again, refused in GitHub's words, since it's up to date. Each act is the form the gateway offered, sent with the ETag of the
read it came from, as any HEART client sends it.
"""
import base64
import datetime
import configparser
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

KEPT = ("content-type", "etag", "content-location", "location", "cache-control")


def token():
    credentials = configparser.ConfigParser()
    credentials.read(os.path.expanduser("~/.artie/credentials"))
    return credentials["https://api.github.com"]["bearer"].strip()


class Client:
    def __init__(self, gateway, secret):
        self.gateway, self.secret = gateway, secret

    def send(self, method, url, headers, body=None):
        at = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
        started = time.monotonic()
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(url, method=method, data=data, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=60) as answer:
                status, response_headers, text = answer.status, dict(answer.headers), answer.read()
        except urllib.error.HTTPError as refusal:
            status, response_headers, text = refusal.code, dict(refusal.headers), refusal.read()
        return {
            "at": at,
            "ms": round((time.monotonic() - started) * 1000),
            "request": {"method": method, "url": url,
                        "headers": {k: v.replace(self.secret, "<token>") for k, v in headers.items()},
                        "body": body},
            "response": {"status": status,
                         "headers": {k: v for k, v in response_headers.items() if k.lower() in KEPT},
                         "body": json.loads(text) if text else None},
        }

    def read(self, path):
        return self.send("GET", self.gateway + path,
                         {"Authorization": f"Bearer {self.secret}", "Accept": "application/hal+json"})

    def act(self, read, key, body):
        """Sends the form [key] of [read] with [body] and the read's ETag, as a HEART client does."""
        form = read["response"]["body"]["_forms"].get(key)
        target = form["_links"]["target"]["href"] if form else self.guess(read, key)
        return self.send(
            form["method"] if form else "POST", self.gateway + target,
            {"Authorization": f"Bearer {self.secret}", "Accept": "application/hal+json",
             "Content-Type": "application/json", "If-Match": read["response"]["headers"]["ETag"]},
            body,
        )

    @staticmethod
    def guess(read, key):
        """An act the read doesn't offer: a client that sends it anyway, by the path it was once told."""
        return read["response"]["body"]["_links"]["self"]["href"] + "/actions/" + key.split(":")[1]


def push(repo, secret, branch, path, text, message):
    """Another client's push to [branch], straight to GitHub: the head moves on."""
    headers = {"Authorization": f"Bearer {secret}", "Accept": "application/vnd.github+json",
               "X-GitHub-Api-Version": "2022-11-28"}
    url = f"https://api.github.com/repos/{repo}/contents/{path}"
    current = None
    try:
        with urllib.request.urlopen(urllib.request.Request(f"{url}?ref={branch}", headers=headers), timeout=30) as a:
            current = json.load(a)["sha"]
    except urllib.error.HTTPError:
        pass
    body = {"message": message, "content": base64.b64encode(text.encode()).decode(), "branch": branch}
    if current:
        body["sha"] = current
    request = urllib.request.Request(url, method="PUT", data=json.dumps(body).encode(), headers=headers)
    with urllib.request.urlopen(request, timeout=30) as answer:
        return answer.status


def settled(client, path, tries=15):
    """Reads [path] again until GitHub has worked out mergeability: as the connector's reason says, shortly."""
    for _ in range(tries):
        read = client.read(path)
        if read["response"]["body"].get("mergeable") is not None:
            return read
        time.sleep(2)
    return read


def more(client, repo, secret, step, pull):
    """Edit, review requests, and a branch update, each through the form the gateway offered."""
    read = client.read(pull(1))
    edited = client.act(read, "github:edit", {"title": "Mulch the beds, all of them",
                                              "body": "Edited through the HEART gateway's form."})
    step("Edit #1's title and body", {"method": "PATCH", "path": pull(1),
                                     "body": {"title": "Mulch the beds, all of them",
                                              "body": "Edited through the HEART gateway's form."}}, read, edited)

    read = client.read(pull(1))
    nobody = client.act(read, "github:request-review", {})
    step("Request a review from nobody", {"method": "POST", "path": pull(1) + "/requested_reviewers", "body": {}},
         read, nobody)

    read = client.read(pull(1))
    stranger = client.act(read, "github:request-review", {"reviewers": ["octocat"]})
    step("Request a review from someone who isn't a collaborator",
         {"method": "POST", "path": pull(1) + "/requested_reviewers", "body": {"reviewers": ["octocat"]}},
         read, stranger)

    pushed = push(repo, secret, "main", "compost.txt", "Turn the compost weekly.\n", "The base moves on")
    first = client.read(pull(1))
    step("Read #1 just after a push to main",
         None, first, first)
    read = settled(client, pull(1))
    head = read["response"]["body"]["head"]["sha"]
    updated = client.act(read, "github:update-branch", {})
    step(f"Update #1's branch after a push to main (GitHub answered the push {pushed})",
         {"method": "PUT", "path": pull(1) + "/update-branch", "body": {"expected_head_sha": head}}, read, updated)

    read = settled(client, pull(1))
    again = client.act(read, "github:update-branch", {})
    step("Update #1's branch again", {"method": "PUT", "path": pull(1) + "/update-branch",
                                      "body": {"expected_head_sha": read["response"]["body"]["head"]["sha"]}},
         read, again)


def main():
    repo, gateway, out = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3]
    secret = token()
    client = Client(gateway, secret)
    pull = lambda n: f"/repos/{repo}/pulls/{n}"
    steps = []

    def step(name, plain, read, act, after=None):
        steps.append({"name": name, "plain": plain, "read": read, "act": act, "after": after})
        print(f"{name}: {act['response']['status']}")

    if sys.argv[4:] == ["more"]:
        more(client, repo, secret, step, pull)
        write(out, steps, secret)
        return

    read = client.read(pull(1))
    closed = client.act(read, "github:close", {})
    step("Close #1", {"method": "PATCH", "path": pull(1), "body": {"state": "closed"}}, read, closed)

    read = client.read(pull(1))
    reopened = client.act(read, "github:reopen", {})
    step("Reopen #1", {"method": "PATCH", "path": pull(1), "body": {"state": "open"}}, read, reopened)

    read = client.read(pull(7))
    head = read["response"]["body"]["head"]["sha"]
    merged = client.act(read, "github:merge", {"merge_method": "squash"})
    step("Merge #7", {"method": "PUT", "path": pull(7) + "/merge", "body": {"merge_method": "squash", "sha": head}},
         read, merged, after=client.read(pull(7)))

    read = client.read(pull(1))
    pushed = push(repo, secret, "clean", "clean.txt", "Mulch the beds, twice.\n", "Another push to the branch")
    stale = client.act(read, "github:merge", {"merge_method": "squash"})
    step(f"Merge #1 after another push (GitHub answered the push {pushed})",
         {"method": "PUT", "path": pull(1) + "/merge",
          "body": {"merge_method": "squash", "sha": read["response"]["body"]["head"]["sha"]}}, read, stale)

    read = client.read(pull(6))
    refused = client.act(read, "github:merge", {"merge_method": "squash"})
    step("Merge #6, which isn't offered", {"method": "PUT", "path": pull(6) + "/merge",
                                           "body": {"merge_method": "squash"}}, read, refused)

    write(out, steps, secret)


def run_trace(steps):
    """The steps as a run trace (heart-run-report skill): scripted relation requests, values marked."""
    out = []
    for step in steps:
        act, read, plain = step["act"], step.get("read"), step.get("plain")
        url = act["request"]["url"]
        params = dict(act["request"].get("body") or {})
        if "/actions/" in url:
            params["relation"] = "github:" + url.rsplit("/actions/", 1)[1]
            fields = "".join(f", {name} {{{name}}}" for name in act["request"].get("body") or {})
            say = f"{step['name']}: act {{relation}}{fields}"
        else:
            params["path"] = urllib.parse.urlsplit(url).path
            say = f"{step['name']}: follow the link to {{path}}"
        trace = {"at": act.get("at"), "ms": act.get("ms"), "mode": "script", "say": say, "params": params,
                 "expect": step.get("expect", "2xx"), "request": act["request"], "response": act["response"]}
        if read:
            # The representation the step was chosen from: its address and its controls.
            trace["held"] = {"url": read["request"]["url"], "body": read["response"]["body"]}
        if read and act["request"]["method"] != "GET":
            trace["before"] = read["response"]["body"]
        if plain:
            trace["compare"] = {"label": "GitHub's API", "diff": False, "request": {
                "method": plain["method"], "url": "https://api.github.com" + plain["path"],
                "headers": {"Authorization": "Bearer <token>"}, "body": plain.get("body")}}
        out.append(trace)
    return {"steps": out}


def write(out, steps, secret):
    with open(out, "w") as file:
        json.dump(steps, file, indent=1)
    if secret in open(out).read():
        sys.exit("the token was written down; nothing should be published")


if __name__ == "__main__":
    main()
