// Writes the messages of release runs, for the animated sequence, from the desk's traces of them (ARTIE's own capture):
//   node messages.mjs OUT.js NAME=TRACE.yaml …
// Each request ARTIE sent is one message, numbered as its step, with its request and answer lines and the REST
// constraints it shows, each with why, from the exchange alone. The parties' own messages, which ARTIE never sees,
// are added where the trace shows their effect: a reply read in a new state was answered since, by the party its
// hr:in-reply-to names.
import fs from "node:fs";
import vm from "node:vm";
import path from "node:path";

const [out, ...runs] = process.argv.slice(2);
const here = path.dirname(new URL(import.meta.url).pathname);
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(here, "../assets/js-yaml.min.js"), "utf8"), sandbox);

const PARTY = { 8160: "replies", 8098: "github", 8099: "watch", 8170: "ci", 8171: "jenkins", 8180: "preview", 8190: "dev" };
const BEHIND = { 8098: "the GitHub API", 8170: "Jenkins" };
const NAME = { 8098: "The gateway", 8170: "The CI wrapper" };
const port = (url) => new URL(url).port;
const party = (url) => PARTY[port(url)] || "other";
const short = (url) => { const u = new URL(url); return decodeURIComponent(u.pathname + u.search); };
const cut = (text, n = 28) => (String(text).length > n ? `${String(text).slice(0, n)}…` : String(text));
const header = (headers, name) => {
  const key = Object.keys(headers || {}).find((k) => k.toLowerCase() === name.toLowerCase());
  return key ? String(headers[key]) : null;
};
const said = (step) => String(step.say || "").replace(/\{(\w+)\}/g, (_, k) => {
  const v = step.params?.[k];
  return v == null ? `{${k}}` : typeof v === "object" ? "…" : cut(v, 40);
});

/** The request and answer lines a person reads: method and path, the headers that carry the constraints, status. */
function lines(req, res) {
  const asked = [`${req.method} ${short(req.url)}`];
  for (const name of ["Accept", "Accept-Profile", "If-Match", "If-None-Match", "Content-Type", "Content-Digest", "Signature-Input"]) {
    const v = header(req.headers, name);
    if (v) asked.push(`${name}: ${cut(v, name === "Signature-Input" ? 60 : 34)}`);
  }
  if (header(req.headers, "Authorization")) asked.push("Authorization: <token>");
  const body = req.body && typeof req.body === "object" ? Object.keys(req.body).filter((k) => k !== "_links") : [];
  if (body.length) asked.push("", `{ ${body.join(", ")} }`);
  const answer = [`${res.status}`];
  for (const name of ["ETag", "Location", "Content-Location", "Cache-Control"]) {
    const v = header(res.headers, name);
    if (v) answer.push(`${name}: ${cut(v, 40)}`);
  }
  return { asked, answer };
}

/** The constraints [ex] of [step] shows, each with why, from the exchange alone. */
function constraints(step, ex) {
  const req = ex.request, res = ex.response || {};
  const why = {};
  const entered = String(step.say || "").startsWith("enter");
  const returned = String(step.say || "").startsWith("return to");
  const rel = step.params?.relation;
  why.identification = `The request names one resource by its URI: ${short(req.url)}.`;
  if (!entered) {
    why.hypermedia = returned
      ? "ARTIE reads again an address an earlier answer handed over; it builds no URI."
      : `The URI came from the representation held before: ${rel ? `its ${rel} ${step.chose?.kind === "form" ? "form" : "link"}` : "a link it offered"}.`;
  }
  if (req.method !== "GET") {
    const ifMatch = header(req.headers, "If-Match");
    why.representations = `It sends a representation of the state it intends${ifMatch ? ", against the version it read: If-Match carries that ETag, so a stale view gets 412, not a blind write" : ""}.`;
  } else {
    why.representations = "It asks for a representation of the resource's current state, never the resource itself.";
  }
  const signed = header(req.headers, "Signature-Input");
  why.selfDescriptive = `Accept${header(req.headers, "Accept-Profile") ? " and Accept-Profile say" : " says"} what it understands${signed ? "; Content-Digest and a signature say who sent what" : ""}: the message explains itself.`;
  if (header(req.headers, "Authorization") || signed) {
    why.stateless = `It carries its own credentials${signed ? " and signature" : ""}; the server keeps no session for ARTIE.`;
  }
  if (res.status === 304 || header(req.headers, "If-None-Match")) {
    why.cache = res.status === 304
      ? "If-None-Match asks whether the copy held is still good; 304 says it is, with no body sent."
      : "If-None-Match revalidates the copy held; the resource changed, so a new representation came back.";
  }
  if (BEHIND[port(req.url)]) why.layered = `${NAME[port(req.url)]} stands in front of ${BEHIND[port(req.url)]}; ARTIE sees only it.`;
  return why;
}

