Feature: Which links and embedded resources a client takes
  A client follows only a link a representation offers, by its relation and,
  when one is asked for, its title. Only a link with an address is a link, only
  a text is a title, only an embedded object with a self address is a resource,
  and everything but the four HAL controls is state. The GitHub service lists
  acme/web's pull requests, each item titled; the build service's job
  "nightly" is shown as a broken service would send it.

  Scenario Outline: A link is followed only if it is offered: <case>
    Given a client at rung L0 with the GitHub service's credential
    When it enters the pull requests of "acme/web"
    And it tries to follow "<rel>" titled "<title>"
    Then <outcome>

    Examples:
      | case                   | rel   | title                      | outcome                                                                         |
      | its relation and title | item  | Speed up the nightly build | it holds a representation                                                       |
      | no such title          | item  | Slow it down               | it failed, saying "no item link titled Slow it down is offered"                 |
      | no such relation       | ghost | Speed up the nightly build | it failed, saying "no ghost link titled Speed up the nightly build is offered" |

  Scenario Outline: A link is followed by its relation alone, when no title is asked for: <case>
    Given a client at rung L0 with the GitHub service's credential
    When it enters the pull requests of "acme/web"
    And it tries to follow "<rel>" with no title
    Then <outcome>

    Examples:
      | case               | rel   | outcome                                      |
      | whatever its title | item  | it holds a representation                    |
      | no such relation   | ghost | it failed, saying "no ghost link is offered" |

  Scenario: A resource keeps state in anything but the four controls
    Given the build service's job also holds {"_templates": {}, "kept": 1}
    And a client at rung L0 with the build service's credential
    When it enters the job "nightly"
    Then the state has exactly these members
      | member |
      | id     |
      | name   |
      | queued |
      | kept   |

  Scenario: Only a link with an address is a link, and only a text is a title
    Given the build service's job links only {"odd": "text", "bare": {"title": "x"}, "numbered": {"href": "/a", "title": 5}, "many": [{"href": "/b"}, {"href": "/c"}]}
    And a client at rung L0 with the build service's credential
    When it enters the job "nightly"
    Then it holds exactly these links, with no titles
      | relation | href |
      | numbered | /a   |
      | many     | /b   |
      | many     | /c   |

  Scenario: Only an embedded object with a self address is a resource
    Given the build service's job embeds only {"a": {"_links": {"self": {"href": "/a"}}}, "b": "text", "c": {"_links": {}}, "d": {"_links": {"self": {}}}, "e": {"_links": {"self": "text"}}}
    And a client at rung L0 with the build service's credential
    When it enters the job "nightly"
    Then it holds embedded resources at exactly "/a"
