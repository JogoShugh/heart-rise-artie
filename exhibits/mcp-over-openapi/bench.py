"""
MCP over OpenAPI, as others build it, beside heart-mcp: one agent loop, the same model, system prompt and task
for every arm, against the employees service in the heart-sims image (localhost:8099, reset before each run).

  .venv/bin/python -I bench.py --arms fastmcp,meta-tools,heart-mcp,artie-goal,artie-script --tasks t1,t2,t3 --reps 3

Each run is one line in runs/<stamp>.jsonl: tokens per model call (from the API's own usage), tool calls with
their inputs and the size of what came back, the HTTP requests the service received (from the simulator's log),
the service's state after, and whether the task was done. The token is read from the environment and written
nowhere; a record with it in is not written.
"""
import argparse
import asyncio
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
from datetime import datetime, timezone
from pathlib import Path

import anthropic
import httpx
import httpx2
import yaml
from fastmcp import Client, FastMCP
from fastmcp.client.transports import StdioTransport

BASE = os.environ.get("BENCH_BASE", "http://localhost:8099")
TOKEN_VARIABLE = f"ARTIE_TOKEN_LOCALHOST_{BASE.rsplit(':', 1)[1]}"
TOKEN = os.environ.get("BENCH_TOKEN") or os.environ["EMPLOYEES_TOKEN"]
AUTH = {"Authorization": f"Bearer {TOKEN}"}
PLAIN = {**AUTH, "Accept": "application/json"}
HERE = Path(__file__).parent
HEART_MCP = Path(os.environ.get("HEART_MCP_BIN", HERE / "../../mcp/heart-mcp-server/build/install/heart-mcp/bin/heart-mcp"))
OPENAPI_MCP = "@ivotoby/openapi-mcp-server@1.16.1"
JAVA_22 = "/Users/josh/Library/Java/JavaVirtualMachines/openjdk-22.0.1/Contents/Home"

SYSTEM = (
    "You work an employee records service through the tools you are given. Do the task you are asked, using "
    "only the tools. The person who asked is away, so do not ask questions. When you are done, say what happened "
    "in one or two sentences."
)

TASKS = {
    "t1": {
        "name": "hire, promote, dismiss",
        "text": 'Hire Samwise Gamgee as a gardener. Then change Samwise\'s role to "gardener, mayor". '
                "Then dismiss Bilbo Baggins.",
    },
    "t2": {
        "name": "a dismissed employee",
        "text": 'Change Frodo Baggins\'s role to "ring bearer, retired".',
    },
    "t3": {
        "name": "someone else changes him meanwhile",
        "text": 'Frodo Baggins is retiring: add ", retired" to the end of his current role, keeping the rest of '
                "it as it is.",
    },
    "t4": {
        "name": "someone else changes him, words that set",
        "text": 'Change Frodo Baggins\'s role to "ring bearer, retired".',
    },
}
INTERFERED_ROLE = "keeper of the Red Book"
INTERFERED = {"t3", "t4"}

# ---- GitHub through the owner's gateway (exhibits/own-controls): heart-sims' small GitHub, the gateway in front ----
# BENCH_BASE=http://localhost:8098 BENCH_TOKEN=github-token bench.py --arms fastmcp-github --tasks g1 --dam conditional --meddle

GITHUB_SYSTEM = (
    "You work a GitHub repository's issues through the tools you are given. Do the task you are asked, using only "
    "the tools. The person who asked is away, so do not ask questions. When you are done, say what happened in one "
    "or two sentences."
)
TASKS["g1"] = {
    "name": "triage an issue a teammate is closing",
    "text": 'In the repository acme/web, issue 7 has been triaged: add " (triaged)" to the end of its current '
            "title, keeping the rest of it as it is, then close it as completed.",
}
# The teammate, on GitHub itself, past the gateway, right after the client first reads issue 7 (as ladder.sh).
TEAMMATE = {"issue": 7, "title": "The nightly build is slow on Mondays", "state": "closed",
            "state_reason": "not_planned"}
PASS = {"X-Gateway-Mode": "pass-through"}
DAM = {"rungs": ""}
# The issue operations of GitHub's own description, as a host would pick them for an issues task.
GITHUB_PATHS = ["/repos/{owner}/{repo}/issues", "/repos/{owner}/{repo}/issues/{issue_number}",
                "/repos/{owner}/{repo}/issues/{issue_number}/comments"]


def github(task):
    return task.startswith("g")


# ---- the service: reset, setup, its log, and what it holds after ----

def sim(method, path, **kwargs):
    return httpx.request(method, BASE + path, timeout=10, **kwargs)


