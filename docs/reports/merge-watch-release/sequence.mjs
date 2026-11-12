// Writes a release run's PlantUML sequence diagram from the desk's trace of it, ARTIE's own capture:
//   node sequence.mjs TRACE.yaml OUT.puml "Title"
// Every request ARTIE sent is from the trace, numbered as its step, with what the step said. The parties' own messages
// to each other, which ARTIE never sees, are drawn apart and say where they come from: the answers to the desk's
// replies, read from each reply's change of state between two reads; the watch's looks; Jenkins' part, from the CI
// wrapper's log of what Jenkins told it.
import fs from "node:fs";
import vm from "node:vm";
import path from "node:path";

const [trace, out, title] = process.argv.slice(2);
const here = path.dirname(new URL(import.meta.url).pathname);
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(here, "../assets/js-yaml.min.js"), "utf8"), sandbox);
const steps = sandbox.jsyaml.loadAll(fs.readFileSync(trace, "utf8")).slice(1);

// The kinds of message, each its own colour, as the legend says.
const HEART = "#A80036", RISE = "#1B7F3B", OWN = "#1F4FB8";
const portOf = (url) => new URL(url).port;
const alias = (url) => `P${portOf(url)}`;
const short = (url) => { const u = new URL(url); return decodeURIComponent(u.pathname + u.search); };
const words = (text) => String(text).replace(/`/g, "").replace(/"/g, "'");
const STATUS = { 200: "200 OK", 201: "201 Created", 204: "204 No Content", 412: "412 Precondition Failed" };
const said = (step) => words(String(step.say || "").replace(/\{(\w+)\}/g, (_, k) => {
  const v = step.params?.[k];
  return v == null ? `{${k}}` : typeof v === "object" ? "…" : String(v).slice(0, 60);
}));

const lines = [
  "@startuml",
  "skin rose",
  "skinparam shadowing true",
  "skinparam defaultFontName Helvetica",
  "skinparam defaultFontSize 12",
  "skinparam sequenceMessageAlign left",
  "skinparam responseMessageBelowArrow true",
  "skinparam maxMessageSize 360",
  "skinparam ParticipantPadding 14",
  "skinparam BoxPadding 8",
  "skinparam NoteBackgroundColor #FFF9B0",
  "skinparam NoteBorderColor #A80036",
  "skinparam NoteFontSize 10",
  "skinparam SequenceGroupBodyBackGroundColor transparent",
  "skinparam SequenceDividerBackgroundColor #F2F2F2",
  "skinparam LegendBackgroundColor #F7F7F7",
  "skinparam LegendFontSize 12",
  `title <size:18>${title}</size>`,
  'box "The release desk: ARTIE and the replies it keeps" #EEEEEE',
  'actor "ARTIE\\n(in the desk)" as ARTIE',
  'participant "Desk replies\\n:8160" as P8160',
  "end box",
  'participant "GitHub\\n(gateway :8098)" as P8098',
  'participant "Offer watch\\n:8099" as P8099',
  'box "CI" #F4F4FA',
  'participant "CI wrapper\\n:8170" as P8170',
  'participant "Jenkins\\n:8171" as P8171',
  "end box",
  'participant "Preview\\n:8180" as P8180',
  'participant "Dev\\n:8190" as P8190',
];

const arrow = (from, to, color, text, reply = false) =>
  lines.push(`${from} -[${color}]${reply ? "->" : ">"} ${to} : ${text}`);
const own = (from, to, text) => arrow(from, to, OWN, text.split("\\n").map((l, i) => (i ? l : `<b>·</b> ${l}`)).join("\\n"));

/** The part of the release a request is in, by the party it went to and what it was for. */
const sectionOf = (step, ex) => {
  const port = portOf(ex.request.url), rel = step.params?.relation || "";
  if (port === "8180") return "Smoke test, the captured script, on the preview";
  if (port === "8190") return "Promote";
  if (rel === "github:comment") return "Stopped: say why on the pull request";
  if (port === "8170") return "Build, by Jenkins";
  if (port === "8099") return "Watch";
  if (rel === "github:merge" || section === "Watch") return "Merge";
  if (port === "8160") return section;
  return section || "Open the pull request";
};

const ORDER = [null, "Open the pull request", "Watch", "Merge", "Build, by Jenkins",
  "Smoke test, the captured script, on the preview", "Promote", "Stopped: say why on the pull request"];
let section = null, inLoop = false, watchShown = false, jenkinsShown = false, scenario = null;
const replyState = new Map();
const pull = steps.find((s) => s.params?.relation === "github:pulls-get")?.address;

steps.forEach((step, i) => {
  const n = i + 1;
  (step.exchanges || []).forEach((ex) => {
    const req = ex.request, res = ex.response || {};
    const body = typeof res.body === "object" && res.body ? res.body : {};
    const to = alias(req.url);
    // Sections only go forward, in the release's own order: a read of a party met again stays where it is.
    const found = sectionOf(step, ex);
    const now = ORDER.indexOf(found) > ORDER.indexOf(section) ? found : section;
    const waiting = to === "P8160" && req.method === "GET";
    if (inLoop && !waiting) { lines.push("end"); inLoop = false; }
    if (now && now !== section) { lines.push(`== ${now} ==`); section = now; }
    if (waiting && !inLoop) { lines.push("loop #E9F5EC until the reply is answered"); inLoop = true; }
    // A reply read in a new state was answered since: by the party its hr:in-reply-to names, signed.
    if (body.reply && body.state) {
      const key = `${body.state}/${body.percent ?? ""}`, before = replyState.get(body.reply);
      const by = body._links?.["hr:in-reply-to"]?.href;
      if (by && portOf(by) === "8099" && !watchShown) {
        watchShown = true;
        if (pull) own("P8099", "P8098", `GET ${short(pull)}\\nlooks every 3 s, until it offers github:merge`);
      }
      if (by && portOf(by) === "8170" && !jenkinsShown) {
        jenkinsShown = true;
        own("P8170", "P8171", "POST /job/app/buildWithParameters\\nSHA, RELEASE_BUILD, PREVIEW_PORT");
        own("P8171", "P8170", "POST /hooks/jenkins\\nQUEUED, STARTED (Notification plugin)");
      }
      if (before !== key && by && body.state !== "awaiting" || (before && before !== key && by)) {
        const rel = body.state === "completed" ? "hr:completed" : body.state === "failed" ? "hr:failed" : "hr:progress";
        if (rel === "hr:completed" && portOf(by) === "8170") {
          own("P8171", "P8180", "starts the library at the commit");
          own("P8171", "P8170", "POST /hooks/jenkins\\nCOMPLETED, FINALIZED: SUCCESS, with its artifacts");
        }
        const note = body.note ? `\\n'${words(body.note)}'` : "";
        arrow(alias(by), to, RISE, `<b>·</b> PATCH ${short(req.url)}\\n<b>${rel}</b>, signed by its key${note}`);
      }
      replyState.set(body.reply, key);
    }
    const color = to === "P8160" ? RISE : HEART;
    const envelope = typeof req.body === "string" && req.body.includes("hr:reply-to")
      ? "\\n<i>with </i><b>hr:reply-to</b><i>, </i><b>hr:keys</b><i>, signed by the desk</i>" : "";
    arrow("ARTIE", to, color, `<b>${n}</b> ${req.method} ${short(req.url)}${envelope}\\n<i>${said(step)}</i>`);
    const state = body.state ? ` <b>${body.state}</b>` : "";
    const expected = step.expect && step.refused ? ` <i>(expected: ${step.expect})</i>` : "";
    arrow(to, "ARTIE", color, `${STATUS[res.status] || res.status}${state}${expected}`, true);
  });
  if (step.say === "check" && step.outcome) {
    lines.push(`note over ARTIE #${step.outcome === "held" ? "E6F4EA" : "FDECEA"} : <b>${n}</b> check: ${step.outcome}`);
  }
});
if (inLoop) lines.push("end");
lines.push(
  "legend left",
  `  <color:${HEART}><b>Red</b></color>: ARTIE's HEART requests to each party, numbered as the steps of the desk's trace`,
  `  <color:${RISE}><b>Green</b></color>: RISE: the desk's replies, read until answered, and each party's signed answer`,
  `  <color:${OWN}><b>Blue</b></color>: the parties' own messages, which ARTIE never sees: the watch's looks, Jenkins' part`,
  "  Notes: the smoke script's checks, held or failed",
  "endlegend",
  "@enduml",
);
fs.writeFileSync(out, lines.join("\n") + "\n");
console.log(`wrote ${out}`);
