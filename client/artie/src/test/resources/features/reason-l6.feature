Feature: L6: the Reason step picks; deterministic code acts
  SPEC §6.8, §6.10.7, §10.3. Reasoning is confined to one step with a
  closed output: an affordance name and field values, or a question back
  to the user. The reasoner never sees a URI or a method, and cannot emit
  one. The agent checks the selection against the current manifest and the
  affordance's schema before anything is sent; a selection that fails is a
  recorded event, not a bad request. Then ARTIE's deterministic core acts.

  Background:
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _links:
        self: {href: /beds/2}
      _forms:
        waterCell:
          _links:
            target: {href: /beds/2/actions/water-cell}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              milliliters: {type: integer, minimum: 1, maximum: 1000}
            required: [milliliters]
      """

  Scenario: The reasoner sees names and schemas, never a URI or a method
    Given the reasoner will select "waterCell" with {"milliliters": 500}
    And the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _forms: {}
      """
    When the agent handles "water bed 2, about 500ml" at "http://garden.test/beds/2"
    Then the reasoner was shown the offers "waterCell" and no URI or method
    And the agent acted
    And the client sent POST "http://garden.test/beds/2/actions/water-cell" with headers:
      | If-Match | "seq-1.hsf.L0" |
    And that request had the JSON body {"milliliters":500}

  Scenario Outline: A selection that fails a deterministic check is rejected before any request: <case>
    Given the reasoner will select "<affordance>" with <fields>
    When the agent handles "<intent>" at "http://garden.test/beds/2"
    Then the agent rejected the selection because "<reason>"
    And the client sent no request after entering

    Examples:
      | case                     | intent               | affordance | fields                  | reason                         |
      | not in the manifest      | harvest bed 2        | harvest    | {}                      | not in the current manifest    |
      | required field missing   | water bed 2          | waterCell  | {}                      | milliliters is required        |
      | above the schema maximum | flood bed 2          | waterCell  | {"milliliters": 5000}   | milliliters is above 1000      |
      | wrong type               | water bed 2 a lot    | waterCell  | {"milliliters": "lots"} | milliliters is not an integer  |
      | field the schema lacks   | water bed 2 politely | waterCell  | {"milliliters": 5, "please": true} | please is not in the schema |

  Scenario: An ambiguous intent comes back as a question, and nothing is sent
    Given the reasoner will ask "How much water, in ml?"
    When the agent handles "water bed 2" at "http://garden.test/beds/2"
    Then the agent asked "How much water, in ml?"
    And the client sent no request after entering

  Scenario: The Claude reasoner keeps the manifest last, behind a fixed prefix
    Given the Claude API will answer with a tool call selecting "waterCell" with {"milliliters": 300}
    When the Claude reasoner reasons about "water bed 2 with 300ml" over the manifest of "http://garden.test/beds/2"
    Then the Claude request has a system prompt and one "select" tool, both marked for prompt caching
    And the Claude request's last message carries the state, the offers and the intent, in that order
    And the Claude request contains no URI from the manifest
    And the Claude reasoner selected "waterCell" with {"milliliters": 300}

  Scenario: The Claude reasoner is told to write words as the intent describes them, to the letter
    A merge watch asked for one friendly sentence, and the model wrote two
    or three in two of five comments. Words a field takes are written as
    the intent says: its length, its tone, what it says.
    Given the Claude API will answer with a tool call selecting "waterCell" with {"milliliters": 300}
    When the Claude reasoner reasons about "water bed 2 with 300ml" over the manifest of "http://garden.test/beds/2"
    Then the Claude request's system prompt says
      | part                                                                                    |
      | When a field takes words the intent describes, write them exactly as it describes them |
      | "one sentence" is one sentence                                                          |
      | nothing added that the intent doesn't ask for                                           |

  Scenario: The model may answer in words instead of the tool, and that is a question
    Current Claude models refuse a forced tool call ("tool_choice: type tool
    and any are not supported for this model"), so the request lets the model
    choose. An answer in words alone is the model asking or explaining, never
    an act: it comes back as a question for the user, and nothing is sent.
    Given the Claude API will answer with the text "Which bed do you mean?"
    When the Claude reasoner reasons about "water it" over the manifest of "http://garden.test/beds/2"
    Then the Claude request lets the model choose whether to call the tool
    And the Claude reasoner asked "Which bed do you mean?"

  Scenario: The reasoner is shown each link by relation and title, never its href, and the agent follows its choice
    Rule 6: a transition is a choice the representation offers, and a link
    is one. The reasoner names a link by relation and title; ARTIE follows it.
    Given the service answers GET "http://garden.test/beds?name=border" with:
      """
      200 OK
      Content-Type: application/hal+json
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      count: 2
      _links:
        self: {href: "/beds?name=border"}
        item:
          - {href: /beds/1, title: North border}
          - {href: /beds/2, title: South border}
      """
    And the reasoner will follow the "item" link titled "South border"
    When the agent handles "water the south border" at "http://garden.test/beds?name=border"
    Then the reasoner was shown these links and no URI
      | rel  | title        |
      | item | North border |
      | item | South border |
    And the agent followed the link to "http://garden.test/beds/2"
    And the manifest offers exactly "waterCell"

  Scenario: The reasoner is offered only the links ARTIE can follow: no templates, nothing outside HEART
    Live, a reasoner given GitHub's repository through the gateway chose the
    branches_url template over the gateway's own branches link (2026-10-18). A
    URI template needs values the reasoner can't give, and a link whose type is
    not HAL leads out of HEART: ARTIE can follow neither, so neither is offered.
    Given the service answers GET "http://garden.test/repos/acme/rockets" with:
      """
      200 OK
      Content-Type: application/hal+json
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      name: rockets
      _links:
        self: {href: /repos/acme/rockets}
        github:repos-list-branches: {href: /repos/acme/rockets/branches, type: application/hal+json, profile: "https://w3id.org/heart-rise/hal-schema-forms"}
        github:issues: {href: /repos/acme/rockets/issues}
        github:branches: {href: "https://api.github.com/repos/acme/rockets/branches{/branch}", type: application/vnd.github+json, templated: true}
        github:hooks: {href: "https://api.github.com/repos/acme/rockets/hooks", type: application/vnd.github+json}
        alternate: {href: "https://github.com/acme/rockets", type: text/html}
      """
    And the reasoner will follow the "github:branches" link
    When the agent handles "look at the branches" at "http://garden.test/repos/acme/rockets"
    Then the reasoner was shown these links and no URI
      | rel                        | title |
      | github:repos-list-branches |       |
      | github:issues              |       |
    And the agent rejected the selection because "no github:branches link titled null in http://garden.test/repos/acme/rockets"
    And the client sent no request after entering

  Scenario: A link the representation does not offer is rejected before any request
    Given the service answers GET "http://garden.test/beds?name=border" with:
      """
      200 OK
      Content-Type: application/hal+json
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      count: 1
      _links:
        item: [{href: /beds/1, title: North border}]
      """
    And the reasoner will follow the "item" link titled "Herb spiral"
    When the agent handles "water the herb spiral" at "http://garden.test/beds?name=border"
    Then the agent rejected the selection because "no item link titled Herb spiral"
    And the client sent no request after entering

  Scenario: The Claude reasoner is shown links without their hrefs, and can choose to follow one
    Given the service answers GET "http://garden.test/beds?name=border" with:
      """
      200 OK
      Content-Type: application/hal+json
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      count: 2
      _links:
        item:
          - {href: /beds/1, title: North border}
          - {href: /beds/2, title: South border}
      """
    And the Claude API will answer with a tool call following the "item" link titled "South border"
    When the Claude reasoner reasons about "water the south border" over the manifest of "http://garden.test/beds?name=border"
    Then the Claude request shows the link "item" titled "South border" and no href
    And the Claude reasoner chose to follow the "item" link titled "South border"

  Scenario: A blank title from the model is no title, so an untitled link can be chosen
    Live, Claude named the gateway's untitled branches link with the title ""
    (2026-10-18), and ARTIE, matching "" against none, refused it.
    Given the service answers GET "http://garden.test/repos/acme/rockets" with:
      """
      200 OK
      Content-Type: application/hal+json
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      name: rockets
      _links:
        github:repos-list-branches: {href: /repos/acme/rockets/branches, type: application/hal+json}
      """
    And the Claude API will answer with a tool call following the "github:repos-list-branches" link titled ""
    When the Claude reasoner reasons about "look at the branches" over the manifest of "http://garden.test/repos/acme/rockets"
    Then the Claude reasoner chose to follow the "github:repos-list-branches" link, with no title

  @llm
  Scenario: A real model picks the act from the manifest
    Given the reasoner is Claude, using the ANTHROPIC_API_KEY environment variable
    When the agent handles "please water bed 2 with 250 ml" at "http://garden.test/beds/2" without acting
    Then the reasoner selected "waterCell" with milliliters 250
