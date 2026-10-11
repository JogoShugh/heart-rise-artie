Feature: Support tickets
  spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, B5. A ticket is one resource,
  /tickets/{id}. Its state says the subject, the customer, the priority, who
  is assigned, and the conversation so far. Its forms say what may be done
  now, and nothing else. A ticket comes into being by an act the service
  index offers, open-ticket; there is no create operation.

  Only the ticket's own rules are here. What every HEART server does (201 and
  Location for a bookmark act, 428, 412, 409, 422 for a body its schema
  refuses, search, the profile) is the conformance features' business, run
  against this server by RunHeartTckTest; how a schema is made from an input
  class is heart-core's.

  Background:
    Given the support desk has no tickets

  Scenario: A ticket opened from the bookmark is NEW, and waits for an agent
    When a customer opens a ticket from the bookmark with {"subject": "Cannot log in", "customer": "Dana Reyes", "description": "The login button does nothing.", "priority": "high"}
    Then ticket 1 is "NEW" with priority "high" and no agent
    And the ticket offers exactly "support:assign, support:comment, support:change-priority"
    And "support:resolve" is absent because "No one is assigned; assign the ticket first."
    And "support:customer-replies" is absent because "The customer has not been asked anything."

  Scenario: Assigning a new ticket gives it an agent, and the agent's acts
    Given ticket 1 was opened about "Cannot log in"
    When the agent records "assign" on ticket 1 with {"agent": "priya"}
    Then ticket 1 is "ASSIGNED" to "priya"
    And the ticket offers exactly "support:assign, support:comment, support:ask-customer, support:resolve, support:change-priority"

  Scenario: A ticket is not assigned again to the agent who has it
    Given ticket 1 was opened about "Cannot log in"
    And ticket 1 is assigned to "priya"
    When the agent records "assign" on ticket 1 with {"agent": "priya"}
    Then the desk refuses it as invalid at "/agent"

  Scenario: Asking the customer waits for their reply, and the reply hands the ticket back
    Given ticket 1 was opened about "Cannot log in"
    And ticket 1 is assigned to "priya"
    When the agent records "ask-customer" on ticket 1 with {"question": "Which browser do you use?"}
    Then ticket 1 is "WAITING_ON_CUSTOMER" to "priya"
    And the ticket offers exactly "support:comment, support:customer-replies, support:resolve, support:change-priority"
    And "support:ask-customer" is absent because "The customer has already been asked; wait for their reply."
    When the agent records "customer-replies" on ticket 1 with {"text": "Firefox, on a laptop."}
    Then ticket 1 is "ASSIGNED" to "priya"
    And the conversation of ticket 1 is "question: Which browser do you use? | reply: Firefox, on a laptop."

  Scenario: A comment is internal or for the customer, and changes nothing else
    Given ticket 1 was opened about "Cannot log in"
    When the agent records "comment" on ticket 1 with {"text": "Seen this before.", "internal": true}
    And the agent records "comment" on ticket 1 with {"text": "We are looking into it.", "internal": false}
    Then ticket 1 is "NEW" with priority "high" and no agent
    And the conversation of ticket 1 is "internal comment: Seen this before. | comment: We are looking into it."

  Scenario: A resolved ticket can only be reopened, closed, or reprioritised
    Given ticket 1 was opened about "Cannot log in"
    And ticket 1 is assigned to "priya"
    When the agent records "resolve" on ticket 1 with {"resolution": "Cleared the session cookie."}
    Then ticket 1 is "RESOLVED" to "priya"
    And the ticket offers exactly "support:reopen, support:close, support:change-priority"
    And "support:comment" is absent because "The ticket is resolved; reopen it first."

  Scenario: Reopening hands a resolved ticket back to its agent
    Given ticket 1 was opened about "Cannot log in"
    And ticket 1 is assigned to "priya"
    And ticket 1 was resolved
    When the agent records "reopen" on ticket 1 with {"reason": "It happened again."}
    Then ticket 1 is "ASSIGNED" to "priya"
    And the conversation of ticket 1 is "resolution: Fixed. | reopened: It happened again."

  Scenario: A closed ticket offers nothing, and says why
    Given ticket 1 was opened about "Cannot log in"
    And ticket 1 is assigned to "priya"
    And ticket 1 was resolved
    When the agent records "close" on ticket 1 with {}
    Then ticket 1 is "CLOSED" to "priya"
    And the ticket offers nothing
    And "support:reopen" is absent because "The ticket is closed."

  Scenario: The priority changes, but not to the one it already has
    Given ticket 1 was opened about "Cannot log in"
    When the agent records "change-priority" on ticket 1 with {"priority": "high"}
    Then the desk refuses it as invalid at "/priority"
    When the agent records "change-priority" on ticket 1 with {"priority": "urgent"}
    Then ticket 1 has priority "urgent"
