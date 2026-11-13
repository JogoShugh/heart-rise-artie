// How far can one Web architect's vision go? Built in the style of Take HEART 1.x.
const pptxgen = require("pptxgenjs");
const { applyTheme } = require(process.env.APPLY_THEME);
const IMG = __dirname + "/img/";

const THEME = {
  name: "Take HEART",
  headFontFace: "Cambria",
  bodyFontFace: "Calibri",
  colors: {
    dk1: "17201B", lt1: "FFFFFF", dk2: "2C5E45", lt2: "EDF2EE",
    accent1: "2F7D55", accent2: "B5562B", accent3: "3D6B8C", accent4: "E3B341",
    accent5: "6F8A7C", accent6: "C9D8CE", hlink: "2F7D55", folHlink: "2C5E45",
  },
};
const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";
pres.theme = { headFontFace: "Cambria", bodyFontFace: "Calibri" };
pres.title = "How far can one Web architect's vision go?";
const C = pres.SchemeColor;
const INK = C.text1, GREEN = C.accent1, RUST = C.accent2, GOLD = C.accent4, SAGE = C.accent5, PALE = C.background2;
const FOOT = "How far can one vision go  ·  Hypertext, inference and hooks";

pres.defineSlideMaster({
  title: "LIGHT",
  background: { color: C.background1 },
  objects: [{ text: { text: FOOT, options: { x: 0.6, y: 7.0, w: 8, h: 0.25, fontSize: 9, color: SAGE } } }],
  slideNumber: { x: 12.2, y: 7.0, w: 0.5, h: 0.25, fontSize: 9, color: SAGE, align: "right" },
  placeholders: [],
});
pres.defineSlideMaster({
  title: "DARK",
  background: { color: C.text1 },
  slideNumber: { x: 12.2, y: 7.0, w: 0.5, h: 0.25, fontSize: 9, color: SAGE, align: "right" },
});

let n = 0;
function slide(kicker, title, dark = false, notes = "") {
  const s = pres.addSlide({ masterName: dark ? "DARK" : "LIGHT" });
  n++;
  s.addText(kicker.toUpperCase(), { x: 0.6, y: 0.32, w: 12.1, h: 0.32, fontSize: 12, bold: true, color: dark ? GOLD : GREEN, charSpacing: 2, margin: 0, isTextBox: true });
  if (title) s.addText(title, { x: 0.6, y: 0.62, w: 12.1, h: 0.8, fontSize: 34, bold: true, fontFace: "Cambria", color: dark ? C.background1 : INK, margin: 0, valign: "middle", isTextBox: true });
  if (notes) s.addNotes(notes);
  return s;
}
const T = (s, text, o) => s.addText(text, { margin: 0, isTextBox: true, fontSize: 15, color: INK, valign: "top", ...o });
function card(s, x, y, w, h, fill = PALE) {
  s.addShape(pres.shapes.RECTANGLE, { x, y, w, h, fill: { color: fill }, line: { color: fill } });
}
function pill(s, x, y, w, text, fill = GREEN, color = C.background1, h = 0.36) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.18, fill: { color: fill }, line: { color: fill } });
  s.addText(text, { x, y, w, h, fontSize: 12, bold: true, color, align: "center", valign: "middle", margin: 0, isTextBox: true });
}
function code(s, x, y, w, h, lines, size = 12) {
  s.addShape(pres.shapes.RECTANGLE, { x, y, w, h, fill: { color: C.text1 }, line: { color: C.text1 } });
  s.addText(lines.map(([style, t], i) => ({
    text: t,
    options: { breakLine: i < lines.length - 1, color: style === "c" ? SAGE : style === "k" ? GOLD : style === "r" ? "E07A5F" : style === "g" ? "7BC79A" : "E8EFEA", italic: style === "c", bold: style === "k" },
  })), { x: x + 0.2, y: y + 0.15, w: w - 0.4, h: h - 0.3, fontFace: "Courier New", fontSize: size, valign: "top", margin: 0, isTextBox: true });
}
function quote(s, x, y, w, h, text, cite, dark, size = 22) {
  T(s, "\u201C", { x: x - 0.65, y: y - 0.35, w: 0.8, h: 0.9, fontSize: 72, fontFace: "Cambria", color: GOLD });
  T(s, text, { x, y, w, h, fontSize: size, italic: true, fontFace: "Cambria", color: dark ? C.background1 : INK });
  T(s, cite, { x, y: y + h + 0.1, w, h: 0.4, fontSize: 13, color: SAGE });
}
function shot(s, file, x, y, w, h, caption) {
  s.addShape(pres.shapes.RECTANGLE, { x: x - 0.04, y: y - 0.04, w: w + 0.08, h: h + 0.08, fill: { color: C.accent6 }, line: { color: C.accent6 } });
  s.addImage({ path: IMG + file, x, y, w, h });
  if (caption) T(s, caption, { x, y: y + h + 0.1, w, h: 0.3, fontSize: 11, color: SAGE, italic: true });
}
function bullets(s, items, o) {
  s.addText(items.map((t, i) => {
    const runs = Array.isArray(t) ? t : [{ text: t }];
    return runs.map((r, j) => ({ text: r.text, options: { bullet: Array.isArray(t) ? undefined : (j === 0 ? { indent: 15 } : undefined), bold: r.b, color: r.color, breakLine: j === runs.length - 1 && i < items.length - 1, paraSpaceAfter: 8 } }));
  }).flat(), { margin: 0, isTextBox: true, fontSize: 15, color: INK, valign: "top", ...o });
}
function stat(s, x, y, w, big, label, color = GREEN) {
  T(s, big, { x, y, w, h: 0.8, fontSize: 44, bold: true, fontFace: "Cambria", color });
  T(s, label, { x, y: y + 0.85, w, h: 0.7, fontSize: 13, color: SAGE });
}
const arrow = (s, x, y) => s.addShape(pres.shapes.RIGHT_ARROW, { x, y, w: 0.4, h: 0.32, fill: { color: SAGE }, line: { color: SAGE } });

