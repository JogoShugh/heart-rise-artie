@explore
Feature: ARTIE explores a running HEART server, whatever its domain
  Run with `./gradlew exploreTest` against the server at ARTIE_BASE_URL, with
  the domain's samples in ARTIE_SAMPLES and its reset command in ARTIE_RESET
  (scripts/buster-campaign.sh sets all three). The same scenarios explore the
  garden, basketball, or any other HEART server: only the samples differ.

  Background:
    Given the server is at its seed state

  Scenario Outline: From the bookmark at rung <rung>, through every act the samples allow
    Given the client works at rung <rung>
    When ARTIE explores the running server with its samples
    Then it took at least one act

    Examples:
      | rung |
      | L0   |
      | L2   |

  Scenario: The same exploration over CoAP, with nothing sent over HTTP
    When ARTIE explores the running server over CoAP with its samples
    Then it took at least one act
    And nothing went over HTTP
