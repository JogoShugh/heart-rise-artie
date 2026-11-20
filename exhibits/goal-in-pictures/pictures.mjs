// Writes the deck's picture slides (slides/02, 03, 05, 10) from the layouts below: a C4 context of the release, the goal
// becoming a swimlane activity, the release's resources as a map walked then replayed, and a C4 dynamic view of the
// run. Each picture is plain SVG that reads whole with no script; code/pictures.js (deck:code) steps through it,
// showing each element from the step its data-at names.
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
const at = (a) => (a == null ? "" : ` data-at="${a}"`);
const yamlStr = (s) => JSON.stringify(s);
const indent = (s) => s.split("\n").map((l) => (l ? "  " + l : l)).join("\n");

// A box with a title and up to two lines under it. kind: person | system | ext | doc | res
function box({ x, y, w, h, title, lines = [], kind = "system", a, id }) {
  const cx = x + w / 2;
  const top = y + h / 2 - (lines.length * 13) / 2 + 3;
  const shape = kind === "doc"
    ? `<path class="shape" d="M${x} ${y}h${w - 14}l14 14v${h - 14}h${-w}z"/>`
    : `<rect class="shape" x="${x}" y="${y}" width="${w}" height="${h}" rx="${kind === "res" ? 18 : 8}"/>`;
  const head = kind === "person" ? `<circle class="shape" cx="${cx}" cy="${y - 12}" r="12"/>` : "";
  const text = [`<text class="t" x="${cx}" y="${top}" text-anchor="middle">${esc(title)}</text>`]
    .concat(lines.map((l, i) => `<text class="s" x="${cx}" y="${top + 14 + i * 13}" text-anchor="middle">${esc(l)}</text>`));
  return `<g class="node ${kind}"${id ? ` data-id="${id}"` : ""}${at(a)}>${head}${shape}${text.join("")}</g>`;
}

// An arrow along a path, with a label at (lx, ly). style: heart | rise | own | think | alt
function arrow({ d, label, lx, ly, style = "heart", a, n, anchor = "middle", dash }) {
  const bx = anchor === "start" ? lx - 12 : lx - 4 - String(label).length * 3.1 - 10;
  const num = n == null ? "" : `<g class="num"><circle cx="${bx}" cy="${ly - 4}" r="8"/><text x="${bx}" y="${ly - 0.5}" text-anchor="middle">${n}</text></g>`;
  return `<g class="edge ${style}${dash ? " dash" : ""}"${at(a)}><path d="${d}" marker-end="url(#pic-arrow)"/>` +
    (label ? `<text class="l" x="${lx}" y="${ly}" text-anchor="${anchor}">${esc(label)}</text>` : "") + num + `</g>`;
}

const defs = `<defs><marker id="pic-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="context-stroke"/></marker></defs>`;
const svg = (w, h, label, body) =>
  `<svg class="pic-svg" viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(label)}">${defs}${body}</svg>`;

function slide({ file, name, part, title, kicker, say, sources = [], code = true, html }) {
  const src = sources.length ? `sources:\n${sources.map(([a, b]) => `  - [${yamlStr(a)}, ${yamlStr(b)}]`).join("\n")}\n` : "";
  const c = code ? `code:\n  file: pictures.js\n  title: Steps through the picture, one part of the goal at a time\n` : "";
  writeFileSync(join(here, "slides", file),
    `# Written by pictures.mjs: edit there, not here.\nname: ${name}\npart: ${yamlStr(part)}\ntitle: ${yamlStr(title)}\nkicker: ${yamlStr(kicker)}\n${c}say: ${yamlStr(say)}\n${src}html: |\n${indent(html)}\n`);
}