// 1. Title
{
  const s = slide("Hypertext, inference and hooks", null, true,
    "The question for the next half hour: if we take one Web architect's 2008 vision at its word, with today's tools, how far does it go? Two halves: running (hypertext lets a model find a path once, then a script replays it), and building (hooks put determinism around the model that wrote the code).");
  T(s, "How far can one Web architect\u2019s vision go?", { x: 0.6, y: 1.5, w: 12.1, h: 1.6, fontSize: 42, bold: true, fontFace: "Cambria", color: C.background1 });
  T(s, "Hypertext, inference, and hooks: determinism around a model.", { x: 0.6, y: 3.2, w: 10, h: 0.6, fontSize: 20, color: C.accent6 });
  ["Look", "Think", "Act"].forEach((t, i) => pill(s, 0.6 + i * 1.75, 4.3, 1.55, t, i === 1 ? C.accent6 : GREEN, i === 1 ? INK : C.background1));
  T(s, "One loop, for a person and for an agent.", { x: 6.0, y: 4.33, w: 5, h: 0.3, fontSize: 13, italic: true, color: C.accent6 });
}

// 2. Walk the dog
{
  const s = slide("Framing  ·  How this was built", "Walk the dog, ship the feature", false,
    "The honest frame. Free time collapsed: family, a new dog. Typing code stopped being possible. What remained: Claude Code's Remote Control on a phone, a personal Max account, and a test-first discipline strong enough that the code can be trusted while I'm out on a walk. The humor is real: the messages really were that short.");
  T(s, "When there\u2019s no time left to type code: family, a new dog, a phone in one hand and a leash in the other.", { x: 0.6, y: 1.7, w: 6.4, h: 0.9, fontSize: 18 });
  bullets(s, [
    [{ text: "Claude Code Remote Control ", b: true }, { text: "on the phone, a personal Max account at home." }],
    [{ text: "Test first, every change: ", b: true }, { text: "Gherkin, a red run, the code, a green run, a gated commit." }],
    [{ text: "Evidence, not trust: ", b: true }, { text: "the gates log every step, so what passed is on record when I get home." }],
    [{ text: "The result: ", b: true }, { text: "repeatable, reliable RISE behaviour, built a few sentences at a time." }],
  ], { x: 0.6, y: 2.8, w: 6.4, h: 3 });
  // phone
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 8.4, y: 1.55, w: 3.4, h: 5.2, rectRadius: 0.35, fill: { color: C.text1 }, line: { color: C.text1 } });
  s.addShape(pres.shapes.RECTANGLE, { x: 8.6, y: 1.95, w: 3.0, h: 4.45, fill: { color: PALE }, line: { color: PALE } });
  T(s, "Remote Control", { x: 8.6, y: 2.0, w: 3.0, h: 0.3, fontSize: 11, bold: true, color: SAGE, align: "center" });
  const bubble = (y, t) => { s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 9.6, y, w: 1.85, h: 0.6, rectRadius: 0.15, fill: { color: GREEN }, line: { color: GREEN } }); T(s, t, { x: 9.68, y: y + 0.05, w: 1.7, h: 0.5, fontSize: 12, color: C.background1, valign: "middle" }); };
  bubble(2.5, "Please give me an update");
  bubble(4.3, "How\u2019s it going?");
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 8.75, y: 3.25, w: 2.4, h: 0.85, rectRadius: 0.15, fill: { color: C.background1 }, line: { color: C.accent6 } });
  T(s, "Red run logged. Writing the fix.", { x: 8.85, y: 3.3, w: 2.2, h: 0.75, fontSize: 12, valign: "middle" });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 8.75, y: 5.05, w: 2.4, h: 0.85, rectRadius: 0.15, fill: { color: C.background1 }, line: { color: C.accent6 } });
  T(s, "Green. pre-commit: TDD ordering check passed.", { x: 8.85, y: 5.1, w: 2.2, h: 0.75, fontSize: 12, valign: "middle" });
  T(s, "Illustrative, not a transcript. The two questions are the real ones, every evening.", { x: 0.6, y: 6.3, w: 7, h: 0.3, fontSize: 11, italic: true, color: SAGE });
}

