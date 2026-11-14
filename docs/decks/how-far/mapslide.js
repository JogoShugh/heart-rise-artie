// The whole story on one slide: drawn into a slide the caller made. mode "path" dims all but the gold path.
//   drawMap(pres, slide, icons, "full" | "path")
module.exports = function drawMap(pres, s, I, mode) {
const pathOnly = mode === "path";
const W = "FFFFFF", GOLD = "E3B341", RUST = "D9653B", SAGE = "A9B8AE", PALE = "EDF2EE";
const T = (t, o) => s.addText(t, { margin: 0, isTextBox: true, fontFace: "Calibri", color: W, valign: "middle", ...o });
const box = (x, y, w, h, fill, o = {}) => s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.08, fill: { color: fill, transparency: o.t ?? 0 }, line: { color: o.line ?? fill, width: o.lw ?? 0.75, transparency: o.lt ?? 0 } });
const line = (x, y, w, h, color, o = {}) => s.addShape(pres.shapes.LINE, { x, y, w, h, line: { color, width: o.w ?? 2.5, dashType: o.dash ?? "solid", endArrowType: o.arrow }, flipV: o.flipV, flipH: o.flipH });
const num = (x, y, n) => { s.addShape(pres.shapes.OVAL, { x, y, w: 0.34, h: 0.34, fill: { color: GOLD }, line: { color: "17201B", width: 1.5 } }); T(String(n), { x, y, w: 0.34, h: 0.34, fontSize: 13, bold: true, color: "17201B", align: "center" }); };
const pill = (x, y, w, t, fill, color = "17201B") => { box(x, y, w, 0.26, fill); T(t, { x, y, w, h: 0.26, fontSize: 9, bold: true, color, align: "center" }); };

T(pathOnly ? "THE WHOLE STORY \u00B7 WHERE WE\u2019RE GOING" : "THE WHOLE STORY, ON ONE PAGE", { x: 0.4, y: 0.28, w: 8, h: 0.3, fontSize: 11, bold: true, color: GOLD, charSpacing: 3 });
T("Think once. Look and act for free. Own the controls.", { x: 0.4, y: 0.58, w: 12.5, h: 0.6, fontSize: 28, bold: true, fontFace: "Cambria" });

const bands = [
  ["THINK", "once", "2C5E45"], ["LOOK · ACT", "0 tokens a replay", "2F7D55"], ["THE WIRE", "one media type", "24503B"],
  ["OUR CONTROLS", "in front of theirs", "3A5A48"], ["THEIRS", "native APIs", "39403B"],
];
bands.forEach(([head, sub, fill], i) => {
  const y = 1.25 + i * 0.95;
  box(2.5, y, 8.25, 0.88, fill);
  T(head, { x: 2.65, y: y + 0.14, w: 1.3, h: 0.3, fontSize: 11, bold: true, color: GOLD, charSpacing: 1 });
  T(sub, { x: 2.65, y: y + 0.44, w: 1.3, h: 0.3, fontSize: 9, italic: true, color: SAGE });
});
// scm: GitLab and GitHub behind one set of typed relations
box(7.12, 5.1, 3.56, 0.7, "26332B", { line: GOLD, lw: 1 });
T([{ text: "scm:", options: { bold: true, color: GOLD, fontFace: "Courier New" } }, { text: "  one set of typed relations, two forges", options: { color: PALE, italic: true } }], { x: 7.22, y: 5.11, w: 3.4, h: 0.2, fontSize: 8.5 });
// The path, drawn first so the boxes sit on it and it shows in the gaps
line(4.3, 1.6, 5.3, 0, GOLD); line(9.6, 1.6, 0, 3.85, GOLD); line(5.7, 5.45, 3.9, 0, GOLD);
// The repair loop
line(4.05, 5.76, 1.67, 0, RUST, { dash: "dash", w: 2 }); line(5.72, 5.65, 0, 0.11, RUST, { dash: "solid", w: 2 }); line(4.05, 1.98, 0, 3.78, RUST, { dash: "dash", w: 2 }); line(4.05, 1.98, 2.9, 0, RUST, { dash: "dash", w: 2 }); line(6.95, 1.87, 0, 0.11, RUST, { w: 2, arrow: "triangle", flipV: true });