// ---------------------------------------------------------------- C4 context: where the release runs
{
  const R = 680, RW = 260, RH = 58;
  const right = [
    ["forge", "Forge gateway", ["HEART, in front of", "GitHub and GitLab"]],
    ["watch", "Watcher", ["RISE: tells the desk", "when a resource offers an act"]],
    ["ci", "CI wrapper", ["HEART, in front of Jenkins,", "which builds and deploys"]],
    ["preview", "Preview deployment", ["the built library app,", "where the smoke test runs"]],
    ["dev", "Dev", ["HEART: environments,", "promote a commit"]],
  ];
  const ry = (i) => 40 + i * 78;
  const body = [
    `<rect class="bound" x="${R - 20}" y="14" width="${RW + 40}" height="${ry(4) + RH + 30 - 14}" rx="14"/>`,
    `<text class="bl" x="${R - 8}" y="32">Our own controls: HEART servers we run</text>`,
    box({ x: 40, y: 54, w: 200, h: 66, kind: "person", title: "Release author", lines: ["writes the goal", "in plain words"] }),
    box({ x: 330, y: 190, w: 220, h: 78, title: "ARTIE, at the release desk", lines: ["HEART client: walks the goal", "once, then replays its script"] }),
    box({ x: 40, y: 320, w: 200, h: 66, kind: "ext", title: "Claude", lines: ["reasons over the offers,", "during the walk only"] }),
    box({ x: 330, y: 360, w: 220, h: 60, kind: "doc", title: "Kept script", lines: ["YAML, with pinned offers"] }),
    ...right.map(([id, t, l], i) => box({ x: R, y: ry(i), w: RW, h: RH, title: t, lines: l, id })),
    arrow({ d: "M240 100 C300 110 330 160 380 188", label: "the goal, plain text", lx: 300, ly: 128, style: "own" }),
    arrow({ d: "M330 250 C290 280 270 320 242 340", label: "walk: which offer?", lx: 250, ly: 286, style: "think", dash: true }),
    arrow({ d: "M440 270 V356", label: "keeps, then replays", lx: 448, ly: 318, anchor: "start", style: "own" }),
    ...right.map((_, i) => arrow({ d: `M552 229 C600 229 620 ${ry(i) + 29} ${R - 2} ${ry(i) + 29}`, style: i === 1 ? "rise" : "heart" })),
    `<text class="l" x="610" y="452" text-anchor="middle">HTTP: HEART and RISE</text>`,
  ].join("");
  slide({
    file: "02-where.yaml", name: "where", part: "The goal", title: "Where the Release Runs",
    kicker: "C4, system context: who and what the goal touches", code: false,
    say: "Before the goal moves, here's where it runs. One person writes the goal in plain words. ARTIE, at the release desk, is the only client. Claude helps once, on the walk, and never again. Everything ARTIE talks to is a HEART server we run ourselves: the forge gateway in front of GitHub and GitLab, the watcher, the CI wrapper in front of Jenkins, the preview the build deploys, and Dev. Our own controls, in front of what we don't control.",
    sources: [["C4 model: system context", "https://c4model.com/diagrams/system-context"]],
    html: `<h2>Where the Release Runs</h2>\n<div class="pic pic-c4">\n  ${svg(980, 470, "C4 system context of the release", body)}\n</div>\n<p class="sq-key pic-key"><span><b class="k-heart">━</b> HEART</span><span><b class="k-rise">━</b> RISE</span><span><b class="k-think">┅</b> Claude, walk only</span><span><b class="k-own">━</b> the desk's own</span></p>`,
  });
}

