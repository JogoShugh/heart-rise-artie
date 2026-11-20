Feature: The gateway keeps a record of its upstream calls, each tied to the request it served
  spec/PLAN-LINE-BY-LINE-REPORT.md, step 4. A run report shows, under each
  HEART request a client made, the calls the gateway made upstream to answer
  it. The client sends a W3C traceparent with every request; the gateway,
  told where to keep its record (GATEWAY_RECORD), writes each upstream call
  it makes as one YAML document, with the traceparent of the request it was
  serving, the request and answer as they went, each credential written as a
  token. Nothing else ties a call to its request: not the time, not the path.
  The gateway read the repository already while serving the issue (for the
  caller's permissions), so reading it for the second request is a
  revalidation, and GitHub answers 304.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Login fails on Safari | open  |              | false  | ada    |

  Scenario: Each upstream call is kept with the traceparent of the request it served
    Given the GitHub gateway keeps a record of its upstream calls
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway, with the traceparent "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
    And "ada" gets "/repos/acme/rockets" through the GitHub gateway, with the traceparent "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"
    Then the record ties these upstream calls, among others, to the traceparent "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
      | method | path                          | status |
      | GET    | /repos/acme/rockets/issues/10 | 200    |
    And the record ties these upstream calls, among others, to the traceparent "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"
      | method | path                | status |
      | GET    | /repos/acme/rockets | 304    |
    And the record is YAML, one document per upstream call, each credential written "<token>"
