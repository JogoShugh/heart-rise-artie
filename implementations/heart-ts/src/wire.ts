// The wire format parsed into types: HAL links, embedded resources, forms and problems.
import { createHash } from "node:crypto";
import { Json, JsonObject, isObject, listOf, objectOf, stringMembers, stringOf } from "./json";
import { BASE64, HAL_SCHEMA_FORMS, HR_INTEGRITY, HR_TEMPLATE_POINTERS, POST, SHA256, REL_VARIABLE } from "./vocabulary";

export const PROFILE = "https://w3id.org/heart-rise/hal-schema-forms";
export const PROBLEMS = "https://w3id.org/heart-rise/problems/";
export const MEDIA_TYPE = "application/hal+json";
export const HAL_FORMS_PROFILE = "https://w3id.org/heart-rise/hal-forms";
export const HAL_FORMS_TYPE = "application/prs.hal-forms+json";
export const JSON_TYPE = "application/json";

export type Rung = "L0" | "L1" | "L2";
/** "json" is no dialect: an answer that names no profile, held as data only (SPEC §6.1). */
export type Dialect = "hal-schema-forms" | "hal-forms" | "json";

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
    /** Identifies the schema: its integrity value if linked, else a digest of it. */
    schemaKey: string;
    dialect: Dialect;
}

export interface Link { rel: string; href: string; title?: string; integrity?: string }

/**
 * A resource another embeds (SPEC §6.1): its relation, its self link's address
 * and title, its state and its own links. It carries no forms and no validator:
 * to act on it, a client follows it, which reads it.
 */
export interface Embedded { rel: string; href: string; title?: string; state: JsonObject; links: Link[] }

export interface Representation {
    url: string;
    etag: string;
    rung: Rung;
    dialect: Dialect;
    state: JsonObject;
    forms: Map<string, Form>;
    links: Link[];
    embedded: Embedded[];
    /** Removed by a DELETE answered with no body: held as it was last read, and not read again. */
    gone?: true;
}

/** SPEC §6.1: the state is the body without exactly these four members; any other member is state. */
const CONTROLS = new Set(["_links", "_embedded", "_forms", "_templates"]);

export function stateOf(body: JsonObject): JsonObject {
    return Object.fromEntries(Object.entries(body).filter(([key]) => !CONTROLS.has(key)));
}

function linksUnder(rel: string, value: Json): Link[] {
    return listOf(value).flatMap((link) => {
        if (!isObject(link) || typeof link.href !== "string") return [];
        return [{ rel, href: link.href, title: stringOf(link.title), integrity: stringOf(link.integrity) }];
    });
}

export function linksOf(body: JsonObject): Link[] {
    return Object.entries(objectOf(body._links)).flatMap(([rel, value]) => linksUnder(rel, value));
}

/** The relation an affordance set is linked under (SPEC §6.1). A body may write it as a CURIE of any prefix. */
const FORMS_RELATION = "https://w3id.org/heart-rise/rels/forms";
const USUAL_FORMS_LINK = "hr:forms";

function relationOf(rel: string, body: JsonObject): string {
    const colon = rel.indexOf(":");
    const curie = listOf(objectOf(body._links).curies).map(objectOf).find((c) => c.name === rel.slice(0, colon));
    const href = stringOf(curie?.href);
    if (href !== undefined) return href.replace(REL_VARIABLE, rel.slice(colon + 1));
    return rel === USUAL_FORMS_LINK ? FORMS_RELATION : rel;
}

export function formsLinkOf(body: JsonObject): Link | undefined {
    return linksOf(body).find((link) => relationOf(link.rel, body) === FORMS_RELATION);
}

function embeddedResource(rel: string, resource: Json): Embedded[] {
    const self = isObject(resource) ? objectOf(resource._links).self : undefined;
    if (!isObject(resource) || !isObject(self) || typeof self.href !== "string") return [];
    return [{ rel, href: self.href, title: stringOf(self.title), state: stateOf(resource), links: linksOf(resource) }];
}

/** Each resource object under `_embedded`, by relation; one without a self link names no resource. */
export function embeddedOf(body: JsonObject): Embedded[] {
    return Object.entries(objectOf(body._embedded))
        .flatMap(([rel, value]) => listOf(value).flatMap((resource) => embeddedResource(rel, resource)));
}

/** A form as sent, with [schema] already read through any reference it carries. */
export function formOf(name: string, raw: JsonObject, schema: JsonObject): Form {
    const target = objectOf(objectOf(raw._links).target);
    return {
        name,
        title: stringOf(raw.title),
        description: stringOf(raw.description),
        target: String(target.href),
        templated: target.templated === true,
        templatePointers: stringMembers(raw[HR_TEMPLATE_POINTERS]),
        method: String(raw.method ?? POST),
        contentType: String(raw.contentType ?? JSON_TYPE),
        schema,
        schemaKey: schemaKeyOf(objectOf(raw.schema)),
        dialect: HAL_SCHEMA_FORMS,
    };
}

export const schemaKeyOf = (declared: JsonObject): string =>
    stringOf(declared[HR_INTEGRITY]) ?? createHash(SHA256).update(JSON.stringify(declared)).digest(BASE64);

/** Whether [bytes] are what an integrity value such as `sha256-...` names. */
export function matchesIntegrity(bytes: Buffer, integrity: string): boolean {
    const dash = integrity.indexOf("-");
    return createHash(integrity.slice(0, dash)).update(bytes).digest(BASE64) === integrity.slice(dash + 1);
}

/** Each violation is a JSON Pointer to the offending value and a message (the draft, Conditional Acts). */
export function violationsOf(problem: JsonObject): string[] {
    return listOf(problem.violations).map(objectOf)
        .map((v) => `${stringOf(v.pointer) ?? ""} ${stringOf(v.message) ?? stringOf(v.detail) ?? ""}`.trim());
}
