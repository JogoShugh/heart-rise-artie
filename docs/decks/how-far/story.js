//@TAWS
// Callback: what if AWS's HAL became HEART?
{
  const s = slide("A callback  ·  Amazon API Gateway", "What if AWS\u2019s HAL became HEART?", true,
    "A few slides back: Amazon API Gateway already answers in HAL, with typed relations that name its acts. What if we took exactly that, and turned it into the HEART media type, which is built on HAL Schema Forms? Same resources, same relation names. Add the forms, then make them cheap. The next five slides are a mockup, rung by rung.");
  const steps = [["HAL", "AWS today: links and typed relations that name the acts", GREEN], ["+ HAL Schema Forms", "each act as a form: method, fields, JSON Schema", "2C5E45"], ["= HEART", "only the forms valid now, guarded, cached by rung", GOLD]];
  steps.forEach(([h, d, c], i) => {
    const x = 0.6 + i * 4.1;
    card(s, x, 2.2, 3.7, 2.3, c === GOLD ? "2C5E45" : c);
    T(s, h, { x: x + 0.25, y: 2.4, w: 3.2, h: 0.55, fontSize: 24, bold: true, fontFace: "Cambria", color: c === GOLD ? GOLD : C.background1 });
    T(s, d, { x: x + 0.25, y: 3.1, w: 3.2, h: 1.2, fontSize: 15, color: C.background1 });
    if (i < 2) T(s, "→", { x: x + 3.72, y: 3.0, w: 0.4, h: 0.5, fontSize: 24, color: GOLD });
  });
  T(s, "Same resources. Same relation names. Nothing for AWS to give up.", { x: 0.6, y: 4.95, w: 12, h: 0.5, fontSize: 18, color: C.accent6 });
  src(s, [["AWS: GetClientCertificates", "https://docs.aws.amazon.com/apigateway/latest/api/API_GetClientCertificates.html"], ["HAL Schema Forms", "https://github.com/jbadeau/hal-schema-forms"]], true);
}
//@END

//@P1
// From preview to two real services
{
  const s = slide("From preview to proof", "Now two real services, built to agree on nothing", true,
    "AWS stays a mockup: nobody serves it as HEART today. From here on everything ran for real, with every request recorded. Two real services, from two companies, that do mostly the same job and agree on almost nothing: GitHub, in the cloud, and GitLab, running on this machine.");
  T(s, "AWS stays a mockup. From here on, everything ran for real, and every request is on the record.", { x: 0.6, y: 1.9, w: 11.5, h: 1.0, fontSize: 22, color: C.background1 });
  const two = (x, h, d) => { card(s, x, 3.2, 5.8, 1.9, "2C5E45"); T(s, h, { x: x + 0.3, y: 3.4, w: 5.2, h: 0.55, fontSize: 26, bold: true, fontFace: "Cambria", color: GOLD }); T(s, d, { x: x + 0.3, y: 4.05, w: 5.2, h: 0.9, fontSize: 16, color: C.background1 }); };
  two(0.6, "GitHub", "the real one, in the cloud: JogoShugh/heart-github-test");
  two(6.9, "GitLab", "Community Edition, in a container on this machine");
  src(s, [["github.com/JogoShugh/heart-github-test", "https://github.com/JogoShugh/heart-github-test"], ["GitLab CE", "https://docs.gitlab.com/install/docker/"]], true);
}
//@END

//@G1
// Two implementations of mostly the same thing
{
  const s = slide("Two forges", "Two implementations of mostly the same thing", false,
    "Each forge's own page, as a visitor sees it, for the same release: a change proposed, merged by squashing, then commented on. The same job, done by both. Different names for nearly everything: pull request or merge request, # or !, the base or the target branch, a comment or a note.");
  shot(s, "ngh504.png", 0.6, 1.6, 5.6, 5.6 * 800 / 1280, "GitHub: pull request #2337, merged.");
  shot(s, "ngl504.png", 7.1, 1.6, 5.6, 5.6 * 800 / 1280, "GitLab: merge request !504, merged.");
  T(s, "Same job: a change is proposed, merged by squashing, then commented on. Different words, numbers, fields and calls for nearly all of it.", { x: 0.6, y: 5.75, w: 12.1, h: 0.75, fontSize: 16 });
  src(s, [["Pull request #2337", "https://github.com/JogoShugh/heart-github-test/pull/2337"], ["GitHub REST: pulls", "https://docs.github.com/en/rest/pulls/pulls"], ["GitLab REST: merge requests", "https://docs.gitlab.com/api/merge_requests/"]]);
}
//@END

