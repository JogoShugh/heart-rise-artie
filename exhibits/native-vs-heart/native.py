#!/usr/bin/env python3
"""Claude alone, against GitHub's REST API (spec/PLAN-NATIVE-VS-HEART-TRIAL.md): the native arm of the triage trial.

One agent, the same model ARTIE's planner and reasoner use, given the task in words and one tool, `http`, which
sends a request to https://api.github.com. No documentation, no gh, no MCP server: what the model knows of GitHub.
The harness adds the credential, which the model never sees; refuses, before sending, any request that could
write outside the scratch repository; keeps a YAML transcript, one document a call; and counts the model's tokens.

    native.py TASK.md OWNER REPO LABEL STAMP TRANSCRIPT.yaml

Reads the model key from ~/.anthropic/api_key and the GitHub token from ~/.artie/credentials; prints neither.
"""
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from checker import TOKEN as GITHUB_TOKEN

MODEL = "claude-sonnet-5-5"
MESSAGES = "https://api.anthropic.com/v1/messages"
API = "https://api.github.com"
MAX_CALLS = 90
MAX_SECONDS = 30 * 60
BODY_LIMIT = 20_000
KEY = (Path.home() / ".anthropic" / "api_key").read_text().strip()

SYSTEM = """You do a task for a user against GitHub, using its REST API, one request at a time, through the http
tool. Each call sends one request to https://api.github.com: give the method, the path (starting with /), any query
parameters, and, for a request that sends one, the JSON body. The answer is the status, the headers that matter
(ETag, Location, Link, rate limits) and the body, cut short past 20,000 characters, saying so. You are authenticated
already: never send credentials. Write only in the repository the task names.
Work through the task in order. Read what you need before you write. Each value the task asks for must be what it
says, exactly. Where the task says to wait for something, read again until it holds, a few seconds apart, within
reason. Where it says to make sure of something, read it and check it. When a request is refused, read why and
try another way; don't repeat a refused request unchanged. Never guess a value you can read.
When the task is done, answer in words, with no tool call, beginning "DONE:", and say what you did. If it can't be
done, answer beginning "STOPPED:", and say why. You have at most 90 requests."""

TOOL = {
    "name": "http",
    "description": "Send one request to GitHub's REST API at https://api.github.com and get its answer.",
    "input_schema": {
        "type": "object",
        "properties": {
            "method": {"type": "string", "enum": ["GET", "POST", "PATCH", "PUT", "DELETE"]},
            "path": {"type": "string", "description": "The path, starting with /, such as /repos/{owner}/{repo}"},
            "query": {"type": "object", "description": "Query parameters, by name"},
            "body": {"description": "The JSON body, for a request that sends one"},
        },
        "required": ["method", "path"],
    },
}


def refused(method, path, owner, repo, stamp):
    """Why the harness refuses a request before it is sent; None when it may go."""
    if not path.startswith("/") or "://" in path or ".." in path:
        return "a path starts with / and stays on api.github.com"
    if method == "GET":
        return None
    scratch = f"/repos/{owner}/{repo}/"
    if not path.startswith(scratch):
        return f"writes go to {scratch[:-1]} only"
    rest = path[len(scratch):]
    if method == "DELETE" and rest.startswith("git/refs/") and stamp not in rest:
        return "only a branch this run made may be deleted"
    if rest.startswith(("branches/main", "git/refs/heads/main", "settings", "hooks", "collaborators", "keys")):
        return "the repository's main branch and settings are not this task's"
    return None


def github(method, path, query, body):
    url = API + path + (("?" + urllib.parse.urlencode(query, doseq=True)) if query else "")
    request = urllib.request.Request(url, method=method,
                                     data=None if body is None else json.dumps(body).encode())
    request.add_header("Authorization", f"Bearer {GITHUB_TOKEN}")
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    if body is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=60) as answer:
            status, headers, text = answer.status, answer.headers, answer.read().decode()
    except urllib.error.HTTPError as failed:
        status, headers, text = failed.code, failed.headers, failed.read().decode()
    kept = {k: headers[k] for k in ("ETag", "Location", "Link", "X-RateLimit-Remaining") if headers.get(k)}
    return status, kept, text


def claude(messages):
    payload = {
        "model": MODEL, "max_tokens": 4096,
        "system": [{"type": "text", "text": SYSTEM, "cache_control": {"type": "ephemeral"}}],
        "tools": [dict(TOOL, cache_control={"type": "ephemeral"})],
        "messages": messages,
    }
    request = urllib.request.Request(MESSAGES, data=json.dumps(payload).encode(), method="POST")
    request.add_header("x-api-key", KEY)
    request.add_header("anthropic-version", "2023-06-01")
    request.add_header("Content-Type", "application/json")
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=300) as answer:
                return json.loads(answer.read().decode())
        except urllib.error.HTTPError as failed:
            if failed.code in (429, 500, 529) and attempt < 4:
                time.sleep(10 * (attempt + 1))
                continue
            raise SystemExit(f"Claude API answered {failed.code}: {failed.read().decode()[:300]}")


