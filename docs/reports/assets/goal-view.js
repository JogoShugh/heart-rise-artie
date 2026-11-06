// A goal file in a report (spec/PLAN-GOAL-MARKDOWN.md), made from the file itself: its body rendered as Markdown
// renders it (paragraphs, then one nested list; backticks as chips, names in braces marked, Wait: and Verify:
// as markers), and, under Full, its source with the front matter highlighted as YAML. Reads the text the page
// embeds in <pre id="goal-text">, into <section id="goal">.
(() => {
  const holder = document.getElementById("goal-text");
  const section = document.getElementById("goal");
  if (!holder || !section) return;
  const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const lines = holder.textContent.replace(/\n$/, "").split("\n");
  const end = lines[0].trim() === "---" ? lines.indexOf("---", 1) : -1;
  const front = end > 0 ? lines.slice(1, end) : [];
  const body = end > 0 ? lines.slice(end + 1) : lines;

  // Words in a step or a paragraph: braces marked, backticks as chips, a leading Wait: or Verify: as a marker.
  const words = (text) => {
    const parts = text.split(/(`[^`]*`)/);
    return parts.map((part, i) => {
      if (i % 2) return `<code class="chip">${esc(part.slice(1, -1))}</code>`;
      let out = esc(part).replace(/\{[A-Za-z_][\w.]*\}/g, (m) => `<span class="y-v">${m}</span>`);
      if (i === 0) out = out.replace(/^(wait|verify):/i, (m) => `<b class="marker">${m}</b>`);
      return out;
    }).join("");
  };

  // The body: paragraphs, then one list, nested 2 spaces a level.
  const rendered = document.createElement("div");
  rendered.className = "goal-rendered";
  let html = "";
  let depth = -1;
  let paragraph = [];
  const flush = () => {
    if (paragraph.length) html += `<p>${words(paragraph.join(" "))}</p>`;
    paragraph = [];
  };
  body.forEach((line) => {
    const item = line.match(/^( *)[-*+] (.*)$/);
    if (!item) {
      if (line.trim() === "") flush(); else if (depth < 0) paragraph.push(line.trim());
      return;
    }
    flush();
    const level = item[1].length / 2;
    if (level > depth) html += "<ul>".repeat(level - depth);
    else html += "</li>" + "</ul></li>".repeat(depth - level);
    depth = level;
    html += `<li>${words(item[2])}`;
  });
  flush();
  if (depth >= 0) html += "</li>" + "</ul></li>".repeat(depth) + "</ul>";
  rendered.innerHTML = html;

  // The source, its front matter highlighted as YAML: keys, values, a URL, the fresh-each-run { now: … }.
  const yamlLine = (line) => {
    const m = line.match(/^(\s*)([^:\s][^:]*):(.*)$/);
    if (!m) return esc(line);
    const value = m[3].replace(/^(\s*)(.*)$/, (_, sp, v) => {
      if (!v) return sp;
      if (/^https?:\/\//.test(v)) return `${sp}<span class="y-u">${esc(v)}</span>`;
      if (/^\{.*\}$/.test(v)) {
        return sp + esc(v).replace(/(\w+):/g, '<span class="y-k">$1</span>:')
          .replace(/: ([^,}]+)/g, ': <span class="y-s">$1</span>');
      }
      return `${sp}<span class="y-s">${esc(v)}</span>`;
    });
    return `${m[1]}<span class="y-k">${esc(m[2])}</span>:${value}`;
  };
  const source = document.createElement("pre");
  source.className = "yaml full";
  source.innerHTML = (end > 0 ? ["---", ...front.map(yamlLine), "---"] : []).concat(
    body.map((line) => {
      const item = line.match(/^( *)([-*+] )(.*)$/);
      return item ? `${item[1]}<span class="y-p">${item[2]}</span>${words(item[3])}` : words(line);
    }),
  ).join("\n");

  section.append(rendered, source);
})();
