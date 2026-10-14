Feature: Where ARTIE finds credentials
  Credentials come from places a person or a deployment controls, never
  from a representation or a model. Each source is found by convention,
  keyed by origin. A source that is set up wrongly stops ARTIE at the
  start, with a message that names variables, files or paths, never a value.

  Rule: Environment variables name the origin they are for

    Scenario Outline: The variable names come from the origin: <origin>
      Then the credential variables for "<origin>" are "<token>", "<user>" and "<password>"

      Examples:
        | origin                      | token                           | user                           | password                           |
        | http://localhost:8080       | ARTIE_TOKEN_LOCALHOST_8080      | ARTIE_USER_LOCALHOST_8080      | ARTIE_PASSWORD_LOCALHOST_8080      |
        | https://garden.example      | ARTIE_TOKEN_GARDEN_EXAMPLE_443  | ARTIE_USER_GARDEN_EXAMPLE_443  | ARTIE_PASSWORD_GARDEN_EXAMPLE_443  |
        | https://api.my-farm.io:8443 | ARTIE_TOKEN_API_MY_FARM_IO_8443 | ARTIE_USER_API_MY_FARM_IO_8443 | ARTIE_PASSWORD_API_MY_FARM_IO_8443 |

    Scenario Outline: What the environment holds decides the credential: <case>
      Given the environment holds:
        | <first>  | <first value>  |
        | <second> | <second value> |
      When ARTIE reads credentials from the environment
      Then the credential for "http://localhost:8080" is <credential>

      Examples:
        | case           | first                         | first value | second                        | second value | credential                     |
        | a token        | ARTIE_TOKEN_LOCALHOST_8080    | t0k3n       | HOME                          | /home/coach  | the Bearer credential "t0k3n"  |
        | user, password | ARTIE_USER_LOCALHOST_8080     | coach       | ARTIE_PASSWORD_LOCALHOST_8080 | s3cret       | the Basic credential "coach:s3cret" |
        | another origin | ARTIE_TOKEN_LOCALHOST_8181    | t0k3n       | HOME                          | /home/coach  | none                           |

    Scenario Outline: An environment set up wrongly stops ARTIE at the start: <case>
      Given the environment holds:
        | <first>  | <first value>  |
        | <second> | <second value> |
      When ARTIE tries to read credentials from the environment
      Then reading credentials failed with "<says>"
      And the failure does not contain "<first value>"
      And the failure does not contain "<second value>"

      Examples:
        | case                | first                      | first value | second                        | second value | says                                                                           |
        | user, no password   | ARTIE_USER_LOCALHOST_8080  | coach       | HOME                          | /home/coach  | ARTIE_USER_LOCALHOST_8080 is set, but ARTIE_PASSWORD_LOCALHOST_8080 is not     |
        | password, no user   | ARTIE_PASSWORD_LOCALHOST_8080 | s3cret   | HOME                          | /home/coach  | ARTIE_PASSWORD_LOCALHOST_8080 is set, but ARTIE_USER_LOCALHOST_8080 is not     |
        | token and user      | ARTIE_TOKEN_LOCALHOST_8080 | t0k3n       | ARTIE_USER_LOCALHOST_8080     | coach        | LOCALHOST_8080 has both a token and a user; set one                            |
        | an empty token      | ARTIE_TOKEN_LOCALHOST_8080 |             | HOME                          | /home/coach  | ARTIE_TOKEN_LOCALHOST_8080 is empty                                            |
