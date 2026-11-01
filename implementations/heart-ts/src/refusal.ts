// Tells the refusals apart, from the status and the problem document.
import { InvalidInput, NotAuthenticated, NotOffered, PreconditionRequired, Refused, RuleRefused, StaleAct } from "./errors";
import { JsonObject } from "./json";
import { PROBLEMS, violationsOf } from "./wire";
import { FAILED, INVALID_INPUT, NOT_AUTHENTICATED, NOT_OFFERED, PRECONDITION_REQUIRED, RULE_REFUSED, STALE } from "./vocabulary";

/** What a refused response said. */
interface RefusedResponse { name: string; status: number; statusText: string; problem: JsonObject }

function unprocessable(message: string, problem: JsonObject): Refused {
    const violations = violationsOf(problem);
    const type = String(problem.type ?? "").replace(PROBLEMS, "");
    if (type === RULE_REFUSED) return new RuleRefused({ kind: RULE_REFUSED, message, status: 422, violations });
    return new InvalidInput({ kind: INVALID_INPUT, message, status: 422, violations });
}

export function refusalOf({ name, status, statusText, problem }: RefusedResponse): Refused {
    const detail = String(problem.detail ?? problem.title ?? statusText);
    const said = `${name} was refused (${String(status)}): ${detail}`;
    switch (status) {
        case 412: return new StaleAct({ kind: STALE, message: `${said}; re-read, not resent`, status });
        case 409: return new NotOffered({ kind: NOT_OFFERED, message: said, status });
        case 428: return new PreconditionRequired({ kind: PRECONDITION_REQUIRED, message: said, status });
        case 401: return new NotAuthenticated({ kind: NOT_AUTHENTICATED, message: said, status });
        case 422: return unprocessable(said, problem);
        default: return new Refused({ kind: FAILED, message: said, status });
    }
}

/** A stale or no-longer-offered act leaves the client holding old state, so it reads again. */
export const needsRereading = (status: number): boolean => status === 412 || status === 409;
