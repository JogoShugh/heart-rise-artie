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
// A new slide [n], a copy of slide [after] and its notes, shown right after it.
function addSlideAfter(after, n) {
  const copy = (from, to, change = (x) => x) =>
    writeFileSync(`${root}/ppt/${to}`, change(readFileSync(`${root}/ppt/${from}`, "utf8")));
  copy(`slides/slide${after}.xml`, `slides/slide${n}.xml`);
  copy(`slides/_rels/slide${after}.xml.rels`, `slides/_rels/slide${n}.xml.rels`,
    (x) => x.replace(`notesSlide${after}.xml`, `notesSlide${n}.xml`));
  copy(`notesSlides/notesSlide${after}.xml`, `notesSlides/notesSlide${n}.xml`);
  copy(`notesSlides/_rels/notesSlide${after}.xml.rels`, `notesSlides/_rels/notesSlide${n}.xml.rels`,
    (x) => x.replace(`slides/slide${after}.xml`, `slides/slide${n}.xml`));
  const rels = load("_rels/presentation.xml.rels");
  const rid = `rId${Math.max(...[...rels.xml.matchAll(/Id="rId(\d+)"/g)].map((m) => Number(m[1]))) + 1}`;
  const before = rels.xml.match(new RegExp(`Id="(rId\\d+)"[^>]*Target="slides/slide${after}.xml"`))[1];
  rels.xml = rels.xml.replace("</Relationships>",
    `<Relationship Id="${rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${n}.xml"/></Relationships>`);
  save(rels);
  const deck = load("presentation.xml");
  const id = Math.max(...[...deck.xml.matchAll(/<p:sldId id="(\d+)"/g)].map((m) => Number(m[1]))) + 1;
  deck.xml = deck.xml.replace(new RegExp(`(<p:sldId id="\\d+" r:id="${before}"/>)`), `$1<p:sldId id="${id}" r:id="${rid}"/>`);
  save(deck);
  const types = { path: `${root}/[Content_Types].xml`, xml: readFileSync(`${root}/[Content_Types].xml`, "utf8") };
  types.xml = types.xml.replace("</Types>",
    `<Override PartName="/ppt/slides/slide${n}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>` +
    `<Override PartName="/ppt/notesSlides/notesSlide${n}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"/></Types>`);
  save(types);
  const app = { path: `${root}/docProps/app.xml`, xml: readFileSync(`${root}/docProps/app.xml`, "utf8") };
  app.xml = app.xml.replace(/<Slides>(\d+)<\/Slides>/, (_, k) => `<Slides>${Number(k) + 1}</Slides>`)
    .replace(/<Notes>(\d+)<\/Notes>/, (_, k) => `<Notes>${Number(k) + 1}</Notes>`);
  save(app);
}


// Slide 30: the task as a plain goal, and Claude's run of it.
{
  const s = load("slides/slide30.xml");
  code(s, 5, [
    ["I", "# github-task.goal.md: the task in plain words (excerpt)"],
    ["P", "- In {owner}/{repo}"],
    ["B", "  - Open an issue titled \"Crash when a bed …\" `as issue`"],
    ["B", "- Open the branch main of {owner}/{repo}"],
    ["B", "  - Start a new branch from here, named fix-…-{stamp}"],
    ["B", "    - Commit a file named fix-steps-{stamp}.md saying …"],
    ["B", "    - Open a pull request into main titled \"Refuse a …\","],
    ["B", "      saying it relates to #{issue.number} `as pr`"],
    ["P", "      - Wait: the pull request can be merged"],
    ["B", "      - Merge it by squashing its commits"],
    ["P", "      - Verify: it merged"],
    ["P", "- On {issue}"],
    ["B", "  - Comment \"Fixed in {version} by #{pr.number}.\""],
    ["B", "  - Close it the right way: the fix shipped in {version}"],
    ["P", "  - Verify:"],
    ["P", "    - it is closed"],
    ["P", "    - its reason is completed"],
  ]);
  place(s, 4, { cy: 3.85 });
  place(s, 5, { cy: 3.55 });
  text(s, 27, ["23"]);
  text(s, 28, ["model calls: planning, then choosing"]);
  text(s, 31, ["90 s"]);
  save(s);
  notes(30, "Real run of github-task.goal.md, the task in plain words, against JogoShugh/heart-github-test through the gateway, 5 November 2026, given version=1.2: issue #2195, PR #2196, 25 steps, 90 seconds. 23 model calls (Claude Sonnet 5.5): the planner turns each line into moves, and the reasoner chooses among the forms and links the gateway offered; ARTIE checked each choice before sending. No script was written by hand: not Kotlin, not YAML. What varies is an input ({owner}, {repo}, {stamp}, {version}) or found on the walk and kept (as issue, as pr).");
}

