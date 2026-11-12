// Writes a run's PlantUML sequence diagram from its trace: node sequence.mjs TRACE.yaml OUT.puml "Title"
// Every message ARTIE sent is from the trace, numbered as its step. The parties' own messages to each other, which
// ARTIE never sees, come from elsewhere and say so: the answers to ARTIE's replies, read from each reply's change of
// state between two reads; the watch's looks; Jenkins' part, from the CI wrapper's log of what Jenkins told it.
import fs from "node:fs";
import vm from "node:vm";
import path from "node:path";

const [trace, out, title] = process.argv.slice(2);
const here = path.dirname(new URL(import.meta.url).pathname);
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(here, "../assets/js-yaml.min.js"), "utf8"), sandbox);
const steps = sandbox.jsyaml.load(fs.readFileSync(trace, "utf8")).steps;

// The kinds of message, each its own colour, as the legend says.
const HEART = "#A80036", RISE = "#1B7F3B", OWN = "#1F4FB8";
const portOf = (url) => new URL(url).port;
const alias = (url) => `P${portOf(url)}`;
const short = (url) => { const u = new URL(url); return u.pathname + u.search; };
const words = (text) => text.replace(/`/g, "").replace(/"/g, "'");
const STATUS = { 200: "200 OK", 201: "201 Created", 204: "204 No Content", 412: "412 Precondition Failed" };

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
  'box "ARTIE and the replies it makes" #EEEEEE',
  'actor ARTIE as ARTIE',
  'participant "ARTIE\'s replies\\n:8160" as P8160',
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
const own = (from, to, text) => arrow(from, to, OWN, text.split("\\n").map((l, i) => i ? l : `<b>·</b> ${l}`).join("\\n"));
const note = (over, text) => lines.push(`note right of ${over}`, ...text, "end note");

const sectionOf = (i) => {
  const say = steps[i].say || "";
  if (/makes its reply/.test(say) && i + 1 < steps.length) return sectionOf(i + 1);
  if (/^S\d/.test(say)) return "Smoke test, on the preview";
  if (/comment/i.test(say)) return "Stopped: say why on the pull request";
  if (/Dev|environments/.test(say)) return "Promote";
  if (/CI wrapper|builds commit|Once \{build\}|smoke script/.test(say)) return "Build, by Jenkins";
  if (/Once \{watch\}|offer watch|asks to be told/.test(say)) return "Watch";
  if (/merg|follows the pull request/.test(say)) return "Merge";
  return "Open the pull request";
};

/** The links an answer hands over: in its result, else its own, but not its plumbing. */
const handed = (body) => {
  const links = { ...(body?.result?._links || {}) };
  for (const [rel, link] of Object.entries(body?._links || {})) {
    if (/^hr:(in-reply-to)$/.test(rel)) links[rel] = link;
  }
  return Object.entries(links).filter(([, l]) => l && l.href).map(([rel, l]) => `<b>_links.${rel}</b> = ${short(l.href.startsWith("/") ? `http://x${l.href}` : l.href)}`);
};

let section = null, inLoop = false, watchShown = false, jenkinsShown = false;
const replyState = new Map();
const pull = steps.find((s) => /opens pull request/.test(s.say || ""));

steps.forEach((step, i) => {
  const n = i + 1, req = step.request, res = step.response || {}, say = step.say || "", body = res.body || {};
  const now = sectionOf(i);
  const waiting = /^Once \{/.test(say);
  if (inLoop && !waiting) { lines.push("end"); inLoop = false; }
  if (now !== section) { lines.push(`== ${now} ==`); section = now; }
  if (waiting && !inLoop) {
    lines.push(`loop #E9F5EC until the reply is answered`);
    inLoop = true;
  }
  const to = alias(req.url);
  // A reply read in a new state was answered since: by the party its hr:in-reply-to names, signed.
  if (body.reply && body.state) {
    const key = `${body.state}/${body.percent ?? ""}`, before = replyState.get(body.reply);
    const by = body._links?.["hr:in-reply-to"]?.href;
    if (before && before !== key && by) {
      const rel = body.state === "completed" ? "hr:completed" : body.state === "failed" ? "hr:failed" : "hr:progress";
      if (rel === "hr:completed" && portOf(by) === "8170") {
        own("P8171", "P8180", "starts the library at the commit");
        own("P8171", "P8170", "POST /hooks/jenkins\\nCOMPLETED, FINALIZED: SUCCESS, with its artifacts");
      }
      const said = body.note ? `\\n"${words(body.note)}"` : "";
      arrow(alias(by), to, RISE, `<b>·</b> PATCH ${short(req.url)}\\n<b>${rel}</b>, signed by its key${said}`);
    }
    replyState.set(body.reply, key);
  }
  if (/Once \{watch\}/.test(say) && !watchShown) {
    watchShown = true;
    own("P8099", "P8098", `GET ${short(pull.request.url)}\\nlooks every 3 s, until it offers github:merge`);
  }
  if (/Once \{build\}/.test(say) && !jenkinsShown) {
    jenkinsShown = true;
    own("P8170", "P8171", "POST /job/app/buildWithParameters\\nSHA, RELEASE_BUILD, PREVIEW_PORT");
    own("P8171", "P8170", "POST /hooks/jenkins\\nQUEUED, STARTED (Notification plugin)");
  }
  const color = to === "P8160" ? RISE : HEART;
  const envelope = req.body?._links?.["hr:reply-to"] ? "\\n<i>with </i><b>hr:reply-to</b><i>, </i><b>hr:keys</b>" : "";
  arrow("ARTIE", to, color, `<b>${n}</b> ${req.method} ${short(req.url)}${envelope}\\n<i>${words(say)}</i>`);
  const state = body.state ? ` <b>${body.state}</b>` : "";
  arrow(to, "ARTIE", color, `${STATUS[res.status] || res.status}${state}`, true);
  const links = handed(body);
  const location = res.headers?.Location;
  if (location) links.unshift(`<b>Location</b> = ${short(location.startsWith("/") ? `http://x${location}` : location)}`);
  if (links.length && (res.status === 201 || body.state === "completed" || body.state === "failed")) note(to, links);
  if (step.summary) lines.push(`note over ARTIE #FFF9B0 : <b>${words(step.summary)}</b>`);
});
if (inLoop) lines.push("end");
lines.push(
  "legend left",
  `  <color:${HEART}><b>Red</b></color>: ARTIE's HEART requests to each party, numbered as the steps of its trace, and their answers`,
  `  <color:${RISE}><b>Green</b></color>: RISE: ARTIE's replies, read until answered, and each party's signed answer to them`,
  `  <color:${OWN}><b>Blue</b></color>: the parties' own messages, which ARTIE never sees: the watch's looks, Jenkins' part`,
  "  Yellow notes: the links an answer handed over; every address after an entry came from one",
  "endlegend",
  "@enduml",
);
fs.writeFileSync(out, lines.join("\n") + "\n");
console.log(`wrote ${out}`);
