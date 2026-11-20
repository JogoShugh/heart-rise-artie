// The kept script, member.heart.yaml, run for each of the library's five members, with no model. Before and after
// each run, every book the member has out is read as plain JSON, so the summary says what the run changed.
//   node artie/five-members.mjs          (the library alone at http://localhost:8140, as heart-sims serves it)
// Writes runs/<member>.trace.yaml for each run, and five-members.yaml.
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parse, stringify } from "yaml";

const here = dirname(fileURLToPath(import.meta.url));
const LIBRARY = "http://localhost:8140";
const ARTIE = join(here, "../../../client/artie-cli/build/install/artie/bin/artie");
const JAVA_HOME = process.env.ARTIE_JAVA_HOME ?? "/Users/josh/Library/Java/JavaVirtualMachines/openjdk-22.0.1/Contents/Home";
const MEMBERS = ["Ana Torres", "David Miller", "Grace Okafor", "Liam Chen", "Sofia Rossi"];
const headers = { Authorization: "Bearer library-token", Accept: "application/json" };
const read = async (path) => (await fetch(`${LIBRARY}${path}`, { headers })).json();

await fetch(`${LIBRARY}/_sim/reset`, { method: "POST" });
mkdirSync(join(here, "runs"), { recursive: true });
const summary = [];
for (const member of MEMBERS) {
  const [found] = (await read(`/accounts?name=${encodeURIComponent(member)}`)).items;
  const account = await read(`/accounts/${found.id}`);
  const before = [];
  // The plain JSON account carries no links; its books are the ones the HEART answer links.
  const heart = await (await fetch(`${LIBRARY}/accounts/${found.id}`, { headers: { ...headers, Accept: "application/hal+json" } })).json();
  for (const item of [].concat(heart._links.item ?? [])) before.push(await read(item.href));
  const file = `runs/${member.toLowerCase().replace(/\s+/g, "-")}.trace.yaml`;
  const started = Date.now();
  const out = execFileSync(ARTIE, ["run", "member.heart.yaml", `member=${member}`, "--trace", file], {
    cwd: here, env: { ...process.env, JAVA_HOME, ARTIE_TOKEN_LOCALHOST_8140: "library-token" }, encoding: "utf8",
  });
  const ms = Date.now() - started;
  const after = [];
  for (const loan of before) after.push(await read(`/loans/${loan.id}`));
  const run = parse(readFileSync(join(here, file), "utf8").split("\n---\n")[0]).run ?? {};
  summary.push({
    member,
    card: account.id,
    books: before.map((loan, i) => ({
      title: loan.title,
      standing: loan.standing,
      renewed: after[i].renewals > loan.renewals,
      due: after[i].due,
    })),
    finished: out.trim().split("\n").at(-1),
    "model calls": run.usage?.calls ?? 0,
    ms,
    trace: file,
  });
  console.log(`${member}: ${summary.at(-1).finished}`);
}
writeFileSync(join(here, "five-members.yaml"), stringify({
  what: "member.heart.yaml, kept from one Claude run for Ana Torres, run for each of the library's five members with no model",
  at: new Date().toISOString(),
  members: summary,
}, { lineWidth: 120 }));
