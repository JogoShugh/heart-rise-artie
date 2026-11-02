"""
What one tool per endpoint costs up front on a large real API: FastMCP's tools for GitHub's REST description
(specs/github.json, from github/rest-api-description), counted by the model's own token counter, in batches.

  .venv/bin/python -I scale.py specs/github.json > runs/scale-github.json
"""
import asyncio
import json
import sys

import anthropic
import httpx2
from fastmcp import Client, FastMCP

MODEL = "claude-sonnet-5-5"
SYSTEM = "You work an API through the tools you are given."
BATCH = 40


async def tools_of(spec):
    server = FastMCP.from_openapi(spec, client=httpx2.AsyncClient(base_url="https://api.github.com"), name="github")
    async with Client(server) as client:
        return [{"name": t.name[:64], "description": t.description or "", "input_schema": t.input_schema}
                for t in await client.list_tools()]


def main():
    spec = json.load(open(sys.argv[1]))
    tools = asyncio.run(tools_of(spec))
    api = anthropic.Anthropic()
    message = [{"role": "user", "content": "x"}]
    base = api.messages.count_tokens(model=MODEL, system=SYSTEM, messages=message).input_tokens
    per_tool, total = [], 0
    for start in range(0, len(tools), BATCH):
        batch = tools[start:start + BATCH]
        counted = api.messages.count_tokens(model=MODEL, system=SYSTEM, tools=batch, messages=message).input_tokens
        total += counted - base
        per_tool.append((counted - base) / len(batch))
        print(f"{start + len(batch)}/{len(tools)}: {total}", file=sys.stderr, flush=True)
    sizes = sorted(len(json.dumps(t)) for t in tools)
    print(json.dumps({
        "spec": spec["info"]["title"], "version": spec["info"]["version"], "tools": len(tools),
        "tool_definition_tokens": total, "mean_tokens_per_tool": round(total / len(tools)),
        "largest_tool_chars": sizes[-1], "median_tool_chars": sizes[len(sizes) // 2], "model": MODEL,
    }, indent=2))


if __name__ == "__main__":
    main()
