// Builds "Find the Way Once" (find-the-way-once.pptx) in the style of the Take HEART decks:
// Cambria throughout, Courier New for code, the green, rust, gold and sage palette.
//   NODE_PATH=<dir with pptxgenjs@3.12.0> node build.js [out.pptx] [--each <dir>]
// With --each, every slide is also written as a deck of its own, for previews.

const pptxgen = require("pptxgenjs");
const path = require("path");

const C = {
  ink: "17201B", white: "FFFFFF", deep: "2C5E45", mist: "EDF2EE", green: "2F7D55",
  rust: "B5562B", blue: "3D6B8C", gold: "E3B341", sage: "6F8A7C", pale: "C9D8CE",
};
const SERIF = "Cambria";
const MONO = "Courier New";
const BULLET = { indent: 18 };

// ---------------------------------------------------------------- helpers

function head(s, eyebrow, title, dark) {
  s.addText(eyebrow, {
    x: 0.6, y: 0.32, w: 12.1, h: 0.32, fontFace: SERIF, fontSize: 12, bold: true,
    color: dark ? C.gold : C.green, charSpacing: 1, margin: 0,
  });
  s.addText(title, {
    x: 0.6, y: 0.62, w: 12.1, h: 0.8, fontFace: SERIF, fontSize: 36, bold: true,
    color: dark ? C.white : C.ink, valign: "top", margin: 0,
  });
}

function text(s, body, o) {
  s.addText(body, Object.assign({ fontFace: SERIF, fontSize: 16, color: C.ink, valign: "top", margin: 0 }, o));
}

function shape(s, kind, o) {
  s.addShape(kind, Object.assign({ rectRadius: 0.08 }, o));
}

function box(s, x, y, w, h, fill, line) {
  shape(s, "roundRect", { x, y, w, h, fill: { color: fill }, line: { color: line || fill, width: 1 } });
}

// A card: a rounded box with a title and a body, padded.
function card(s, x, y, w, h, title, body, o = {}) {
  const fill = o.fill || C.mist;
  const dark = fill === C.ink || fill === C.green || fill === C.deep;
  box(s, x, y, w, h, fill, o.line);
  const runs = [];
  if (o.tag) runs.push({ text: o.tag, options: { fontSize: 12, bold: true, color: dark ? C.gold : C.green, breakLine: true, charSpacing: 1 } });
  runs.push({ text: title, options: { fontSize: o.titleSize || 20, bold: true, color: dark ? C.white : C.ink, breakLine: true } });
  [].concat(body).forEach((b, i, all) => runs.push({
    text: b, options: { fontSize: o.size || 15, color: dark ? C.pale : C.ink, breakLine: i < all.length - 1, paraSpaceBefore: 6 },
  }));
  text(s, runs, { x: x + 0.25, y: y + 0.2, w: w - 0.5, h: h - 0.4 });
}

function arrow(s, x, y, w = 0.4, h = 0.4, color = C.sage) {
  shape(s, "rightArrow", { x, y, w, h, fill: { color }, line: { color, width: 1 } });
}

function downArrow(s, x, y, w = 0.4, h = 0.35, color = C.sage) {
  shape(s, "downArrow", { x, y, w, h, fill: { color }, line: { color, width: 1 } });
}

function pill(s, x, y, w, label, on, h = 0.46) {
  box(s, x, y, w, h, on ? C.green : C.mist, on ? C.green : C.pale);
  text(s, label, { x, y, w, h, fontSize: 14, bold: true, color: on ? C.white : C.sage, align: "center", valign: "middle" });
}

