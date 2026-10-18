// Kotlin syntax colouring for heart-scripts shown in reports: comments, strings (with their $templates),
// keywords, numbers, and calls, the DSL's verbs among them. Every <code class="kotlin"> on the page is
// coloured in place, from its text, so the page needs no library. The rules come with it, so no page can
// leave them out; their colours are the page's own tokens (--kt-kw, --kt-str, --kt-tpl, --kt-cm, --kt-fn,
// --kt-num, light and dark), with light values to fall back on when a page has none.
(() => {
  const KEYWORDS = new Set(["val", "var", "fun", "if", "else", "when", "return", "by", "to", "in", "is", "as",
    "null", "true", "false", "class", "object", "for", "while", "import", "package", "this", "it"]);
  const TOKEN = /(\/\/[^\n]*|\/\*[\s\S]*?\*\/)|("(?:\\.|[^"\\])*")|\b(\d+(?:\.\d+)?L?)\b|\b([A-Za-z_]\w*)\b/g;
  const span = (cls, text) => {
    const s = document.createElement("span");
    s.className = cls;
    s.textContent = text;
    return s;
  };
  // A string, its ${…} and $name templates marked inside it.
  const string = (text) => {
    const s = document.createElement("span");
    s.className = "kt-str";
    let last = 0;
    text.replace(/\$\{[^}]*\}|\$[A-Za-z_]\w*/g, (m, at) => {
      s.append(document.createTextNode(text.slice(last, at)), span("kt-tpl", m));
      last = at + m.length;
      return m;
    });
    s.append(document.createTextNode(text.slice(last)));
    return s;
  };
  const colour = (code) => {
    const text = code.textContent;
    const out = document.createDocumentFragment();
    let last = 0;
    for (const m of text.matchAll(TOKEN)) {
      out.append(document.createTextNode(text.slice(last, m.index)));
      const [all, comment, str, num, word] = m;
      // A call: a name with ( or { after it. Tested on what follows, as a lookahead alone can't be captured.
      const call = word && /^\s*[({]/.test(text.slice(m.index + all.length));
      if (comment) out.append(span("kt-cm", comment));
      else if (str) out.append(string(str));
      else if (num) out.append(span("kt-num", num));
      else if (KEYWORDS.has(word)) out.append(span("kt-kw", word));
      else if (call) out.append(span("kt-fn", word));
      else out.append(document.createTextNode(all));
      last = m.index + all.length;
    }
    out.append(document.createTextNode(text.slice(last)));
    code.replaceChildren(out);
  };
  const rules = document.createElement("style");
  rules.textContent = [
    ".kt-kw { color: var(--kt-kw, #8a3f8f); font-weight: 500; }", ".kt-str { color: var(--kt-str, #2f6f3f); }",
    ".kt-tpl { color: var(--kt-tpl, #b0641c); }", ".kt-cm { color: var(--kt-cm, #7b847e); font-style: italic; }",
    ".kt-fn { color: var(--kt-fn, #1f5a8a); }", ".kt-num { color: var(--kt-num, #a0522d); }",
  ].join("\n");
  document.head.append(rules);
  document.querySelectorAll("code.kotlin").forEach(colour);
})();