// 3. Frustration
{
  const s = slide("20 October 2008  ·  Fielding\u2019s frustration", "Our question: how far can we take his vision today?", true,
    "Not 'who got REST wrong'. The question is constructive: take the 2008 post at its word, with today's models and tools, and see how far it goes.");
  quote(s, 1.3, 1.95, 5.6, 1.5, "I am getting frustrated by the number of people calling any HTTP-based interface a REST API.", "Roy T. Fielding, \u201CREST APIs must be hypertext-driven\u201D, 20 Oct 2008", true, 24);
  T(s, "Not \u201Cwho got it wrong\u201D. How far can we go if we take one Web architect\u2019s vision at its word, with the tools we have now?", { x: 1.3, y: 4.3, w: 5.6, h: 1.2, fontSize: 17, color: C.accent6 });
  shot(s, "fielding-crop.png", 7.5, 1.9, 5.2, 5.2 * 440 / 758);
}

// 4. HATEOAS
{
  const s = slide("Why the Web scaled", "HATEOAS: Fielding\u2019s explanation, not his invention", false,
    "Hypermedia as the engine of application state is the name the 2000 dissertation gave to what the Web already did. It explains why one generic program, the browser, could use every site. At the application layer, it is arguably the most depended-upon and consequential network constraint of all time, for the human web.");
  card(s, 0.6, 1.75, 5.9, 4.6);
  T(s, "A description, not a proposal", { x: 0.9, y: 2.0, w: 5.3, h: 0.4, fontSize: 20, bold: true, fontFace: "Cambria", color: GREEN });
  bullets(s, [
    "Hypermedia as the engine of application state: the name his dissertation (2000) gave to why the Web scaled so fast.",
    "Every page carries its own next steps, its links and forms.",
    "So a browser needs no prior knowledge of any site, and never ships an update when a site changes.",
  ], { x: 0.9, y: 2.6, w: 5.3, h: 3.5 });
  s.addShape(pres.shapes.RECTANGLE, { x: 7.0, y: 1.75, w: 5.7, h: 4.6, fill: { color: C.text1 }, line: { color: C.text1 } });
  T(s, "At the application layer, perhaps the most depended-upon and consequential network constraint of all time", { x: 7.35, y: 2.1, w: 5.0, h: 2.0, fontSize: 22, bold: true, fontFace: "Cambria", color: C.background1 });
  T(s, "for the human web.", { x: 7.35, y: 4.2, w: 5.0, h: 0.5, fontSize: 22, bold: true, fontFace: "Cambria", color: GOLD });
  T(s, "Every link followed and every form sent, by billions of people, every day.", { x: 7.35, y: 5.1, w: 5.0, h: 0.8, fontSize: 14, color: C.accent6 });
}

// 5. Web made LLMs
{
  const s = slide("Why this team cares", "The Web made LLMs possible, and only half of it", false,
    "Brief. The same architecture made a crawlable corpus. Filtered Common Crawl was the largest share of GPT-3's training mix. But crawlers only read: they follow links, they never submit forms. The transactional half of the Web was never in the corpus.");
  const box = (x, head, body, fill, color) => { card(s, x, 1.9, 3.6, 3.2, fill); T(s, head, { x: x + 0.3, y: 2.15, w: 3.0, h: 0.5, fontSize: 20, bold: true, fontFace: "Cambria", color }); T(s, body, { x: x + 0.3, y: 2.8, w: 3.0, h: 2.2, fontSize: 15, color: fill === PALE ? INK : C.background1 }); };
  box(0.6, "Links everywhere", "The Web\u2019s architecture made every page reachable from another. A crawler needs no map.", PALE, GREEN);
  arrow(s, 4.33, 3.3);
  box(4.85, "A corpus", "Filtered Common Crawl was the largest share of GPT-3\u2019s training mix.", PALE, GREEN);
  arrow(s, 8.58, 3.3);
  box(9.1, "The missing half", "Crawlers read. They never send a form, so the Web\u2019s transactions were never learned from.", C.text1, GOLD);
  T(s, "The transactional half of the Web is where agents now have to work.", { x: 0.6, y: 5.5, w: 12, h: 0.5, fontSize: 18, bold: true, color: RUST });
}

// 6. Information and controls
{
  const s = slide("Fielding, in the comments on his 2008 post", "Information and controls, presented together", true,
    "This is the marriage. On the human web, the page and its forms arrive together; the information itself is how you find out what you can do.");
  quote(s, 1.3, 1.95, 10.8, 2.0, "When I say hypertext, I mean the simultaneous presentation of information and controls such that the information becomes the affordance through which the user (or automaton) obtains choices and selects actions.", "Roy T. Fielding, comment, 20 October 2008", true, 26);
  pill(s, 1.3, 4.9, 2.2, "information");
  T(s, "+", { x: 3.6, y: 4.85, w: 0.4, h: 0.45, fontSize: 24, bold: true, color: GOLD, align: "center" });
  pill(s, 4.1, 4.9, 2.2, "controls");
  T(s, "=", { x: 6.4, y: 4.85, w: 0.4, h: 0.45, fontSize: 24, bold: true, color: GOLD, align: "center" });
  pill(s, 6.9, 4.9, 2.2, "affordance");
  T(s, "On the human web they were married: the page and its forms arrive together.", { x: 1.3, y: 5.6, w: 10, h: 0.4, fontSize: 15, italic: true, color: C.accent6 });
}