MEDDLE = {"on": False}
SEQUENCE = {"on": False}


def interfere(task, offset):
    """Someone else changes Frodo once the client has read him: by the sim, right after the read (certain, for any
    client), or by a thread that watches the log (a model's pause lets it in; a script's milliseconds may not)."""
    if task not in INTERFERED or github(task) or (SEQUENCE["on"] and not MEDDLE["on"]):
        return None  # in --sequence, a run with no teammate has none
    if MEDDLE["on"]:
        sim("PUT", "/_sim/employees/meddle", json={"employee": 1, "role": INTERFERED_ROLE}).raise_for_status()
        return None
    return Interferer(offset)


def setup(task):
    sim("POST", "/_sim/reset").raise_for_status()
    if github(task):
        sim("PUT", "/_sim/github/dam", content=DAM["rungs"]).raise_for_status()
        if MEDDLE["on"]:
            sim("PUT", "/_sim/github/meddle", json=TEAMMATE).raise_for_status()
    if task == "t2":
        sim("DELETE", "/employees/1/actions/dismiss", headers=PLAIN).raise_for_status()


def log():
    return sim("GET", "/_sim/log").json()


def employee(number):
    answer = sim("GET", f"/employees/{number}", headers=PLAIN)
    return answer.json() if answer.status_code == 200 else None


def everyone():
    found, number = [], 1
    while (one := employee(number)) is not None:
        found.append(one)
        number += 1
    return found


def check(task, held, final_text):
    if github(task):
        issue = sim("GET", "/repos/acme/web/issues/7", headers={**PLAIN, **PASS}).json()
        title_kept = issue["title"].startswith(TEAMMATE["title"])
        reason_kept = issue.get("state_reason") == TEAMMATE["state_reason"]
        triaged = issue["title"].endswith("(triaged)")
        said = bool(re.search(r"not.planned|already closed|teammate|someone|changed|412|428|409", final_text or "", re.I))
        return {"done": title_kept and reason_kept and triaged, "title": issue["title"], "state": issue["state"],
                "state_reason": issue.get("state_reason"), "teammate_title_kept": title_kept,
                "teammate_reason_kept": reason_kept, "triaged": triaged, "said_why": said}
    by_name = {}
    for one in held:
        by_name.setdefault(f"{one['firstName']} {one['lastName']}", []).append(one)
    working = sorted((o["firstName"], o["role"]) for o in held if o["status"] == "working")
    if task == "t1":
        done = working == [("Frodo", "ring bearer"), ("Samwise", "gardener, mayor")] and len(held) == 3
        return {"done": done, "working": working, "employees": len(held)}
    if task == "t2":
        frodo = by_name["Frodo Baggins"][0]
        untouched = frodo["role"] == "ring bearer" and frodo["status"] == "dismissed" and len(held) == 2
        said = bool(re.search(r"dismiss", final_text or "", re.I))
        return {"done": untouched and said, "untouched": untouched, "said_why": said, "working": working,
                "employees": len(held)}
    frodo = by_name["Frodo Baggins"][0]
    role = frodo["role"]
    if task == "t4":
        # Words that set a value, met by someone else's change: right is never to write over it unseen. Stopping
        # and saying so, or writing it and saying whose change it replaced, are both open; done means said.
        # Said: the other value named, or the change said as ARTIE's guard says it, by the member.
        mentioned = bool(re.search(f"{INTERFERED_ROLE}|someone else changed role", final_text or "", re.I))
        overwrote = role == "ring bearer, retired"
        return {"done": mentioned, "role": role, "overwrote": overwrote, "mentioned": mentioned,
                "silent_overwrite": overwrote and not mentioned, "employees": len(held)}
    return {"done": role == f"{INTERFERED_ROLE}, retired", "role": role,
            "lost_update": role == "ring bearer, retired", "employees": len(held)}


class Interferer(threading.Thread):
    """For t3: once the client has read Frodo, someone else changes his role, as a person at another desk would."""

    def __init__(self, offset):
        super().__init__(daemon=True)
        self.offset, self.fired, self.stopped = offset, None, threading.Event()

    def run(self):
        while not self.stopped.is_set():
            for entry in log()[self.offset:]:
                path = entry["url"].split("?")[0]
                if entry["method"] == "GET" and path == "/employees/1" and "X-Interferer" not in entry["headers"]:
                    body = {"firstName": "Frodo", "lastName": "Baggins", "role": INTERFERED_ROLE}
                    answer = sim("PUT", "/employees/1/actions/update", json=body, headers={**PLAIN, "X-Interferer": "1"})
                    self.fired = {"at": now(), "status": answer.status_code}
                    return
            time.sleep(0.05)


