// Writes a run's PlantUML sequence diagram from its trace: node sequence.mjs TRACE.yaml OUT.puml "Title"
// Every message ARTIE sent is from the trace, numbered as its step. The parties' own messages to each other, which
// ARTIE never sees, are drawn grey: the answers to ARTIE's replies, read from the replies' changes of state between
// two reads, and Jenkins' part, from the CI wrapper's log of the notifications Jenkins sent it.
import fs from "node:fs";
import vm from "node:vm";
import path from "node:path";

const [trace, out, title] = process.argv.slice(2);
const here = path.dirname(new URL(import.meta.url).pathname);
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(here, "../assets/js-yaml.min.js"), "utf8"), sandbox);
const steps = sandbox.jsyaml.load(fs.readFileSync(trace, "utf8")).steps;

const PARTIES = [
  ["ARTIE", "ARTIE", "actor"],
  ["8098", "GitHub\\n(gateway :8098)", "participant"],
  ["8160", "ARTIE's replies\\n:8160", "participant"],
  ["8099", "Offer watch\\n:8099", "participant"],
  ["8170", "CI wrapper\\n:8170", "participant"],
  ["8171", "Jenkins\\n:8171", "participant"],
  ["8180", "Preview\\n:8180", "participant"],
  ["8190", "Dev\\n:8190", "participant"],
];
const alias = (port) => `P${port}`;
const portOf = (url) => new URL(url).port;
const short = (url) => { const u = new URL(url); return u.pathname + u.search; };
const words = (text) => text.replace(/`/g, "").replace(/"/g, "'");
const STATUS = { 200: "200 OK", 201: "201 Created", 204: "204 No Content", 412: "412 stale" };

const lines = [
  "@startuml",
  "!theme plain",
  "skinparam shadowing false",
  "skinparam defaultFontName Helvetica",
  "skinparam sequenceMessageAlign left",
  "skinparam responseMessageBelowArrow true",
  "skinparam maxMessageSize 320",
  "skinparam ParticipantPadding 12",
  `title ${title}`,
];
for (const [port, name, kind] of PARTIES) lines.push(`${kind} "${name}" as ${port === "ARTIE" ? "ARTIE" : alias(port)}`);

// PlantUML's color markup ends at each line break, so each line is coloured on its own.
const muted = (text) => text.split("\\n").map((line) => `<color:#666666>${line}</color>`).join("\\n");
const grey = (from, to, text) => lines.push(`${from} -[#888888]> ${to} : ${muted(text)}`);
let section = null;
const sectionOf = (i) => {
  const say = steps[i].say || "";
  // A reply is made for the ask that follows it.
  if (/makes its reply/.test(say) && i + 1 < steps.length) return sectionOf(i + 1);
  if (/^S\d/.test(say)) return "Smoke test, on the preview";
  if (/comment/i.test(say)) return "Stopped: say why on the pull request";
  if (/Dev|environments/.test(say)) return "Promote";
  if (/CI wrapper|builds commit|Once \{build\}|smoke script/.test(say)) return "Build, by Jenkins";
  if (/Once \{watch\}|offer watch|asks to be told/.test(say)) return "Watch";
  if (/merg|follows the pull request/.test(say)) return "Merge";
  return "Open the pull request";
};
const replyState = new Map();
let jenkinsShown = false, watchShown = false;

steps.forEach((step, i) => {
  const n = i + 1, req = step.request, res = step.response || {}, say = step.say || "";
  const now = sectionOf(i);
  if (now !== section) {
    lines.push(`== ${now} ==`);
    section = now;
  }
  const to = alias(portOf(req.url));
  const body = res.body || {};
  // A reply read in a new state was answered since: by the party its hr:in-reply-to names.
  if (body.reply && body.state) {
    const before = replyState.get(body.reply);
    const key = `${body.state}/${body.percent ?? ""}`;
    const by = body._links?.["hr:in-reply-to"]?.href;
    if (before && before !== key && by) {
      const rel = body.state === "completed" ? "hr:completed" : body.state === "failed" ? "hr:failed" : "hr:progress";
      const what = body.note ? `, "${words(body.note)}"` : "";
      grey(alias(portOf(by)), to, `PATCH ${short(req.url)}\\n${rel}${what}, signed`);
    }
    replyState.set(body.reply, key);
  }
  // The watch looks at the pull request while GitHub works out whether it can be merged.
  if (/Once \{watch\}/.test(say) && !watchShown) {
    watchShown = true;
    const pull = steps.find((s) => /opens pull request/.test(s.say || ""));
    grey("P8099", "P8098", `GET ${short(pull.request.url)}\\nlooks every 3 s, until it offers github:merge`);
  }
  // The wrapper starts the Jenkins job, and Jenkins tells it, as the build goes.
  if (/Once \{build\}/.test(say) && !jenkinsShown) {
    jenkinsShown = true;
    grey("P8170", "P8171", "POST /job/app/buildWithParameters\\nSHA, RELEASE_BUILD, PREVIEW_PORT");
    grey("P8171", "P8170", "POST /hooks/jenkins\\nQUEUED, STARTED (Notification plugin)");
  }
  const label = `<b>${n}</b> ${req.method} ${short(req.url)}\\n<i>${words(say)}</i>`;
  lines.push(`ARTIE -> ${to} : ${label}`);
  lines.push(`${to} --> ARTIE : ${STATUS[res.status] || res.status}${body.state ? ` ${body.state}` : ""}`);
  if (step.summary) lines.push(`note over ARTIE : ${words(step.summary)}`);
});

// Jenkins' deploy and completion belong just before the completed answer: move them there.
const completed = lines.findIndex((l) => l.includes("hr:completed") && l.startsWith("P8170"));
if (completed > 0) {
  lines.splice(completed, 0,
    `P8171 -[#888888]> P8180 : ${muted("starts the library at the commit")}`,
    `P8171 -[#888888]> P8170 : ${muted("POST /hooks/jenkins\\nCOMPLETED, FINALIZED: SUCCESS, artifacts")}`);
}
lines.push(
  "legend right",
  "  black: ARTIE's requests and their answers, numbered as the steps of its trace",
  "  grey: the parties' own messages, which ARTIE never sees: answers to its replies,",
  "  read from each reply's change of state; Jenkins' part, from the CI wrapper's log",
  "endlegend",
  "@enduml",
);
fs.writeFileSync(out, lines.join("\n") + "\n");
console.log(`wrote ${out}`);
