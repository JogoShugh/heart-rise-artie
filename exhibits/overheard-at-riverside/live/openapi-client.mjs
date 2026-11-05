// An OpenAPI client of the library, written as most are: it reads openapi.json, and treats every operation under a
// resource's path as something it can do there. One option, optIn, has it also read the affordance link the
// description documents (x-heart-affordances): the Link header on each plain JSON answer names the set of forms the
// state offers now, and the client keeps only the operations a form in it matches, by method and path template.
// The set is fetched once per address, and checked against the link's integrity.
//   node live/openapi-client.mjs [simulator address, default http://127.0.0.1:8181]
// Every exchange is written to openapi-optin.capture.yaml.
import { createHash } from "node:crypto";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { stringify } from "yaml";

const here = dirname(fileURLToPath(import.meta.url));
const SIMULATOR = (process.argv[2] ?? "http://127.0.0.1:8181").replace(/\/$/, "");
const AUTHORIZATION = "Bearer library-token";

const exchanges = [];
async function send(method, path, headers = {}, body) {
  const response = await fetch(`${SIMULATOR}${path}`, {
    method, body, headers: { "X-Sim-Host": "library.test", Authorization: AUTHORIZATION, ...headers },
  });
  const bytes = Buffer.from(await response.arrayBuffer());
  let json = null;
  try { json = JSON.parse(bytes.toString("utf8")); } catch { /* not JSON */ }
  exchanges.push({
    request: { method, url: path, headers, ...(body ? { body: JSON.parse(body) } : {}) },
    response: {
      status: response.status, "content-type": response.headers.get("content-type"),
      ...(response.headers.get("link") ? { link: response.headers.get("link") } : {}),
      body: path === "/openapi.json" ? "(the description, read once)" : json,
    },
  });
  return { status: response.status, headers: response.headers, bytes, json };
}

// What any OpenAPI client knows: the operations, by method and path template.
const description = (await send("GET", "/openapi.json")).json;
const operations = Object.entries(description.paths).flatMap(([path, methods]) =>
  Object.entries(methods).map(([method, op]) => ({ method: method.toUpperCase(), path, summary: op.summary })));
const doneTo = (resource) => operations.filter((op) => op.path.startsWith(`${resource}/`) && op.method !== "GET");
const matches = (template, href) => new RegExp(`^${template.replace(/\{[^}]+\}/g, "[^/]+")}$`).test(href);

// The opt-in: the link the description documents, the set it names, and the operations the set's forms match.
const sets = new Map();
const affordance = description["x-heart-affordances"];
const linked = (header) => (header ?? "").split(/,\s*(?=<)/).map((part) => ({
  href: /<([^>]+)>/.exec(part)?.[1], rel: /rel="([^"]+)"/.exec(part)?.[1],
  type: /type="([^"]+)"/.exec(part)?.[1], integrity: /integrity="([^"]+)"/.exec(part)?.[1],
})).find((link) => link.rel === affordance.rel);

async function allowedNow(header, candidates) {
  const link = linked(header);
  if (!link) return { offered: candidates, from: "no link" };
  if (!sets.has(link.href)) {
    const set = await send("GET", link.href, { Accept: link.type });
    const [algorithm, digest] = link.integrity.split(/-(.*)/s);
    const intact = createHash(algorithm).update(set.bytes).digest("base64") === digest;
    if (!intact) throw new Error(`the set at ${link.href} does not match its integrity`);
    sets.set(link.href, Object.values(set.json._forms));
  }
  const forms = sets.get(link.href);
  return { offered: candidates.filter((op) => forms.some((f) => f.method === op.method && matches(op.path, f._links.target.href))), from: link.href };
}

async function loan(id, { optIn }) {
  const answer = await send("GET", `/loans/${id}`, { Accept: "application/json" });
  const candidates = doneTo("/loans/{id}");
  const { offered, from } = optIn ? await allowedNow(answer.headers.get(affordance.header), candidates) : { offered: candidates, from: "the description alone" };
  return { title: answer.json.title, standing: answer.json.standing, offered: offered.map((op) => op.summary), from };
}

const steps = [];
const step = async (say, run) => {
  // The description was read before the first step; it belongs to it.
  const from = steps.length ? exchanges.length : 0;
  steps.push({ say, outcome: await run(), exchanges: exchanges.slice(from) });
};

await fetch(`${SIMULATOR}/_sim/reset`, { method: "POST" });
await step("It reads the description: six operations, every one listed for every loan", async () =>
  ({ operations: operations.map((op) => `${op.method} ${op.path}`) }));
await step("The Hobbit, as it always read it: renew and return both look possible", () => loan(3310, { optIn: false }));
await step("So it renews, and is refused only after the call", async () => {
  const answer = await send("POST", "/loans/3310/actions/renew", { Accept: "application/json", "Content-Type": "application/json" }, "{}");
  return { status: answer.status, detail: answer.json?.detail };
});
await step("Opted in: the same answer, plus the link it now reads. The Hobbit allows return only", () => loan(3310, { optIn: true }));
await step("Matilda, opted in: renew and return", () => loan(3311, { optIn: true }));
await step("The Hobbit again: its set is held by address, so nothing more is fetched", () => loan(3310, { optIn: true }));

writeFileSync(join(here, "openapi-optin.capture.yaml"), stringify({
  what: "An OpenAPI client of Riverside's library, live from heart-sims, before and after it opts in to the affordance link",
  at: new Date().toISOString(),
  service: "heart-sims library.test (heart/heart-sims), Bearer credential not recorded",
  steps,
}, { lineWidth: 120 }));
for (const s of steps) console.log(`${s.say}\n  ${JSON.stringify(s.outcome)}  (${s.exchanges.length} exchanges)`);
