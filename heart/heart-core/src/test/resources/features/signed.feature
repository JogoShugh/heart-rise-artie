Feature: A signature proves the body and the key it was made with
  RFC 9421 HTTP message signatures with an Ed25519 key (RFC 8037). The signature covers the request's method,
  target, Content-Digest (RFC 9530) and If-Match. A verifier checks the digest of the body it received, then the
  signature. So a body changed after signing is refused, and so is a signature from another key. A signer
  publishes its key as a JWK (RFC 7517, RFC 8037), and a verifier reads the key it checks with from there.

  Scenario Outline: A signature is accepted only for the body and key it was made with: <key>
    Given the library signs the body <signed>
    When the signature is checked against the body <checked> with the key of <key>
    Then the signature is <outcome>

    Examples:
      | signed         | checked        | key                               | outcome  |
      | {"result":"a"} | {"result":"a"} | library                           | accepted |
      | {"result":"a"} | {"result":"b"} | library                           | refused  |
      | {"result":"a"} | {"result":"a"} | other                             | refused  |
      | {"result":"a"} | {"result":"a"} | library, read back from its JWK   | accepted |
      | {"result":"a"} | {"result":"a"} | other, read back from its JWK     | refused  |

  Scenario: A key published as a JWK names its type, its curve and its key id
    When the library's key is published as a JWK, with the key id "library-2026-11"
    Then the JWK has these members
      | kty | crv     | kid             | alg   | use |
      | OKP | Ed25519 | library-2026-11 | EdDSA | sig |
    And its x is the key's 32 bytes, in base64url without padding
