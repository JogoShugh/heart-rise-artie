Feature: HEART through MCP, with six fixed verbs
  Any MCP host works any HEART service through six tools that never change:
  heart_look, heart_act, heart_follow, heart_describe, heart_explain and
  heart_back. What is legal now comes back in every result: the current
  resource's state, the acts it offers with their titles and schemas, and
  its links. No tool input and no result carries an address, a method or a
  header. The host's model chooses; ARTIE builds and sends every request,
  and checks a choice against what it holds before anything is sent.
  The host speaks to heart-mcp through the official MCP client SDK, over
  stdio, as any host does.

  Background:
    Given an MCP host connected to heart-mcp with the bookmark "garden"

  Scenario: The tools are six verbs, and none takes an address
    Then the host sees exactly the tools "heart_look, heart_act, heart_follow, heart_describe, heart_explain, heart_back"
    And no tool takes an input named like an address, a method or a header

  Scenario: A service that is not configured is refused, naming those that are
    When the host calls heart_look with the service "orchard"
    Then the tool answers with an error mentioning "garden"

  @live
  Scenario: Looking at the garden shows its acts with titles and schemas, and no address
    When the host calls heart_look with the service "garden"
    Then the result offers exactly the acts "garden:search-beds, garden:get-bed, garden:build-bed"
    And every offered act has a title and a schema
    And no address appears anywhere in the result

  @live
  Scenario: From the bookmark, the host finds bed 2, which offers only watering
    When the host finds the bed "bed-2" from the garden's bookmark
    Then the result's state has "status" "PLANTED"
    And the result offers exactly the acts "garden:water-cell"
    And no address appears anywhere in the result

  @live
  Scenario: Watering bed 2 is acted, and the result is the bed's new state and acts
    Given the host has found the bed "bed-2"
    When the host calls heart_act "garden:water-cell" with {"milliliters": 250}
    Then the outcome is "acted"
    And the result's state has "moistureLevel" 65
    And the result offers exactly the acts "garden:harvest"
    And the event log shows 1 act sent

  @live
  Scenario: An act the bed does not offer is rejected, and nothing is sent
    Given the host has found the bed "bed-1"
    When the host calls heart_act "garden:harvest" with {}
    Then the outcome is "rejected-before-sending"
    And the outcome mentions "garden:harvest is not offered now"
    And the event log shows 0 acts sent

  @live
  Scenario: Fields that break the act's schema are rejected, and nothing is sent
    Given the host has found the bed "bed-1"
    When the host calls heart_act "garden:sow-seed" with {"seedType": "basil", "depthCm": 50}
    Then the outcome is "rejected-before-sending"
    And the outcome mentions "depthCm"
    And the event log shows 0 acts sent

  @live
  Scenario: Too much water is refused by the bed's rule, in the server's words
    Given the host has found the bed "bed-2"
    When the host calls heart_act "garden:water-cell" with {"milliliters": 700}
    Then the outcome is "rule-refused"
    And the outcome mentions "The bed would be waterlogged: at most 600 ml now."

  @live
  Scenario: When another client waters first, the act is stale, dropped, and the result is fresh
    Given the host has found the bed "bed-2"
    And another client waters bed 2 with 100 ml
    When the host calls heart_act "garden:water-cell" with {"milliliters": 100}
    Then the outcome is "stale"
    And the result's state has "moistureLevel" 50
    And the event log shows 1 act sent

  @live
  Scenario: Asked why bed 1 cannot be harvested, the host gets the server's reason
    Given the host has found the bed "bed-1"
    When the host calls heart_explain "garden:harvest"
    Then the explanation is "Nothing is growing yet; sow a seed and water it until it is ready."

  @live
  Scenario: Describing an offered act gives the description from the service's profile
    Given the host has found the bed "bed-2"
    When the host calls heart_describe "garden:water-cell"
    Then the description mentions "Offered only while the bed is planted."

  @live
  Scenario: Back returns to the resources before, each read again
    Given the host has found the bed "bed-2"
    When the host calls heart_back
    And the host calls heart_back
    Then the result offers exactly the acts "garden:search-beds, garden:get-bed, garden:build-bed"

  # The garden admits only callers with a credential. heart-mcp reads it as
  # ARTIE does: the environment, ~/.artie/credentials, then Vault. No tool
  # takes one, and no result shows one.

  @live
  Scenario: With the garden's credential, the host waters bed 2, and the credential is in no result
    Given the host has found the bed "bed-2"
    When the host calls heart_act "garden:water-cell" with {"milliliters": 250}
    Then the outcome is "acted"
    And the credential ARTIE sends to the garden appears in no tool result and no event

  @live
  Scenario: With no credential, looking at the garden is refused, and the error names no value
    Given heart-mcp holds no credentials
    When the host calls heart_look with the service "garden"
    Then the tool answers with an error mentioning "asks for a credential (Basic, Bearer); ARTIE sent none"

  @live
  Scenario: A credential the garden no longer admits makes the act not-authenticated, and nothing changes
    Given the host has found the bed "bed-2"
    And heart-mcp's credential for the garden is replaced by one the garden does not admit
    When the host calls heart_act "garden:water-cell" with {"milliliters": 250}
    Then the outcome is "not-authenticated"
    And the outcome mentions "refused the Bearer credential ARTIE sent"
    And the result's state has "moistureLevel" 40
    And the event log shows 1 act sent
