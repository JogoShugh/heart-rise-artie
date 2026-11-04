// The only module that talks to the network. It never builds a URI of its own:
// targets come from forms, expanded as each form says.
import { Choice, choicesAmong, declared, misfit, requiredOf } from "./choices";
import { IntegrityFailure, NotAChoice, OutsideSchema, Refused } from "./errors";
import { templatesOf } from "./halforms";
import { Json, JsonObject, isObject, objectOf, parseJson, resolve, stringMembers, stringOf } from "./json";
import { needsRereading, refusalOf } from "./refusal";
import { addressOf, fill, fillPointer } from "./template";
import { FormChecks, SchemaFailure } from "./validation";
import { Dialect, Form, HAL_FORMS_PROFILE, HAL_FORMS_TYPE, JSON_TYPE, MEDIA_TYPE, PROFILE, Representation, Rung, embeddedOf, formOf, formsLinkOf, linksOf, matchesIntegrity, stateOf } from "./wire";
import { ACCEPT_PROFILE, AUTHORIZATION, CACHE_CONTROL, CONTENT_LOCATION, CONTENT_TYPE, ETAG, FORMS_BY_LINK, GET, HAL_FORMS, HR_INTEGRITY, IF_MATCH, IF_NONE_MATCH, L0, L1, L2, LINK_HEADER, MANUAL_REDIRECT, NOT_A_CHOICE, NO_CACHE, OUTSIDE_SCHEMA, PREFERENCE_APPLIED, PROFILE_REL, READ, SCHEMA_BY_LINK, UTF8 } from "./vocabulary";

/** Where shared layers are kept. A `Map` will do; pass something with a limit if a `Map` would grow too far. */
export interface LayerCache {
    get(key: string): JsonObject | undefined;
    set(key: string, layer: JsonObject): unknown;
}

export interface Options {
    rung?: Rung;
    /** The dialect to ask for. A client reads whichever one comes back. HAL-FORMS is served at L0 only. */
    dialect?: Dialect;
    /** An Authorization header value, sent only to the bookmark's own origin. */
    authorization?: string;
    /** Every request this client sends, for a test to inspect. */
    log?: { method: string; url: string; status: number; ifMatch?: string }[];
    /**
     * Shared layers read so far, by address and integrity, so none is read twice.
     * Give clients one map to share what they read, but only clients that may
     * all read the same layers: a client finds what another read, with no request.
     */
    setCache?: LayerCache;
    /** Schemas that could not be compiled, noted locally. The act goes to the service anyway. */
    schemaFailures?: SchemaFailure[];
}

const bytesOf = async (response: Response): Promise<Buffer> => Buffer.from(await response.arrayBuffer());
const objectFrom = (bytes: Buffer): JsonObject => objectOf(parseJson(bytes.toString(UTF8)));

const problemOf = async (response: Response): Promise<JsonObject> =>
    objectOf(await response.text().then(parseJson).catch(() => null));

/** The dialect a client asks for unless told otherwise, and the one compliance is judged in. */
const REFERENCE_DIALECT: Dialect = "hal-schema-forms";

/** The profiles an answer names: in its Link header, or, from a host that can't send one, in its own links (SPEC §6.1). */
function profilesOf(response: Response, body: JsonObject): string {
    const header = response.headers.get(LINK_HEADER);
    if (header !== null) return header;
    return linksOf(body).filter((link) => link.rel === PROFILE_REL).map((link) => `<${link.href}>`).join(", ");
}

/** The dialect of an answer. HAL-FORMS's own media type says so, as toolchains name no HEART profile. */
function dialectOf(url: string, response: Response, body: JsonObject): Dialect {
    const links = profilesOf(response, body);
    const type = response.headers.get(CONTENT_TYPE) ?? "";
    if (links.includes(`<${HAL_FORMS_PROFILE}>`) || type.startsWith(HAL_FORMS_TYPE)) return HAL_FORMS;
    if (links.includes(`<${PROFILE}>`)) return REFERENCE_DIALECT;
    throw new Error(`${url} does not name the profile ${PROFILE}`);
}