// ---------------------------------------------------------------- Goal becomes activity
{
  const lanes = ["ARTIE", "Forge", "Watcher", "CI · Jenkins", "Preview", "Dev"];
  const LW = 112, L0 = 6, cx = (i) => L0 + LW * i + LW / 2, NW = 104, NH = 38;
  const node = (lane, y, title, sub, a, kind = "system") =>
    box({ x: cx(lane) - NW / 2, y, w: NW, h: NH, title, lines: sub ? [sub] : [], a, kind });
  const wait = (y, label, a) => `<g class="node wait"${at(a)}><rect class="shape" x="${cx(0) - 46}" y="${y}" width="92" height="30" rx="15"/><text class="t" x="${cx(0)}" y="${y + 19}" text-anchor="middle">⧗ ${esc(label)}</text></g>`;
  const link = (x1, y1, x2, y2, a, style = "heart", label) => {
    const mid = y1 + 10;
    const d = x1 === x2 ? `M${x1} ${y1} V${y2 - 2}` : `M${x1} ${y1} V${mid} H${x2} V${y2 - 2}`;
    return arrow({ d, a, style, label, lx: (x1 + x2) / 2, ly: mid - 4 });
  };
  const H = 600;
  const body = [
    ...lanes.map((l, i) => `<g class="lane"><rect x="${L0 + LW * i}" y="0" width="${LW}" height="${H}"/><text class="lh" x="${cx(i)}" y="20" text-anchor="middle">${esc(l)}</text></g>`),
    `<g class="node start" data-at="1"><circle cx="${cx(0)}" cy="46" r="8"/></g>`,
    link(cx(0), 54, cx(1), 70, 1), node(1, 70, "scm:pulls-get", "PR 501", 1, "res"),
    link(cx(1), 108, cx(2), 126, 2), node(2, 126, "watcher:watch", "when scm:merge", 2, "res"),
    link(cx(2), 164, cx(0), 180, 2, "rise", "answers"), wait(180, "once answered", 2),
    link(cx(0), 210, cx(1), 226, 3), node(1, 226, "scm:merge", "squash", 3, "res"),
    link(cx(1), 264, cx(3), 280, 4), node(3, 280, "ci:build", "merged commit", 4, "res"),
    link(cx(3), 318, cx(0), 334, 4, "rise", "answers"), wait(334, "once answered", 4),
    link(cx(0), 364, cx(3), 380, 5), node(3, 380, "hr:in-reply-to", "the build", 5, "res"),
    link(cx(3), 418, cx(4), 434, 6), node(4, 434, "ci:smoke-test", "on the preview", 6, "res"),
    link(cx(4), 472, cx(0), 488, 7),
    `<g class="node decide" data-at="7"><path class="shape" d="M${cx(0)} 488 l30 20 l-30 20 l-30 -20 z"/><text class="t" x="${cx(0)}" y="512" text-anchor="middle">passed?</text></g>`,
    arrow({ d: `M${cx(0) + 30} 508 H${cx(5) - NW / 2 - 2}`, a: 7, label: "yes", lx: cx(3), ly: 503 }),
    node(5, 489, "env:promote", "5 of 5 passed", 7, "res"),
    arrow({ d: `M${cx(0)} 528 V548 H${cx(1)} V${552}`, a: 8, label: "no", lx: cx(0) + 40, ly: 544, style: "alt" }),
    node(1, 554, "scm:comment", "not promoted", 8, "res alt"),
    `<g class="node end" data-at="7"><circle cx="${cx(5)}" cy="${548}" r="9"/><circle class="in" cx="${cx(5)}" cy="${548}" r="5"/></g>`,
    arrow({ d: `M${cx(5)} 527 V537`, a: 7 }),
  ].join("");
  const goal = [
    [1, "Open pull request {pull} of {owner}/{repo}"],
    [2, "At {watcher}, ask to be told when {pr} offers scm:merge"],
    [3, "Once {watch} is answered, at {pr}, merge it by squashing"],
    [4, "At {jenkins}, build commit {merged.merge_commit_sha}, asking to be told how it goes"],
    [5, "Once {build} is answered, follow its in-reply-to link to the build"],
    [6, "Run the smoke script it links as ci:smoke-test, on the deployment it links as ci:deployment"],
    [7, "If {smoke.passed}, at {dev}, open environment {environment} and promote commit…"],
    [8, "Otherwise, at {pr}, act scm:comment with body = \"Not promoted to Dev…\""],
  ];
  const caps = [
    "Open: ARTIE enters at the forge gateway and gets pull request 501 by scm:pulls-get.",
    "Ask: the watcher takes the request and makes a reply; ARTIE rests until it is answered.",
    "Merge: once answered, ARTIE acts scm:merge on 501, method squash.",
    "Build: the CI wrapper has Jenkins build the merged commit; ARTIE rests again.",
    "Follow: the answered reply links the build, hr:in-reply-to.",
    "Smoke: ARTIE runs the script the build links, on the preview it deployed.",
    "Decide: passed, so ARTIE promotes the commit at Dev, citing 5 of 5. Done.",
    "Otherwise: had the smoke failed (as 504's did), a comment on the pull request instead.",
  ];
  const list = goal.map(([n, t]) => `    <li class="keep" data-at="${n}">${esc(t)}</li>`).join("\n");
  slide({
    file: "03-activity.yaml", name: "activity", part: "The goal", title: "The Goal Becomes an Activity",
    kicker: "Each line of plain words, as the act it becomes at run time",
    say: "Here's the jump from words to motion. On the left, the goal's lines, as I wrote them. On the right, the same goal as an activity, one lane per party. Press play: each line lights up as the act it becomes. Open is scm:pulls-get at the forge. Ask is a RISE request to the watcher, and ARTIE rests until it's answered. Merge, build, rest again, follow the reply to the build, run the smoke script on the preview. Then the one decision in the goal: passed, promote at Dev; otherwise, a comment on the pull request. That's 504's path.",
    sources: [["release.goal.md", "https://github.com/JogoShugh/heart-rise-artie/blob/main/exhibits/merge-watch-release/release.goal.md"]],
    html: `<h2>The Goal Becomes an Activity</h2>\n<div class="pic pic-activity" data-steps="8" data-intro="Press play: each line of the goal becomes an act." data-captions='${JSON.stringify(caps).replace(/'/g, "&#39;")}'>\n  <div class="pic-two">\n  <ol class="pic-goal">\n${list}\n  </ol>\n  ${svg(682, H, "The goal as a swimlane activity", body)}\n  </div>\n</div>`,
  });
}

