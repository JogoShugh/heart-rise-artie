# Idea: Buster as a brain with thin hooks (the sidecar approach)

**Status:** idea only, 2026-10-12. Nothing here is decided or built. Raised
while asking how Buster could test servers that are not Spring WebFlux, or not
on the JVM at all.

## The idea

Split Buster in two:

- **The brain:** one Buster service holding everything that matters: the
  engine, the plugins, the verifier, campaign state, the client event log, the
  report and the live view. Written once, in Kotlin on the JVM, and run as its
  own process.
- **The hook:** a tiny piece of middleware for each web framework. It has no
  logic. It hands each exchange to the brain and does what the brain says.

The intelligence stays in one place, and the part written per framework is as
thin as it can be. A plugin, a verdict rule or a campaign change is made once
in the brain, and every platform gets it.

## One exchange

1. The client sends a request to the app.
2. The hook notes the method, path, headers and `traceparent`, and lets the app
   handle the request as usual.
3. The app produces its response. The hook holds it before it goes out, and
   before compression.
4. The hook sends both halves to the brain over a local connection: the
   request details, and the response status, headers and body.
5. The brain classifies the response, checks the campaign plan, perhaps
   applies a plugin, and records the exchange for its verdicts.
6. The brain answers either "send it as is" or "send this instead", with the
   replacement status, headers and body.
7. The hook writes that answer to the client, with a correct Content-Length.

```
client ──► [ app + hook ] ──► client
                 │  ▲
         exchange│  │ as is / replacement
                 ▼  │
           [ Buster brain ] ◄── client event log (OTel lines)
                 ▲
     record / plan / arm / report   (test script, CI)
```

## In practice

- **Setup:** add the Buster hook package for the stack (an ASP.NET middleware,
  Express or Fastify middleware, FastAPI or Django middleware, Ktor plugin,
  http4k filter, Quarkus or Micronaut filter, servlet filter). Set one
  setting, such as `BUSTER_URL=http://localhost:7070`. If it is unset, the
  hook does nothing.
- **Running it:** in a test environment the brain runs next to the app: in
  the same docker-compose file, the same Kubernetes pod, or started by the
  test harness. Tests that call the app in-process (TestClient,
  WebApplicationFactory) call the hook directly. It never runs in production.
- **A campaign:** the same steps as today, pointed at the brain: record, plan,
  arm round k and run the client, repeat, then the gate.
  `scripts/buster-campaign.sh` becomes the same for every stack.
- **The client side does not change:** ARTIE, or any client, still sends
  `traceparent` and posts its event log to the brain.

## What a hook knows that a proxy does not

The hook runs inside the app, so it can pass along context a proxy has to
guess:

- which route or handler answered;
- the resource type;
- whether a response is a resource, an affordance set or a schema, as
  declared rather than guessed from the bytes.

That makes classification exact. It also works where traffic cannot be routed
through a proxy: tests that call the app in-process, TLS that ends inside the
app, serverless platforms.

## Costs, and how to keep them down

- **Latency:** one local round trip per response.
  - While recording, the hook reports without waiting for an answer.
  - While armed, the brain first sends the hook a short list of what it might
    touch (method, path pattern, client); everything else passes straight
    through.
- **Buffering:** the hook must hold the whole body. That is fine for HEART's
  JSON and wrong for streams, so large or streaming responses pass through
  untouched.
- **The brain is down:** the hook fails open, and the app behaves normally.
  The brain marks those exchanges as unseen, so the campaign gate shows a hole
  instead of claiming a pass.
- **Maintenance:** one hook per framework, each roughly 50 to 100 lines, with
  nothing clever in them.
- **Security:** off unless explicitly enabled, and never deployed to
  production.

## What has to be specified so anyone can write a hook

1. The exchange message: the request half, the response half, and optional
   server context (route, resource type, kind of representation).
2. The reply: "as is", or a replacement.
3. The fail-open and pass-through rules.
4. The control API (record, plan, arm, off, report) and the client event log
   format, which is already OpenTelemetry fields plus W3C trace context.

## Precedents

- Envoy `ext_proc` and `ext_authz`: a proxy asks an outside service to inspect
  or rewrite messages.
- Open Policy Agent: the app asks a local policy service and obeys the answer.
- Dapr: the app talks to a local sidecar for shared features.
- The OpenTelemetry collector with thin per-language SDKs.

Buster would be the same pattern aimed at testing.

## Sidecar or reverse proxy?

They are not rivals, and both can feed the same brain over the same exchange
message:

- **Reverse proxy:** no change to the app at all, but it guesses
  classification from the bytes, and needs a network position, TLS handling
  and URL rewriting.
- **Hook:** a one-line install per stack, exact classification, and it works
  in-process.

So the brain is the product, and the proxy and the hooks are two ways in.
Today's `BusterWebFilter` would become the first hook, for Spring WebFlux.

## Related

- `buster-over-coap.md`: a CoAP adapter would be another hook, mapping CoAP
  options to the same exchange message.