CHANGE = {"names": "", "when": "start"}
ALL_CHANGES = "moved,renamed,required,method,notice"


class ChangeUnder(threading.Thread):
    """
    The service changes under the client (PUT /_sim/employees/changes): at the start, once the client is ready
    (an MCP server has built its tools from the description as it was), or midway, once the client's first act
    has been answered.
    """

    def __init__(self, offset):
        super().__init__(daemon=True)
        self.offset, self.fired, self.stopped = offset, None, threading.Event()

    def apply(self):
        sim("PUT", "/_sim/employees/changes", content=CHANGE["names"]).raise_for_status()
        self.fired = now()

    def run(self):
        while not self.stopped.is_set():
            for entry in log()[self.offset:]:
                acted = entry["method"] in ("POST", "PUT", "PATCH", "DELETE") and 200 <= entry["status"] < 300
                if acted and "X-Interferer" not in entry["headers"]:
                    self.apply()
                    return
            time.sleep(0.05)


def change_under(offset):
    if not CHANGE["names"]:
        return None
    change = ChangeUnder(offset)
    if CHANGE["when"] in ("start", "before"):
        change.apply()
    else:
        change.start()
    return change


def requests_since(offset):
    seen = [e for e in log()[offset:] if "X-Interferer" not in e["headers"] and e["url"] != "/openapi.json"]
    return [{"method": e["method"], "url": e["url"], "status": e["status"],
             "accept": e["headers"].get("Accept"), "if_match": e["headers"].get("If-Match")} for e in seen]


# ---- the arms: an MCP client for each ----

def openapi_spec():
    return httpx.get(BASE + "/openapi.json", headers=AUTH, timeout=10).json()


def per_endpoint(spec=None):
    http = httpx2.AsyncClient(base_url=BASE, headers=PLAIN, timeout=30)
    return FastMCP.from_openapi(spec or openapi_spec(), client=http, name="employees")


def typical_spec():
    """
    The twin as a typical hand-written spec would be: each operation described by its summary alone ("Update the
    employee"), without the sentences heart-core writes about the state an act needs ("Only allowed when status is
    working"). The endpoints, schemas and answers are the same.
    """
    spec = openapi_spec()
    for path in spec["paths"].values():
        for operation in path.values():
            if isinstance(operation, dict) and "operationId" in operation:
                operation["description"] = operation.get("summary", "")
    return spec


def meta_tools():
    """
    The meta-tools pattern, as Stainless's dynamic mode describes it (list the endpoints, get one's schema, invoke
    it), built here over FastMCP's own per-endpoint tools, so the endpoints, their schemas and their calls are the
    same as the per-endpoint arm's; only what the model is handed differs.
    """
    inner = per_endpoint()
    outer = FastMCP("employees-dynamic")

    async def endpoints():
        async with Client(inner) as client:
            return await client.list_tools()

    @outer.tool
    async def list_api_endpoints(search: str = "") -> list[dict]:
        """List the API's endpoints, each with its name and description. With search, only those whose name or
        description contains it. Use get_api_endpoint_schema for an endpoint's input, then invoke_api_endpoint."""
        found = [{"name": t.name, "description": t.description} for t in await endpoints()]
        return [e for e in found if search.lower() in (e["name"] + " " + (e["description"] or "")).lower()]

    @outer.tool
    async def get_api_endpoint_schema(endpoint: str) -> dict:
        """The JSON Schema of an endpoint's input, by its name from list_api_endpoints."""
        for tool in await endpoints():
            if tool.name == endpoint:
                return {"name": tool.name, "description": tool.description, "input_schema": tool.input_schema}
        return {"error": f"no endpoint {endpoint}"}

    @outer.tool
    async def invoke_api_endpoint(endpoint: str, args: dict) -> str:
        """Call an endpoint by its name, with args that match its input schema. Returns the API's answer."""
        async with Client(inner) as client:
            return result_text(await client.call_tool_mcp(endpoint, args))

    return outer


def github_tools():
    """FastMCP's per-endpoint tools for GitHub's issue operations, from GitHub's own description, through the gateway."""
    spec = json.loads((HERE / "specs/github.json").read_text())
    spec["paths"] = {path: spec["paths"][path] for path in GITHUB_PATHS}
    http = httpx2.AsyncClient(base_url=BASE, headers={**PLAIN, **PASS}, timeout=30)
    return FastMCP.from_openapi(spec, client=http, name="github")