// 7. Divorce
{
  const s = slide("The machine web", "The divorce: data in the response, controls elsewhere", false,
    "APIs kept the information and moved the controls out of band: docs, SDKs, OpenAPI, and now MCP tool lists. Agents inherit the divorce: every tool and endpoint is loaded into the context window up front, whatever the current state allows.");
  card(s, 0.6, 1.75, 3.6, 3.0);
  T(s, "The response", { x: 0.9, y: 1.95, w: 3, h: 0.4, fontSize: 18, bold: true, fontFace: "Cambria", color: GREEN });
  T(s, "Data only. Status, ids, timestamps. Nothing says what you may do next.", { x: 0.9, y: 2.5, w: 3.0, h: 2, fontSize: 15 });
  s.addShape(pres.shapes.RECTANGLE, { x: 4.6, y: 1.75, w: 4.0, h: 3.0, fill: { color: C.text1 }, line: { color: C.text1 } });
  T(s, "The controls, out of band", { x: 4.9, y: 1.95, w: 3.5, h: 0.4, fontSize: 18, bold: true, fontFace: "Cambria", color: GOLD });
  bullets(s, ["Docs and SDKs", "OpenAPI descriptions", "MCP tool lists"], { x: 4.9, y: 2.5, w: 3.4, h: 2, color: C.background1 });
  stat(s, 9.1, 1.8, 3.6, "13.5 MB", "GitHub\u2019s OpenAPI description, as one file. An agent given it reads every operation, whatever state the pull request is in.", RUST);
  quote(s, 1.3, 5.25, 11.2, 0.6, "Failure here implies that out-of-band information is driving interaction instead of hypertext.", "Roy T. Fielding, \u201CREST APIs must be hypertext-driven\u201D, 2008", false, 18);
}

// 8. One loop, two readers
{
  const s = slide("One loop, two readers", "Look, Think, Act  =  Sync, Reason, Act", false,
    "A person in a browser and ARTIE run the same loop. Sync and Act are plain code. Reason is the only place a model sits, and a kept script can stand in for it on every later run.");
  const rows = [["Look", "Sync", "Read the representation: the state and the controls offered now."], ["Think", "Reason", "Choose among what\u2019s offered. The only place a model sits, and a kept script can stand in for it."], ["Act", "Act", "Send the form: method, target, fields, If-Match, signed."]];
  T(s, "A person", { x: 0.6, y: 1.65, w: 2, h: 0.3, fontSize: 13, bold: true, color: SAGE });
  T(s, "ARTIE", { x: 3.2, y: 1.65, w: 2, h: 0.3, fontSize: 13, bold: true, color: SAGE });
  rows.forEach(([a, b, d], i) => {
    const y = 2.05 + i * 1.0;
    pill(s, 0.6, y, 2.0, a, C.accent6, INK, 0.5);
    arrow(s, 2.7, y + 0.09);
    pill(s, 3.2, y, 2.0, b, i === 1 ? GOLD : GREEN, i === 1 ? INK : C.background1, 0.5);
    T(s, d, { x: 5.5, y: y - 0.02, w: 7.2, h: 0.6, fontSize: 15, valign: "middle" });
  });
  quote(s, 1.3, 5.3, 11.2, 0.6, "Typed relations, specific media types, and action-specific elements provide the guidance needed for automated agents.", "Roy T. Fielding, comment, 2008", false, 18);
}

// 9. HEART RISE ARTIE
{
  const s = slide("Putting them back together", "HEART, RISE and ARTIE", false,
    "Three names, briefly. HEART is the media type and its rules; RISE is how asynchronous work answers back; ARTIE is the one generic client. All built test-first under the bdd-gates skill.");
  const items = [
    ["HEART", "Hypermedia Enforced Agentic Reliable Transactions", "Each answer carries the state plus the forms valid now (HAL Schema Forms), with typed relations, If-Match guards and signed acts."],
    ["RISE", "Reciprocal Interface for State Exchange", "Replies for asynchronous work: a watcher or a build answers back when it\u2019s ready."],
    ["ARTIE", "Autonomous Restful Traversal and Interactive Engine", "One generic client for all of it, coupled to the media type, never to a domain."],
  ];
  items.forEach(([k, exp, d], i) => {
    const x = 0.6 + i * 4.1;
    card(s, x, 1.75, 3.85, 3.4, i === 2 ? C.text1 : PALE);
    T(s, k, { x: x + 0.3, y: 1.95, w: 3.3, h: 0.6, fontSize: 30, bold: true, fontFace: "Cambria", color: i === 2 ? GOLD : GREEN });
    T(s, exp, { x: x + 0.3, y: 2.6, w: 3.3, h: 0.75, fontSize: 14, italic: true, color: i === 2 ? C.accent6 : SAGE });
    T(s, d, { x: x + 0.3, y: 3.45, w: 3.3, h: 2.2, fontSize: 15, color: i === 2 ? C.background1 : INK });
  });
  T(s, "Built test-first under the bdd-gates skill: Claude Code hooks plus git hooks.", { x: 0.6, y: 5.45, w: 12, h: 0.4, fontSize: 15, bold: true, color: RUST });
}

