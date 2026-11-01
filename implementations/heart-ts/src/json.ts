// Raw JSON meets the type system here. The rest of the client reads wire values
// through these narrowing functions, so a value of the wrong shape reads as absent.
export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export type JsonObject = { [key: string]: Json };

const isJson = (value: unknown): value is Json => {
    if (typeof value !== "object" || value === null) return ["boolean", "number", "string"].includes(typeof value) || value === null;
    return Object.values(value).every(isJson);
};

/** The one place raw text becomes a value; `JSON.parse` itself is untyped. */
export function parseJson(text: string): Json {
    const parsed: unknown = JSON.parse(text);
    if (!isJson(parsed)) throw new Error("not JSON");
    return parsed;
}

export const isObject = (value: Json | undefined): value is JsonObject =>
    typeof value === "object" && value !== null && !Array.isArray(value);

export const objectOf = (value: Json | undefined): JsonObject => (isObject(value) ? value : {});

export const stringOf = (value: Json | undefined): string | undefined => (typeof value === "string" ? value : undefined);

/** A HAL member: one value or a list of them. */
export const listOf = (value: Json | undefined): Json[] => {
    if (value === undefined) return [];
    return Array.isArray(value) ? value : [value];
};

export const stringsOf = (value: Json | undefined): string[] =>
    listOf(value).flatMap((item) => (typeof item === "string" ? [item] : []));

export const stringMembers = (value: Json | undefined): Record<string, string> =>
    Object.fromEntries(Object.entries(objectOf(value)).flatMap(([key, member]) => (typeof member === "string" ? [[key, member]] : [])));

function step(node: Json | undefined, token: string): Json | undefined {
    if (Array.isArray(node)) return node[Number(token)];
    return isObject(node) ? node[token] : undefined;
}

/** A JSON Pointer (RFC 6901) into a JSON value. */
export function resolve(value: Json, pointer: string): Json | undefined {
    if (pointer === "") return value;
    return pointer.slice(1).split("/").map((t) => t.replace(/~1/g, "/").replace(/~0/g, "~"))
        .reduce<Json | undefined>(step, value);
}