def arm_client(arm):
    if arm == "fastmcp-github":
        return Client(github_tools())
    if arm == "fastmcp":
        return Client(per_endpoint())
    if arm == "fastmcp-typical":
        return Client(per_endpoint(typical_spec()))
    if arm == "meta-tools":
        return Client(meta_tools())
    if arm.startswith("openapi-mcp-"):
        env = {**os.environ, "API_BASE_URL": BASE, "OPENAPI_SPEC_PATH": f"{BASE}/openapi.json",
               "API_HEADERS": f"Authorization:Bearer {TOKEN},Accept:application/json",
               "TOOLS_MODE": arm.removeprefix("openapi-mcp-"), "VERBOSE": "false"}
        return Client(StdioTransport("npx", ["-y", OPENAPI_MCP], env=env, log_file=HERE / "runs/openapi-mcp.log"))
    if arm in ("heart-mcp", "heart-mcp-lean"):
        # heart-mcp as the benchmark first met it (HEART_MCP_BIN, a frozen copy), or as built now: each act's
        # schema shown once, no hr: members in the state (mcp-lean.feature).
        binary = HEART_MCP if arm == "heart-mcp" else HERE / "../../mcp/heart-mcp-server/build/install/heart-mcp/bin/heart-mcp"
        env = {**os.environ, "JAVA_HOME": JAVA_22, "HEART_BOOKMARKS": f"employees={BASE}/", TOKEN_VARIABLE: TOKEN}
        return Client(StdioTransport(str(binary.resolve()), [], env=env, log_file=HERE / f"runs/{arm}.log"))
    raise SystemExit(f"no arm {arm}")


def as_tool(tool):
    return {"name": tool.name, "description": tool.description or "", "input_schema": tool.input_schema}


def result_text(result):
    parts = [getattr(c, "text", None) or json.dumps(getattr(c, "model_dump", lambda: str(c))()) for c in result.content]
    text = "\n".join(parts)
    if not text and getattr(result, "structured_content", None):
        text = json.dumps(result.structured_content)
    return text


def now():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


# ---- one run ----

async def run(model, arm, task, max_turns, api):
    setup(task)
    system = GITHUB_SYSTEM if github(task) else SYSTEM
    offset = len(log())
    interferer = interfere(task, offset)
    started, calls, tools_used, final_text, error = time.time(), [], [], None, None
    if CHANGE["names"] and CHANGE["when"] == "before":
        # The control: changed before the MCP server builds its tools, as after a restart following a release.
        sim("PUT", "/_sim/employees/changes", content=CHANGE["names"]).raise_for_status()
    async with arm_client(arm) as client:
        tools = [as_tool(t) for t in await client.list_tools()]
        change = change_under(offset)
        base = api.messages.count_tokens(model=model, system=system, messages=[{"role": "user", "content": "x"}])
        with_tools = api.messages.count_tokens(model=model, system=system, tools=tools,
                                               messages=[{"role": "user", "content": "x"}])
        if interferer:
            interferer.start()
        messages = [{"role": "user", "content": TASKS[task]["text"]}]
        cached_tools = cached(tools)
        for _ in range(max_turns):
            t0 = time.time()
            answer = api.messages.create(model=model, max_tokens=2048, system=system, tools=cached_tools,
                                         messages=marked(messages))
            calls.append({**usage_of(answer.usage), "ms": int((time.time() - t0) * 1000), "stop": answer.stop_reason})
            messages.append({"role": "assistant", "content": [b.model_dump(exclude_none=True) for b in answer.content]})
            uses = [b for b in answer.content if b.type == "tool_use"]
            if not uses:
                final_text = "\n".join(b.text for b in answer.content if b.type == "text")
                break
            results = []
            for use in uses:
                t1 = time.time()
                try:
                    outcome = await client.call_tool_mcp(use.name, use.input)
                    text, is_error = result_text(outcome), bool(outcome.is_error)
                except Exception as broken:  # a tool that throws is answered as an error, as hosts do
                    text, is_error = f"error: {broken}", True
                tools_used.append({"tool": use.name, "input": use.input, "error": is_error, "chars": len(text),
                                   "ms": int((time.time() - t1) * 1000), "result": text[:4000]})
                results.append({"type": "tool_result", "tool_use_id": use.id, "content": text or "(empty)",
                                "is_error": is_error})
            messages.append({"role": "user", "content": results})
        else:
            error = f"stopped after {max_turns} turns"
    if interferer:
        interferer.stopped.set()
    if change:
        change.stopped.set()
    requests = requests_since(offset)
    held = everyone()
    record = {
        "dam": DAM["rungs"] if github(task) else None,
        "change": CHANGE["names"] or None, "when": CHANGE["when"] if CHANGE["names"] else None,
        "changed_at": change.fired if change else None,
        "at": now(), "model": model, "arm": arm, "task": task, "seconds": round(time.time() - started, 1),
        "tools_offered": len(tools), "tool_names": [t["name"] for t in tools],
        "tool_definition_tokens": with_tools.input_tokens - base.input_tokens,
        **totals(calls), "calls": calls,
        "tool_calls": len(tools_used), "tool_errors": sum(t["error"] for t in tools_used), "tools": tools_used,
        "http": len(requests), "http_4xx": sum(400 <= r["status"] < 500 for r in requests), "requests": requests,
        "interfered": interferer.fired if interferer else ({"by": "meddle"} if MEDDLE["on"] and (task in INTERFERED or github(task)) else None),
        "check": check(task, held, final_text), "final_text": final_text, "error": error,
    }
    return record


