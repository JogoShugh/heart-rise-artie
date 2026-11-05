// Edits Take HEART's GitHub slides in place, in the unpacked deck at argv[2]: the Kotlin scripts become the YAML ones,
// with the YAML runs' numbers. Slide XML is changed by shape id; every other byte stays as it was.
import { readFileSync, writeFileSync } from "node:fs";
const root = process.argv[2];
const EMU = 914400;
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const load = (path) => ({ path: `${root}/ppt/${path}`, xml: readFileSync(`${root}/ppt/${path}`, "utf8") });
const save = (doc) => writeFileSync(doc.path, doc.xml);

function shapeOf(doc, id) {
  const all = doc.xml.match(/<p:sp>[\s\S]*?<\/p:sp>/g);
  const sp = all.find((s) => new RegExp(`<p:cNvPr id="${id}" `).test(s));
  if (!sp) throw new Error(`no shape ${id} in ${doc.path}`);
  return sp;
}
function replaceShape(doc, id, change) {
  const sp = shapeOf(doc, id);
  doc.xml = doc.xml.replace(sp, change(sp));
}
// Moves or resizes a shape, in inches; a value left out stays.
function place(doc, id, { y, cy }) {
  replaceShape(doc, id, (sp) => sp.replace(/<a:off x="(\d+)" y="(\d+)"\/><a:ext cx="(\d+)" cy="(\d+)"\/>/, (_, x, oy, cx, ocy) =>
    `<a:off x="${x}" y="${y != null ? Math.round(y * EMU) : oy}"/><a:ext cx="${cx}" cy="${cy != null ? Math.round(cy * EMU) : ocy}"/>`));
}
// A code box's lines, each [style, text]: I a comment, B a line in bold, P plain; styles taken from the box's own lines.
function code(doc, id, lines, size) {
  replaceShape(doc, id, (sp) => {
    const paras = sp.match(/<a:p>[\s\S]*?<\/a:p>/g);
    const styleOf = (p) => (/ i="1"/.test(p) ? "I" : / b="1"/.test(p) ? "B" : "P");
    const pick = (s) => paras.find((p) => styleOf(p) === s) ?? paras.find((p) => styleOf(p) === "P");
    const made = lines.map(([s, text]) => {
      let p = pick(s).replace(/<a:t>[^<]*<\/a:t>/, `<a:t>${esc(text)}</a:t>`);
      if (size) p = p.replace(/sz="\d+"/g, `sz="${size}"`);
      return p;
    }).join("");
    const first = sp.indexOf("<a:p>"), last = sp.lastIndexOf("</a:p>") + "</a:p>".length;
    return sp.slice(0, first) + made + sp.slice(last);
  });
}
// A text shape's paragraphs, each the first run of the paragraph it replaces, holding the new text.
function text(doc, id, texts) {
  replaceShape(doc, id, (sp) => {
    let i = 0;
    return sp.replace(/<a:p>[\s\S]*?<\/a:p>/g, (p) => {
      const t = texts[i++];
      if (t == null) return p;
      const runs = p.match(/<a:r>[\s\S]*?<\/a:r>/g);
      if (!runs) return p;
      const one = runs[0].replace(/<a:t>[^<]*<\/a:t>/, `<a:t>${esc(t)}</a:t>`);
      return p.replace(runs.join(""), one).replace(/(<a:r>[\s\S]*?<\/a:r>)(?:<a:r>[\s\S]*?<\/a:r>)+/, "$1");
    });
  });
}
// The first table's cells, by row and column from 0, each given text.
function cells(doc, rows) {
  const frame = doc.xml.match(/<p:graphicFrame>[\s\S]*?<\/p:graphicFrame>/)[0];
  let r = 0;
  const changed = frame.replace(/<a:tr [\s\S]*?<\/a:tr>/g, (tr) => {
    const row = rows[r++];
    if (!row) return tr;
    let c = 0;
    return tr.replace(/<a:tc[\s\S]*?<\/a:tc>/g, (tc) => {
      const t = row[c++];
      if (t == null) return tc;
      let first = true;
      return tc.replace(/<a:t>[^<]*<\/a:t>/g, () => (first ? ((first = false), `<a:t>${esc(t)}</a:t>`) : "<a:t></a:t>"));
    });
  });
  doc.xml = doc.xml.replace(frame, changed);
}
// A table's last rows taken away, keeping [keep] rows; its frame's height shrinks with them.
function keepRows(doc, keep) {
  const frame = doc.xml.match(/<p:graphicFrame>[\s\S]*?<\/p:graphicFrame>/)[0];
  const rows = frame.match(/<a:tr [\s\S]*?<\/a:tr>/g);
  let changed = frame;
  rows.slice(keep).forEach((tr) => { changed = changed.replace(tr, ""); });
  doc.xml = doc.xml.replace(frame, changed);
}
// A notes page's words.
function notes(n, words) {
  const doc = load(`notesSlides/notesSlide${n}.xml`);
  const runs = [...doc.xml.matchAll(/<a:t>([^<]*)<\/a:t>/g)].filter((m) => m[1].length > 3);
  doc.xml = doc.xml.replace(runs[0][0], `<a:t>${esc(words)}</a:t>`);
  runs.slice(1).forEach((m) => { doc.xml = doc.xml.replace(m[0], "<a:t></a:t>"); });
  save(doc);
}

