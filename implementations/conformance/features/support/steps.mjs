// Steps for client.feature, driving heart-ts. Another client implementation
// would bring its own steps file with the same phrases.
import { Before, After, Given, When, Then, setDefaultTimeout } from "@cucumber/cucumber";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import http from "node:http";
import { createRequire } from "node:module";
import { openGarden } from "./garden-fixture.mjs";

const require = createRequire(import.meta.url);
const heart = require("../../../heart-ts/dist/index.js");

setDefaultTimeout(30_000);
const credential = `Bearer ${process.env.GARDEN_TOKEN ?? "fixture"}`;

Before(function ({ pickle }) {
    this.error = undefined;
    this.scenario = pickle.name;
});

After(async function () {
    this.fake?.close();
    this.otherOrigin?.close();
    await this.garden?.close();
});

Given("the garden is at its seed state", async function () {
    this.garden = await openGarden(this.scenario);
});

function client(world, rung, authorization) {
    const log = [];
    const schemaFailures = [];
    return { log, schemaFailures, heart: new heart.HeartClient({ rung, authorization, log, schemaFailures }) };
}

Given("a client at rung {word} with the garden's credential", function (rung) {
    this.me = client(this, rung, credential);
});

Given("another client at rung {word} with the garden's credential", function (rung) {
    this.other = client(this, rung, credential);
});

Given("a client at rung {word} with no credential", function (rung) {
    this.me = client(this, rung, undefined);
});

When("it enters at the bookmark", async function () {
    this.rep = await this.me.heart.enter(this.garden.bookmark);
});

When("it tries to enter at the bookmark", async function () {
    try { await this.me.heart.enter(this.garden.bookmark); } catch (e) { this.error = e; }
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
    await this.other.heart.enter(this.garden.bookmark);
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
    this.resolved = [await this.me.heart.choices(form, {}), await this.me.heart.choices(form, {})];
});