function messages(trace) {
  const steps = sandbox.jsyaml.loadAll(fs.readFileSync(trace, "utf8")).slice(1);
  const all = [], replyState = new Map();
  let watchShown = false, jenkinsShown = false;
  const pull = steps.find((s) => s.params?.relation === "github:pulls-get")?.address;
  const own = (from, to, label, note) => all.push({ kind: "own", from, to, label, note });
  steps.forEach((step, i) => {
    (step.exchanges || []).forEach((ex) => {
      const req = ex.request, res = ex.response || {};
      const body = typeof res.body === "object" && res.body ? res.body : {};
      if (body.reply && body.state) {
        const key = `${body.state}/${body.percent ?? ""}`, before = replyState.get(body.reply);
        const by = body._links?.["hr:in-reply-to"]?.href;
        if (by && port(by) === "8099" && !watchShown && pull) {
          watchShown = true;
          own("watch", "github", `GET ${short(pull)}, every 3 s`, "The watch looks at the pull request until it offers github:merge.");
        }
        if (by && port(by) === "8170" && !jenkinsShown) {
          jenkinsShown = true;
          own("ci", "jenkins", "POST buildWithParameters", "The wrapper starts the Jenkins job with the commit.");
          own("jenkins", "ci", "QUEUED, STARTED", "Jenkins tells the wrapper, by its Notification plugin.");
        }
        if ((before !== key && by && body.state !== "awaiting") || (before && before !== key && by)) {
          const rel = body.state === "completed" ? "hr:completed" : body.state === "failed" ? "hr:failed" : "hr:progress";
          if (rel === "hr:completed" && port(by) === "8170") {
            own("jenkins", "preview", "starts the app", "Jenkins deploys the preview at the commit.");
            own("jenkins", "ci", "COMPLETED: SUCCESS", "Jenkins tells the wrapper the build is done, with its artifacts.");
          }
          all.push({
            kind: "rise", from: party(by), to: "replies", label: `PATCH reply · ${rel}`,
            note: `${party(by) === "watch" ? "The watch" : "The CI wrapper"} answers the desk's reply, signed by its own key.`,
            why: {
              identification: `It answers the reply by its URI, ${short(req.url)}.`,
              hypermedia: "That URI was handed over in the ask's hr:reply-to link; the party built none.",
              representations: `It sends the reply's new state, ${body.state}.`,
              selfDescriptive: "Content-Digest and an RFC 9421 signature, by a key named in hr:keys, travel with it.",
              stateless: "The answer carries its hr:in-reply-to link and signature; no session ties it to the ask.",
            },
          });
        }
        replyState.set(body.reply, key);
      }
      // An ask carries the reply the desk made for it, before it asked: made in the desk, with no request on the wire.
      const envelope = typeof req.body === "object" && req.body ? req.body._links || {} : {};
      const replyTo = envelope["hr:reply-to"]?.href;
      if (replyTo) {
        all.push({
          kind: "rise", inside: true, from: "artie", to: "replies", label: `makes reply ${cut(short(replyTo), 24)}`,
          note: "Before it asks, the desk makes the reply the answer will come to, and the key list to check its signature by. "
            + "This happens inside the desk, with no request on the wire: the next message carries both in its envelope.",
          asked: [`reply  ${short(replyTo)}`, `keys   ${short(envelope["hr:keys"]?.href || "")}`, "", "state: awaiting"],
          answer: [],
          why: {
            identification: `The reply is a resource of its own, with its own URI: ${short(replyTo)}.`,
            hypermedia: "Its URI is handed to the party in hr:reply-to; the party builds none, it follows the link.",
          },
        });
      }
      const { asked, answer } = lines(req, res);
      if (replyTo) asked.push(`_links: hr:reply-to ${short(replyTo)}, hr:keys`);
      const state = body.state ? ` ${body.state}` : "";
      all.push({
        kind: party(req.url) === "replies" ? "rise" : "heart", n: i + 1, from: "artie", to: party(req.url),
        label: `${req.method} ${step.params?.relation || cut(short(req.url), 22)}`, status: `${res.status}${state}`,
        say: said(step), entered: String(step.say || "").startsWith("enter"), asked, answer,
        why: constraints(step, ex),
      });
    });
  });
  return all;
}

const data = Object.fromEntries(runs.map((arg) => {
  const [name, trace] = arg.split("=");
  return [name, messages(trace)];
}));
fs.writeFileSync(out, `window.RUNS = ${JSON.stringify(data)};\n`);
console.log(`wrote ${out}: ${Object.entries(data).map(([k, v]) => `${k} ${v.length} messages`).join(", ")}`);