// Slide 30: the task in words, as YAML, and its live run.
{
  const s = load("slides/slide30.xml");
  code(s, 5, [
    ["I", "# github-task.intent.heart.yaml (excerpt)"],
    ["P", "steps:"],
    ["B", "  - intent: open the repository ${owner}/${repo}"],
    ["P", "    then:"],
    ["B", "      - intent: open an issue titled \"Crash when a bed …\""],
    ["P", "        then:"],
    ["P", "          - keep-as: issue"],
    ["B", "  - intent: open the branch main of ${owner}/${repo}"],
    ["P", "    then:"],
    ["B", "      - intent: start a new branch from here, …"],
    ["P", "        then:"],
    ["B", "          - intent: commit a file named …"],
    ["B", "          - intent: \"open a pull request into main …\""],
    ["P", "            then:"],
    ["P", "              - keep-as: pull"],
    ["P", "              - until:"],
    ["P", "                  offered: github:merge"],
    ["B", "              - intent: merge it, squashing its commits"],
    ["B", "  - intent: open issue ${issue.number} in ${owner}/${repo}"],
    ["P", "    then:"],
    ["B", "      - intent: \"comment on it: Fixed in 1.2 by #${pull.number}.\""],
    ["B", "      - intent: close it, since the fix is done"],
  ], 1100);
  place(s, 4, { cy: 4.35 });
  place(s, 5, { cy: 4.05 });
  text(s, 31, ["53 s"]);
  save(s);
  notes(30, "Real run of github-task.intent.heart.yaml, a script held as data, against JogoShugh/heart-github-test through the gateway, 5 November 2026: issue #2174, PR #2175, 24 steps. artie run runs the YAML directly: no Kotlin, no compiler. Each intent step is one model call (Claude Sonnet 5.5), choosing among the forms and links the gateway offered; ARTIE checked each choice before sending. Two more runs that day took 51 and 54 s, with the same ten choices.");
}

// Slide 31: the kept script as YAML, its lock file, and the replay.
{
  const s = load("slides/slide31.xml");
  code(s, 5, [
    ["I", "# github-task.heart.yaml (excerpt)"],
    ["B", "- act: github:open-pull-request"],
    ["P", "  fields:"],
    ["P", "    base: main"],
    ["P", "    title: Refuse a bed with no name (reasoned)"],
    ["P", "    body: \"Relates to #${issue.number}\""],
    ["P", "  then:"],
    ["P", "    - sync: true"],
    ["P", "    - keep-as: pull"],
    ["P", "    - until:"],
    ["P", "        offered: github:merge"],
    ["B", "    - act: github:merge"],
    ["P", "      fields:"],
    ["P", "        merge_method: squash"],
  ]);
  place(s, 4, { cy: 3.2 });
  place(s, 5, { cy: 2.9 });
  code(s, 7, [
    ["I", "# github-task.pins.yaml (lock file)"],
    ["B", "github:merge:"],
    ["P", "  integrity: sha256-uJuM7+5OJxJsXKNcZdbk…"],
    ["P", "  act: sha256-rIPDcwcEW99MvpphQzxb…"],
  ]);
  place(s, 6, { y: 5.1 });
  place(s, 7, { y: 5.25 });
  cells(s, [
    [null, "Reasoned", "Replayed"],
    ["Model calls", "10", "0"],
    ["Time", "53 s", "32 s"],
    ["Steps", "24", "28, with 2 more checks"],
    ["Each act checked", "against offers", "strictly, to lock"],
    ["Values to review", "—", "0 of 19"],
    ["Live result", "#2174, PR #2175", "#2178, PR #2179"],
  ]);
  text(s, 9, ["The lock file pins each form’s integrity, and its act with the target set aside. A strict replay refuses a form no longer offered, or changed in any way."]);
  save(s);
  notes(31, "artie generate --format yaml read the capture of a reasoned run of this task (issue #2172, PR #2173) and wrote the script as data: every selection named, each value read again as a reference (${issue.number}, ${pull.number}, ${stamp}), 19 fields and 0 to review, with a lock file of what was offered. It ran live with no model, guard strict, 5 November 2026: issue #2178, PR #2179, 32 seconds. After each act that changed what it acted on, the replay checks the change: two more steps than the reasoned run. A run of a YAML script doesn't yet record enough for artie generate to read its values back as references, so the kept script was generated from a run of the same steps written in Kotlin.");
}