const chip = (x, y, w, t, o = {}) => { const h = o.h ?? 0.5; box(x, y, w, h, o.fill ?? "1B2B22", { t: 0, line: o.line ?? "4A5E52", lt: 0, lw: o.line ? 1.5 : 0.75 });
  const is = o.isz ?? 0.22; if (o.icon) s.addImage({ data: I[o.icon], x: x + 0.12, y: y + (h - is) / 2, w: is, h: is });
  const pad = o.icon ? 0.14 + is : 0.08; T(t, { x: x + pad, y, w: w - pad - 0.08, h, fontSize: o.size ?? 11, bold: !!o.bold, color: o.color ?? W, align: "center", fontFace: o.mono ? "Courier New" : "Calibri" }); };
const sub = (y, t) => T(t, { x: 4.2, y: y + 0.63, w: 6.5, h: 0.22, fontSize: 9, color: SAGE });

// 1 Think
let y = 1.25 + 0.1;
chip(4.25, y, 2.0, "release.goal.md\nthe goal, in words", { size: 10, icon: "mdEDF2EE" }); num(4.12, y - 0.12, 1);
chip(6.45, y, 2.0, "Claude captures\n20 calls, once", { size: 10, icon: "claudeD97757" }); num(6.32, y - 0.12, 2);
chip(8.65, y, 1.95, "kept YAML + pins", { size: 10, bold: true, icon: "yamlEDF2EE" }); num(8.52, y - 0.12, 3);
sub(1.25, ""); pill(9.0, 1.25 + 0.64, 1.6, "Hightower · ZTA", "E3B341");
// 2 Look, act
y = 2.2 + 0.1;
chip(4.25, y, 1.25, "browser", { size: 10, icon: "globeEDF2EE" }); chip(5.6, y, 1.25, "heart-ts", { size: 10, mono: true, icon: "tsEDF2EE" }); chip(6.95, y, 1.35, "heart-mcp", { size: 10, mono: true, icon: "mcpEDF2EE" });
chip(8.65, y, 1.95, "ARTIE", { size: 14, bold: true, line: GOLD, icon: "compassE3B341", isz: 0.28 }); num(8.52, y - 0.12, 4);
sub(2.2, "generic clients: pin check · If-Match · signed · L2 cache");
// 3 Wire
y = 3.15 + 0.1;
chip(4.25, y, 2.45, "HEART = HAL + HAL Schema Forms", { size: 10, bold: true, icon: "heartE5484D" }); chip(6.8, y, 1.0, "L0 · L1 · L2", { size: 9.5, icon: "layersEDF2EE", isz: 0.18 });
chip(7.9, y, 1.3, "scm: relations", { size: 9, mono: true, icon: "gitEDF2EE", isz: 0.18 }); chip(9.3, y, 1.3, "hr:unavailable", { size: 7.5, mono: true, icon: "infoEDF2EE", isz: 0.15 });
T([{ text: "RISE, dotted blue: ", options: { color: "6FA8DC", bold: true } }, { text: "watch now, be told later", options: { color: SAGE } }], { x: 4.2, y: 3.15 + 0.63, w: 2.6, h: 0.22, fontSize: 9 }); pill(6.85, 3.15 + 0.64, 2.2, "Fielding · enter by bookmark", "D9C9C2", "8A3C1E");
// 4 Our controls
y = 4.1 + 0.1;
chip(4.25, y, 1.45, "state-scoped\nacts", { size: 9.5, icon: "toggleEDF2EE" }); chip(5.8, y, 1.5, "412\u00B7409\u00B7405", { size: 9.5, mono: true, icon: "slashEDF2EE", isz: 0.18 }); chip(7.4, y, 1.1, "watcher", { size: 10, bold: true, icon: "bellA9B8AE", isz: 0.2 });
chip(8.65, y, 1.95, "gateway\nYAML connectors", { size: 10, bold: true, line: GOLD, icon: "shieldE3B341", isz: 0.26 }); num(8.52, y - 0.12, 5);
sub(4.1, "acts offered only when the state allows; refusals explained \u00B7 runs on your laptop, in front of GitHub");
// 5 Not ours
y = 5.05 + 0.1;
chip(4.25, y, 0.9, "preview", { size: 8, color: SAGE, icon: "awsA9B8AE", isz: 0.24 }); chip(5.24, y, 0.85, "Dev", { size: 11, icon: "serverEDF2EE" });
chip(6.17, y, 0.88, "Jenkins", { size: 11, icon: "jenkinsE8735F", isz: 0.22, size: 10 }); chip(7.2, 5.33, 1.12, "GitLab", { h: 0.4, size: 11, icon: "gitlabFC6D26", isz: 0.26 });
chip(8.65, 5.33, 1.95, "GitHub", { h: 0.4, size: 13, bold: true, icon: "githubFFFFFF", isz: 0.28 }); num(10.42, 5.22, 6);
 (() => 0)(5.05, "merge · build · smoke test · promote, each by its own native API");