# ---- prompt caching, as a host does it: the tools, and each turn's last block ----

def cached(tools):
    return tools[:-1] + [{**tools[-1], "cache_control": {"type": "ephemeral"}}] if tools else tools


def marked(messages):
    """The messages with the last block of the last one marked for the cache; the kept messages stay unmarked."""
    last = messages[-1]
    content = last["content"]
    blocks = [{"type": "text", "text": content}] if isinstance(content, str) else [dict(b) for b in content]
    blocks[-1] = {**blocks[-1], "cache_control": {"type": "ephemeral"}}
    return messages[:-1] + [{**last, "content": blocks}]


def usage_of(usage):
    return {"input": usage.input_tokens, "output": usage.output_tokens,
            "cache_read": getattr(usage, "cache_read_input_tokens", None) or 0,
            "cache_write": getattr(usage, "cache_creation_input_tokens", None) or 0}


def totals(calls):
    """Input as billed (uncached), what the cache read and wrote, and every input token the model took in."""
    input_, read, write = (sum(c[k] for c in calls) for k in ("input", "cache_read", "cache_write"))
    return {"model_calls": len(calls), "input_tokens": input_, "output_tokens": sum(c["output"] for c in calls),
            "cache_read": read, "cache_write": write, "input_processed": input_ + read + write}


# ---- code mode (spec/PLAN-CODE-MODE-VS-HEART.md): the model writes a program, a sandbox runs it ----
# As Anthropic's "Code execution with MCP" and Cloudflare's Code Mode do it, steelmanned: one function per operation
# of the service's OpenAPI description, built from it at the start of each run (so a release is seen), each
# answering its status, ETag and body, and taking If-Match where the description says a write may; a sandbox with
# no network but the service, read-only, with a time, memory and process limit.

SANDBOX_NET = os.environ.get("BENCH_SANDBOX_NET", "heart-bench")
SANDBOX_SERVICE = os.environ.get("BENCH_SANDBOX_SERVICE", "http://heart-sims-tt:8099")
SANDBOX_IMAGE = "python:3.12-slim"
SAVED = HERE / "runs/codemode"

CODE_SYSTEM = (
    "You work an employee records service by writing Python programs. Your one tool, run_program, runs a program in "
    "a sandbox; `import api` there gives one function per operation of the service, listed below. Each returns a "
    "dict: status, etag (the ETag header, or None) and body. Print what you need to see. Do the task you are asked, "
    "using only programs. The person who asked is away, so do not ask questions. When you are done, say what "
    "happened in one or two sentences.\n\nThe api module:\n\n"
)

RUN_PROGRAM = {
    "name": "run_program",
    "description": "Run a Python 3.12 program in the sandbox, with `import api` available. Returns its exit code, "
                   "its standard output and its standard error.",
    "input_schema": {"type": "object", "properties": {"code": {"type": "string", "description": "The program"}},
                     "required": ["code"]},
}


