Feature: How a client reads what a service serves
  A client reads a resource as HEART, never from a cache, and refuses one that
  does not say it is HEART. It names a refused read as a read, asks nothing of
  a form that is not offered, and sends an empty validator when it holds none.
  The build service serves here; its job "nightly" offers "ci:build".

  Scenario: A resource that does not name the profile is not read
    Given the build service names no profile
    And a client at rung L0 with the build service's credential
    When it tries to enter the job "nightly"
    Then it failed, saying "does not name the profile"

  Scenario: A resource that names the profile in its own links, as a static host serves it, is read
    Given the build service names its profile only in its own links
    And a client at rung L0 with the build service's credential
    When it enters the job "nightly"
    Then it offers exactly "ci:build"

  Scenario: A read that is refused is named as a read
    Given the build service refuses every read with status 503
    And a client at rung L0 with the build service's credential
    When it tries to enter the job "nightly"
    Then it was refused as "failed" with status 503
    And the refusal says "read was refused (503)"

  Scenario: Nothing can be done before entering
    Given a client at rung L0 with the build service's credential
    When it tries to read the resource again
    Then it failed, saying "enter a service first"

  Scenario: Choices are asked of an offered form only
    Given a client at rung L0 with the build service's credential
    When it enters the job "nightly"
    And it tries to read the choices of "ghost"
    Then it failed, saying "ghost is not offered now"

  Scenario: A resource is read as HEART, never from a cache
    Given a client at rung L0 with the build service's credential
    When it enters the job "nightly"
    Then the last read accepted "application/hal+json", at the profile, with no cache

  Scenario: A shared layer is read as what it may be
    Given a client at rung L2 with the build service's credential
    When it enters the job "nightly"
    Then the affordance set was read accepting "application/hal+json, application/schema+json, application/json"
    And it offers exactly "ci:build"

  Scenario: An act with no validator to send sends an empty one
    Given the build service sends no validator
    And a client at rung L0 with the build service's credential
    When it enters the job "nightly"
    And it tries to submit "ci:build" with {}
    Then it carried an empty validator

  Scenario: A client need not keep a log
    Given a client with no options but the build service's credential
    When it enters the job "nightly"
    Then the representation is at rung L0
