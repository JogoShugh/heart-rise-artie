Feature: A resource links its sub-collections
  spec/PLAN-GITHUB-ISSUES.md, step I6. Real APIs keep collections under an
  item: an issue's comments are at /repos/{owner}/{repo}/issues/{number}/comments.
  A type may declare such sub-collections, each by a relation of its profile
  and a URI template whose variables are the item path's. Each resource
  links them, filled from its own state, beside its self link, so a client
  follows a link and never builds the address. The links are Layer 1: they
  name this resource, so they are never in a shared affordance set.

  Background:
    Given a pull request type declared with the item path "/repos/{owner}/{repo}/pulls/{number}"
    And the pull request type links these sub-collections
      | relation | path                                          |
      | comments | /repos/{owner}/{repo}/pulls/{number}/comments |
    And pull request 482 of "acme/rockets" is open

  Scenario Outline: The resource links each sub-collection, filled from its state, at every rung
    When the pull request is rendered at L<rung>
    Then it links these sub-collections
      | link           | href                                   |
      | pulls:comments | /repos/acme/rockets/pulls/482/comments |
    And its curies name the profile "pulls"

    Examples:
      | rung |
      | 0    |
      | 1    |
      | 2    |

  Scenario: The links are not in the shared affordance set
    Given pull request 7 of "acme/engines" is open
    When both pull requests are rendered at L1
    Then they link the same affordance set

  Scenario: HAL-FORMS links the same sub-collections
    When the pull request is rendered in HAL-FORMS
    Then it links these sub-collections
      | link           | href                                   |
      | pulls:comments | /repos/acme/rockets/pulls/482/comments |

  Scenario Outline: A sub-collection the declaration can't fill is refused when the type is declared
    When a pull request type is declared linking "<relation>" at "<path>"
    Then the declaration is refused, saying "<refusal>"

    Examples:
      | relation | path                                                 | refusal                                                |
      | comments | /repos/{owner}/{repo}/pulls/{number}/comments/{page} | pulls: the sub-collection comments has no value for page |
      | reviews  | /repos/{owner}/{repo}/pulls/{number}/reviews         | no relation reviews in pulls                           |

  Scenario: A type that links no sub-collections renders as before, byte for byte
    Given a lamp resource type declared with the profile "home"
    Then the lamp type links no sub-collections
