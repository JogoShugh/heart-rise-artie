// heart-ts: a second HEART client, written from the specification
// (spec/SPEC.md, spec/HAL-SCHEMA-FORMS-HR.md) and the Internet-Draft
// (standards/draft-gough-hal-schema-forms-heart-00.md), not ported from ARTIE.
//
// It does what a HEART client must, and no more: enter at a bookmark; read a
// resource's state and its legal forms at L0 or L2, checking every shared layer
// against the integrity value of the link that led to it; act with If-Match;
// tell the refusals apart; and follow links by relation and title. It never
// builds a URI of its own: targets come from forms, expanded as each form says.
import { createHash } from "node:crypto";

export const PROFILE = "https://w3id.org/heart-rise/hal-schema-forms";
const PROBLEMS = "https://w3id.org/heart-rise/problems/";
const MEDIA_TYPE = "application/hal+json";

export type Rung = "L0" | "L2";
export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
type JsonObject = { [key: string]: Json };

export interface Form {
    name: string;
    title?: string;
    description?: string;
    target: string;
    templated: boolean;
    templatePointers: Record<string, string>;
    method: string;
    contentType: string;
    schema: JsonObject;
}

export interface Link { rel: string; href: string; title?: string; integrity?: string }

export interface Representation {
    url: string;
    etag: string;
    rung: Rung;
    state: JsonObject;
    forms: Map<string, Form>;
    links: Link[];
}

/** Every refusal names what happened; none carries a credential. */
export class Refused extends Error {
    constructor(readonly kind: string, message: string, readonly status: number, readonly violations: string[] = []) {
        super(message);
    }
}
export class StaleAct extends Refused {}
export class NotOffered extends Refused {}
export class InvalidInput extends Refused {}
export class RuleRefused extends Refused {}
export class PreconditionRequired extends Refused {}
export class NotAuthenticated extends Refused {}
export class IntegrityFailure extends Error {}

export interface Options {
    rung?: Rung;
    /** An Authorization header value, sent only to the bookmark's own origin. */
    authorization?: string;
    /** Every request this client sends, for a test to inspect. */
    log?: { method: string; url: string; status: number; ifMatch?: string }[];
}

export class HeartClient {
    private readonly rung: Rung;
    private origin?: string;
    current?: Representation;

