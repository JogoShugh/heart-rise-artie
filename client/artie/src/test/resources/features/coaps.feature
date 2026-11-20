Feature: ARTIE over coaps, with a pre-shared key
  CoAP carries no Basic or Bearer credential. A service reached beyond its
  own machine is reached over coaps: DTLS 1.2 with a pre-shared key per
  client (RFC 7252 §9.1.3.1). ARTIE reads the key from the same places as
  any credential, scoped to the coaps origin, and holds it only inside the
  DTLS connector: nothing a model is shown, no event and no error carries
  it. These scenarios run against a scripted coaps server on this machine,
  which admits the identity "artie" with its key.

  Background:
    Given a coaps service holds bed 1 EMPTY, admitting "artie" with its key

  Scenario: With the service's key, ARTIE reads and acts over coaps
    Given ARTIE holds the coaps service's key for "artie"
    When the client enters the coaps bed
    And the client acts on "sow" with:
      """yaml
      seedType: tomato
      """
    Then the manifest offers exactly "water"
    And the coaps service received 2 requests

  Scenario: With no key for the coaps origin, nothing is sent
    Given ARTIE holds no credentials
    When the client tries to enter the coaps bed
    Then the client raised a "NotAuthenticated" refusal
    And the client raised an error mentioning "asks for a credential (PSK); ARTIE sent none"
    And the coaps service received 0 requests

  Scenario: With a wrong key, the handshake fails, and ARTIE says so without the key
    Given ARTIE holds a wrong key for "artie" for the coaps service
    When the client tries to enter the coaps bed
    Then the client raised an error mentioning "the DTLS handshake did not complete"
    And the error does not contain "wrong-key-for-artie"
    And the coaps service received 0 requests

  Scenario: A key prints as its kind alone, and appears in no event
    Given ARTIE holds the coaps service's key for "artie"
    And the client keeps an event log
    When the client enters the coaps bed
    Then the coaps credential prints as "PreShared(****)"
    And the coaps key appears in no event

  Scenario: The ARTIE a script host finds reads over coaps, with the key from the environment
    Given the environment holds the coaps service's key for "artie"
    When a script host's ARTIE enters the coaps bed
    Then it holds the bed, "EMPTY", offered "sow"
    And the coaps service received 1 request
