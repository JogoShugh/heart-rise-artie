Feature: GitHub's link members become HEART links: through the gateway where it serves them, typed everywhere
  spec/PLAN-GATEWAY-URL-LINKS.md. GitHub puts its links in the state, as
  members named *_url, and on a pull request also in its own _links. The
  connector says which members are links, by pattern; the gateway never
  scans other data for addresses. Each such member leaves the state and
  becomes a link with a media type: a link into GitHub's API that the
  gateway serves is rewritten to the gateway's own address, under the
  operation's relation; any other API link keeps GitHub's address; web pages
  and images stay GitHub's, typed as what they are. A URI template is a
  templated link. A nested object that is a resource of its own (a user, a
  repository) is embedded under _embedded, its links lifted the same way;
  its data stays. git:// and ssh addresses have no HEART binding, so they
  stay data.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Login fails on Safari | open  |              | false  | ada    |
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"

  Scenario: An issue's link members leave its state and become typed links
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links these, by type
      | relation                    | href                                                                   | type                        | templated |
      | self                        | /repos/acme/rockets/issues/10                                          |                             |           |
      | github:issues-list-comments | /repos/acme/rockets/issues/10/comments                                 | application/hal+json        |           |
      | github:repos-get            | /repos/acme/rockets                                                    | application/hal+json        |           |
      | github:events               | https://api.github.com/repos/acme/rockets/issues/10/events             | application/vnd.github+json |           |
      | github:labels               | https://api.github.com/repos/acme/rockets/issues/10/labels{/name}      | application/vnd.github+json | true      |
      | alternate                   | https://github.com/acme/rockets/issues/10                              | text/html                   |           |
    And the answer's state has none of these members
      | member         |
      | url            |
      | repository_url |
      | comments_url   |
      | events_url     |
      | labels_url     |
      | html_url       |
    And the answer's state still has these members
      | member |
      | number |
      | title  |
      | state  |

  Scenario: An issue's author is embedded, with its own typed links
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the answer embeds "github:user" with its self at "/users/ada" and these links
      | relation         | href                                          | type                        |
      | alternate        | https://github.com/ada                        | text/html                   |
      | icon             | https://github.com/images/error/ada_happy.gif | image/gif                   |
      | github:followers | /users/ada/followers                          | application/hal+json        |
      | github:repos     | https://api.github.com/users/ada/repos        | application/vnd.github+json |
    And the embedded "github:user" still has the member "login" as "ada"
    And the answer's state has none of these members
      | member |
      | user   |

  Scenario: A pull request's repositories are embedded, served by the gateway, and git addresses stay data
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links "self" to "/repos/acme/rockets/pulls/482"
    And the answer embeds "github:base-repo" with its self at "/repos/acme/rockets" and these links
      | relation  | href                                   | type      |
      | alternate | https://github.com/acme/rockets        | text/html |
    And the embedded "github:base-repo" still has the member "git_url" as "git:github.com/acme/rockets.git"
    And the answer's state still has these members
      | member |
      | head   |
      | base   |

  Scenario: GitHub's own _links on a pull request become links too, and a template is never taken for a served path
    GitHub's review_comment template, .../pulls/comments{/number}, would look
    like the pull request read's path once its expression is set aside; a
    template is a family of addresses, so the gateway never rewrites one.
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the answer links these, by type
      | relation              | href                                                              | type                        | templated |
      | github:commits        | /repos/acme/rockets/pulls/482/commits                             | application/hal+json        |           |
      | github:issues-get     | /repos/acme/rockets/issues/482                                    | application/hal+json        |           |
      | github:review-comment | https://api.github.com/repos/acme/rockets/pulls/comments{/number} | application/vnd.github+json | true      |

  Scenario: The users on a pull request's head and base are embedded, not flattened into its links
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the answer embeds "github:head-user" with its self at "/users/acme" and these links
      | relation         | href                  | type                 |
      | github:followers | /users/acme/followers | application/hal+json |
    And the answer has none of these links
      | relation                  |
      | github:head-user-followers |
      | github:base-user-followers |
      | github:pulls-get          |
