"""
The benchmark's report, as one HTML page: tables computed from the runs, words written for them.

  .venv/bin/python -I report.py > report/index.html
"""
import html
import json
import re
import statistics
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).parent
RUNS = HERE / "runs"

ARM_NAMES = {
    "fastmcp": "FastMCP, one tool per endpoint",
    "fastmcp-typical": "FastMCP, typical spec",
    "meta-tools": "Meta-tools (list, schema, invoke)",
    "heart-mcp": "heart-mcp, as first met",
    "heart-mcp-lean": "heart-mcp, lean",
    "artie-goal": "ARTIE goal run",
    "artie-intent": "ARTIE intent run",
    "artie-kept": "ARTIE kept script, no model",
    "artie-script": "ARTIE script, no model",
}
STATIC = ["fastmcp", "fastmcp-typical", "meta-tools"]
HEART = ["heart-mcp", "heart-mcp-lean", "artie-goal", "artie-intent", "artie-kept", "artie-script"]
CHANGES = ["moved", "renamed", "required", "method", "notice", "moved,renamed,required,method,notice"]
CHANGE_NAMES = {"moved": "moved", "renamed": "renamed", "required": "required", "method": "method",
                "notice": "notice", "moved,renamed,required,method,notice": "all five"}
ADMITS = re.compile(r"could(n't| not)|unable|not able|failed|fail|error|404|405|422|409|412|not (found|offered|allowed|"
                    r"possible|done)|didn't|did not|wasn't|was not|cannot|can't|asks|refused|stopped", re.I)


def load(*names):
    found = []
    for name in names:
        path = RUNS / name
        if path.exists():
            found += [json.loads(line) for line in path.open()]
    return [r for r in found if "failed" not in r]


def median(values):
    values = [v for v in values if v is not None]
    return round(statistics.median(values)) if values else None


def group(runs, *keys):
    groups = defaultdict(list)
    for run in runs:
        groups[tuple(run.get(k) for k in keys)].append(run)
    return groups


def asked(run):
    text = run.get("final_text") or ""
    return "the planner asks" in text or "unclear to the reasoner" in text


def outcome(run):
    """What a run came to: done; stopped to ask; failed and said so; or failed, saying it succeeded."""
    if run["check"]["done"]:
        return "done"
    if asked(run):
        return "asked"
    return "failed, said so" if ADMITS.search(run.get("final_text") or "") else "failed, said it succeeded"


def k(n):
    return "—" if n is None else (f"{n / 1000:.1f}k" if n >= 1000 else str(n))


def esc(text):
    return html.escape(str(text))


def table(head, rows, cls=""):
    out = [f'<div class="table-scroll"><table class="{cls}"><thead><tr>']
    out += [f"<th>{h}</th>" for h in head]
    out.append("</tr></thead><tbody>")
    for row in rows:
        out.append("<tr>" + "".join(f"<td>{c}</td>" if i else f"<th>{c}</th>" for i, c in enumerate(row)) + "</tr>")
    out.append("</tbody></table></div>")
    return "".join(out)


def score(group_runs):
    done = sum(r["check"]["done"] for r in group_runs)
    total = len(group_runs)
    cls = "ok" if done == total else ("bad" if done == 0 else "mid")
    return f'<span class="{cls}">{done}/{total}</span>'


# ---- the sections ----

def baseline(runs):
    by = group(runs, "task", "arm")
    rows = []
    for arm in STATIC + HEART:
        cells = [ARM_NAMES[arm]]
        for task in ("t1", "t2"):
            g = by.get((task, arm), [])
            cells.append(f"{score(g)} · {k(median(r['input_processed'] for r in g))}" if g else "—")
        g = by.get(("t1", arm), [])
        cells.append(k(median(r["tool_definition_tokens"] for r in g)) if g else "—")
        cells.append(str(median(r["model_calls"] for r in g)) if g else "—")
        cells.append(str(median(r["http"] for r in g)) if g else "—")
        rows.append(cells)
    return table(["Arm", "Hire, promote, dismiss", "A dismissed employee", "Tool definitions",
                  "Model calls (task 1)", "HTTP requests (task 1)"], rows)


def concurrent(runs):
    by = group([r for r in runs if r["task"] == "t3"], "arm")
    rows = []
    for arm in STATIC + HEART:
        g = by.get((arm,), [])
        if not g:
            continue
        lost = sum(bool(r["check"].get("lost_update")) for r in g)
        noticed = sum("red book" in (r.get("final_text") or "").lower() for r in g)
        refused = sum(any(q["status"] == 412 for q in r["requests"]) for r in g)
        stopped = sum(outcome(r) == "failed, said so" and not r["check"].get("lost_update") for r in g)
        rows.append([ARM_NAMES[arm], score(g), f'<span class="{"bad" if lost else "ok"}">{lost}/{len(g)}</span>',
                     f"{noticed}/{len(g)}", f"{refused}/{len(g)}", f"{stopped}/{len(g)}",
                     k(median(r["input_processed"] for r in g))])
    return table(["Arm", "Kept both changes", "Lost the other change", "Mentioned the other change", "Refused 412",
                  "Stopped, said why", "Input tokens"], rows)


