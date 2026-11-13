Feature: A connector says its upstream in a profile's own words, on the way out
  A profile names what a client may do and what a resource holds, whoever
  serves it: scm:merge, a pull request's state. Each connector maps those
  words to its own upstream's. An act's field may take the profile's values,
  each sent upstream as the members the upstream takes; a resource may add
  the profile's members to its state, made from the upstream's, and any
  member it says otherwise keeps the upstream's own value under upstream.

  Scenario: A field in the profile's words is sent upstream as the members the upstream takes
    Given the GitHub gateway reads GitHub's description with the shipped connector, its act "merge" taking "method" as
      | value  | sends                     |
      | merge  | {"merge_method":"merge"}  |
      | squash | {"merge_method":"squash"} |
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"
    And pull request 482's head is at "a1b2c3"
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the "github:merge" form offers "method" as one of "merge, squash"
    When the client sends "github:merge" with {"method":"squash"} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to PUT "/repos/acme/rockets/pulls/482/merge" with {"merge_method":"squash","sha":"a1b2c3"}

  Scenario: A resource named only by a page's address is still served at its own path
    GitLab's merge request names its project and number only in its page's
    address (web_url), whose path is not the read's. The connector says that
    path's template; the read's variables are matched from it.
    Given the GitHub gateway reads GitHub's description with the shipped connector, its pulls addressed by "/html_url" as "/{owner}/{repo}/pull/{pull_number}"
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer holds
      | pointer           | value                     |
      | /_links/self/href | /repos/acme/rockets/pulls/482 |

  Scenario: A resource's state has the profile's members, the upstream's own kept under upstream
    Given the GitHub gateway reads GitHub's description with the shipped connector, its pulls saying
      | member | from      | values                             |
      | state  | /state    | {"open":"opened","closed":"closed"} |
      | head   | /head/sha |                                    |
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"
    And pull request 482's head is at "a1b2c3"
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer holds
      | pointer         | value  |
      | /state          | opened |
      | /head           | a1b2c3 |
      | /upstream/state | open   |