def api_library(spec):
    """The api module's source, and its documentation for the model: one function per operation of [spec]."""
    source = [
        "import json, os, urllib.error, urllib.parse, urllib.request",
        "BASE, TOKEN = os.environ['API_BASE'], os.environ['API_TOKEN']",
        "def _call(method, path, query=None, body=None, if_match=None):",
        "    query = {k: v for k, v in (query or {}).items() if v is not None}",
        "    url = BASE + path + ('?' + urllib.parse.urlencode(query) if query else '')",
        "    headers = {'Authorization': 'Bearer ' + TOKEN, 'Accept': 'application/json'}",
        "    data = None",
        "    if body is not None:",
        "        data, headers['Content-Type'] = json.dumps(body).encode(), 'application/json'",
        "    if if_match:",
        "        headers['If-Match'] = if_match",
        "    request = urllib.request.Request(url, data=data, method=method, headers=headers)",
        "    try:",
        "        with urllib.request.urlopen(request, timeout=20) as r:",
        "            status, etag, text = r.status, r.headers.get('ETag'), r.read().decode()",
        "    except urllib.error.HTTPError as e:",
        "        status, etag, text = e.code, e.headers.get('ETag'), e.read().decode()",
        "    try:",
        "        parsed = json.loads(text) if text else None",
        "    except ValueError:",
        "        parsed = text",
        "    return {'status': status, 'etag': etag, 'body': parsed}",
    ]
    docs = []
    for path, operations in spec["paths"].items():
        for method, operation in operations.items():
            if not isinstance(operation, dict) or "operationId" not in operation:
                continue
            name = re.sub(r"\W", "_", operation["operationId"])
            parameters = operation.get("parameters", [])
            in_path = [p["name"] for p in parameters if p.get("in") == "path"]
            in_query = [p["name"] for p in parameters if p.get("in") == "query"]
            conditional = any(p.get("name") == "If-Match" for p in parameters)
            takes_body = "requestBody" in operation
            arguments = in_path + (["body"] if takes_body else []) + [f"{q}=None" for q in in_query] + \
                (["if_match=None"] if conditional else [])
            pythonic = re.sub(r"\{(\w+)\}", r"{\1}", path)
            query = "{" + ", ".join(f"'{q}': {q}" for q in in_query) + "}" if in_query else "None"
            source.append(f"def {name}({', '.join(arguments)}):")
            source.append(f"    return _call('{method.upper()}', f'{pythonic}', {query}, "
                          f"{'body' if takes_body else 'None'}, {'if_match' if conditional else 'None'})")
            body = ""
            if takes_body:
                schema = operation["requestBody"]["content"]["application/json"]["schema"]
                if "$ref" in schema:
                    schema = spec["components"]["schemas"][schema["$ref"].rsplit("/", 1)[1]]
                body = f"\n    body: {json.dumps(schema.get('properties', {}))}, required {schema.get('required', [])}"
            prose = (operation.get("summary", "") + ". " + operation.get("description", "")).strip()
            conditional_doc = "\n    if_match: the etag you read; with it, refused 412 if the state moved since" \
                if conditional else ""
            docs.append(f"api.{name}({', '.join(arguments)})  # {method.upper()} {path}\n    {prose}{body}"
                        f"{conditional_doc}")
    return "\n".join(source) + "\n", "\n\n".join(docs)


def sandboxed(program, library):
    """[program] run in the sandbox beside the api module: its exit code, output and errors."""
    runs = HERE / "runs"
    runs.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=runs) as folder:
        Path(folder, "program.py").write_text(program)
        Path(folder, "api.py").write_text(library)
        command = ["docker", "run", "--rm", "--network", SANDBOX_NET, "--read-only", "--cpus", "1",
                   "--memory", "256m", "--pids-limit", "64", "-e", f"API_BASE={SANDBOX_SERVICE}",
                   "-e", f"API_TOKEN={TOKEN}", "-v", f"{folder}:/code:ro", "-w", "/code", SANDBOX_IMAGE,
                   "python", "program.py"]
        try:
            done = subprocess.run(command, capture_output=True, text=True, timeout=120)
            return done.returncode, done.stdout[-4000:], done.stderr[-2000:]
        except subprocess.TimeoutExpired:
            return 124, "", "the program ran past its time limit"


def program_turns(model, api, task, library, docs, opening, max_turns):
    """The model writing programs until it says it is done: its calls, the programs it ran, its last word."""
    messages = [{"role": "user", "content": opening}]
    tools = cached([RUN_PROGRAM])
    calls, programs, final_text, finished = [], [], None, None
    for _ in range(max_turns):
        t0 = time.time()
        answer = api.messages.create(model=model, max_tokens=4096, system=CODE_SYSTEM + docs, tools=tools,
                                     messages=marked(messages))
        calls.append({**usage_of(answer.usage), "ms": int((time.time() - t0) * 1000), "stop": answer.stop_reason})
        messages.append({"role": "assistant", "content": [b.model_dump(exclude_none=True) for b in answer.content]})
        uses = [b for b in answer.content if b.type == "tool_use"]
        if not uses:
            final_text = "\n".join(b.text for b in answer.content if b.type == "text")
            break
        results = []
        for use in uses:
            code = use.input.get("code", "")
            exit_, out, err = sandboxed(code, library)
            programs.append({"sha": hashlib.sha256(code.encode()).hexdigest()[:12], "exit": exit_,
                             "lines": code.count("\n") + 1})
            if exit_ == 0:
                finished = code
            results.append({"type": "tool_result", "tool_use_id": use.id, "is_error": exit_ != 0,
                            "content": f"exit {exit_}\nstdout:\n{out or '(none)'}\nstderr:\n{err or '(none)'}"})
        messages.append({"role": "user", "content": results})
    return calls, programs, final_text, finished