// Slide 31: the kept script artie generate wrote from that run, as YAML, its lock file, and the replay.
{
  const s = load("slides/slide31.xml");
  code(s, 5, [
    ["I", "# github-task.heart.yaml (excerpt)"],
    ["B", "- act: github:open-pull-request"],
    ["P", "  fields:"],
    ["P", "    base: main"],
    ["P", "    title: Refuse a bed with no name (steps)"],
    ["P", "    body: \"Relates to #${issue.number}\""],
    ["P", "  then:"],
    ["P", "    - keep-as: pr"],
    ["P", "    - until:"],
    ["P", "        offered: github:merge"],
    ["B", "    - act: github:merge"],
    ["P", "…"],
    ["B", "- act: github:comment"],
    ["P", "  fields:"],
    ["P", "    body: \"Fixed in ${version} by #${pr.number}.\""],
  ]);
  place(s, 4, { cy: 3.4 });
  place(s, 5, { cy: 3.1 });
  code(s, 7, [
    ["I", "# github-task.pins.yaml (lock file)"],
    ["B", "github:merge:"],
    ["P", "  integrity: sha256-uJuM7+5OJxJsXKNcZdbk…"],
    ["P", "  act: sha256-rIPDcwcEW99MvpphQzxb…"],
  ]);
  place(s, 6, { y: 5.25 });
  place(s, 7, { y: 5.4 });
  cells(s, [
    [null, "From the goal", "Replayed"],
    ["Model calls", "23", "0"],
    ["Time", "90 s", "27 s"],
    ["Release, an input", "version=1.2", "version=1.3"],
    ["Each act checked", "against offers", "strictly, to lock"],
    ["Values to review", "—", "0 of 19"],
    ["Live result", "#2195, PR #2196", "#2197, PR #2198"],
  ]);
  text(s, 9, ["The lock file pins each form’s integrity, and its act with the target set aside. Given 1.3, the replay commented “Fixed in 1.3 by #2198.”"]);
  save(s);
  notes(31, "artie generate --format yaml read the capture of the goal run on slide 30 and wrote github-task.heart.yaml: every selection named, and every value that varies read again as a reference: inputs (${owner}, ${repo}, ${stamp}, ${version}) and what the walk found and kept (${issue.number}, ${pr.number}). 19 fields, 0 to review, with a lock file of what was offered. It ran live with no model, guard strict, given version=1.3, 5 November 2026: issue #2197, PR #2198, 27 seconds, 28 steps (after each act that changed what it acted on, the replay checks the change).");
}

// Slide 32: the October runs, said as such; the intent rung in plain words.
{
  const s = load("slides/slide32.xml");
  text(s, 13, ["Every move in plain words; Claude picks the form and fields from what’s offered."]);
  text(s, 24, ["All 9 runs finished: 9 issues opened and closed, 9 pull requests merged, every committed file read back from GitHub. Kotlin scripts, 18 October; from a plain goal to YAML on 5 November: 90 s, 27 s and 34 s."]);
  save(s);
  notes(32, "Live runs, 18 October 2026, scratch repository JogoShugh/heart-github-test, with the scripts in Kotlin. Ranges: scripted 17.6-18.2 s, intent 45.9-49.6 s, hybrid 24.2-30.1 s. Reasoner: Claude Sonnet 5.5. In every intent run Claude made the same ten choices. The same three on 5 November 2026, one live run each, with no script written by hand: the plain goal 90 s (23 model calls, planning and choosing), the kept YAML artie generate wrote from it 27 s (0), and the hybrid 34 s (1). Those times include a walk of the pages of open pull requests and branches the scratch repository now holds.");
}

