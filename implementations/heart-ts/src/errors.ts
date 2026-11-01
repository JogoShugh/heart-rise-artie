/** What a refusal says: its kind, its sentence, its status, and any violations. */
export interface RefusalFacts { kind: string; message: string; status: number; violations?: string[] }

/** Every refusal names what happened; none carries a credential. */
export class Refused extends Error {
    readonly kind: string;
    readonly status: number;
    readonly violations: string[];

    constructor(facts: RefusalFacts) {
        super(facts.message);
        this.kind = facts.kind;
        this.status = facts.status;
        this.violations = facts.violations ?? [];
    }
}
export class StaleAct extends Refused {}
export class NotOffered extends Refused {}
export class InvalidInput extends Refused {}
export class RuleRefused extends Refused {}
export class PreconditionRequired extends Refused {}
export class NotAuthenticated extends Refused {}
/** Not sent: a value is not among its property's choices, or a required one has none now. */
export class NotAChoice extends Refused {}
export class IntegrityFailure extends Error {}