//@G2
// Can we make them interchangeable?
{
  const s = slide("The question", "Can we make them interchangeable?", false,
    "The callback. AWS already names its acts as typed relations: clientcertificate:update. The preview turned them into forms. Can we do the same for two forges that never agreed: one name per act, the same on both, so one client and one script work on either? The answer the next slides show: yes, with the scm: prefix, one YAML connector per forge, and HEART on top.");
  const hd = (t) => ({ text: t, options: { bold: true, color: C.background1, fill: { color: C.text1 } } });
  const m = (t) => ({ text: t, options: { fontFace: "Courier New", fontSize: 12 } });
  const r = (t) => ({ text: t, options: { fontFace: "Courier New", fontSize: 12, bold: true, color: GREEN } });
  s.addTable([
    [hd("The act"), hd("GitHub says"), hd("GitLab says"), hd("One typed relation?")],
    ["Read one", m("GET /repos/{o}/{r}/pulls/{n}"), m("GET /projects/{id}/merge_requests/{iid}"), r("scm:pulls-get")],
    ["Merge, squashed", m("PUT …/merge {merge_method: squash}"), m("PUT …/merge {squash: true}"), r("scm:merge")],
    ["Comment", m("POST /issues/{n}/comments"), m("POST …/merge_requests/{iid}/notes"), r("scm:comment")],
    ["Can it merge?", m("mergeable_state: clean"), m("detailed_merge_status: mergeable"), r("mergeable_state")],
  ], { x: 0.6, y: 1.6, w: 12.1, colW: [1.9, 3.9, 4.0, 2.3], fontSize: 13, color: INK, border: { type: "solid", pt: 0.5, color: "C9D8CE" }, rowH: 0.5, valign: "middle" });
  card(s, 0.6, 4.4, 12.1, 1.6, C.text1);
  T(s, [{ text: "Callback: ", options: { bold: true, color: GOLD } }, { text: "AWS already names its acts, clientcertificate:update, and our preview turned them into forms. Here, one name per act, the same on both forges: ", options: { color: C.background1 } }, { text: "scm:", options: { bold: true, color: GOLD, fontFace: "Courier New" } }, { text: " typed relations. One client, one script, either forge.", options: { color: C.background1 } }], { x: 0.9, y: 4.6, w: 11.5, h: 1.2, fontSize: 17 });
  src(s, [["GitHub REST: pulls", "https://docs.github.com/en/rest/pulls/pulls"], ["GitLab REST: merge requests", "https://docs.gitlab.com/api/merge_requests/"], ["GitLab REST: notes", "https://docs.gitlab.com/api/notes/"]]);
}
//@END

//@H1
// One UI from the common relations
{
  const s = slide("What typed relations buy  ·  text/html", "One plain UI, rendered from the shared relations", false,
    "The gateway serves each resource as text/html too. It's a plain, functional UI, rendered only from the data, the typed relations and the forms offered now. Nothing in the renderer knows GitHub or GitLab. Here, both merged: green buttons are the forms offered now, grey ones are not offered in this state. scm:comment is lit on both.");
  shot(s, "hgh504.png", 0.6, 1.6, 5.95, 5.95 * 760 / 1280, "GitHub #2337 through the gateway, as text/html.");
  shot(s, "hgl504.png", 6.75, 1.6, 5.95, 5.95 * 760 / 1280, "GitLab !504 through the gateway, as text/html.");
  T(s, [{ text: "Green: ", options: { bold: true, color: GREEN } }, { text: "offered now.  " }, { text: "Grey: ", options: { bold: true, color: SAGE } }, { text: "not in this state (merged). One renderer, no forge-specific code: it reads the media type, as a browser reads HTML." }], { x: 0.6, y: 5.75, w: 12.1, h: 0.8, fontSize: 16 });
  src(s, [["Report: Real GitHub, Local GitLab", "https://jogoshugh.github.io/heart-rise-artie/reports/merge-watch-real-github/"]]);
}
//@END

