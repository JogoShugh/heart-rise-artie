// OpenAPI with one hypermedia step: the spec-reading dashboard (../openapi-live),
// plus the standing link. Each plain answer about a bed carries
//   Link: </affordance-sets/...>; rel="https://w3id.org/heart-rise/rels/forms"; integrity="sha256-..."
// naming the affordance set of the state it shows, and the description's
// x-heart-affordances extension says how to use it. This dashboard follows the
// link (each set is immutable, so it is fetched once and checked against its
// integrity), and keeps only the operations whose method and path template a
// form in the set names. Without the link, it shows every operation, as the
// spec-reading dashboard does.
//
// What it binds, its "bookmark": the garden's URL, the description's URL
// (/openapi.json), and the operation getBed, which shows a bed. Everything
// else comes from the description at run time: the bed's members and their
// titles, a button for every POST operation under getBed's path that is not
// deprecated, each button's title (the operation's summary), and its fields
// (the request body's schema, with its defaults, examples and limits).
//
// What the description alone cannot say, which operations the bed's current
// state allows, comes from the link.
import http from "node:http";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";

const port = Number(process.env.PORT ?? 3005);
const garden = process.env.GARDEN_URL ?? "http://localhost:8080";
const descriptionUrl = process.env.GARDEN_OPENAPI ?? `${garden}/openapi.json`;
const auth: Record<string, string> = process.env.GARDEN_TOKEN ? { Authorization: `Bearer ${process.env.GARDEN_TOKEN}` } : {};
const LOOKUP = "getBed";

type Json = any; // eslint-disable-line @typescript-eslint/no-explicit-any
type Operation = { id: string; method: string; path: string; op: Json };
type Allowed = { method: string; template: string }[] | null; // null: no link, so nothing is known

// Every call the dashboard makes to the garden, with its status, for the regression suite.
const calls: { method: string; path: string; status: number }[] = [];

// The description, kept with its ETag and revalidated on every use.
let held: { etag: string | null; doc: Json } | null = null;

async function description(): Promise<Json> {
    const headers: Record<string, string> = { ...auth, Accept: "application/json" };
    if (held?.etag) headers["If-None-Match"] = held.etag;
    const answer = await fetch(descriptionUrl, { headers });
    if (answer.status === 304 && held) return held.doc;
    if (!answer.ok) throw new Error(`the description answered ${answer.status}`);
    held = { etag: answer.headers.get("ETag"), doc: await answer.json() };
    return held.doc;
}

/** Follows a local $ref, as many times as it takes. */
function deref(doc: Json, node: Json): Json {
    while (node && typeof node.$ref === "string" && node.$ref.startsWith("#/")) {
        node = node.$ref.slice(2).split("/").reduce((at: Json, key: string) => at?.[key], doc);
    }
    return node;
}

function operations(doc: Json): Operation[] {
    return Object.entries(doc.paths ?? {}).flatMap(([path, item]: [string, Json]) =>
        ["get", "post", "put", "patch", "delete"].filter((m) => item[m])
            .map((method) => ({ id: item[method].operationId, method, path, op: item[method] })));
}

function fill(template: string, values: Record<string, unknown>): string {
    return template.replace(/\{([^}]+)\}/g, (_, name) => encodeURIComponent(String(values[name])));
}