// The smoke script Jenkins links: code on demand, dashed as the constraint is optional
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 5.95, y: 5.68, w: 1.32, h: 0.21, rectRadius: 0.05, fill: { color: "0F1512" }, line: { color: "6FA8DC", width: 1, dashType: "dash" } });
T("smoke script, linked", { x: 5.95, y: 5.68, w: 1.32, h: 0.21, fontSize: 7.5, italic: true, color: "9FC3E6", align: "center" });
// RISE: the watcher and Jenkins reply later, back up to ARTIE
const R = { w: 1.75, dash: "sysDot" };
line(6.61, 4.99, 0, 0.16, "6FA8DC", R); line(6.61, 4.99, 4.07, 0, "6FA8DC", R);
line(7.95, 4.04, 0, 0.16, "6FA8DC", R); line(7.95, 4.04, 2.73, 0, "6FA8DC", R);
line(10.68, 2.55, 0, 2.44, "6FA8DC", R); line(10.6, 2.55, 0.08, 0, "6FA8DC", { w: 1.75, arrow: "triangle", flipH: true });
// The gold path is HATEOAS; 7 is the repair
line(2.62, 6.07, 0.34, 0, GOLD, { w: 3 });
T([{ text: "The gold path is hypermedia as the engine of application state: ", options: { bold: true, color: GOLD } }, { text: "every step is a control the last answer offered. Nothing is built from a manual.", options: { color: PALE } }], { x: 3.05, y: 5.95, w: 9.9, h: 0.25, fontSize: 11 });
s.addShape(pres.shapes.OVAL, { x: 2.67, y: 6.25, w: 0.24, h: 0.24, fill: { color: RUST }, line: { color: "17201B", width: 1 } });
T("7", { x: 2.67, y: 6.25, w: 0.24, h: 0.24, fontSize: 9, bold: true, color: W, align: "center" });
T("A form changed? The replay stops, the model repairs at \u2461 (the capture), then it\u2019s zero tokens again.", { x: 3.05, y: 6.24, w: 9.9, h: 0.26, fontSize: 11, italic: true, color: RUST });

// Left rail: building
box(0.4, 1.25, 1.95, 4.68, "223028");
T("BUILDING", { x: 0.55, y: 1.35, w: 1.7, h: 0.3, fontSize: 11, bold: true, color: GOLD, charSpacing: 1 });
T("bdd-gates hooks", { x: 0.55, y: 1.62, w: 1.7, h: 0.25, fontSize: 9, italic: true, color: SAGE });
[["Gherkin", "the behaviour, first"], ["red run", "fails on behaviour"], ["code", "complexity lint, every edit"], ["green run", "+ architecture rules"], ["gated commit", "order checked: test, then code"]].forEach(([t, note], i) => {
  const yy = 1.95 + i * 0.66;
  box(0.6, yy, 1.55, 0.36, i === 1 ? "8A3C1E" : i === 3 ? "2F7D55" : "33443A");
  s.addImage({ data: I[["cucumber8FD694", "xFFFFFF", "codeFFFFFF", "checkFFFFFF", "commitFFFFFF"][i]], x: 0.7, y: yy + 0.08, w: 0.2, h: 0.2 });
  T(t, { x: 0.92, y: yy, w: 1.2, h: 0.36, fontSize: 11, bold: true, align: "center" });
  T(note, { x: 0.6, y: yy + 0.37, w: 1.55, h: 0.22, fontSize: 8, italic: true, color: i === 2 || i === 3 ? GOLD : SAGE, align: "center" });
});
T("Instructions are advice. Hooks aren't.", { x: 0.55, y: 5.32, w: 1.7, h: 0.6, fontSize: 9.5, italic: true, color: PALE, valign: "top" });