/** What each rung asks for (SPEC §6.4). */
const PREFER: Partial<Record<Rung, string>> = { L1: FORMS_BY_LINK, L2: "forms=link, schema=link" };

/** The rung of an answer: what it says it applied (SPEC §6.10), not what was asked. */
function appliedRung(response: Response, dialect: Dialect): Rung {
    const applied = (response.headers.get(PREFERENCE_APPLIED) ?? "").split(",").map((token) => token.trim());
    if (dialect !== REFERENCE_DIALECT || !applied.includes(FORMS_BY_LINK)) return L0;
    return applied.includes(SCHEMA_BY_LINK) ? L2 : L1;
}

/** Methods that carry no body. */
const DELETE = "DELETE";
const BODYLESS = [DELETE, "HEAD"];

/** Where an act's answer lives: the Content-Location it names, else the resource acted on. */
function answerUrl(response: Response, target: URL, heldUrl: string): string {
    const location = response.headers.get(CONTENT_LOCATION);
    return location ? new URL(location, target).toString() : heldUrl;
}

export class HeartClient {
    private readonly rung: Rung;
    private readonly dialect: Dialect;
    private readonly layers: LayerCache;
    private origin?: string;
    /** What the client last read of each plain JSON resource, to revalidate it with its ETag. */
    private readonly plainReads = new Map<string, { etag: string; body: Json }>();
    private readonly checks: FormChecks;
    current?: Representation;

    constructor(private readonly options: Options = {}) {
        this.rung = options.rung ?? L0;
        this.dialect = options.dialect ?? REFERENCE_DIALECT;
        this.layers = options.setCache ?? new Map<string, JsonObject>();
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
        const target = addressOf(form, held, fields);
        if (form.method === GET) return this.sync(target.toString());
        const response = await this.send(target.toString(), this.actRequest(form, held.etag, fields));
        if (!response.ok) throw await this.refusal(name, response);
        return this.answered(answerUrl(response, target, held.url), response, form.method === DELETE ? held : undefined);
    }

    /**
     * An act's answer is the resource's new representation; a service that sends none leaves it to be read,
     * except after a DELETE, which removed it: what was [deleted] is held as gone, and nothing is read.
     */
    private async answered(url: string, response: Response, deleted?: Representation): Promise<Representation> {
        const bytes = await bytesOf(response);
        if (bytes.length) return this.complete(url, response, bytes);
        if (deleted) return (this.current = { ...deleted, gone: true });
        return this.sync(url);
    }