// The follow-up to slide 33, made from it before it changes: every kind of closure named, the walk finds which.
addSlideAfter(33, 38);
{
  const s = load("slides/slide38.xml");
  text(s, 2, ["PREVIEW  ·  …OR NAME EVERY CASE"]);
  text(s, 3, ["Name every case; the walk finds which"]);
  code(s, 5, [
    ["I", "# closeout.goal.md (excerpt): every kind of closure, named"],
    ["P", "- Whenever an issue labelled {label} changes"],
    ["B", "  - If it's labelled {shipped}, close it as completed."],
    ["B", "  - If it's labelled {wontfix}, close it as not planned."],
    ["B", "  - If it's labelled {duplicate}, close it as a duplicate."],
    ["I", "# closeout.heart.yaml, as artie generate wrote it (excerpt)"],
    ["P", "- given:"],
    ["P", "    - field: github:labels"],
    ["P", "      has: ${wontfix}"],
    ["P", "  then:"],
    ["B", "    - act: github:close-not-planned"],
  ], 1100);
  place(s, 4, { cy: 2.4 });
  place(s, 5, { cy: 2.1 });
  cells(s, [
    ["Labelled", "Pinned for that case", "Closed as"],
    ["shipped", "github:comment, github:close", "completed"],
    ["wontfix", "github:close-not-planned", "not planned"],
    ["duplicate", "github:close-duplicate", "duplicate"],
  ]);
  text(s, 7, [
    "Three issues, one per label, so the walk met every case. Claude decided each If once, as a check on the issue’s labels, and artie generate kept each as a given, its close pinned.",
    null,
    "Replayed with no model on three new issues, release 1.4: each closed its own way. 0 model calls, 52 s.",
  ]);
  text(s, 9, ["The cases are in the words; which one applies is found on the walk. Labels and release are inputs."]);
  save(s);
  notes(38, "closeout.goal.md is a watch: whenever an issue labelled {label} changes, it closes the issue the way its labels say it ended; every label and the release are inputs. To meet every case, three issues were staged, one labelled shipped, one wontfix, one duplicate. Claude's run, live, 5 November 2026: 22 model calls, 132 s; #2207 closed as completed with \"Fixed in 1.3.\", #2208 as not planned, #2209 as a duplicate. artie generate wrote closeout.heart.yaml: each If a given on the issue's labels, each close pinned. Replayed with no model, guard strict, release 1.4, on three new issues: #2211 completed with \"Fixed in 1.4.\", #2212 not planned, #2213 duplicate; 52 s. A case the walk never met would stay in words for the model, so stage every case you mean to keep.");
}

// Slide 33: the hybrid, as the goal marks it.
{
  const s = load("slides/slide33.xml");
  code(s, 5, [
    ["I", "# github-task.hybrid.goal.md (excerpt)"],
    ["P", "- On {issue}"],
    ["P", "  - Comment \"Fixed in {version} by #{pr.number}.\""],
    ["B", "  - Close it the right way: the fix shipped in {version} `reasoned`"],
    ["I", "# github-task.hybrid.heart.yaml, as artie generate wrote it"],
    ["P", "- act: github:comment"],
    ["P", "  fields:"],
    ["P", "    body: \"Fixed in ${version} by #${pr.number}.\""],
    ["B", "- intent: \"Close it the right way: the fix shipped in ${version}\""],
    ["B", "  reasoned: true"],
  ], 1100);
  place(s, 4, { cy: 2.25 });
  place(s, 5, { cy: 1.95 });
  cells(s, [
    ["Hybrid run", "Claude chose", "Step time"],
    ["5 November", "github:close", "5.4 s"],
  ]);
  keepRows(s, 2);
  text(s, 7, [
    "GitHub offers three ways to close an issue: close (completed), close-not-planned and close-duplicate. Which one is right is a judgement about what happened, so the goal marks that one step reasoned, and artie generate keeps it in words.",
    null,
    "Claude chose github:close, and the check confirmed GitHub recorded it as completed. Every other step ran exactly as written, held strictly to the lock file.",
  ]);
  save(s);
  notes(33, "The hybrid comes from the goal: the close step is marked `reasoned`, so artie generate keeps it as a reason step and pins the rest. Goal run, version=1.2: issue #2215, PR #2216. The hybrid replay, live, version=1.3, 5 November 2026: issue #2217, PR #2218, 34 s, one model call; the close step took 5.4 s, the model's choice and GitHub's answer together. The next slide removes the judgement: name every case, and let the walk find which.");
}

// Slide 34 (35 once the new slide is in): the words come first, in a plain goal.
{
  const s = load("slides/slide34.xml");
  text(s, 5, [null, "the task in plain words, a goal; the model maps each line to the forms offered"]);
  text(s, 18, ["Hybrid: pin the routine, mark the one step that needs judgement reasoned; or name every case."]);
  save(s);
}

// Slide 37 (38): where the runs are.
{
  const s = load("slides/slide37.xml");
  text(s, 4, [null, null, null, null, null,
    "Three ways through GitHub: scripted, intent and hybrid runs in Kotlin, 3 live runs each, issues #49–#65 and PRs #50–#66, 18 October 2026; gateway e171027, ARTIE f494ff4, reasoner Claude Sonnet 5.5.",
    "From a plain goal to YAML, 5 November 2026, on JogoShugh/heart-github-test: github-task.goal.md (#2195, PR #2196) and its kept YAML replayed strictly (#2197, PR #2198); the hybrid (#2215–#2218); the close-out watch (#2207–#2209) and its replay with no model (#2211–#2213). Scripts and runs: exhibits/github-task-yaml.",
  ]);
  save(s);
}
console.log("edited");