// Right rail: capture once, then replay
box(10.95, 1.25, 2.0, 4.68, "223028");
const head = (y, h, sub) => { T(h, { x: 11.08, y, w: 1.8, h: 0.24, fontSize: 11, bold: true, color: GOLD, charSpacing: 1 }); T(sub, { x: 11.08, y: y + 0.23, w: 1.8, h: 0.2, fontSize: 8, italic: true, color: SAGE }); };
const row = (y, big, label, color = GOLD, size = 17) => { T(big, { x: 11.08, y, w: 0.84, h: 0.42, fontSize: size, bold: true, fontFace: "Cambria", color }); T(label, { x: 11.93, y, w: 1.0, h: 0.42, fontSize: 8, color: PALE }); };
head(1.33, "CAPTURE", "once \u00B7 Claude \u00B7 real GitHub");
row(1.8, "20", "model calls"); row(2.25, "36k", "tokens: 33,140 in, 2,980 out");
line(11.08, 2.8, 1.75, 0, "3A4A40", { w: 1 });
head(2.9, "REPLAY", "each release after \u00B7 no model");
row(3.37, "0", "model calls, tokens");
T("per 100 releases, L2 cache", { x: 11.08, y: 3.8, w: 1.8, h: 0.18, fontSize: 7.5, italic: true, color: SAGE });
const two = (y, a, b, label) => { T(a, { x: 11.08, y, w: 0.9, h: 0.3, fontSize: 13, bold: true, fontFace: "Cambria", color: GOLD }); T(b, { x: 11.98, y, w: 0.9, h: 0.3, fontSize: 13, bold: true, fontFace: "Cambria", color: GOLD }); T(label, { x: 11.08, y: y + 0.27, w: 1.8, h: 0.16, fontSize: 7.5, color: PALE }); };
T([{ text: "GitHub", options: { bold: true } }], { x: 11.08, y: 3.98, w: 0.9, h: 0.16, fontSize: 7.5, color: SAGE }); T([{ text: "local GitLab", options: { bold: true } }], { x: 11.98, y: 3.98, w: 0.9, h: 0.16, fontSize: 7.5, color: SAGE });
two(4.13, "5,069", "3,985", "requests never sent");
two(4.5, "3.4 MB", "3.8 MB", "bytes never received");
line(11.08, 4.95, 1.75, 0, "3A4A40", { w: 1 });
head(5.0, "INSTEAD OF", "an agent with no hypermedia");
row(5.45, "13.5 MB", "GitHub\u2019s OpenAPI, read up front", RUST, 14);

