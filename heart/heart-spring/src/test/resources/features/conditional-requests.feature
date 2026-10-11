Feature: Conditional requests over HTTP: revalidating Layer 1, and acting on what was read
  SPEC §6.2 and RFC 9110 §13.1. A Layer 1 ETag names one representation of
  one state. HTTP is stateless, so an act repeats the negotiation headers its
  client read with (Accept, Accept-Profile, Prefer), and its If-Match is
  compared, whole and strongly, with the ETag those headers select now.
  heart-core decides whether a validator matches (its negotiation.feature);
  these scenarios check that heart-spring hands it the right one.

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario: A 304 has no body, and keeps the ETag the client holds
    When a client gets "/lamps/7" as hal-schema-forms with If-None-Match "\"seq-3.hsf.L0\""
    Then the response status is 304
    And the response has no body
    And the response has ETag "\"seq-3.hsf.L0\""

  Scenario: An ETag from another rung does not revalidate
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "forms=link"
    And the client gets it again at Prefer "forms=link, schema=link" with the ETag it holds
    Then the response status is 200
    And the response has ETag "\"seq-3.hsf.L2\""

  Scenario Outline: An act sent with the headers it read with matches the ETag it holds: <dialect>, Prefer "<prefer>"
    When a client gets "/lamps/7" as <dialect> with Prefer "<prefer>"
    And the client posts {"brightness": 20} to "/lamps/7/actions/dim" with the ETag and headers it synced with
    Then the response status is 200
    And the response has ETag "<etag>"

    Examples:
      | dialect          | prefer                  | etag              |
      | hal-schema-forms |                         | \"seq-4.hsf.L0\" |
      | hal-schema-forms | forms=link              | \"seq-4.hsf.L1\" |
      | hal-schema-forms | forms=link, schema=link | \"seq-4.hsf.L2\" |
      | hal-forms        |                         | \"seq-4.hf.L0\"  |

  Scenario: An ETag for another representation than the act's headers select is stale, and changes nothing
    The lamp has not changed, but the client read L2 and now asks for L0.
    Strong comparison sees two different entity-tags.
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "forms=link, schema=link"
    And the client posts {"brightness": 20} to "/lamps/7/actions/dim" with the ETag it holds but Prefer ""
    Then the response status is 412
    And lamp 7 is still at version 3

  Scenario: A write that lands after the If-Match check makes the act stale, and it changes nothing
    SPEC §6.2: 412 means another write landed first. The act is performed
    on the state whose ETag matched, never on one read again afterwards, so
    a write that lands between the check and the act is caught too.
    Given another client's dim to 80 lands on lamp 7 just after the server checks the next act's If-Match
    When a client posts {"brightness": 20} to "/lamps/7/actions/dim" as hal-schema-forms with If-Match "\"seq-3.hsf.L0\""
    Then the response status is 412
    And lamp 7 has brightness 80 at version 4

  Scenario: If-Match * matches whatever representation the act's headers select
    When a client posts {"brightness": 20} to "/lamps/7/actions/dim" as hal-schema-forms with If-Match "*"
    Then the response status is 200

  Scenario: A body that is not JSON at all is a bad request, not a schema failure, and changes nothing
    When a client posts {"brightness": to "/lamps/7/actions/dim" as hal-schema-forms with If-Match "\"seq-3.hsf.L0\""
    Then the response status is 400
    And lamp 7 is still at version 3

  Scenario: A refusal names what it refused: the act not offered, or each field that failed
    When a client posts {} to "/lamps/7/actions/switch-on" as hal-schema-forms with If-Match "\"seq-3.hsf.L0\""
    Then the problem's "rel" member is "/profiles/home#switch-on"
    When a client posts {"brightness": 0} to "/lamps/7/actions/dim" as hal-schema-forms with If-Match "\"seq-3.hsf.L0\""
    Then the problem lists a violation at "/brightness"
