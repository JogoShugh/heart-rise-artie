// Kotlin syntax colouring for heart-scripts shown in reports: comments, strings (with their $templates),
// keywords, numbers, and calls, the DSL's verbs among them. Every <code class="kotlin"> on the page is
// coloured in place, from its text, so the page needs no library. The rules come with it, so no page can
// leave them out; their colours are the page's own tokens (--kt-kw, --kt-str, --kt-tpl, --kt-cm, --kt-fn,
// --kt-num, light and dark), with light values to fall back on when a page has none.
// window.KotlinView.classes(text) gives each character's class ("" for plain), for a page that colours
// text it also marks (the derivation report).
(() => {
  const KEYWORDS = new Set(["val", "var", "fun", "if", "else", "when", "return", "by", "to", "in", "is", "as",
    "null", "true", "false", "class", "object", "for", "while", "import", "package", "this", "it"]);
  const TOKEN = /(\/\/[^\n]*|\/\*[\s\S]*?\*\/)|("""[\s\S]*?"""(?!")|"(?:\\.|[^"\\])*")|\b(\d+(?:\.\d+)?L?)\b|\b([A-Za-z_]\w*)\b/g;
  // Each token of the text: its class ("" for plain) and its text, in order, covering every character.
  const tokens = (text) => {
    const out = [];
    let last = 0;
    for (const m of text.matchAll(TOKEN)) {
      out.push(["", text.slice(last, m.index)]);
      const [all, comment, str, num, word] = m;
      // A call: a name with ( or { after it. Tested on what follows, as a lookahead alone can't be captured.
      const call = word && /^\s*[({]/.test(text.slice(m.index + all.length));
      if (comment) out.push(["kt-cm", comment]);
      else if (str) {
        // A string, its ${…} and $name templates marked inside it.
        let at = 0;
        str.replace(/\$\{[^}]*\}|\$[A-Za-z_]\w*/g, (t, i) => {
          out.push(["kt-str", str.slice(at, i)], ["kt-str kt-tpl", t]);
          at = i + t.length;
          return t;
        });
        out.push(["kt-str", str.slice(at)]);
      } else if (num) out.push(["kt-num", num]);
      else if (KEYWORDS.has(word)) out.push(["kt-kw", word]);
      else if (call) out.push(["kt-fn", word]);
      else out.push(["", all]);
      last = m.index + all.length;
    }
    out.push(["", text.slice(last)]);
    return out.filter(([, t]) => t.length > 0);
  };
  const classes = (text) => tokens(text).flatMap(([cls, t]) => Array.from({ length: t.length }, () => cls));
  const colour = (code) => {
    const out = document.createDocumentFragment();
    for (const [cls, text] of tokens(code.textContent)) {
      if (!cls) { out.append(document.createTextNode(text)); continue; }
      const s = document.createElement("span");
      s.className = cls;
      s.textContent = text;
      out.append(s);
    }
    code.replaceChildren(out);
  };
  const rules = document.createElement("style");
  rules.textContent = [
    ".kt-kw { color: var(--kt-kw, #8a3f8f); font-weight: 500; }", ".kt-str { color: var(--kt-str, #2f6f3f); }",
    ".kt-str.kt-tpl, .kt-tpl { color: var(--kt-tpl, #b0641c); }",
    ".kt-cm { color: var(--kt-cm, #7b847e); font-style: italic; }",
    ".kt-fn { color: var(--kt-fn, #1f5a8a); }", ".kt-num { color: var(--kt-num, #a0522d); }",
  ].join("\n");
  document.head.append(rules);
  window.KotlinView = { classes };
  document.querySelectorAll("code.kotlin").forEach(colour);
})();
