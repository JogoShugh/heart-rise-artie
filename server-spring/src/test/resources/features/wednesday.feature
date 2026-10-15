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

  Scenario: An unknown change stops the garden at the start
    When the garden is declared with the change "flood"
    Then the declaration fails with "unknown garden.change 'flood'; known: move-url, rename-field, rename-act, retitle, tighter-limit, new-required-input"