// 10. State-scoped affordances
{
  const s = slide("Putting them back together", "State-scoped affordances", false,
    "The gateway's own text/html for the merged pull request #2336 on real GitHub. Close, Reopen, Merge and the others are shown but not offered; Comment, Label and Edit are. That's the same answer ARTIE reads as HAL Schema Forms. No context window full of tools: the service returns what can be done now.");
  shot(s, "html-github-503.png", 0.6, 1.7, 7.4, 7.4 * 720 / 1280, "Real GitHub pull request #2336, merged, through the gateway as text/html. Screenshot, 13 Nov 2026.");
  T(s, "What this resource, in this state, lets you do now, and nothing else.", { x: 8.4, y: 1.75, w: 4.3, h: 1.2, fontSize: 20, bold: true, fontFace: "Cambria", color: GREEN });
  bullets(s, [
    [{ text: "Merged: ", b: true }, { text: "Comment, Label and Edit are offered. Merge, Close and Reopen are not." }],
    [{ text: "One answer, two readers: ", b: true }, { text: "the same state, as HAL Schema Forms, is what ARTIE reads." }],
    [{ text: "No giant context window ", b: true }, { text: "of tools or OpenAPI: the model reads only what applies." }],
  ], { x: 8.4, y: 3.1, w: 4.3, h: 3 });
}

// 11. Bloated?
{
  const s = slide("\u201CBut isn\u2019t hypermedia bloated?\u201D", "L0, L1, L2: one page, three ways to deliver it", false,
    "The analogy: a web page with all its CSS and JavaScript inlined, versus the same page with linked, cached files. Same page, different delivery. L0 inlines everything; L1 links the form set; L2 links the schemas too, content-addressed and immutable for a year. The numbers are from five real releases per forge, with no model calls. From the second release on, about 50 form-set and schema reads per release never reach the network: ARTIE's cache answers them. Resources themselves still revalidate by ETag and answer 304.");
  const rung = (y, k, head, d) => { pill(s, 0.6, y, 0.75, k); T(s, head, { x: 1.55, y: y - 0.04, w: 4.6, h: 0.35, fontSize: 15, bold: true }); T(s, d, { x: 1.55, y: y + 0.32, w: 4.6, h: 0.6, fontSize: 13, color: SAGE }); };
  T(s, "Like a page with its CSS and JavaScript inlined, or linked and cached.", { x: 0.6, y: 1.6, w: 5.6, h: 0.6, fontSize: 15, italic: true });
  rung(2.35, "L0", "State, forms and schemas in one answer", "Simple and correct: like a page with everything inlined.");
  rung(3.35, "L1", "Prefer: forms=link", "The form set is its own content-addressed link, cached.");
  rung(4.35, "L2", "Prefer: forms=link, schema=link", "Schemas linked too: immutable, shared across forms, states and resources.");
  T(s, "\u201CL0 buys correctness, L2 buys economy.\u201D HTTP caching does the scaling, as it always has on the Web.", { x: 0.6, y: 5.45, w: 5.6, h: 0.8, fontSize: 14, bold: true, color: GREEN });
  // table
  const hd = (t) => ({ text: t, options: { bold: true, color: C.background1, fill: { color: C.text1 } } });
  const rows = [
    [hd("Requests on the network, per release"), hd("1st"), hd("2nd"), hd("3rd"), hd("4th"), hd("5th")],
    ["L0", "59", "59", "59", "56", "59"],
    ["L2, a fresh cache each release", "112", "112", "111", "106", "112"],
    [{ text: "L2, one cache kept", options: { bold: true, color: GREEN } }, "112", { text: "61", options: { bold: true, color: GREEN } }, { text: "59", options: { bold: true, color: GREEN } }, { text: "58", options: { bold: true, color: GREEN } }, { text: "59", options: { bold: true, color: GREEN } }],
  ];
  s.addTable(rows, { x: 6.6, y: 1.65, w: 6.1, colW: [2.6, 0.7, 0.7, 0.7, 0.7, 0.7], fontSize: 12, color: INK, border: { type: "solid", pt: 0.5, color: "C9D8CE" }, fill: { color: "FFFFFF" }, rowH: 0.36, align: "center", valign: "middle" });
  T(s, "Real GitHub, five releases, no model calls. A warm release sends about 53 fewer requests: the form sets and schemas never leave ARTIE\u2019s memory.", { x: 6.6, y: 3.25, w: 6.1, h: 0.7, fontSize: 12, color: SAGE, italic: true });
  stat(s, 6.6, 4.05, 3.0, "51.2M", "requests never sent, over 1,000,000 releases (GitHub)");
  stat(s, 9.7, 4.05, 3.0, "34.6 GB", "bytes not received at that scale: 23% on GitHub, 60% on GitLab");
  T(s, "Bytes: JSON bodies before compression, headers excluded. Extrapolated from the measured warm release.", { x: 6.6, y: 5.75, w: 6.1, h: 0.5, fontSize: 11, color: SAGE });
}