//@S1
{
  const s = slide("Building it back up  ·  Step 1 of 5  ·  HEART L0, simplified", "Only data: what most APIs send today", false,
    "Start where most APIs are. GitHub's pull request, as data only, shortened from about 40 fields. It says what the pull request is. It says nothing about what you can do with it. Those controls live out of band: docs pages, an SDK, a 13.5 MB OpenAPI file. Like AWS's three docs pages in the preview.");
  code(s, 0.6, 1.55, 6.4, 4.5, Y(`
# GET /repos/JogoShugh/heart-github-test/pulls/2325
# Accept: application/json
number: 2325
title: "Renewals: none during the audit"
body: Variant 504 of the Riverside Public Library release.
state: open
merged: false
draft: false
mergeable_state: clean
head:
  ref: library/pr-504
  sha: e266e3b53e0e04204c4f8eff6b14208ba53aafea
base:
  ref: library/main
# …and about 30 more fields`), 11);
  T(s, "And the controls?", { x: 7.3, y: 1.6, w: 5.4, h: 0.5, fontSize: 22, bold: true, fontFace: "Cambria", color: RUST });
  bullets(s, [
    "Can it be merged now? By which methods? With what fields?",
    "Can I comment? Reopen? Request a review?",
    "Answered elsewhere: docs pages, an SDK, a 13.5 MB OpenAPI file, read by a developer and coded into a client.",
  ], { x: 7.3, y: 2.25, w: 5.4, h: 2.8 });
  T(s, "The same gap as AWS’s three docs pages, in the preview.", { x: 7.3, y: 5.15, w: 5.4, h: 0.6, fontSize: 14, italic: true, color: SAGE });
  src(s, [["GitHub REST: get a pull request", "https://docs.github.com/en/rest/pulls/pulls#get-a-pull-request"], ["GitHub OpenAPI description", "https://github.com/github/rest-api-description"]]);
}
//@END

//@S2
{
  const s = slide("Building it back up  ·  Step 2 of 5  ·  HEART L0, simplified", "Links: it names itself and its vocabulary", false,
    "The gateway's answer adds HAL links: self, the scm: curie that says where the relation names are defined, the forge's own page as an alternate, and typed links to related resources. GitHub's own *_url members became these links and left the data.");
  code(s, 0.6, 1.55, 6.9, 4.5, Y(`
# GET http://gateway/repos/JogoShugh/heart-github-test/pulls/2325
# Accept: application/hal+json
number: 2325
title: "Renewals: none during the audit"
state: open
mergeable_state: clean
+_links:
+  self:
+    href: /repos/JogoShugh/heart-github-test/pulls/2325
+  curies:
+    - name: scm
+      href: /profiles/scm#{rel}
+      templated: true
+  alternate:
+    href: https://github.com/JogoShugh/heart-github-test/pull/2325
+    type: text/html
+  scm:commits:
+    href: /repos/JogoShugh/heart-github-test/pulls/2325/commits
+  scm:diff:
+    href: https://github.com/JogoShugh/heart-github-test/pull/2325.diff
+    type: text/x-diff`), 10);
  T(s, "Where it is, and what its words mean", { x: 7.8, y: 1.6, w: 4.9, h: 0.5, fontSize: 18, bold: true, fontFace: "Cambria", color: GREEN });
  bullets(s, [
    [{ text: "self: ", b: true }, { text: "its own address, handed over, never built." }],
    [{ text: "curies: ", b: true }, { text: "scm: names resolve to one shared vocabulary, for both forges." }],
    [{ text: "alternate: ", b: true }, { text: "the forge’s own page, for people." }],
    [{ text: "Typed links: ", b: true }, { text: "GitHub’s *_url members became links and left the data." }],
  ], { x: 7.8, y: 2.2, w: 4.9, h: 3.5 });
  src(s, [["HAL (Kelly)", "https://datatracker.ietf.org/doc/html/draft-kelly-json-hal-11"], ["RFC 8288 Web Linking", "https://www.rfc-editor.org/rfc/rfc8288"]]);
}
//@END