def run_code(model, arm, task, max_turns, api):
    """code-fresh: a new program each run. code-saved: the first that finished, run again; written anew on failure."""
    setup(task)
    offset = len(log())
    interferer = interfere(task, offset)
    if interferer:
        interferer.start()
    change = change_under(offset)
    started = time.time()
    library, docs = api_library(openapi_spec())
    saved = SAVED / f"{task}.py"
    calls, programs, final_text, finished, replayed = [], [], None, None, None
    if arm == "code-saved" and saved.exists():
        exit_, out, err = sandboxed(saved.read_text(), library)
        replayed = {"exit": exit_, "stdout": out[-1500:], "stderr": err[-800:]}
        programs.append({"sha": hashlib.sha256(saved.read_bytes()).hexdigest()[:12], "exit": exit_, "saved": True})
        if exit_ == 0:
            final_text = out[-1500:]
    if final_text is None:
        opening = TASKS[task]["text"]
        if replayed:
            opening += (f"\n\nA program kept from an earlier run of this task failed this time (exit "
                        f"{replayed['exit']}):\n{replayed['stderr'] or replayed['stdout']}")
        calls, programs_written, final_text, finished = program_turns(model, api, task, library, docs, opening,
                                                                      max_turns)
        programs += programs_written
    if arm == "code-saved" and finished:
        SAVED.mkdir(parents=True, exist_ok=True)
        saved.write_text(finished)
    if interferer:
        interferer.stopped.set()
    if change:
        change.stopped.set()
    requests = requests_since(offset)
    held = everyone()
    return {
        "change": CHANGE["names"] or None, "when": CHANGE["when"] if CHANGE["names"] else None,
        "changed_at": change.fired if change else None,
        "at": now(), "model": model if calls else None, "arm": arm, "task": task,
        "seconds": round(time.time() - started, 1), "tools_offered": 1, "tool_definition_tokens": None,
        **totals(calls), "calls": calls, "programs": programs, "replayed": replayed,
        "tool_calls": len(programs), "tool_errors": sum(p["exit"] != 0 for p in programs), "tools": None,
        "http": len(requests), "http_4xx": sum(400 <= r["status"] < 500 for r in requests), "requests": requests,
        "interfered": interferer.fired if interferer else ({"by": "meddle"} if MEDDLE["on"] and task in INTERFERED else None),
        "check": check(task, held, final_text), "final_text": final_text, "error": None,
    }


# ---- ARTIE, native: a goal run with the reasoner, or a script with no model ----

ARTIE = HERE / "../../client/artie-cli/build/install/artie/bin/artie"
# A goal run plans from the words; an intent run is the goal's steps as act(intent = …), each chosen from what is
# offered then; a kept script is artie generate's script from a clean goal run, held to its pins, with no model.
ARTIE_SOURCES = {"artie-goal": "{task}.goal.md", "artie-intent": "{task}.intent.kts", "artie-kept": "{task}.kept.kts",
                 "artie-script": "{task}.kts", "artie-data": "{task}.kept.heart.yaml"}
# artie-data, the kept script held as data, is given the reasoner only to fall back to its words when a form changed
# (its config says on-change: intent); it calls the model on no other step.
ARTIE_REASONING = {"artie-goal", "artie-intent", "artie-data"}


