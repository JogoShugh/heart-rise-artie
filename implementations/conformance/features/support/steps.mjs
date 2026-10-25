// Steps for client.feature, driving heart-ts. Another client implementation
// would bring its own steps file with the same phrases.
import { Before, After, Given, When, Then, setDefaultTimeout } from "@cucumber/cucumber";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import http from "node:http";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const heart = require("../../../heart-ts/dist/index.js");

setDefaultTimeout(30_000);
const garden = process.env.GARDEN_URL ?? "http://localhost:8080";
const bookmark = `${garden}/`;

Before(function () {
    this.error = undefined;
});

After(function () {
    this.fake?.close();
});

Given("the garden is at its seed state", function () {
    execFileSync(process.env.GARDEN_RESET, { stdio: "ignore" });
});

function client(world, rung, authorization) {
    const log = [];
    return { log, heart: new heart.HeartClient({ rung, authorization, log }) };
}

Given("a client at rung {word} with the garden's credential", function (rung) {
    this.me = client(this, rung, `Bearer ${process.env.GARDEN_TOKEN}`);
});

Given("another client at rung {word} with the garden's credential", function (rung) {
    this.other = client(this, rung, `Bearer ${process.env.GARDEN_TOKEN}`);
});

Given("a client at rung {word} with no credential", function (rung) {
    this.me = client(this, rung, undefined);
});

When("it enters at the bookmark", async function () {
    this.rep = await this.me.heart.enter(bookmark);
});

When("it tries to enter at the bookmark", async function () {
    try { await this.me.heart.enter(bookmark); } catch (e) { this.error = e; }
});

When("it submits {string} with {}", async function (name, body) {
    this.validatorBefore = this.me.heart.current?.etag;
    this.rep = await this.me.heart.act(name, JSON.parse(body));
});

When("it tries to submit {string} with {}", async function (name, body) {
    this.sentBefore = this.me.log.length;
    try { this.rep = await this.me.heart.act(name, JSON.parse(body)); } catch (e) { this.error = e; }
    this.rep = this.me.heart.current;
});

When("the other client waters bed {int} with {int} ml", async function (bed, ml) {
    await this.other.heart.enter(bookmark);
    await this.other.heart.act("garden:get-bed", { id: bed });
    await this.other.heart.act("garden:water-cell", { milliliters: ml });
});

When("it follows the {string} link titled {string}", async function (rel, title) {
    this.rep = await this.me.heart.follow(rel, title);
});

Then("the state's {string} is {string}", function (member, value) {
    assert.equal(String(this.rep.state[member]), value);
});

Then("the state's {string} is {int}", function (member, value) {
    assert.equal(this.rep.state[member], value);
});

Then("the representation is at rung {word}", function (rung) {
    assert.equal(this.rep.rung, rung);
});

Then("it offers exactly {string}", function (names) {
    assert.deepEqual([...this.rep.forms.keys()], names.split(",").map((n) => n.trim()));
});