# A YAML emitter for the transcript (captures are YAML, bodies as YAML, long text as | blocks).
def yaml(value, indent=0):
    pad = "  " * indent
    if isinstance(value, dict):
        if not value:
            return " {}"
        out = ""
        for k, v in value.items():
            key = k if re.fullmatch(r"[A-Za-z_][\w-]*", str(k)) else json.dumps(str(k))
            out += f"\n{pad}{key}:" + yaml(v, indent + 1)
        return out
    if isinstance(value, list):
        if not value:
            return " []"
        return "".join(f"\n{pad}-" + yaml(v, indent + 1).replace("\n" + "  " * (indent + 1), "\n" + pad + "  ", 1)
                       if isinstance(v, dict) else f"\n{pad}-" + yaml(v, indent + 1) for v in value)
    if isinstance(value, str) and ("\n" in value or len(value) > 100):
        return " |-\n" + "\n".join(pad + line for line in value.splitlines())
    if isinstance(value, str):
        return " " + json.dumps(value) if value == "" or re.search(r"[:#{}\[\],&*?|>!%@`'\"]|^\s|\s$|^-", value) \
            or value.lower() in ("true", "false", "null", "yes", "no") or re.fullmatch(r"[\d.eE+-]+", value) \
            else " " + value
    return " " + json.dumps(value)


def body_of(text):
    try:
        return json.loads(text) if text else None
    except json.JSONDecodeError:
        return text


def main():
    if len(sys.argv) != 7:
        sys.exit(__doc__)
    task_file, owner, repo, label, stamp, out = sys.argv[1:]
    if owner != "JogoShugh" or repo != "heart-github-test":
        sys.exit("the scratch repository only: JogoShugh/heart-github-test")
    task = Path(task_file).read_text()
    for name, value in (("owner", owner), ("repo", repo), ("label", label), ("stamp", stamp)):
        task = task.replace("{" + name + "}", value)
    messages = [{"role": "user", "content": [{"type": "text", "text": task}]}]
    usage = {"calls": 0, "input": 0, "output": 0, "cache_read": 0, "cache_write": 0}
    steps, calls, refusals, began = [], 0, 0, time.time()
    ended, last_words = "out of requests", ""
    while True:
        if time.time() - began > MAX_SECONDS:
            ended = "out of time"
            break
        # The conversation so far is cached up to its last block, as ARTIE's prompts cache their fixed parts.
        for message in messages:
            for block in message["content"]:
                block.pop("cache_control", None)
        messages[-1]["content"][-1]["cache_control"] = {"type": "ephemeral"}
        answer = claude(messages)
        used = answer.get("usage", {})
        usage["calls"] += 1
        usage["input"] += used.get("input_tokens", 0)
        usage["output"] += used.get("output_tokens", 0)
        usage["cache_read"] += used.get("cache_read_input_tokens", 0)
        usage["cache_write"] += used.get("cache_creation_input_tokens", 0)
        content = answer["content"]
        messages.append({"role": "assistant", "content": content})
        words = " ".join(b["text"] for b in content if b["type"] == "text").strip()
        uses = [b for b in content if b["type"] == "tool_use"]
        if not uses:
            last_words = words
            ended = "done" if words.startswith("DONE:") else "stopped" if words.startswith("STOPPED:") else "answered"
            break
        results = []
        for use in uses:
            if calls >= MAX_CALLS:
                results.append({"type": "tool_result", "tool_use_id": use["id"], "is_error": True,
                                "content": "No requests are left."})
                continue
            calls += 1
            asked = use["input"]
            method, path = str(asked.get("method", "GET")).upper(), str(asked.get("path", ""))
            query, body = asked.get("query") or None, asked.get("body")
            started = time.time()
            why = refused(method, path, owner, repo, stamp)
            if why:
                refusals += 1
                status, headers, text = None, {}, f"refused by the harness, nothing sent: {why}"
            else:
                status, headers, text = github(method, path, query, body)
            told = text if len(text) <= BODY_LIMIT else text[:BODY_LIMIT] + \
                f"\n… cut short: {len(text)} characters in all."
            results.append({"type": "tool_result", "tool_use_id": use["id"],
                            "content": json.dumps({"status": status, "headers": headers}) + "\n" + told})
            steps.append({
                "n": calls,
                "at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "ms": int((time.time() - started) * 1000),
                "said": words,
                "request": {"method": method, "path": path, **({"query": query} if query else {}),
                            **({"body": body} if body is not None else {})},
                "response": {"status": status, "headers": headers,
                             "body": body_of(text) if len(text) <= BODY_LIMIT else told},
            })
            words = ""
        messages.append({"role": "user", "content": results})
        if calls >= MAX_CALLS:
            ended = "out of requests"
            break
    run = {"run": {
        "arm": "native", "model": MODEL, "stamp": stamp, "owner": owner, "repo": repo, "label": label,
        "ended": ended, "requests": calls, "refused_by_harness": refusals,
        "seconds": round(time.time() - began, 1), "usage": usage, "said_last": last_words,
    }}
    text = "".join("---" + yaml(doc) + "\n" for doc in [run] + steps)
    if GITHUB_TOKEN in text or KEY in text:
        sys.exit("a credential is in the transcript: nothing written")
    Path(out).write_text(text)
    print(json.dumps(run["run"], indent=2))


if __name__ == "__main__":
    main()
