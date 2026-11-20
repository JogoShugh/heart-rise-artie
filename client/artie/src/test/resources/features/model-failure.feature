Feature: A model that can't answer stops the run at its step, saying why, never a crash
  exhibits/mcp-over-openapi: the model API answered 400 ("Your credit balance
  is too low") in the middle of a bench. A script's intent step stopped as
  "the script failed", as if its own code were wrong, and a goal run crashed
  with the exception. A model the client reasons or plans with is the
  client's own part: when it can't answer, the client failed, and the run
  stops at the step that needed it, with the API's own words, as any other
  refusal by the client does.

  Background:
    Given the service answers GET "http://garden.test/beds/2" 2 times with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms:
        water:
          _links:
            target: {href: /beds/2/actions/water}
          method: POST
          contentType: application/json
          schema: {type: object, properties: {milliliters: {type: integer}}}
      """

  Scenario: An intent step whose model can't answer stops there, refused by the client
    Given ARTIE's reasoner is Claude, through the scripted Claude API
    And the Claude API will answer 400 with "credit balance is too low"
    When this script runs with ARTIE, allowed its reasoner
      """kotlin
      heart("http://garden.test/beds/2") {
          act(intent = "water it")
      }
      """
    Then the script stopped at step 1, "intent": the model failed, refused by the client: "Claude API answered 400: credit balance is too low"

  Scenario: A goal run whose planner's model can't answer stops, saying so
    Given the file "water.goal.md" is
      """
      ---
      start: http://garden.test/beds/2
      ---

      Water the bed.
      """
    And the planner's model will fail: "Claude API answered 400: credit balance is too low"
    When the goal "water.goal.md" runs with ARTIE, captured in "goal.trace.yaml", given
      | name | value |
    Then the goal run stopped, saying "the model failed: Claude API answered 400: credit balance is too low"