// Slide 32: the October runs, said as such; the intent rung in YAML's words.
{
  const s = load("slides/slide32.xml");
  text(s, 13, ["Every move said in words (intent:); Claude picks the form and fields from what’s offered."]);
  text(s, 24, ["All 9 runs finished: 9 issues opened and closed, 9 pull requests merged, every committed file read back from GitHub. Kotlin scripts, 18 October; in YAML on 5 November: 53 s, 32 s and 38 s."]);
  save(s);
  notes(32, "Live runs, 18 October 2026, scratch repository JogoShugh/heart-github-test, with the scripts in Kotlin. Ranges: scripted 17.6-18.2 s, intent 45.9-49.6 s, hybrid 24.2-30.1 s. Reasoner: Claude Sonnet 5.5. In every intent run Claude made the same ten choices. The same three as YAML, one live run each on 5 November 2026: intent 53 s (10 model calls), kept 32 s (0), hybrid 38 s (1). Those times include a walk of three pages of open pull requests the scratch repository now holds.");
}

// Slide 33: the hybrid as YAML.
{
  const s = load("slides/slide33.xml");
  code(s, 5, [
    ["I", "# github-task.hybrid.heart.yaml (excerpt)"],
    ["P", "- act: github:issues-get"],
    ["P", "  fields:"],
    ["P", "    issue_number: ${issue.number}"],
    ["P", "  then:"],
    ["P", "    - act: github:comment"],
    ["P", "      fields:"],
    ["P", "        body: \"Fixed in 1.2 by #${pull.number}.\""],
    ["B", "    - intent: \"close it the right way: the fix shipped in 1.2\""],
    ["P", "    - check:"],
    ["P", "        - field: state_reason"],
    ["P", "          is: completed"],
  ], 1100);
  place(s, 4, { cy: 2.55 });
  place(s, 5, { cy: 2.25 });
  cells(s, [
    ["Hybrid run", "Claude chose", "Step time"],
    ["5 November", "github:close", "9.1 s"],
  ]);
  keepRows(s, 2);
  text(s, 7, [
    "GitHub offers three ways to close an issue: close (completed), close-not-planned and close-duplicate. Which one is right is a judgement about what happened, so that one step is left as intent.",
    null,
    "Claude chose github:close, and the check confirmed GitHub recorded it as completed. Every other step ran exactly as written, held strictly to the lock file.",
  ]);
  save(s);
  notes(33, "The hybrid is the kept YAML that artie generate wrote, with one step put back in words: intent: instead of act: github:close. Live, 5 November 2026: issue #2180, PR #2181, 38 s, one model call; the close step took 9.1 s, the model's choice and GitHub's answer together. In October's three Kotlin hybrid runs, the model's one call took 5.5 to 9.8 seconds.");
}

// Slide 34: YAML's words for an intent step.
{
  const s = load("slides/slide34.xml");
  text(s, 5, [null, "intent: … for each step; the model maps words to the forms offered"]);
  text(s, 18, ["Hybrid: pin the routine, keep intent: … for the call that needs judgement."]);
  save(s);
}

// Slide 37: where the YAML runs are.
{
  const s = load("slides/slide37.xml");
  text(s, 4, [null, null, null, null, null,
    "Three ways through GitHub: scripted, intent and hybrid runs in Kotlin, 3 live runs each, issues #49–#65 and PRs #50–#66, 18 October 2026; gateway e171027, ARTIE f494ff4, reasoner Claude Sonnet 5.5.",
    "GitHub preview, as YAML: github-task.intent.heart.yaml (issue #2174, PR #2175), github-task.heart.yaml replayed strictly with its lock file (#2178, PR #2179), and github-task.hybrid.heart.yaml (#2180, PR #2181), on JogoShugh/heart-github-test, 5 November 2026. Scripts and runs: exhibits/github-task-yaml.",
  ]);
  save(s);
}
console.log("edited");