// Code in a dark panel. A line starting with #, // or <!-- is a comment (sage, italic); a request or status line is
// bold; ⟦…⟧ marks a highlight (gold, bold).
function code(s, x, y, w, h, lines, o = {}) {
  box(s, x, y, w, h, o.fill || C.ink);
  const runs = [];
  lines.forEach((ln, i) => {
    const last = i === lines.length - 1;
    const comment = /^\s*(#|\/\/|<!--)/.test(ln);
    const request = /^(GET|POST|PUT|DELETE|PATCH|HTTP|\d{3} [A-Z])/.test(ln);
    // No-break spaces, so blank lines and indents survive every renderer.
    const kept = ln === "" ? " " : ln.replace(/^ +| {2,}/g, (m) => " ".repeat(m.length));
    const parts = kept.split(/(⟦[^⟧]*⟧)/).filter((p) => p !== "");
    parts.forEach((p, j) => {
      const hl = p.startsWith("⟦");
      runs.push({
        text: hl ? p.slice(1, -1) : p,
        options: {
          color: hl ? C.gold : comment ? C.sage : C.white,
          bold: hl || request, italic: comment && !hl,
          breakLine: j === parts.length - 1 && !last,
        },
      });
    });
  });
  text(s, runs, {
    x: x + 0.22, y: y + 0.16, w: w - 0.44, h: h - 0.32, fontFace: MONO, fontSize: o.size || 12,
    lineSpacingMultiple: 1.0,
  });
}

function label(s, x, y, w, words, dark) {
  text(s, words, { x, y, w, h: 0.32, fontSize: 12, bold: true, color: dark ? C.gold : C.green, charSpacing: 1 });
}

// A numbered note beside a code panel.
function note(s, n, x, y, w, words, h = 0.9) {
  shape(s, "ellipse", { x, y: y + 0.02, w: 0.34, h: 0.34, fill: { color: C.green }, line: { color: C.green, width: 1 } });
  text(s, String(n), { x, y: y + 0.02, w: 0.34, h: 0.34, fontSize: 13, bold: true, color: C.white, align: "center", valign: "middle" });
  text(s, words, { x: x + 0.48, y, w: w - 0.48, h, fontSize: 15 });
}

function big(s, x, y, w, number, words, o = {}) {
  text(s, number, { x, y, w, h: 0.85, fontSize: o.size || 44, bold: true, color: o.color || (o.dark ? C.gold : C.green) });
  text(s, words, { x, y: y + 0.85, w, h: 0.75, fontSize: 13, color: o.dark ? C.pale : C.sage });
}

function quote(s, y, words, cite, h = 1.4) {
  text(s, "“", { x: 0.75, y: y - 0.35, w: 0.8, h: 0.9, fontSize: 72, color: C.gold });
  text(s, words, { x: 1.4, y, w: 10.9, h, fontSize: 24, italic: true, color: C.white });
  if (cite) text(s, cite, { x: 1.4, y: y + h + 0.05, w: 10.9, h: 0.4, fontSize: 13, color: C.pale });
}

function foot(s, words, dark, y = 6.3) {
  text(s, words, { x: 0.6, y, w: 12.1, h: 0.5, fontSize: 15, italic: true, color: dark ? C.pale : C.sage });
}

function table(s, rows, o) {
  const body = rows.map((r, i) => r.map((cell, j) => {
    const c = typeof cell === "object" ? cell : { text: String(cell) };
    const header = i === 0;
    const first = j === 0 && !header;
    return {
      text: c.text,
      options: Object.assign({
        fill: { color: header ? C.ink : first ? C.mist : c.fill || C.white },
        color: header ? C.white : c.color || C.ink,
        bold: header || first || !!c.bold,
        align: header || j > 0 ? "center" : "left",
        valign: "middle",
      }, c.options || {}),
    };
  }));
  s.addTable(body, Object.assign({
    fontFace: SERIF, fontSize: 13, border: { type: "solid", pt: 0.75, color: C.pale },
    margin: [0.04, 0.08, 0.04, 0.08],
  }, o));
}

// ---------------------------------------------------------------- slides

const slides = [];
const add = (master, build) => slides.push({ master, build });

// 1. Title
add("DARK", (s) => {
  text(s, "HYPERMEDIA CONTROLS, AI AND WORK DONE IN BULK", { x: 0.8, y: 1.6, w: 11.7, h: 0.5, fontSize: 16, bold: true, color: C.gold, charSpacing: 2 });
  text(s, "Find the Way Once", { x: 0.8, y: 2.2, w: 11.7, h: 1.6, fontSize: 60, bold: true, color: C.white, valign: "middle" });
  text(s, "How the Web put controls beside information, how APIs moved them into documents, and how a client that finds its way once can do the same work again and again with no model.",
    { x: 0.8, y: 3.9, w: 10.5, h: 1.4, fontSize: 22, color: C.pale });
  pill(s, 0.8, 5.75, 1.9, "Give notice", true);
  pill(s, 2.95, 5.75, 1.6, "Update", true);
  pill(s, 4.8, 5.75, 1.6, "Dismiss", false);
  text(s, "Only the controls that apply now.", { x: 6.7, y: 5.8, w: 5, h: 0.4, fontSize: 14, italic: true, color: C.pale });
  s.addNotes("The example throughout is an employees service. A working employee is offered give notice and update; dismiss is offered only once notice is given. The buttons are the controls for one employee, now.");
});

// 2. The one idea
add("CONTENT", (s) => {
  head(s, "THE ONE IDEA", "Spend the model once, where the way is unknown");
  text(s, "Most business work is the same process over many records, every day, for months. Its shape can be found once: which links, which forms, which values.",
    { x: 0.6, y: 1.75, w: 6.2, h: 1.6, fontSize: 20 });
  text(s, [
    { text: "Find the way with a model, and keep it.", options: { bold: true, breakLine: true } },
    { text: "Run it after with none, held to the server’s controls.", options: { bold: true, color: C.green } },
  ], { x: 0.6, y: 3.55, w: 6.2, h: 1.1, fontSize: 22 });
  text(s, "The thread through this deck: when the controls travel in the answer, a client can find its way from a bookmark, and a kept way can tell when the server has changed.",
    { x: 0.6, y: 4.95, w: 6.2, h: 1.2, fontSize: 16, color: C.sage });
  box(s, 7.3, 1.75, 5.4, 4.4, C.mist);
  big(s, 7.65, 1.95, 4.8, "48,491", "input tokens, once: a goal in plain words, run with the model to find the way");
  big(s, 7.65, 3.35, 4.8, "0", "tokens a run after that, at 6, 30 or 90 employees: the way, kept as a script");
  big(s, 7.65, 4.75, 4.8, "15 of 15", "runs right");
  s.addNotes("This is the claim, and the rest of the deck is why it works. The numbers are from the runs at the end: one goal run with the model, then a generated script run 15 times with no model.");
});

// 3. Act I
add("CONTENT", (s) => {
  head(s, "ACT I  ·  FOUNDATIONS OF HYPERMEDIA", "Information you can act on from where you stand");
  shape(s, "line", { x: 0.9, y: 2.55, w: 11.5, h: 0, line: { color: C.pale, width: 1.5 } });
  const items = [
    ["1945", "Vannevar Bush", "“As We May Think”. The Memex: documents joined by trails that a reader follows and adds to."],
    ["1965", "Ted Nelson", "Coins “hypertext”: text that branches, where the reader chooses the path."],
    ["1968", "Douglas Engelbart", "The demonstration: links, windows and a mouse, live, on information you could click and change."],
    ["1987", "HyperCard", "Bill Atkinson’s HyperCard comes with every Mac: cards whose buttons take you to the next card."],
  ];
  items.forEach(([year, who, what], i) => {
    const x = 0.6 + i * 3.08;
    box(s, x, 2.37, 1.2, 0.38, C.green);
    text(s, year, { x, y: 2.37, w: 1.2, h: 0.38, fontSize: 14, bold: true, color: C.white, align: "center", valign: "middle" });
    card(s, x, 3.15, 2.85, 2.35, who, what, { size: 15 });
  });
  foot(s, "Hypertext was always about choosing your next move from what is in front of you.", false, 5.8);
  s.addNotes("Four milestones before the Web. The common thread is a reader who moves through information by choosing among the paths it offers.");
});

// 4. Act II: the Web
add("CONTENT", (s) => {
  head(s, "ACT II  ·  THE EARLY SUCCESS OF THE WEB", "Pages stopped only showing, and started offering");
  card(s, 0.6, 1.75, 5.4, 1.75, "Tim Berners-Lee", "A proposal at CERN, then the first browser and server. Any document can link to any other.", { tag: "1989–91" });
  card(s, 0.6, 3.7, 5.4, 1.75, "Forms", "Mosaic adds fill-out forms; HTML 2.0 (RFC 1866) makes them standard.", { tag: "1993–95" });
  code(s, 6.4, 1.75, 6.3, 3.7, [
    "<h1>Anna Miller</h1>",
    "<p>Clerk. Working.</p>",
    "",
    "<!-- a link: an address you didn't build -->",
    "<a href=\"/employees\">All employees</a>",
    "",
    "<!-- a form: where it goes, how, and what -->",
    "<form action=\"/employees/3/notice\"",
    "      method=\"post\">",
    "  <button>Give notice</button>",
    "</form>",
  ], { size: 13 });
  text(s, "A link handed over an address. A form said where it goes, how, and what it needs, and it was there only when the act was allowed.",
    { x: 0.6, y: 5.75, w: 12.1, h: 0.8, fontSize: 18, bold: true, color: C.green });
  s.addNotes("Forms are the controls. The person never needed the site's rules in advance: the next move was in front of them.");
});

// 5. Act II: why one browser fits every site
add("CONTENT", (s) => {
  head(s, "ACT II  ·  WHY ONE BROWSER FITS EVERY SITE", "The browser is coupled to HTML, not to your bank");
  box(s, 0.6, 2.2, 2.9, 2.6, C.ink);
  text(s, [
    { text: "Web browser", options: { fontSize: 22, bold: true, color: C.white, breakLine: true } },
    { text: "one generic client", options: { fontSize: 14, color: C.pale } },
  ], { x: 0.85, y: 2.5, w: 2.5, h: 1.2 });
  text(s, "Ships once. Never updated for a site.", { x: 0.85, y: 3.75, w: 2.5, h: 0.9, fontSize: 14, color: C.pale });
  arrow(s, 3.55, 3.3, 0.5, 0.4);
  box(s, 4.1, 2.2, 3.4, 2.6, C.green);
  text(s, [
    { text: "text/html + HTTP", options: { fontFace: MONO, fontSize: 20, bold: true, color: C.white, breakLine: true } },
    { text: "links, forms, fields, methods, status codes", options: { fontSize: 14, color: C.white, paraSpaceBefore: 8 } },
  ], { x: 4.35, y: 2.5, w: 2.95, h: 2.1 });
  arrow(s, 7.55, 3.3, 0.5, 0.4);
  ["An online bank", "A wiki", "A shop", "Any site not yet built"].forEach((w, i) => {
    box(s, 8.2, 1.95 + i * 0.8, 4.5, 0.62, C.mist);
    text(s, w, { x: 8.45, y: 1.95 + i * 0.8, w: 4.1, h: 0.62, fontSize: 16, valign: "middle" });
  });
  text(s, "Coupled to: the media type and the protocol.    Not coupled to: accounts, articles, carts.",
    { x: 0.6, y: 5.35, w: 12.1, h: 0.5, fontSize: 18, bold: true, color: C.green });
  text(s, "Every response tells it what can be done next, so a site can change without the browser noticing.",
    { x: 0.6, y: 5.9, w: 12.1, h: 0.5, fontSize: 16, color: C.sage });
  s.addNotes("The generality of the browser was a design property, not luck. It knows the language of the Web, not the business of any one site.");
});

// 6. Act III: Fielding's four constraints
add("DARK", (s) => {
  head(s, "ACT III  ·  2000: FIELDING’S DISSERTATION", "REST, and its least-followed constraint", true);
  quote(s, 1.95, "REST is defined by four interface constraints: identification of resources; manipulation of resources through representations; self-descriptive messages; and, hypermedia as the engine of application state.",
    "Roy T. Fielding, Architectural Styles and the Design of Network-based Software Architectures, 2000, §5.1.5", 1.5);
  ["Identification of resources", "Manipulation through representations", "Self-descriptive messages", "Hypermedia as the engine of application state"].forEach((w, i) => {
    const x = 0.6 + i * 3.08;
    const last = i === 3;
    box(s, x, 4.25, 2.85, 1.0, last ? C.gold : C.deep);
    text(s, w, { x: x + 0.15, y: 4.25, w: 2.55, h: 1.0, fontSize: 15, bold: last, color: last ? C.ink : C.white, align: "center", valign: "middle" });
  });
  text(s, "The client moves from state to state by choosing among the controls each response carries.",
    { x: 0.6, y: 5.6, w: 12.1, h: 0.5, fontSize: 18, bold: true, color: C.white });
  foot(s, "The architecture of the Web, written down: the browser’s generality was a design property.", true, 6.2);
  s.addNotes("Fielding's dissertation describes the Web's architecture. The fourth constraint is the one most APIs later dropped.");
});

// 7. Act III: information and controls
add("DARK", (s) => {
  head(s, "ACT III  ·  WHAT HE MEANT BY HYPERTEXT", "Information and controls, together", true);
  quote(s, 1.95, "When I say hypertext, I mean the simultaneous presentation of information and controls such that the information becomes the affordance through which the user (or automaton) obtains choices and selects actions.",
    "Roy T. Fielding, comment on “REST APIs must be hypertext-driven”, 2008", 1.5);
  card(s, 0.6, 4.3, 5.9, 1.3, "Information: what is true now", "Anna Miller, clerk, working.", { fill: C.deep, size: 16 });
  card(s, 6.8, 4.3, 5.9, 1.3, "Controls: what can be done now", "Give notice. Update. (Dismiss is not offered yet.)", { fill: C.green, size: 16 });
  foot(s, "“Or automaton”: the same answer serves a person, an agent and a script.", true, 6.2);
  s.addNotes("This is the definition the rest of the deck leans on. Controls are the half that APIs lost.");
});

// 8. Act III: 2008
add("DARK", (s) => {
  head(s, "ACT III  ·  20 OCTOBER 2008", "“REST APIs must be hypertext-driven”", true);
  quote(s, 1.85, "I am getting frustrated by the number of people calling any HTTP-based interface a REST API. … That is RPC. It screams RPC.", null, 1.1);
  quote(s, 3.4, "A REST API should be entered with no prior knowledge beyond the initial URI (bookmark) and set of standardized media types that are appropriate for the intended audience …",
    "Roy T. Fielding, “REST APIs must be hypertext-driven”, Untangled, 20 October 2008", 1.5);
  foot(s, "A bookmark and the media types: everything else, the client learns from what the server hands it.", true, 6.2);
  s.addNotes("Eight years after the dissertation, the author had to say it out loud. A bag of endpoints known in advance is RPC, whatever it is called.");
});

// 9. Act III: none of them
add("DARK", (s) => {
  head(s, "ACT III  ·  FIELDING, IN THE COMMENTS", "“None of them”", true);
  quote(s, 1.85, "How many Web browsers are aware of the distinction between an online-banking resource and a Wiki resource? None of them. They don’t need to be aware of the resource types.", null, 1.5);
  quote(s, 3.85, "REST is software design on the scale of decades: every detail is intended to promote software longevity and independent evolution.",
    "Roy T. Fielding, comments on “REST APIs must be hypertext-driven”, October 2008", 1.1);
  foot(s, "Generic clients, and services free to change without breaking them: that was the point.", true, 6.2);
  s.addNotes("Two replies from the comments on the same post. The first is the generic client; the second is evolution over time.");
});

// 10. Act IV: the pivot
add("CONTENT", (s) => {
  head(s, "ACT IV  ·  THE PIVOT", "Data on the wire, the rules in a document");
  shape(s, "line", { x: 0.9, y: 2.55, w: 11.5, h: 0, line: { color: C.pale, width: 1.5 } });
  const items = [
    ["1998–2001", "SOAP and WSDL", "XML messages, every operation described in advance in a document the client is built against."],
    ["2000s", "JSON over HTTP", "JSON (2001; RFC 4627, 2006) replaces XML. Resources and verbs, called “REST”, without the hypertext."],
    ["2011", "Swagger", "A document of every path, method and body, with clients and docs generated from it."],
    ["2015–16", "OpenAPI", "Swagger given to the OpenAPI Initiative and renamed: the industry’s standard."],
  ];
  items.forEach(([year, what, why], i) => {
    const x = 0.6 + i * 3.08;
    box(s, x, 2.37, 1.4, 0.38, C.rust);
    text(s, year, { x, y: 2.37, w: 1.4, h: 0.38, fontSize: 14, bold: true, color: C.white, align: "center", valign: "middle" });
    card(s, x, 3.15, 2.85, 2.35, what, why, { size: 15 });
  });
  foot(s, "The answer kept the data. The controls moved out of band: into a document, read before the call.", false, 5.8);
  s.addNotes("Each step was reasonable on its own. Together they moved the rules out of the answer and into a description the client must read first.");
});

// 11. Act IV: where the rules went
add("CONTENT", (s) => {
  head(s, "ACT IV  ·  WHERE THE CONTROLS WENT", "Each copy of the rules is staler than the last");
  const chain = [
    ["The server’s rules", "true now", C.ink],
    ["An OpenAPI document", "true when published", C.mist],
    ["Docs and SDKs", "true when generated", C.mist],
    ["Agent tools", "true when wrapped", C.mist],
    ["A model’s training", "true when trained", C.mist],
  ];
  chain.forEach(([w, when, fill], i) => {
    const x = 0.6 + i * 2.5;
    box(s, x, 1.8, 2.1, 1.5, fill);
    const dark = fill === C.ink;
    text(s, [
      { text: w, options: { fontSize: 17, bold: true, color: dark ? C.white : C.ink, breakLine: true } },
      { text: when, options: { fontSize: 13, italic: true, color: dark ? C.gold : C.rust, paraSpaceBefore: 6 } },
    ], { x: x + 0.15, y: 1.8, w: 1.8, h: 1.5, align: "center", valign: "middle" });
    if (i < chain.length - 1) arrow(s, x + 2.13, 2.37, 0.34, 0.36);
  });
  card(s, 0.6, 3.75, 5.9, 2.3, "Rules in a document", [
    "Every operation, always listed.",
    "What is allowed now: found out after the call.",
    "A change: an error, if the program reads it.",
  ], { size: 16 });
  card(s, 6.8, 3.75, 5.9, 2.3, "Controls in the answer", [
    "Only the controls the state allows.",
    "Absent with a reason when not allowed.",
    "A change: seen before the call.",
  ], { fill: C.green, size: 16 });
  foot(s, "One SDK per API became one set of agent tools per API.");
  s.addNotes("Out of band means the client learns the rules somewhere other than the answer. Every copy can be stale; the answer, by definition, is not.");
});

// 12. Act V: agents today
add("CONTENT", (s) => {
  head(s, "ACT V  ·  AGENTS ON APIs TODAY", "Two ways an agent uses an API now");
  card(s, 0.6, 1.75, 5.9, 2.5, "Tools, one per operation", [
    "Each operation becomes a tool with its description (MCP).",
    "The model picks one tool a turn, with every description in its context.",
  ], { tag: "MCP", size: 16 });
  card(s, 6.8, 1.75, 5.9, 2.5, "Code mode", [
    "The model writes a program against the API, and runs it in a sandbox.",
    "Fewer turns and fewer tokens than calling tools one by one (Cloudflare’s Code Mode; Anthropic’s code execution with MCP, 2025).",
  ], { tag: "PROGRAMS", size: 16 });
  text(s, [
    { text: "Both learn the rules from a document. Both find the way again in every run.", options: { bold: true, breakLine: true } },
    { text: "Code mode is the stronger of the two, so it is the one measured here.", options: { color: C.green, bold: true } },
  ], { x: 0.6, y: 4.6, w: 12.1, h: 1.0, fontSize: 19 });
  s.addNotes("Code mode is a fair opponent: it loops, it reads, it adapts. The question is what it costs to do that every run, and what happens when its program is kept.");
});

// 13. Act V: code mode's program
add("CONTENT", (s) => {
  head(s, "ACT V  ·  CODE MODE AT WORK", "The program it wrote for 90 employees");
  code(s, 0.6, 1.6, 7.7, 4.95, [
    "items=[];p='/employees?name=miller'",
    "while p:",
    "    d,_=g(p);items+=d['_links']['item']",
    "    n=d['_links'].get('next');p=n['href'] if n else None",
    "for i in items:",
    "    e,et=g(i['href'])",
    "    k=⟦{'working':'giveNotice','on notice':'dismiss'}⟧.get(e['status'])",
    "    if not k: continue",
    "    tp=e['_templates'][k]",
    "    h=dict(H);h['If-Match']=et",
    "    ...",
    "    try:",
    "        r=urlopen(Request(b+tp['target'],data=data,",
    "                  method=tp['method'],headers=h))",
    "        res[(k,r.status)]+=1",
    "    ⟦except Exception as x:⟧",
    "        res[(k,str(x))]+=1;errs.append(x.read())",
    "print(res,errs[:2])",
  ], { size: 12 });
  note(s, 1, 8.6, 1.65, 4.1, "It decides each employee’s state itself, from a status string it saw.");
  note(s, 2, 8.6, 2.6, 4.1, "It takes the method and target from the forms, with the ETag it read.");
  note(s, 3, 8.6, 3.55, 4.1, "Its earlier programs that run sent no If-Match (428) or the wrong method (405). The server refused them.");
  note(s, 4, 8.6, 4.7, 4.1, "A refusal is counted and printed. The program exits 0.");
  note(s, 5, 8.6, 5.55, 4.1, "A new program every run: 24k to 36k tokens each time.");
  s.addNotes("Code mode on HAL-FORMS alone, with the OpenAPI description withheld, its last program in one run at 90 employees. Trimmed: the imports and helper g() are left out.");
});

// 14. Act V: why it's brittle
add("CONTENT", (s) => {
  head(s, "ACT V  ·  WHY IT IS BRITTLE", "The program holds the rules as they were");
  card(s, 0.6, 1.75, 5.9, 1.8, "The state rule, copied", "“working” means give notice. A new state, or a renamed one, makes the map wrong, and nothing says so.", { size: 15 });
  card(s, 6.8, 1.75, 5.9, 1.8, "Names from one reading", "giveNotice, status, item: names it saw once. A release that renames one breaks the program, not the service.", { size: 15 });
  card(s, 0.6, 3.8, 5.9, 1.8, "Refusals swallowed", "except Exception: count it and carry on. A refusal becomes a line of output, and the run looks finished.", { size: 15 });
  card(s, 6.8, 3.8, 5.9, 1.8, "Values and ETag from different reads", "One program fetched a fresh ETag and sent a role worked out before a teammate’s change. No server can see that: the change was lost.", { size: 15 });
  foot(s, "Fresh code mode copes by paying the model again in every run. Kept, the program doesn’t cope at all.");
  s.addNotes("None of this is a bad model. It is what a program written against a reading of the rules does when the rules move.");
});

// 15. Act V: saved code mode through a release
add("CONTENT", (s) => {
  head(s, "ACT V  ·  KEEP THE PROGRAM, AND THE WAY FREEZES", "A field renamed, and the kept program said nothing");
  label(s, 0.6, 1.5, 6.3, "KEPT FROM RUN 1");
  code(s, 0.6, 1.85, 6.3, 2.8, [
    "import api",
    "g=api.getEmployee(⟦3⟧);print(g)",
    "u=api.update(⟦3⟧,{\"firstName\":\"Samwise\",",
    "    \"lastName\":\"Gamgee\",\"role\":\"gardener, mayor\"},",
    "    if_match=g['etag']);print(u)",
    "b=api.getEmployee(⟦2⟧);print(b)",
    "",
    "import api",
    "print(api.dismiss(⟦2⟧,",
    "    if_match=⟦'\"seq-1.json.8b9e801bed8b4db8\"'⟧))",
  ], { size: 12 });
  label(s, 7.2, 1.5, 5.5, "AFTER THE RELEASE: WHAT IT PRINTED");
  code(s, 7.2, 1.85, 5.5, 2.8, [
    "{'status': ⟦422⟧, ... 'violations':",
    "  [{'pointer': '/position', 'message':",
    "  \"required property 'position'",
    "   not found\"}]}",
    "{'status': ⟦404⟧, 'body':",
    "  {'error': 'no employee 3'}}",
    "{'status': 200, ... 'status': ⟦'dismissed'⟧}",
    "",
    "# exit 0",
  ], { size: 12 });
  big(s, 0.6, 4.95, 3.8, "12 of 20", "runs right: every run before the release, none after");
  big(s, 4.75, 4.95, 3.8, "8 of 8", "runs wrong after the rename, silently: Samwise not hired, Bilbo dismissed", { color: C.rust });
  big(s, 8.9, 4.95, 3.8, "0", "nonzero exits, so nothing asked the model to write again", { color: C.rust });
  s.addNotes("Code mode on the endpoints, its programs kept from run 1. IDs 3 and 2 and an ETag are written in as run 1 saw them. A release renamed role to position. Two tasks, 10 runs each.");
});

// 16. Act VI: HEART
add("CONTENT", (s) => {
  head(s, "ACT VI  ·  HEART", "Put the controls back in the answer");
  text(s, [
    { text: "HEART", options: { bold: true } },
    { text: " (Hypermedia Enforced Agentic Reliable Transactions): a profile for HTTP APIs.", options: { breakLine: true } },
  ], { x: 0.6, y: 1.7, w: 5.6, h: 0.9, fontSize: 18 });
  text(s, [
    { text: "Each answer carries, or links to, the acts its resource allows in its state now.", options: { bullet: BULLET, breakLine: true } },
    { text: "An act not offered is not allowed, and the answer can say why.", options: { bullet: BULLET, breakLine: true } },
    { text: "Every write sends the ETag it read: stale is refused 412, missing 428.", options: { bullet: BULLET, breakLine: true } },
    { text: "Forms are named as link relations, with hashes a client can check.", options: { bullet: BULLET } },
  ], { x: 0.6, y: 2.65, w: 5.6, h: 3.3, fontSize: 17, paraSpaceAfter: 10 });
  code(s, 6.5, 1.7, 6.2, 4.55, [
    "GET /employees/5",
    "Prefer: forms=link, schema=link",
    "",
    "200 OK",
    "ETag: \"seq-2.hsf.L2.35c0b9edcb817baf\"",
    "",
    "{ \"id\": 5, \"firstName\": \"Maria\",",
    "  \"lastName\": \"Miller\", \"role\": \"clerk\",",
    "  \"status\": \"on notice\",",
    "  \"_links\": {",
    "    \"self\": { \"href\": \"/employees/5\" },",
    "    ⟦\"hr:forms\"⟧: {",
    "      \"href\": \"/affordance-sets/e90ab1498c6b5e60\",",
    "      \"integrity\": \"sha256-6QqxSYxrXmDl…\" } } }",
  ], { size: 13 });
  foot(s, "Information: Maria Miller, on notice. Controls: the form set for that state, by link.");
  s.addNotes("One employee as the employees service answers, at L2. The state, an ETag, and a link to the forms this state allows. The address and hash are from the goal run's trace.");
});

// 17. Act VI: one form set per state
add("CONTENT", (s) => {
  head(s, "ACT VI  ·  MAKING IT CHEAP", "One form set per state, shared by every resource in it");
  code(s, 0.6, 1.6, 6.6, 4.6, [
    "GET /affordance-sets/e90ab1498c6b5e60",
    "# immutable: cached for a year",
    "",
    "{ \"_forms\": {",
    "    \"staff:dismiss\": {",
    "      \"title\": \"Dismiss the employee\",",
    "      ⟦\"method\": \"DELETE\"⟧,",
    "      \"_links\": { \"target\": {",
    "        \"href\": \"/employees/{id}/actions/dismiss\",",
    "        \"templated\": true } },",
    "      \"hr:templatePointers\": { \"id\": \"/id\" },",
    "      \"schema\": { \"$ref\": \"/schemas/…\" } },",
    "    \"staff:update\": {",
    "      \"title\": \"Update the employee\", … } } }",
  ], { size: 13 });
  const rows = [
    ["working", "give notice, update", C.green],
    ["on notice", "dismiss, update", C.green],
    ["dismissed", "nothing", C.sage],
  ];
  label(s, 7.6, 1.6, 5.1, "90 EMPLOYEES, THREE STATES");
  rows.forEach(([state, offered, color], i) => {
    const y = 2.0 + i * 1.0;
    box(s, 7.6, y, 2.0, 0.75, C.ink);
    text(s, [{ text: state, options: { bold: true, breakLine: true } }, { text: "30 employees", options: { fontSize: 12, color: C.pale } }],
      { x: 7.7, y, w: 1.8, h: 0.75, fontSize: 15, color: C.white, align: "center", valign: "middle" });
    arrow(s, 9.68, y + 0.2, 0.36, 0.34);
    box(s, 10.1, y, 2.6, 0.75, C.mist);
    text(s, offered, { x: 10.2, y, w: 2.4, h: 0.75, fontSize: 15, bold: true, color, align: "center", valign: "middle" });
  });
  text(s, "Content-addressed and immutable. A client fetches one set per state, not one per employee: 3 fetches a run, at 6 employees or 90.",
    { x: 7.6, y: 5.05, w: 5.1, h: 1.1, fontSize: 15 });
  foot(s, "L0: the forms inside every answer. L2: by link, as here. A client asks with Prefer: forms=link, schema=link.");
  s.addNotes("This is why cost can follow the states rather than the records. Resources in the same state share one set of controls.");
});

// 18. Act VI: the formats
add("CONTENT", (s) => {
  head(s, "ACT VI  ·  THE FORMATS", "Where the controls sit in each");
  table(s, [
    ["Format", "What an answer holds", "Here"],
    ["HAL", "The resource’s state, and _links: related resources by relation", "The base of the two below"],
    ["HAL-FORMS", "HAL, and _templates: each act’s method, target and fields", "Spring HATEOAS’s format, widely used; what the service answers by default"],
    ["HAL Schema Forms", "HAL, and _forms: each act’s method, a link to its target, and a JSON Schema for what it takes", "HEART’s reference format: forms named as link relations, with hashes a client can check"],
    ["OpenAPI", "Not an answer: a separate description of every endpoint, its path, method and body", "The same service’s endpoints, as code mode uses them"],
  ].map((r) => r.map((c, j) => (j === 0 ? c : { text: c, options: { align: "left" } }))),
  { x: 0.6, y: 1.75, w: 12.1, colW: [2.4, 5.0, 4.7], rowH: [0.5, 0.78, 0.78, 0.78, 0.78], fontSize: 16 });
  foot(s, "The first three put the controls in the answer. The fourth puts them in a document beside it.");
  s.addNotes("HAL-FORMS is the default answer of this service and of Spring HATEOAS. HAL Schema Forms adds JSON Schema and the HEART profile.");
});

// 19. Act VI: the server refuses, with a reason
add("CONTENT", (s) => {
  head(s, "ACT VI  ·  THE SERVER’S CONTROLS", "Refused, and told why, before anything is written");
  const codes = [
    ["409", "Not offered", "The act isn’t allowed in this state: dismiss before notice."],
    ["412", "Stale", "Someone changed it since you read it: read again, decide again."],
    ["428", "No validator", "A write must carry the ETag it read."],
    ["422", "Doesn’t fit", "The body breaks the form’s schema, at the field named."],
  ];
  codes.forEach(([n, name, why], i) => {
    const x = 0.6 + i * 3.08;
    box(s, x, 1.8, 2.85, 2.5, i === 1 ? C.ink : C.mist);
    const dark = i === 1;
    text(s, [
      { text: n, options: { fontSize: 44, bold: true, color: dark ? C.gold : C.rust, breakLine: true } },
      { text: name, options: { fontSize: 20, bold: true, color: dark ? C.white : C.ink, breakLine: true } },
      { text: why, options: { fontSize: 15, color: dark ? C.pale : C.ink, paraSpaceBefore: 6 } },
    ], { x: x + 0.25, y: 1.95, w: 2.35, h: 2.65 });
  });
  text(s, [
    { text: "Each reason travels as problem details (RFC 9457). ", options: {} },
    { text: "On HEART, code mode met 57 refusals of 428 and then sent the ETag; ARTIE sent one with every act, 43 of 43.", options: { bold: true } },
  ], { x: 0.6, y: 4.65, w: 12.1, h: 0.9, fontSize: 17 });
  foot(s, "A control guards only what the client pairs with it: send the values and the ETag of the same read.");
  s.addNotes("The server's guard holds for every client. What it cannot see is a client that pairs a fresh ETag with stale values.");
});

// 20. Act VI: your own controls
add("CONTENT", (s) => {
  head(s, "ACT VI  ·  CONTROLS OF YOUR OWN", "Don’t own the API? Put your own controls in front");
  box(s, 0.6, 1.9, 3.4, 2.2, C.mist);
  text(s, [
    { text: "A service you don’t control", options: { bold: true, fontSize: 19, breakLine: true } },
    { text: "GitHub, for example: its owners set the rules, and its API lists every operation.", options: { fontSize: 15, paraSpaceBefore: 6 } },
  ], { x: 0.85, y: 2.2, w: 2.9, h: 2.0 });
  arrow(s, 4.1, 3.0, 0.5, 0.4);
  box(s, 4.7, 1.9, 3.6, 2.2, C.green);
  text(s, [
    { text: "Your gateway", options: { bold: true, fontSize: 19, color: C.white, breakLine: true } },
    { text: "OpenAPI in, HEART out: forms only when an act is allowed, ETags on every write, refusals with reasons.", options: { fontSize: 15, color: C.white, paraSpaceBefore: 6 } },
  ], { x: 4.95, y: 2.2, w: 3.1, h: 2.0 });
  arrow(s, 8.4, 3.0, 0.5, 0.4);
  box(s, 9.0, 1.9, 3.7, 2.2, C.ink);
  text(s, [
    { text: "A person, an agent, a script", options: { bold: true, fontSize: 19, color: C.white, breakLine: true } },
    { text: "Doing what you want, not merely what the API allows.", options: { fontSize: 15, color: C.pale, paraSpaceBefore: 6 } },
  ], { x: 9.25, y: 2.2, w: 3.2, h: 2.0 });
  text(s, [
    { text: "In an API you own, build the controls in from the start: stronger still.", options: { bold: true, color: C.green, breakLine: true } },
    { text: "The rest of this deck uses a service built that way: the employees service, its HEART side and its OpenAPI twin.", options: { color: C.sage } },
  ], { x: 0.6, y: 4.5, w: 12.1, h: 1.2, fontSize: 18 });
  s.addNotes("GitHub is only the example, chosen because we don't own it. The same gateway idea applies to any internal system behind an OpenAPI description.");
});

// 21. Act VII: ARTIE
add("CONTENT", (s) => {
  head(s, "ACT VII  ·  ARTIE, THE GENERIC CLIENT", "ARTIE is to HEART what a browser is to HTML");
  box(s, 0.6, 2.2, 2.9, 2.6, C.ink);
  text(s, [
    { text: "ARTIE", options: { fontSize: 22, bold: true, color: C.white, breakLine: true } },
    { text: "one generic client", options: { fontSize: 14, color: C.pale } },
  ], { x: 0.85, y: 2.5, w: 2.5, h: 1.2 });
  text(s, "Autonomous Restful Traversal and Interactive Engine.", { x: 0.85, y: 3.75, w: 2.5, h: 0.9, fontSize: 13, color: C.pale });
  arrow(s, 3.55, 3.3, 0.5, 0.4);
  box(s, 4.1, 2.2, 3.4, 2.6, C.green);
  text(s, [
    { text: "HAL + HEART", options: { fontFace: MONO, fontSize: 20, bold: true, color: C.white, breakLine: true } },
    { text: "links, forms, JSON Schema, ETags, problem details", options: { fontSize: 14, color: C.white, paraSpaceBefore: 8 } },
  ], { x: 4.35, y: 2.5, w: 2.95, h: 2.1 });
  arrow(s, 7.55, 3.3, 0.5, 0.4);
  ["An employees service", "GitHub, through a gateway", "A garden", "Any HEART service not yet built"].forEach((w, i) => {
    box(s, 8.2, 1.95 + i * 0.8, 4.5, 0.62, C.mist);
    text(s, w, { x: 8.45, y: 1.95 + i * 0.8, w: 4.1, h: 0.62, fontSize: 16, valign: "middle" });
  });
  text(s, "It follows links and forms, sends the ETag it read, and caches what it may. It runs a goal in plain words with a model, and a kept script with none.",
    { x: 0.6, y: 5.3, w: 12.1, h: 0.9, fontSize: 18, bold: true, color: C.green });
  s.addNotes("The same coupling as the browser: to formats, not to any API. Nothing in ARTIE knows about employees.");
});

// 22. Act VII: wayfinding
add("CONTENT", (s) => {
  head(s, "ACT VII  ·  WAYFINDING: SYNC, REASON, ACT", "The model picks only from what is offered");
  const steps = [
    ["1  Sync", "no model", "Read the representation; fetch its forms and schemas by link; revalidate with ETags."],
    ["2  Reason", "the model", "It sees the state, the forms offered now and the step’s words, and picks one form and its values. ARTIE checks them against the schema."],
    ["3  Act", "no model", "Send it with If-Match. A 412 reads again and decides again."],
  ];
  steps.forEach(([name, who, what], i) => {
    const x = 0.6 + i * 2.2;
    const on = i === 1;
    box(s, 0.6, 1.65 + i * 1.5, 5.4, 1.3, on ? C.green : C.mist, on ? C.green : C.pale);
    text(s, [
      { text: name + "   ", options: { bold: true, fontSize: 18, color: on ? C.white : C.ink } },
      { text: who, options: { italic: true, fontSize: 13, color: on ? C.gold : C.sage, breakLine: true } },
      { text: what, options: { fontSize: 14, color: on ? C.white : C.ink, paraSpaceBefore: 4 } },
    ], { x: 0.8, y: 1.72 + i * 1.5, w: 5.0, h: 1.2 });
    void x;
  });
  label(s, 6.4, 1.5, 6.3, "WHAT THE MODEL SAW, AND CHOSE (FROM THE TRACE)");
  code(s, 6.4, 1.85, 6.3, 4.1, [
    "<state>",
    "{\"id\":5,\"firstName\":\"Maria\",\"lastName\":\"Miller\",",
    " \"role\":\"clerk\",\"status\":\"on notice\"}",
    "</state>",
    "<offers>",
    "{\"staff:dismiss\":{\"title\":\"Dismiss the employee\",…},",
    " \"staff:update\":{\"title\":\"Update the employee\",…}}",
    "</offers>",
    "<intent>",
    "dismiss this employee",
    "</intent>",
    "",
    "# chose",
    "⟦select: act staff:dismiss, fields {}⟧",
  ], { size: 12 });
  foot(s, "Every exchange is traced. From the trace, artie generate writes the way down.");
  s.addNotes("The model never builds an address or invents an act. It picks one of the offered forms; ARTIE does the rest deterministically.");
});

// 23. Act VIII: a goal
add("CONTENT", (s) => {
  head(s, "ACT VIII  ·  FIND THE WAY ONCE", "A goal, in plain words");
  label(s, 0.6, 1.5, 7.6, "ARTIE/T5.GOAL.MD");
  code(s, 0.6, 1.85, 7.6, 4.3, [
    "---",
    "start: http://localhost:8099/",
    "limits: { moves: 100 }",
    "---",
    "",
    "Move every employee named Miller one step",
    "through leaving: give notice to anyone working,",
    "dismiss anyone on notice, and leave the",
    "dismissed as they are.",
    "",
    "- Find the employees named Miller",
    "  - Move each of them one step: give notice to",
    "    anyone working, dismiss anyone on notice, and",
    "    leave the dismissed as they are ⟦`moves 60`⟧",
  ], { size: 13 });
  note(s, 1, 8.6, 1.9, 4.1, "A bookmark, and the words. No link, form or field is named.");
  note(s, 2, 8.6, 2.9, 4.1, "`moves 60`: the most moves that step may take.");
  note(s, 3, 8.6, 3.9, 4.1, "Run once with the model, at 6 employees: 48,491 input tokens, 18 calls.");
  note(s, 4, 8.6, 4.9, 4.1, "It searched, opened each employee, gave notice or dismissed, and went back.");
  s.addNotes("artie run t5.goal.md --reasoner claude. The goal run is the exploratory pass that finds the way.");
});

// 24. Act VIII: artie generate
add("CONTENT", (s) => {
  head(s, "ACT VIII  ·  ARTIE GENERATE", "The run, written down as a loop");
  label(s, 0.6, 1.5, 7.6, "ARTIE/T5.GENERATED.HEART.YAML");
  code(s, 0.6, 1.85, 7.6, 4.75, [
    "# Generated by artie generate from the run of t5.goal.md.",
    "heart: 1",
    "kind: kept",
    "start: http://localhost:8099/",
    "steps:",
    "  - act: staff:search-employees",
    "    fields: { name: Miller }",
    "    then:",
    "      - ⟦whenever⟧:",
    "          - ⟦when-offered⟧:",
    "              - act: staff:give-notice",
    "                from-intent: \"Move each of them one step: …\"",
    "              - act: staff:dismiss",
    "                from-intent: \"Move each of them one step: …\"",
    "            ⟦otherwise⟧:",
    "              - note: none of these acts is offered here",
  ], { size: 13 });
  note(s, 1, 8.6, 1.9, 4.1, "whenever: each member of the list, by its link.");
  note(s, 2, 8.6, 2.75, 4.1, "when-offered: the first case whose act the employee offers now.");
  note(s, 3, 8.6, 3.75, 4.1, "otherwise: nothing to do, and no model.");
  note(s, 4, 8.6, 4.6, 4.1, "Pinned to the forms the run was offered, held to before every act.");
  note(s, 5, 8.6, 5.6, 4.1, "The run opened six employees; generate wrote one loop, not six steps.");
  s.addNotes("Trimmed for the slide: the inferred lines and the comments. The whole file is in the report. Data in a closed vocabulary, not code: no sandbox needed.");
});

// 25. Act VIII: the server decides the state
add("CONTENT", (s) => {
  head(s, "ACT VIII  ·  WHO DECIDES THE STATE", "The server decides; the script reads the offer");
  label(s, 0.6, 1.55, 6.0, "THE KEPT SCRIPT");
  const flow = ["Search by surname", "Each employee in the list", "What does it offer now?"];
  flow.forEach((w, i) => {
    box(s, 0.6, 1.95 + i * 1.0, 5.6, 0.62, i === 2 ? C.ink : C.mist);
    text(s, w, { x: 0.6, y: 1.95 + i * 1.0, w: 5.6, h: 0.62, fontSize: 16, bold: i === 2, color: i === 2 ? C.white : C.ink, align: "center", valign: "middle" });
    if (i < 2) downArrow(s, 3.2, 2.6 + i * 1.0, 0.4, 0.3);
  });
  downArrow(s, 3.2, 4.6, 0.4, 0.3);
  pill(s, 0.6, 5.0, 1.75, "give notice", true);
  pill(s, 2.52, 5.0, 1.75, "dismiss", true);
  pill(s, 4.44, 5.0, 1.76, "nothing: note", false);
  label(s, 6.9, 1.55, 5.8, "CODE MODE’S PROGRAM");
  code(s, 6.9, 1.95, 5.8, 1.6, [
    "k={'working':'giveNotice',",
    "   'on notice':'dismiss'}",
    "  .get(e['status'])",
  ], { size: 14 });
  text(s, [
    { text: "The client decides, from a status string it read once.", options: { bold: true, breakLine: true } },
    { text: "A state with a new name, or a new act, and the map is quietly wrong.", options: { color: C.rust } },
  ], { x: 6.9, y: 3.75, w: 5.8, h: 1.2, fontSize: 16 });
  text(s, "In ARTIE’s script, an offer no case names falls back to the step’s words: the model chooses from what is offered.",
    { x: 6.9, y: 4.95, w: 5.8, h: 0.9, fontSize: 15, color: C.green, bold: true });
  foot(s, "The condition for each branch is the offer. The script never reads a status to guess what is allowed.");
  s.addNotes("Both loop. The difference is where the rule lives: in the server's offers, or copied into the client.");
});

// 26. Act VIII: when the service changes
add("CONTENT", (s) => {
  head(s, "ACT VIII  ·  WHEN THE SERVICE CHANGES", "Pins catch a change before any request");
  const steps = [
    ["Next step", C.mist, C.ink],
    ["Pin holds?", C.ink, C.white],
  ];
  steps.forEach(([w, fill, color], i) => {
    box(s, 0.6 + i * 2.3, 1.85, 2.0, 0.7, fill);
    text(s, w, { x: 0.6 + i * 2.3, y: 1.85, w: 2.0, h: 0.7, fontSize: 16, bold: true, color, align: "center", valign: "middle" });
  });
  arrow(s, 2.63, 2.03, 0.24, 0.34);
  arrow(s, 4.93, 1.75, 0.5, 0.34, C.green);
  text(s, "yes", { x: 4.93, y: 1.5, w: 0.6, h: 0.25, fontSize: 11, color: C.green, bold: true });
  box(s, 5.5, 1.62, 2.6, 0.55, C.green);
  text(s, "Act, no model", { x: 5.5, y: 1.62, w: 2.6, h: 0.55, fontSize: 15, bold: true, color: C.white, align: "center", valign: "middle" });
  arrow(s, 4.93, 2.3, 0.5, 0.34, C.rust);
  text(s, "changed", { x: 4.85, y: 2.62, w: 0.9, h: 0.25, fontSize: 11, color: C.rust, bold: true });
  box(s, 5.5, 2.22, 2.6, 0.55, C.rust);
  text(s, "Fall back to the words", { x: 5.5, y: 2.22, w: 2.6, h: 0.55, fontSize: 15, bold: true, color: C.white, align: "center", valign: "middle" });
  arrow(s, 8.15, 2.3, 0.4, 0.34);
  box(s, 8.6, 2.22, 4.1, 0.55, C.mist);
  text(s, "The model chooses from what is offered now", { x: 8.6, y: 2.22, w: 4.1, h: 0.55, fontSize: 14, align: "center", valign: "middle" });
  text(s, "A release renamed role to position in two forms.", { x: 0.6, y: 3.15, w: 6.0, h: 0.45, fontSize: 18, bold: true });
  text(s, [
    { text: "ARTIE’s pins refused the changed forms before any write.", options: { bullet: BULLET, breakLine: true } },
    { text: "Each step fell back to its words; the model chose position: 3 calls, 4,381 tokens, once.", options: { bullet: BULLET, breakLine: true } },
    { text: "The repair was kept in the script and its pins: the runs after it used no model.", options: { bullet: BULLET } },
  ], { x: 0.6, y: 3.65, w: 6.0, h: 2.5, fontSize: 16, paraSpaceAfter: 8 });
  label(s, 6.9, 3.15, 5.8, "WHAT KEEP WROTE: THE DIFF A REVIEWER SEES");
  code(s, 6.9, 3.5, 5.8, 2.6, [
    "@@ steps: staff:hire",
    "     fields:",
    "       firstName: Samwise",
    "       lastName: Gamgee",
    "⟦-      role: gardener⟧",
    "⟦+      position: gardener⟧",
    "@@ pins: staff:hire",
    "⟦-    integrity: sha256-mxzDdaZ5uWQZ…⟧",
    "⟦+    integrity: sha256-qcwlYZ6VgYc/…⟧",
  ], { size: 12 });
  foot(s, "Kept without a person only where every value sent is the script’s own; otherwise proposed for review.");
  s.addNotes("Self-repair at the edge. The model is used where something changed, once, and the result is kept as data a person can review in a few lines of diff.");
});

// 27. Act IX: what ran
add("CONTENT", (s) => {
  head(s, "ACT IX  ·  THE EVIDENCE", "Many records, a few states: what ran");
  table(s, [
    ["An employee’s state", "What the server offers"],
    ["working", "give notice, update"],
    ["on notice", "dismiss, update"],
    ["dismissed", "nothing"],
  ], { x: 0.6, y: 1.7, w: 5.4, colW: [2.4, 3.0], rowH: 0.55, fontSize: 15 });
  text(s, "Each run seeded employees named Miller through the service’s own acts: a third in each state. 6, 30 and 90 employees; 5 runs each.",
    { x: 0.6, y: 4.1, w: 5.4, h: 1.6, fontSize: 15, color: C.sage });
  const clients = [
    ["ARTIE, the generated script", "The goal run once at 6 employees; artie generate wrote the script; ARTIE ran it.", C.green],
    ["ARTIE, one case", "The same loop with give notice alone: an employee on notice falls back to the words.", C.ink],
    ["Code mode, fresh", "Python against a library made from the OpenAPI description, every run.", C.ink],
    ["Code mode, HAL-FORMS alone", "Python against the hypermedia, told only the address; the description withheld.", C.ink],
  ];
  clients.forEach(([name, what, color], i) => {
    const y = 1.7 + i * 1.12;
    box(s, 6.5, y, 6.2, 0.98, C.mist);
    text(s, [
      { text: name, options: { bold: true, color, breakLine: true, fontSize: 16 } },
      { text: what, options: { fontSize: 13 } },
    ], { x: 6.7, y: y + 0.08, w: 5.8, h: 0.85 });
  });
  foot(s, "Right means: each working employee now on notice, each on notice now dismissed, the dismissed left, no one else touched.");
  s.addNotes("One service, Claude Sonnet 5.5, code mode with 16 turns and a locked-down sandbox. The bench is exhibits/mcp-over-openapi/bench.py.");
});

// 28. Act IX: the model per run
add("CONTENT", (s) => {
  head(s, "ACT IX  ·  THE MODEL, PER RUN", "No model at any size, and three form sets a run");
  const us = (t) => ({ text: t, bold: true, color: C.green });
  table(s, [
    ["Client (median of 5)", "6 employees", "30 employees", "90 employees"],
    [{ text: "ARTIE, the generated script", color: C.green }, us("0 tokens, 0 calls"), us("0 tokens, 0 calls"), us("0 tokens, 0 calls")],
    ["ARTIE, one case", "2,544 tokens, 2 calls", "12,720 tokens, 10 calls", "38,160 tokens, 30 calls"],
    ["Code mode, fresh", "10,166 tokens, 5 calls", "10,374 tokens, 5 calls", "10,225 tokens, 5 calls"],
    ["Code mode, HAL-FORMS alone", "24,444 tokens, 7 calls", "36,380 tokens, 8 calls", "35,348 tokens, 8 calls"],
  ], { x: 0.6, y: 1.7, w: 12.1, colW: [3.7, 2.8, 2.8, 2.8], rowH: [0.55, 0.6, 0.6, 0.6, 0.6], fontSize: 15 });
  big(s, 0.6, 4.85, 3.6, "3", "form sets fetched a run by ARTIE, at every size: one per state");
  big(s, 4.6, 4.85, 3.6, "15 of 15", "runs right, for every client");
  text(s, "Without a case for a state, the model is asked once per record in it. Keeping the new case after its first use takes that back to none (next to build).",
    { x: 8.6, y: 4.95, w: 4.1, h: 1.4, fontSize: 14, color: C.sage });
  s.addNotes("ARTIE's goal run cost 48,491 tokens once. Code mode writes a loop too, so its tokens hardly grow with the records, but it pays them in every run.");
});

// 29. Act IX: wire and time
add("CONTENT", (s) => {
  head(s, "ACT IX  ·  THE WIRE AND THE TIME", "Requests grow with the records, for every client");
  table(s, [
    ["Client (median of 5)", "Form sets fetched", "Requests: 6 / 30 / 90", "Seconds: 6 / 30 / 90", "Refused, 5 runs at 90"],
    [{ text: "ARTIE, the generated script", color: C.green }, "3 at every size", "28 / 110 / 313", { text: "1.1 / 1.7 / 3.1", bold: true, color: C.green }, "0"],
    ["ARTIE, one case", "3 at every size", "28 / 110 / 313", "5.3 / 25.4 / 75.1", "0"],
    ["Code mode, fresh", "none: no forms", "18 / 56 / 163", "11.4 / 13.5 / 14.8", "0"],
    ["Code mode, HAL-FORMS alone", "none: forms inline", "39 / 194 / 600", "21.8 / 28.5 / 29.2", { text: "307: 428 and 405", color: C.rust, bold: true }],
  ], { x: 0.6, y: 1.7, w: 12.1, colW: [3.3, 2.0, 2.3, 2.3, 2.2], rowH: [0.55, 0.6, 0.6, 0.6, 0.6], fontSize: 15 });
  text(s, [
    { text: "ARTIE reads each employee and returns to the list, revalidating as it goes; code mode on endpoints reads less.", options: { bullet: BULLET, breakLine: true } },
    { text: "Code mode on HAL-FORMS wrote without If-Match (428) or with the wrong method (405) before it got it right. The server refused each one; nothing wrong was written.", options: { bullet: BULLET, breakLine: true } },
    { text: "Each code-mode program also starts a sandbox: about 0.24 s more per program, measured here.", options: { bullet: BULLET } },
  ], { x: 0.6, y: 4.85, w: 12.1, h: 1.5, fontSize: 15, paraSpaceAfter: 6 });
  s.addNotes("The cost that follows the records is the wire, for every client. The model's cost follows the states for ARTIE, and the runs for code mode.");
});

// 30. Act IX: at a million runs
add("DARK", (s) => {
  head(s, "ACT IX  ·  AT SCALE", "A million runs of 90 employees", true);
  const cols = [
    ["ARTIE, the generated script", "48,491", "input tokens, all in the one goal run", "18", "model calls", "none", "sandbox"],
    ["Code mode, fresh", "10.2 billion", "input tokens", "5 million", "model calls", "263 hours, $224", "sandbox start time, hosted price"],
    ["Code mode, HAL-FORMS alone", "34.7 billion", "input tokens", "8.4 million", "model calls", "487 hours, $442", "sandbox start time, hosted price"],
  ];
  cols.forEach(([name, a, al, b, bl, c, cl], i) => {
    const x = 0.6 + i * 4.1;
    const us = i === 0;
    box(s, x, 1.65, 3.85, 4.4, us ? C.green : C.deep);
    text(s, name, { x: x + 0.25, y: 1.8, w: 3.4, h: 0.45, fontSize: 16, bold: true, color: C.white });
    [[a, al, 32], [b, bl, 26], [c, cl, 20]].forEach(([n, l, sz], j) => {
      const y = 2.35 + j * 1.2;
      text(s, n, { x: x + 0.25, y, w: 3.4, h: 0.6, fontSize: sz, bold: true, color: C.gold, valign: "bottom" });
      text(s, l, { x: x + 0.25, y: y + 0.6, w: 3.4, h: 0.4, fontSize: 12, color: C.pale });
    });
  });
  foot(s, "Projected from the measured runs at 90 employees, not run. Every client was right in every measured run, so the rate holds: 100%.", true, 6.25);
  s.addNotes("Sandbox: start time from 0.24 s more per program, 4 to 7 programs a run; hosted price at E2B's per-second rates for 1 vCPU and 256 MB. Priced with the prompt cache: ARTIE 19,558; code mode 2.79 and 11.4 billion.");
});

// 31. Act IX: through a release
add("CONTENT", (s) => {
  head(s, "ACT IX  ·  THROUGH A RELEASE", "Right, and at what cost, when the service changes");
  const bad = (t) => ({ text: t, bold: true, color: C.rust });
  const us = (t) => ({ text: t, bold: true, color: C.green });
  table(s, [
    ["Client", "Right of 20, measured", "Input tokens, 20 runs", "Right at 1,000,000 runs", "Input tokens at 1,000,000"],
    [{ text: "ARTIE, kept scripts", color: C.green }, us("20 (100%)"), "46,864, with its wayfinding", us("1,000,000 (100%)"), us("46,864")],
    ["Code mode, fresh", "20 (100%)", "158,995", "1,000,000 (100%)", "7.95 billion"],
    ["Code mode on HAL-FORMS alone", "20 (100%)", "316,303", "1,000,000 (100%)", "15.8 billion"],
    ["Code mode, kept programs", bad("12 (60%)"), "16,004", bad("12 (0.0012%)"), "16,004"],
  ], { x: 0.6, y: 1.7, w: 12.1, colW: [3.3, 2.1, 2.5, 2.2, 2.0], rowH: [0.7, 0.6, 0.6, 0.6, 0.6], fontSize: 15 });
  text(s, [
    { text: "Two tasks, 10 runs each: a teammate’s change in the middle of one, and a field renamed from run 7.", options: { bullet: BULLET, breakLine: true } },
    { text: "Kept programs were right only before the release, and wrong, silently, in every run after it.", options: { bullet: BULLET, breakLine: true } },
    { text: "ARTIE paid once per task to find the way (42,483 for both) and once to repair after the release (4,381); every other run used no model.", options: { bullet: BULLET } },
  ], { x: 0.6, y: 5.0, w: 12.1, h: 1.5, fontSize: 15, paraSpaceAfter: 6 });
  s.addNotes("From the Wayfinding Through Change runs, same service, same model. Projections hold each client's measured rate; one release in the period, as measured.");
});

// 31b. Act IX: code mode and ARTIE together
add("CONTENT", (s) => {
  head(s, "ACT IX  ·  CODE MODE AND ARTIE TOGETHER", "Code mode reads the task; ARTIE does the acts");
  const us = (t) => ({ text: t, bold: true, color: C.green });
  const bad = (t) => ({ text: t, bold: true, color: C.rust });
  table(s, [
    ["Client (two tasks, a teammate, a release)", "Right of 20", "Tokens, 20 runs", "100 runs", "The model"],
    [{ text: "ARTIE alone", color: C.green }, us("20 (100%)"), "46,864", us("46,864"), "the edges"],
    [{ text: "A harness running ARTIE, code mode on failure", color: C.green }, us("20 (100%)"), "56,366", us("56,366"), "first runs; ARTIE's at the release"],
    ["A harness running kept skills, code mode on failure", "20 (100%)", "74,415", "74,415", "first runs, teammate, release"],
    ["Code mode with ARTIE as its tool", "20 (100%)", "107,607", "354,235", "every run, choosing a script"],
    ["Code mode, fresh", "20 (100%)", "158,995", "794,975", "every run"],
    ["Code mode, keeping skills", "20 (100%)", "188,747", "845,282", "every run"],
    ["Code mode, kept programs", bad("12 (60%)"), "16,004", "16,004", "run 1"],
  ], { x: 0.6, y: 1.6, w: 12.1, colW: [4.9, 1.4, 1.7, 1.4, 2.7], rowH: [0.5, 0.44, 0.44, 0.44, 0.44, 0.44, 0.44, 0.44], fontSize: 13 });
  text(s, [
    { text: "One kept script, any surname: Miller at 6, Smith at 30, Garcia at 90 employees, 15 of 15 right, no model after the first run.", options: { bullet: BULLET, breakLine: true } },
    { text: "With ARTIE as its tool, code mode wrote no program and needed no sandbox; the model never held the token.", options: { bullet: BULLET } },
  ], { x: 0.6, y: 5.45, w: 12.1, h: 1.0, fontSize: 15, paraSpaceAfter: 6 });
  s.addNotes("Measured 3 November 2026 on the same bench. The harness: a plain runner replays the scripts ARTIE kept, and calls code mode only when ARTIE exits nonzero.");
});

// 31b2. Act IX: what each layer knows
add("CONTENT", (s) => {
  head(s, "ACT IX  ·  THE STACK OF COUPLINGS", "What each layer knows, and so what a release can break");
  const stack = (x, title, layers) => {
    label(s, x, 1.6, 5.8, title);
    layers.forEach(([name, knows, fill, color], i) => {
      const y = 2.0 + i * 0.82;
      box(s, x, y, 5.8, 0.72, fill);
      text(s, [
        { text: name, options: { bold: true, fontSize: 16, color: color || C.ink, breakLine: true } },
        { text: knows, options: { fontSize: 13, color: color ? C.pale : C.sage } },
      ], { x: x + 0.2, y: y + 0.06, w: 5.4, h: 0.62, valign: "middle" });
    });
  };
  stack(0.6, "KEPT CODE ON ENDPOINTS", [
    ["Task words", "what the person asked", C.mist],
    ["Kept program or skill", "field names, state rules, paging, IDs: copies of the domain", "FBE4E1"],
    ["SDK", "paths, methods, bodies", C.mist],
    ["OpenAPI description", "out of band: true when published", C.mist],
    ["Service", "its rules, true now", C.ink, C.white],
  ]);
  stack(6.9, "ARTIE ON HYPERMEDIA", [
    ["Goal", "the task, in plain words", "E3F1E2"],
    ["Kept script and pins", "relations, inputs, each form’s digest: a lock file", C.mist],
    ["ARTIE", "the media type, registered relations such as next", C.mist],
    ["Each answer", "in band: the state, and the controls allowed now", C.mist],
    ["Service", "its rules, true now", C.ink, C.white],
  ]);
  foot(s, "The higher a layer, the longer it should last. On endpoints the domain sits in kept code; on hypermedia it stays in the answers.", false, 6.2);
  s.addNotes("Read top down: each layer depends on the one below. A release changes the service; the question is how far up the change must travel. Next slide: measured.");
});

// 31c. Act IX: how far a change travels
add("CONTENT", (s) => {
  head(s, "ACT IX  ·  HOW FAR A CHANGE TRAVELS", "Kept code holds the domain; the goal holds the task");
  const ok = (t) => ({ text: t, fill: "E3F1E2", color: C.green, bold: true });
  const once = (t) => ({ text: t, fill: "FBF1DF", color: "8A5200", bold: true });
  const bad = (t) => ({ text: t, fill: "FBE4E1", color: C.rust, bold: true });
  table(s, [
    ["The change", "Kept programs", "Kept skill, run by a harness", "ARTIE’s kept script"],
    ["Acts moved to new addresses", ok("right*"), ok("right*"), ok("right: the pin holds the act")],
    ["Update became a PATCH", ok("right*"), ok("right*"), once("a new act: model once")],
    ["role renamed position", bad("422, exit 0: wrong"), once("422, then rewritten"), once("stopped before the write, repaired")],
    ["Dismiss needs notice first", bad("409, exit 0: wrong"), once("409, then rewritten"), once("gave notice, then dismissed")],
    ["Update needs a reason", bad("422, exit 0: wrong"), bad("made up a reason, and kept it"), once("stopped and asked for it")],
    ["More records than a page", bad("34 of 90, exit 0"), bad("50 of 90, exit 0"), ok("right: follows next")],
    ["A teammate changed him first", ok("right"), once("412, rewritten twice"), ok("412, read again: no model")],
  ], { x: 0.6, y: 1.6, w: 12.1, colW: [3.4, 2.6, 3.0, 3.1], rowH: [0.46, 0.44, 0.44, 0.44, 0.44, 0.44, 0.44, 0.44], fontSize: 14 });
  text(s, [
    { text: "A pin holds what a form does, not where it is: a move goes on with no model; a new method or field is a new act.", options: { bullet: BULLET, breakLine: true } },
    { text: "The goal changed for one of the seven: a reason, which only a person can give. ARTIE asked; the skill made one up.", options: { bullet: BULLET, breakLine: true } },
    { text: "The kept script is the goal’s lock file: a release re-records it, and a person reviews the diff.", options: { bullet: BULLET } },
  ], { x: 0.6, y: 5.3, w: 12.1, h: 1.15, fontSize: 15, paraSpaceAfter: 4 });
  foot(s, "* The SDK was generated from the description at the start of every run. Task t1, one run per release, on the way kept before it.", false, 6.5);
  s.addNotes("Measured 4 November 2026. Kept programs and skills copy the domain: field names, state rules, paging. A pin holds what a form does, not where it is: its act, the method, content type, template pointers and schema with the target set aside. ARTIE never builds an address, so a moved form goes on with no model; a new method is a new act, and called the model once (1,265 tokens). Strict stops at any change. The rename and teammate rows are from the 20-run sequence; the paging from the runs at 90 employees.");
});

// 32. Close: where the model earns its keep
add("CONTENT", (s) => {
  head(s, "CLOSE  ·  WHERE THE MODEL EARNS ITS KEEP", "Find the way once; the model at the edges");
  card(s, 0.6, 1.75, 5.9, 2.1, "Work that is new every run", "A model in the middle of every run earns its keep: code mode, tools, or ARTIE running a goal.", { size: 17 });
  card(s, 6.8, 1.75, 5.9, 2.1, "The same work, in bulk", "Find the way once, keep it, run it with none. The model returns only at the edges: a changed form, a state not met before.", { fill: C.green, size: 17 });
  text(s, [
    { text: "They combine. ", options: { bold: true } },
    { text: "Code mode gave ARTIE each task as a goal once and ran what ARTIE kept: right 20 of 20. Run by a harness that calls code mode only when ARTIE fails, it cost 56,366 tokens in all.", options: {} },
  ], { x: 0.6, y: 4.2, w: 12.1, h: 1.2, fontSize: 18 });
  foot(s, "The middle ground: services stable in shape for resources of one type in one state, where repeatability and cost matter.");
  s.addNotes("Not against code mode: about where the model sits. Inference spent once, to make the work deterministic.");
});

// 33. Close: limits
add("CONTENT", (s) => {
  head(s, "CLOSE  ·  HONEST LIMITS", "What this does and doesn’t show");
  card(s, 0.6, 1.7, 5.9, 3.9, "Limits", [
    "One service, one model (Sonnet 5.5), one goal; 5 runs per size.",
    "Employees seeded the same way each run: the same states in the same proportions.",
    "The one-case script was written by hand, as generate would write it.",
    "Keeping a new case for a state met the first time: not built yet.",
    "A million runs is arithmetic on measured runs, not runs.",
  ], { size: 15 });
  card(s, 6.8, 1.7, 5.9, 3.9, "Where code mode did well", [
    "Fresh, it was right every time.",
    "It adapts to a release with no machinery of its own: it reads the service again every run.",
    "Told only the address, it found its way on HAL-FORMS alone: no model has seen this service.",
    "A status check in a kept program would have made it fail loudly, and be written again.",
  ], { size: 15 });
  s.addNotes("Said plainly, so the comparison is fair.");
});

// 34. Closing
add("DARK", (s) => {
  text(s, "THE WEB DID IT FOR PEOPLE", { x: 0.8, y: 1.6, w: 11.7, h: 0.5, fontSize: 16, bold: true, color: C.gold, charSpacing: 2 });
  text(s, "Agents get the same", { x: 0.8, y: 2.2, w: 11.7, h: 1.6, fontSize: 60, bold: true, color: C.white, valign: "middle" });
  text(s, "Controls in the answer, true now. A way found once, kept as data, and held to those controls every run after.",
    { x: 0.8, y: 3.9, w: 10.5, h: 1.4, fontSize: 22, color: C.pale });
  pill(s, 0.8, 5.75, 2.2, "Find the way once", true);
  pill(s, 3.25, 5.75, 2.2, "Keep it", true);
  pill(s, 5.7, 5.75, 2.6, "Run it with no model", true);
  s.addNotes("Close on the controls: they are what let a client find its way, and what tell a kept way that something changed.");
});

// 35. Sources
add("CONTENT", (s) => {
  head(s, "SOURCES", "Quotes, history and evidence");
  text(s, [
    "Roy T. Fielding, Architectural Styles and the Design of Network-based Software Architectures, doctoral dissertation, UC Irvine, 2000, ch. 5 (§5.1.5).",
    "Roy T. Fielding, “REST APIs must be hypertext-driven”, Untangled, 20 October 2008: roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven; the hypertext, browser and decades quotes are his replies in its comments, October 2008.",
    "Vannevar Bush, “As We May Think”, The Atlantic, 1945. Ted Nelson, ACM, 1965. Douglas Engelbart, demonstration, 1968. Apple HyperCard, 1987.",
    "Tim Berners-Lee, “Information Management: A Proposal”, CERN, 1989. HTML 2.0, RFC 1866, 1995. SOAP 1.1, W3C Note, 2000; WSDL 1.1, 2001. JSON, RFC 4627, 2006. Swagger, 2011; OpenAPI Initiative, 2015.",
    "Cloudflare, “Code Mode”, 2025. Anthropic, “Code execution with MCP”, 2025. Problem details, RFC 9457. HAL; HAL-FORMS; Spring HATEOAS.",
    "Runs: exhibits/mcp-over-openapi in the artie repository, 2–3 November 2026: the employees service (heart-sims), Claude Sonnet 5.5. Reports: “Many Records, a Few States” and “Wayfinding Through Change”. Sandbox prices at E2B’s per-second rates.",
  ].map((t, i, all) => ({ text: t, options: { bullet: BULLET, breakLine: i < all.length - 1 } })),
  { x: 0.6, y: 1.65, w: 12.1, h: 4.9, fontSize: 15, paraSpaceAfter: 10 });
  s.addNotes("Every figure in the evidence slides is from those runs; projections are marked as projections.");
});

// ---------------------------------------------------------------- build

function deck(which) {
  const pptx = new pptxgen();
  pptx.layout = "LAYOUT_WIDE";
  pptx.title = "Find the Way Once";
  pptx.author = "Josh Gough";
  pptx.theme = { headFontFace: SERIF, bodyFontFace: SERIF };
  const number = { x: 12.2, y: 7.0, w: 0.6, h: 0.3, fontFace: SERIF, fontSize: 10, align: "right" };
  pptx.defineSlideMaster({ title: "CONTENT", background: { color: C.white }, slideNumber: Object.assign({ color: C.sage }, number) });
  pptx.defineSlideMaster({ title: "DARK", background: { color: C.ink }, slideNumber: Object.assign({ color: C.sage }, number) });
  which.forEach(({ master, build }) => build(pptx.addSlide({ masterName: master })));
  return pptx;
}

async function main() {
  const args = process.argv.slice(2);
  const out = args[0] && !args[0].startsWith("--") ? args[0] : path.join(__dirname, "find-the-way-once.pptx");
  await deck(slides).writeFile({ fileName: out });
  console.log(`${slides.length} slides: ${out}`);
  const each = args.indexOf("--each");
  if (each >= 0) {
    const dir = args[each + 1];
    for (let i = 0; i < slides.length; i++) {
      await deck([slides[i]]).writeFile({ fileName: path.join(dir, `s${String(i + 1).padStart(2, "0")}.pptx`) });
    }
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
