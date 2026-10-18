Feature: heart-mcp shows embedded resources, followable, with no address
  SPEC §6.1, decided 2026-10-18 (spec/PLAN-HAL-EMBEDDED.md). A resource may
  embed others. heart-mcp shows each one beside the links, by relation and
  title, with its state, so a model reads it without following it. A model
  may follow it like a link, which fetches it so it can be acted on. As for
  everything else heart-mcp shows, no address appears.

  Scenario: Each embedded resource is shown with its state, and may be followed
    Given heart-mcp holds a resource that embeds these resources
      | relation        | title | login |
      | pulls:author    | Ada   | ada   |
      | pulls:reviewers | Lin   | lin   |
      | pulls:reviewers | Kay   | kay   |
    When heart-mcp shows what it holds
    Then the view embeds these resources
      | relation        | title | login |
      | pulls:author    | Ada   | ada   |
      | pulls:reviewers | Lin   | lin   |
      | pulls:reviewers | Kay   | kay   |
    And a model may follow each of them by relation and title
    And no address appears in the view

  Scenario: Only links ARTIE can follow are shown: no templates, nothing outside HEART
    As for ARTIE's own reasoner (reason-l6.feature): a model given a template or
    a link out of HEART chose one live, and nothing could follow it.
    Given heart-mcp holds a resource with these links
      | relation                   | type                        | templated |
      | github:repos-list-branches | application/hal+json        | false     |
      | github:issues              |                             | false     |
      | github:branches            | application/vnd.github+json | true      |
      | github:hooks               | application/vnd.github+json | false     |
      | alternate                  | text/html                   | false     |
    When heart-mcp shows what it holds
    Then the view shows exactly these links
      | relation                   |
      | github:repos-list-branches |
      | github:issues              |
