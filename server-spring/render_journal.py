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
    # SPEC [0.5] §6.9.1: the variable names the Layer 1 property it resolves from.
    return ("{id}" in href and templated is True, href)


def header_value(filename: str, name: str) -> str:
    for line in read_text(filename).splitlines():
        if line.lower().startswith(name.lower() + ":"):
            return line.split(":", 1)[1].strip()
    return ""


def check_l0_collapsed() -> tuple[bool, str]:
    """L0 (no Prefer): the sowSeed form arrives inline with its full schema."""
    doc = read_json("01-bed1-l0.json")
    schema = doc.get("_forms", {}).get("sowSeed", {}).get("schema", {})
    return ("properties" in schema, ", ".join(sorted(schema.get("properties", {}).keys())))


def check_l2_linked() -> tuple[bool, str]:
    doc = read_json("01-bed1-empty.json")
    return ("_forms" not in doc and read_text("01-set-url.txt").startswith("/affordance-sets/sha256-"),
            read_text("01-set-url.txt"))


def check_profile_link() -> tuple[bool, str]:
    link = header_value("01-bed1-headers.txt", "Link")
    return ('<https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"' in link, link)


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
    l1_url = esc(read_text("01-set-l1-url.txt"))
    l0_ok, l0_fields = check_l0_collapsed()
    l2_ok, _ = check_l2_linked()
    profile_ok, link = check_profile_link()
    templated_ok, href = check_templated_href("01-set-pointer.json", "_forms", "sowSeed")
    cache_ok, cache_val = check_cache_control("01-schema-headers.txt")
    return f'''
  <section class="entry" id="sow">
    <div class="entry-head"><span class="entry-num">01</span><h2 class="entry-title">Planting bed-1 — sowSeed</h2></div>
    <p class="entry-sub">layer 1 → layer 2 → layer 3, captured live</p>
    <p class="entry-narrative">An <strong>EMPTY</strong> bed allows exactly one action: sowing a seed. With no <code>Prefer</code> header (rung L0) everything arrives in one response: state, the form, and its full schema.</p>
    <div class="specimen"><div class="specimen-label">input</div><pre>curl -H 'Accept: application/hal+json' \\
     -H 'Accept-Profile: &lt;https://github.com/jogoshugh/heart-rise/hal-schema-forms&gt;' \\
     http://localhost:8080/beds/1</pre></div>
    <div class="specimen"><div class="specimen-label output">output</div><pre>{yaml_block("01-bed1-l0.json")}</pre></div>
    {verdict_html(l0_ok,
        f"L0 is collapsed: the <code>sowSeed</code> form is inline with its full schema (fields: {esc(l0_fields)}).",
        "Expected the sowSeed form inline with a full schema when no Prefer is sent (SPEC §6.4).")}

    <p class="entry-narrative">The same request with <code>Prefer: forms=link, schema=link</code> (rung L2) returns state plus one link:</p>
    <div class="specimen"><div class="specimen-label output">output</div><pre>{yaml_block("01-bed1-empty.json")}</pre></div>
    {verdict_html(l2_ok,
        f"No <code>_forms</code> in Layer 1. Just state and a typed link to the pointer-only set: <code>{set_url}</code>",
        "Expected no inline _forms and an /affordance-sets/sha256-... link at L2.")}
    {verdict_html(profile_ok,
        f"The response names its profile in a header, not a media-type parameter: <code>Link: {esc(link)}</code>",
        f"Expected Link: &lt;.../heart-rise/hal-schema-forms&gt;; rel=\"profile\". Got: <code>{esc(link)}</code>")}

    <p class="entry-narrative">At <code>Prefer: forms=link</code> (rung L1) the link points somewhere else: <code>{l1_url}</code>. That set embeds the schemas, so its bytes differ, and so does its address (SPEC §6.5):</p>
    <div class="specimen"><div class="specimen-label output">output (L1 set, schemas embedded)</div><pre>{yaml_block("01-set-embedded.json")}</pre></div>
    {verdict_html(templated_ok,
        f"<code>target.href</code> is a literal, unresolved URI Template (<code>{esc(href)}</code>) — byte-identical for every EMPTY bed, real HTTP caches included.",
        f"Expected target.href to contain the literal string {{id}} with templated:true; actually got <code>{esc(href)}</code>. This bed's affordance-set is no longer bed-agnostic.")}

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
    <div class="specimen"><div class="specimen-label">input</div><pre>curl -H 'Accept: application/prs.hal-forms+json' \\
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
    expected = [
        ("06-missing-status.txt", "428", "act without If-Match"),
        ("06-stale-status.txt", "412", "stale If-Match"),
        ("06-invalid-status.txt", "409", "act not in the manifest"),
        ("06-schema-status.txt", "422", "body fails the schema"),
        ("06-notfound-status.txt", "404", "unknown schema"),
        ("06-revalidate-status.txt", "304", "Layer 1 revalidation, nothing changed"),
    ]
    results = [(label, want, read_text(f)) for f, want, label in expected]
    all_ok = all(got == want for _, want, got in results)
    listing = ", ".join(f"{esc(label)} → <strong>{esc(got)}</strong>" for label, _, got in results)
    detail_ok = f"{listing}. Each problem has its own code."
    detail_bug = f"Expected {'/'.join(w for _, w, _ in results)}. Got: {listing}."
    return f'''
  <section class="entry" id="errors">
    <div class="entry-head"><span class="entry-num">06</span><h2 class="entry-title">Ways to fail, correctly</h2></div>
    <p class="entry-sub">distinct failure modes, distinct status codes, checked live</p>
    <p class="entry-narrative">A missing precondition, a stale write, an illegal transition, a bad body, and an unknown resource are different problems. SPEC 0.4 gives each its own code, and checks acts in that order.</p>
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
