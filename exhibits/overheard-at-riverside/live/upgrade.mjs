// The library, live, both ways: one client, heart-ts, reads Riverside's loans as plain JSON and then as HEART,
// from the same running service; a plain JSON parser reads the HEART answer too. Every exchange is written to
// upgrade.capture.yaml.
//   node live/upgrade.mjs [simulator address, default http://127.0.0.1:8181]
// The simulator is heart-sims (heart/heart-sims), which serves the library as library.test.
import { createRequire } from "node:module";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { stringify } from "yaml";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const { HeartClient } = require("../../../implementations/heart-ts/dist/index.js");
const SIMULATOR = (process.argv[2] ?? "http://127.0.0.1:8181").replace(/\/$/, "");
const LIBRARY = "http://library.test";
const AUTHORIZATION = "Bearer library-token";

// The simulator answers for library.test when a request names it; every exchange is kept for the capture.
const exchanges = [];
const realFetch = globalThis.fetch;
globalThis.fetch = async (input, init = {}) => {
  const url = new URL(typeof input === "string" ? input : input.url);
  const headers = new Headers(init.headers);
  headers.set("X-Sim-Host", url.host);
  const response = await realFetch(`${SIMULATOR}${url.pathname}${url.search}`, { ...init, headers });
  const text = await response.clone().text();
  const asked = {};
  headers.forEach((value, name) => { if (name !== "x-sim-host" && name !== "authorization") asked[name] = value; });
  let body = text;
  try { body = JSON.parse(text); } catch { body = text.length > 160 ? `${text.slice(0, 160)}…` : text; }
  exchanges.push({
    request: { method: init.method ?? "GET", url: `${url.pathname}${url.search}`, headers: asked },
    response: { status: response.status, "content-type": response.headers.get("content-type"), etag: response.headers.get("etag"), body },
  });
  return response;
};

const offered = (rep) => [...rep.forms.keys()];
const steps = [];
const step = async (say, run) => {
  const from = exchanges.length;
  const outcome = await run().catch((error) => ({ stopped: error.message }));
  steps.push({ say, outcome, exchanges: exchanges.slice(from) });
};

await realFetch(`${SIMULATOR}/_sim/reset`, { method: "POST" });

await step("An old-school client asks for plain JSON: The Hobbit, data only", async () => {
  const plain = new HeartClient({ dialect: "json", authorization: AUTHORIZATION });
  const rep = await plain.enter(`${LIBRARY}/loans/3310`);
  return { dialect: rep.dialect, state: rep.state, forms: offered(rep), links: rep.links.length };
});

await step("It tries to renew The Hobbit, and nothing is sent: plain JSON offers no acts", async () => {
  const plain = new HeartClient({ dialect: "json", authorization: AUTHORIZATION });
  await plain.enter(`${LIBRARY}/loans/3310`);
  await plain.act("library:renew", {});
  return { sent: true };
});

await step("The same client code asks for HEART: The Hobbit offers report a problem and return, and not renew", async () => {
  const heart = new HeartClient({ authorization: AUTHORIZATION });
  const rep = await heart.enter(`${LIBRARY}/loans/3310`);
  return { dialect: rep.dialect, standing: rep.state.standing, forms: offered(rep) };
});

await step("And Matilda offers renew, report a problem and return", async () => {
  const heart = new HeartClient({ authorization: AUTHORIZATION });
  const rep = await heart.enter(`${LIBRARY}/loans/3311`);
  return { dialect: rep.dialect, standing: rep.state.standing, forms: offered(rep) };
});

await step("A plain JSON parser reads the HEART answer: the data is all there, the controls are extra members", async () => {
  const response = await fetch(`${LIBRARY}/loans/3311`, { headers: { Accept: "application/hal+json", Authorization: AUTHORIZATION } });
  const body = await response.json();
  return { title: body.title, due: body.due, standing: body.standing, extra: Object.keys(body).filter((k) => k.startsWith("_")) };
});

await step("A browser asks for text/html, and gets the page", async () => {
  const response = await fetch(`${LIBRARY}/loans/3311`, { headers: { Accept: "text/html", Authorization: AUTHORIZATION } });
  return { status: response.status, type: response.headers.get("content-type") };
});

const capture = {
  what: "Riverside's library, live from heart-sims: one client, heart-ts, as plain JSON and as HEART",
  at: new Date().toISOString(),
  service: "heart-sims library.test (heart/heart-sims), Bearer credential not recorded",
  steps,
};
writeFileSync(join(here, "upgrade.capture.yaml"), stringify(capture, { lineWidth: 120 }));
for (const s of steps) console.log(`${s.say}\n  ${JSON.stringify(s.outcome)}`);
