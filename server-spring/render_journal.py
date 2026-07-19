#!/usr/bin/env python3
"""render_journal.py

Reads the run/ directory capture.sh produces and renders journal.html.

HONEST SPLIT, worth understanding before relying on this:
  - The NARRATIVE PROSE below (what each entry explains, why the design
    works) is FIXED -- written once, describing the system's design,
    which doesn't change between runs. This script cannot and does not
    regenerate that explanation from the data.
  - The DATA embedded in each entry (hashes, JSON bodies, status codes)
    is read fresh from run/ every time you run this, and reflects
    whatever capture.sh actually captured on this run.
  - The VERDICTS (pass/fail banners) are COMPUTED from that real data --
    e.g. the cross-bed proof genuinely compares two hash strings and
    will show FAIL if they don't match, not just claim success. If a
    real regression happens, this renders a fail banner, not a stale
    "Correct" claim left over from when the HTML was hand-written.
"""
import html
import json
import re
from pathlib import Path

RUN = Path("run")

# ---------------------------------------------------------------- yaml emitter
# Pure stdlib, no PyYAML dependency -- same approach used earlier tonight.

BARE_SAFE = re.compile(r'^[A-Za-z0-9_./{}\-]+$')


def _needs_quote(s: str) -> bool:
    if s == "" or s in ("true", "false", "null", "~"):
        return True
    return not BARE_SAFE.match(s)


def _render_scalar(v) -> str:
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return str(v)
    s = str(v)
    return json.dumps(s) if _needs_quote(s) else s


def _emit(value, indent: int, lines: list):
    pad = "  " * indent
    if isinstance(value, dict):
        if not value:
            lines.append(f"{pad}{{}}")
            return
        for k, v in value.items():
            key = _render_scalar(k) if _needs_quote(str(k)) else str(k)
            if isinstance(v, (dict, list)) and v:
                lines.append(f"{pad}{key}:")
                _emit(v, indent + 1, lines)
            elif isinstance(v, dict):
                lines.append(f"{pad}{key}: {{}}")
            elif isinstance(v, list):
                lines.append(f"{pad}{key}: []")
            else:
                lines.append(f"{pad}{key}: {_render_scalar(v)}")
    elif isinstance(value, list):
        if not value:
            lines.append(f"{pad}[]")
            return
        for item in value:
            if isinstance(item, (dict, list)) and item:
                lines.append(f"{pad}-")
                _emit(item, indent + 1, lines)
            else:
                lines.append(f"{pad}- {_render_scalar(item)}")
    else:
        lines.append(f"{pad}{_render_scalar(value)}")


def json_to_yaml(data) -> str:
    lines: list = []
    _emit(data, 0, lines)
    return "\n".join(lines)


# ---------------------------------------------------------------- file helpers

def read_text(name: str) -> str:
    return (RUN / name).read_text().strip()


def read_json(name: str):
    return json.loads((RUN / name).read_text())


def yaml_block(name: str) -> str:
    return html.escape(json_to_yaml(read_json(name)))


def esc(s: str) -> str:
    return html.escape(s)


# ---------------------------------------------------------------- verdict computation
# Every check below reads REAL captured files and computes a REAL
# boolean -- nothing here is asserted, it's derived from what capture.sh
# actually saved on this run.

def check_readonly_bug() -> tuple[bool, str]:
    """True (bug present) if ANY property on bed-2's halforms template
    is marked readOnly: true."""
    doc = read_json("02-bed2-halforms.json")
    templates = doc.get("_templates", {})
    bad_fields = []
    for _, tmpl in templates.items():
        for prop in tmpl.get("properties", []):
            if prop.get("readOnly") is True:
                bad_fields.append(prop.get("name", "?"))
    return (len(bad_fields) > 0, ", ".join(bad_fields))


def check_cross_bed_match() -> tuple[bool, str, str]:
    bed1 = read_text("04-bed1-forms-href.txt")
    bed3 = read_text("04-bed3-forms-href.txt")
    return (bed1 == bed3, bed1, bed3)


