Feature: Act — submitting to a known affordance

  Background:
    Given the service returns the following on enter:
      """yaml
      status: initial
      _forms:
        transition:
          href: https://example.com/resource
          method: POST
          hash: abc123
          _schema:
            type: object
            properties:
              value:
                type: string
            required: [value]
      """
    And acting on "transition" returns:
      """yaml
      status: transitioned
      _forms:
        next-action:
          href: https://example.com/resource/next
          method: POST
          hash: def456
          _schema:
            type: object
            properties:
              note:
                type: string
            required: [note]
      """
    And the Content-Type declares the H.E.A.R.T. dual profile
    When the client enters the service
    And the client acts on "transition" with:
      """yaml
      value: hello
      """

  Scenario: Resource state reflects the transition
    Then the resource state contains:
      | key    | value        |
      | status | transitioned |

  Scenario: Manifest reflects the transition
    Then the manifest affordances are:
      | rel         | present |
      | next-action | true    |
      | transition  | false   |

  Scenario: New affordance fields are accessible
    Then the "next-action" affordance has:
      | href                              | hash   |
      | https://example.com/resource/next | def456 |

