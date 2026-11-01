# heart-ts

A small HEART client in TypeScript. Give it one address, and it finds out what a
service offers and how to act on it, with no generated code and no knowledge of
the service ahead of time.

It was written from the specification (`spec/SPEC.md`), so it shows that the
specification is enough to write a client from. It is by the same author as
ARTIE, so it is not an independent implementation.

## A first run

The package is not published. Build it here:

```bash
npm ci
npm run build
```

Then enter a service at its bookmark, and go on from what it offers:

```ts
import { HeartClient, Refused } from "heart-ts";

const client = new HeartClient({ authorization: `Bearer ${token}` });

await client.enter("http://localhost:8080/");
await client.act("garden:get-bed", { id: 2 });

console.log(client.current?.state);          // what the resource says
console.log([...client.current!.forms.keys()]); // what you may do now

try {
    await client.act("garden:water-cell", { milliliters: 250 });
} catch (error) {
    if (error instanceof Refused) console.log(error.kind, error.message);
}
```

Nothing there names an address beyond the bookmark. Each act comes from a form the
last answer offered, and the client fills in the target, the method and `If-Match`.

## What it does

- **`enter(url)`** reads the bookmark.
- **`act(name, fields)`** submits an offered form.
- **`follow(rel, title?)`** follows a link, or a resource embedded in the answer.
- **`choices(name, fields?)`** gives the values a form's properties may take.
- **`refresh()`** reads the current resource again.
- **`current`** is what the client holds: `state`, `forms`, `links`, `embedded`,
  `rung`, `dialect`, `etag` and `url`.

## Rungs and dialects

Ask for a rung, and the client takes the real one from each answer. If a service
ignores the request, the client carries on at the rung the service gave.

| Rung | The client asks for | What comes back |
|---|---|---|
| `L0` (default) | nothing | Forms inline in the resource |
| `L1` | `forms=link` | A link to an affordance set, which holds its schemas |
| `L2` | `forms=link, schema=link` | A link to a set, which points to its schemas |

Shared layers are immutable. The client checks each against its integrity value,
and never reads the same one twice.

Two dialects are spoken. `hal-schema-forms` is the default and works at every rung.
`hal-forms` is the L0 on-ramp, and it is what Spring HATEOAS produces:

```ts
const client = new HeartClient({ dialect: "hal-forms" });
```

Whichever one you ask for, the client reads the dialect the answer is in.

## When an act is refused

Every refusal is a `Refused`, with a `kind`, a `status`, a `message` and any
`violations`. Some are decided here, before anything is sent.

| `kind` | What happened | Status |
|---|---|---|
| `outside-schema` | The fields break the schema the service declared | not sent |
| `not-a-choice` | A value is not among a property's choices | not sent |
| `stale` | Someone acted first; the client has read again, and did not resend | 412 |
| `not-offered` | The act is not legal now; the client has read again | 409 |
| `precondition-required` | The service wanted `If-Match` | 428 |
| `not-authenticated` | The service wants a credential | 401 |
| `invalid-input` | The service refused the body | 422 |
| `rule-refused` | A domain rule refused the body | 422 |
| `failed` | Anything else | as sent |

A shared layer whose bytes do not match its link throws `IntegrityFailure`.

The check before sending refuses only what a schema disallows. Undeclared
properties pass, and nothing is coerced or defaulted. If a schema cannot be
compiled, the act goes to the service anyway, and the failure is noted once in
`schemaFailures`. The service stays the authority.

## Options

| Option | Meaning |
|---|---|
| `rung` | The rung to ask for |
| `dialect` | The dialect to ask for |
| `authorization` | An `Authorization` value, sent only to the bookmark's own origin |
| `log` | An array that collects every request sent |
| `schemaFailures` | An array that collects schemas that could not be compiled |
| `setCache` | Anything with `get` and `set`, to share shared layers between clients |

Give clients one `setCache` only if they may all read the same layers: a client
finds what another read, and sends no request. A `Map` never shrinks, so a
long-running process that meets many services should pass something with a limit.

## Tests

The tests are the scenarios in `../conformance/features`, which drive this client
through its public interface. They replay exchanges recorded from a real garden,
so they run offline. In this folder:

```bash
npm run lint       # ESLint, tsc, dependency-cruiser, Knip
npm run mutation   # Stryker, which edits src/ while it runs
```

To run the same scenarios against a live garden, use `../conformance/run.sh`.
When the garden's representations change, record them again with
`../conformance/record.sh`.