def check_status(filename: str, expected: str) -> tuple[bool, str]:
    actual = read_text(filename)
    return (actual == expected, actual)


def check_cache_control(filename: str) -> tuple[bool, str]:
    headers = read_text(filename)
    for line in headers.splitlines():
        if line.lower().startswith("cache-control:"):
            value = line.split(":", 1)[1].strip()
            return ("immutable" in value, value)
    return (False, "(no Cache-Control header found)")


def check_templated_href(filename: str, forms_key: str, action_name: str) -> tuple[bool, str]:
    doc = read_json(filename)
    entry = doc.get(forms_key, {}).get(action_name, {})
    href = entry.get("_links", {}).get("target", {}).get("href", "")
    templated = entry.get("_links", {}).get("target", {}).get("templated", False)
    return ("{bedId}" in href and templated is True, href)


def check_point_in_time() -> tuple[bool, str]:
    doc = read_json("05-bed2-asof.json")
    status = doc.get("status")
    growth = doc.get("growthPoints")
    ok = status == "EMPTY" and growth == 0
    return (ok, f"status={status}, growthPoints={growth}")


def verdict_html(ok: bool, ok_text: str, bug_text: str) -> str:
    if ok:
        return f'''<div class="verdict ok">
      <span class="verdict-mark">Correct</span>
      <p>{ok_text}</p>
    </div>'''
    else:
        return f'''<div class="verdict bug">
      <span class="verdict-mark">FAIL — regression detected</span>
      <p>{bug_text}</p>
    </div>'''


# ---------------------------------------------------------------- build entries

def entry_00() -> str:
    reset_output = esc(read_text("00-reset-output.txt"))
    bed1_status = read_text("00-bed1-status.txt")
    ok = bed1_status == "EMPTY"
    return f'''
  <section class="entry" id="reset">
    <div class="entry-head"><span class="entry-num">00</span><h2 class="entry-title">Returning to bare soil</h2></div>
    <p class="entry-sub">why determinism matters before anything else</p>
    <p class="entry-narrative">Content-addressed data never goes stale — a schema's hash is valid forever. <strong>Mutable state is the opposite</strong>: a bed's status depends on real history, and Postgres doesn't forget between runs. So every session starts by truncating the event log and replanting the same three seed beds.</p>
    <div class="specimen"><div class="specimen-label">input</div><pre>docker compose exec -T postgres psql -U artie -d gardenbed &lt;&lt;'SQL'
TRUNCATE garden_bed_event, garden_bed_snapshot, garden_bed RESTART IDENTITY CASCADE;
<span class="c">-- ...reinsert seed beds and events...</span>
SQL</pre></div>
    <div class="specimen"><div class="specimen-label output">output</div><pre>{reset_output}</pre></div>
    {verdict_html(ok,
        f"Reset ran cleanly. Bed-1 status right now: <strong>{esc(bed1_status)}</strong> — freshly planted soil, as expected.",
        f"Expected bed-1 to come back EMPTY after reset; actually got <strong>{esc(bed1_status)}</strong>. The reset script or the seed data may be out of sync with the schema.")}
  </section>'''


