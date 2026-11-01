// Tells the refusals apart, from the status and the problem document.
import { InvalidInput, NotAuthenticated, NotOffered, PreconditionRequired, Refused, RuleRefused, StaleAct } from "./errors";
import { JsonObject } from "./json";
import { PROBLEMS, violationsOf } from "./wire";

/** What a refused response said. */
export interface RefusedResponse { name: string; status: number; statusText: string; problem: JsonObject }

function unprocessable(message: string, problem: JsonObject): Refused {
    const violations = violationsOf(problem);
    const type = String(problem.type ?? "").replace(PROBLEMS, "");
    if (type === "rule-refused") return new RuleRefused({ kind: "rule-refused", message, status: 422, violations });
    return new InvalidInput({ kind: "invalid-input", message, status: 422, violations });
}

export function refusalOf({ name, status, statusText, problem }: RefusedResponse): Refused {
    const detail = String(problem.detail ?? problem.title ?? statusText);
    const said = `${name} was refused (${status}): ${detail}`;
    switch (status) {
        case 412: return new StaleAct({ kind: "stale", message: `${said}; re-read, not resent`, status });
        case 409: return new NotOffered({ kind: "not-offered", message: said, status });
        case 428: return new PreconditionRequired({ kind: "precondition-required", message: said, status });
        case 401: return new NotAuthenticated({ kind: "not-authenticated", message: said, status });
        case 422: return unprocessable(said, problem);
        default: return new Refused({ kind: "failed", message: said, status });
    }
}

/** A stale or no-longer-offered act leaves the client holding old state, so it reads again. */
export const needsRereading = (status: number): boolean => status === 412 || status === 409;
