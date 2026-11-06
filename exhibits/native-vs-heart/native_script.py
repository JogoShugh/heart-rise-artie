#!/usr/bin/env python3
"""The native arm's way to make the task repeatable (spec/PLAN-NATIVE-VS-HEART-TRIAL.md): the model is given its
own successful run's transcript and the task, and asked for a script that does the task again with no model.
One call; its tokens are counted as the native arm's.

    native_script.py TASK.md TRANSCRIPT.yaml OUT.py

The script it writes takes OWNER REPO LABEL STAMP as arguments and the GitHub token from GITHUB_TOKEN.
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

from native import KEY, MESSAGES, MODEL

ASK = """Below is a task, and the transcript of a run in which you did it through GitHub's REST API, request by
request. Write a Python 3 script that does the same task again with no model: standard library only (urllib), the
GitHub token from the environment variable GITHUB_TOKEN, and the arguments OWNER REPO LABEL STAMP, in that order,
in place of the values this run used ({owner}, {repo}, {label}, {stamp}). Read every number, id and sha the script
needs from GitHub's answers as it goes; never write one from this run into it. It must stop at the first request
that fails, saying which, and print each request it makes on one line. Answer with the script alone, in one
```python block.

<task>
{task}
</task>

<transcript>
{transcript}
</transcript>"""


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    task_file, transcript_file, out = sys.argv[1:]
    transcript = Path(transcript_file).read_text()
    run = transcript.split("\n---", 1)[0]
    values = {k: re.search(rf"^  {k}: (.*)$", run, re.M).group(1).strip('"') for k in ("owner", "repo", "label", "stamp")}
    task = Path(task_file).read_text()
    for name, value in values.items():
        task = task.replace("{" + name + "}", value)
    body = {"model": MODEL, "max_tokens": 16000,
            "messages": [{"role": "user", "content": ASK.format(task=task, transcript=transcript, **values)}]}
    request = urllib.request.Request(MESSAGES, data=json.dumps(body).encode(), method="POST")
    request.add_header("x-api-key", KEY)
    request.add_header("anthropic-version", "2023-06-01")
    request.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(request, timeout=600) as answer:
        message = json.loads(answer.read().decode())
    text = "".join(b.get("text", "") for b in message["content"])
    script = re.search(r"```python\n(.*?)```", text, re.S)
    if not script:
        sys.exit("no script in the answer:\n" + text[:2000])
    Path(out).write_text(script.group(1))
    used = message.get("usage", {})
    print(json.dumps({"wrote": out, "lines": script.group(1).count("\n"), "usage": {
        "calls": 1, "input": used.get("input_tokens", 0), "output": used.get("output_tokens", 0),
        "cache_read": used.get("cache_read_input_tokens", 0), "cache_write": used.get("cache_creation_input_tokens", 0),
    }}, indent=2))


if __name__ == "__main__":
    main()
