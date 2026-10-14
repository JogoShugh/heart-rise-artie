// The garden team's dashboard, 1.1.0: rebuilt on Tuesday against garden-1.1.0.json,
// after the server shipped the frost. Built first on Monday against garden-1.0.0.json.
//
// It is what a typical team writes: a client generated from the OpenAPI description
// (src/generated, by OpenAPI Generator), and a small web page on top. Everything the
// page knows about beds was bound when it was built: the statuses (BedStatusEnum),
// which button each status gets (copied from the description's prose), and which
// generated call each button makes.
import http from "node:http";
import { readFileSync } from "node:fs";
import { BedsApi, Configuration, ResponseError, type Bed, BedStatusEnum } from "./generated";

const VERSION = "1.1.0";
const port = Number(process.env.PORT ?? 3001);
const garden = process.env.GARDEN_URL ?? "http://localhost:8080";

// Plain JSON, the representation the OpenAPI description documents.
const api = new BedsApi(new Configuration({
    basePath: garden,
    accessToken: process.env.GARDEN_TOKEN,
    headers: { Accept: "application/json" },
}));

// Every call the dashboard makes to the garden, with its status, for the regression suite.
const calls: { method: string; path: string; status: number }[] = [];

const STATUS_LABEL: Record<BedStatusEnum, string> = {
    EMPTY: "Empty",
    PLANTED: "Planted",
    READY_TO_HARVEST: "Ready to harvest",
    FROZEN: "Frozen",
};

type Button = { title: string; action: string; fields: string };

const WATER: Button = {
    title: "Water the bed", action: "water",
    fields: `<input name="milliliters" type="number" min="1" max="1000" value="250" aria-label="Millilitres">`,
};
const SOW: Button = {
    title: "Sow a seed", action: "sow",
    fields: `<input name="seedType" value="basil" aria-label="Seed"><input name="depthCm" type="number" min="1" max="10" value="2" aria-label="Depth">`,
};
const HARVEST: Button = { title: "Harvest the crop", action: "harvest", fields: "" };
const THAW: Button = { title: "Thaw the bed", action: "thaw", fields: "" };

// From the description: sow an empty bed, harvest a ready one, thaw a frozen one, water a planted one.
function buttonsFor(status: BedStatusEnum): Button[] {
    switch (status) {
        case BedStatusEnum.Empty:
            return [SOW];
        case BedStatusEnum.ReadyToHarvest:
            return [HARVEST];
        case BedStatusEnum.Frozen:
            return [THAW];
        default:
            return [WATER];
    }
}

async function act(id: number, action: string, form: URLSearchParams): Promise<Bed> {
    switch (action) {
        case "water":
            return logged("POST", `/beds/${id}/actions/water-cell`,
                () => api.waterCell({ id, waterCellRequest: { milliliters: Number(form.get("milliliters")) } }));
        case "sow":
            return logged("POST", `/beds/${id}/actions/sow-seed`, () => api.sowSeed({
                id, sowSeedRequest: { seedType: String(form.get("seedType")), depthCm: Number(form.get("depthCm")) },
            }));
        case "harvest":
            return logged("POST", `/beds/${id}/actions/harvest`, () => api.harvest({ id, body: {} }));
        case "thaw":
            return logged("POST", `/beds/${id}/actions/thaw`, () => api.thaw({ id, body: {} }));
        default:
            throw new Error(`no action ${action}`);
    }
}

async function logged<T>(method: string, path: string, call: () => Promise<T>): Promise<T> {
    try {
        const result = await call();
        calls.push({ method, path, status: 200 });
        return result;
    } catch (e) {
        if (e instanceof ResponseError) calls.push({ method, path, status: e.response.status });
        throw e;
    }
}

function card(bed: Bed, message = ""): string {
    const buttons = buttonsFor(bed.status).map((b) => `
      <form class="act" hx-post="/beds/${bed.id}/${b.action}" hx-target="closest .card" hx-swap="outerHTML">
        ${b.fields}<button type="submit">${b.title}</button>
      </form>`).join("");
    return `
    <section class="card" id="bed-${bed.id}" hx-get="/beds/${bed.id}/card" hx-trigger="every 2s" hx-swap="outerHTML">
      <h2>${bed.label}</h2>
      <p>Status: <span class="badge">${STATUS_LABEL[bed.status]}</span></p>
      <dl>
        <dt>Crop</dt><dd>${bed.cropName ?? "none"}</dd>
        <dt>Moisture</dt><dd class="moisture">${bed.moistureLevel}</dd>
        <dt>Growth</dt><dd>${bed.growthPoints}</dd>
      </dl>
      <div class="buttons">${buttons}</div>
      ${message ? `<p class="message">${message}</p>` : ""}
    </section>`;
}

function page(body: string): string {
    return `<!doctype html><html lang="en"><head><meta charset="utf-8">
<title>Garden dashboard ${VERSION}</title><script src="/htmx.min.js"></script></head>
<body><h1>Garden dashboard <small>${VERSION}</small></h1>${body}</body></html>`;
}

async function problem(e: unknown): Promise<string> {
    if (!(e instanceof ResponseError)) return String(e);
    const body = await e.response.json().catch(() => ({}));
    return `The garden refused it (${e.response.status}): ${body.detail ?? body.title ?? ""}`;
}

const htmx = readFileSync(require.resolve("htmx.org/dist/htmx.min.js"));

http.createServer(async (req, res) => {
    const url = new URL(req.url ?? "/", `http://localhost:${port}`);
    const match = url.pathname.match(/^\/beds\/(\d+)(?:\/(card|water|sow|harvest|thaw))?$/);
    const send = (status: number, type: string, body: string | Buffer) => {
        res.writeHead(status, { "Content-Type": type });
        res.end(body);
    };
    try {
        if (url.pathname === "/htmx.min.js") return send(200, "text/javascript", htmx);
        if (url.pathname === "/_calls") return send(200, "application/json", JSON.stringify(calls));
        if (!match) return send(404, "text/plain", "not found");
        const id = Number(match[1]);
        if (req.method === "POST") {
            const form = new URLSearchParams(await new Promise<string>((ok) => {
                let data = "";
                req.on("data", (chunk) => (data += chunk));
                req.on("end", () => ok(data));
            }));
            try {
                return send(200, "text/html", card(await act(id, match[2], form)));
            } catch (e) {
                const message = await problem(e);
                return send(200, "text/html", card(await api.getBed({ id }), message));
            }
        }
        const bed = await logged("GET", `/beds/${id}`, () => api.getBed({ id }));
        return send(200, "text/html", match[2] === "card" ? card(bed) : page(card(bed)));
    } catch (e) {
        return send(500, "text/plain", `The dashboard failed: ${e}`);
    }
}).listen(port, () => console.log(`Garden dashboard ${VERSION} on http://localhost:${port}/beds/2`));
