// The only module that talks to the network. It never builds a URI of its own:
// targets come from forms, expanded as each form says.
import { Choice, choicesAmong, declared, misfit, requiredOf } from "./choices";
import { IntegrityFailure, NotAChoice, OutsideSchema, Refused } from "./errors";
import { Json, JsonObject, isObject, objectOf, parseJson, resolve, stringMembers, stringOf } from "./json";
import { needsRereading, refusalOf } from "./refusal";
import { expand, fill, fillPointer, withQuery } from "./template";
import { FormChecks, SchemaFailure } from "./validation";
import { Form, Link, MEDIA_TYPE, PROFILE, Representation, Rung, embeddedOf, formOf, linksOf, matchesIntegrity, stateOf } from "./wire";

export interface Options {
    rung?: Rung;
    /** An Authorization header value, sent only to the bookmark's own origin. */
    authorization?: string;
    /** Every request this client sends, for a test to inspect. */
    log?: { method: string; url: string; status: number; ifMatch?: string }[];
    /** Schemas that could not be compiled, noted locally. The act goes to the service anyway. */
    schemaFailures?: SchemaFailure[];
}

const bytesOf = async (response: Response): Promise<Buffer> => Buffer.from(await response.arrayBuffer());
const objectFrom = (bytes: Buffer): JsonObject => objectOf(parseJson(bytes.toString("utf8")));

const problemOf = async (response: Response): Promise<JsonObject> =>
    objectOf(await response.text().then(parseJson).catch(() => null));

function requireProfile(url: string, response: Response): void {
    const links = response.headers.get("Link") ?? "";
    if (!links.includes(`<${PROFILE}>`)) throw new Error(`${url} does not name the profile ${PROFILE}`);
}

const appliedRung = (response: Response): Rung =>
    ((response.headers.get("Preference-Applied") ?? "").includes("forms=link") ? "L2" : "L0");

/** Where an act's answer lives: the Content-Location it names, else the resource acted on. */
function answerUrl(response: Response, target: URL, heldUrl: string): string {
    const location = response.headers.get("Content-Location");
    return location ? new URL(location, target).toString() : heldUrl;
}

export class HeartClient {
    private readonly rung: Rung;
    private origin?: string;
    /** What the client last read of each plain JSON resource, to revalidate it with its ETag. */
    private readonly plainReads = new Map<string, { etag: string; body: Json }>();
    private readonly checks: FormChecks;
    current?: Representation;

    constructor(private readonly options: Options = {}) {
        this.rung = options.rung ?? "L0";
        this.checks = new FormChecks(options.schemaFailures);
    }

    /** Enter a service at its bookmark. */
    async enter(url: string): Promise<Representation> {
        this.origin = new URL(url).origin;
        return this.sync(url);
    }

    /** Re-read the current resource. */
    async refresh(): Promise<Representation> {
        return this.sync(this.held().url);
    }

    /** Follow a link the representation offers, or a resource it embeds, by relation and title. */
    async follow(rel: string, title?: string): Promise<Representation> {
        const matches = (l: { rel: string; title?: string }) => l.rel === rel && (title === undefined || l.title === title);
        const link = this.held().links.find(matches) ?? this.held().embedded.find(matches);
        if (!link) throw new Error(`no ${rel} link${title ? ` titled ${title}` : ""} is offered; nothing was sent`);
        return this.sync(new URL(link.href, this.held().url).toString());
    }

    /**
     * Submit an offered form. A safe form (GET) reads the resource its filled
     * target names. Any other is an act, conditional on the representation held.
     */
    async act(name: string, fields: JsonObject): Promise<Representation> {
        const held = this.held();
        const form = held.forms.get(name);
        if (!form) throw new Error(`${name} is not offered now; nothing was sent`);
        await this.admit(form, held, fields);
        const target = new URL(expand(form, held.state, fields), held.url);
        if (form.method === "GET") return this.sync(withQuery(target, form, fields).toString());
        const response = await this.send(target.toString(), this.actRequest(form, held.etag, fields));
        if (!response.ok) throw await this.refusal(name, response);
        return this.complete(answerUrl(response, target, held.url), response, await bytesOf(response));
    }