//@S3
{
  const s = slide("Building it back up  ·  Step 3 of 5  ·  HEART L0, simplified", "Forms: what you can do now, and how", false,
    "The open pull request offers seven forms. scm:merge in full: its target, method, content type and JSON Schema, from GitHub's own description, with the merge method as scm's input. scm:comment needs one field, body. These are real, from the L0 run on GitHub.");
  code(s, 0.6, 1.55, 6.3, 4.5, Y(`
!_forms:
  scm:close: {…}
+  scm:merge:
    title: Merge
    description: Merge the pull request into its base branch.
    _links:
      target:
        href: /repos/JogoShugh/heart-github-test/
              pulls/2325/actions/merge
    method: PUT
    contentType: application/json
    schema:
      type: object
      additionalProperties: false
      required: [method]
      properties:
        method:
          title: Merge method
          enum: [merge, squash, rebase]
        commit_title:
          type: string
        commit_message:
          type: string`), 10);
  code(s, 7.05, 1.55, 5.65, 4.5, Y(`
+  scm:comment:
    title: Comment
    _links:
      target:
        href: /repos/JogoShugh/heart-github-test/
              pulls/2325/actions/comment
    method: POST
    contentType: application/json
    schema:
      type: object
      required: [body]
      properties:
        body:
          type: string
          description: The contents of the comment.
  scm:label: {…}
  scm:edit: {…}
  scm:request-review: {…}
  scm:update-branch: {…}
# 7 forms, while open`), 10);
  src(s, [["HAL Schema Forms", "https://github.com/jbadeau/hal-schema-forms"], ["JSON Schema 2020-12", "https://json-schema.org/draft/2020-12"], ["GitHub REST: merge a pull request", "https://docs.github.com/en/rest/pulls/pulls#merge-a-pull-request"]]);
}
//@END

//@S4
{
  const s = slide("Building it back up  ·  Step 4 of 5  ·  HEART L0, simplified", "Guards, and why the other acts are absent", false,
    "The whole answer's headers: the profile, the ETag, no-cache, and the alternates the gateway also serves (HAL-FORMS, text/html, plain JSON). An act sends the ETag back as If-Match: if the pull request changed since it was read, the answer is 412, not a lost update. And the absent acts have reasons: reopen isn't offered because the pull request is already open, in the connector's own words.");
  code(s, 0.6, 1.55, 6.3, 2.75, Y(`
# 200 OK
Content-Type: application/hal+json
Content-Profile: <https://w3id.org/heart-rise/
                  hal-schema-forms>
ETag: "seq-0.hsf.L0.6042001a61e14760"
Cache-Control: no-cache
Vary: Accept, Prefer, Accept-Profile
Link: <…/pulls/2325>; rel="alternate";
        type="application/prs.hal-forms+json",
      <…/pulls/2325>; rel="alternate"; type="text/html"`), 10);
  code(s, 0.6, 4.45, 6.3, 1.6, Y(`
# the act carries what it read
PUT /repos/…/pulls/2325/actions/merge
+If-Match: "seq-0.hsf.L0.6042001a61e14760"
{method: squash}
# changed since? 412, not a lost update`), 10);
  code(s, 7.05, 1.55, 5.65, 4.5, Y(`
!_links:
  hr:unavailable:
    href: /unavailable/bf257b5ee24d7901
    integrity: sha256-vyV7XuJNeQF8\u2026
# GET /unavailable/bf257b5ee24d7901  (as served)
unavailable:
  - rel: /profiles/scm#reopen
    title: Reopen
+    reason: The pull request is already open.
# the reason is the connector's own words:
# github-scm/pull.reader.yaml
#   open:
#     absent:
#       reopen: The pull request is
#               already open.`), 10);
  src(s, [["RFC 9110: If-Match, 412", "https://www.rfc-editor.org/rfc/rfc9110#name-if-match"], ["artie (repository)", "https://github.com/JogoShugh/heart-rise-artie"]]);
}
//@END

