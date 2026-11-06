// One row per run, from results.txt and each run's own output and trace: what it did, what it cost, and where the
// model wobbled. Writes summary.yaml beside the runs.
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import { createRequire } from "node:module";
const { stringify } = createRequire("/Users/josh/repos/artie/exhibits/overheard-at-riverside/package.json")("yaml");

const dir = "/Users/josh/repos/artie/exhibits/merge-watch/runs/yaml";
const rows = readFileSync(`${dir}/results.txt`, "utf8").trim().split("\n").filter((l) => l.includes(" | ")).map((line) => {
  const parts = line.split(" | ");
  const [name, model] = parts;
  const field = (re) => parts.find((p) => re.test(p)) ?? "";
  const bump = field(/^bump /), draft = field(/^draft /), secs = field(/^\d+s$/);
  const attempts = Number(field(/^attempts=/).replace("attempts=", "") || 1);
  const out = existsSync(`${dir}/${name}.out`) ? readFileSync(`${dir}/${name}.out`, "utf8") : "";
  const gz = `${dir}/${name}.trace.yaml.gz`, plain = `${dir}/${name}.trace.yaml`;
  const trace = existsSync(plain) ? readFileSync(plain, "utf8") : existsSync(gz) ? gunzipSync(readFileSync(gz)).toString("utf8") : "";
  const usage = trace.match(/^ {2}usage:\n((?: {4}\w+: \d+\n)+)/m);
  const u = Object.fromEntries((usage?.[1] ?? "").trim().split("\n").filter(Boolean).map((l) => l.trim().split(": ")).map(([k, v]) => [k, Number(v)]));
  const finished = out.match(/^finished, (\d+) steps; acted: (.*)$/m);
  return {
    run: name,
    attempts,
    model: model.replace("model=", ""),
    merged: /merged=true/.test(bump),
    told: Number(draft.match(/told=(\d+)/)?.[1] ?? 0) > 0,
    seconds: Number(secs.replace("s", "")),
    steps: finished ? Number(finished[1]) : null,
    acted: finished?.[2] ?? "stopped before it finished",
    calls: u.calls ?? null,
    input: u.input ?? null,
    cache_read: u.cache_read ?? null,
    output: u.output ?? null,
    stale: (out.match(/: refused$/gm) ?? []).length,
    unclear: (out.match(/unclear to the reasoner/g) ?? []).length,
    wrong: (out.match(/not offered|refused by the server: no reason/g) ?? []).length,
  };
});
writeFileSync(`${dir}/summary.yaml`, stringify({ what: "The merge watch from generated YAML, local models and Claude, on the scratch repository", runs: rows }));
for (const r of rows) console.log(`${r.run.padEnd(18)} ${r.merged ? "merged" : "NOT merged"} ${r.told ? "told" : "NOT told"} ${r.seconds}s calls=${r.calls} out=${r.output} stale=${r.stale} unclear=${r.unclear} wrong=${r.wrong}`);
