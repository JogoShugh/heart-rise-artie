//@HELPERS
// Sources, each a hyperlink, on a line above the footer.
function src(s, pairs, dark = false, y = 6.62) {
  const runs = [{ text: "Sources:  ", options: { color: SAGE, bold: true } }];
  pairs.forEach(([label, url], i) => {
    runs.push({ text: label, options: { hyperlink: { url }, color: dark ? GOLD : GREEN } });
    if (i < pairs.length - 1) runs.push({ text: "  ·  ", options: { color: SAGE } });
  });
  s.addText(runs, { x: 0.6, y, w: 12.1, h: 0.3, fontSize: 10, margin: 0, isTextBox: true, valign: "middle" });
}
// A numbered box drawn on a screenshot, at pixel coordinates of the picture, with an arrow from a label at (lx, ly).
function callouts(s, img, { x, y, w, pxW, pxH }, items, lx) {
  const k = w / pxW;
  const h = pxH * k;
  shot(s, img, x, y, w, h);
  items.forEach(([n, [x1, y1, x2, y2], label, detail], i) => {
    const bx = x + x1 * k, by = y + y1 * k, bw = (x2 - x1) * k, bh = (y2 - y1) * k;
    s.addShape(pres.shapes.RECTANGLE, { x: bx, y: by, w: bw, h: bh, fill: { color: GOLD, transparency: 85 }, line: { color: GOLD, width: 2.5 } });
    const ly = 1.65 + i * 0.98;
    // the label
    s.addShape(pres.shapes.OVAL, { x: lx, y: ly, w: 0.38, h: 0.38, fill: { color: GOLD }, line: { color: GOLD } });
    T(s, String(n), { x: lx, y: ly, w: 0.38, h: 0.38, fontSize: 13, bold: true, align: "center", valign: "middle" });
    T(s, label, { x: lx + 0.5, y: ly - 0.04, w: 12.73 - lx - 0.5, h: 0.32, fontSize: 14, bold: true, fontFace: "Courier New", color: GREEN });
    T(s, detail, { x: lx + 0.5, y: ly + 0.3, w: 12.73 - lx - 0.5, h: 0.6, fontSize: 12, color: INK });
    // the arrow, from the label's left edge to the box's right edge
    const sx = lx - 0.05, sy = ly + 0.19, ex = bx + bw + 0.02, ey = by + bh / 2;
    const o = { x: Math.min(sx, ex), y: Math.min(sy, ey), w: Math.max(Math.abs(sx - ex), 0.01), h: Math.max(Math.abs(sy - ey), 0.01), line: { color: GOLD, width: 1.75, endArrowType: "triangle" } };
    if (sx > ex && ey >= sy) o.flipH = true;
    else if (sx > ex && ey < sy) { o.flipH = true; o.flipV = true; }
    s.addShape(pres.shapes.LINE, o);
  });
}
//@END

//@AWS
// Precursor: AWS API Gateway, HAL with typed relations
{
  const s = slide("A precursor, still live", "Amazon API Gateway: relations that name the acts", false,
    "Live, current AWS documentation: GetClientCertificates. HAL: curies point each relation to its docs page; typed relations name what you can do: by-id, generate, and on each embedded certificate delete and update. What it lacks: the method, the fields, the schema. A client still reads the docs page the curie points to. HAL Schema Forms, the base of HEART's media type, puts exactly that in the response: method, content type, JSON Schema. HEART then scopes the forms to state.");
  shot(s, "aws-live.png", 0.6, 1.65, 3.55, 3.55 * 2084 / 1648, "GetClientCertificates, sample response.");
  T(s, "The relation names the act…", { x: 4.85, y: 1.65, w: 3.7, h: 0.4, fontSize: 18, bold: true, fontFace: "Cambria", color: GREEN });
  bullets(s, [
    [{ text: "clientcertificate:generate", b: true }, { text: " on the list." }],
    [{ text: "clientcertificate:delete", b: true }, { text: " and " }, { text: "update", b: true }, { text: " on each certificate, beside its data." }],
    [{ text: "curies", b: true }, { text: " link each relation to its docs page." }],
  ], { x: 4.85, y: 2.15, w: 3.7, h: 2.2, fontSize: 14 });
  T(s, "…but not how to do it.", { x: 4.85, y: 4.2, w: 3.7, h: 0.4, fontSize: 18, bold: true, fontFace: "Cambria", color: RUST });
  T(s, "No method, no fields, no schema. The client still reads the curie’s docs page: out of band.", { x: 4.85, y: 4.65, w: 3.7, h: 1.3, fontSize: 14 });
  code(s, 8.85, 1.65, 3.85, 4.4, [
    ["c", "# HAL Schema Forms adds the form"],
    ["k", "_forms:"],
    ["p", "  scm:merge:"],
    ["p", "    _links:"],
    ["p", "      target: {href: …/merge}"],
    ["g", "    method: PUT"],
    ["g", "    contentType: application/json"],
    ["g", "    schema:"],
    ["g", "      type: object"],
    ["g", "      properties:"],
    ["g", "        method:"],
    ["g", "          enum: [merge, squash,"],
    ["g", "                 rebase]"],
    ["c", "# HEART: only while it applies"],
  ], 12);
  src(s, [["AWS: GetClientCertificates", "https://docs.aws.amazon.com/apigateway/latest/api/API_GetClientCertificates.html"], ["HAL (Kelly)", "https://datatracker.ietf.org/doc/html/draft-kelly-json-hal-11"], ["HAL Schema Forms (Badeau)", "https://github.com/jbadeau/hal-schema-forms"]]);
}
//@END

