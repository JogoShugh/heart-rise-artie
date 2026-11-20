#!/usr/bin/env python3
"""
Captures issues on the scratch repository read two ways, GET only: straight
to GitHub's REST API, and through the HEART gateway, with the explanation
its hr:unavailable link leads to (spec/PLAN-GITHUB-ISSUES.md, I8). Also the
repository, a comment, and the lists an issue links: its comments and its
sub-issues. The token is written <token>.

    python3 capture-issues.py OWNER/REPO GATEWAY_URL OUT_DIR NAME=PATH...

Each NAME=PATH is a path both GitHub and the gateway answer, such as
open=/repos/acme/rockets/issues/10; it is kept as OUT_DIR/issue-NAME.json.
"""
import json
import os
import sys

from capture import exchange, token


def main():
    repo, gateway, out = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3]
    secret = token()
    os.makedirs(out, exist_ok=True)
    github_headers = {"Authorization": f"Bearer {secret}", "Accept": "application/vnd.github+json",
                      "X-GitHub-Api-Version": "2022-11-28"}
    heart_headers = {"Authorization": f"Bearer {secret}", "Accept": "application/hal+json"}
    for named in sys.argv[4:]:
        name, path = named.split("=", 1)
        if not path.startswith(f"/repos/{repo}"):
            sys.exit(f"{path} is not in {repo}")
        github = exchange(f"https://api.github.com{path}", github_headers, secret)
        heart = exchange(f"{gateway}{path}", heart_headers, secret)
        body = heart["response"]["body"]
        unavailable = body.get("_links", {}).get("hr:unavailable", {}).get("href") if isinstance(body, dict) else None
        explanation = exchange(f"{gateway}{unavailable}", {"Accept": "application/json"}, secret) if unavailable else None
        with open(os.path.join(out, f"issue-{name}.json"), "w") as file:
            json.dump({"name": name, "path": path, "github": github, "heart": heart, "explanation": explanation},
                      file, indent=1)
        print(f"{name}: GitHub {github['response']['status']}, HEART {heart['response']['status']}")
    if secret in "".join(open(os.path.join(out, f)).read() for f in os.listdir(out)):
        sys.exit("the token was written down; nothing should be published")


if __name__ == "__main__":
    main()