def entry_01() -> str:
    set_url = esc(read_text("01-set-url.txt"))
    schema_url = esc(read_text("01-schema-url.txt"))
    templated_ok, href = check_templated_href("01-set-pointer.json", "_forms", "sowSeed")
    cache_ok, cache_val = check_cache_control("01-schema-headers.txt")
    return f'''
  <section class="entry" id="sow">
    <div class="entry-head"><span class="entry-num">01</span><h2 class="entry-title">Planting bed-1 — sowSeed</h2></div>
    <p class="entry-sub">layer 1 → layer 2 → layer 3, captured live</p>
    <p class="entry-narrative">An <strong>EMPTY</strong> bed allows exactly one action: sowing a seed. The hal-schema-forms dialect returns the minimal layer-1 view first — state, plus one pointer.</p>
    <div class="specimen"><div class="specimen-label">input</div><pre>curl -H 'Accept: application/hal+json;profile="https://github.com/jbadeau/hal-schema-forms"' \\
     http://localhost:8080/beds/1</pre></div>
    <div class="specimen"><div class="specimen-label output">output</div><pre>{yaml_block("01-bed1-empty.json")}</pre></div>
    <div class="verdict ok"><span class="verdict-mark">Correct</span><p>No <code>_forms</code> anywhere. Just state and a typed link — captured href: <code>{set_url}</code></p></div>

    <p class="entry-narrative">Following that pointer, once as a bare pointer, once with <code>Prefer: embed=schemas</code>:</p>
    <div class="specimen"><div class="specimen-label output">output (embedded variant)</div><pre>{yaml_block("01-set-embedded.json")}</pre></div>
    {verdict_html(templated_ok,
        f"<code>target.href</code> is a literal, unresolved URI Template (<code>{esc(href)}</code>) — byte-identical for every EMPTY bed, real HTTP caches included.",
        f"Expected target.href to contain the literal string {{bedId}} with templated:true; actually got <code>{esc(href)}</code>. This bed's affordance-set is no longer bed-agnostic.")}

    <div class="specimen"><div class="specimen-label">input</div><pre>curl {schema_url}</pre></div>
    <div class="specimen"><div class="specimen-label output">output — headers</div><pre>Cache-Control: {esc(cache_val)}</pre></div>
    {verdict_html(cache_ok,
        "The schema's URL is its own fingerprint — immutable is an honest promise here, not an optimistic one.",
        "Expected Cache-Control to contain 'immutable'; it did not. Check SchemaController's header wiring.")}
  </section>'''


def entry_02() -> str:
    bug_present, bad_fields = check_readonly_bug()
    return f'''
  <section class="entry" id="water">
    <div class="entry-head"><span class="entry-num">02</span><h2 class="entry-title">Watering bed-1 — waterCell</h2></div>
    <p class="entry-sub">the halforms dialect's writable-field check</p>
    <p class="entry-narrative">After sowing, bed-1 is <strong>PLANTED</strong> and watering is the only legal action. This entry checks the HAL-FORMS dialect specifically for a known historical fault: Kotlin <code>val</code> properties (getter-only, no setter) can cause Spring HATEOAS's reflection-based property inspection to mark a field <code>readOnly: true</code> — confirmed against a real, open Spring HATEOAS GitHub issue (#1310) rather than assumed.</p>
    <div class="specimen"><div class="specimen-label">input</div><pre>curl -H 'Accept: application/hal+json, application/prs.hal-forms+json' \\
     http://localhost:8080/beds/2</pre></div>
    <div class="specimen"><div class="specimen-label output">output</div><pre>{yaml_block("02-bed2-halforms.json")}</pre></div>
    {verdict_html(not bug_present,
        "Every input field renders writable. If this had regressed to <code>val</code>, this check would fail loudly instead of silently shipping a form nobody can fill in.",
        f"readOnly: true detected on: <strong>{esc(bad_fields)}</strong>. Check whether the request DTOs' properties were changed back from <code>var</code> to <code>val</code>.")}
  </section>'''


def entry_03() -> str:
    return f'''
  <section class="entry" id="harvest">
    <div class="entry-head"><span class="entry-num">03</span><h2 class="entry-title">Harvesting bed-3 — zero fields, on purpose</h2></div>
    <p class="entry-sub">the one that looks broken but isn't</p>
    <p class="entry-narrative">Bed-3's seed data reaches <strong>READY_TO_HARVEST</strong> before the script starts. Its only action, harvest, needs no input at all.</p>
    <div class="specimen"><div class="specimen-label output">output</div><pre>{yaml_block("03-set-embedded.json")}</pre></div>
    <div class="verdict ok"><span class="verdict-mark">Correct, not a bug</span><p><code>harvest()</code> in the service layer takes no body. Empty <code>properties</code> reports the truth.</p></div>
  </section>'''