//@PROD
// 12. In production
{
  const s = slide("Not new, not niche", "Hypermedia is already in production", false,
    "Okta's Authentication API answers with HAL: _embedded for the user and policy, _links for what can be done next, here changePassword or cancel, because the password expired. Amazon API Gateway documents HAL with typed relations today; it gets previewed as HEART a few slides on. Spring HATEOAS is being pitched for agents now: Adib Saikali, Spring I/O 2026. What HEART adds: forms with JSON Schema, guards, and typed relations.");
  shot(s, "okta-crop.png", 0.6, 1.65, 3.55, 3.55 * 2200 / 1640, "Okta Authentication API: expired password.");
  T(s, "Okta", { x: 4.6, y: 1.65, w: 3.6, h: 0.4, fontSize: 20, bold: true, fontFace: "Cambria", color: GREEN });
  T(s, "The password expired, so the answer’s _links offer next: changePassword, and cancel. Nothing else. State, then controls, in one answer.", { x: 4.6, y: 2.1, w: 3.6, h: 1.8, fontSize: 15 });
  T(s, "Amazon API Gateway", { x: 4.6, y: 4.0, w: 3.6, h: 0.4, fontSize: 20, bold: true, fontFace: "Cambria", color: GREEN });
  T(s, "HAL with typed relations, in its current API reference. We\u2019ll preview it as HEART shortly.", { x: 4.6, y: 4.45, w: 3.6, h: 1.2, fontSize: 15 });
  card(s, 8.6, 1.65, 4.1, 4.3, C.text1);
  T(s, "Spring HATEOAS, for agents", { x: 8.85, y: 1.9, w: 3.6, h: 0.5, fontSize: 20, bold: true, fontFace: "Cambria", color: GOLD });
  T(s, "Adib Saikali (Broadcom), “REST is for Agents: Rediscovering Spring HATEOAS”, Spring I/O 2026.", { x: 8.85, y: 2.5, w: 3.6, h: 1.3, fontSize: 15, color: C.background1 });
  T(s, "What HEART adds: forms with JSON Schema, If-Match guards, typed relations, and only the forms that apply now.", { x: 8.85, y: 3.9, w: 3.6, h: 1.8, fontSize: 15, color: C.accent6 });
  src(s, [["Okta Authentication API", "https://developer.okta.com/docs/api/resources/authn"], ["AWS: GetClientCertificates", "https://docs.aws.amazon.com/apigateway/latest/api/API_GetClientCertificates.html"], ["Spring I/O 2026: REST is for Agents", "https://2026.springio.net/sessions/rest-is-for-agents-rediscovering-spring-hateoas/"]]);
}
//@END

//@KEPT
// Kept YAML vs Code Mode
{
  const s = slide("What gets kept", "A kept script is data, not a program", false,
    "Code Mode (Cloudflare, September 2025; Anthropic's code execution with MCP, November 2025) has the model write TypeScript against tool APIs and runs it in a sandbox. That's a real improvement on stuffing tool lists into context. But what you keep is a program: it needs code review, a sandbox, dependency hygiene, and it can call anything its bindings expose. ARTIE keeps YAML: no loops you didn't capture, no imports, no eval. Every act names a relation, and before it's sent ARTIE checks the form is still offered and still matches its pin. A review is reading data.");
  code(s, 0.6, 1.65, 6.4, 4.75, [
    ["c", "# release.heart.yaml (excerpt), kept by artie generate"],
    ["k", "heart: 1"],
    ["k", "kind: kept"],
    ["p", "steps:"],
    ["p", "  - act: scm:pulls-get"],
    ["p", "    fields: {owner: ${owner}, repo: ${repo},"],
    ["p", "             pull_number: ${pull}}"],
    ["p", "    then: [keep-as: pr]"],
    ["p", "  - enter: ${watcher}"],
    ["p", "    steps:"],
    ["p", "      - ask: watcher:watch"],
    ["p", "        fields: {resource: ${pr.@self},"],
    ["p", "                 offers: scm:merge}"],
    ["p", "  - once-answered: {reply: watch, steps: […"],
    ["g", "      - act: scm:merge"],
    ["g", "        fields: {method: squash}"],
    ["c", "# release.pins.yaml: what each form was, by hash"],
    ["p", "  scm:merge: {method: PUT,"],
    ["p", "    integrity: sha256-sGUdrPfH…}"],
  ], 11);
  const col = (x, head, color, items) => {
    T(s, head, { x, y: 1.65, w: 2.7, h: 0.45, fontSize: 17, bold: true, fontFace: "Cambria", color });
    bullets(s, items, { x, y: 2.15, w: 2.7, h: 4.2, fontSize: 13 });
  };
  col(7.3, "Kept YAML (ARTIE)", GREEN, [
    "Data. No loops, imports or eval to review.",
    "Every act names a relation the server must still offer.",
    "Pinned by hash: a changed form stops the replay.",
    "Runs with no model, in any HEART client.",
  ]);
  col(10.1, "Code Mode", RUST, [
    "The model writes TypeScript or Python against tool APIs.",
    "What you keep is a program: code review, sandbox, dependencies.",
    "It can call anything its bindings expose, in any order.",
    "A real step up from tool lists in context.",
  ]);
  src(s, [["Cloudflare: Code Mode (2025)", "https://blog.cloudflare.com/code-mode/"], ["Anthropic: Code execution with MCP", "https://www.anthropic.com/engineering/code-execution-with-mcp"], ["artie (repository)", "https://github.com/JogoShugh/heart-rise-artie"]]);
}
//@END

