# heart-mcp: HEART through MCP

heart-mcp lets any MCP host, such as Claude Code, Claude Desktop or your own
agent, work any HEART service.

An MCP server usually hands the model a catalogue of what it can do in
principle. heart-mcp hands it what is legal right now, for one resource in its
current state. The model chooses. ARTIE, the HEART client, checks the choice
and builds and sends every request.

- [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md): how it works, with diagrams.
- [PROOF.md](PROOF.md): the evidence from real runs against the garden,
  basketball and tickets services.

## The six tools

The tool list never changes. What is legal now comes back in every result.

| Tool | Input | What it does |
|---|---|---|
| `heart_look` | `service` (optional) | Starts at a configured service's bookmark, or reads the current resource again. |
| `heart_act` | `act`, `fields` | Takes one offered act by its name (such as `garden:water-cell`), with fields that satisfy its schema. |
| `heart_follow` | `rel`, `title` | Follows one of the current resource's links. |
| `heart_describe` | `act` | The service's fuller description of an offered act, from its profile. |
| `heart_explain` | `act` | Why an act is not offered now, in the service's words. Nothing is sent. |
| `heart_back` | none | Returns to the resource before this one, read again. |

Every result holds:

- `resource.state`: the resource's state;
- `acts`: each offered act's `name`, `title`, `description` and `schema`;
- `links`: each link's `rel` and `title`;
- after an act, an `outcome`, whose `kind` is one of:
  - `acted`;
  - `rejected-before-sending`: the act wasn't offered, or the fields broke its schema; nothing was sent;
  - `stale`: someone else changed the resource first; the act was dropped, and the result shows the fresh resource;
  - `not-offered`;
  - `invalid-input`;
  - `rule-refused`: the fields were valid, but a rule of the resource said no, in its own words.

**What the model never sees:** an address, a method, a header or a validator.
It can't build a request, and it can't call an act that isn't offered.

## Run it

### With Docker

Build from the repo root:

```sh
docker build -f mcp/Dockerfile -t heart-mcp .
```

Add it to Claude Code:

```sh
claude mcp add heart -- docker run -i --rm \
  -e HEART_BOOKMARKS=garden=http://host.docker.internal:8080/ heart-mcp
```

Inside the container, `localhost` is the container itself, so a service on your
machine is `host.docker.internal`.

For Claude Desktop, add the same command to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "heart": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "-e", "HEART_BOOKMARKS=garden=http://host.docker.internal:8080/", "heart-mcp"]
    }
  }
}
```

### Without Docker

Build with Gradle; it runs on JDK 21 and compiles for 22:

```sh
cd mcp && ./gradlew :heart-mcp-server:installDist
```

Then point the host at the start script. It needs Java 22 or later:

```sh
claude mcp add heart -e HEART_BOOKMARKS=garden=http://localhost:8080/ \
  -- "$PWD/heart-mcp-server/build/install/heart-mcp/bin/heart-mcp"
```

### Settings

- `HEART_BOOKMARKS`: the services, as `name=url` pairs separated by commas.
  Required.
- `ARTIE_EVENTS`: a file for ARTIE's event log (OpenTelemetry fields, one JSON
  line per decision).
- `ARTIE_EVENTS_URL`: where to POST those lines instead.

### Credentials

Every HEART service requires a credential (SPEC §6.13). heart-mcp reads it as
ARTIE does, and the host's model never sees it: no tool takes one, and no result,
error or event shows one. ARTIE adds it just above the wire, for the service's
own origin only. In order, it reads:

1. **Environment variables, named by origin.** `http://localhost:8080` reads
   `ARTIE_TOKEN_LOCALHOST_8080` (Bearer), or `ARTIE_USER_LOCALHOST_8080` and
   `ARTIE_PASSWORD_LOCALHOST_8080` (Basic).
2. **`~/.artie/credentials`** (or `$ARTIE_HOME/credentials`), readable by you
   only (`chmod 600`), one section per origin:

   ```ini
   [http://localhost:8080]
   bearer = ...

   [https://tickets.example]
   user = coach
   password = ...
   ```

3. **Vault**, when `ARTIE_VAULT_ADDR` is set: the secret at
   `secret/artie/<host>_<port>` holds `bearer`, or `user` and `password`. ARTIE
   logs in with `VAULT_TOKEN`, `~/.vault-token`, or an AppRole
   (`ARTIE_VAULT_ROLE_ID` and `ARTIE_VAULT_SECRET_ID_FILE`).

For the services in this repo, `scripts/dev-credentials.sh` makes one token per
service and writes it to `~/.artie/credentials`. It never prints a token.

In Docker, the service is `http://host.docker.internal:8080`, so the credential is
for that origin. Mount a credentials file made for it, read-only:

```sh
claude mcp add heart -- docker run -i --rm -v "$HOME/.artie-docker:/root/.artie:ro" \
  -e HEART_BOOKMARKS=garden=http://host.docker.internal:8080/ heart-mcp
```

ARTIE's promise covers what passes through ARTIE. A host that also gives its
model a shell on the same machine, as Claude Code does, lets that model read your
environment and your files directly. Deny it reads of `~/.artie` and of the
environment in the host's own settings, or keep credentials in Vault.

## Use it from your own JVM code

`heart-mcp` is a library. Give it a client and bookmarks, then register its tools
on any MCP SDK server:

```kotlin
val server = Server(Implementation("my-agent", "1.0"), HeartMcp.options())
val credentials = CredentialSources.standard(System.getenv(), Path.of(System.getProperty("user.home")))
HeartMcp(HeartMcp.client("my-agent", EventSinks.NONE, credentials), mapOf("garden" to "http://localhost:8080/"))
    .register(server)
```

## Prove it

```sh
scripts/mcp-check.sh            # 15 scenarios through the MCP client SDK, 13 against the live garden
scripts/mcp-check.sh --docker   # the image over JSON-RPC: tools, find bed 2, water it
```

Both start their own garden server on 8080, so they refuse to run if 8080 is in
use. They stop the server and reset the data when they finish.

## Status

This is a spike on branch `mcp-spike`:
- The transport is stdio only. There's no HTTP transport and no auth.
- It uses the MCP Kotlin SDK 0.8.1, which speaks protocol version 2025-06-18.