def run_artie(model, arm, task):
    setup(task)
    offset = len(log())
    interferer = interfere(task, offset)
    if interferer:
        interferer.start()
    change = change_under(offset)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    trace = HERE / f"runs/artie/{arm}-{task}-{stamp}.yaml"
    trace.parent.mkdir(exist_ok=True)
    source = HERE / "artie" / ARTIE_SOURCES[arm].format(task=task)
    command = [str(ARTIE.resolve()), "run", str(source), "--rung", "L2", "--trace", str(trace)]
    if arm in ARTIE_REASONING:
        command += ["--reasoner", "claude", "--model", model]
    env = {**os.environ, TOKEN_VARIABLE: TOKEN}
    started = time.time()
    done = subprocess.run(command, env=env, capture_output=True, text=True, timeout=900)
    if interferer:
        interferer.stopped.set()
    if change:
        change.stopped.set()
    output = (done.stdout + done.stderr).strip()
    run_doc = next(iter(yaml.safe_load_all(trace.read_text())), {}) if trace.exists() else {}
    usage = ((run_doc or {}).get("run") or {}).get("usage") or {}
    calls = [{"input": usage.get("input", 0), "output": usage.get("output", 0),
              "cache_read": usage.get("cache_read", 0), "cache_write": usage.get("cache_write", 0)}]
    requests = requests_since(offset)
    held = everyone()
    return {
        "change": CHANGE["names"] or None, "when": CHANGE["when"] if CHANGE["names"] else None,
        "changed_at": change.fired if change else None,
        "at": now(), "model": model if arm in ARTIE_REASONING else None, "arm": arm, "task": task,
        "seconds": round(time.time() - started, 1), "exit": done.returncode,
        "tools_offered": None, "tool_definition_tokens": None,
        **totals(calls), "model_calls": usage.get("calls", 0), "calls": None,
        "tool_calls": len(re.findall(r"^\d+ ", output, re.M)), "tool_errors": None, "tools": None,
        "http": len(requests), "http_4xx": sum(400 <= r["status"] < 500 for r in requests), "requests": requests,
        "interfered": interferer.fired if interferer else ({"by": "meddle"} if MEDDLE["on"] and (task in INTERFERED or github(task)) else None),
        "check": check(task, held, output), "final_text": output[-3000:], "trace": str(trace.relative_to(HERE)),
        "error": None if done.returncode == 0 else f"exit {done.returncode}",
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--arms", default="fastmcp,fastmcp-typical,meta-tools,heart-mcp,artie-goal,artie-script")
    parser.add_argument("--tasks", default="t1,t2,t3")
    parser.add_argument("--reps", type=int, default=3)
    parser.add_argument("--model", default="claude-sonnet-5-5")
    parser.add_argument("--max-turns", type=int, default=25)
    parser.add_argument("--out")
    parser.add_argument("--changes", default="", help=f"changes to make under the client; all is {ALL_CHANGES}")
    parser.add_argument("--when", default="start", choices=["before", "start", "midway"])
    parser.add_argument("--meddle", action="store_true", help="the sim changes Frodo right after the client reads him")
    parser.add_argument("--dam", default="", help="the GitHub gateway owner's rungs (GATEWAY_DAM), for g tasks")
    parser.add_argument("--sequence", action="store_true",
                        help="spec/PLAN-CODE-MODE-VS-HEART.md: runs 1-3 as they are, 4-6 with a teammate's change "
                             "(meddle), 7 on with a field renamed")
    args = parser.parse_args()
    CHANGE["names"] = ALL_CHANGES if args.changes == "all" else args.changes
    CHANGE["when"] = args.when
    MEDDLE["on"] = args.meddle
    SEQUENCE["on"] = args.sequence
    DAM["rungs"] = args.dam
    (HERE / "runs").mkdir(exist_ok=True)
    out = Path(args.out or HERE / f"runs/{datetime.now().strftime('%Y%m%d-%H%M%S')}.jsonl")
    api = anthropic.Anthropic()
    for rep in range(args.reps):
        if args.sequence:
            MEDDLE["on"] = 3 <= rep <= 5
            CHANGE["names"], CHANGE["when"] = ("renamed", "start") if rep >= 6 else ("", "start")
        for task in args.tasks.split(","):
            for arm in args.arms.split(","):
                if arm in ("artie-script", "artie-kept") and task in INTERFERED and not args.meddle:
                    continue  # a script reads and acts within milliseconds: no one can change Frodo in between
                if arm == "artie-kept" and not (HERE / "artie" / f"{task}.kept.kts").exists():
                    continue  # kept only from a goal run that acted: task 2's acted on nothing
                try:
                    if arm.startswith("code-"):
                        record = run_code(args.model, arm, task, min(args.max_turns, 8), api)
                    elif arm.startswith("artie-"):
                        record = run_artie(args.model, arm, task)
                    else:
                        record = asyncio.run(run(args.model, arm, task, args.max_turns, api))
                except Exception as broken:
                    record = {"at": now(), "model": args.model, "arm": arm, "task": task, "failed": repr(broken)}
                record["rep"] = rep + 1
                line = json.dumps(record)
                if TOKEN in line or re.search(r"sk-ant", line):
                    raise SystemExit("a secret is in the record; not written")
                with out.open("a") as file:
                    file.write(line + "\n")
                check_ = record.get("check", {})
                print(f"{rep + 1} {task} {arm:14} done={check_.get('done')} in={record.get('input_processed')} "
                      f"out={record.get('output_tokens')} calls={record.get('model_calls')} tools={record.get('tool_calls')} "
                      f"http={record.get('http')} 4xx={record.get('http_4xx')} {record.get('failed', '')}", flush=True)
    print(f"written: {out}")


if __name__ == "__main__":
    sys.exit(main())
