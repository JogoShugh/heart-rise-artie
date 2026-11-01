// Templates: pointer, then field, then the owning resource's member.
import { JsonObject, listOf, resolve } from "./json";
import { Form } from "./wire";

/** Where a template's variables are found. */
export interface TemplateScope { pointers: Record<string, string>; state: JsonObject; fields: JsonObject }

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
export function expand(form: Form, state: JsonObject, fields: JsonObject): string {
    return form.templated ? fill(form.target, { pointers: form.templatePointers, state, fields }) : form.target;
}

/** Adds to a safe form's [target] each field its template did not take. */
export function withQuery(target: URL, form: Form, fields: JsonObject): URL {
    for (const [key, value] of Object.entries(fields)) {
        if (!form.templated || !form.target.includes(`{${key}}`)) target.searchParams.set(key, String(value));
    }
    return target;
}

/** A pointer whose {name}s are other fields of the form, escaped as RFC 6901 asks. */
export function fillPointer(pointer: string, fields: JsonObject): string {
    return pointer.replace(VARIABLE, (_, field: string) =>
        String(fields[field] ?? "").replace(/~/g, "~0").replace(/\//g, "~1"));
}
