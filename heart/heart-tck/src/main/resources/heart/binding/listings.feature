Feature: Listings: plain lists a server serves for its forms' choices
  HAL-SCHEMA-FORMS-HR Rule 8 lets a form take a property's choices from
  another resource, by a link the client reads. A domain declares such a list
  as a listing: a path, and the values for the query it is asked with. The
  library serves it as plain JSON, revalidated on every use like any Layer 1
  resource, so the domain never touches HTTP.

  Scenario: A declared listing is served as plain JSON, filtered by its query
    When a client gets "/colours?tone=warm" with Accept "application/json"
    Then the response status is 200
    And the response body is exactly {"colours":[{"name":"amber","tone":"warm"},{"name":"red","tone":"warm"}]}
    And the response has Cache-Control "no-cache"

  Scenario: A listing is revalidated by its ETag
    When a client gets "/colours?tone=cool" with Accept "application/json"
    And a client gets "/colours?tone=cool" again with the ETag it was given
    Then the response status is 304
