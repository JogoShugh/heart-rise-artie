// Steps for the shared conformance features, driving heart-ts against the
// recorded garden and the simulated services (sims.mjs). Another client brings
// its own steps file with the same phrases.
import { Before, After, Given, When, Then, setDefaultTimeout } from "@cucumber/cucumber";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { openGarden } from "./garden-fixture.mjs";
import { sims } from "./sims.mjs";

const require = createRequire(import.meta.url);
const heart = require("../../../heart-ts/dist/index.js");

setDefaultTimeout(30_000);
const credential = `Bearer ${process.env.GARDEN_TOKEN ?? "fixture"}`;

Before(function ({ pickle }) {
    this.error = undefined;
    this.scenario = pickle.name;
});

After(async function () {
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

// ---- Embedded resources (SPEC §6.1, §6.11) ----

Then("it holds these embedded resources", function (table) {
    const held = (this.rep.embedded ?? []).map((e) => ({ relation: e.rel, title: e.title ?? "", status: String(e.state.status) }));
    assert.deepEqual(held, table.hashes());
});

Then("it read no bed on its own", function () {
    const beds = this.me.log.filter((r) => /\/beds\/\d+$/.test(r.url));
    assert.deepEqual(beds, []);
});

Then("the state has exactly these members", function (table) {
    assert.deepEqual(Object.keys(this.rep.state), table.hashes().map((r) => r.member));
});

Then("it refused the affordance set for its integrity", function () {
    assert.ok(this.error instanceof heart.IntegrityFailure, `expected an integrity failure, got ${this.error}`);
});

// ---- Choices from state (HAL-SCHEMA-FORMS-HR Rule 8) ----

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

When("it resolves the choices for {string} of {string} twice", async function (property, form) {
    this.resolved = [await this.me.heart.choices(form, {}), await this.me.heart.choices(form, {})];
});

Then("the second answer held the same choices as the first", function () {
    assert.ok(Object.values(this.resolved[0]).flat().length > 0);
    assert.deepEqual(this.resolved[1], this.resolved[0]);
});

Then("the act {string} was sent {int} times", async function (form, times) {
    const acts = await simActsOn(form);
    assert.equal(acts.length, times);
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

Then("the resource was read {int} times", async function (times) {
    const reads = (await received(this)).filter((r) => r.method === "GET" && !/^\/(affordance-sets|schemas)\//.test(r.url));
    assert.equal(reads.length, times);
});

Then("the service was sent the credential", async function () {
    const first = (await received(this)).find((r) => r.service !== "elsewhere.test");
    assert.ok(first.authorization);
});

Then("the other origin was sent none", async function () {
    const other = (await received(this)).filter((r) => r.service === "elsewhere.test");
    assert.equal(other.length, 1);
    assert.equal(other[0].authorization, undefined);
});

const lastRequest = async (world) => (await received(world)).at(-1);

Then("the last request was {word} with the body {string}", async function (method, body) {
    assert.equal((await lastRequest(this)).method, method);
    assert.equal((await lastRequest(this)).body, body);
});

Then("it carried the validator it read", async function () {
    const act = (await received(this)).filter((r) => r.method !== "GET").at(-1);
    assert.equal(act.ifMatch, this.validatorBefore);
});

Then("it had the content type {string}", async function (type) {
    assert.equal((await lastRequest(this)).contentType ?? "", type);
});

Then("the last request was for {string}", async function (url) {
    assert.equal((await lastRequest(this)).url, url);
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

/** The last read of a resource itself: shared layers, which a client reads by their links, are not counted. */
const lastRead = async (world) => (await received(world))
    .filter((r) => r.method === "GET" && !/^\/(affordance-sets|schemas)\//.test(r.url)).at(-1);

Then("the last read accepted {string}, at the profile, with no cache", async function (accept) {
    assert.equal((await lastRead(this)).accept, accept);
    assert.equal((await lastRead(this)).profile, `<${heart.PROFILE}>`);
    assert.equal((await lastRead(this)).cacheControl, "no-cache");
});

Then("the last read accepted {string}, with no cache", async function (accept) {
    assert.equal((await lastRead(this)).accept, accept);
    assert.equal((await lastRead(this)).cacheControl, "no-cache");
});

Then("the last read carried no validator", async function () {
    assert.equal((await lastRead(this)).ifNoneMatch, undefined);
});

Then("the affordance set was read accepting {string}", async function (accept) {
    const set = (await received(this)).find((r) => r.url.startsWith("/affordance-sets/"));
    assert.equal(set.accept, accept);
});

Then("it carried an empty validator", async function () {
    const act = (await received(this)).filter((r) => r.method !== "GET").at(-1);
    assert.equal(act.ifMatch, "");
});

Then("the form {string} has choices for exactly {string}", async function (form, property) {
    assert.deepEqual(Object.keys(await this.me.heart.choices(form)), [property]);
});

Then("the values for {string} of {string} are {}", async function (property, form, values) {
    const choices = await this.me.heart.choices(form);
    assert.deepEqual(choices[property].map((c) => c.value), JSON.parse(values));
});

Then("it holds exactly these links, with no titles", function (table) {
    assert.deepEqual(this.rep.links.map((l) => ({ relation: l.rel, href: l.href })), table.hashes());
    assert.ok(this.rep.links.every((l) => l.title === undefined));
});

Then("it holds embedded resources at exactly {string}", function (href) {
    assert.deepEqual(this.rep.embedded.map((e) => e.href), [href]);
});

// ---- HAL-FORMS, the L0 on-ramp ----

const halFormsClient = (rung, authorization) => {
    const log = [];
    const schemaFailures = [];
    return { log, schemaFailures, heart: new heart.HeartClient({ dialect: "hal-forms", rung, authorization, log, schemaFailures }) };
};

Given("a HAL-FORMS client with the garden's credential", function () {
    this.me = halFormsClient(undefined, credential);
});

Given("another HAL-FORMS client with the garden's credential", function () {
    this.other = halFormsClient(undefined, credential);
});

When("the other client waters bed {int} with {int} ml, in HAL-FORMS", async function (bed, ml) {
    await this.other.heart.enter(this.garden.bookmark);
    await this.other.heart.act("getBed", { id: bed });
    await this.other.heart.follow("item", `bed-${bed}`);
    await this.other.heart.act("waterCell", { milliliters: ml });
});

const dialects = { "HAL-FORMS": "hal-forms", "HAL Schema Forms": "hal-schema-forms" };

Then("the representation is at rung L0, in {}", function (dialect) {
    assert.equal(this.rep.rung, "L0");
    assert.equal(this.rep.dialect, dialects[dialect]);
});

Then("the last read accepted {string}, at the HAL-FORMS profile, with no cache", async function (accept) {
    assert.equal((await lastRead(this)).accept, accept);
    assert.equal((await lastRead(this)).profile, "<https://w3id.org/heart-rise/hal-forms>");
    assert.equal((await lastRead(this)).cacheControl, "no-cache");
});

Then("the last read asked for no layering", async function () {
    assert.equal((await lastRead(this)).prefer, undefined);
});

// ---- The rungs ----

const setsRead = (log) => log.filter((r) => /\/affordance-sets\//.test(r.url)).map((r) => r.url);

When("the other client reads bed {int}", async function (bed) {
    await this.other.heart.enter(this.garden.bookmark);
    await this.other.heart.act("garden:get-bed", { id: bed });
});

Then("no shared layer was read twice", function () {
    const shared = this.me.log.filter((r) => /\/(affordance-sets|schemas)\//.test(r.url)).map((r) => r.url);
    assert.ok(shared.length > 0, "no shared layer was read");
    assert.deepEqual(shared, [...new Set(shared)]);
});

Then("the two clients read different affordance sets", function () {
    const mine = setsRead(this.me.log);
    assert.ok(mine.length > 0);
    assert.deepEqual(mine.filter((url) => setsRead(this.other.log).includes(url)), []);
});

Then("the last read asked for {string}", async function (prefer) {
    assert.equal((await lastRead(this)).prefer, prefer);
});

function pairOfClients(world, setCache, authorization) {
    const client = () => {
        const log = [];
        return { log, heart: new heart.HeartClient({ rung: "L1", log, setCache, authorization }) };
    };
    world.me = client();
    world.other = client();
}

Then("the affordance set was read {int} times", async function (times) {
    const sets = (await received(this)).filter((r) => r.url.startsWith("/affordance-sets/"));
    assert.equal(sets.length, times);
});

// ---- An empty answer, and a cache of the caller's ----

Then("it sent {word} for {string}, with no body", async function (method, url) {
    const sent = (await received(this)).filter((r) => r.method === method && r.url === url);
    assert.deepEqual(sent.map((r) => r.body), [""], JSON.stringify(sent));
});

// ---- One resource in two dialects ----

When("each client reads bed {int} in its own dialect", async function (bed) {
    await this.me.heart.enter(this.garden.bookmark);
    await this.me.heart.act("garden:get-bed", { id: bed });
    await this.other.heart.enter(this.garden.bookmark);
    await this.other.heart.act("getBed", { id: bed });
    await this.other.heart.follow("item", `bed-${bed}`);
});

const offeredActs = (client) => [...client.heart.current.forms.values()]
    .map((form) => `${form.method} ${form.target.split("/").at(-1)}`);

Then("both hold the same state", function () {
    assert.deepEqual(this.other.heart.current.state, this.me.heart.current.state);
});

Then("both offer the act {string}, by the same method", function (act) {
    assert.deepEqual(offeredActs(this.me), [`POST ${act}`]);
    assert.deepEqual(offeredActs(this.other), [`POST ${act}`]);
});

// ---- The simulated services (heart/heart-sims) ----

const SERVICES = {
    build: { host: "build.test", credential: "Bearer build-token" },
    GitHub: { host: "github.test", credential: "Bearer github-token" },
    github: { host: "github.test", credential: "Bearer github-token" },
    deploy: { host: "deploy.test", credential: "Bearer deploy-token" },
};

/** One header of a request the simulator logged, whatever its case. */
const headerOf = (logged, name) => Object.entries(logged.headers).find(([key]) => key.toLowerCase() === name.toLowerCase())?.[1] ?? undefined;

/** What the service received, in order, in one shape: the hand-built service's log, or the simulator's. */
async function received(world) {
    return (await sims.log()).map((r) => ({
        ...r,
        prefer: headerOf(r, "Prefer"), accept: headerOf(r, "Accept"), profile: headerOf(r, "Accept-Profile"),
        cacheControl: headerOf(r, "Cache-Control"), ifMatch: headerOf(r, "If-Match"), ifNoneMatch: headerOf(r, "If-None-Match"),
        contentType: headerOf(r, "Content-Type"), authorization: headerOf(r, "Authorization"),
    }));
}

/** The act a form names, as its target's last segment: "ci:build" is acted on at .../actions/build. */
const segmentOf = (form) => form.slice(form.indexOf(":") + 1);

/** The acts on [form] the simulated services received, in order. */
async function simActsOn(form) {
    return (await sims.log()).filter((r) => r.method !== "GET" && r.url.endsWith(`/actions/${segmentOf(form)}`));
}

Given("a client at rung {word} with the {word} service's credential", function (rung, service) {
    this.me = client(this, rung, SERVICES[service].credential);
});

Given("the build service has a job {string} whose parameters are {}", async function (name, parameters) {
    await sims.configureJob(name, parameters);
});

Given("the build service declares the parameters of its job {string} as {}, without checking them", async function (name, parameters) {
    await sims.declareSchema(SERVICES.build.host, "ci:build", parameters);
});

When("the job {string} is given the parameters {}", async function (name, parameters) {
    await sims.configureJob(name, parameters);
});

Given("the build service refuses {string} as invalid input, saying {string}", async function (form, detail) {
    const problem = { type: "https://w3id.org/heart-rise/problems/invalid-input", title: "Invalid input", detail };
    await sims.answerAct(SERVICES.build.host, segmentOf(form), 422, JSON.stringify(problem));
});

When("it enters the job {string}", async function (name) {
    this.rep = await this.me.heart.enter(`http://${SERVICES.build.host}/jobs/${name}`);
});

Then("the act {string} was sent once, with exactly {}", async function (form, body) {
    const acts = await simActsOn(form);
    assert.equal(acts.length, 1);
    assert.deepEqual(JSON.parse(acts[0].body), JSON.parse(body));
});

/** A refusal of [kind] before sending: nothing that acts went out after the try. */
function refusedBeforeSending(world, kind) {
    assert.ok(world.error instanceof heart.Refused, `expected a refusal, got ${world.error}`);
    assert.equal(world.error.kind, kind);
    assert.ok(world.error.message.includes("nothing was sent"), world.error.message);
    const sent = world.me.log.slice(world.sentBefore).filter((r) => r.method !== "GET");
    assert.deepEqual(sent, [], "something was sent");
}

Then("it was refused as {string} before sending, with the one violation {string}", function (kind, violation) {
    refusedBeforeSending(this, kind);
    assert.deepEqual(this.error.violations, [violation]);
});

Then("it was refused as {string} before sending, with the violations", function (kind, table) {
    refusedBeforeSending(this, kind);
    assert.deepEqual(this.error.violations, table.raw().map(([violation]) => violation));
});

Given("the deploy service's app {string} has the regions", async function (app, table) {
    const regions = {};
    for (const { environment, name, label, available } of table.hashes()) {
        (regions[environment] ??= []).push({ name, label, available: available === "true" });
    }
    await sims.setRegions(app, regions);
});

Given("the deploy service has the releases", async function (table) {
    for (const { app, version, status } of table.hashes()) await sims.publishRelease(app, version, status);
});

When("it enters the app {string}", async function (name) {
    this.rep = await this.me.heart.enter(`http://${SERVICES.deploy.host}/apps/${name}`);
});

/** The reads of the deploy service's releases list, in order. */
const releaseReads = async () => (await sims.log()).filter((r) => r.service === SERVICES.deploy.host && r.url.startsWith("/releases"));

Then("the releases were read at {string}", async function (path) {
    assert.deepEqual((await releaseReads()).map((r) => r.url), [path]);
});

Then("the second read of the releases carried the ETag of the first, and was answered 304", async function () {
    const reads = await releaseReads();
    assert.equal(reads.length, 2, JSON.stringify(reads));
    assert.equal(reads[0].status, 200);
    assert.equal(reads[1].headers["If-None-Match"] ?? reads[1].headers["if-none-match"], reads[0].etag);
    assert.equal(reads[1].status, 304);
});

const hostOf = (service) => SERVICES[service].host;

Given("a HAL-FORMS client with the {word} service's credential", function (service) {
    this.me = halFormsClient(undefined, SERVICES[service].credential);
});

Given("two clients at rung L1 with the {word} service's credential and {}", function (service, caches) {
    const kinds = {
        "one cache between them": () => new Map(),
        "a cache each": () => undefined,
        "one cache that keeps nothing": () => ({ get: () => undefined, set: () => undefined }),
    };
    pairOfClients(this, kinds[caches](), SERVICES[service].credential);
});

When("it tries to enter the app {string}", async function (name) {
    await trying(this, () => this.me.heart.enter(`http://${hostOf("deploy")}/apps/${name}`));
});

When("the other client enters the app {string}", async function (name) {
    await this.other.heart.enter(`http://${hostOf("deploy")}/apps/${name}`);
});

Given("the deploy service serves up to rung {word}", async function (rung) {
    await sims.deployServesUpTo(rung);
});

Given("the deploy service's app {string} starts deploying", async function (app) {
    await sims.startDeploying(app);
});

Given("the {word} service says it applied {string}", async function (service, applied) {
    await sims.faults(hostOf(service), { applied });
});

Given("the {word} service links its affordance set as {string}, with the curie {string} for {string}", async function (service, rel, curie, href) {
    await sims.faults(hostOf(service), { setLink: { rel, curie, href } });
});

Given("the {word} service links its affordance set as {string}, with no curie", async function (service, rel) {
    await sims.faults(hostOf(service), { setLink: { rel } });
});

Given("the {word} service links its affordance set with an integrity its bytes do not match", async function (service) {
    await sims.faults(hostOf(service), { wrongIntegrity: true });
});

Given("the {word} service serves every affordance set at one address", async function (service) {
    await sims.faults(hostOf(service), { oneAddress: true });
});

Given("the {word} service names no profile", async function (service) {
    await sims.faults(hostOf(service), { noProfile: true });
});

Given("the {word} service's media type is {string}", async function (service, mediaType) {
    await sims.faults(hostOf(service), { mediaType });
});

Given("the {word} service's template {string} is {}", async function (service, name, template) {
    await sims.template(hostOf(service), name, template);
});

Given("a HAL-FORMS client at rung {word} with the {word} service's credential", function (rung, service) {
    this.me = halFormsClient(rung, SERVICES[service].credential);
});

Given("the build service's job {string} has the HAL-FORMS parameters {}", async function (job, properties) {
    const template = { method: "POST", target: `/jobs/${job}/actions/build`, contentType: "application/json", properties: JSON.parse(properties) };
    await sims.template(hostOf("build"), "build", JSON.stringify(template));
});

Given("the {word} service serves only HAL Schema Forms", async function (service) {
    await sims.faults(hostOf(service), { onlyAccept: "application/hal+json" });
});

Given("the {word} service serves {string} with status {int} and the body {}", async function (service, path, status, body) {
    await sims.plain(hostOf(service), { path, status, body });
});

Given("the {word} service answers {string} with status {int} and the body {}", async function (service, form, status, body) {
    await sims.answerAct(hostOf(service), segmentOf(form), status, body);
});

Given("the {word} service refuses every read with status {int}", async function (service, readStatus) {
    await sims.faults(hostOf(service), { readStatus });
});

Given("the {word} service sends no validator", async function (service) {
    await sims.faults(hostOf(service), { noValidator: true });
});

Given("a client with no options but the {word} service's credential", function (service) {
    this.me = { heart: new heart.HeartClient({ authorization: SERVICES[service].credential }) };
});

When("it tries to enter the job {string}", async function (name) {
    await trying(this, () => this.me.heart.enter(`http://${hostOf("build")}/jobs/${name}`));
});

Then("it offers exactly these acts", function (table) {
    assert.deepEqual([...this.rep.forms.keys()], table.raw().map(([act]) => act));
});

When("it enters the pull requests of {string}", async function (repository) {
    this.rep = await this.me.heart.enter(`http://${hostOf("github")}/repos/${repository}/pulls`);
});

When("it enters the pull request {int} of {string}", async function (number, repository) {
    this.rep = await this.me.heart.enter(`http://${hostOf("github")}/repos/${repository}/pulls/${number}`);
});

Given("the {word} service's job also holds {}", async function (service, members) {
    await sims.faults(hostOf(service), { members: JSON.parse(members) });
});

Given("the {word} service's job links only {}", async function (service, links) {
    await sims.faults(hostOf(service), { linksOnly: JSON.parse(links) });
});

Given("the {word} service's job embeds only {}", async function (service, embedded) {
    await sims.faults(hostOf(service), { embedsOnly: JSON.parse(embedded) });
});

Then("it sent {word} with the body {string} and the content type {string}", async function (method, body, type) {
    const sent = (await received(this)).filter((r) => r.method === method).at(-1);
    assert.deepEqual([sent.body, sent.contentType ?? ""], [body, type]);
});

Given("the {word} service sends {string} to another origin", async function (service, form) {
    await sims.faults(hostOf(service), { reshaped: { [form]: { _links: { target: { href: "http://elsewhere.test/comments" } } } } });
});

Given("the {word} service's form {string} names no method or content type", async function (service, form) {
    await sims.faults(hostOf(service), { reshaped: { [form]: { method: null, contentType: null } } });
});

Given("the {word} service's form {string} takes {string}", async function (service, form, contentType) {
    await sims.faults(hostOf(service), { reshaped: { [form]: { contentType } } });
});

Given("the {word} service answers acts with {word} body, from {}", async function (service, body, from) {
    const faults = { actsWithNoBody: body === "no" };
    if (from !== "nowhere") faults.actsFrom = JSON.parse(from);
    await sims.faults(hostOf(service), faults);
});

Given("the {word} service links its affordance set without an integrity value", async function (service) {
    await sims.faults(hostOf(service), { noIntegrity: true });
});

Given("the {word} service links no affordance set", async function (service) {
    await sims.faults(hostOf(service), { noSet: true });
});

Given("the {word} service's job offers {string} as {} to {string}, with pointers {}", async function (service, form, kind, target, pointers) {
    const shape = {
        _links: { target: { href: target, templated: kind !== "an untemplated GET" } },
        method: "GET",
        schema: { type: "object" },
        "hr:templatePointers": JSON.parse(pointers),
    };
    await sims.faults(hostOf(service), { reshaped: { [form]: shape } });
});

Given("the {word} service serves {string} with the validator {string} and the body {}", async function (service, path, validator, body) {
    await sims.plain(hostOf(service), { path, status: 200, body, validator: `"${validator}"` });
});

Then("the client holds {string}, gone", function (path) {
    assert.equal(new URL(this.me.heart.current.url).pathname, path);
    assert.equal(this.me.heart.current.gone, true);
});

// ---- The employees service: a HEART service, or handicapped as Spring HATEOAS's affordances example ----

const employeesService = "http://employees.test";

Given("a client asking in {} at rung {word} for the employees service", function (dialect, rung) {
    const log = [];
    const schemaFailures = [];
    const options = { dialect: dialects[dialect], rung, authorization: "Bearer employees-token", log, schemaFailures };
    this.me = { log, schemaFailures, heart: new heart.HeartClient(options) };
});

Given("the employees service has changed: {word}", async function (change) {
    await sims.changeEmployees(change);
});

Given("the employees service is handicapped as Spring HATEOAS", async function () {
    await sims.handicapEmployees("spring");
});

When("it enters the employees service at its bookmark", async function () {
    this.rep = await this.me.heart.enter(`${employeesService}/`);
});

When("it enters the employees of the employees service", async function () {
    this.rep = await this.me.heart.enter(`${employeesService}/employees`);
});

When("it tries to enter the employees of the employees service", async function () {
    await trying(this, () => this.me.heart.enter(`${employeesService}/employees`));
});

When("it follows {string} with no title", async function (rel) {
    this.rep = await this.me.heart.follow(rel);
});

Then("it embeds these employees", function (table) {
    const held = this.me.heart.current.embedded.map((e) => ({ firstName: String(e.state.firstName), role: String(e.state.role) }));
    assert.deepEqual(held, table.hashes());
});

Then("the employees service holds these employees", async function (table) {
    assert.deepEqual((await sims.employees()).map((e) => ({ firstName: e.firstName })), table.hashes());
});
