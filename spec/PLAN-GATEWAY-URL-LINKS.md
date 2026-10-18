# Plan: members that are only links become links (gateway)

Drafted 2026-10-18, revised the same day with the author. Not started. Waits on a precursor:
`_embedded`, done properly across all of HEART (its own plan, next).

## The problem

GitHub, like many JSON APIs, puts its links in the state as plain members named `*_url`. Through
the gateway they reach HEART clients unchanged, as data. Measured live on 2026-10-18:

- Pull request #20 has **157** members whose value is a URL. Its `_links` holds only `self`,
  `curies` and `hr:unavailable`.
- Issue #10 has 20, fifteen of them on the author's `user` object (`followers_url`, `gists_url`...).

A client should find what it can do next in the hypermedia controls, not by guessing which strings
in the data are addresses.

## The rules, as agreed

1. **Every member that is only a link moves into `_links`**, whatever it points at: an API
   resource, a web page, an image. None stays in the state.
2. **Only configured members.** The connector names which members are links, by pattern
   (`*_url`, `url`). The gateway never scours bodies for URL-like strings: an issue's body, a
   comment's text, or any other data is never touched.
3. **API links go through the gateway.** A link into an upstream API the gateway serves is
   rewritten to the gateway's address. The gateway may serve several upstreams (GitHub, Jenkins,
   others), each with its connector; a link is rewritten when its URI falls under one of their base
   URLs, and the rewrite is a prefix swap from that configured base, never a guess.
   - The operation is in the connector: the full HEART resource, with forms and rules.
   - It isn't: a pass-through, GET only, the upstream's answer with its link members lifted the
     same way, and no forms ("readable, not yet modelled"). Only paths matching a GET operation in
     the upstream's OpenAPI description are served; anything else is a 404, so it is not an open
     proxy.
4. **Everything else stays external, as a link.** The gateway is an API gateway, not an HTML or
   image gateway. Web pages (`html_url`), images (`avatar_url`), `.diff` and `.patch` pages keep
   their own addresses, in `_links`, with a `type` hint.
5. **Media type hints on every link** (`type`, RFC 8288's target attribute). Links the gateway
   serves carry `application/hal+json;profile=…` (HAL Schema Forms).
6. **Templates** (`{/name}`, `{+path}`, `{?since,...}`) become `templated: true`.
7. **Nested objects become `_embedded`** (`user`, `head.repo`, `base.repo`, `owner`), each with its
   own `self` and its own lifted links, under a relation named after its path (`github:user`,
   `github:head-repo`).
8. **No binding, no link.** `git://` stays data until HEART has a binding for it; `ssh_url`
   (`git@github.com:...`) is not a URI, so it stays data.
9. **Nothing is dropped by default.** `drop` is an option, empty by default.
10. **Every lifted relation is described in the profile**, with its title and type.

## The configuration

In `connector.yaml`, for every resource, overridable in a `<noun>.resource.yaml`:

```yaml
links:
  members: ["*_url", url]          # which members are links; nothing else is read
  self: url                        # the resource's own address: becomes self
  relations:                       # names; otherwise <profile>:<name without _url, hyphenated>
    html_url: alternate
    avatar_url: icon
  types:                           # by pattern on the URI, first match wins
    - { host: github.com, suffix: .diff, type: text/x-diff }
    - { host: github.com, suffix: .patch, type: text/x-patch }
    - { host: github.com, type: text/html }
    - { host: avatars.githubusercontent.com, type: "image/*" }
  embed: [user, owner, head/repo, base/repo]   # nested objects that become _embedded
  drop: []
```

The upstream's base URL (`UPSTREAM_URL`) is already configured; links under it are API links (rule
3) and need no `types` entry.

## Order

1. Precursor: the `_embedded` plan, done and green across HEART.
2. Features in heart-gateway, with tables, against the fake GitHub: each rule above a scenario. A
   red run at test level.
3. The configuration model and the transform; then the pass-through; green.
4. The GitHub connector's `links` section.
5. Live, reads only, in the scratch repository: issue #10 and pull request #20, before and after,
   in a short report.
6. Rerun `github-comment.kts` and `github-pulls.kts` unchanged.
7. SPEC: a SHOULD for every HEART server: a member whose value is only a link belongs in `_links`.

Commits local; the author pushes.

## Decided 2026-10-18, after phase 2 (92fdc40)

- **Order:** a live before-and-after report first (issue #10 and pull request #20, reads only), then
  the pass-through.
- **Pass-through scope:** a connector allowlist of GET operations (`users/get`, a user's followers, a
  pull request's commits...). Nothing else is answered; links to any other API resource stay
  external, typed as the API's.
- **Passed-through lists** are served as HEART collections: `_links.item` per member, titled by a
  configured member, `_embedded.item` summaries, paging from GitHub's Link header.
- **Relations** of passed-through links are named after their member (`github:followers`); the profile
  describes each, citing its operation.