//@GH
// GitHub annotated
{
  const s = slide("Typed relations  ·  On GitHub", "What each scm: relation did, on GitHub’s own page", false,
    "Pull request #2337 on real GitHub, variant 504: merged, then the smoke test failed scenario S3, so it wasn't promoted and the script commented. Each box is something one scm: relation read or did. The page is GitHub's; ARTIE never saw it, only the gateway's HAL Schema Forms.");
  callouts(s, "gh504.png", { x: 0.6, y: 1.65, w: 7.2, pxW: 920, pxH: 550 }, [
    [1, [28, 22, 578, 64], "scm:pulls-get", "Read pull request 2337, kept as pr."],
    [2, [28, 70, 132, 105], "state, merged", "state: closed, merged: true, read after the act."],
    [3, [356, 72, 616, 100], "head.sha", "The head the view had: GitHub merges only if it’s still there."],
    [4, [86, 364, 554, 400], "scm:merge {method: squash}", "Squashed into library/main as 1361304."],
    [5, [86, 434, 906, 534], "scm:comment {body}", "“Not promoted to Dev: smoke scenarios S3 failed.”"],
  ], 8.35);
  src(s, [["Pull request #2337", "https://github.com/JogoShugh/heart-github-test/pull/2337"], ["GitHub: merge a pull request", "https://docs.github.com/en/rest/pulls/pulls#merge-a-pull-request"], ["GitHub: create an issue comment", "https://docs.github.com/en/rest/issues/comments#create-an-issue-comment"]]);
}
//@END

//@GL
// GitLab annotated
{
  const s = slide("Typed relations  ·  On GitLab", "The same relations, on GitLab’s page", false,
    "Merge request !504 on the GitLab running on this machine, the same variant, the same script. Same five things, different words on the page: GitLab says merge request, Merged, pr-504 into main, commits were squashed. ARTIE read and sent the same scm: relations on both.");
  callouts(s, "gl504.png", { x: 0.6, y: 1.65, w: 4.75, pxW: 680, pxH: 660 }, [
    [1, [6, 8, 442, 54], "scm:pulls-get", "Read merge request 504, kept as pr."],
    [2, [6, 52, 89, 80], "state, merged", "GitLab’s merged becomes state: closed, merged: true."],
    [3, [316, 53, 470, 81], "head.sha", "GitLab’s sha: it merges only if the source head is still there."],
    [4, [10, 266, 668, 408], "scm:merge {method: squash}", "“Changes merged into main with 8ef9444b (commits were squashed).”"],
    [5, [48, 563, 669, 642], "scm:comment {body}", "The same comment, as a GitLab note."],
  ], 7.0);
  src(s, [["GitLab: merge requests API", "https://docs.gitlab.com/api/merge_requests/"], ["GitLab: notes API", "https://docs.gitlab.com/api/notes/"]]);
}
//@END

//@MAP
// The mapping
{
  const s = slide("Typed relations  ·  One relation, two native calls", "How each scm: relation maps to each forge", false,
    "Every row is from the recorded runs: ARTIE's request to the gateway, and the gateway's call upstream, joined by traceparent. ARTIE sends the same thing to both gateways. Only the connector's YAML knows the forge.");
  const hd = (t) => ({ text: t, options: { bold: true, color: C.background1, fill: { color: C.text1 } } });
  const m = (t) => ({ text: t, options: { fontFace: "Courier New", fontSize: 10.5 } });
  const r = (t) => ({ text: t, options: { bold: true, color: GREEN, fontFace: "Courier New", fontSize: 11 } });
  s.addTable([
    [hd("scm: relation"), hd("ARTIE sends, either forge"), hd("GitHub, natively"), hd("GitLab, natively")],
    [r("scm:pulls-get"), m("act scm:pulls-get\n{owner, repo, pull_number}"), m("GET /repos/{o}/{r}/pulls/2337"), m("GET /projects/acme%2Fweb/merge_requests/504")],
    [r("state, merged"), m("state: closed\nmerged: true"), m("state, merged (as is)"), m("state: merged → closed, true")],
    [r("mergeable_state"), m("clean, blocked, dirty, behind…"), m("mergeable_state (as is)"), m("detailed_merge_status:\nmergeable → clean, conflict → dirty")],
    [r("scm:merge"), m("PUT the form's target\nIf-Match: \"seq-0.hsf.L2.…\"\n{method: squash}"), m("PUT /repos/{o}/{r}/pulls/2337/merge\n{merge_method: squash, sha: head.sha}"), m("PUT /projects/acme%2Fweb/merge_requests\n/504/merge {squash: true, sha}")],
    [r("scm:comment"), m("POST the form's target\n{body}"), m("POST /repos/{o}/{r}/issues/2337/comments\n{body}"), m("POST /projects/acme%2Fweb/merge_requests\n/504/notes {body}")],
  ], { x: 0.6, y: 1.6, w: 12.1, colW: [1.9, 3.0, 3.6, 3.6], fontSize: 11, color: INK, border: { type: "solid", pt: 0.5, color: "C9D8CE" }, valign: "middle", margin: 0.06 });
  T(s, [{ text: "Stale or refused, the same on both: ", options: { bold: true, color: GREEN } }, { text: "the forge’s 409 (head moved) becomes 412, and its 405 (can’t merge now) a refusal, so ARTIE handles one set of answers." }], { x: 0.6, y: 5.85, w: 12.1, h: 0.6, fontSize: 14 });
  src(s, [["GitHub: pulls", "https://docs.github.com/en/rest/pulls/pulls#merge-a-pull-request"], ["GitHub: comments", "https://docs.github.com/en/rest/issues/comments#create-an-issue-comment"], ["GitLab: merge requests", "https://docs.gitlab.com/api/merge_requests/"], ["GitLab: notes", "https://docs.gitlab.com/api/notes/"]]);
}
//@END

