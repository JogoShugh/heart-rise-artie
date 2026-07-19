Feature: Act — rejection of unknown affordances

  Background:
    Given the service returns the following on enter:
      """yaml
      status: initial
      _forms:
        transition:
          href: https://example.com/resource
          method: POST
          hash: abc123
          _schema:
            type: object
            properties:
              value:
                type: string
            required: [value]
      """
    And the Content-Type declares the H.E.A.R.T. dual profile
    And the client enters the service

  Scenario: Acting on an affordance absent from the manifest raises an error
    When the client attempts to act on "nonexistent" with:
      """yaml
      value: hello
      """
    Then the client raises an error
