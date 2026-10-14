Feature: Credentials from a Vault server
  With ARTIE_VAULT_ADDR set, ARTIE reads an origin's credential from Vault's
  key/value store (version 2), at artie/<host>_<port> under the mount
  ARTIE_VAULT_MOUNT (by default, secret). The secret holds bearer, or user
  and password, as the credentials file does. ARTIE logs in to Vault with
  VAULT_TOKEN, ~/.vault-token, or an AppRole. Each credential is held for
  ARTIE_VAULT_TTL_SECONDS (by default, 300), then read again; after a 401
  from the service it is read again at once. The Vault token goes only to
  Vault, and the service's credential only to the service. When Vault
  cannot answer, ARTIE says so with Vault's status, never its body.

  Background:
    Given the environment holds:
      | ARTIE_VAULT_ADDR | https://vault.test |
      | VAULT_TOKEN      | vault-t0k3n        |

  Scenario Outline: Vault's answer decides the credential: <case>
    Given Vault answers GET "https://vault.test/v1/secret/data/artie/garden.test_443" with:
      """
      <status>
      Content-Type: application/json

      <body>
      """
    When ARTIE reads its credentials
    Then the credential for "https://garden.test" is <credential>

    Examples:
      | case                | status        | body                                                    | credential                          |
      | a token             | 200 OK        | {data: {data: {bearer: t0k3n}, metadata: {version: 1}}} | the Bearer credential "t0k3n"       |
      | a user and password | 200 OK        | {data: {data: {user: coach, password: s3cret}}}         | the Basic credential "coach:s3cret" |
      | no secret there     | 404 Not Found | {errors: []}                                            | none                                |

  Scenario Outline: Vault cannot give the credential, and ARTIE says why without its body: <case>
    Given Vault answers GET "https://vault.test/v1/secret/data/artie/garden.test_443" with:
      """
      <status>
      Content-Type: application/json

      {errors: ["token vault-t0k3n is not allowed; secret is t0k3n"]}
      """
    When ARTIE reads its credentials
    And ARTIE tries to get the credential for "https://garden.test"
    Then reading credentials failed with "<says>"
    And the failure does not contain "vault-t0k3n"
    And the failure does not contain "t0k3n"

    Examples:
      | case          | status                    | says                                                                   |
      | refused       | 403 Forbidden             | no credential for https://garden.test:443: Vault answered 403 for secret/data/artie/garden.test_443 |
      | Vault failing | 500 Internal Server Error | no credential for https://garden.test:443: Vault answered 500 for secret/data/artie/garden.test_443 |

  Scenario: Vault cannot be reached
    Given Vault cannot be reached
    When ARTIE reads its credentials
    And ARTIE tries to get the credential for "https://garden.test"
    Then reading credentials failed with "no credential for https://garden.test:443: Vault at https://vault.test cannot be reached"

  Scenario: The Vault token goes only to Vault, and the service's credential only to the service
    Given Vault answers GET "https://vault.test/v1/secret/data/artie/garden.test_443" with:
      """
      200 OK
      Content-Type: application/json

      {data: {data: {bearer: t0k3n}}}
      """
    And ARTIE holds the credentials it reads
    And the service answers GET "https://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      """
    When the client enters "https://garden.test/beds/2"
    Then the request to "https://garden.test/beds/2" carried Authorization "Bearer t0k3n"
    And the request to "https://garden.test/beds/2" carried no "X-Vault-Token" header
    And the request to "https://vault.test/v1/secret/data/artie/garden.test_443" carried "X-Vault-Token" "vault-t0k3n"
    And the request to "https://vault.test/v1/secret/data/artie/garden.test_443" carried no Authorization

  Scenario Outline: A credential is held for its time to live, and read again after a 401: <case>
    Given the environment holds:
      | ARTIE_VAULT_TTL_SECONDS | <ttl> |
      | HOME                    | /home/coach |
    And Vault answers GET "https://vault.test/v1/secret/data/artie/garden.test_443" with:
      """
      200 OK
      Content-Type: application/json

      {data: {data: {bearer: old}}}
      """
    And Vault answers GET "https://vault.test/v1/secret/data/artie/garden.test_443" with:
      """
      200 OK
      Content-Type: application/json

      {data: {data: {bearer: new}}}
      """
    When ARTIE reads its credentials
    And ARTIE <asks>
    Then the credential for "https://garden.test" was read from Vault <reads> times, and is now the Bearer credential "<now>"

    Examples:
      | case                          | ttl | asks                                                                | reads | now |
      | within the time to live       | 300 | gets the credential for "https://garden.test" twice                  | 1     | old |
      | after the time to live        | 0   | gets the credential for "https://garden.test" twice                  | 2     | new |
      | renewed after a 401           | 300 | gets the credential for "https://garden.test", then a renewed one    | 2     | new |

  Scenario: The Vault token can come from ~/.vault-token, as Vault's own CLI keeps it
    Given the environment holds:
      | ARTIE_VAULT_ADDR | https://vault.test |
    And the environment does not hold "VAULT_TOKEN"
    And the home folder holds ".vault-token" with "file-vault-t0k3n"
    And Vault answers GET "https://vault.test/v1/secret/data/artie/garden.test_443" with:
      """
      200 OK
      Content-Type: application/json

      {data: {data: {bearer: t0k3n}}}
      """
    When ARTIE reads its credentials
    Then the credential for "https://garden.test" is the Bearer credential "t0k3n"
    And the request to "https://vault.test/v1/secret/data/artie/garden.test_443" carried "X-Vault-Token" "file-vault-t0k3n"

  Scenario: An AppRole logs in, and logs in again once when its token is refused
    Given the environment does not hold "VAULT_TOKEN"
    And the environment holds:
      | ARTIE_VAULT_ROLE_ID | role-1 |
      | HOME                | /home/coach |
    And ARTIE_VAULT_SECRET_ID_FILE names a file with mode "0600" holding "secret-id-1"
    And Vault answers POST "https://vault.test/v1/auth/approle/login" with:
      """
      200 OK
      Content-Type: application/json

      {auth: {client_token: approle-t0k3n-1, lease_duration: 3600}}
      """
    And Vault answers GET "https://vault.test/v1/secret/data/artie/garden.test_443" with:
      """
      403 Forbidden
      Content-Type: application/json

      {errors: ["permission denied"]}
      """
    And Vault answers POST "https://vault.test/v1/auth/approle/login" with:
      """
      200 OK
      Content-Type: application/json

      {auth: {client_token: approle-t0k3n-2, lease_duration: 3600}}
      """
    And Vault answers GET "https://vault.test/v1/secret/data/artie/garden.test_443" with:
      """
      200 OK
      Content-Type: application/json

      {data: {data: {bearer: t0k3n}}}
      """
    When ARTIE reads its credentials
    Then the credential for "https://garden.test" is the Bearer credential "t0k3n"
    And the client sent these requests:
      | POST https://vault.test/v1/auth/approle/login                    |
      | GET https://vault.test/v1/secret/data/artie/garden.test_443      |
      | POST https://vault.test/v1/auth/approle/login                    |
      | GET https://vault.test/v1/secret/data/artie/garden.test_443      |
    And the AppRole logins sent the role "role-1" and the secret ID from the file

  Scenario Outline: Vault set up wrongly stops ARTIE at the start: <case>
    Given the environment does not hold "VAULT_TOKEN"
    And the environment holds:
      | ARTIE_VAULT_ADDR | <address> |
      | <name>           | <value>   |
    When ARTIE tries to read its credentials
    Then reading credentials failed with "<says>"

    Examples:
      | case                  | address           | name                | value       | says                                                                               |
      | no way to log in      | https://vault.test | HOME               | /home/coach | ARTIE_VAULT_ADDR is set, but there is no VAULT_TOKEN, ~/.vault-token or ARTIE_VAULT_ROLE_ID |
      | a role, no secret ID  | https://vault.test | ARTIE_VAULT_ROLE_ID | role-1     | ARTIE_VAULT_ROLE_ID is set, but ARTIE_VAULT_SECRET_ID_FILE is not                  |
      | plain HTTP, not local | http://vault.test  | VAULT_TOKEN         | vault-t0k3n | ARTIE will not send a Vault token over plain HTTP to http://vault.test:80          |
