#!/usr/bin/env python3
"""
I8 of spec/PLAN-GITHUB-ISSUES.md: issues through the HEART gateway on the
scratch repository, each exchange kept as it went (the token written
<token>), with the plain GitHub request a client would have had to build
itself. Writes only to the repository it is given.

    python3 issues.py OWNER/REPO GATEWAY_URL OUT_FILE

Every new thing comes from a form a resource offered: the repository's
open-issue, then the issue's comment, each answered 201 with Location. Then
the issue's acts, one by one, each sent with the ETag of the read it came
from; its comments and sub-issues followed by the links the issue carries;
an act after someone else changed the issue on GitHub, refused 412; and an
act the issue doesn't offer, refused 409.
"""
import json
import sys
import time
import urllib.request

from act import Client, run_trace, token, write


def github(secret, method, path, body):
    """Another client's change, straight to GitHub."""
    request = urllib.request.Request(
        "https://api.github.com" + path, method=method, data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {secret}", "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28"})
    with urllib.request.urlopen(request, timeout=30) as answer:
        return answer.status


def settled(client, path, tries=10):
    """Reads [path] until two reads in a row agree: GitHub goes on changing an issue for a moment after an act
    (its updated_at moves when a comment is edited), and an act sent in that moment is rightly stale."""
    read = client.read(path)
    for _ in range(tries):
        time.sleep(2)
        again = client.read(path)
        if again["response"]["headers"].get("ETag") == read["response"]["headers"].get("ETag"):
            return again
        read = again
    return read


def main():
    repo, gateway, out = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3]
    owner = repo.split("/")[0]
    secret = token()
    client = Client(gateway, secret)
    steps = []

    def step(name, plain, read, act, after=None, expect="2xx"):
        """One step, with the status the run expects of it: "2xx", or a status such as 201, 409, 412."""
        steps.append({"name": name, "plain": plain, "read": read, "act": act, "after": after, "expect": expect})
        print(f"{name}: {act['response']['status']}")
        return act

    def acted(path, key, body, name, plain, expect="2xx"):
        """Reads [path], then sends its form [key] with [body], as a HEART client does."""
        read = settled(client, path)
        return step(name, plain, read, client.act(read, key, body), expect=expect)

    def created(answer):
        location = answer["response"]["headers"].get("Location")
        if not location:
            write(out, steps, secret)
            sys.exit(f"no Location: {answer['response']['status']} {answer['response']['body']}")
        return location

    # New things, from the forms resources offer.
    body = {"title": "Weed the paths", "body": "Opened through the HEART gateway's form."}
    issue = created(acted(f"/repos/{repo}", "github:open-issue", body, "Open an issue from the repository",
                          {"method": "POST", "path": f"/repos/{repo}/issues", "body": body}, expect=201))
    number = issue.rsplit("/", 1)[1]
    body = {"title": "Weed the gravel path", "body": "A sub-issue, opened through the same form."}
    child = created(acted(f"/repos/{repo}", "github:open-issue", body, "Open a second issue",
                          {"method": "POST", "path": f"/repos/{repo}/issues", "body": body}, expect=201))
    body = {"body": "Commented through the HEART gateway's form."}
    commented = acted(issue, "github:comment", body, "Comment on the issue",
                      {"method": "POST", "path": f"{issue}/comments", "body": body}, expect=201)
    comment = commented["response"]["headers"].get("Location")

    # The comments, found by the link the issue carries; the new one edited, when GitHub took it.
    read = client.read(issue)
    comments = read["response"]["body"]["_links"]["github:issues-list-comments"]["href"]
    listed = client.read(comments)
    step("Follow the issue's comments link", None, read, listed)
    if comment:
        body = {"body": "Commented, then edited, through the HEART gateway's forms."}
        acted(comment, "github:edit", body, "Edit the comment",
              {"method": "PATCH", "path": comment, "body": body})

    # The issue's own acts.
    for key, body, name, method, path in [
        ("github:edit", {"title": "Weed the paths, all of them"}, "Edit the issue's title", "PATCH", issue),
        ("github:label", {"labels": ["chore"]}, "Label it", "POST", f"{issue}/labels"),
        ("github:unlabel", {"name": "chore"}, "Take the label off", "DELETE", f"{issue}/labels/chore"),
        ("github:assign", {"assignees": [owner]}, "Assign the owner", "POST", f"{issue}/assignees"),
        ("github:unassign", {"assignees": [owner]}, "Unassign the owner", "DELETE", f"{issue}/assignees"),
        ("github:lock", {"lock_reason": "off-topic"}, "Lock the conversation", "PUT", f"{issue}/lock"),
    ]:
        plain_body = None if key == "github:unlabel" else body
        acted(issue, key, body, name, {"method": method, "path": path, "body": plain_body})

    read = settled(client, issue)
    step("Lock it again, which isn't offered", {"method": "PUT", "path": f"{issue}/lock", "body": {}},
         read, client.act(read, "github:lock", {}), expect=409)
    acted(issue, "github:unlock", {}, "Unlock the conversation",
          {"method": "DELETE", "path": f"{issue}/lock", "body": None})

    # Sub-issues: named by id, not number, as GitHub does; listed by the link the issue carries.
    child_id = client.read(child)["response"]["body"]["id"]
    body = {"sub_issue_id": child_id}
    acted(issue, "github:add-sub-issue", body, "Add the second issue as a sub-issue",
          {"method": "POST", "path": f"{issue}/sub_issues", "body": body})
    read = client.read(issue)
    sub_issues = read["response"]["body"]["_links"]["github:issues-list-sub-issues"]["href"]
    step("Follow the issue's sub-issues link", None, read, client.read(sub_issues))
    acted(issue, "github:remove-sub-issue", body, "Remove the sub-issue",
          {"method": "DELETE", "path": f"/repos/{repo}/issues/{number}/sub_issue", "body": body})

    # Stale: someone else changes the issue on GitHub between the read and the act.
    read = settled(client, issue)
    changed = github(secret, "PATCH", f"/repos/{repo}/issues/{number}", {"body": "Changed straight on GitHub."})
    step(f"Close it after another change on GitHub (GitHub answered the change {changed})",
         {"method": "PATCH", "path": issue, "body": {"state": "closed", "state_reason": "completed"}},
         read, client.act(read, "github:close", {}), expect=412)

    acted(child, "github:close-not-planned", {}, "Close the second issue as not planned",
          {"method": "PATCH", "path": child, "body": {"state": "closed", "state_reason": "not_planned"}})
    acted(issue, "github:close", {}, "Close the issue as completed",
          {"method": "PATCH", "path": issue, "body": {"state": "closed", "state_reason": "completed"}})
    acted(issue, "github:reopen", {}, "Reopen it",
          {"method": "PATCH", "path": issue, "body": {"state": "open", "state_reason": "reopened"}})
    body = {"duplicate_issue_id": child_id}
    acted(issue, "github:close-duplicate", body, "Close it as a duplicate of the second",
          {"method": "PATCH", "path": issue,
           "body": {"state": "closed", "state_reason": "duplicate", "duplicate_issue_id": child_id}})

    write(out, steps, secret)
    # The same run as a run trace, for the run report (heart-run-report skill).
    write(out.replace(".json", "") + "-run.json", run_trace(steps), secret)


if __name__ == "__main__":
    main()
