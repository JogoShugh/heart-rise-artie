"""
The benchmark's runs, summed up by arm and task: medians, rates and counts, as JSON for the report.

  .venv/bin/python -I summarize.py runs/full-sonnet.jsonl > runs/full-sonnet.summary.json
"""
import json
import statistics
import sys
from collections import defaultdict

ARMS = ["fastmcp", "fastmcp-typical", "meta-tools", "heart-mcp", "artie-goal", "artie-script"]


def median(values):
    values = [v for v in values if v is not None]
    return round(statistics.median(values)) if values else None


def weighted(run):
    """Input tokens as a bill weighs them: cache writes at 1.25, cache reads at 0.1, the rest at 1."""
    return run["input_tokens"] + 1.25 * run["cache_write"] + 0.1 * run["cache_read"]


def main():
    runs = [json.loads(line) for line in open(sys.argv[1])]
    failed = [r for r in runs if "failed" in r]
    groups = defaultdict(list)
    for run in runs:
        if "failed" not in run:
            groups[(run["arm"], run["task"])].append(run)
    rows = []
    for (arm, task), group in sorted(groups.items(), key=lambda kv: (kv[0][1], ARMS.index(kv[0][0]))):
        rows.append({
            "arm": arm, "task": task, "runs": len(group),
            "done": sum(r["check"]["done"] for r in group),
            "lost_updates": sum(bool(r["check"].get("lost_update")) for r in group) if task == "t3" else None,
            "interfered": sum(bool(r.get("interfered")) for r in group) if task == "t3" else None,
            "tool_definition_tokens": median(r.get("tool_definition_tokens") for r in group),
            "input_processed": median(r["input_processed"] for r in group),
            "input_weighted": median(weighted(r) for r in group),
            "output_tokens": median(r["output_tokens"] for r in group),
            "model_calls": median(r["model_calls"] for r in group),
            "tool_calls": median(r["tool_calls"] for r in group),
            "tool_errors": sum(r.get("tool_errors") or 0 for r in group),
            "tool_result_chars": median(sum(t["chars"] for t in r["tools"]) if r.get("tools") else None for r in group),
            "http": median(r["http"] for r in group),
            "http_4xx": sum(r["http_4xx"] for r in group),
            "seconds": median(r["seconds"] for r in group),
        })
    print(json.dumps({"runs": len(runs), "failed": [{k: r[k] for k in ("arm", "task", "failed")} for r in failed],
                      "rows": rows}, indent=2))


if __name__ == "__main__":
    main()
