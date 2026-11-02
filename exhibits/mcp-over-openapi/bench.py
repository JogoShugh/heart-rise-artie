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
import json
import os
import re
import subprocess
import sys
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

BASE = "http://localhost:8099"
TOKEN = os.environ["EMPLOYEES_TOKEN"]
AUTH = {"Authorization": f"Bearer {TOKEN}"}
PLAIN = {**AUTH, "Accept": "application/json"}
HERE = Path(__file__).parent
HEART_MCP = HERE / "../../mcp/heart-mcp-server/build/install/heart-mcp/bin/heart-mcp"
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
}
INTERFERED_ROLE = "keeper of the Red Book"


# ---- the service: reset, setup, its log, and what it holds after ----

def sim(method, path, **kwargs):
    return httpx.request(method, BASE + path, timeout=10, **kwargs)


def setup(task):
    sim("POST", "/_sim/reset").raise_for_status()
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


def arm_client(arm):
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
    if arm == "heart-mcp":
        env = {**os.environ, "JAVA_HOME": JAVA_22, "HEART_BOOKMARKS": f"employees={BASE}/",
               "ARTIE_TOKEN_LOCALHOST_8099": TOKEN}
        return Client(StdioTransport(str(HEART_MCP.resolve()), [], env=env, log_file=HERE / "runs/heart-mcp.log"))
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
    offset = len(log())
    interferer = Interferer(offset) if task == "t3" else None
    started, calls, tools_used, final_text, error = time.time(), [], [], None, None
    async with arm_client(arm) as client:
        tools = [as_tool(t) for t in await client.list_tools()]
        base = api.messages.count_tokens(model=model, system=SYSTEM, messages=[{"role": "user", "content": "x"}])
        with_tools = api.messages.count_tokens(model=model, system=SYSTEM, tools=tools,
                                               messages=[{"role": "user", "content": "x"}])
        if interferer:
            interferer.start()
        messages = [{"role": "user", "content": TASKS[task]["text"]}]
        cached_tools = cached(tools)
        for _ in range(max_turns):
            t0 = time.time()
            answer = api.messages.create(model=model, max_tokens=2048, system=SYSTEM, tools=cached_tools,
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
    requests = requests_since(offset)
    held = everyone()
    record = {
        "at": now(), "model": model, "arm": arm, "task": task, "seconds": round(time.time() - started, 1),
        "tools_offered": len(tools), "tool_names": [t["name"] for t in tools],
        "tool_definition_tokens": with_tools.input_tokens - base.input_tokens,
        **totals(calls), "calls": calls,
        "tool_calls": len(tools_used), "tool_errors": sum(t["error"] for t in tools_used), "tools": tools_used,
        "http": len(requests), "http_4xx": sum(400 <= r["status"] < 500 for r in requests), "requests": requests,
        "interfered": interferer.fired if interferer else None,
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


# ---- ARTIE, native: a goal run with the reasoner, or a script with no model ----

ARTIE = HERE / "../../client/artie-cli/build/install/artie/bin/artie"


def run_artie(model, arm, task):
    setup(task)
    offset = len(log())
    interferer = Interferer(offset) if task == "t3" else None
    if interferer:
        interferer.start()
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    trace = HERE / f"runs/artie/{arm}-{task}-{stamp}.yaml"
    trace.parent.mkdir(exist_ok=True)
    source = HERE / ("artie/" + (f"{task}.goal.md" if arm == "artie-goal" else f"{task}.kts"))
    command = [str(ARTIE.resolve()), "run", str(source), "--rung", "L2", "--trace", str(trace)]
    if arm == "artie-goal":
        command += ["--reasoner", "claude", "--model", model]
    env = {**os.environ, "ARTIE_TOKEN_LOCALHOST_8099": TOKEN}
    started = time.time()
    done = subprocess.run(command, env=env, capture_output=True, text=True, timeout=900)
    if interferer:
        interferer.stopped.set()
    output = (done.stdout + done.stderr).strip()
    run_doc = next(iter(yaml.safe_load_all(trace.read_text())), {}) if trace.exists() else {}
    usage = ((run_doc or {}).get("run") or {}).get("usage") or {}
    calls = [{"input": usage.get("input", 0), "output": usage.get("output", 0),
              "cache_read": usage.get("cache_read", 0), "cache_write": usage.get("cache_write", 0)}]
    requests = requests_since(offset)
    held = everyone()
    return {
        "at": now(), "model": model if arm == "artie-goal" else None, "arm": arm, "task": task,
        "seconds": round(time.time() - started, 1), "exit": done.returncode,
        "tools_offered": None, "tool_definition_tokens": None,
        **totals(calls), "model_calls": usage.get("calls", 0), "calls": None,
        "tool_calls": len(re.findall(r"^\d+ ", output, re.M)), "tool_errors": None, "tools": None,
        "http": len(requests), "http_4xx": sum(400 <= r["status"] < 500 for r in requests), "requests": requests,
        "interfered": interferer.fired if interferer else None,
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
    args = parser.parse_args()
    (HERE / "runs").mkdir(exist_ok=True)
    out = Path(args.out or HERE / f"runs/{datetime.now().strftime('%Y%m%d-%H%M%S')}.jsonl")
    api = anthropic.Anthropic()
    for rep in range(args.reps):
        for task in args.tasks.split(","):
            for arm in args.arms.split(","):
                if arm == "artie-script" and task == "t3":
                    continue  # a script reads and acts within milliseconds: no one can change Frodo in between
                try:
                    if arm.startswith("artie-"):
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