    /** The conditional request for an act. A DELETE has no body: its fields filled its target's query (SPEC §6.2). */
    private actRequest(form: Form, etag: string, fields: JsonObject): RequestInit {
        const headers = { ...this.negotiation(), "If-Match": etag };
        if (form.method === "DELETE") return { method: form.method, headers };
        return { method: form.method, headers: { ...headers, "Content-Type": form.contentType }, body: JSON.stringify(fields) };
    }

    // ---- Choices from state (HAL-SCHEMA-FORMS-HR Rule 8) ----

    /**
     * The choices of each property of the offered form [name] that carries
     * `hr:options`, with [fields] filled so far: from the held resource, at a
     * pointer that may name other fields, or from the resource a link names,
     * read like any Layer 1 resource. Only items equal to every member of
     * `where` are choices.
     */
    async choices(name: string, fields: JsonObject = {}): Promise<Record<string, Choice[]>> {
        const held = this.held();
        const form = held.forms.get(name);
        if (!form) throw new Error(`${name} is not offered now`);
        const result: Record<string, Choice[]> = {};
        for (const [property, options] of declared(form.schema)) {
            result[property] = await this.resolveChoices(options, held, fields);
        }
        return result;
    }

    /** Refuses, sending nothing, when any sent value is not among its choices or a required one has none now. */
    private async admit(form: Form, held: Representation, fields: JsonObject): Promise<void> {
        const required = requiredOf(form.schema);
        const misfits: string[] = [];
        for (const [property, options] of declared(form.schema)) {
            const choices = await this.resolveChoices(options, held, fields);
            const found = misfit({ property, sent: fields[property], required: required.has(property), choices });
            if (found) misfits.push(found);
        }
        if (misfits.length) {
            throw new NotAChoice({ kind: "not-a-choice", message: `${misfits.join("; ")}; nothing was sent`, status: 0, violations: misfits });
        }
        this.requireSchema(form, fields);
    }

    /** Refuses, sending nothing, what the form's declared schema disallows. */
    private requireSchema(form: Form, fields: JsonObject): void {
        const violations = this.checks.outsideSchema(form, fields);
        if (!violations.length) return;
        const message = `${form.name}: ${violations.join("; ")}; nothing was sent`;
        throw new OutsideSchema({ kind: "outside-schema", message, status: 0, violations });
    }

    private async resolveChoices(options: JsonObject, held: Representation, fields: JsonObject): Promise<Choice[]> {
        return choicesAmong(await this.choiceItems(options, held, fields), options);
    }

    private async choiceItems(options: JsonObject, held: Representation, fields: JsonObject): Promise<Json | undefined> {
        if (typeof options.pointer === "string") return resolve(held.state, fillPointer(options.pointer, fields));
        if (isObject(options.link)) return this.linkedItems(options, held, fields);
        return undefined;
    }

    private async linkedItems(options: JsonObject, held: Representation, fields: JsonObject): Promise<Json | undefined> {
        const link = objectOf(options.link);
        const pointers = stringMembers(options.templatePointers);
        const href = link.templated === true ? fill(String(link.href), { pointers, state: held.state, fields }) : String(link.href);
        const answer = await this.plain(new URL(href, held.url).toString());
        return typeof options.items === "string" && answer !== undefined ? resolve(answer, options.items) : answer;
    }