def changes(runs, when, arms):
    by = group([r for r in runs if r.get("when") == when], "arm", "change")
    shown = [c for c in CHANGES if any(by.get((arm, c)) for arm in arms)]
    rows = []
    for arm in arms:
        if any(by.get((arm, c)) for c in shown):
            rows.append([ARM_NAMES[arm]] + [score(by[(arm, c)]) if by.get((arm, c)) else "—" for c in shown])
    return table(["Arm"] + [CHANGE_NAMES[c] for c in shown], rows, "matrix")


def outcomes(runs, arms):
    by = group([r for r in runs if r.get("when") in ("start", "midway")], "arm")
    kinds = ["done", "asked", "failed, said so", "failed, said it succeeded"]
    rows = []
    for arm in arms:
        g = by.get((arm,), [])
        if g:
            counts = [sum(outcome(r) == kind for r in g) for kind in kinds]
            rows.append([ARM_NAMES[arm]] + [str(c) for c in counts] + [str(sum(r["http_4xx"] for r in g))])
    return table(["Arm", "Done", "Stopped to ask", "Failed, said so", "Failed, said it succeeded", "Refusals (4xx)"],
                 rows)


def lean(runs_before, runs_after):
    before, after = group(runs_before, "task"), group(runs_after, "task")
    rows = []
    for task, name in (("t1", "Hire, promote, dismiss"), ("t2", "A dismissed employee"), ("t3", "Someone else changes him")):
        b = median(r["input_processed"] for r in before.get((task,), []))
        a = median(r["input_processed"] for r in after.get((task,), []))
        cut = f"{round(100 * (a - b) / b)}%" if a and b else "—"
        rows.append([name, k(b), k(a), cut, score(after.get((task,), []))])
    return table(["Task", "heart-mcp, as first met", "heart-mcp, lean", "Change", "Lean, done"], rows)


def models(runs):
    by = group(runs, "task", "arm")
    rows = []
    for arm in ("fastmcp", "meta-tools", "heart-mcp-lean"):
        cells = [ARM_NAMES[arm]]
        for task in ("t1", "t2", "t3"):
            g = by.get((task, arm), [])
            cells.append(f"{score(g)} · {k(median(r['input_processed'] for r in g))}" if g else "—")
        rows.append(cells)
    return table(["Arm", "Hire, promote, dismiss", "A dismissed employee", "Someone else changes him"], rows)


def fix(before, stale, toward):
    """The intent run before tonight's fixes, after deciding again on stale, and after acts toward the words."""
    rows = []
    for name, pick in (("Someone else changes him", lambda r: r["task"] == "t3"),
                       ("Changed midway", lambda r: r.get("when") == "midway"),
                       ("Changed at start", lambda r: r.get("when") == "start")):
        b, m, a = ([r for r in runs if pick(r)] for runs in (before, stale, toward))
        rows.append([name, score(b), score(m), score(a), k(median(r["input_processed"] for r in a))])
    return table(["Intent run", "Before", "Decides again on stale", "And acts toward the words", "Input tokens, now"],
                 rows)


def overwrite(runs):
    """Words that set a value, while someone else changes it: who wrote over it, and who said so."""
    by = group(runs, "arm")
    rows = []
    for arm in STATIC + HEART:
        g = by.get((arm,), [])
        if not g:
            continue
        n = len(g)
        refused = sum(any(q["status"] == 412 for q in r["requests"]) for r in g)
        asked_ = sum(asked(r) for r in g)
        said = sum(r["check"]["overwrote"] and r["check"]["mentioned"] for r in g)
        silent = sum(r["check"]["silent_overwrite"] for r in g)
        rows.append([ARM_NAMES[arm], f"{refused}/{n}", f"{asked_}/{n}", f"{said}/{n}",
                     f'<span class="{"bad" if silent else "ok"}">{silent}/{n}</span>', k(median(r["input_processed"] for r in g))])
    return table(["Arm", "Refused 412", "Stopped and asked", "Wrote it, said whose change it replaced",
                  "Wrote over it, said nothing", "Input tokens"], rows)


def example(run, title):
    lines = []
    for tool in run.get("tools") or []:
        lines.append(f"→ {tool['tool']} {json.dumps(tool['input'])}")
        lines.append(f"  ← {tool['result'][:220]}")
    lines.append("")
    lines.append(f"said: {run.get('final_text') or ''}")
    wire = "\n".join(f"{q['method']} {q['url']} → {q['status']}" for q in run["requests"])
    return (f'<details class="src"><summary>{esc(title)}</summary><pre><code>{esc(chr(10).join(lines))}</code></pre>'
            f'<p>On the wire:</p><pre><code>{esc(wire)}</code></pre></details>')


