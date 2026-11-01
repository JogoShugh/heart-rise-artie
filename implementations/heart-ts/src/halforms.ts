// HAL-FORMS, the L0 on-ramp (SPEC §6.1): `_templates` read into the same Form
// the reference dialect gives, with each properties array as a JSON Schema.
// A property's `type` stays out of that schema: it is a hint for a person
// filling in a form, and a checkbox's value is no string.
import { Json, JsonObject, isObject, listOf, objectOf, stringOf } from "./json";
import { Form, JSON_TYPE, schemaKeyOf } from "./wire";
import { GET, HAL_FORMS, HR_OPTIONS, OBJECT_TYPE } from "./vocabulary";

/** HAL-FORMS property member -> JSON Schema keyword, where they mean the same. */
const KEYWORDS = new Map([["min", "minimum"], ["max", "maximum"], ["minLength", "minLength"], ["maxLength", "maxLength"], ["regex", "pattern"]]);

/** Choices written as HAL-FORMS `options` read as `hr:options` do; the names are shared. */
function propertySchema(property: JsonObject): JsonObject {
    const schema: JsonObject = {};
    for (const [from, to] of KEYWORDS) {
        const value = property[from];
        if (value !== undefined) schema[to] = value;
    }
    if (isObject(property.options)) schema[HR_OPTIONS] = property.options;
    return schema;
}

const named = (properties: Json | undefined): [string, JsonObject][] =>
    listOf(properties).flatMap((property): [string, JsonObject][] =>
        (isObject(property) && typeof property.name === "string" ? [[property.name, property]] : []));

function schemaOf(properties: Json | undefined): JsonObject {
    const entries = named(properties);
    return {
        type: OBJECT_TYPE,
        properties: Object.fromEntries(entries.map(([name, property]) => [name, propertySchema(property)])),
        required: entries.filter(([, property]) => property.required === true).map(([name]) => name),
    };
}

function templateForm(name: string, raw: JsonObject, resource: string): Form {
    const schema = schemaOf(raw.properties);
    return {
        name,
        title: stringOf(raw.title),
        description: undefined,
        target: stringOf(raw.target) ?? resource,
        templated: false,
        templatePointers: {},
        method: String(raw.method ?? GET),
        contentType: String(raw.contentType ?? JSON_TYPE),
        schema,
        schemaKey: schemaKeyOf(schema),
        dialect: HAL_FORMS,
    };
}

/** The forms of a HAL-FORMS [body]; a template with no target acts on the [resource] itself. */
export function templatesOf(body: JsonObject, resource: string): Map<string, Form> {
    const entries = Object.entries(objectOf(body._templates));
    return new Map(entries.map(([name, raw]) => [name, templateForm(name, objectOf(raw), resource)]));
}