// Foundation: REST's constraints, with the uniform interface holding its own four
box(0.4, 6.58, 12.55, 0.86, "0F1512", { line: "2D3A31" });
T([{ text: "REST", options: { bold: true, color: GOLD, fontSize: 13 } }, { text: "\nFielding\u2019s\ndissertation, 2000", options: { color: SAGE, fontSize: 8 } }], { x: 0.55, y: 6.6, w: 1.3, h: 0.82, fontSize: 11 });
const k = (x, w, name, where) => { box(x, 6.66, w, 0.7, "1D4F78"); T([{ text: name, options: { bold: true, color: "DCEBFA", fontSize: 9.5 } }, ...(where ? [{ text: "\n\u25B2 " + where, options: { color: GOLD, fontSize: 8 } }] : [])], { x, y: 6.66, w, h: 0.7, align: "center" }); };
k(1.85, 1.05, "client\u2013server", "ARTIE \u2194 gateway"); k(2.98, 0.95, "stateless", "If-Match, signed"); k(4.01, 0.9, "cache", "L2 form sets");
// the uniform interface: a container, named, holding its four constraints
box(5.0, 6.66, 5.3, 0.7, "163B5C", { line: "2E6A9E", lw: 1 });
T([{ text: "uniform interface", options: { bold: true, color: "DCEBFA" } }, { text: "  \u00B7 its four constraints", options: { color: "9FC3E6" } }], { x: 5.1, y: 6.68, w: 5.1, h: 0.24, fontSize: 9.5 });
const u = (x, w, t, fill, color) => { box(x, 6.95, w, 0.34, fill); T(t, { x, y: 6.95, w, h: 0.34, fontSize: 8, bold: true, color, align: "center" }); };
u(5.08, 1.15, "identification\nof resources", "1D4F78", "DCEBFA");
u(6.29, 1.38, "manipulation of resources\nthrough representations", "1D4F78", "DCEBFA");
u(7.73, 1.18, "self-descriptive\nmessages", "1D4F78", "DCEBFA");
u(8.97, 1.25, "HATEOAS\n\u25B2 the gold path", GOLD, "17201B");
k(10.4, 1.2, "layered system", "the gateway"); // code on demand: REST's one optional constraint, drawn dashed
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 11.68, y: 6.66, w: 1.2, h: 0.7, rectRadius: 0.08, fill: { color: "0F1512" }, line: { color: "6FA8DC", width: 1.25, dashType: "dash" } });
T([{ text: "code on demand", options: { bold: true, color: "DCEBFA", fontSize: 9.5 } }, { text: "\noptional", options: { italic: true, color: "9FC3E6", fontSize: 8 } }, { text: "  \u25B2 smoke link", options: { color: GOLD, fontSize: 8 } }], { x: 11.68, y: 6.66, w: 1.2, h: 0.7, align: "center" });
if (pathOnly) {
  let y;
  s.addShape(pres.shapes.RECTANGLE, { x: 0.3, y: 1.18, w: 12.75, h: 6.3, fill: { color: "17201B", transparency: 28 }, line: { color: "17201B", transparency: 100 } });
  box(7.12, 5.1, 3.56, 0.7, "26332B", { line: GOLD, lw: 1 });
  T([{ text: "scm:", options: { bold: true, color: GOLD, fontFace: "Courier New" } }, { text: "  one set of typed relations, two forges", options: { color: PALE, italic: true } }], { x: 7.22, y: 5.11, w: 3.4, h: 0.2, fontSize: 8.5 });
  line(4.3, 1.6, 5.3, 0, GOLD); line(9.6, 1.6, 0, 3.85, GOLD); line(5.7, 5.45, 3.9, 0, GOLD);
  y = 1.35; chip(4.25, y, 2.0, "release.goal.md\nthe goal, in words", { size: 10, icon: "mdEDF2EE" }); num(4.12, y - 0.12, 1);
  y = 1.35; chip(6.45, y, 2.0, "Claude captures\n20 calls, once", { size: 10, icon: "claudeD97757" }); num(6.32, y - 0.12, 2);
  y = 1.35; chip(8.65, y, 1.95, "kept YAML + pins", { size: 10, bold: true, icon: "yamlEDF2EE" }); num(8.52, y - 0.12, 3);
  y = 2.3; chip(8.65, y, 1.95, "ARTIE", { size: 14, bold: true, line: GOLD, icon: "compassE3B341", isz: 0.28 }); num(8.52, y - 0.12, 4);
  y = 4.2; chip(8.65, y, 1.95, "gateway\nYAML connectors", { size: 10, bold: true, line: GOLD, icon: "shieldE3B341", isz: 0.26 }); num(8.52, y - 0.12, 5);
  chip(8.65, 5.33, 1.95, "GitHub", { h: 0.4, size: 13, bold: true, icon: "githubFFFFFF", isz: 0.28 }); num(10.42, 5.22, 6);
  chip(7.2, 5.33, 1.12, "GitLab", { h: 0.4, size: 11, icon: "gitlabFC6D26", isz: 0.26 });
  chip(5.24, 5.15, 0.85, "Dev", { size: 11, icon: "serverEDF2EE" }); chip(6.17, 5.15, 0.88, "Jenkins", { icon: "jenkinsE8735F", isz: 0.22, size: 10 });
  line(2.62, 6.07, 0.34, 0, GOLD, { w: 3 });
T([{ text: "The gold path is hypermedia as the engine of application state: ", options: { bold: true, color: GOLD } }, { text: "every step is a control the last answer offered. Nothing is built from a manual.", options: { color: PALE } }], { x: 3.05, y: 5.95, w: 9.9, h: 0.25, fontSize: 11 });

}
};
