Feature: Wednesday: six more kinds of change, each one release from Monday
  From OpenAPI to HEART, part four. Tuesday's frost added a state and an
  act. Real APIs change in other ways too, so the garden can be released with
  one of six other changes, chosen by garden.change (GARDEN_CHANGE). Each is
  one change from Monday, nothing else; with none set, the garden is
  Monday's, byte for byte. These scenarios read what a planted bed's
  representation offers under each change, straight from the declaration.

  Scenario Outline: A planted bed under the change "<change>"
    Given the garden is declared with the change "<change>"
    When a planted bed 2 is rendered at L0
    Then it offers exactly the form "<relation>"
    And that form is titled "<title>" and targets "<target>"
    And its milliliters may be at most <max>
    And its body requires "<required>"
    And its state names its moisture "<moisture>"

    Examples:
      | change             | relation         | title            | target                     | max  | required          | moisture      |
      | none               | garden:water-cell | Water the bed    | /beds/2/actions/water-cell | 1000 | milliliters       | moistureLevel |
      | move-url           | garden:water-cell | Water the bed    | /beds/2/actions/water      | 1000 | milliliters       | moistureLevel |
      | rename-field       | garden:water-cell | Water the bed    | /beds/2/actions/water-cell | 1000 | milliliters       | soilMoisture  |
      | rename-act         | garden:irrigate   | Water the bed    | /beds/2/actions/water-cell | 1000 | milliliters       | moistureLevel |
      | retitle            | garden:water-cell | Irrigate the bed | /beds/2/actions/water-cell | 1000 | milliliters       | moistureLevel |
      | tighter-limit      | garden:water-cell | Water the bed    | /beds/2/actions/water-cell | 500  | milliliters       | moistureLevel |
      | new-required-input | garden:water-cell | Water the bed    | /beds/2/actions/water-cell | 1000 | milliliters, method | moistureLevel |

  Scenario: The new required input offers its choices
    Given the garden is declared with the change "new-required-input"
    When a planted bed 2 is rendered at L0
    Then the water form's "method" is one of "drip, spray", for example "drip"

  Scenario Outline: A planted bed under the careful change "<change>"
    Three of the changes break a client that bound early. A careful OpenAPI
    team would make them differently, so that nothing breaks: keep the old
    path working and mark it deprecated, keep the old member beside the new
    one and mark it deprecated, and make the new input optional with a
    default. These are those three, held to the same rules on both sides,
    since one declaration serves both.
    Given the garden is declared with the change "<change>"
    When a planted bed 2 is rendered at L0
    Then it offers exactly the form "garden:water-cell"
    And that form is titled "Water the bed" and targets "<target>"
    And its body requires "milliliters"
    And its state has the members "<members>"

    Examples:
      | change             | target                     | members                     |
      | move-url-kept      | /beds/2/actions/water      | moistureLevel               |
      | rename-field-kept  | /beds/2/actions/water-cell | soilMoisture, moistureLevel |
      | new-optional-input | /beds/2/actions/water-cell | moistureLevel               |

  Scenario: The new optional input offers its choices, and defaults to drip
    Given the garden is declared with the change "new-optional-input"
    When a planted bed 2 is rendered at L0
    Then the water form's "method" is one of "drip, spray", defaulting to "drip"

  Scenario: The moved act keeps its old path, which the OpenAPI description marks deprecated
    Given the garden is declared with the change "move-url-kept"
    When the garden's OpenAPI description is rendered
    Then its operation "waterCell" is at "/beds/{id}/actions/water"
    And its operation "waterCellFormer" is at "/beds/{id}/actions/water-cell", deprecated

  Scenario: The renamed member keeps its old name too, which the OpenAPI description marks deprecated
    Given the garden is declared with the change "rename-field-kept"
    When the garden's OpenAPI description is rendered
    Then the bed's member "moistureLevel" is deprecated, and "soilMoisture" is not

  Scenario: An unknown change stops the garden at the start
    When the garden is declared with the change "flood"
    Then the declaration fails with "unknown garden.change 'flood'; known: move-url, rename-field, rename-act, retitle, tighter-limit, new-required-input, move-url-kept, rename-field-kept, new-optional-input"
