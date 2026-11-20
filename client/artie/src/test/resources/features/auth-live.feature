@auth-live
Feature: Every source and both schemes, against the real services
  scripts/auth-check.sh starts the garden and the basketball service, each
  admitting one Bearer token and one Basic user made for this run only, and
  a Vault dev server holding the garden's token and basketball's user. Each
  scenario gives ARTIE one source alone, so the credential that gets in can
  have come from nowhere else. Nothing here reads ~/.artie or the shell's
  own variables.

  Scenario Outline: <source> gives the <service> its <scheme> credential, and the service admits it
    Given ARTIE's only credential source is <source>, holding the <service>'s <scheme> credential
    And the client keeps an event log
    When the client enters the <service>'s bookmark
    Then the bookmark was read with a <scheme> credential
    And the run's <service> credentials appear in no event

    Examples:
      | source               | service    | scheme |
      | the environment      | garden     | Bearer |
      | the environment      | basketball | Basic  |
      | the credentials file | garden     | Basic  |
      | the credentials file | basketball | Bearer |
      | Vault                | garden     | Bearer |
      | Vault                | basketball | Basic  |

  Scenario: With the credential from Vault, ARTIE waters bed 2, and the garden's own history shows it
    Given the beds are at their seed state
    And ARTIE's only credential source is Vault, holding the garden's Bearer credential
    When the client enters the garden's bookmark
    And the client acts on "garden:get-bed" with:
      """yaml
      id: 2
      """
    And the client acts on "garden:water-cell" with:
      """yaml
      milliliters: 250
      """
    Then the resource state contains:
      | key           | value |
      | moistureLevel | 65    |

  Scenario Outline: A real service refuses <case>
    Given ARTIE's only credential source is <source>
    When the client tries to enter the <service>'s bookmark
    Then the client raised a "NotAuthenticated" refusal
    And the client raised an error mentioning "<says>"

    Examples:
      | case                     | source                                           | service    | says                                                        |
      | a caller with none       | the environment, holding nothing                 | garden     | asks for a credential (Basic, Bearer); ARTIE sent none      |
      | a token it does not know | the environment, holding a Bearer token made up  | basketball | refused the Bearer credential ARTIE sent                    |
