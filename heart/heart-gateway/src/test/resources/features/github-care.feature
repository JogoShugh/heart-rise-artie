Feature: A trial may turn off the care the gateway takes, one kind at a time
  spec/ideas/off-the-rails.md, the gateway's half. Each act through the
  gateway costs GitHub reads the act itself doesn't: the issue, read to know
  the state the act is checked against; the repository and the caller, read
  to work out which acts are offered now; the issue again just before the
  act, in case it changed (a race); and the issue again after it, to answer
  with the state the act left. A conditional read answered 304 is free under
  GitHub's rate limit, but it is still a round trip.

  A trial that measures what each kind of care costs may turn it off, by the
  gateway's environment (GATEWAY_CARE) or for one request
  (X-Gateway-Care), and only by saying so:

  - no-offers: the repository and the caller aren't read; every form is shown
  - no-race: the issue isn't read again just before the act
  - no-reread: the act's own answer is the state, where it is the resource
    (its offers are still worked out, so the repository and the caller are
    still read, unless no-offers says otherwise)
  - trust-copies=SECONDS: a copy read that recently is used without asking
  - off-the-rails: all four, trusting copies for 60 seconds

  A write drops the caller's copies of what it touched and of what lies
  above it, so a trusted copy is never one the caller's own act made stale.
  Never by default: by default the gateway takes every kind of care.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And the GitHub gateway's switch for acts with no validator is on
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state  | state_reason | locked | author |
      | 10     | Login fails on Safari | open   |              | false  | ada    |
      | 11     | Crash on start        | closed | completed    | false  | ada    |

  Scenario Outline: What GitHub is asked for one act, taking care as <care>, by <by>
    Given the GitHub gateway takes care as "<care>", by <by>
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    And GitHub's requests are counted from here
    And the client sends "github:close" with {} and no validator
    Then the GitHub gateway answers 200
    And the answer's state is "closed"
    And GitHub was asked
      | request                                | times        |
      | GET /repos/acme/rockets/issues/10      | <issue>      |
      | GET /repos/acme/rockets                | <repository> |
      | GET /user                              | <user>       |
      | PATCH /repos/acme/rockets/issues/10    | 1            |

    Examples:
      | care                          | by              | issue | repository | user |
      | every kind                    | its environment | 3     | 3          | 3    |
      | no-offers                     | its environment | 3     | 0          | 0    |
      | no-race                       | its environment | 2     | 2          | 2    |
      | no-reread                     | its environment | 2     | 3          | 3    |
      | trust-copies=60               | its environment | 1     | 1          | 0    |
      | no-offers, no-race, no-reread | its environment | 1     | 0          | 0    |
      | off-the-rails                 | its environment | 0     | 0          | 0    |
      | off-the-rails                 | the request     | 0     | 0          | 0    |

  Scenario: With no offers worked out, every form is shown, and GitHub says what it allows
    Given the GitHub gateway takes care as "no-offers", by its environment
    When "ada" gets "/repos/acme/rockets/issues/11" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the form "github:close" is shown

  Scenario: A trusted copy is never one the caller's own act made stale
    Given the GitHub gateway takes care as "trust-copies=60", by its environment
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    And the client sends "github:edit" with {"title":"Login fails on Safari 18"} and no validator
    And "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the pull request's title is now "Login fails on Safari 18"

  Scenario: A request that names care the gateway doesn't know is refused
    Given the GitHub gateway takes care as "no-manners", by the request
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the GitHub gateway answers 400
    And GitHub was asked nothing