    /** The conditional request for an act. A DELETE has no body: its fields filled its target or query (SPEC §6.2). */
    private actRequest(form: Form, etag: string, fields: JsonObject): RequestInit {
        const headers = { ...this.negotiation(), [IF_MATCH]: etag };
        if (BODYLESS.includes(form.method)) return { method: form.method, headers };
        return { method: form.method, headers: { ...headers, [CONTENT_TYPE]: form.contentType }, body: JSON.stringify(fields) };
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
            throw new NotAChoice({ kind: NOT_A_CHOICE, message: `${misfits.join("; ")}; nothing was sent`, status: 0, violations: misfits });
        }
        this.requireSchema(form, fields);
    }

    /** Refuses, sending nothing, what the form's declared schema disallows. */
    private requireSchema(form: Form, fields: JsonObject): void {
        const violations = this.checks.outsideSchema(form, fields);
        if (!violations.length) return;
        const message = `${form.name}: ${violations.join("; ")}; nothing was sent`;
        throw new OutsideSchema({ kind: OUTSIDE_SCHEMA, message, status: 0, violations });
    }

    private async resolveChoices(options: JsonObject, held: Representation, fields: JsonObject): Promise<Choice[]> {
        return choicesAmong(await this.choiceItems(options, held, fields), options);
    }

    private async choiceItems(options: JsonObject, held: Representation, fields: JsonObject): Promise<Json | undefined> {
        if (Array.isArray(options.inline)) return options.inline;
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
        const headers: Record<string, string> = { Accept: JSON_TYPE, [CACHE_CONTROL]: NO_CACHE };
        if (kept) headers[IF_NONE_MATCH] = kept.etag;
        const response = await this.send(url, { headers });
        if (response.status === 304 && kept) return kept.body;
        if (!response.ok) return undefined;
        const body = parseJson(await response.text());
        const etag = response.headers.get(ETAG);
        if (etag) this.plainReads.set(url, { etag, body });
        return body;
    }

    // ---- Sync: Layer 1, then, above L0, the affordance set and its schemas ----

    private async sync(url: string): Promise<Representation> {
        const response = await this.send(url, { headers: { ...this.negotiation(), [CACHE_CONTROL]: NO_CACHE } });
        if (!response.ok) throw await this.refusal(READ, response);
        return this.complete(url, response, await bytesOf(response));
    }

    private async complete(url: string, response: Response, bytes: Buffer): Promise<Representation> {
        const body = objectFrom(bytes);
        const dialect = dialectOf(url, response, body);
        const rung = appliedRung(response, dialect);
        const links = linksOf(body);
        const forms = dialect === HAL_FORMS ? templatesOf(body, url) : await this.formsAt(url, body, rung);
        const etag = response.headers.get(ETAG) ?? "";
        this.current = { url, etag, rung, dialect, state: stateOf(body), forms, links, embedded: embeddedOf(body) };
        return this.current;
    }

    private async formsAt(url: string, body: JsonObject, rung: Rung): Promise<Map<string, Form>> {
        return this.forms(rung === L0 ? body : await this.affordanceSet(url, body, rung), url);
    }

    private async affordanceSet(url: string, body: JsonObject, rung: Rung): Promise<JsonObject> {
        const set = formsLinkOf(body);
        if (!set) throw new Error(`${url} applied ${rung} but links no affordance set`);
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
        return this.shared(new URL(schema.$ref, base).toString(), stringOf(schema[HR_INTEGRITY]));
    }

    /** A shared layer, fetched only through a link that carries its integrity, and checked before use. */
    private async shared(url: string, integrity: string | undefined): Promise<JsonObject> {
        if (!integrity) throw new IntegrityFailure(`${url} was linked without an integrity value; nothing was sent`);
        const key = `${integrity} ${url}`;
        const layer = this.layers.get(key) ?? await this.verified(url, integrity);
        this.layers.set(key, layer);
        return layer;
    }

    /** A shared layer read from the service, refused unless its bytes match. */
    private async verified(url: string, integrity: string): Promise<JsonObject> {
        const response = await this.send(url, { headers: { Accept: `${MEDIA_TYPE}, application/schema+json, ${JSON_TYPE}` } });
        const bytes = await bytesOf(response);
        if (!matchesIntegrity(bytes, integrity)) throw new IntegrityFailure(`${url} does not match the integrity of the link that led to it`);
        return objectFrom(bytes);
    }

    // ---- The wire ----

    private layering(): Record<string, string> {
        const prefer = PREFER[this.rung];
        return this.dialect === REFERENCE_DIALECT && prefer ? { Prefer: prefer } : {};
    }

    private negotiation(): Record<string, string> {
        return {
            ...(this.dialect === HAL_FORMS
                ? { Accept: `${HAL_FORMS_TYPE}, ${MEDIA_TYPE};q=0.5`, [ACCEPT_PROFILE]: `<${HAL_FORMS_PROFILE}>` }
                : { Accept: MEDIA_TYPE, [ACCEPT_PROFILE]: `<${PROFILE}>` }),
            ...this.layering(),
        };
    }

    private async send(url: string, init: RequestInit): Promise<Response> {
        const headers = new Headers(init.headers);
        if (this.options.authorization && new URL(url).origin === this.origin) {
            headers.set(AUTHORIZATION, this.options.authorization);
        }
        const response = await fetch(url, { ...init, headers, redirect: MANUAL_REDIRECT });
        this.options.log?.push({
            method: init.method ?? GET, url, status: response.status, ifMatch: headers.get(IF_MATCH) ?? undefined,
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
