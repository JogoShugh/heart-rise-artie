Feature: The gateway is configured by its environment
  spec/PLAN-OPENAPI-GATEWAY.md, Container. A gateway runs in a container,
  so everything it needs comes from environment variables: where the upstream
  is, where its description is, how to call it, what to name the relations,
  and who may call the gateway. A setting that is missing or wrong stops the
  gateway at start, naming the variable and never a secret's value.

  Scenario: The least a gateway needs, with defaults for the rest
    Given the environment
      | UPSTREAM_URL             | http://garden:8080 |
      | GATEWAY_PROFILE          | garden             |
      | HEART_AUTH_BEARER_TOKENS | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then the upstream is "http://garden:8080"
    And the description is read from "http://garden:8080/openapi.json"
    And the gateway calls the upstream with no credential
    And the relations are named in the profile "garden"
    And the gateway listens on port 8090

  Scenario: A Bearer token for the upstream, and a description from a mounted file
    Given the environment
      | UPSTREAM_URL             | http://garden:8080 |
      | UPSTREAM_SPEC            | /config/garden.json |
      | UPSTREAM_AUTH            | bearer             |
      | UPSTREAM_TOKEN           | not-a-real-token   |
      | GATEWAY_PROFILE          | garden             |
      | PORT                     | 8383               |
      | HEART_AUTH_BEARER_TOKENS | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then the description is read from "/config/garden.json"
    And the gateway calls the upstream with "Bearer not-a-real-token"
    And the gateway listens on port 8383

  Scenario: A connector folder names the profile, so GATEWAY_PROFILE is not needed
    Given the environment
      | UPSTREAM_URL             | http://doors:8080 |
      | GATEWAY_CONNECTOR        | /connectors/doors |
      | HEART_AUTH_BEARER_TOKENS | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then the connector is read from "/connectors/doors"
    And the relations are named by the connector's profile

  Scenario: Without a connector, the gateway reads only HEART's affordance link
    Given the environment
      | UPSTREAM_URL             | http://garden:8080 |
      | GATEWAY_PROFILE          | garden             |
      | HEART_AUTH_BEARER_TOKENS | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then the gateway has no connector

  Scenario: A Basic user for the upstream
    Given the environment
      | UPSTREAM_URL             | http://garden:8080 |
      | UPSTREAM_AUTH            | basic              |
      | UPSTREAM_USER            | gate               |
      | UPSTREAM_PASSWORD        | not-a-real-password |
      | GATEWAY_PROFILE          | garden             |
      | HEART_AUTH_BEARER_TOKENS | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then the gateway calls the upstream with "Basic Z2F0ZTpub3QtYS1yZWFsLXBhc3N3b3Jk"

  Scenario Outline: A missing or wrong setting stops the gateway, naming the variable and no secret
    Given the environment
      | UPSTREAM_URL             | <url>     |
      | UPSTREAM_AUTH            | <auth>    |
      | GATEWAY_PROFILE          | <profile> |
      | HEART_AUTH_BEARER_TOKENS | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then it refuses to start, saying "<says>"

    Examples:
      | url                | auth    | profile | says                                                     |
      |                    | none    | garden  | set UPSTREAM_URL: where the upstream is                  |
      | http://garden:8080 | none    |         | set GATEWAY_PROFILE, or GATEWAY_CONNECTOR to a connector naming one |
      | http://garden:8080 | bearer  | garden  | UPSTREAM_AUTH is bearer, so set UPSTREAM_TOKEN           |
      | http://garden:8080 | forward | garden  | UPSTREAM_AUTH must be none, bearer or basic, not forward |

  Scenario: Who may call the gateway is required, as on every HEART server
    Given the environment
      | UPSTREAM_URL    | http://garden:8080 |
      | GATEWAY_PROFILE | garden             |
    When the gateway reads its configuration
    Then it refuses to start, saying "set HEART_AUTH_BASIC_USERS or HEART_AUTH_BEARER_TOKENS; a HEART service will not start open"
