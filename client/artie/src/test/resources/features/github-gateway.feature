Feature: ARTIE drives GitHub issues through the HEART gateway
  spec/PLAN-ARTIE-SCRIPTS.md, step 1. The gateway serves GitHub's issues as
  HEART resources; ARTIE meets them as any HEART server's, from the
  bookmark, by forms and links alone. Here the gateway and a small GitHub
  run in process, on GitHub's own example JSON, so ARTIE's requests reach
  them as they would over the network, and nothing is mocked in between.
  No scenario names an address but the bookmark's.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title                 | state | author |
      | 10     | Login fails on Safari | open  | ada    |
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"

  Scenario: From the bookmark to an issue, by the bookmark's lookup form
    When the client enters "https://github.gateway.test/"
    And the client acts on "github:issues-get" with:
      """yaml
      owner: acme
      repo: rockets
      issue_number: 10
      """
    Then the resource state contains:
      | key   | value                 |
      | title | Login fails on Safari |
      | state | open                  |
    And the manifest offers exactly
      | act                      |
      | github:close             |
      | github:close-not-planned |
      | github:close-duplicate   |
      | github:edit              |
      | github:lock              |
      | github:label             |
      | github:unlabel           |
      | github:assign            |
      | github:unassign          |
      | github:set-milestone     |
      | github:comment           |
      | github:add-sub-issue     |
      | github:remove-sub-issue  |

  Scenario: Commenting brings a comment into being, and ARTIE then holds the comment
    When the client enters "https://github.gateway.test/"
    And the client acts on "github:issues-get" with:
      """yaml
      owner: acme
      repo: rockets
      issue_number: 10
      """
    And the client acts on "github:comment" with:
      """yaml
      body: Fixed in 1.2.
      """
    Then the resource state contains:
      | key  | value         |
      | body | Fixed in 1.2. |
    And the manifest offers exactly
      | act         |
      | github:edit |

  Scenario: The issue links its comments; ARTIE follows the link, then a comment by its title
    Given issue 10 of "acme/rockets" has these comments on the small GitHub
      | id  | author | body                   |
      | 501 | ada    | It fails on Safari 17. |
    When the client enters "https://github.gateway.test/"
    And the client acts on "github:issues-get" with:
      """yaml
      owner: acme
      repo: rockets
      issue_number: 10
      """
    And the client follows the "github:issues-list-comments" link
    And the client follows the "item" link titled "It fails on Safari 17."
    Then the resource state contains:
      | key  | value                  |
      | body | It fails on Safari 17. |

  Scenario: Closing; then close is no longer offered, and ARTIE refuses it without asking
    When the client enters "https://github.gateway.test/"
    And the client acts on "github:issues-get" with:
      """yaml
      owner: acme
      repo: rockets
      issue_number: 10
      """
    And the client acts on "github:close" with:
      """yaml
      {}
      """
    Then the resource state contains:
      | key   | value  |
      | state | closed |
    When the client tries to act on "github:close" with:
      """yaml
      {}
      """
    Then the client sent no request for it
    And the client explains "github:close" as "The issue is already closed."