    constructor(private readonly options: Options = {}) {
        this.rung = options.rung ?? "L0";
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

    /** Follow a link the representation offers, by relation and title. */
    async follow(rel: string, title?: string): Promise<Representation> {
        const link = this.held().links.find((l) => l.rel === rel && (title === undefined || l.title === title));
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
        const target = new URL(this.expand(form, held, fields), held.url);
        if (form.method === "GET") {
            for (const [key, value] of Object.entries(fields)) {
                if (!form.templated || !form.target.includes(`{${key}}`)) target.searchParams.set(key, String(value));
            }
            return this.sync(target.toString());
        }
        const response = await this.send(target.toString(), {
            method: form.method,
            headers: { ...this.negotiation(), "Content-Type": form.contentType, "If-Match": held.etag },
            body: JSON.stringify(fields),
        });
        if (response.ok) {
            const url = response.headers.get("Content-Location")
                ? new URL(response.headers.get("Content-Location")!, target).toString()
                : held.url;
            return this.complete(url, response, Buffer.from(await response.arrayBuffer()));
        }
        throw await this.refusal(name, response);
    }

    // ---- Sync: Layer 1, then, above L0, the affordance set and its schemas ----

    private async sync(url: string): Promise<Representation> {
        const response = await this.send(url, { headers: { ...this.negotiation(), "Cache-Control": "no-cache" } });
        if (!response.ok) throw await this.refusal("read", response);
        return this.complete(url, response, Buffer.from(await response.arrayBuffer()));
    }

    private async complete(url: string, response: Response, bytes: Buffer): Promise<Representation> {
        const links = response.headers.get("Link") ?? "";
        if (!links.includes(`<${PROFILE}>`)) throw new Error(`${url} does not name the profile ${PROFILE}`);
        const body = JSON.parse(bytes.toString("utf8")) as JsonObject;
        const applied = (response.headers.get("Preference-Applied") ?? "").includes("forms=link") ? "L2" : "L0";
        const linked = this.links(body);
        let formsBody = body;
        if (applied !== "L0") {
            const set = linked.find((l) => l.rel === "hr:forms");
            if (!set) throw new Error(`${url} applied ${applied} but links no affordance set`);
            formsBody = await this.shared(new URL(set.href, url).toString(), set.integrity);
        }
        const forms = new Map<string, Form>();
        for (const [name, raw] of Object.entries((formsBody._forms ?? {}) as JsonObject)) {
            forms.set(name, await this.form(name, raw as JsonObject, url));
        }
        const state = Object.fromEntries(Object.entries(body).filter(([key]) => !key.startsWith("_")));
        this.current = { url, etag: response.headers.get("ETag") ?? "", rung: applied, state, forms, links: linked };
        return this.current;
    }

    private async form(name: string, raw: JsonObject, base: string): Promise<Form> {
        const target = ((raw._links as JsonObject)?.target ?? {}) as JsonObject;
        let schema = (raw.schema ?? {}) as JsonObject;
        if (typeof schema.$ref === "string") {
            schema = await this.shared(new URL(schema.$ref, base).toString(), schema["hr:integrity"] as string | undefined);
        }
        return {
            name,
            title: raw.title as string | undefined,
            description: raw.description as string | undefined,
            target: String(target.href),
            templated: target.templated === true,
            templatePointers: (raw["hr:templatePointers"] ?? {}) as Record<string, string>,
            method: String(raw.method ?? "POST"),
            contentType: String(raw.contentType ?? "application/json"),
            schema,
        };
    }

    /** A shared layer, fetched only through a link that carries its integrity, and checked before use. */
    private async shared(url: string, integrity: string | undefined): Promise<JsonObject> {
        if (!integrity) throw new IntegrityFailure(`${url} was linked without an integrity value; nothing was sent`);
        const response = await this.send(url, { headers: { Accept: `${MEDIA_TYPE}, application/schema+json, application/json` } });
        const bytes = Buffer.from(await response.arrayBuffer());
        const [algorithm, expected] = [integrity.slice(0, integrity.indexOf("-")), integrity.slice(integrity.indexOf("-") + 1)];
        const actual = createHash(algorithm).update(bytes).digest("base64");
        if (actual !== expected) throw new IntegrityFailure(`${url} does not match the integrity of the link that led to it`);
        return JSON.parse(bytes.toString("utf8")) as JsonObject;
    }

    // ---- Templates: pointer, then field, then the owning resource's member ----

    private expand(form: Form, held: Representation, fields: JsonObject): string {
        if (!form.templated) return form.target;
        return form.target.replace(/\{([^}]+)\}/g, (_, name: string) => {
            if (name.startsWith("?")) return "";
            const pointer = form.templatePointers[name];
            const value = pointer !== undefined ? resolve(held.state, pointer) : fields[name] ?? held.state[name];
            if (typeof value !== "string" && typeof value !== "number") {
                throw new Error(`the template variable ${name} has no string or number value; nothing was sent`);
            }
            return encodeURIComponent(String(value));
        });
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
        const problem = (await response.json().catch(() => ({}))) as JsonObject;
        const type = String(problem.type ?? "").replace(PROBLEMS, "");
        // Each violation is a JSON Pointer to the offending value and a message (the draft, Conditional Acts).
        const violations = ((problem.violations ?? []) as JsonObject[])
            .map((v) => `${v.pointer ?? ""} ${v.message ?? v.detail ?? ""}`.trim());
        const detail = String(problem.detail ?? problem.title ?? response.statusText);
        const said = `${name} was refused (${response.status}): ${detail}`;
        switch (response.status) {
            case 412: await this.refresh(); return new StaleAct("stale", `${said}; re-read, not resent`, 412);
            case 409: await this.refresh(); return new NotOffered("not-offered", said, 409);
            case 428: return new PreconditionRequired("precondition-required", said, 428);
            case 401: return new NotAuthenticated("not-authenticated", said, 401);
            case 422: return type === "rule-refused"
                ? new RuleRefused("rule-refused", said, 422, violations)
                : new InvalidInput("invalid-input", said, 422, violations);
            default: return new Refused("failed", said, response.status);
        }
    }

    private links(body: JsonObject): Link[] {
        const result: Link[] = [];
        for (const [rel, value] of Object.entries((body._links ?? {}) as JsonObject)) {
            for (const link of (Array.isArray(value) ? value : [value]) as JsonObject[]) {
                if (link && typeof link.href === "string") {
                    result.push({ rel, href: link.href, title: link.title as string | undefined, integrity: link.integrity as string | undefined });
                }
            }
        }
        return result;
    }

    private held(): Representation {
        if (!this.current) throw new Error("enter a service first");
        return this.current;
    }
}

/** A JSON Pointer (RFC 6901) into a JSON value. */
function resolve(value: Json, pointer: string): Json | undefined {
    if (pointer === "") return value;
    return pointer.slice(1).split("/").map((t) => t.replace(/~1/g, "/").replace(/~0/g, "~"))
        .reduce<Json | undefined>((node, token) => (node && typeof node === "object" ? (node as JsonObject)[token] : undefined), value);
}