//@CONN
// Connector YAML, side by side
{
  const s = slide("For the engineers  ·  The connectors", "scm:merge, written once per forge", false,
    "The merge act in each connector. Same relation, same input (method), same guard (the head sha the view had), same stale and refused codes. The upstream operation, the field names and the values differ, and only here.");
  code(s, 0.6, 1.6, 6.0, 4.85, [
    ["c", "# github-scm/pull.resource.yaml"],
    ["k", "merge:"],
    ["p", "  operation: pulls/merge"],
    ["p", "  fields: [commit_title, commit_message]"],
    ["p", "  inputs:"],
    ["p", "    method:"],
    ["p", "      enum: [merge, squash, rebase]"],
    ["g", "  values:"],
    ["g", "    method:"],
    ["g", "      merge:  {merge_method: merge}"],
    ["g", "      squash: {merge_method: squash}"],
    ["g", "      rebase: {merge_method: rebase}"],
    ["p", "  bind: {sha: /head/sha}"],
    ["p", "  stale: 409"],
    ["p", "  refused: 405"],
  ], 12);
  code(s, 6.7, 1.6, 6.0, 4.85, [
    ["c", "# gitlab/pull.resource.yaml"],
    ["k", "merge:"],
    ["p", "  operation: pulls/merge"],
    ["p", "  fields: [merge_commit_message]"],
    ["p", "  inputs:"],
    ["p", "    method:"],
    ["p", "      enum: [merge, squash]"],
    ["g", "  values:"],
    ["g", "    method:"],
    ["g", "      merge:  {squash: false}"],
    ["g", "      squash: {squash: true}"],
    ["p", "  bind: {sha: /sha}"],
    ["p", "  stale: 409"],
    ["p", "  refused: 405"],
    ["c", "# + normal: state, merged, mergeable_state"],
  ], 12);
  src(s, [["artie (repository)", "https://github.com/JogoShugh/heart-rise-artie"], ["GitHub OpenAPI description", "https://github.com/github/rest-api-description"], ["GitLab merge requests API", "https://docs.gitlab.com/api/merge_requests/"]]);
}
//@END

//@SKILL
// The prompt: SKILL.md
{
  const s = slide("For the engineers  ·  The prompt", "The skill tells the model the loop, and that the gates win", false,
    "The bdd-gates skill's own words, from SKILL.md. The skill is instructions, and instructions are advisory; that's why it installs hooks and says, in its first paragraph, that the hooks win.");
  code(s, 0.6, 1.6, 6.3, 4.85, [
    ["c", "# SKILL.md, the opening"],
    ["p", "Instructions are advisory. A model can drift"],
    ["p", "past them. These gates are real processes with"],
    ["p", "real exit codes, so they hold even when"],
    ["p", "attention slips. This skill installs them and"],
    ["p", "explains the loop they enforce. **The skill"],
    ["p", "never replaces the hooks**: if a gate blocks"],
    ["p", "you, satisfy it; never edit it away."],
  ], 13);
  code(s, 7.1, 1.6, 5.6, 4.85, [
    ["c", "# SKILL.md, the work loop"],
    ["p", "1. Write or change the .feature scenario."],
    ["p", "   Use Scenario Outlines and data tables,"],
    ["p", "   not repeated Given/When/Then blocks."],
    ["p", "2. Run the BDD suite (native command, or"],
    ["p", "   .claude/bdd-gates/bdd-run.sh on wrapper"],
    ["p", "   stacks). See it fail for the right"],
    ["p", "   reason: BDD_RUN FAIL."],
    ["p", "3. Write step definitions, then production"],
    ["p", "   code. The gate now allows it."],
    ["p", "4. Run the suite until BDD_RUN PASS. Fix"],
    ["p", "   lint findings as they appear."],
    ["p", "5. End the turn. The Stop gate runs the"],
    ["p", "   full check."],
  ], 12);
  src(s, [["bdd-gates skill (repository)", "https://github.com/JogoShugh/bdd-gates-skill"], ["Claude Code hooks", "https://code.claude.com/docs/en/hooks"], ["Gherkin reference", "https://cucumber.io/docs/gherkin/reference/"]]);
}
//@END