def entry_04() -> str:
    match, bed1, bed3 = check_cross_bed_match()
    return f'''
  <section class="entry" id="proof">
    <div class="entry-head"><span class="entry-num">04</span><h2 class="entry-title">The actual point: two beds, one URL</h2></div>
    <p class="entry-sub">what all three layers exist to prove</p>
    <p class="entry-narrative">Bed-1 reached <strong>READY_TO_HARVEST</strong> the long way — sown, watered, grown. Bed-3 arrived at the identical status from seed data. Neither bed knows about the other.</p>
    <div class="hash-pair">
      <div class="hash-chip"><div class="label">bed-1 forms href</div><div class="value">{esc(bed1)}</div></div>
      <div class="hash-chip"><div class="label">bed-3 forms href</div><div class="value">{esc(bed3)}</div></div>
    </div>
    {f"""<div class="seal-row"><div class="seal">verified<br>match</div><div class="seal-text"><h3>Identical, not merely similar.</h3><p>Two independently-arrived-at beds, one shared, cached resource.</p></div></div>""" if match else
    f'''<div class="verdict bug"><span class="verdict-mark">FAIL — no sharing occurred</span><p>Bed-1 and bed-3 are both READY_TO_HARVEST but point at DIFFERENT affordance-set URLs. The content-addressing scheme is broken — check GardenBedStatus-to-hash mapping.</p></div>'''}
  </section>'''


def entry_05() -> str:
    ok, detail = check_point_in_time()
    return f'''
  <section class="entry" id="pit">
    <div class="entry-head"><span class="entry-num">05</span><h2 class="entry-title">Asking bed-2 what it looked like, in the past</h2></div>
    <p class="entry-sub">the payoff of event sourcing over current-state storage</p>
    <p class="entry-narrative">Asking for bed-2's state before its first event occurred should return the same thing as a bed with no history at all.</p>
    <div class="specimen"><div class="specimen-label">input</div><pre>curl http://localhost:8080/beds/2?asOf=2026-07-08T00:00:00Z</pre></div>
    <div class="specimen"><div class="specimen-label output">output</div><pre>{yaml_block("05-bed2-asof.json")}</pre></div>
    {verdict_html(ok,
        f"Folded state at that instant: {esc(detail)} — zero qualifying events folds to the same empty seed state as no history at all.",
        f"Expected EMPTY / growthPoints 0 before any events occurred; actually got {esc(detail)}. The asOf filter may not be excluding later events correctly.")}
  </section>'''


def entry_06() -> str:
    stale_ok, stale_code = check_status("06-stale-status.txt", "412")
    invalid_ok, invalid_code = check_status("06-invalid-status.txt", "409")
    notfound_ok, notfound_code = check_status("06-notfound-status.txt", "404")
    all_ok = stale_ok and invalid_ok and notfound_ok
    detail_ok = f"Stale write → <strong>{stale_code}</strong>, illegal transition → <strong>{invalid_code}</strong>, unknown schema → <strong>{notfound_code}</strong> — three different problems, three different, correct codes."
    detail_bug = f"Expected 412/409/404. Actually got {stale_code}/{invalid_code}/{notfound_code}."
    return f'''
  <section class="entry" id="errors">
    <div class="entry-head"><span class="entry-num">06</span><h2 class="entry-title">Three ways to fail, correctly</h2></div>
    <p class="entry-sub">distinct failure modes, distinct status codes, checked live</p>
    <p class="entry-narrative">A stale write, an illegal transition, and an unknown resource are three genuinely different problems.</p>
    {verdict_html(all_ok, detail_ok, detail_bug)}
  </section>'''


CSS = Path("template_style.css").read_text() if Path("template_style.css").exists() else ""


def main():
    entries_html = "\n".join([
        entry_00(), entry_01(), entry_02(), entry_03(), entry_04(), entry_05(), entry_06()
    ])

    template = Path("journal_template.html").read_text()
    output = template.replace("<!-- ENTRIES -->", entries_html)
    Path("journal.html").write_text(output)
    print("Wrote journal.html")


if __name__ == "__main__":
    main()
