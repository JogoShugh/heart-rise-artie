Feature: A caller may ask the gateway to pass its request through to GitHub, untouched
  spec/ideas/gateway-pass-through.md, the transparent rung. A plain client,
  one written against GitHub's own API, can be pointed at the gateway and
  cross the same layer a HEART client crosses. A request carrying the header
  X-Gateway-Mode: pass-through goes to GitHub as it came, as the caller, and
  GitHub's answer comes back as it is: its status, its headers, its body. No
  validator is asked for, nothing is checked, and nothing is added. Every
  other request is served as HEART, at the same addresses, at the same time.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Login fails on Safari | open  |              | false  | ada    |

  Scenario Outline: One address, two modes: <mode>
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway, <mode>
    Then the GitHub gateway answers 200
    And the answer <is>

    Examples:
      | mode               | is                                                   |
      | passed through     | is GitHub's own, with its ETag and no HEART controls |
      | as HEART           | is HEART's, with its forms                           |

  Scenario: A write passes through as GitHub's own operation, with no validator
    When "ada" sends POST "/repos/acme/rockets/issues" with {"title":"Add dark mode"} through the GitHub gateway, passed through
    Then the GitHub gateway answers 201
    And GitHub was asked to POST "/repos/acme/rockets/issues" with {"title":"Add dark mode"}
    And the answer is GitHub's own, with no HEART controls

  Scenario: GitHub's refusal comes back as GitHub gave it
    When "eve" gets "/repos/acme/rockets/issues/10" through the GitHub gateway, passed through
    Then the GitHub gateway answers 401
    And the answer's message is "Bad credentials"

  Scenario: A request passed through with no credential is refused by the gateway, as any other
    When a caller with no credential gets "/repos/acme/rockets/issues/10" through the GitHub gateway, passed through
    Then the GitHub gateway answers 401
    And GitHub was asked nothing
