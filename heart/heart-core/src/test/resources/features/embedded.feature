Feature: A resource embeds the resources its state holds
  SPEC §6.1, decided 2026-10-18 (spec/PLAN-HAL-EMBEDDED.md). A type may declare
  that part of its state is another resource: a pull request's author, its
  reviewers. Each is embedded the HAL way, under `_embedded`, keyed by a
  relation of the profile, with its own self link, filled from its own members
  by an item path. What is embedded leaves the state. An embedded resource
  carries no forms (HAL-SCHEMA-FORMS-HR Rule 9): a client follows it to act.
  Embedded resources name one resource, so they are Layer 1, never in a shared
  affordance set.

  Background:
    Given a pull request type declared with the item path "/repos/{owner}/{repo}/pulls/{number}"
    And the pull request type embeds these resources
      | relation  | pointer    | self           | variable | from   |
      | author    | /author    | /users/{login} | login    | /login |
      | reviewers | /reviewers | /users/{login} | login    | /login |
    And pull request 482 of "acme/rockets" is open, written by "ada"
    And its reviewers are
      | login |
      | lin   |
      | kay   |

  Scenario Outline: The resource embeds each resource, with its self link, in every rendering
    When the pull request is rendered <rendering>
    Then it embeds these resources
      | relation        | index | self       | login |
      | pulls:author    |       | /users/ada | ada   |
      | pulls:reviewers | 0     | /users/lin | lin   |
      | pulls:reviewers | 1     | /users/kay | kay   |
    And these members are not in its state
      | member    |
      | author    |
      | reviewers |
    And no embedded resource carries forms
    And its curies name the profile "pulls"

    Examples:
      | rendering   |
      | at L0       |
      | at L1       |
      | at L2       |
      | in HAL-FORMS |

  Scenario: Embedded resources are not in the shared affordance set
    Given pull request 7 of "acme/engines" is open, written by "bo"
    When both pull requests are rendered at L1
    Then they link the same affordance set

  Scenario: An embedded resource embeds its own
    Given the author embeds this resource
      | relation     | pointer | self          | variable | from  |
      | organization | /org    | /orgs/{name}  | name     | /name |
    And the author belongs to the organization "rocketry"
    When the pull request is rendered at L0
    Then the embedded author embeds these resources
      | relation           | index | self           | name     |
      | pulls:organization |       | /orgs/rocketry | rocketry |
    And the embedded author has no member "org"

  Scenario Outline: Nothing to embed leaves nothing behind
    Given the pull request has <author>
    When the pull request is rendered at L0
    Then it embeds no "pulls:author"
    And these members are not in its state
      | member |
      | author |

    Examples:
      | author         |
      | no author      |
      | a null author  |

  Scenario Outline: An embedding the declaration can't fill is refused when the type is declared
    When a pull request type is declared embedding "<relation>" at "<pointer>" with the self "<self>"
    Then the declaration is refused, saying "<refusal>"

    Examples:
      | relation | pointer | self                   | refusal                                     |
      | owner    | /owner  | /users/{login}         | no relation owner in pulls                  |
      | author   | /author | /users/{login}/{team}  | item path variable team has no pointer      |

  Scenario: A type that embeds nothing renders no _embedded
    Given a lamp resource type declared with the profile "home"
    Then the lamp type embeds nothing
