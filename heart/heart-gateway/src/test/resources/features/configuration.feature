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

  Scenario: Calling the upstream as each caller, the gateway holds no credentials of its own
    Given the environment
      | UPSTREAM_URL    | https://api.github.com |
      | UPSTREAM_AUTH   | caller                 |
      | GATEWAY_PROFILE | github                 |
    When the gateway reads its configuration
    Then the gateway calls the upstream with each caller's own credential
    And the gateway admits any Basic or Bearer credential, for the upstream to judge, and no request without one

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
      | http://garden:8080 | forward | garden  | UPSTREAM_AUTH must be none, bearer, basic or caller, not forward |

  Scenario Outline: A development switch lets acts come with no validator, for a trial: <case>
    spec/ideas/off-the-rails.md. Never by default: HEART asks every act for
    the validator of the state it was chosen from. A trial that measures
    what that costs may turn it off, and only by saying so.
    Given the environment
      | UPSTREAM_URL               | http://garden:8080 |
      | GATEWAY_PROFILE            | garden             |
      | HEART_ACTS_WITHOUT_VALIDATOR | <switch>         |
      | HEART_AUTH_BEARER_TOKENS   | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then acts <need>

    Examples:
      | case     | switch  | need                          |
      | unset    |         | need a validator              |
      | switched | allowed | may come with no validator    |

  Scenario: The switch says only allowed
    Given the environment
      | UPSTREAM_URL                 | http://garden:8080 |
      | GATEWAY_PROFILE              | garden             |
      | HEART_ACTS_WITHOUT_VALIDATOR | yes                |
      | HEART_AUTH_BEARER_TOKENS     | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then it refuses to start, saying "HEART_ACTS_WITHOUT_VALIDATOR must be allowed, or unset, not yes"

  Scenario Outline: The gateway serves https from a keystore, or plain http without one: <case>
    A local gateway in front of an https upstream should not be the only hop
    without TLS: a client then pays a handshake to it, as it would to the
    upstream. The keystore's password is a secret, never written back.
    Given the environment
      | UPSTREAM_URL             | http://garden:8080 |
      | GATEWAY_PROFILE          | garden             |
      | GATEWAY_TLS_KEYSTORE     | <keystore>         |
      | GATEWAY_TLS_PASSWORD     | <password>         |
      | HEART_AUTH_BEARER_TOKENS | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then <serves>

    Examples:
      | case             | keystore             | password          | serves                                                    |
      | no keystore      |                      |                   | the gateway serves plain http                             |
      | a keystore       | /config/gateway.p12  | not-a-real-secret | the gateway serves https from "/config/gateway.p12"       |
      | no password      | /config/gateway.p12  |                   | it refuses to start, saying "GATEWAY_TLS_KEYSTORE is set, so set GATEWAY_TLS_PASSWORD" |

  Scenario Outline: A trial may turn off kinds of care the gateway takes: <case>
    spec/ideas/off-the-rails.md, and github-care.feature. Never by default.
    Given the environment
      | UPSTREAM_URL             | http://garden:8080 |
      | GATEWAY_PROFILE          | garden             |
      | GATEWAY_CARE             | <care>             |
      | HEART_AUTH_BEARER_TOKENS | 5e884898da28047151d0e56f8dc6292773603d0d6aff2bdcb2c38e8f3b2ba4c1 |
    When the gateway reads its configuration
    Then <takes>

    Examples:
      | case          | care                     | takes                                                                  |
      | unset         |                          | the gateway takes every kind of care                                   |
      | some off      | no-offers, no-race       | the gateway takes care as "no-offers, no-race"                         |
      | all off       | off-the-rails            | the gateway takes care as "no-offers, no-race, no-reread, trust-copies=60" |
      | a word it doesn't know | no-manners      | it refuses to start, saying "GATEWAY_CARE names no-offers, no-race, no-reread, trust-copies=SECONDS or off-the-rails, not no-manners" |

  Scenario: Who may call the gateway is required, as on every HEART server
    Given the environment
      | UPSTREAM_URL    | http://garden:8080 |
      | GATEWAY_PROFILE | garden             |
    When the gateway reads its configuration
    Then it refuses to start, saying "set HEART_AUTH_BASIC_USERS or HEART_AUTH_BEARER_TOKENS; a HEART service will not start open"
