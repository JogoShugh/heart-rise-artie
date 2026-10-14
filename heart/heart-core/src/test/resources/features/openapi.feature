Feature: An OpenAPI 3.1 description of everything a server could ever be asked, from its declarations
  The first question a client asks is what can ever be done with this API.
  Every act a HEART server offers is declared, with its schema, and every
  offer key is listed in advance (SPEC §6.9), so the union of everything the
  server could ever offer is known before any request. heart-core renders it
  as an OpenAPI 3.1 document describing the plain JSON rendering (the
  OpenAPI twin): one operation per act, the act's own schema in components,
  the state as a schema whose offer key is a closed enum, and the rule about
  state where OpenAPI can carry it: in prose. The document is canonical JSON,
  so the same declarations always give the same bytes.

  Background:
    Given a lamp resource type declared with the profile "home"
    And the lamp keeps its past
    When the OpenAPI description of the lamp is rendered at version "1.0.0"

  Scenario: It is a valid OpenAPI 3.1 document, and each schema in it is valid JSON Schema 2020-12
    Then it validates against the OpenAPI 3.1 schema
    And every schema in its components is a valid JSON Schema 2020-12 schema
    And its "openapi" is "3.1.1"
    And its "info.title" is "Lamps"
    And its "info.version" is "1.0.0"

  Scenario: It is the same bytes every time, and its validator names them
    When the OpenAPI description of the lamp is rendered at version "1.0.0" again
    Then the two renderings are identical bytes
    And its validator is "openapi-" and the first 16 hex digits of its body's digest

  Scenario: Every operation the server could ever serve, and no other
    Then its operations are exactly
      | method | path                           | operationId   |
      | GET    | /lamps                         | searchLamps   |
      | GET    | /lamps/{id}                    | getLamp       |
      | GET    | /lamps/{id}/history            | getLampHistory |
      | POST   | /lamps/{id}/actions/switch-on  | switchOn      |
      | POST   | /lamps/{id}/actions/switch-off | switchOff     |
      | POST   | /lamps/{id}/actions/dim        | dim           |
      | POST   | /actions/install-lamp          | installLamp   |

  Scenario: An act's request body is the act's own schema, the very one its form carries
    Then the operation "dim" takes a required application/json body "#/components/schemas/Dim"
    And the component "Dim" is the schema of the act "dim"
    And the operation "switchOn" takes a required application/json body "#/components/schemas/SwitchOff"

  Scenario: The rule about state is prose only: where OpenAPI can say it
    Then the description of "dim" ends "Only allowed when status is ON."
    And the description of "switchOn" ends "Only allowed when status is OFF."
    And the summary of "dim" is "Dim the lamp"

  Scenario: The state is a schema whose offer key is a closed enum of every declared key
    Then the operation "getLamp" answers 200 with "#/components/schemas/Lamp"
    And the component "Lamp" has the property "status" with the enum "ON, OFF"

  Scenario: An act answers with the state, and documents its refusals; no validator is asked for
    Then the operation "dim" answers 200 with "#/components/schemas/Lamp"
    And the operation "dim" documents the responses "200, 401, 404, 409, 422"
    And the operation "dim" has no "If-Match" parameter
    And the operation "installLamp" documents the responses "201, 401, 422"

  Scenario: Both credentials the service admits are security schemes
    Then its security schemes are "basic" as http "basic" and "bearer" as http "bearer"

  Scenario: Another version is other bytes
    When the OpenAPI description of the lamp is rendered at version "1.1.0" again
    Then the two renderings are not identical bytes
