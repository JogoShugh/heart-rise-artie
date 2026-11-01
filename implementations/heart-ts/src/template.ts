// Templates: pointer, then field, then the owning resource's member.
import { JsonObject, listOf, resolve } from "./json";
import { Form } from "./wire";
import { GET, HAL_FORMS } from "./vocabulary";

/** Where a template's variables are found. */
interface TemplateScope { pointers: Record<string, string>; state: JsonObject; fields: JsonObject }

const VARIABLE = /\{([^}]+)\}/g;

function queryPairs(key: string, scope: TemplateScope): string[] {
    const pointer = scope.pointers[key];
    const value = pointer !== undefined ? resolve(scope.state, pointer) : scope.fields[key];
    if (value === null) return [];
    return listOf(value).map((v) => `${encodeURIComponent(key)}=${encodeURIComponent(String(v))}`);
}

/** A query expression ({?tag,count}): each value found, by name; none found, nothing. */
function queryExpression(names: string, scope: TemplateScope): string {
    const pairs = names.split(",").flatMap((key) => queryPairs(key, scope));
    return pairs.length ? `?${pairs.join("&")}` : "";
}

function variable(name: string, scope: TemplateScope): string {
    const pointer = scope.pointers[name];
    const value = pointer !== undefined ? resolve(scope.state, pointer) : scope.fields[name] ?? scope.state[name];
    if (typeof value !== "string" && typeof value !== "number") {
        throw new Error(`the template variable ${name} has no string or number value; nothing was sent`);
    }
    return encodeURIComponent(String(value));
}

/**
 * A template filled as Rule 1 fills a target: each variable from its template
 * pointer into the state, else from the fields, else from the state's member.
 */
export function fill(template: string, scope: TemplateScope): string {
    return template.replace(VARIABLE, (_, name: string) =>
        (name.startsWith("?") ? queryExpression(name.slice(1), scope) : variable(name, scope)));
}

/** A form's filled target, relative or not, as the form says. */
function expand(form: Form, state: JsonObject, fields: JsonObject): string {
    return form.templated ? fill(form.target, { pointers: form.templatePointers, state, fields }) : form.target;
}

/** The names a template takes, from plain variables and query expressions alike. */
const namesIn = (template: string): string[] =>
    [...template.matchAll(VARIABLE)].flatMap((match) => (match[1] ?? "").replace("?", "").split(","));

/** Adds to a safe form's [target] each field its template did not take. */
function withQuery(target: URL, form: Form, fields: JsonObject): URL {
    const taken = form.templated ? namesIn(form.target) : [];
    for (const [key, value] of Object.entries(fields)) {
        if (!taken.includes(key)) target.searchParams.set(key, String(value));
    }
    return target;
}

const QUERY_METHODS = [GET, "HEAD", "DELETE"];

/** Whether a form's fields leave in its query. HAL-FORMS sends a DELETE's as it sends a GET's. */
const fieldsInQuery = (form: Form): boolean =>
    (form.dialect === HAL_FORMS ? QUERY_METHODS.includes(form.method) : form.method === GET);

/** Where an act on [form] goes. HAL-FORMS replaces the target's query, as an HTML form does. */
export function addressOf(form: Form, held: { url: string; state: JsonObject }, fields: JsonObject): URL {
    const target = new URL(expand(form, held.state, fields), held.url);
    if (!fieldsInQuery(form)) return target;
    if (form.dialect === HAL_FORMS) target.search = "";
    return withQuery(target, form, fields);
}

/** A pointer whose {name}s are other fields of the form, escaped as RFC 6901 asks. */
export function fillPointer(pointer: string, fields: JsonObject): string {
    return pointer.replace(VARIABLE, (_, field: string) =>
        String(fields[field] ?? "").replace(/~/g, "~0").replace(/\//g, "~1"));
}
