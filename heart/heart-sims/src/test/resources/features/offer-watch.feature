Feature: The offer watch: told on a reply when a resource offers an act
  A request to be told (RISE) about any HEART resource: the watcher reads the resource at the address the request
  names, as a HEART client does, and answers the caller's reply once its forms offer the act the request names,
  linking the resource and the ETag it was offered at. It knows nothing of the resource's service but HEART: here a
  pull request on the GitHub gateway, which offers merging only once GitHub has worked out it can be merged. If the
  act is not offered in the time the request allows, the reply is told so, as a failure, linking the resource.

  Background:
    Given the GitHub gateway's pull request 482, whose mergeability GitHub has not worked out yet
    And the watching caller's reply r-pr-482 is awaiting

  Scenario: Asked to watch for an act, the watcher makes a watch at its own address
    When the caller asks the watcher to be told when pull request 482 offers "github:merge", with reply r-pr-482, signed
    Then the watcher answers 201, the watch at "/watches/1"

  Scenario: While the resource does not offer the act, the reply is told the watcher is still looking
    Given the caller has asked the watcher to be told when pull request 482 offers "github:merge", with reply r-pr-482
    When the watcher looks, and merge is not offered yet
    Then reply r-pr-482 is awaiting, told the watcher is still looking

  Scenario: Once the resource offers the act, the reply is completed, its result linking the resource
    Given the caller has asked the watcher to be told when pull request 482 offers "github:merge", with reply r-pr-482
    And the watcher has looked, and merge is not offered yet
    When GitHub works out that pull request 482 can be merged, and the watcher looks again
    Then reply r-pr-482 is completed, its result linking pull request 482 as "github:merge" offers it
    And watch 1 is "answered"

  Scenario: An act not offered in the time allowed is a failure, linking the resource
    When the caller asks the watcher to be told when pull request 482 offers "github:merge", with reply r-pr-482, waiting at most 1 look
    And the watcher looks, and merge is not offered yet
    Then reply r-pr-482 is failed, its problem linking pull request 482
    And watch 1 is "gave up"

  Scenario: Asked to watch with no reply, and unsigned, the watcher makes a watch for the caller to read
    When the caller asks the watcher to watch pull request 482 for "github:merge", with no reply, unsigned
    Then the watcher answers 201, the watch at "/watches/1"
    And reading watch 1, it is "watching", about pull request 482

  Scenario: With no reply, the caller reads the watch to learn the resource offers the act, and no reply is told
    Given the caller has asked the watcher to watch pull request 482 for "github:merge", with no reply
    And the watcher has looked, and merge is not offered yet
    When GitHub works out that pull request 482 can be merged, and the watcher looks again
    Then reading watch 1, it is "answered", about pull request 482
    And reply r-pr-482 was told nothing

  Scenario: A request to watch that its caller did not sign is refused, and makes no watch
    When the caller asks the watcher to be told when pull request 482 offers "github:merge", with reply r-pr-482, unsigned
    Then the watcher answers 401
