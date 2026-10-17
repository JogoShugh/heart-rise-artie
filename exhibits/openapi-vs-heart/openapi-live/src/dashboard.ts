// OpenAPI at its best: a garden dashboard with no generated code. It reads the
// garden's OpenAPI description while it runs (revalidated by its ETag on every
// render) and builds the page from it.
//
// What it binds, its "bookmark": the garden's URL, the description's URL
// (/openapi.json), and the operation getBed, which shows a bed. Everything
// else comes from the description at run time: the bed's members and their
// titles, a button for every POST operation under getBed's path that is not
// deprecated, each button's title (the operation's summary), and its fields
// (the request body's schema, with its defaults, examples and limits).
//
// What it cannot know: which operations the bed's current state allows. The
// description says that only in prose ("Only allowed when status is
// PLANTED"), so every operation is shown in every state, and the garden
// refuses the ones it does not allow.
import http from "node:http";
import { readFileSync } from "node:fs";

const port = Number(process.env.PORT ?? 3004);
const garden = process.env.GARDEN_URL ?? "http://localhost:8080";
const descriptionUrl = process.env.GARDEN_OPENAPI ?? `${garden}/openapi.json`;
const auth: Record<string, string> = process.env.GARDEN_TOKEN ? { Authorization: `Bearer ${process.env.GARDEN_TOKEN}` } : {};
const LOOKUP = "getBed";

type Json = any; // eslint-disable-line @typescript-eslint/no-explicit-any
type Operation = { id: string; method: string; path: string; op: Json };

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

async function call(method: string, path: string, body?: unknown): Promise<{ status: number; json: Json }> {
    const answer = await fetch(garden + path, {
        method: method.toUpperCase(),
        headers: { ...auth, Accept: "application/json", ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    calls.push({ method: method.toUpperCase(), path, status: answer.status });
    const text = await answer.text();
    return { status: answer.status, json: text ? JSON.parse(text) : null };
}

/** The bed, by the lookup operation the dashboard was given. */
async function readBed(doc: Json, id: number): Promise<{ bed: Json; lookup: Operation }> {
    const lookup = operations(doc).find((o) => o.id === LOOKUP);
    if (!lookup) throw new Error(`the description has no operation ${LOOKUP}`);
    const read = await call("get", fill(lookup.path, { id }));
    if (read.status !== 200) throw new Error(`the garden answered ${read.status}`);
    return { bed: read.json, lookup };
}

/** The operations a bed could ever be asked: POST under the lookup's path, not deprecated. */
function actsOn(doc: Json, lookup: Operation): Operation[] {
    return operations(doc).filter((o) => o.method === "post" && o.path.startsWith(`${lookup.path}/`) && !o.op.deprecated);
}

function bodySchema(doc: Json, op: Json): Json {
    return deref(doc, deref(doc, op.requestBody)?.content?.["application/json"]?.schema) ?? { properties: {} };
}

function card(doc: Json, bed: Json, lookup: Operation, message = "", refused = false): string {
    const schema = deref(doc, deref(doc, lookup.op.responses?.["200"])?.content?.["application/json"]?.schema) ?? {};
    const properties: Json = schema.properties ?? {};
    const members = Object.entries(bed)
        .filter(([name]) => !properties[name]?.deprecated)
        .map(([name, value]) => `<div><span class="name">${esc(properties[name]?.title ?? name)}</span>` +
            `<span class="value" data-member="${esc(name)}">${esc(value === null ? "none" : String(value))}</span></div>`)
        .join("");
    const forms = actsOn(doc, lookup).map((o) => form(doc, bed, o)).join("");
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
    const { bed, lookup } = await readBed(doc, id);
    const o = actsOn(doc, lookup).find((x) => x.id === operationId);
    if (!o) return card(doc, bed, lookup, `The description no longer has ${operationId}.`, true);
    const answer = await call("post", fill(o.path, bed), bodyFrom(doc, o.op, form));
    if (answer.status < 300) return card(doc, answer.json, lookup);
    const detail = answer.json?.detail ?? answer.json?.title ?? "";
    return card(doc, (await readBed(doc, id)).bed, lookup, `The garden refused it (${answer.status}): ${detail}`, true);
}

function page(body: string): string {
    return `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Garden dashboard, live from OpenAPI</title><link rel="stylesheet" href="/dashboard.css"><script src="/htmx.min.js"></script></head>
<body><header class="topbar">${mark}<h1>Garden dashboard</h1><span class="version">reads OpenAPI live</span>
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
        const { bed, lookup } = await readBed(doc, id);
        const html = card(doc, bed, lookup);
        return send(200, "text/html", match[2] === "card" ? html : page(html));
    } catch (e) {
        return send(500, "text/plain", `The dashboard failed: ${e}`);
    }
}).listen(port, () => console.log(`Garden dashboard (live OpenAPI) on http://localhost:${port}/beds/2`));