//@WIRING
// hooks.json
{
  const s = slide("For the engineers  ·  The wiring", "Five hook events, each a shell script", false,
    "core/hooks.json, merged into .claude/settings.json by install.sh. protect-gate runs before every tool; gherkin-first before every edit; gate-bash before every shell command; log-event and lint after every edit; check.sh when the model tries to stop. Exit code 2 blocks the call and sends stderr back to the model.");
  code(s, 0.6, 1.6, 6.6, 4.85, [
    ["c", "// core/hooks.json (abridged)"],
    ["k", "\"PreToolUse\": ["],
    ["p", "  { \"matcher\": \"*\","],
    ["g", "    …/protect-gate.sh },"],
    ["p", "  { \"matcher\": \"Edit|Write|MultiEdit\","],
    ["g", "    …/gherkin-first.sh },"],
    ["p", "  { \"matcher\": \"Bash\","],
    ["g", "    …/gate-bash.sh } ],"],
    ["k", "\"PostToolUse\": ["],
    ["p", "  { \"matcher\": \"Edit|Write|MultiEdit\","],
    ["g", "    …/log-event.sh, …/lint.sh } ],"],
    ["k", "\"Stop\": ["],
    ["g", "  …/check.sh  (timeout 600) ],"],
    ["k", "\"UserPromptSubmit\": ["],
    ["g", "  …/refactor-prompt.sh ]"],
  ], 12);
  T(s, "Exit 2 blocks the call and hands stderr to the model.", { x: 7.5, y: 1.6, w: 5.2, h: 0.8, fontSize: 18, bold: true, fontFace: "Cambria", color: GREEN });
  T(s, "Blocked in this very session, word for word:", { x: 7.5, y: 2.55, w: 5.2, h: 0.4, fontSize: 14, color: SAGE });
  code(s, 7.5, 3.0, 5.2, 2.4, [
    ["r", "Blocked: this command reaches the"],
    ["r", "TDD gate or refactor mode."],
    ["p", ""],
    ["r", "Blocked: run tests through"],
    ["r", ".claude/bdd-gates/bdd-run.sh [extra"],
    ["r", "args] so the BDD_RUN result is logged."],
  ], 12);
  T(s, "The second stopped a raw test run that would have left no BDD_RUN line in the log.", { x: 7.5, y: 5.6, w: 5.2, h: 0.8, fontSize: 13 });
  src(s, [["bdd-gates: core/hooks.json", "https://github.com/JogoShugh/bdd-gates-skill"], ["Claude Code hooks reference", "https://code.claude.com/docs/en/hooks"]]);
}
//@END

//@GHERKIN
// gherkin-first.sh and log-event.sh
{
  const s = slide("For the engineers  ·  Gherkin first", "No production write until a feature file changes", false,
    "gherkin-first.sh runs before every Edit or Write. If the file is production code and no feature file has an in-flight change, it exits 2 with a message the model reads. log-event.sh, after every edit, appends a fact to the event log, which the pre-commit check reads in order.");
  code(s, 0.6, 1.6, 6.9, 4.85, [
    ["c", "# core/gherkin-first.sh (PreToolUse)"],
    ["p", "rel=$(bdd_rel \"$file_path\")"],
    ["p", "bdd_is_prod \"$rel\" || exit 0"],
    ["p", "bdd_features_dirty && exit 0"],
    ["c", "# The author switched refactor mode on"],
    ["p", "bdd_refactor_on && exit 0"],
    ["p", ""],
    ["p", "cat >&2 <<EOF"],
    ["r", "Blocked: about to modify production code"],
    ["r", "($rel) with no feature file change in this"],
    ["r", "changeset yet. Write or update the Gherkin"],
    ["r", "scenario first, run the BDD suite to see it"],
    ["r", "fail, then implement."],
    ["p", "EOF"],
    ["k", "exit 2"],
  ], 12);
  code(s, 7.75, 1.6, 4.95, 2.6, [
    ["c", "# core/log-event.sh (PostToolUse)"],
    ["p", "if bdd_is_prod \"$rel\"; then"],
    ["g", "  bdd_append \"MAIN_WRITE $rel\""],
    ["p", "elif bdd_is_feature \"$rel\"; then"],
    ["g", "  bdd_append \"FEATURE_WRITE $rel\""],
    ["p", "fi"],
  ], 12);
  code(s, 7.75, 4.4, 4.95, 2.05, [
    ["c", "# core/bdd-run.sh: every run, pass or fail"],
    ["p", "if [ $ec -eq 0 ]; then"],
    ["g", "  bdd_append \"BDD_RUN PASS exit=0\""],
    ["p", "else"],
    ["r", "  bdd_append \"BDD_RUN FAIL exit=$ec\""],
    ["p", "fi"],
  ], 12);
  src(s, [["bdd-gates: core/", "https://github.com/JogoShugh/bdd-gates-skill"]]);
}
//@END

