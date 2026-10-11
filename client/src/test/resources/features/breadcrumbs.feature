Feature: ARTIE leaves breadcrumbs: trace context and an event log
  Decided 2026-10-10 (hack log). Some right behaviour sends nothing: a
  forms link not followed, tampered bytes refused, a stale act dropped. A
  test tool such as Buster sees only the wire, so ARTIE can say what it
  decided. Each request carries a W3C Trace Context traceparent: one trace
  id per client, a new span id per request. With the event log on, ARTIE
  writes one line per decision, in OpenTelemetry log data model fields,
  tied by trace_id and span_id to the request the decision was about. The
  log is off by default; it can go to a JSON Lines file, to a URL by POST,
  or both.

  Background:
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        curies: [{name: garden, href: "/profiles/garden#{rel}", templated: true}]
      _forms:
        garden:water-cell:
          title: Water the bed
          _links: {target: {href: /beds/2/actions/water-cell}}
          method: POST
          schema: {type: object}
      """

  Scenario: Every request carries traceparent; one trace per client, a new span per request
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _forms: {}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _forms: {}
      """
    When the client enters "http://garden.test/beds/2"
    And the client enters "http://garden.test/beds/2"
    And a second client enters "http://garden.test/beds/2"
    Then every request on the wire had a W3C traceparent
    And the first two requests share a trace id but not a span id
    And the third request has another trace id

  Scenario: A forms link without Preference-Applied is skipped, and the log says so
    Given the client keeps an event log
    And the client works at rung L2
    And the service answers GET "http://garden.test/beds/3" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 3
      _links:
        self: {href: /beds/3}
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: /affordance-sets/x, integrity: sha256-AAAA}
      """
    When the client enters "http://garden.test/beds/3"
    Then the event log has "rung.applied" with "heart.rung" "L0" for the request to "http://garden.test/beds/3"
    And the event log has "forms.skipped" with "heart.forms.href" "http://garden.test/affordance-sets/x" for the request to "http://garden.test/beds/3"
    And every event line has the fields "timestamp, trace_id, span_id, severity_text, event_name, body, attributes, resource"

  Scenario: Tampered bytes are refused, and the log says so
    Given the client keeps an event log
    And the client works at rung L2
    And the service serves affordance set "planted" at "/affordance-sets/planted":
      """yaml
      _forms: {}
      """
    And bed 5 links to affordance set "planted" with integrity "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    When the client tries to enter "http://garden.test/beds/5"
    Then the event log has "integrity.refused" with "url.full" "http://garden.test/affordance-sets/planted" for the request to "http://garden.test/affordance-sets/planted"

  Scenario: An act is logged with its precondition; a stale act is logged as dropped after the re-sync
    Given the client keeps an event log
    And the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      412 Precondition Failed
      Content-Type: application/problem+json

      type: https://github.com/jogoshugh/heart-rise/problems/stale
      title: The act is stale
      status: 412
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _forms: {}
      """
    When the client enters "http://garden.test/beds/2"
    And the client tries to act on "garden:water-cell" with:
      """yaml
      {}
      """
    Then the event log has "act.sent" with "heart.if_match" "\"seq-1.hsf.L0\"" for the request to "http://garden.test/beds/2/actions/water-cell"
    And the event log has "act.refused" with "heart.problem.type" "https://github.com/jogoshugh/heart-rise/problems/stale" for the request to "http://garden.test/beds/2/actions/water-cell"
    And the event log has "act.refused" with "heart.outcome" "discarded after re-sync" for the request to "http://garden.test/beds/2/actions/water-cell"

  Scenario: An act the manifest does not offer is logged as absent, and nothing is sent
    Given the client keeps an event log
    When the client enters "http://garden.test/beds/2"
    And the client tries to act on "garden:harvest" with:
      """yaml
      {}
      """
    Then the event log has "affordance.absent" with "heart.rel" "garden:harvest" for the request to "http://garden.test/beds/2"
    And the client sent no request after entering

  Scenario: A client that is done says so, so a watcher can close its open questions
    Given the client keeps an event log
    When the client enters "http://garden.test/beds/2"
    And the client is done
    Then the last event is "client.done"

  Scenario: The log goes to a JSON Lines file and to a URL by POST when both are set
    Given the client writes events to a file and posts them to "http://buster.test/_buster/events"
    And the service answers POST "http://buster.test/_buster/events" with:
      """
      204 No Content
      """
    When the client enters "http://garden.test/beds/2"
    Then the event file has a "rung.applied" line
    And the client posted a "rung.applied" line to "http://buster.test/_buster/events"

  Scenario: With the log off, ARTIE writes nothing
    Given the client writes events to a file only when ARTIE_EVENTS is set, and it is not
    When the client enters "http://garden.test/beds/2"
    Then no event file was written
