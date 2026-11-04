// The words of the protocol, each named once. A typo here fails loudly; the same
// typo as an inline string would be a request no service understands.

// HTTP headers
export const CONTENT_TYPE = "Content-Type";
export const CONTENT_LOCATION = "Content-Location";
export const LINK_HEADER = "Link";
export const ETAG = "ETag";
export const IF_MATCH = "If-Match";
export const IF_NONE_MATCH = "If-None-Match";
export const CACHE_CONTROL = "Cache-Control";
export const ACCEPT_PROFILE = "Accept-Profile";
export const PREFERENCE_APPLIED = "Preference-Applied";
export const AUTHORIZATION = "Authorization";
export const NO_CACHE = "no-cache";

// HTTP methods
export const GET = "GET";
export const POST = "POST";

// Rungs, and what asks for them (SPEC §6.4)
export const L0 = "L0";
export const L1 = "L1";
export const L2 = "L2";
export const FORMS_BY_LINK = "forms=link";
export const SCHEMA_BY_LINK = "schema=link";

// Dialects (SPEC §6.1)
export const HAL_FORMS = "hal-forms";
export const HAL_SCHEMA_FORMS = "hal-schema-forms";
/** The link relation a resource names its profile by, from a host that can't send the Link header. */
export const PROFILE_REL = "profile";

// Members a HEART form may carry
export const HR_OPTIONS = "hr:options";
export const HR_INTEGRITY = "hr:integrity";
export const HR_TEMPLATE_POINTERS = "hr:templatePointers";
export const DEFAULT_VALUE_FIELD = "value";
export const DEFAULT_PROMPT_FIELD = "prompt";
export const REL_VARIABLE = "{rel}";
export const OBJECT_TYPE = "object";

// Kinds of refusal
export const NOT_A_CHOICE = "not-a-choice";
export const OUTSIDE_SCHEMA = "outside-schema";
export const RULE_REFUSED = "rule-refused";
export const INVALID_INPUT = "invalid-input";
export const STALE = "stale";
export const NOT_OFFERED = "not-offered";
export const PRECONDITION_REQUIRED = "precondition-required";
export const NOT_AUTHENTICATED = "not-authenticated";
export const FAILED = "failed";

// What a read is called in a refusal
export const READ = "read";

// Encodings
export const UTF8 = "utf8";
export const SHA256 = "sha256";
export const BASE64 = "base64";

// Fetch settings
export const MANUAL_REDIRECT = "manual";
