// Kotlin syntax colouring for heart-scripts shown in reports: comments, strings (with their $templates),
// keywords, numbers, and calls, the DSL's verbs among them. Every <code class="kotlin"> on the page is
// coloured in place, from its text, so the page needs no library. Colours are the page's own tokens:
// .kt-cm, .kt-str, .kt-tpl, .kt-kw, .kt-num, .kt-fn.
(() => {
  const KEYWORDS = new Set(["val", "var", "fun", "if", "else", "when", "return", "by", "to", "in", "is", "as",
    "null", "true", "false", "class", "object", "for", "while", "import", "package", "this", "it"]);
  const TOKEN = /(\/\/[^\n]*|\/\*[\s\S]*?\*\/)|("(?:\\.|[^"\\])*")|\b(\d+(?:\.\d+)?L?)\b|\b([A-Za-z_]\w*)\b(\s*(?=[({]))?/g;
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
      const [all, comment, str, num, word, call] = m;
      if (comment) out.append(span("kt-cm", comment));
      else if (str) out.append(string(str));
      else if (num) out.append(span("kt-num", num));
      else if (KEYWORDS.has(word)) out.append(span("kt-kw", word), document.createTextNode(call || ""));
      else if (call !== undefined) out.append(span("kt-fn", word), document.createTextNode(call));
      else out.append(document.createTextNode(all));
      last = m.index + all.length;
    }
    out.append(document.createTextNode(text.slice(last)));
    code.replaceChildren(out);
  };
  document.querySelectorAll("code.kotlin").forEach(colour);
})();
