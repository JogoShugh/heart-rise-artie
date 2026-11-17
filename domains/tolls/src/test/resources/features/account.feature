Feature: An E-ZPass-style toll account, and the fares charged to it
  An account is one resource, /accounts/{id}. Its state says who holds it,
  the balance, its tags and which are lost, and every trip and payment, in
  order. Its forms say what may be done now, and nothing else.

  The toll rules here: an account opens with at least $25; under $10 it is
  low; below $0 it is suspended, and its tags no longer pay at a plaza until
  funds bring it back; a trip's fare is set by the plaza, the vehicle's class
  and the time of day; a trip is charged to a tag on the account that is not
  lost; a trip can be disputed, and refunded, once; closing refunds the
  balance.

  Only the account's own rules are here. What every HEART server does is
  tested once in the HEART library, and RunHeartTckTest runs the conformance
  features against this server.

  Background:
    Given account 1, held by Pat Miller

  Scenario: A new account offers only adding funds, and the rest say why not
    When the holder opens account 1
    Then the account offers exactly
      | act             |
      | tolls:add-funds |
    And "tolls:record-trip" is absent because "The account is not open yet; add at least $25 first."

  Scenario: An account opens with at least $25
    When the holder records "add-funds" on account 1 with {"amount": 20}
    Then the account refuses it by a toll rule at "/amount"
    And account 1 is "NEW" with a balance of "0.00"

  Scenario: Funds of $25 open the account: every act of an open account is offered
    When the holder records "add-funds" on account 1 with {"amount": 25}
    Then account 1 is "ACTIVE" with a balance of "25.00"
    And the account offers exactly
      | act                   |
      | tolls:add-funds       |
      | tolls:add-tag         |
      | tolls:record-trip     |
      | tolls:report-tag-lost |
      | tolls:dispute-toll    |
      | tolls:close-account   |

  Scenario Outline: A trip at <plaza> by a <class> at <time> costs <fare>
    Given account 1 is open with $40 and the tag "T-1001"
    When the plaza records "record-trip" on account 1 with {"tag": "T-1001", "plaza": "<plaza>", "vehicleClass": "<class>", "time": "<time>"}
    Then the trip is charged "<fare>"
    And account 1 is "ACTIVE" with a balance of "<balance>"

    Examples:
      | plaza           | class           | time     | fare  | balance |
      | harbor-bridge   | car             | peak     | 7.00  | 33.00   |
      | harbor-bridge   | car             | off-peak | 5.60  | 34.40   |
      | north-tunnel    | car             | peak     | 6.50  | 33.50   |
      | turnpike-exit-9 | car             | off-peak | 2.60  | 37.40   |
      | harbor-bridge   | two-axle-truck  | peak     | 14.00 | 26.00   |
      | north-tunnel    | five-axle-truck | off-peak | 26.00 | 14.00   |

  Scenario: The tag charged is chosen from the account's own tags that are not lost
    HAL-SCHEMA-FORMS-HR Rule 8. The form does not take any tag: it says the
    choices are the account's tags, at /tags, leaving out the lost ones.
    Given account 1 is open with $40 and the tag "T-1001"
    When the holder opens account 1
    Then the form "tolls:record-trip" takes "tag" from "/tags", the "id" of each tag not lost

  Scenario: A lost tag no longer pays: it is not one of the choices
    Given account 1 is open with $40 and the tags "T-1001" and "T-1002"
    And the tag "T-1001" was reported lost
    When the plaza records "record-trip" on account 1 with {"tag": "T-1001", "plaza": "harbor-bridge", "vehicleClass": "car", "time": "peak"}
    Then the account refuses it as not one of the choices at "/tag"
    And account 1 is "ACTIVE" with a balance of "40.00"

  Scenario: A tag already on the account is not added again
    Given account 1 is open with $40 and the tag "T-1001"
    When the holder records "add-tag" on account 1 with {"tag": "T-1001"}
    Then the account refuses it by a toll rule at "/tag"

  Scenario Outline: A balance of <balance> after a trip leaves the account <phase>
    Given account 1 is open with $<funds> and the tag "T-1001"
    When the plaza records "record-trip" on account 1 with {"tag": "T-1001", "plaza": "harbor-bridge", "vehicleClass": "car", "time": "peak"}
    Then account 1 is "<phase>" with a balance of "<balance>"

    Examples:
      | funds | balance | phase       |
      | 25    | 18.00   | ACTIVE      |
      | 15    | 8.00    | LOW_BALANCE |

  Scenario: Below $0 the account is suspended: its tags stop paying, and only funds and disputes are offered
    Given account 1 is open with $25 and the tag "T-1001"
    And the tag "T-1001" was charged "five-axle-truck" trips at "harbor-bridge" at "peak" 1 time
    Then account 1 is "SUSPENDED" with a balance of "-10.00"
    And the account offers exactly
      | act                |
      | tolls:add-funds    |
      | tolls:dispute-toll |
    And "tolls:record-trip" is absent because "The balance is below zero, so the tags no longer pay; add funds first."
    When the holder records "add-funds" on account 1 with {"amount": 30}
    Then account 1 is "ACTIVE" with a balance of "20.00"

  Scenario: A disputed trip is refunded, once
    Given account 1 is open with $40 and the tag "T-1001"
    And the plaza recorded "record-trip" on account 1 with {"tag": "T-1001", "plaza": "north-tunnel", "vehicleClass": "car", "time": "peak"}
    When the holder disputes account 1's entry number 3
    Then account 1 is "ACTIVE" with a balance of "40.00"
    When the holder disputes account 1's entry number 3
    Then the account refuses it by a toll rule at "/sequenceNo"

  Scenario: Only a trip can be disputed
    Given account 1 is open with $40 and the tag "T-1001"
    When the holder disputes account 1's entry number 1
    Then the account refuses it by a toll rule at "/sequenceNo"

  Scenario: Closing the account refunds the balance, and then nothing is offered
    Given account 1 is open with $40 and the tag "T-1001"
    When the holder records "close-account" on account 1 with {}
    Then account 1 is "CLOSED" with a balance of "0.00"
    And the account says "40.00" was refunded
    And the account offers nothing
