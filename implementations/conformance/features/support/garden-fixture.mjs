// The garden each scenario talks to, one of three:
//   no GARDEN_URL                     replays fixtures/garden/, offline. This is what the gate runs.
//   GARDEN_URL                        the live garden, as run.sh starts it.
//   GARDEN_URL with GARDEN_RECORD     the live garden through a recorder, as record.sh runs it.
// A replay is strict: each request must be the one recorded, in order, and
// every recorded exchange must be used. A credential is never recorded, only
// whether one was sent.
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import http from "node:http";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const fixtures = join(dirname(fileURLToPath(import.meta.url)), "../../fixtures/garden");
const fixtureFile = (scenario) => join(fixtures, `${scenario.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "")}.json`);
const dropped = new Set(["date", "connection", "keep-alive", "transfer-encoding", "set-cookie"]);

async function bodyOf(req) {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    return Buffer.concat(chunks).toString("utf8");
}

const requestOf = async (req) => ({
    method: req.method,
    url: req.url,
    authorized: req.headers.authorization !== undefined,
    ifMatch: req.headers["if-match"] ?? null,
    ifNoneMatch: req.headers["if-none-match"] ?? null,
    preferred: req.headers.prefer ?? null,
    body: await bodyOf(req),
});

async function listen(server) {
    await new Promise((ok) => server.listen(0, "127.0.0.1", ok));
    return `http://127.0.0.1:${server.address().port}/`;
}

async function replayGarden(scenario) {
    const exchanges = JSON.parse(readFileSync(fixtureFile(scenario), "utf8"));
    const misses = [];
    let next = 0;
    const server = http.createServer(async (req, res) => {
        const request = await requestOf(req);
        const expected = exchanges[next];
        if (JSON.stringify(request) !== JSON.stringify(expected?.request)) {
            misses.push(`exchange ${next}: got ${JSON.stringify(request)}, recorded ${JSON.stringify(expected?.request)}`);
            res.writeHead(500);
            return res.end("not the recorded request");
        }
        next += 1;
        res.writeHead(expected.response.status, expected.response.headers);
        res.end(expected.response.body);
    });
    const bookmark = await listen(server);
    return {
        bookmark,
        async close() {
            server.close();
            if (misses.length) throw new Error(misses.join("\n"));
            if (next !== exchanges.length) throw new Error(`${exchanges.length - next} recorded exchanges were never requested`);
        },
    };
}

async function recordingGarden(scenario, upstream) {
    const exchanges = [];
    const server = http.createServer(async (req, res) => {
        const request = await requestOf(req);
        const answer = await fetch(new URL(req.url, upstream), {
            method: req.method,
            headers: Object.fromEntries(Object.entries(req.headers).filter(([key]) => key !== "host" && key !== "connection")),
            body: request.body === "" ? undefined : request.body,
            redirect: "manual",
        });
        const body = Buffer.from(await answer.arrayBuffer()).toString("utf8");
        const headers = Object.fromEntries([...answer.headers].filter(([key]) => !dropped.has(key)));
        exchanges.push({ request, response: { status: answer.status, headers, body } });
        res.writeHead(answer.status, headers);
        res.end(body);
    });
    const bookmark = await listen(server);
    return {
        bookmark,
        async close() {
            server.close();
            mkdirSync(fixtures, { recursive: true });
            writeFileSync(fixtureFile(scenario), `${JSON.stringify(exchanges, null, 2)}\n`);
        },
    };
}

/** The garden for one scenario, at its seed state. */
export async function openGarden(scenario) {
    const live = process.env.GARDEN_URL;
    if (!live) return replayGarden(scenario);
    execFileSync(process.env.GARDEN_RESET, { stdio: "ignore" });
    if (process.env.GARDEN_RECORD) return recordingGarden(scenario, live);
    return { bookmark: `${live}/`, async close() {} };
}
