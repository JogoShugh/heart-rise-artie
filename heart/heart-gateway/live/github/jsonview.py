"""
The reports' one way to show JSON: each document goes on the page once, as
data, and json-view.js renders it only when the reader opens its box, as a
diff of two documents or a short preview of one (json-view.js says how).

    view = JsonView()
    view.pair(github_body, heart_body, "GitHub", "HEART", "summary html")
    view.one(problem, "the answer", "summary html")
    view.assets()   # the style and script, once, in the page's head
    view.data()     # every document, once each, before </main>
"""
import hashlib
import html
import json
import os

# The viewer's one copy, beside the reports it serves.
ASSETS = os.path.join(os.path.dirname(__file__), "../../../../docs/reports/assets")


class JsonView:
    def __init__(self):
        self.documents = {}

    def _id(self, document):
        text = json.dumps(document, ensure_ascii=False, separators=(",", ":"))
        key = "j" + hashlib.sha256(text.encode()).hexdigest()[:12]
        self.documents[key] = text
        return key

    def pair(self, a, b, label_a, label_b, summary, lead="", tail=""):
        """A box that opens as what [b] changes of [a], between the html [lead] and [tail]."""
        return (f'<details class="jv" data-a="{self._id(a)}" data-b="{self._id(b)}" '
                f'data-label-a="{html.escape(label_a)}" data-label-b="{html.escape(label_b)}">'
                f'<summary>{summary}</summary>{lead}<div class="jv-tail">{tail}</div></details>')

    def one(self, document, label, summary, lead=""):
        """A box that opens as one document's first lines, after the html [lead]."""
        return (f'<details class="jv" data-a="{self._id(document)}" data-label-a="{html.escape(label)}">'
                f"<summary>{summary}</summary>{lead}</details>")

    @staticmethod
    def request_line(who, method, url, body=None, headers=None):
        """One request, as a line: who sends it, the method, the target, the headers that matter, the body."""
        parts = [f"{method} {url}"] + [f"{k}: {v}" for k, v in (headers or {}).items()]
        if body is not None:
            parts.append(json.dumps(body, ensure_ascii=False))
        return f'<p class="jv-req"><span class="who">{html.escape(who)}</span> <code>{html.escape("  ".join(parts))}</code></p>'

    @staticmethod
    def answer_line(response, kept=("Location", "Content-Location", "ETag")):
        """An answer, as a line: its status and the headers that matter."""
        headers = "  ".join(f"{k}: {v}" for k, v in response["headers"].items() if k in kept)
        return (f'<p class="jv-req"><span class="who">answer</span> '
                f'<code>{html.escape(f"HTTP {response['status']}  {headers}".strip())}</code></p>')

    def read(self, capture, summary):
        """One read made two ways: GitHub's answer, and what HEART's answer changes of it; why the rest are absent."""
        github, heart = capture["github"], capture["heart"]
        lead = (self.request_line("GitHub", "GET", github["request"]["url"])
                + self.request_line("HEART", "GET", heart["request"]["url"], headers={"Accept": "application/hal+json"}))
        explanation = capture.get("explanation")
        tail = self.one(explanation["response"]["body"], "hr:unavailable", "Why the rest are absent") if explanation else ""
        return self.pair(github["response"]["body"], heart["response"]["body"], "GitHub's answer", "HEART's answer",
                         summary, lead, tail)

    def act(self, step, summary):
        """One act: the request GitHub's API needs, the form HEART offered, and what the act changed."""
        plain, sent, answer = step.get("plain"), step["act"]["request"], step["act"]["response"]
        lead = ""
        if plain:
            lead += self.request_line("GitHub's API", plain["method"], "https://api.github.com" + plain["path"],
                                      plain.get("body"), {"Authorization": "Bearer <token>"})
        headers = {k: v for k, v in sent["headers"].items() if k == "If-Match"}
        lead += self.request_line("HEART", sent["method"], sent["url"], sent.get("body"), headers)
        lead += self.answer_line(answer)
        before = (step.get("read") or {}).get("response", {}).get("body")
        after = answer["body"]
        if answer["status"] < 300 and isinstance(after, dict) and isinstance(before, dict) and "_links" in after:
            return self.pair(before, after, "the state read before", "the state after", summary, lead)
        return self.one(after, "HEART's answer", summary, lead)

    @staticmethod
    def assets():
        with open(os.path.join(ASSETS, "json-view.css")) as css, open(os.path.join(ASSETS, "json-view.js")) as js:
            return f"<style>\n{css.read()}</style>\n<script>\n{js.read()}</script>"

    def data(self):
        # "</" inside a script element would end it; JSON allows the escaped slash.
        slash = "<\\/"
        return "".join(f'<script type="application/json" id="{key}">{text.replace("</", slash)}</script>'
                       for key, text in self.documents.items())