//@PRECOMMIT
// pre-commit
{
  const s = slide("For the engineers  ·  The commit", "git pre-commit reads the log in order", false,
    "The git pre-commit hook works for any committer, not just Claude Code. Since the last commit, the event log must show a feature write before the first production write, a failing BDD run before the first production write, and a passing final run. Order comes from line order in the append-only log, so events in the same second compare correctly.");
  code(s, 0.6, 1.6, 7.4, 4.85, [
    ["c", "# .githooks/pre-commit (abridged)"],
    ["p", "feature=$(first_line ' FEATURE_WRITE ')"],
    ["p", "fail=$(first_line \"${run_re}FAIL\")"],
    ["p", "main=$(first_line ' MAIN_WRITE ')"],
    ["p", "[ -n \"$feature\" ] || reasons+=(\"no FEATURE_WRITE"],
    ["p", "  since the last commit\")"],
    ["p", "[ -n \"$fail\" ] || reasons+=(\"no failing BDD run"],
    ["p", "  since the last commit (the scenario was never"],
    ["p", "  seen red)\")"],
    ["p", "[ \"$fail\" -gt \"$main\" ] && reasons+=(\"production"],
    ["p", "  code was written before the BDD suite was"],
    ["p", "  seen failing\")"],
    ["p", "case \"$last_run\" in *\" PASS\"*) ;; *) reasons+=("],
    ["p", "  \"the latest BDD run … is not PASS\") ;; esac"],
    ["g", "echo \"pre-commit: TDD ordering check passed.\""],
  ], 12);
  T(s, "Three facts, in order", { x: 8.3, y: 1.6, w: 4.4, h: 0.45, fontSize: 18, bold: true, fontFace: "Cambria", color: GREEN });
  ["FEATURE_WRITE", "BDD_RUN FAIL", "MAIN_WRITE …", "BDD_RUN PASS"].forEach((t, i) => {
    pill(s, 8.3, 2.2 + i * 0.62, 3.2, t, i === 1 ? RUST : i === 3 ? GREEN : C.accent6, i === 0 || i === 2 ? INK : C.background1);
    if (i < 3) T(s, "↓", { x: 11.6, y: 2.25 + i * 0.62, w: 0.4, h: 0.4, fontSize: 16, color: SAGE });
  });
  T(s, "Works for a plain terminal git commit too. --no-verify and git commit -n are blocked for the model by protect-gate.sh.", { x: 8.3, y: 4.85, w: 4.4, h: 1.4, fontSize: 13 });
  src(s, [["bdd-gates: core/pre-commit", "https://github.com/JogoShugh/bdd-gates-skill"], ["Git hooks", "https://git-scm.com/docs/githooks"]]);
}
//@END

//@FOUND
// 19. Foundations
{
  const s = slide("Foundations", "Standing on these shoulders", false,
    "Links only. Fielding; Mike Kelly's HAL; Mike Amundsen's HAL-FORMS; J. Badeau's HAL Schema Forms, the base of HEART's media type, and HyperBench; Kin Lane; Adib Saikali; Rohit Bhardwaj and Michael Carducci on hypermedia and agents; Kelsey Hightower's Zero Token Architecture, an inspiration for this talk; the W3C Autonomous Agents on the Web Community Group; and one of numerous research papers, Ciortea et al. at AAMAS 2019.");
  const L = (who, what, url) => [{ text: who, options: { bold: true, hyperlink: { url }, color: GREEN } }, { text: "  " + what, options: { color: INK } }];
  const left = [
    L("Roy T. Fielding", "the dissertation (2000)", "https://ics.uci.edu/~fielding/pubs/dissertation/top.htm"),
    L("Roy T. Fielding", "“REST APIs must be hypertext-driven” (2008)", "https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven"),
    L("Mike Kelly", "HAL", "https://datatracker.ietf.org/doc/html/draft-kelly-json-hal-11"),
    L("Mike Amundsen", "HAL-FORMS", "https://rwcbook.github.io/hal-forms/"),
    L("J. Badeau", "HAL Schema Forms, the base of HEART’s media type", "https://github.com/jbadeau/hal-schema-forms"),
    L("J. Badeau", "HyperBench, a hypermedia-driven workbench", "https://github.com/jbadeau/hyperbench"),
  ];
  const right = [
    L("Kin Lane", "API Evangelist", "https://apievangelist.com"),
    L("Adib Saikali", "“REST is for Agents”, Spring I/O 2026", "https://2026.springio.net/sessions/rest-is-for-agents-rediscovering-spring-hateoas/"),
    L("Rohit Bhardwaj", "(Salesforce) “From REST to Reasoning Agents”", "https://nofluffjuststuff.com/topics/from_rest_to_reasoning_agents_designing_apis_for_the_ai_era"),
    L("Michael Carducci", "“Hypermedia APIs and the Future of AI Agentic Systems”", "https://nofluffjuststuff.com/topics/hypermedia_apis_and_the_future_of_ai_agentic_systems"),
    L("Kelsey Hightower", "“ZTA: Zero Token Architecture”, WeAreDevelopers 2026: infer once, run everywhere", "https://www.wearedevelopers.com/videos/100533-zta-zero-token-architecture"),
    L("W3C", "Autonomous Agents on the Web Community Group", "https://www.w3.org/community/webagents/"),
    L("Ciortea et al.", "“A Decade in Hindsight”, AAMAS 2019: one of numerous papers", "https://dblp.org/rec/conf/atal/CiorteaMGBRZ19"),
  ];
  const list = (items, x) => s.addText(items.map((r, i) => r.map((run, j) => ({ text: run.text, options: { ...run.options, breakLine: j === r.length - 1 && i < items.length - 1, paraSpaceAfter: 12 } }))).flat(), { x, y: 1.85, w: 5.45, h: 4.4, fontSize: 14, margin: 0, valign: "top", isTextBox: true });
  card(s, 0.6, 1.65, 6.0, 4.75); card(s, 6.8, 1.65, 5.9, 4.75);
  list(left, 0.85); list(right, 7.05);
}
//@END

