
// Mockup: AWS's client certificate as HEART, L0, L1, L2
const AWSDOC = ["AWS: GetClientCertificates", "https://docs.aws.amazon.com/apigateway/latest/api/API_GetClientCertificates.html"];
const AWSUPD = ["UpdateClientCertificate", "https://docs.aws.amazon.com/apigateway/latest/api/API_UpdateClientCertificate.html"];
const AWSDEL = ["DeleteClientCertificate", "https://docs.aws.amazon.com/apigateway/latest/api/API_DeleteClientCertificate.html"];
const AWSGEN = ["GenerateClientCertificate", "https://docs.aws.amazon.com/apigateway/latest/api/API_GenerateClientCertificate.html"];
const AWSPATCH = ["PatchOperation", "https://docs.aws.amazon.com/apigateway/latest/api/API_PatchOperation.html"];
function tag(s) { pill(s, 10.3, 0.28, 2.4, "MOCKUP", RUST); }
function ladder(s, on, note) {
  ["Today", "L0", "L1", "L2"].forEach((t, i) => pill(s, 0.6 + i * 1.05, 6.18, 0.95, t, i === on ? GREEN : C.accent6, i === on ? C.background1 : INK, 0.3));
  if (note) T(s, note, { x: 4.95, y: 6.16, w: 7.75, h: 0.35, fontSize: 13, italic: true, color: SAGE, valign: "middle" });
}
// YAML lines: "#" comment, "!" key line in gold, "+" green (what HEART adds), else plain
const Y = (text) => text.replace(/^\n/, "").split("\n").map((l) => {
  const m = /^(\s*)([#!+])\s?(.*)$/.exec(l);
  if (!m) return ["p", l];
  return [{ "#": "c", "!": "k", "+": "g" }[m[2]], m[1] + (m[2] === "#" ? "# " : "") + m[3]];
});
const PY = 1.5, PH = 4.6;

// M1. Today
{
  const s = slide("A preview of HEART  ·  1 of 5  ·  Today", "AWS already names the acts. Only the forms are missing", false,
    "AWS's real sample response, written as YAML, all of it but the certificate's PEM body. The relations are typed and per item: delete and update sit on the certificate, generate on the list. What a client can't learn from it: the method, the fields, the limits. Those are on three other docs pages, linked by the curie. The next four slides are a mockup: the same resources as HEART would answer, at each rung.");
  tag(s);
  code(s, 0.6, PY, 4.1, PH, Y(`
# GET /clientcertificates  (AWS's sample)
!_links:
  curies:
    name: clientcertificate
    href: https://docs.aws.amazon.com/
      apigateway/latest/developerguide/
      restapi-clientcertificate-{rel}.html
    templated: true
  self:
    href: /clientcertificates
  clientcertificate:by-id:
    href: /clientcertificates/
      {clientcertificate_id}
    templated: true
+  clientcertificate:generate:
+    href: /clientcertificates
  item:
    href: /clientcertificates/xmbiqp`), 10);
  code(s, 4.85, PY, 4.0, PH, Y(`
!_embedded:
  item:
    _links:
      self:
        href: /clientcertificates/xmbiqp
+      clientcertificate:delete:
+        href: /clientcertificates/xmbiqp
+      clientcertificate:update:
+        href: /clientcertificates/xmbiqp
    clientCertificateId: xmbiqp
    createdDate: 2015-12-08T18:02:16Z
    description: test-client-cert-2
    expirationDate: 2016-12-07T18:02:16Z
    pemEncodedCertificate: |
      -----BEGIN CERTIFICATE-----
      MIIC6DC...XuHVdZ5r27XRRXEjg==
      -----END CERTIFICATE-----`), 10);
  T(s, "Still looked up elsewhere", { x: 9.05, y: PY, w: 3.65, h: 0.45, fontSize: 16, bold: true, fontFace: "Cambria", color: RUST });
  const hd = (t) => ({ text: t, options: { bold: true, color: C.background1, fill: { color: C.text1 } } });
  s.addTable([
    [hd("Relation"), hd("Method"), hd("Body")],
    ["generate", "POST", "description, tags"],
    ["update", "PATCH", "patchOperations"],
    ["delete", "DELETE", "none"],
  ], { x: 9.05, y: 2.05, w: 3.65, colW: [1.05, 0.85, 1.75], fontSize: 12, color: INK, border: { type: "solid", pt: 0.5, color: "C9D8CE" }, rowH: 0.4, valign: "middle" });
  T(s, "Three more docs pages, read by a developer and coded into a client. That is the gap HEART closes, one rung at a time.", { x: 9.05, y: 3.85, w: 3.65, h: 1.6, fontSize: 14 });
  ladder(s, 0, "Green: the relations AWS already offers.");
  src(s, [AWSDOC, AWSGEN, AWSUPD, AWSDEL]);
}

// M2. L0, the certificate
{
  const s = slide("A preview of HEART  ·  2 of 5  ·  HEART L0", "L0: the certificate, with its forms inline", false,
    "Mockup. The certificate as a HEART resource at L0: AWS's state as is, plus each act as a form: target, method, content type and its JSON Schema, all in one answer. The relation names stay AWS's. The ETag lets an act carry If-Match, so a stale view gets 412 rather than a lost update. The update schema follows AWS's PatchOperation: op, path, value, from.");
  tag(s);
  code(s, 0.6, PY, 5.0, PH, Y(`
# GET /clientcertificates/xmbiqp
# Accept: application/hal+json
# Accept-Profile: <https://w3id.org/heart-rise/hal-schema-forms>
# 200 OK
# ETag: "seq-1.hsf.L0"
# Cache-Control: no-cache
# Link: <https://w3id.org/heart-rise/hal-schema-forms>;
#   rel="profile"
clientCertificateId: xmbiqp
createdDate: 2015-12-08T18:02:16Z
description: test-client-cert-2
expirationDate: 2016-12-07T18:02:16Z
pemEncodedCertificate: |
  -----BEGIN CERTIFICATE-----
  MIIC6DC...XuHVdZ5r27XRRXEjg==
  -----END CERTIFICATE-----
!_links:
  self:
    href: /clientcertificates/xmbiqp
  curies:
    - name: clientcertificate
      href: /profiles/clientcertificate#{rel}
      templated: true`), 10);
  code(s, 5.75, PY, 6.95, PH, Y(`
+_forms:
+  clientcertificate:update:
    title: Update
    _links:
      target:
        href: /clientcertificates/xmbiqp
    method: PATCH
    contentType: application/json
    schema:
      type: object
      required: [patchOperations]
      properties:
        patchOperations:
          type: array
          items:
            type: object
            properties:
              op:
                enum: [add, remove, replace, move, copy, test]
              path: {type: string, examples: [/description]}
              value: {type: string}
              from: {type: string}
+  clientcertificate:delete:
    title: Delete
    _links:
      target:
        href: /clientcertificates/xmbiqp
    method: DELETE`), 9);
  ladder(s, 1, "Everything in one answer: correct, and repeated on every read.");
  src(s, [AWSUPD, AWSPATCH, AWSDEL, ["HAL Schema Forms", "https://github.com/jbadeau/hal-schema-forms"]]);
}

// M3. L0, the list
{
  const s = slide("A preview of HEART  ·  3 of 5  ·  HEART L0", "L0: the list offers generate, with what it needs", false,
    "Mockup. The list of certificates at L0. generate becomes a form: POST, JSON, and the two fields AWS's GenerateClientCertificate takes, description and tags. by-id becomes a search-like form whose one field fills the template. Each certificate is embedded with its own forms, as on the previous slide.");
  tag(s);
  code(s, 0.6, PY, 5.6, PH, Y(`
# GET /clientcertificates
# 200 OK, ETag: "seq-7.hsf.L0", Cache-Control: no-cache
!_links:
  self:
    href: /clientcertificates
  curies:
    - name: clientcertificate
      href: /profiles/clientcertificate#{rel}
      templated: true
!_embedded:
  item:
    - clientCertificateId: xmbiqp
      description: test-client-cert-2
      expirationDate: 2016-12-07T18:02:16Z
      _links:
        self:
          href: /clientcertificates/xmbiqp
      _forms:
        clientcertificate:update: {…}
        clientcertificate:delete: {…}`), 10);
  code(s, 6.35, PY, 6.35, PH, Y(`
+_forms:
+  clientcertificate:generate:
    title: Generate a client certificate
    _links:
      target:
        href: /clientcertificates
    method: POST
    contentType: application/json
    schema:
      type: object
      properties:
        description:
          type: string
        tags:
          type: object
          additionalProperties: {type: string}
+  clientcertificate:by-id:
    title: Find a certificate by its id
    _links:
      target:
        href: /clientcertificates/{clientcertificate_id}
        templated: true
    method: GET
    schema:
      type: object
      required: [clientcertificate_id]
      properties:
        clientcertificate_id: {type: string}`), 9);
  ladder(s, 1, "The list carries its own acts, and each item carries its own.");
  src(s, [AWSGEN, AWSDOC, ["HAL Schema Forms", "https://github.com/jbadeau/hal-schema-forms"]]);
}

// M4. L1
{
  const s = slide("A preview of HEART  ·  4 of 5  ·  HEART L1", "L1: the forms become one shared, cached set", false,
    "Mockup. With Prefer: forms=link, the certificate links its form set instead of inlining it. Every certificate links the same set, at a content address with an integrity hash, cached for a year and immutable. The target becomes a template the client fills from the certificate's own state, by the pointer. The certificate itself is still revalidated by its ETag; a state seen before costs one 304.");
  tag(s);
  code(s, 0.6, PY, 5.3, PH, Y(`
# GET /clientcertificates/xmbiqp
# Prefer: forms=link
# 200 OK
# ETag: "seq-1.hsf.L1"
# Cache-Control: no-cache
# Preference-Applied: forms=link
clientCertificateId: xmbiqp
createdDate: 2015-12-08T18:02:16Z
description: test-client-cert-2
expirationDate: 2016-12-07T18:02:16Z
pemEncodedCertificate: |
  -----BEGIN CERTIFICATE----- …
!_links:
  self:
    href: /clientcertificates/xmbiqp
+  hr:forms:
+    href: /affordance-sets/{hash}
+    type: application/hal+json
+    profile: https://w3id.org/heart-rise/
+      hal-schema-forms
+    integrity: sha256-…`), 10);
  code(s, 6.05, PY, 6.65, PH, Y(`
# GET /affordance-sets/{hash}
# Cache-Control: public, max-age=31536000, immutable
!_forms:
  clientcertificate:update:
    title: Update
    _links:
      target:
        href: /clientcertificates/{id}
        templated: true
+    hr:templatePointers:
+      id: /clientCertificateId
    method: PATCH
    contentType: application/json
    hr:integrity: sha256-…
    schema:
      type: object
      required: [patchOperations]
      properties:
        patchOperations: {…as at L0…}
  clientcertificate:delete:
    title: Delete
    _links:
      target:
        href: /clientcertificates/{id}
        templated: true
    hr:templatePointers:
      id: /clientCertificateId
    method: DELETE`), 9);
  ladder(s, 2, "One set for every certificate: fetched once, then read from cache.");
  src(s, [["RFC 9111 HTTP Caching", "https://www.rfc-editor.org/rfc/rfc9111"], ["RFC 7240 Prefer", "https://www.rfc-editor.org/rfc/rfc7240"], AWSUPD]);
}

// M5. L2
{
  const s = slide("A preview of HEART  ·  5 of 5  ·  HEART L2", "L2: schemas linked too, and shared everywhere", false,
    "Mockup. With Prefer: forms=link, schema=link, each form in the set references its schema by $ref, at a content address with an integrity hash. One schema, say for a patch of the description, is shared across forms, states and resources. This is the rung the release desk ran on GitHub and GitLab: from the second release on, about 50 set and schema reads per release never reached the network.");
  tag(s);
  code(s, 0.6, PY, 6.0, PH, Y(`
# GET /affordance-sets/{hash}
# Prefer: forms=link, schema=link
# Cache-Control: public, max-age=31536000, immutable
!_forms:
  clientcertificate:update:
    title: Update
    _links:
      target:
        href: /clientcertificates/{id}
        templated: true
    hr:templatePointers:
      id: /clientCertificateId
    method: PATCH
    contentType: application/json
    hr:integrity: sha256-…
+    schema:
+      $ref: /schemas/{hash}
+      hr:integrity: sha256-…
  clientcertificate:delete:
    title: Delete
    _links:
      target:
        href: /clientcertificates/{id}
        templated: true
    hr:templatePointers:
      id: /clientCertificateId
    method: DELETE`), 9);
  code(s, 6.75, PY, 5.95, PH, Y(`
# GET /schemas/{hash}
# Content-Type: application/schema+json
# Cache-Control: public, max-age=31536000, immutable
$schema: https://json-schema.org/draft/2020-12/schema
type: object
required: [patchOperations]
properties:
  patchOperations:
    type: array
    items:
      type: object
      properties:
        op:
          enum: [add, remove, replace,
                 move, copy, test]
        path:
          type: string
          examples: [/description]
        value:
          type: string
        from:
          type: string`), 10);
  ladder(s, 3, "A second read: 304 for the certificate; set and schema from cache, no request.");
  src(s, [["RFC 9111 HTTP Caching", "https://www.rfc-editor.org/rfc/rfc9111"], ["JSON Schema 2020-12", "https://json-schema.org/draft/2020-12"], AWSPATCH]);
}

// Pivot
{
  const s = slide("From preview to proof", "Now for real: GitHub and GitLab", true,
    "AWS stays a mockup: nobody serves it as HEART today. Everything from here on ran for real. A HEART gateway in front of real GitHub and a local GitLab, one YAML connector per forge, the same scm: relations on both, ARTIE as the only client, and Claude called once, to find the path.");
  const steps = [["Gateway", "HEART in front of each forge\u2019s own API"], ["Connector", "one YAML file per forge"], ["scm: relations", "the same on GitHub and GitLab"], ["ARTIE", "one generic client; Claude once, then none"]];
  steps.forEach(([h, d], i) => {
    const x = 0.6 + i * 3.1;
    card(s, x, 2.3, 2.75, 2.2, i === 2 ? C.accent1 : C.text2);
    T(s, h, { x: x + 0.25, y: 2.5, w: 2.3, h: 0.5, fontSize: 20, bold: true, fontFace: "Cambria", color: i === 2 ? C.background1 : GOLD });
    T(s, d, { x: x + 0.25, y: 3.1, w: 2.3, h: 1.2, fontSize: 15, color: C.background1 });
    if (i < 3) T(s, "\u2192", { x: x + 2.78, y: 3.05, w: 0.3, h: 0.5, fontSize: 22, color: GOLD });
  });
  T(s, "The mockup showed the shape. The next slides show it running, with every request on the record.", { x: 0.6, y: 5.0, w: 12, h: 0.8, fontSize: 18, color: C.accent6 });
}
