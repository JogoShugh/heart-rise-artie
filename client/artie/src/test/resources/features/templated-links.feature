Feature: A templated link is kept as its template
  HAL: a link whose `templated` is true has a URI Template (RFC 6570) as its
  href, such as GitHub's `.../labels{/name}`. ARTIE keeps it as written,
  resolved against the representation's URL only when it is relative, and
  never parses it as a URI, which it isn't until it is expanded. The same
  holds for a link inside an embedded resource.

  Background:
    Given the service answers GET "http://garden.test/issues/10" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      number: 10
      _links:
        self: {href: /issues/10}
        github:labels: {href: "https://api.github.com/repos/acme/rockets/issues/10/labels{/name}", templated: true}
        github:search: {href: "/issues{?q}", templated: true}
      _embedded:
        github:user:
          login: ada
          _links:
            self: {href: https://api.github.com/users/ada}
            github:events: {href: "https://api.github.com/users/ada/events{/privacy}", templated: true}
      _forms: {}
      """

  Scenario: Templated links are read as their templates, absolute or resolved
    When the client enters "http://garden.test/issues/10"
    Then the resource links these templates
      | relation      | href                                                              |
      | github:labels | https://api.github.com/repos/acme/rockets/issues/10/labels{/name} |
      | github:search | http://garden.test/issues{?q}                                     |
    And the embedded "github:user" links "github:events" to the template "https://api.github.com/users/ada/events{/privacy}"