async function call(method: string, path: string, body?: unknown): Promise<{ status: number; json: Json; link: string | null }> {
    const answer = await fetch(garden + path, {
        method: method.toUpperCase(),
        headers: { ...auth, Accept: "application/json", ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    calls.push({ method: method.toUpperCase(), path, status: answer.status });
    const text = await answer.text();
    return { status: answer.status, json: text ? JSON.parse(text) : null, link: answer.headers.get("Link") };
}

// Affordance sets by address: immutable, so each is fetched once.
const sets = new Map<string, Json>();

/** The acts the answer's state allows, from the set its Link names; null if it names none. */
async function allowed(doc: Json, link: string | null): Promise<Allowed> {
    const rel = doc["x-heart-affordances"]?.rel;
    const found = rel && link?.split(/,(?=\s*<)/).find((l) => l.includes(`rel="${rel}"`));
    if (!found) return null;
    const href = found.match(/<([^>]+)>/)![1];
    if (!sets.has(href)) {
        const answer = await fetch(garden + href, { headers: { ...auth, Accept: "application/hal+json" } });
        calls.push({ method: "GET", path: href, status: answer.status });
        const text = await answer.text();
        const integrity = found.match(/integrity="([^"]+)"/)?.[1];
        const digest = `sha256-${createHash("sha256").update(text).digest("base64")}`;
        if (!answer.ok || (integrity && integrity !== digest)) return null;
        sets.set(href, JSON.parse(text));
    }
    return Object.values(sets.get(href)._forms ?? {}).map((form: Json) => ({
        method: String(form.method ?? "POST").toLowerCase(),
        template: form._links?.target?.href,
    }));
}

/** A path template with its variable names dropped: `/beds/{id}/x` and `/beds/{bedId}/x` match. */
const shape = (template: string) => template.replace(/\{[^}]+\}/g, "{}");

/** The bed, by the lookup operation the dashboard was given. */
async function readBed(doc: Json, id: number): Promise<{ bed: Json; lookup: Operation; link: string | null }> {
    const lookup = operations(doc).find((o) => o.id === LOOKUP);
    if (!lookup) throw new Error(`the description has no operation ${LOOKUP}`);
    const read = await call("get", fill(lookup.path, { id }));
    if (read.status !== 200) throw new Error(`the garden answered ${read.status}`);
    return { bed: read.json, lookup, link: read.link };
}

/** The operations the bed allows now: POST under the lookup's path, not deprecated, and named by a form. */
function actsOn(doc: Json, lookup: Operation, now: Allowed): Operation[] {
    return operations(doc).filter((o) => o.method === "post" && o.path.startsWith(`${lookup.path}/`) && !o.op.deprecated)
        .filter((o) => now === null || now.some((a) => a.method === o.method && shape(a.template) === shape(o.path)));
}

function bodySchema(doc: Json, op: Json): Json {
    return deref(doc, deref(doc, op.requestBody)?.content?.["application/json"]?.schema) ?? { properties: {} };
}

function card(doc: Json, bed: Json, lookup: Operation, now: Allowed, message = "", refused = false): string {
    const schema = deref(doc, deref(doc, lookup.op.responses?.["200"])?.content?.["application/json"]?.schema) ?? {};
    const properties: Json = schema.properties ?? {};
    const members = Object.entries(bed)
        .filter(([name]) => !properties[name]?.deprecated)
        .map(([name, value]) => `<div><span class="name">${esc(properties[name]?.title ?? name)}</span>` +
            `<span class="value" data-member="${esc(name)}">${esc(value === null ? "none" : String(value))}</span></div>`)
        .join("");
    const forms = actsOn(doc, lookup, now).map((o) => form(doc, bed, o)).join("");
    return `
    <section class="card" hx-get="/beds/${bed.id}/card" hx-trigger="every 2s" hx-swap="outerHTML">
      <div class="card-head"><div><div class="kind">${esc(schema.title ?? lookup.path)}</div><h2>${esc(bed.label ?? bed.id)}</h2></div></div>
      <div class="facts">${members}</div>
      <div class="buttons">${forms}</div>
      ${message ? `<p class="message${refused ? " refused" : ""}">${esc(message)}</p>` : ""}
    </section>`;
}

function form(doc: Json, bed: Json, o: Operation): string {
    const schema = bodySchema(doc, o.op);
    const required: string[] = schema.required ?? [];
    const inputs = Object.entries(schema.properties ?? {})
        .map(([name, property]) => input(name, deref(doc, property), required.includes(name))).join("");
    return `
      <form class="act" hx-post="/beds/${bed.id}/act/${encodeURIComponent(o.id)}" hx-target="closest .card" hx-swap="outerHTML">
        ${inputs}<button type="submit">${esc(o.op.summary ?? o.id)}</button>
      </form>`;
}

/** A field and its label, together, so it's clear which act it belongs to. */
function field(label: string, control: string): string {
    return `<label class="field">${esc(label)}${control}</label>`;
}

function input(name: string, schema: Json, required: boolean): string {
    const label = schema.title ?? name;
    const preset = schema.default ?? schema.examples?.[0];
    let attrs = ` name="${esc(name)}" aria-label="${esc(label)}" placeholder="${esc(label)}"`;
    if (preset !== undefined) attrs += ` value="${esc(String(preset))}"`;
    if (required) attrs += " required";
    if (schema.minimum !== undefined) attrs += ` min="${schema.minimum}"`;
    if (schema.maximum !== undefined) attrs += ` max="${schema.maximum}"`;
    if (Array.isArray(schema.enum)) {
        // The preset first, as a person would see it chosen.
        const options = [...schema.enum].sort((a, b) => Number(b === preset) - Number(a === preset));
        return field(label, `<select${attrs}>${options.map((v) => `<option>${esc(String(v))}</option>`).join("")}</select>`);
    }
    const numeric = schema.type === "integer" || schema.type === "number";
    return field(label, `<input${numeric ? ` type="number"` : ""}${attrs}>`);
}

/** The submitted form as the JSON the operation's schema asks for. */
function bodyFrom(doc: Json, op: Json, form: URLSearchParams): Json {
    const schema = bodySchema(doc, op);
    const body: Json = {};
    for (const [name, raw] of Object.entries(schema.properties ?? {})) {
        const property = deref(doc, raw);
        const value = form.get(name);
        if (value === null || value === "") continue;
        const types = [property.type].flat();
        body[name] = types.includes("integer") || types.includes("number") ? Number(value) : value;
    }
    return body;
}

async function act(doc: Json, id: number, operationId: string, form: URLSearchParams): Promise<string> {
    const { bed, lookup, link } = await readBed(doc, id);
    const now = await allowed(doc, link);
    const o = actsOn(doc, lookup, now).find((x) => x.id === operationId);
    if (!o) return card(doc, bed, lookup, now, `The bed does not allow ${operationId} now.`, true);
    const answer = await call("post", fill(o.path, bed), bodyFrom(doc, o.op, form));
    if (answer.status < 300) return card(doc, answer.json, lookup, await allowed(doc, answer.link));
    const detail = answer.json?.detail ?? answer.json?.title ?? "";
    const again = await readBed(doc, id);
    return card(doc, again.bed, lookup, await allowed(doc, again.link), `The garden refused it (${answer.status}): ${detail}`, true);
}

function page(body: string): string {
    return `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Garden dashboard, OpenAPI with affordance links</title><link rel="stylesheet" href="/dashboard.css"><script src="/htmx.min.js"></script></head>
<body><header class="topbar">${mark}<h1>Garden dashboard</h1><span class="version">OpenAPI + affordance link</span>
<span class="live">Polling every 2s</span></header><main>${body}</main></body></html>`;
}

function esc(text: unknown): string {
    return String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

const htmx = readFileSync(require.resolve("htmx.org/dist/htmx.min.js"));
// The look all the dashboards share (../shared), so they differ only in how they bind.
const css = readFileSync(`${__dirname}/../../shared/dashboard.css`);
const mark = readFileSync(`${__dirname}/../../shared/mark.svg`, "utf8");

http.createServer(async (req, res) => {
    const url = new URL(req.url ?? "/", `http://localhost:${port}`);
    const match = url.pathname.match(/^\/beds\/(\d+)(?:\/(card)|\/act\/([^/]+))?$/);
    const send = (status: number, type: string, body: string | Buffer) => {
        res.writeHead(status, { "Content-Type": type });
        res.end(body);
    };
    try {
        if (url.pathname === "/htmx.min.js") return send(200, "text/javascript", htmx);
        if (url.pathname === "/dashboard.css") return send(200, "text/css", css);
        if (url.pathname === "/_calls") return send(200, "application/json", JSON.stringify(calls));
        if (!match) return send(404, "text/plain", "not found");
        const id = Number(match[1]);
        const doc = await description();
        if (req.method === "POST" && match[3]) {
            const form = new URLSearchParams(await new Promise<string>((ok) => {
                let data = "";
                req.on("data", (chunk) => (data += chunk));
                req.on("end", () => ok(data));
            }));
            return send(200, "text/html", await act(doc, id, decodeURIComponent(match[3]), form));
        }
        const { bed, lookup, link } = await readBed(doc, id);
        const html = card(doc, bed, lookup, await allowed(doc, link));
        return send(200, "text/html", match[2] === "card" ? html : page(html));
    } catch (e) {
        return send(500, "text/plain", `The dashboard failed: ${e}`);
    }
}).listen(port, () => console.log(`Garden dashboard (OpenAPI + affordance links) on http://localhost:${port}/beds/2`));
