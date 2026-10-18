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