    /** A plain JSON resource, read as Layer 1 is (SPEC §6.1): no-cache, revalidated by its ETag. */
    private async plain(url: string): Promise<Json | undefined> {
        const kept = this.plainReads.get(url);
        const headers: Record<string, string> = { Accept: "application/json", "Cache-Control": "no-cache" };
        if (kept) headers["If-None-Match"] = kept.etag;
        const response = await this.send(url, { headers });
        if (response.status === 304 && kept) return kept.body;
        if (!response.ok) return undefined;
        const body = parseJson(await response.text());
        const etag = response.headers.get("ETag");
        if (etag) this.plainReads.set(url, { etag, body });
        return body;
    }

    // ---- Sync: Layer 1, then, above L0, the affordance set and its schemas ----

    private async sync(url: string): Promise<Representation> {
        const response = await this.send(url, { headers: { ...this.negotiation(), "Cache-Control": "no-cache" } });
        if (!response.ok) throw await this.refusal("read", response);
        return this.complete(url, response, await bytesOf(response));
    }

    private async complete(url: string, response: Response, bytes: Buffer): Promise<Representation> {
        requireProfile(url, response);
        const body = objectFrom(bytes);
        const rung = appliedRung(response);
        const links = linksOf(body);
        const formsBody = rung === "L0" ? body : await this.affordanceSet(url, links);
        const forms = await this.forms(formsBody, url);
        const etag = response.headers.get("ETag") ?? "";
        this.current = { url, etag, rung, state: stateOf(body), forms, links, embedded: embeddedOf(body) };
        return this.current;
    }

    private async affordanceSet(url: string, links: Link[]): Promise<JsonObject> {
        const set = links.find((l) => l.rel === "hr:forms");
        if (!set) throw new Error(`${url} applied L2 but links no affordance set`);
        return this.shared(new URL(set.href, url).toString(), set.integrity);
    }

    private async forms(body: JsonObject, base: string): Promise<Map<string, Form>> {
        const forms = new Map<string, Form>();
        for (const [name, value] of Object.entries(objectOf(body._forms))) {
            const raw = objectOf(value);
            forms.set(name, formOf(name, raw, await this.schema(raw, base)));
        }
        return forms;
    }

    private async schema(raw: JsonObject, base: string): Promise<JsonObject> {
        const schema = objectOf(raw.schema);
        if (typeof schema.$ref !== "string") return schema;
        return this.shared(new URL(schema.$ref, base).toString(), stringOf(schema["hr:integrity"]));
    }

    /** A shared layer, fetched only through a link that carries its integrity, and checked before use. */
    private async shared(url: string, integrity: string | undefined): Promise<JsonObject> {
        if (!integrity) throw new IntegrityFailure(`${url} was linked without an integrity value; nothing was sent`);
        const response = await this.send(url, { headers: { Accept: `${MEDIA_TYPE}, application/schema+json, application/json` } });
        const bytes = await bytesOf(response);
        if (!matchesIntegrity(bytes, integrity)) throw new IntegrityFailure(`${url} does not match the integrity of the link that led to it`);
        return objectFrom(bytes);
    }

    // ---- The wire ----

    private negotiation(): Record<string, string> {
        return {
            Accept: MEDIA_TYPE,
            "Accept-Profile": `<${PROFILE}>`,
            ...(this.rung === "L2" ? { Prefer: "forms=link, schema=link" } : {}),
        };
    }

    private async send(url: string, init: RequestInit): Promise<Response> {
        const headers = new Headers(init.headers);
        if (this.options.authorization && new URL(url).origin === this.origin) {
            headers.set("Authorization", this.options.authorization);
        }
        const response = await fetch(url, { ...init, headers, redirect: "manual" });
        this.options.log?.push({
            method: init.method ?? "GET", url, status: response.status, ifMatch: headers.get("If-Match") ?? undefined,
        });
        return response;
    }

    private async refusal(name: string, response: Response): Promise<Refused> {
        const problem = await problemOf(response);
        if (needsRereading(response.status)) await this.refresh();
        return refusalOf({ name, status: response.status, statusText: response.statusText, problem });
    }

    private held(): Representation {
        if (!this.current) throw new Error("enter a service first");
        return this.current;
    }
}