Then("every shared layer it fetched matched the integrity of its link", function () {
    // A mismatch would have thrown before the representation was returned; at L2 the layers were fetched.
    const shared = this.me.log.filter((r) => /\/(affordance-sets|schemas)\//.test(r.url));
    if (this.rep.rung === "L2") assert.ok(shared.length > 0, "no shared layer was fetched at L2");
    assert.ok(shared.every((r) => r.status === 200), JSON.stringify(shared));
});

Then("the act carried If-Match with the validator it read", function () {
    const act = this.me.log.find((r) => r.method === "POST");
    assert.ok(act, "no act was sent");
    assert.equal(act.ifMatch, this.validatorBefore);
});

Then("it was refused as {string} with status {int}", function (kind, status) {
    assert.ok(this.error instanceof heart.Refused, `expected a refusal, got ${this.error}`);
    assert.equal(this.error.kind, kind);
    assert.equal(this.error.status, status);
});

Then("the refusal says {string}", function (text) {
    const said = [this.error.message, ...this.error.violations].join(" ");
    assert.ok(said.includes(text), `the refusal said: ${said}`);
});

Then("the water act was sent once", function () {
    assert.equal(this.me.log.filter((r) => r.method === "POST" && r.url.includes("water-cell")).length, 1);
});

Then("it was refused before sending, because {string}", function (text) {
    assert.ok(this.error, "expected a refusal");
    assert.ok(this.error.message.includes(text), this.error.message);
    // Reading may happen, as reading a form's choices does; nothing that acts may go.
    const sent = this.me.log.slice(this.sentBefore).filter((r) => r.method !== "GET");
    assert.deepEqual(sent, [], "something was sent");
});

// ---- A service that sends an affordance set whose bytes do not match its link ----

Given("a service whose affordance set does not match the integrity of its link", async function () {
    const profile = `<${heart.PROFILE}>; rel="profile"`;
    this.fake = http.createServer((req, res) => {
        if (req.url === "/set") {
            res.writeHead(200, { "Content-Type": "application/hal+json" });
            return res.end(JSON.stringify({ _forms: { harvest: { _links: { target: { href: "/x" } }, method: "POST" } } }));
        }
        res.writeHead(200, { "Content-Type": "application/hal+json", Link: profile, ETag: '"1"', "Preference-Applied": "forms=link, schema=link" });
        res.end(JSON.stringify({ id: 1, _links: { self: { href: "/" }, "hr:forms": { href: "/set", integrity: "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" } } }));
    });
    await new Promise((ok) => this.fake.listen(0, "127.0.0.1", ok));
    this.fakeUrl = `http://127.0.0.1:${this.fake.address().port}/`;
});

When("it tries to enter that service", async function () {
    try { await this.me.heart.enter(this.fakeUrl); } catch (e) { this.error = e; }
});

// ---- Embedded resources (SPEC §6.1, §6.11) ----

Then("it holds these embedded resources", function (table) {
    const held = (this.rep.embedded ?? []).map((e) => ({ relation: e.rel, title: e.title ?? "", status: String(e.state.status) }));
    assert.deepEqual(held, table.hashes());
});

Then("it read no bed on its own", function () {
    const beds = this.me.log.filter((r) => /\/beds\/\d+$/.test(r.url));
    assert.deepEqual(beds, []);
});

Given("a service whose resource embeds its author, and keeps a member named {string}", async function (member) {
    const profile = `<${heart.PROFILE}>; rel="profile"`;
    const headers = { "Content-Type": "application/hal+json", Link: profile, ETag: '"1"' };
    this.fake = http.createServer((req, res) => {
        res.writeHead(200, headers);
        if (req.url === "/users/ada") {
            return res.end(JSON.stringify({
                login: "ada", name: "Ada Lovelace", _links: { self: { href: "/users/ada" } },
                _forms: { befriend: { _links: { target: { href: "/users/ada/actions/befriend" } }, method: "POST", schema: {} } },
            }));
        }
        res.end(JSON.stringify({
            number: 482, [member]: "kept as state", _links: { self: { href: "/" } }, _forms: {},
            _embedded: { author: { login: "ada", _links: { self: { href: "/users/ada", title: "Ada" } } } },
        }));
    });
    await new Promise((ok) => this.fake.listen(0, "127.0.0.1", ok));
    this.fakeUrl = `http://127.0.0.1:${this.fake.address().port}/`;
});

When("it enters that service", async function () {
    this.rep = await this.me.heart.enter(this.fakeUrl);
});

Then("the state has exactly these members", function (table) {
    assert.deepEqual(Object.keys(this.rep.state), table.hashes().map((r) => r.member));
});

Then("it refused the affordance set for its integrity", function () {
    assert.ok(this.error instanceof heart.IntegrityFailure, `expected an integrity failure, got ${this.error}`);
});

// ---- Choices from state (HAL-SCHEMA-FORMS-HR Rule 8) ----

Given("a service whose game takes its players and its agents from state", async function () {
    const profile = `<${heart.PROFILE}>; rel="profile"`;
    const player = (number, name, fouledOut = false) => ({ number, name, fouledOut });
    const game = {
        sport: "basketball",
        home: { players: [player(4, "Ana"), player(7, "Bea", true), player(9, "Cy")] },
        away: { players: [player(3, "Dee")] },
        _links: { self: { href: "/" } },
        _forms: {
            "record-foul": {
                _links: { target: { href: "/actions/record-foul" } }, method: "POST",
                schema: {
                    type: "object", required: ["team", "playerNumber"],
                    properties: {
                        team: { type: "string", enum: ["home", "away"] },
                        playerNumber: {
                            type: "integer",
                            "hr:options": {
                                pointer: "/{team}/players", valueField: "number", promptField: "name",
                                where: { fouledOut: false }, maxItems: 1,
                            },
                        },
                    },
                },
            },
            "call-agent": {
                _links: { target: { href: "/actions/call-agent" } }, method: "POST",
                schema: {
                    type: "object", required: ["agent"],
                    properties: {
                        agent: {
                            type: "string",
                            "hr:options": {
                                link: { href: "/agents{?skill}", templated: true },
                                templatePointers: { skill: "/sport" }, items: "/agents",
                                valueField: "login", promptField: "name", where: { onDuty: true },
                            },
                        },
                    },
                },
            },
        },
    };
    const agent = (login, name, skill, onDuty) => ({ login, name, skill, onDuty });
    const agents = [
        agent("priya", "Priya", "basketball", true), agent("marco", "Marco", "basketball", false),
        agent("lena", "Lena", "basketball", true), agent("tom", "Tom", "football", true),
    ];
    this.fakeLog = [];
    this.fake = http.createServer((req, res) => {
        const url = new URL(req.url, "http://fake");
        const seen = { method: req.method, url: req.url, ifNoneMatch: req.headers["if-none-match"] };
        this.fakeLog.push(seen);
        if (url.pathname === "/agents") {
            const skill = url.searchParams.get("skill");
            const etag = `"agents-${skill}"`;
            seen.status = req.headers["if-none-match"] === etag ? 304 : 200;
            res.writeHead(seen.status, { "Content-Type": "application/json", ETag: etag, "Cache-Control": "no-cache" });
            return res.end(seen.status === 304 ? undefined : JSON.stringify({ agents: agents.filter((a) => a.skill === skill) }));
        }
        seen.status = 200;
        res.writeHead(200, { "Content-Type": "application/hal+json", Link: profile, ETag: '"1"' });
        res.end(JSON.stringify(game));
    });
    await new Promise((ok) => this.fake.listen(0, "127.0.0.1", ok));
    this.fakeUrl = `http://127.0.0.1:${this.fake.address().port}/`;
});

/** The choices as a table shows them: each value as written, and its prompt. */
const rows = (choices) => choices.map((c) => ({ value: String(c.value), prompt: c.prompt }));

Then("the choices for {string} of {string}, by {string}, are", async function (property, form, field, table) {
    const expected = table.hashes();
    for (const value of [...new Set(expected.map((r) => r[field]))]) {
        const choices = await this.me.heart.choices(form, { [field]: value });
        const want = expected.filter((r) => r[field] === value).map(({ value, prompt }) => ({ value, prompt }));
        assert.deepEqual(rows(choices[property] ?? []), want, `${field} ${value}`);
    }
});

Then("the choices for {string} of {string} are", async function (property, form, table) {
    const choices = await this.me.heart.choices(form, {});
    assert.deepEqual(rows(choices[property] ?? []), table.hashes());
});

Then("the agents were read at {string}", function (path) {
    assert.deepEqual(this.fakeLog.filter((r) => r.url.startsWith("/agents")).map((r) => r.url), [path]);
});

When("it resolves the choices for {string} of {string} twice", async function (property, form) {
    await this.me.heart.choices(form, {});
    await this.me.heart.choices(form, {});
});

Then("the second read of the agents carried the ETag of the first, and was answered 304", function () {
    const reads = this.fakeLog.filter((r) => r.url.startsWith("/agents"));
    assert.equal(reads.length, 2, JSON.stringify(reads));
    assert.equal(reads[0].status, 200);
    assert.equal(reads[1].ifNoneMatch, '"agents-basketball"');
    assert.equal(reads[1].status, 304);
});

Then("the act {string} was sent once", function (form) {
    assert.equal(this.fakeLog.filter((r) => r.method === "POST" && r.url === `/actions/${form}`).length, 1);
});
