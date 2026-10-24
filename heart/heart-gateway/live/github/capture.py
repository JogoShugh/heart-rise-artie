#!/usr/bin/env python3
"""
Captures, for each pull request of a seeded repository, the same read made
two ways, GET only: straight to GitHub's REST API, and through the HEART
gateway, with the explanation its hr:unavailable link leads to. Each
exchange is kept as it went, request and response, but for the token,
which is written as <token> (spec/PLAN-GITHUB-PULLS.md, phase 9).

    python3 capture.py OWNER/REPO GATEWAY_URL OUT_DIR [NUMBER...]

The gateway must be running, calling GitHub as each caller
(UPSTREAM_AUTH=caller). The token is read from ~/.artie/credentials.
"""
import configparser
import json
import os
import sys
import urllib.error
import urllib.request

KEPT_HEADERS = ("content-type", "etag", "link", "cache-control", "vary", "x-github-api-version-selected")


def token():
    credentials = configparser.ConfigParser()
    credentials.read(os.path.expanduser("~/.artie/credentials"))
    return credentials["https://api.github.com"]["bearer"].strip()


def exchange(url, headers, secret):
    """One GET, as sent and as answered; the token is never written down."""
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=60) as answer:
            status, response_headers, body = answer.status, dict(answer.headers), answer.read()
    except urllib.error.HTTPError as refusal:
        status, response_headers, body = refusal.code, dict(refusal.headers), refusal.read()
    shown = {name: value.replace(secret, "<token>") for name, value in headers.items()}
    return {
        "request": {"method": "GET", "url": url, "headers": shown},
        "response": {
            "status": status,
            "headers": {k: v for k, v in response_headers.items() if k.lower() in KEPT_HEADERS},
            "body": json.loads(body) if body else None,
        },
    }


def main():
    repo, gateway, out = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3]
    numbers = [int(n) for n in sys.argv[4:]] or range(1, 8)
    secret = token()
    os.makedirs(out, exist_ok=True)
    for number in numbers:
        path = f"/repos/{repo}/pulls/{number}"
        github = exchange(
            f"https://api.github.com{path}",
            {"Authorization": f"Bearer {secret}", "Accept": "application/vnd.github+json",
             "X-GitHub-Api-Version": "2022-11-28"},
            secret,
        )
        heart = exchange(f"{gateway}{path}", {"Authorization": f"Bearer {secret}", "Accept": "application/hal+json"}, secret)
        unavailable = heart["response"]["body"].get("_links", {}).get("hr:unavailable", {}).get("href")
        explanation = exchange(f"{gateway}{unavailable}", {"Accept": "application/json"}, secret) if unavailable else None
        # The same read at L1 and L2: the forms by link to a shared, content-addressed affordance set,
        # and at L2 each form's schema by link too (HAL-SCHEMA-FORMS-HR).
        rungs = {}
        for rung, prefer in (("L1", "forms=link"), ("L2", "forms=link, schema=link")):
            read = exchange(f"{gateway}{path}", {"Authorization": f"Bearer {secret}", "Accept": "application/hal+json",
                                                 "Prefer": prefer}, secret)
            forms = read["response"]["body"].get("_links", {}).get("hr:forms", {}).get("href")
            chain = {"read": read, "set": exchange(f"{gateway}{forms}", {"Accept": "application/hal+json"}, secret)}
            if rung == "L2":
                refs = [f["schema"]["$ref"] for f in chain["set"]["response"]["body"].get("_forms", {}).values()
                        if "$ref" in f.get("schema", {})]
                chain["schemas"] = [exchange(f"{gateway}{ref}", {"Accept": "application/schema+json"}, secret)
                                    for ref in refs[:1]]
            rungs[rung] = chain
        sizes = {"L0": len(json.dumps(heart["response"]["body"], separators=(",", ":")))}
        for rung, chain in rungs.items():
            sizes[rung] = len(json.dumps(chain["read"]["response"]["body"], separators=(",", ":")))
            sizes[rung + " set"] = len(json.dumps(chain["set"]["response"]["body"], separators=(",", ":")))
        with open(os.path.join(out, f"pull-{number}.json"), "w") as file:
            json.dump({"number": number, "github": github, "heart": heart, "explanation": explanation,
                       "rungs": rungs, "sizes": sizes}, file, indent=1)
        print(f"#{number}: GitHub {github['response']['status']}, HEART {heart['response']['status']}")
    if secret in "".join(open(os.path.join(out, f)).read() for f in os.listdir(out)):
        sys.exit("the token was written down; nothing should be published")


if __name__ == "__main__":
    main()