// 12. In production
{
  const s = slide("Not new, not niche", "Hypermedia is already in production", false,
    "Okta's Authentication API answers with HAL: _embedded for the user and policy, _links for what can be done next, here changePassword or cancel, because the password expired. Amazon API Gateway's management API documented its own typed link relations (2017 archive). Spring HATEOAS is being pitched for agents now: Adib Saikali, Spring I/O 2026. What HEART adds: forms with JSON Schema, guards, and typed relations.");
  shot(s, "okta-crop.png", 0.6, 1.65, 3.55, 3.55 * 2200 / 1640, "Okta Authentication API: expired password.");
  T(s, "Okta", { x: 4.5, y: 1.65, w: 3.4, h: 0.4, fontSize: 20, bold: true, fontFace: "Cambria", color: GREEN });
  T(s, "The password expired, so the answer\u2019s _links offer next: changePassword, and cancel. Nothing else.", { x: 4.5, y: 2.1, w: 3.4, h: 1.6, fontSize: 15 });
  T(s, "Amazon API Gateway", { x: 4.5, y: 3.9, w: 3.4, h: 0.4, fontSize: 20, bold: true, fontFace: "Cambria", color: GREEN });
  T(s, "Documented as HAL, with its own typed link relations: restapi:deployments, apikey:create\u2026", { x: 4.5, y: 4.35, w: 3.4, h: 1.4, fontSize: 15 });
  shot(s, "aws-crop.png", 8.3, 1.65, 4.4, 4.4 * 516 / 760, "API Gateway REST API Reference, archived 21 Jan 2017.");
  card(s, 8.3, 5.05, 4.4, 1.3, C.text1);
  T(s, [{ text: "Spring HATEOAS, for agents: ", options: { bold: true, color: GOLD } }, { text: "Adib Saikali, \u201CREST is for Agents\u201D, Spring I/O 2026. HEART adds schemas, guards and typed relations.", options: { color: C.background1 } }], { x: 8.5, y: 5.15, w: 4.0, h: 1.1, fontSize: 13 });
}

// 13. Tokenomics
{
  const s = slide("The tokenomics slide", "Capture once with a model, replay with none", false,
    "One Claude capture on real GitHub at rung L2: 20 model calls, 33,140 input and 2,980 output tokens. Then the kept script ran ten releases on two forges, and five more three ways for the cache numbers: zero model calls. Pins stop a replay that no longer fits, so the model is called only to repair.");
  code(s, 0.6, 1.7, 6.3, 3.6, [
    ["c", "# release.goal.md: the goal, in words"],
    ["p", "- Open pull request {pull} of {owner}/{repo} `as pr`"],
    ["p", "- At {watcher}, ask to be told when {pr}"],
    ["p", "  offers scm:merge `as watch`"],
    ["p", "- Once {watch} is answered, at {pr},"],
    ["p", "  merge it by squashing `as merged`"],
    ["p", "- At {jenkins}, build commit"],
    ["p", "  {merged.merge_commit_sha} `as build`"],
    ["p", "- \u2026 run the smoke script it links \u2026 `as smoke`"],
    ["p", "- If {smoke.passed}, at {dev}, promote \u2026"],
    ["p", "  Otherwise, at {pr}, act scm:comment \u2026"],
  ], 13);
  stat(s, 7.4, 1.7, 2.6, "20", "model calls, once: the capture on real GitHub");
  stat(s, 10.2, 1.7, 2.6, "33,140", "input tokens, plus 2,980 output, once");
  stat(s, 7.4, 3.5, 2.6, "0", "model calls in every replay since", GREEN);
  stat(s, 10.2, 3.5, 2.6, "2", "forges replayed: real GitHub, and GitLab on this machine", GREEN);
  T(s, "Pins hold the replay to what was offered. If a form changes, the replay stops, and only then is the model called again, to repair.", { x: 7.4, y: 5.3, w: 5.3, h: 1.0, fontSize: 14, bold: true, color: RUST });
}

// 14. Two forges
{
  const s = slide("Proof", "One script, real GitHub and a local GitLab", false,
    "Same kept script, same scm: relations. GitHub is the real cloud service; GitLab runs locally with no cloud calls. Each forge has one YAML connector in the gateway that maps its native API to the same typed relations, so scm:merge means the same on both. On the wire, compatible because of the typed relation.");
  shot(s, "github-503.png", 0.6, 1.7, 5.9, 5.9 * 720 / 1280, "Real GitHub: pull request #2336, merged by the replay.");
  shot(s, "gitlab-503.png", 6.8, 1.7, 5.9, 5.9 * 720 / 1280, "Local GitLab: merge request !503, merged by the same script.");
  T(s, [{ text: "On the wire, compatible because of the typed relation. ", options: { bold: true, color: GREEN } }, { text: "One YAML connector per forge maps its native API to scm:merge, scm:comment\u2026 The GitLab runs make no cloud calls and no model calls." }], { x: 0.6, y: 5.55, w: 12.1, h: 0.8, fontSize: 15 });
}