def first(runs, **match):
    for run in runs:
        if all(run.get(key) == value for key, value in match.items()):
            return run
    return None


def main():
    kept = [r for r in load("intent-kept-fixed-sonnet.jsonl") if r["arm"] == "artie-kept"]
    toward = load("intent-toward-sonnet.jsonl")
    # The goal run's concurrent edit and words that set a value, run again once a step's own read guards its act.
    validator = load("validator-sonnet.jsonl")
    goal_now = [r for r in validator if r["arm"] == "artie-goal"]
    base = ([r for r in load("full-sonnet.jsonl") if not (r["arm"] == "artie-goal" and r["task"] == "t3")] + kept
            + [r for r in toward if r["task"] not in ("t3", "t4")] + [r for r in validator if r["task"] == "t3"])
    intent_before = [r for r in load("intent-kept-sonnet.jsonl", "changes-intent-kept-sonnet.jsonl") if r["arm"] == "artie-intent"]
    intent_stale = [r for r in load("intent-kept-fixed-sonnet.jsonl", "changes-intent-kept-fixed-sonnet.jsonl")
                    if r["arm"] == "artie-intent"]
    intent_now = [r for r in toward if r["task"] == "t3"] + load("changes-intent-toward-sonnet.jsonl")
    t4 = ([r for r in load("t4-sonnet.jsonl") if r["arm"] not in ("artie-intent", "artie-goal")]
          + [r for r in validator if r["task"] == "t4"])
    lean_runs = load("lean-sonnet.jsonl")
    nuclear = (load("changes-sonnet.jsonl") + load("changes-lean-sonnet.jsonl")
               + [r for r in load("changes-intent-kept-fixed-sonnet.jsonl") if r["arm"] == "artie-kept"]
               + load("changes-intent-toward-sonnet.jsonl"))
    control = load("control-sonnet.jsonl")
    haiku = load("haiku.jsonl")
    haiku_changes = load("changes-haiku.jsonl")
    scale = json.loads((RUNS / "scale-github.json").read_text())
    template = (HERE / "report/template.html").read_text()
    t3 = [r for r in base + lean_runs if r["task"] == "t3"]
    sections = {
        "BASELINE": baseline(base + lean_runs),
        "CONCURRENT": concurrent(base + lean_runs),
        "CHANGES_START": changes(nuclear, "start", STATIC + HEART),
        "CHANGES_MIDWAY": changes(nuclear, "midway", STATIC + HEART),
        "CHANGES_CONTROL": changes(control, "before", STATIC + ["heart-mcp-lean"]),
        "OUTCOMES": outcomes(nuclear, STATIC + HEART),
        "LEAN": lean([r for r in base if r["arm"] == "heart-mcp"], lean_runs),
        "INTENT_FIX": fix(intent_before, intent_stale, intent_now),
        "OVERWRITE": overwrite(t4),
        "EXAMPLE_ASKED": example(next(r for r in t4 if r["arm"] == "artie-intent" and asked(r)),
                                 "ARTIE's intent run: refused 412, decides again, and asks before writing over"),
        "HAIKU": models(haiku) if haiku else "<p>Not run.</p>",
        "HAIKU_CHANGES": changes(haiku_changes, "start", ["fastmcp", "meta-tools", "heart-mcp-lean"]) if haiku_changes else "",
        "GITHUB_TOOLS": f"{scale['tools']:,}",
        "GITHUB_TOKENS": f"{scale['tool_definition_tokens']:,}",
        "GITHUB_PER_TOOL": f"{scale['mean_tokens_per_tool']:,}",
        "GITHUB_LARGEST": f"{scale['largest_tool_chars'] // 1000}",
        "RUN_COUNT": str(len(base) + len(lean_runs) + len(t4) + len(intent_before) + len(intent_stale) + len(goal_now) + len(nuclear) + len(control) + len(haiku) + len(haiku_changes)),
        "EXAMPLE_LOST": example(first(t3, arm="fastmcp"), "FastMCP loses the other change, and says it succeeded"),
        "EXAMPLE_KEPT": example(first(t3, arm="heart-mcp-lean"), "heart-mcp is refused 412, reads again, keeps both"),
        "EXAMPLE_MOVED": example(first(nuclear, arm="fastmcp", change="moved", when="start"),
                                 "FastMCP after the acts moved: 404, and half the work done"),
        "EXAMPLE_NOTICE": example(first(nuclear, arm="heart-mcp-lean", change="notice", when="start"),
                                  "heart-mcp after notice was added: finds and gives notice, then dismisses"),
        "EXAMPLE_NOTICE_STATIC": example(first(nuclear, arm="meta-tools", change="notice", when="start"),
                                         "Meta-tools after notice was added: no tool for it"),
    }
    for key, value in sections.items():
        template = template.replace("{{" + key + "}}", value)
    print(template)


if __name__ == "__main__":
    main()
