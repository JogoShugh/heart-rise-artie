// Choices from state (HAL-SCHEMA-FORMS-HR Rule 8): deciding, with nothing read here.
import { Json, JsonObject, isObject, listOf, objectOf, stringOf, stringsOf } from "./json";

/** One choice for a property (HAL-SCHEMA-FORMS-HR Rule 8): the value sent, and the prompt shown. */
export interface Choice { value: Json; prompt: string }

/** What [misfit] needs of one property. */
interface PropertyCheck { property: string; sent: Json | undefined; required: boolean; choices: Choice[] }

/** The properties of [schema] that carry `hr:options`, with it. */
export function declared(schema: JsonObject): [string, JsonObject][] {
    return Object.entries(objectOf(schema.properties)).flatMap(([name, property]): [string, JsonObject][] => {
        const options = objectOf(property)["hr:options"];
        return isObject(options) ? [[name, options]] : [];
    });
}

export const requiredOf = (schema: JsonObject): Set<string> => new Set(stringsOf(schema.required));

function objectChoice(item: JsonObject, options: JsonObject): Choice[] {
    const value = stringOf(options.valueField) ?? "value";
    const prompt = stringOf(options.promptField) ?? "prompt";
    const matches = Object.entries(objectOf(options.where)).every(([k, v]) => JSON.stringify(item[k]) === JSON.stringify(v));
    const chosen = item[value];
    if (!matches || chosen === undefined) return [];
    return [{ value: chosen, prompt: String(item[prompt] ?? chosen) }];
}

function scalarChoice(item: Json, options: JsonObject): Choice[] {
    const scalar = Object.keys(objectOf(options.where)).length === 0 && item !== null && typeof item !== "object";
    return scalar ? [{ value: item, prompt: String(item) }] : [];
}

/** Only items equal to every member of `where` are choices. */
export function choicesAmong(items: Json | undefined, options: JsonObject): Choice[] {
    if (!Array.isArray(items)) return [];
    return items.flatMap((item) => (isObject(item) ? objectChoice(item, options) : scalarChoice(item, options)));
}

/** A sentence if a sent value is not among its choices, or a required property has none now. */
export function misfit({ property, sent, required, choices }: PropertyCheck): string | undefined {
    if (sent === undefined) return required && !choices.length ? `${property} has no choices now` : undefined;
    const allowed = new Set(choices.map((c) => JSON.stringify(c.value)));
    const outside = listOf(sent).find((v) => !allowed.has(JSON.stringify(v)));
    return outside === undefined ? undefined : `${property}: ${String(outside)} is not one of the choices`;
}