// ---------------------------------------------------------------- Resource map: walked once, replayed
{
  const N = (x, y, title, sub, extra = {}) => box({ x, y, w: 132, h: 42, kind: "res", title, lines: sub ? [sub] : [], ...extra });
  const cluster = (x, y, w, h, t) => `<g class="cluster"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14"/><text class="bl" x="${x + 12}" y="${y + 20}">${esc(t)}</text></g>`;
  const mark = (x, y, t) => `<text class="mark" x="${x}" y="${y}">${esc(t)}</text>`;
  const walk = (n, a, d, label, lx, ly, extra = {}) =>
    arrow({ d, label, lx, ly, n, a, style: "heart path", ...extra });
  const body = [
    cluster(10, 10, 316, 230, "Forge gateway"), cluster(10, 256, 316, 160, "Watcher"),
    cluster(340, 10, 330, 406, "CI wrapper, with Jenkins and the preview"), cluster(684, 10, 286, 406, "Dev"),
    N(26, 64, "forge index", "/"), mark(26, 58, "⚑ start: a bookmark"),
    N(178, 64, "pull request 501", "/pulls/501"),
    N(26, 176, "a comment", "on 501", { kind: "res alt" }),
    N(26, 314, "watcher index", "/"), mark(26, 308, "⚑ entered by bookmark"),
    N(178, 314, "watch reply", "a RISE reply"),
    N(356, 64, "jenkins index", "/"), mark(356, 58, "⚑ entered by bookmark"),
    N(522, 64, "build reply", "a RISE reply"),
    N(522, 176, "build 1", "/builds/1"),
    N(356, 300, "smoke script", "smoke.heart.yaml"),
    N(522, 300, "preview", "the built library"),
    N(700, 64, "dev index", "/"), mark(700, 58, "⚑ entered by bookmark"),
    N(700, 176, "environment 1", "/environments/1"),
    N(830, 300, "promotion", "the merged commit"),
    walk(1, "1 9", "M158 85 H176", "scm:pulls-get", 167, 128),
    walk(2, "2 9", "M158 335 H176", "watcher:watch", 167, 380),
    arrow({ d: "M260 312 V108", label: "answers when 501", lx: 266, ly: 260, anchor: "start", style: "rise", a: "2 9", dash: true }),
    `<text class="l" data-at="2 9" x="266" y="274">offers scm:merge</text>`,
    walk(3, "3 9", "M212 64 C212 34 278 34 278 62", "scm:merge", 245, 30),
    walk(4, "4 9", "M488 85 H520", "ci:build", 504, 128),
    walk(5, "5 9", "M588 106 V174", "hr:in-reply-to", 596, 146, { anchor: "start" }),
    walk(6, "6 9", "M560 218 C520 260 450 260 430 298", "ci:smoke-test", 468, 248),
    arrow({ d: "M600 218 V298", label: "ci:deployment", lx: 606, ly: 268, anchor: "start", style: "heart path", a: "6 9" }),
    arrow({ d: "M488 321 H520", label: "runs on", lx: 504, ly: 296, style: "own", a: "6 9" }),
    walk(7, "7 9", "M766 106 V174", "env:get-environment", 774, 146, { anchor: "start" }),
    walk(8, "8 9", "M800 218 C820 250 860 260 880 298", "env:promote", 860, 248, { anchor: "start" }),
    arrow({ d: "M220 106 C180 140 140 150 120 174", label: "scm:comment, otherwise", lx: 150, ly: 150, anchor: "start", style: "alt", dash: true }),
    `<g class="replay-tag" data-at="9"><rect x="356" y="364" width="300" height="40" rx="10"/><text class="t" x="506" y="382" text-anchor="middle">Replay: the same path, by relation</text><text class="s" x="506" y="396" text-anchor="middle">offers held to their pins · 0 model calls</text></g>`,
  ].join("");
  const caps = [
    "1 · Claude chose scm:pulls-get among the forge's offers.",
    "2 · Entered the watcher by bookmark; chose watcher:watch. The reply rests until 501 offers scm:merge.",
    "3 · On 501, chose scm:merge, method squash.",
    "4 · Entered Jenkins' wrapper by bookmark; chose ci:build.",
    "5 · Followed the answered reply to the build: hr:in-reply-to.",
    "6 · Ran the script the build links as ci:smoke-test, on the preview it links as ci:deployment.",
    "7 · Entered Dev by bookmark; chose env:get-environment.",
    "8 · Chose env:promote. The walk: 28 model calls in HAL-FORMS.",
    "Replay: the kept script follows the same relations, holds each offer to its pin, and asks no model.",
  ];
  slide({
    file: "05-map.yaml", name: "map", part: "The goal", title: "The Release as a Map of Resources",
    kicker: "Walked once with Claude, then replayed by relation",
    say: "Same release, drawn as the web it is: resources, and the relations between them. Nobody hands ARTIE this map. It finds it, one representation at a time. On the walk, Claude picks each relation among the offers. Three hops are bookmarks, the inputs at the top of the goal; the rest are links. When we replay, the script follows the same relations, checks every offer against its pin, and asks no model at all. The path is the script.",
    sources: [["release.heart.yaml, the kept script", "https://github.com/JogoShugh/heart-rise-artie/blob/main/exhibits/merge-watch-release/release.heart.yaml"]],
    html: `<h2>The Release as a Map of Resources</h2>\n<div class="pic pic-map" data-steps="9" data-intro="Press play: the walk, then the replay." data-captions='${JSON.stringify(caps).replace(/'/g, "&#39;")}'>\n  ${svg(980, 426, "The release's resources, and the relations walked between them", body)}\n</div>`,
  });
}

