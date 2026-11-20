Feature: Looking after the garden from a dashboard
  The garden team's regression suite. It runs against every dashboard, at
  every release, as a black box: it opens a bed, reads its buttons and its
  status, and presses buttons, the way a person would. The same scenarios
  run on the bespoke dashboard and on the ARTIE dashboard; only the page
  objects that find a button on each page differ.

  Monday's scenarios were written with Monday's release. The @tuesday ones
  were added with the frost, when the server changed.

  Background:
    Given the garden is at its seed state

  Scenario: A planted bed can be watered
    When the operator opens bed 2
    Then bed 2 shows exactly the buttons "Water the bed"
    When the operator presses "Water the bed" on bed 2
    Then bed 2's moisture is 65
    And the dashboard sent nothing the garden refused

  Scenario: An empty bed can be sown, and not watered
    When the operator opens bed 1
    Then bed 1 shows exactly the buttons "Sow a seed"

  Scenario: A ready bed can be harvested
    When the operator opens bed 3
    Then bed 3 shows exactly the buttons "Harvest the crop"
    When the operator presses "Harvest the crop" on bed 3
    Then bed 3 shows exactly the buttons "Sow a seed"
    And the dashboard sent nothing the garden refused

  @tuesday
  Scenario: After the frost, a frozen bed shows it is frozen
    Given a hard frost has hit the garden
    When the operator opens bed 2
    Then bed 2's status reads frozen

  @tuesday
  Scenario: After the frost, a frozen bed offers thaw, and not water
    Given a hard frost has hit the garden
    When the operator opens bed 2
    Then bed 2 shows exactly the buttons "Thaw the bed"

  @tuesday
  Scenario: Thawing a frozen bed lets it be watered again
    Given a hard frost has hit the garden
    When the operator opens bed 2
    And the operator presses "Thaw the bed" on bed 2
    Then bed 2 shows exactly the buttons "Water the bed"
    And the dashboard sent nothing the garden refused

  @tuesday
  Scenario: Every button a frozen bed shows is one the garden accepts
    Given a hard frost has hit the garden
    When the operator opens bed 2
    And the operator presses every button bed 2 shows
    Then the dashboard sent nothing the garden refused