//@S5
{
  const s = slide("Building it back up  ·  Step 5 of 5  ·  HEART L0, simplified", "After the merge: the server changed, the client didn’t", false,
    "The same pull request, read after scm:merge. The state changed, so the forms changed: three left, comment, label and edit. merge, close and reopen are gone, each with its reason. ARTIE needed no new code to know; it read what's offered. The ETag changed too, so any act still holding the old one gets 412.");
  code(s, 0.6, 1.55, 6.0, 4.5, Y(`
# GET …/pulls/2325, after scm:merge
+ETag: "seq-0.hsf.L0.ed4a2f1e1ab3f687"
number: 2325
state: closed
merged: true
mergeable_state: unknown
!_forms:
  scm:comment: {…}
  scm:label: {…}
  scm:edit: {…}
# gone: scm:merge, scm:close, scm:reopen,
#       scm:request-review, scm:update-branch`), 11);
  code(s, 6.75, 1.55, 5.95, 3.4, Y(`
# GET /unavailable/d0a0136ee8d4d2bb  (as served)
unavailable:
  - title: Close
    reason: The pull request is already closed.
  - title: Reopen
    reason: A merged pull request can't be reopened.
  - title: Merge
    reason: A closed pull request can't be merged;
            reopen it first.
  - title: Request a review
    reason: A closed pull request can't have reviews requested;
            reopen it first.
  - title: Update the branch
    reason: A closed pull request's branch isn't updated;
            reopen it first.
# each also has rel: /profiles/scm#close, #reopen, \u2026`), 10);
  T(s, "7 forms open, 3 merged. ARTIE needed no new code: it reads what’s offered, as a browser does.", { x: 6.75, y: 5.1, w: 5.95, h: 0.9, fontSize: 15, bold: true, color: GREEN });
  src(s, [["artie: github-scm/pull.reader.yaml", "https://github.com/JogoShugh/heart-rise-artie"], ["Fielding (2008)", "https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven"]]);
}
//@END

//@RELEASE
// The release, end to end, in screenshots
{
  const s = slide("The whole release  ·  1 of 2", "The release desk, and Jenkins, for real", false,
    "The release desk is a HEART service: a person, or ARTIE, asks it to release a pull request, and it runs the kept script. Its own text/html is shown here, after five releases on the GitHub test repository, run today. Jenkins is real, the official image, in a container on this Mac, building each merged commit and answering back through RISE when the build finishes.");
  shot(s, "desk.png", 0.85, 1.55, 5.3, 5.3 * 900 / 1280, "The release desk, as text/html: \u201CRelease a pull request\u201D is its act.");
  shot(s, "jenkins.png", 7.1, 1.55, 5.3, 5.3 * 900 / 1280, "Jenkins in a container: builds #33 to #37, one per release (#33 failed).");
  T(s, "Every party is a HEART service, read and acted on by the same client. Jenkins sits behind a thin HEART wrapper and answers back through RISE.", { x: 0.6, y: 5.85, w: 12.1, h: 0.7, fontSize: 15 });
  src(s, [["Jenkins (official image)", "https://hub.docker.com/r/jenkins/jenkins"], ["artie (repository)", "https://github.com/JogoShugh/heart-rise-artie"]]);
}
{
  const s = slide("The whole release  ·  2 of 2", "The build’s console, and Dev, promoted or not", false,
    "Jenkins' console for one build of the merged commit. Then the Dev environment, another HEART service: promoted when the smoke scenarios pass. For 504, scenario S3 failed, so Dev wasn't promoted and the script commented on the pull request instead, as the earlier slides showed on both forges.");
  shot(s, "jenkins-console.png", 0.85, 1.55, 5.3, 5.3 * 900 / 1280, "Build #35: the merged commit of #2341, tested, deployed, SUCCESS.");
  shot(s, "dev.png", 7.1, 1.55, 5.3, 5.3 * 900 / 1280, "Dev, as text/html: commit 6c030f1 of #2341, promoted.");
  T(s, "Smoke passed: promote. Smoke failed: comment on the pull request, “Not promoted to Dev”. One kept script, no model.", { x: 0.6, y: 5.85, w: 12.1, h: 0.7, fontSize: 15 });
  src(s, [["Jenkins (official image)", "https://hub.docker.com/r/jenkins/jenkins"], ["Pull request #2341", "https://github.com/JogoShugh/heart-github-test/pull/2341"]]);
}
//@END