// ---------------------------------------------------------------- C4 dynamic: the run, numbered
{
  const B = (x, y, w, h, t, l, id) => box({ x, y, w, h, title: t, lines: l, id });
  const calls = [
    ["ARTIE → forge", "GET 501", "scm:pulls-get", "heart"],
    ["ARTIE → watcher", "POST watch", "watcher:watch", "heart"],
    ["watcher → forge", "looks at 501 until it offers scm:merge", "own", "own"],
    ["watcher → desk reply", "answers", "RISE", "rise"],
    ["ARTIE → forge", "PUT merge, squash", "scm:merge", "heart"],
    ["ARTIE → CI wrapper", "POST build", "ci:build", "heart"],
    ["CI wrapper → Jenkins → preview", "builds, deploys", "own", "own"],
    ["CI wrapper → desk reply", "answers", "RISE", "rise"],
    ["ARTIE → CI wrapper", "GET the build", "hr:in-reply-to", "heart"],
    ["ARTIE → preview", "the smoke script's 30 calls", "ci:smoke-test", "heart"],
    ["ARTIE → Dev", "GET environment 1, POST promote", "env:promote", "heart"],
  ];
  // A numbered call along a cubic curve, its number at the curve's midpoint.
  const call = (n, [x0, y0], [x1, y1], [x2, y2], [x3, y3], style = "heart") => {
    const mx = 0.125 * x0 + 0.375 * x1 + 0.375 * x2 + 0.125 * x3, my = 0.125 * y0 + 0.375 * y1 + 0.375 * y2 + 0.125 * y3;
    return `<g class="edge ${style}" data-at="${n}"><path d="M${x0} ${y0} C${x1} ${y1} ${x2} ${y2} ${x3} ${y3}" marker-end="url(#pic-arrow)"/>` +
      `<g class="num"><circle cx="${mx}" cy="${my}" r="9"/><text x="${mx}" y="${my + 3.5}" text-anchor="middle">${n}</text></g></g>`;
  };
  const body = [
    B(20, 170, 170, 80, "ARTIE", ["the release desk"], "artie"),
    B(20, 360, 170, 60, "Desk replies", ["RISE replies, resting"], "replies"),
    B(330, 20, 190, 60, "Forge gateway", ["GitHub · GitLab"]),
    B(330, 140, 190, 60, "Watcher", ["RISE"]),
    B(330, 260, 190, 60, "CI wrapper", ["in front of Jenkins"]),
    B(330, 380, 190, 60, "Preview", ["the built library"]),
    B(640, 200, 150, 60, "Dev", ["environments"]),
    call(1, [190, 178], [260, 178], [280, 36], [328, 36]),
    call(2, [190, 205], [260, 205], [290, 166], [328, 166]),
    call(3, [425, 138], [425, 118], [425, 100], [425, 82], "own"),
    call(4, [330, 192], [260, 250], [240, 330], [192, 374], "rise"),
    call(5, [190, 190], [270, 190], [290, 64], [328, 64]),
    call(6, [190, 222], [260, 222], [290, 280], [328, 280]),
    call(7, [425, 322], [425, 340], [425, 360], [425, 378], "own"),
    call(8, [330, 310], [270, 340], [240, 380], [192, 396], "rise"),
    call(9, [190, 238], [260, 238], [290, 302], [328, 302]),
    call(10, [150, 252], [150, 380], [260, 412], [328, 412]),
    call(11, [190, 214], [320, 230], [500, 232], [638, 230]),
  ].join("");
  const rows = calls.map(([who, what, rel, s], i) =>
    `    <li class="keep ${s}" data-at="${i + 1}"><b>${i + 1}</b><span><strong>${esc(who)}</strong> ${esc(what)} <code>${esc(rel)}</code></span></li>`).join("\n");
  slide({
    file: "10-dynamic.yaml", name: "dynamic", part: "The release", title: "The Release, Numbered",
    kicker: "C4, dynamic: the run's calls, in order, on the same boxes",
    say: "Before every message, the same run in eleven numbered calls on the context boxes. C4 calls this a dynamic diagram; UML calls it a collaboration diagram. Red is HEART, green is RISE, blue is a party's own work. Notice who talks to whom: only ARTIE starts anything, and the two answers come back to its own replies, not to ARTIE itself. The next slide plays every message behind these eleven.",
    sources: [["C4 model: dynamic diagram", "https://c4model.com/diagrams/dynamic"]],
    html: `<h2>The Release, Numbered</h2>\n<div class="pic pic-dynamic" data-steps="11" data-intro="Press play: the run's calls in order." data-captions='[]'>\n  <div class="pic-two">\n  ${svg(800, 450, "C4 dynamic view of the release", body)}\n  <ol class="pic-calls">\n${rows}\n  </ol>\n  </div>\n</div>\n<p class="sq-key pic-key"><span><b class="k-heart">━</b> HEART</span><span><b class="k-rise">━</b> RISE</span><span><b class="k-own">━</b> a party's own work</span></p>`,
  });
}