Then("the second answer held the same choices as the first", function () {
    assert.ok(Object.values(this.resolved[0]).flat().length > 0);
    assert.deepEqual(this.resolved[1], this.resolved[0]);
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

// ---- A form's schema, checked just before an act is sent ----

/** One resource offering one form, shaped by [world.service]; every request is kept in [world.fakeLog]. */
function resourceOf(world) {
    const { name, schema, target, templated, method, form, state, links } = world.service;
    return JSON.stringify({
        ...state,
        _links: { self: { href: "/" }, ...links },
        ...world.service.embedded,
        _forms: { [name]: { _links: { target: { href: target, templated } }, method, schema, ...form } },
    });
}

function answer(world, req, res, body) {
    const { service } = world;
    const plain = service.plain[new URL(req.url, "http://fake").pathname];
    if (plain) {
        res.writeHead(plain.status, { "Content-Type": plain.type, ...plain.headers });
        return res.end(plain.body);
    }
    const refused = req.method !== "GET" ? service.refusal : undefined;
    if (req.method === "GET" && service.readStatus) {
        res.writeHead(service.readStatus);
        return res.end();
    }
    if (world.fakeRefusal && req.method !== "GET") {
        res.writeHead(422, { "Content-Type": "application/problem+json" });
        return res.end(JSON.stringify(world.fakeRefusal));
    }
    if (refused) {
        res.writeHead(refused.status, { "Content-Type": "application/problem+json" });
        return res.end(refused.body);
    }
    const headers = { "Content-Type": "application/hal+json", ...service.validator, ...service.headers };
    if (service.profile) headers.Link = `<${heart.PROFILE}>; rel="profile"`;
    res.writeHead(200, headers);
    res.end(body ?? resourceOf(world));
}

const recorded = (req, body) => ({
    method: req.method, url: req.url, body,
    authorization: req.headers.authorization, ifMatch: req.headers["if-match"], contentType: req.headers["content-type"],
    accept: req.headers.accept, profile: req.headers["accept-profile"], cacheControl: req.headers["cache-control"],
    ifNoneMatch: req.headers["if-none-match"],
});

async function serve(handler) {
    const server = http.createServer(async (req, res) => {
        const chunks = [];
        for await (const chunk of req) chunks.push(chunk);
        handler(req, res, Buffer.concat(chunks).toString("utf8"));
    });
    await new Promise((ok) => server.listen(0, "127.0.0.1", ok));
    return server;
}

Given("a service offering {string} with the schema {}", async function (name, schema) {
    this.service = {
        name, schema: JSON.parse(schema), target: `/actions/${name}`, templated: false, method: "POST",
        form: {}, state: {}, links: {}, headers: {}, profile: true, plain: {}, validator: { ETag: '"1"' }, embedded: {},
    };
    this.fakeLog = [];
    this.fake = await serve((req, res, body) => {
        this.fakeLog.push(recorded(req, body));
        answer(this, req, res);
    });
    this.fakeUrl = `http://127.0.0.1:${this.fake.address().port}/`;
});

Given("that service refuses {string} as invalid input, saying {string}", function (_name, detail) {
    this.fakeRefusal = { type: "https://w3id.org/heart-rise/problems/invalid-input", title: "Invalid input", detail };
});

const actsOn = (world, form) => world.fakeLog.filter((r) => r.method === "POST" && r.url === `/actions/${form}`);

Then("the act {string} was sent {int} times", function (form, times) {
    assert.equal(actsOn(this, form).length, times);
});

Then("the act {string} was sent with exactly {}", function (form, body) {
    assert.deepEqual(JSON.parse(actsOn(this, form)[0].body), JSON.parse(body));
});

Then("it was refused as {string} before sending, because {string}", function (kind, text) {
    assert.ok(this.error instanceof heart.Refused, `expected a refusal, got ${this.error}`);
    assert.equal(this.error.kind, kind);
    assert.ok(this.error.violations.join(" ").includes(text), this.error.violations.join(" "));
    assert.ok(this.error.message.includes("nothing was sent"), this.error.message);
    const sent = this.me.log.slice(this.sentBefore).filter((r) => r.method !== "GET");
    assert.deepEqual(sent, [], "something was sent");
});

Then("the local log holds one schema failure, for {string}", function (form) {
    assert.equal(this.me.schemaFailures.length, 1, JSON.stringify(this.me.schemaFailures));
    const [failure] = this.me.schemaFailures;
    assert.equal(failure.form, form);
    assert.ok(failure.schema.length > 0);
    assert.ok(failure.error.length > 0);
});

// ---- What a client does with what a service sends ----

Given("that service answers {string} with status {int} and the body {}", function (_name, status, body) {
    this.service.refusal = { status, body };
});

Given("that service names no profile", function () {
    this.service.profile = false;
});

Given("that service refuses every read with status {int}", function (status) {
    this.service.readStatus = status;
});

Given("that service links {string} titled {string}", function (rel, title) {
    this.service.links[rel] = { href: "/", title };
});

Given("that service sends {string} to another origin", async function (name) {
    this.otherLog = [];
    this.otherOrigin = await serve((req, res, body) => {
        this.otherLog.push(recorded(req, body));
        answer(this, req, res, JSON.stringify({ _links: { self: { href: "/" } }, _forms: {} }));
    });
    this.service.target = `http://127.0.0.1:${this.otherOrigin.address().port}/actions/${name}`;
});

Given("that service offers {string} by {word}", function (_name, method) {
    this.service.method = method;
});

Given("that service's form names no method", function () {
    this.service.method = undefined;
});

Given("that service's form takes {string}", function (type) {
    this.service.form.contentType = type;
});

Given("that service reads {string} at {string}, with pointers {} into the state {}", function (_name, target, pointers, state) {
    Object.assign(this.service, { method: "GET", target, templated: true, state: JSON.parse(state) });
    this.service.form["hr:templatePointers"] = JSON.parse(pointers);
});

function declares(world, schema, state) {
    Object.assign(world.service, { schema: JSON.parse(schema), state: JSON.parse(state) });
}

Given("that service declares the schema {}, with the state {}", function (schema, state) {
    declares(this, schema, state);
});

Given("that service now declares the schema {}", function (schema) {
    declares(this, schema, "{}");
});

Given("that service applies L2, with its affordance set linked without an integrity value", function () {
    this.service.headers["Preference-Applied"] = "forms=link, schema=link";
    this.service.links["hr:forms"] = { href: "/set" };
});

Given("that service applies L2, with no affordance set", function () {
    this.service.headers["Preference-Applied"] = "forms=link, schema=link";
});

Given("that service answers acts from {string}", function (location) {
    this.service.headers["Content-Location"] = location;
});

/** Runs [act], keeping what it throws for a Then to read. */
async function trying(world, act) {
    try { world.rep = await act(); } catch (e) { world.error = e; }
}

When("it tries to read the resource again", async function () {
    await trying(this, () => this.me.heart.refresh());
});

When("it reads the resource again", async function () {
    this.rep = await this.me.heart.refresh();
});

When("it tries to read the choices of {string}", async function (form) {
    await trying(this, () => this.me.heart.choices(form));
});

When("it tries to follow {string} titled {string}", async function (rel, title) {
    await trying(this, () => this.me.heart.follow(rel, title));
});

When("it tries to follow {string} with no title", async function (rel) {
    await trying(this, () => this.me.heart.follow(rel));
});

Then("it failed, saying {string}", function (text) {
    assert.ok(this.error, "expected a failure");
    assert.ok(this.error.message.includes(text), this.error.message);
});

Then("it holds a representation", function () {
    assert.equal(this.error, undefined);
    assert.ok(this.rep);
});

Then("the resource was read {int} times", function (times) {
    assert.equal(this.fakeLog.filter((r) => r.method === "GET").length, times);
});

Then("the service was sent the credential", function () {
    assert.ok(this.fakeLog[0].authorization);
});

Then("the other origin was sent none", function () {
    assert.equal(this.otherLog.length, 1);
    assert.equal(this.otherLog[0].authorization, undefined);
});

const lastRequest = (world) => world.fakeLog.at(-1);

Then("the last request was {word} with the body {string}", function (method, body) {
    assert.equal(lastRequest(this).method, method);
    assert.equal(lastRequest(this).body, body);
});

Then("it carried the validator it read", function () {
    assert.equal(lastRequest(this).ifMatch, '"1"');
});

Then("it had the content type {string}", function (type) {
    assert.equal(lastRequest(this).contentType ?? "", type);
});

Then("the last request was for {string}", function (url) {
    assert.equal(lastRequest(this).url, url);
});

Then("the client holds {string}", function (path) {
    assert.equal(new URL(this.me.heart.current.url).pathname, path);
});

Then("no schema failure was noted", function () {
    assert.deepEqual(this.me.schemaFailures, []);
});

Then("the only violation is {string}", function (text) {
    assert.deepEqual(this.error.violations, [text]);
});

Then("it names no violations", function () {
    assert.deepEqual(this.error.violations, []);
});

Given("that service applies L2, with a true affordance set", function () {
    const set = JSON.stringify({ _forms: { "from-the-set": { _links: { target: { href: "/x" } }, schema: {} } } });
    const integrity = `sha256-${createHash("sha256").update(set).digest("base64")}`;
    this.service.headers["Preference-Applied"] = "forms=link, schema=link";
    this.service.links["hr:forms"] = { href: "/set", integrity };
    this.service.plain["/set"] = { status: 200, type: "application/hal+json", headers: {}, body: set };
});

Given("that service sends no validator", function () {
    this.service.validator = {};
});

Given("a client with no options", function () {
    this.me = { heart: new heart.HeartClient() };
});

Given("that service serves {string} with status {int} and the body {}", function (path, status, body) {
    this.service.plain[path] = { status, type: "application/json", headers: {}, body };
});

Given("that service's links are {}", function (links) {
    this.service.links = JSON.parse(links);
});

Given("that service embeds {}", function (embedded) {
    this.service.embedded = { _embedded: JSON.parse(embedded) };
});

Given("that service's target is not templated", function () {
    this.service.templated = false;
});

const lastRead = (world) => world.fakeLog.filter((r) => r.method === "GET").at(-1);

Then("the last read accepted {string}, at the profile, with no cache", function (accept) {
    assert.equal(lastRead(this).accept, accept);
    assert.equal(lastRead(this).profile, `<${heart.PROFILE}>`);
    assert.equal(lastRead(this).cacheControl, "no-cache");
});

Then("the last read accepted {string}, with no cache", function (accept) {
    assert.equal(lastRead(this).accept, accept);
    assert.equal(lastRead(this).cacheControl, "no-cache");
});

Then("the last read carried no validator", function () {
    assert.equal(lastRead(this).ifNoneMatch, undefined);
});

Then("the affordance set was read accepting {string}", function (accept) {
    assert.equal(this.fakeLog.find((r) => r.url === "/set").accept, accept);
});

Then("it carried an empty validator", function () {
    assert.equal(lastRequest(this).ifMatch, "");
});

Then("the form {string} has choices for exactly {string}", async function (form, property) {
    assert.deepEqual(Object.keys(await this.me.heart.choices(form)), [property]);
});

Then("the values for {string} of {string} are {}", async function (property, form, values) {
    const choices = await this.me.heart.choices(form);
    assert.deepEqual(choices[property].map((c) => c.value), JSON.parse(values));
});

Then("it holds exactly the links {string}, with no titles", function (links) {
    assert.equal(this.rep.links.map((l) => `${l.rel} ${l.href}`).join(", "), links);
    assert.ok(this.rep.links.every((l) => l.title === undefined));
});

Then("it holds embedded resources at exactly {string}", function (href) {
    assert.deepEqual(this.rep.embedded.map((e) => e.href), [href]);
});

Given("that service serves {string} with the validator {string} and the body {}", function (path, validator, body) {
    this.service.plain[path] = { status: 200, type: "application/json", headers: { ETag: `"${validator}"` }, body };
});