//@COUPLE
// The generic client
{
  const s = slide("Putting them back together  \u00B7  The generic client", "ARTIE is to APIs what a browser is to web pages", false,
    "The browser is coupled to HTML and HTTP, and to nothing about any site: not account numbers, not URL layouts, not which buttons exist today. That's why a bank can change its forms tonight and nobody updates their browser. ARTIE is coupled the same way, to one media type, HAL with HEART's schema forms, and HTTP. Zero domain code: no client certificates, no pull requests, no merge rules. Each answer carries its controls. The next slides preview what that looks like on an API you know, AWS API Gateway.");
  const row = (y, client, sub, speaks, mt, targets, dark, ghosts) => {
    card(s, 0.6, y, 2.6, 1.6, dark ? C.text1 : C.accent1);
    T(s, client, { x: 0.8, y: y + 0.25, w: 2.3, h: 0.5, fontSize: 22, bold: true, fontFace: "Cambria", color: dark ? GOLD : C.background1 });
    T(s, sub, { x: 0.8, y: y + 0.85, w: 2.3, h: 0.6, fontSize: 13, color: C.background1 });
    arrow(s, 3.35, y + 0.64);
    card(s, 3.9, y, 3.6, 1.6, PALE);
    T(s, speaks, { x: 4.1, y: y + 0.15, w: 3.2, h: 0.3, fontSize: 11, color: SAGE, bold: true });
    T(s, mt, { x: 4.1, y: y + 0.42, w: 3.2, h: 0.6, fontSize: 14, bold: true, fontFace: "Courier New", color: INK });
    T(s, ghosts, { x: 4.1, y: y + 1.0, w: 3.3, h: 0.55, fontSize: 10, fontFace: "Courier New", color: "AEBFB5" });
    arrow(s, 7.65, y + 0.64);
    targets.forEach((t, i) => {
      card(s, 8.2 + (i % 2) * 2.3, y + Math.floor(i / 2) * 0.82, 2.2, 0.72, i === targets.length - 1 ? "FFFFFF" : PALE);
      T(s, t, { x: 8.3 + (i % 2) * 2.3, y: y + Math.floor(i / 2) * 0.82, w: 2.0, h: 0.72, fontSize: 13, valign: "middle", italic: i === targets.length - 1, color: i === targets.length - 1 ? SAGE : INK });
    });
  };
  row(1.65, "Web browser", "one generic client, for every site", "SPEAKS, AND IS COUPLED TO", "text/html\n+ HTTP", ["An online bank", "A wiki", "A shop", "Any site not yet built"], true, "image/jpeg  image/png  text/css\ntext/javascript  image/svg+xml  font/woff2");
  row(3.65, "ARTIE", "one generic client, for every API", "SPEAKS, AND IS COUPLED TO", "application/hal+json\n+ HEART profile + HTTP", ["AWS API Gateway (preview next)", "GitHub", "GitLab", "Any API not yet built"], false, "application/schema+json\napplication/problem+json  application/yaml");
  T(s, [{ text: "Coupled to: ", options: { bold: true, color: GREEN } }, { text: "the media type and the protocol.   " }, { text: "Not coupled to: ", options: { bold: true, color: RUST } }, { text: "certificates, pull requests, merge rules, URL layouts, or which acts exist today. Zero domain code." }], { x: 0.6, y: 5.55, w: 12.1, h: 0.8, fontSize: 15 });
  src(s, [["Fielding, dissertation, ch. 5", "https://ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm"], ["HAL Schema Forms", "https://github.com/jbadeau/hal-schema-forms"], ["artie (repository)", "https://github.com/JogoShugh/heart-rise-artie"]]);
}
//@END