// 15. Hooks
{
  const s = slide("How it was built  ·  Determinism through hooks", "Instructions are advisory. Hooks are not.", false,
    "The bdd-gates skill. A model can be told to write the test first; it can't be trusted to always do it. Hooks make it a property of the environment. They cost zero tokens.");
  const hd = (t) => ({ text: t, options: { bold: true, color: C.background1, fill: { color: C.text1 } } });
  s.addTable([
    [hd("Gate"), hd("Where"), hd("What it holds")],
    ["Gherkin first", "PreToolUse", "No production code until a scenario says what it must do"],
    ["Red, logged", "PostToolUse", "A failing test run is recorded before the first src/main write"],
    ["Green to stop", "Stop", "The session can\u2019t end with the suite red"],
    ["Ordering at commit", "git pre-commit", "The log must show feature, red, code, green, in that order"],
    ["Gate protection", "PreToolUse", "The gates and their log can\u2019t be edited by the model"],
  ], { x: 0.6, y: 1.7, w: 8.0, colW: [2.0, 1.7, 4.3], fontSize: 13, color: INK, border: { type: "solid", pt: 0.5, color: "C9D8CE" }, rowH: 0.5, valign: "middle" });
  card(s, 9.0, 1.7, 3.7, 3.0, C.text1);
  T(s, "0", { x: 9.3, y: 1.9, w: 3.1, h: 1.0, fontSize: 60, bold: true, fontFace: "Cambria", color: GOLD });
  T(s, "model calls to enforce them. The gates are scripts the harness runs, not instructions the model may forget.", { x: 9.3, y: 3.0, w: 3.1, h: 1.6, fontSize: 14, color: C.background1 });
  T(s, "Claude Code hooks guard the edits; git hooks guard the history.", { x: 0.6, y: 5.0, w: 8, h: 0.4, fontSize: 15, bold: true, color: GREEN });
}

// 16. Red first
{
  const s = slide("How it was built  ·  Red first, live", "The scenario, then the failing run", false,
    "A real one from this work. The real-GitHub capture dropped the step that opens the pull request, because nothing read a value from it; a later step only named it. The scenario says so first, and the run fails on behaviour before any code is written.");
  code(s, 0.6, 1.7, 7.2, 3.9, [
    ["k", "Scenario: A step that only reads is kept in the"],
    ["k", "  script when a later step names what it read"],
    ["c", "  `as NAME` on a read is no detour when a later step"],
    ["c", "  names it: the watch is asked about ${front.@self}"],
    ["p", "  Given the file \"named-read.goal.md\" is"],
    ["g", "    - Search the desk's releases that are {state}"],
    ["g", "      `as front`"],
    ["g", "    - At {watcher}, ask to be told when {front}"],
    ["g", "      offers desk:release `as watch`"],
    ["p", "  \u2026"],
    ["p", "  Then the script has a line with"],
    ["p", "    \"- act: desk:search-releases\""],
  ], 12);
  code(s, 8.1, 1.7, 4.6, 2.3, [
    ["c", "# the red run, logged by the gate"],
    ["r", "496 tests completed, 1 failed,"],
    ["r", "41 skipped"],
    ["p", "testcase: A step that only reads"],
    ["p", "is kept in the script when \u2026"],
  ], 12);
  T(s, "Red on behaviour, not on a compile error. The gate logs the run; only then may src/main change.", { x: 8.1, y: 4.25, w: 4.6, h: 1.5, fontSize: 14 });
}

// 17. Code green commit
{
  const s = slide("How it was built  ·  Code, green, commit", "The fix, the green run, the gated commit", false,
    "The fix in Detours.kt: keep a step whose `as` name another step mentions. Then green, then the commit, which the git pre-commit hook checks for ordering. The report beside it is the module's real test report.");
  code(s, 0.6, 1.7, 7.2, 3.3, [
    ["c", "// Detours.kt: keep a read another step names"],
    ["p", "private fun named(capture: Capture): Set<Int> {"],
    ["p", "  val marks = capture.steps.associateWith {"],
    ["p", "    capture.marks(it) }"],
    ["p", "  return marks.filter { (step, own) ->"],
    ["p", "    val name = own[\"as\"]?.toString()"],
    ["p", "      ?: return@filter false"],
    ["p", "    val mention = Regex(\"\"\"\\{$name[.}]\"\"\")"],
    ["p", "    marks.any { (other, its) -> other.line !="],
    ["p", "      step.line && its.values.any {"],
    ["p", "        mention.containsMatchIn(\"$it\") } }"],
    ["p", "  }.keys.map { it.number }.toSet()"],
  ], 11);
  code(s, 0.6, 5.2, 7.2, 1.1, [
    ["g", "pre-commit: TDD ordering check passed."],
    ["c", "# then the capture again, and the open-PR step stays"],
  ], 12);
  shot(s, "testreport-crop.png", 8.1, 1.7, 4.6, 4.6 * 380 / 900, "artie-cli test report: 198 tests, 0 failures.");
  T(s, "Then the replay, on real GitHub and local GitLab, with the regenerated script.", { x: 8.1, y: 4.0, w: 4.6, h: 1.0, fontSize: 14, bold: true, color: GREEN });
}

