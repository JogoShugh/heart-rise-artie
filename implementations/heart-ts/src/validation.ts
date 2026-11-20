// A form's declared schema, checked just before an act is sent. The schema is
// found at run time, so each one is compiled once and kept by its key. Only
// what a schema disallows is refused: nothing is coerced, defaulted or removed.
// A schema that cannot be compiled blocks nothing; the service still decides.
import { Ajv2020, ErrorObject, ValidateFunction } from "ajv/dist/2020";
import { JsonObject } from "./json";
import { Form } from "./wire";

/** A local note that a form's schema could not be compiled. It holds no field value. */
export interface SchemaFailure { form: string; schema: string; error: string }

/** RFC 6901: a member name as one token of a JSON Pointer. */
const token = (name: unknown): string => `/${String(name).replaceAll("~", "~0").replaceAll("/", "~1")}`;

/** By keyword, the parameter naming the member a violation is about when it is not the value at instancePath. */
const MEMBER_PARAM: Readonly<Record<string, string>> = { required: "missingProperty", additionalProperties: "additionalProperty" };

/** The member a violation is about, one missing or one not allowed; none for every other keyword. */
const member = (error: ErrorObject): string => {
    const param = MEMBER_PARAM[error.keyword];
    return param ? token(error.params[param]) : "";
};

/** Said the same by every client: the pointer of the value, then the keyword it breaks; the keyword alone at the body. */
const sentence = (error: ErrorObject): string => `${error.instancePath}${member(error)} ${error.keyword}`.trim();

export class FormChecks {
    /** By schema key. A schema that failed to compile is kept as undefined, so it is tried and noted once. */
    private readonly checkers = new Map<string, ValidateFunction | undefined>();

    constructor(private readonly failures: SchemaFailure[] = []) {}

    /** One sentence per way [fields] break the form's schema; none if the schema could not be compiled. */
    outsideSchema(form: Form, fields: JsonObject): string[] {
        const checker = this.checkerFor(form);
        if (!checker || checker(fields)) return [];
        return (checker.errors ?? []).map(sentence);
    }

    private checkerFor(form: Form): ValidateFunction | undefined {
        if (!this.checkers.has(form.schemaKey)) this.checkers.set(form.schemaKey, this.compile(form));
        return this.checkers.get(form.schemaKey);
    }

    private compile(form: Form): ValidateFunction | undefined {
        try {
            // A fresh compiler each time, as one remembers every `$id` it has seen.
            return new Ajv2020({ strict: false, allErrors: true, logger: false }).compile(form.schema);
        } catch (error) {
            this.failures.push({ form: form.name, schema: form.schemaKey, error: String(error) });
            return undefined;
        }
    }
}