//@ZTA
// Why this talk: from token maxing to tokenomics
{
  const ZTA = "https://www.wearedevelopers.com/videos/100533-zta-zero-token-architecture";
  const s = slide("Why this talk", "From token maxing to tokenomics", false,
    "Start with Kelsey Hightower. He opened his talk ZTA, Zero Token Architecture, at the WeAreDevelopers World Congress North America in September 2026 by telling the room they were having way too much fun burning way too many tokens. His point: agents that run inference on every call burn tokens and work against zero trust. Infer once, then run the result as reliable, deterministic code. That lands at a moment when the industry is turning. Companies ranked engineers by tokens burned; Meta's internal leaderboard covered more than 85,000 employees. Per-token prices for GPT-4-class models fell about 98% since late 2022, yet enterprise AI bills went up, because volume went up. J.R. Storment of the FinOps Foundation says the conversation moved from tokenmaxxing and go fast to guardrails and control. Ashish Bhatia puts it in two phases: the first was enablement, the next is governance. Token maxing re-learns the API on every call. Tokenomics learns it once and keeps something that stays true. So the question for the rest of the talk: what keeps it true?");
  card(s, 0.6, 1.65, 6.3, 4.75, C.text1);
  T(s, "“You are all having way too much fun burning way too many tokens”", { x: 0.95, y: 1.95, w: 5.6, h: 1.9, fontSize: 26, fontFace: "Cambria", italic: true, color: C.background1 });
  T(s, [{ text: "Kelsey Hightower", options: { bold: true, color: GOLD, hyperlink: { url: ZTA } } }, { text: ", opening ", options: { color: C.background1 } }, { text: "ZTA: Zero Token Architecture", options: { bold: true, color: GOLD, hyperlink: { url: ZTA } } }, { text: ", WeAreDevelopers World Congress North America, 26 Sep 2026", options: { color: C.background1 } }], { x: 0.95, y: 3.35, w: 5.6, h: 0.8, fontSize: 14 });
  T(s, "Agents that run inference on every call burn tokens and work against zero trust. Infer once, then run the result as reliable, deterministic code.", { x: 0.95, y: 4.5, w: 5.6, h: 1.2, fontSize: 16, color: C.accent6 });
  const row = (y, head, body) => {
    T(s, head, { x: 7.3, y, w: 5.4, h: 0.35, fontSize: 13, bold: true, color: RUST, charSpacing: 1 });
    T(s, body, { x: 7.3, y: y + 0.38, w: 5.4, h: 0.95, fontSize: 15 });
  };
  row(1.7, "TOKEN MAXING", "Companies ranked engineers by tokens burned. Meta’s internal leaderboard covered 85,000+ employees.");
  row(2.95, "THE BILL", "Per-token prices for GPT-4-class models fell about 98% since late 2022. Enterprise AI bills rose anyway: volume.");
  row(4.2, "THE TURN", "The FinOps Foundation’s J.R. Storment: from tokenmaxxing and “go fast” to guardrails and control. Ashish Bhatia: enablement first, governance next.");
  T(s, "Token maxing re-learns the API on every call. Tokenomics learns it once and keeps something that stays true. What keeps it true?", { x: 7.3, y: 5.55, w: 5.4, h: 0.8, fontSize: 17, bold: true, fontFace: "Cambria", color: GREEN });
  src(s, [["Hightower: ZTA, Zero Token Architecture (talk)", ZTA], ["Cranney: Infer Once, Run Everywhere", "https://www.wearedevelopers.com/magazine/783-infer-once-run-everywhere-zero-token-architecture"], ["Bhatia: From Tokenmaxing to Tokenomics", "https://ashbhatia.substack.com/p/from-tokenmaxing-to-tokenomics"], ["The Next Web: Token prices fell 98%", "https://thenextweb.com/news/token-prices-fell-98-enterprise-ai-bills-tripled-now-the-industry-wants-a-standards-body-to-explain-why"]]);
}
//@END

//@BRIDGE
// Infer once. Then what?
{
  const RF = "https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven";
  const s = slide("Infer once. Then what?", "When to think, and how “once” stays true", false,
    "Infer once, run everywhere. But everywhere includes tomorrow, after the API changed. A script frozen against URLs and docs it learned out of band either breaks, or pays tokens to re-learn. Fielding wrote the answer down in 2008: enter with nothing but a bookmark and media types the client already understands, and from then on take every next step from the choices the server puts in the answer. Then a kept replay only has to check that the act it wants is still offered. That check costs no tokens. The model comes back only when the controls really changed, to repair. Hightower tells you to infer once; Fielding tells you how once stays true.");
  card(s, 0.6, 1.65, 4.6, 4.75);
  const step = (y, head, body, color) => { T(s, head, { x: 0.9, y, w: 4.1, h: 0.35, fontSize: 13, bold: true, color, charSpacing: 1 }); T(s, body, { x: 0.9, y: y + 0.38, w: 4.1, h: 0.95, fontSize: 15 }); };
  step(1.9, "HIGHTOWER: WHEN TO THINK", "Once. Infer the task, then run it as plain, deterministic code. Zero tokens per run.", GREEN);
  step(3.25, "THE GAP", "“Everywhere” includes tomorrow. A script frozen against out-of-band URLs breaks, or pays tokens to re-learn.", RUST);
  step(4.6, "FIELDING: WHY IT STAYS TRUE", "Every answer carries its controls. The replay checks its act is still offered. That check is free.", GREEN);
  s.addShape(pres.shapes.RECTANGLE, { x: 5.5, y: 1.65, w: 7.2, h: 4.75, fill: { color: C.text1 }, line: { color: C.text1 } });
  T(s, "“A REST API should be entered with no prior knowledge beyond the initial URI (bookmark) and set of standardized media types that are appropriate for the intended audience (i.e., expected to be understood by any client that might use the API). From that point on, all application state transitions must be driven by client selection of server-provided choices that are present in the received representations or implied by the user’s manipulation of those representations. The transitions may be determined (or limited by) the client’s knowledge of media types and resource communication mechanisms, both of which may be improved on-the-fly (e.g., code-on-demand). [Failure here implies that out-of-band information is driving interaction instead of hypertext.]”", { x: 5.8, y: 1.85, w: 6.6, h: 3.85, fontSize: 14.5, fontFace: "Cambria", italic: true, color: C.background1 });
  T(s, [{ text: "Roy T. Fielding", options: { bold: true, color: GOLD, hyperlink: { url: RF } } }, { text: ", “REST APIs must be hypertext-driven”, 20 Oct 2008", options: { color: C.accent6 } }], { x: 5.8, y: 5.8, w: 6.6, h: 0.4, fontSize: 13 });
  src(s, [["Fielding: REST APIs must be hypertext-driven (2008)", RF], ["Hightower: ZTA, Zero Token Architecture (talk)", "https://www.wearedevelopers.com/videos/100533-zta-zero-token-architecture"]]);
}
//@END
