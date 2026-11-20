Feature: heart-mcp shows an act's schema once, and no HEART plumbing in the state
  At L2, a HEART client fetches a shared form set once and knows it after by its
  address, so the wire carries each form once. heart-mcp does the same for the
  model: an act's schema is shown the first time the model meets it; after that,
  while it is the same, the act shows its name and title and says its schema is as
  before. A schema that changes, such as a choice from state, is shown again. The
  state is the resource's own: members HEART adds for clients, such as the
  bookmark's hr:manifest, are ARTIE's to read, not the model's.

  Scenario: An act's schema is shown the first time, and after that is said to be as before
    Given heart-mcp holds an employee offering these acts
      | act           | field  |
      | staff:dismiss |        |
      | staff:update  | role   |
    When heart-mcp shows what it holds
    Then these acts show their schemas
      | act           |
      | staff:dismiss |
      | staff:update  |
    When heart-mcp shows what it holds again
    Then these acts show their schemas as before, with their titles
      | act           |
      | staff:dismiss |
      | staff:update  |

  Scenario: An act whose schema changed is shown with its new schema
    Given heart-mcp holds an employee offering these acts
      | act           | field  |
      | staff:dismiss |        |
      | staff:update  | role   |
    When heart-mcp shows what it holds
    And the employee now offers these acts
      | act           | field  |
      | staff:dismiss |        |
      | staff:update  | reason |
    And heart-mcp shows what it holds again
    Then these acts show their schemas
      | act          |
      | staff:update |
    And these acts show their schemas as before, with their titles
      | act           |
      | staff:dismiss |

  Scenario: The state shows the resource's own members, not HEART's plumbing
    Given heart-mcp holds a bookmark whose state has a title and an hr:manifest
    When heart-mcp shows what it holds
    Then the view's state has exactly these members
      | member |
      | title  |
