// The company script's three runs, at L0, L1 and L2, measured from their traces: requests by kind, the bytes of each
// answer's body as the client received it, and the time on the wire; and, as fragments for the deck, one loan's answer
// at L0 and at L2, and the affordance set L2 links to.
//   node artie/rungs/measure.mjs        (after the three runs: L0.trace.yaml, L1.trace.yaml, L2.trace.yaml)
// Writes rungs.yaml.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parseAllDocuments, stringify } from "yaml";

const here = dirname(fileURLToPath(import.meta.url));
const kind = (url) => {
  const path = new URL(url).pathname;
  if (path.startsWith("/affordance-sets/")) return "affordance set";
  if (path.startsWith("/schemas/")) return "schema";
  if (path.startsWith("/unavailable/")) return "why absent";
  return "resource";
};
const bytes = (body) => (body === undefined || body === null ? 0 : Buffer.byteLength(typeof body === "string" ? body : JSON.stringify(body)));

const measured = {};
const fragments = {};
for (const rung of ["L0", "L1", "L2"]) {
  const docs = parseAllDocuments(readFileSync(join(here, `${rung}.trace.yaml`), "utf8")).map((d) => d.toJS());
  const exchanges = docs.flatMap((d) => d?.exchanges ?? []);
  const byKind = {};
  for (const x of exchanges) {
    const k = kind(x.request.url);
    byKind[k] ??= { requests: 0, bytes: 0, distinct: new Set() };
    byKind[k].requests += 1;
    byKind[k].bytes += bytes(x.response.body);
    byKind[k].distinct.add(x.request.url);
  }
  const loans = exchanges.filter((x) => /\/loans\/\d+$/.test(new URL(x.request.url).pathname) && x.request.method === "GET");
  measured[rung] = {
    requests: exchanges.length,
    "bytes received": exchanges.reduce((n, x) => n + bytes(x.response.body), 0),
    "ms on the wire": exchanges.reduce((n, x) => n + (x.ms ?? 0), 0),
    "loan reads": loans.length,
    "bytes per loan read": Math.round(loans.reduce((n, x) => n + bytes(x.response.body), 0) / Math.max(loans.length, 1)),
    "by kind": Object.fromEntries(Object.entries(byKind).map(([k, v]) => [k, { requests: v.requests, distinct: v.distinct.size, bytes: v.bytes }])),
  };
  const renewable = loans.find((x) => x.response.body?.standing?.startsWith("renewable"));
  if (renewable) fragments[`${rung} loan`] = renewable.response.body;
  if (rung === "L2") {
    const set = exchanges.find((x) => kind(x.request.url) === "affordance set" && JSON.stringify(x.response.body).includes("library:renew"));
    if (set) fragments["L2 affordance set"] = { url: new URL(set.request.url).pathname, body: set.response.body };
  }
}

writeFileSync(join(here, "rungs.yaml"), stringify({
  what: "company.heart.yaml for Harbor Point, run with no model at L0, L1 and L2, measured from each run's exchanges",
  measured,
  fragments,
}, { lineWidth: 120 }));
for (const [rung, m] of Object.entries(measured)) {
  console.log(rung, m.requests, "requests,", m["bytes received"], "bytes,", m["ms on the wire"], "ms; per loan read", m["bytes per loan read"], "bytes;",
    JSON.stringify(m["by kind"]));
}