// 18. When the gate says no
{
  const s = slide("How it was built  ·  When the gate says no", "The gates hold, even against the model", false,
    "Three moments from this work. The protect-gate hook blocked the model from reading its own event log; it didn't retry. Refactor mode is the human's switch, never the model's. And an edit made outside Claude Code's edit tool was redone through it, so the log saw it.");
  const items = [
    ["Blocked", "The protect-gate hook stopped the model reading the gate\u2019s own event log. It didn\u2019t try another way."],
    ["Human\u2019s switch", "Refactor mode, the only time src/main may change with no new red, is switched on by the person, never the model."],
    ["Redone", "An edit made outside Claude Code\u2019s edit tool was redone through it, so the log records every change."],
  ];
  items.forEach(([k, d], i) => {
    const y = 1.75 + i * 1.45;
    pill(s, 0.6, y + 0.1, 2.2, k, i === 0 ? RUST : GREEN);
    T(s, d, { x: 3.1, y, w: 9.6, h: 1.1, fontSize: 17, valign: "middle" });
  });
  T(s, "The model doesn\u2019t have to be trustworthy for the process to be.", { x: 0.6, y: 6.2, w: 12, h: 0.45, fontSize: 18, bold: true, fontFace: "Cambria", color: GREEN });
}

// 19. Foundations
{
  const s = slide("Foundations", "Standing on these shoulders", false,
    "Links only. Fielding; Mike Kelly's HAL; Mike Amundsen's HAL-FORMS; J. Badeau's HAL Schema Forms, the base of HEART's media type, and HyperBench; Kin Lane; Adib Saikali; the W3C Autonomous Agents on the Web Community Group; and one of numerous research papers, Ciortea et al. at AAMAS 2019.");
  const L = (who, what, url) => [{ text: who, options: { bold: true, hyperlink: { url }, color: GREEN } }, { text: "  " + what, options: { color: INK } }];
  const left = [
    L("Roy T. Fielding", "the dissertation (2000) and the 2008 post", "https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven"),
    L("Mike Kelly", "HAL", "https://datatracker.ietf.org/doc/html/draft-kelly-json-hal-11"),
    L("Mike Amundsen", "HAL-FORMS", "https://rwcbook.github.io/hal-forms/"),
    L("J. Badeau", "HAL Schema Forms, the base of HEART\u2019s media type", "https://github.com/jbadeau/hal-schema-forms"),
    L("J. Badeau", "HyperBench, a hypermedia-driven workbench", "https://github.com/jbadeau/hyperbench"),
  ];
  const right = [
    L("Kin Lane", "API Evangelist", "https://apievangelist.com"),
    L("Adib Saikali", "\u201CREST is for Agents\u201D, Spring I/O 2026", "https://2026.springio.net/sessions/rest-is-for-agents-rediscovering-spring-hateoas/"),
    L("W3C", "Autonomous Agents on the Web Community Group", "https://www.w3.org/community/webagents/"),
    L("Ciortea, Mayer, Gandon, Boissier, Ricci, Zimmermann", "\u201CA Decade in Hindsight\u201D, AAMAS 2019: one of numerous papers", "https://dblp.org/rec/conf/atal/CiorteaMGBRZ19"),
  ];
  const list = (items, x) => s.addText(items.map((r, i) => r.map((run, j) => ({ text: run.text, options: { ...run.options, breakLine: j === r.length - 1 && i < items.length - 1, paraSpaceAfter: 14 } }))).flat(), { x, y: 1.85, w: 5.45, h: 3.6, fontSize: 15, margin: 0, valign: "top", isTextBox: true });
  card(s, 0.6, 1.65, 6.0, 3.9); card(s, 6.8, 1.65, 5.9, 3.9);
  T(s, "Others speaking now on hypermedia and agents include Rohit Bhardwaj (\u201CFrom REST to Reasoning Agents\u201D) and Michael Carducci (\u201CHypermedia APIs and the Future of AI Agentic Systems\u201D), NFJS.", { x: 0.6, y: 5.8, w: 12.1, h: 0.7, fontSize: 13, italic: true, color: SAGE });
  list(left, 0.85); list(right, 7.05);
}

// 20. Close
{
  const s = slide("Close", null, true,
    "Building is gated by hooks. Running is driven by hypertext and replayed from kept scripts. Both move work from tokens to processes.");
  T(s, "The Web gave us the model. Hypertext and hooks let us stop paying it for the same path twice.", { x: 0.6, y: 1.4, w: 11.5, h: 2.0, fontSize: 36, bold: true, fontFace: "Cambria", color: C.background1 });
  const col = (x, head, d) => { T(s, head, { x, y: 4.0, w: 3.8, h: 0.4, fontSize: 18, bold: true, color: GOLD }); T(s, d, { x, y: 4.45, w: 3.8, h: 1.4, fontSize: 15, color: C.accent6 }); };
  col(0.6, "Running", "Driven by hypertext: only what\u2019s offered now. Found once with a model, replayed with none.");
  col(4.7, "Building", "Gated by hooks: Gherkin, red, code, green, commit. No model call to enforce.");
  col(8.8, "Both", "Move work from tokens to processes, where it costs nothing to repeat.");
}

(async () => {
  const out = process.argv[2] || "deck.pptx";
  await pres.writeFile({ fileName: out });
  await applyTheme(out, THEME);
  console.log("wrote", out, n, "slides");
})();
