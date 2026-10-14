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

  Rule: A credentials file in the .artie folder names one origin per section

    Scenario Outline: The credentials file gives the credential: <case>
      Given ARTIE's folder holds a credentials file with mode "0600":
        """
        # Credentials, one origin per section.
        [<section>]
        <first line>
        <second line>
        """
      When ARTIE reads its credentials
      Then the credential for "<origin>" is <credential>

      Examples:
        | case                    | section                | first line   | second line       | origin                  | credential                          |
        | a token                 | http://localhost:8080  | bearer = t0k3n |                 | http://localhost:8080   | the Bearer credential "t0k3n"       |
        | a user and password     | https://garden.example | user = coach | password = s3cret | https://garden.example  | the Basic credential "coach:s3cret" |
        | default port written out | https://garden.example:443 | bearer = t0k3n |             | https://garden.example  | the Bearer credential "t0k3n"       |
        | another origin          | http://localhost:8181  | bearer = t0k3n |                 | http://localhost:8080   | none                                |

    Scenario Outline: A credentials file set up wrongly stops ARTIE at the start: <case>
      Given ARTIE's folder holds a credentials file with mode "<mode>":
        """
        [<section>]
        <first line>
        <second line>
        """
      When ARTIE tries to read its credentials
      Then reading credentials failed with "<says>"
      And the failure does not contain "t0k3n"
      And the failure does not contain "s3cret"

      Examples:
        | case                     | mode | section               | first line     | second line       | says                                                    |
        | readable by others       | 0644 | http://localhost:8080 | bearer = t0k3n |                   | can be read by others; run chmod 600 on it              |
        | a path, not an origin    | 0600 | http://localhost:8080/beds | bearer = t0k3n |              | line 1: a section names an origin, not a path           |
        | CoAP                     | 0600 | coap://tray.local     | bearer = t0k3n |                   | line 1: CoAP credentials are not supported (they need OSCORE or DTLS) |
        | a token and a user       | 0600 | http://localhost:8080 | bearer = t0k3n | user = coach      | http://localhost:8080 has both a token and a user; set one |
        | a user with no password  | 0600 | http://localhost:8080 | user = coach   |                   | http://localhost:8080 has a user but no password        |
        | an unknown key           | 0600 | http://localhost:8080 | token = t0k3n  |                   | line 2: unknown key 'token'; use bearer, or user and password |
        | a line with no equals    | 0600 | http://localhost:8080 | bearer t0k3n   |                   | line 2: expected 'key = value'                          |

    Scenario: With no credentials file, there are no credentials from it, and no error
      Given ARTIE's folder holds no credentials file
      When ARTIE reads its credentials
      Then the credential for "http://localhost:8080" is none

    Scenario: The environment wins over the file for the same origin
      Given ARTIE's folder holds a credentials file with mode "0600":
        """
        [http://localhost:8080]
        bearer = from-the-file
        """
      And the environment holds:
        | ARTIE_TOKEN_LOCALHOST_8080 | from-the-environment |
        | HOME                       | /home/coach          |
      When ARTIE reads its credentials
      Then the credential for "http://localhost:8080" is the Bearer credential "from-the-environment"

    Scenario: ARTIE_HOME names the folder to read instead of ~/.artie
      Given ARTIE_HOME names a folder holding a credentials file with mode "0600":
        """
        [http://localhost:8080]
        bearer = t0k3n
        """
      When ARTIE reads its credentials
      Then the credential for "http://localhost:8080" is the Bearer credential "t0k3n"
