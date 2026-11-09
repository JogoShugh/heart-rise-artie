Feature: A signature proves the body and the key it was made with
  RFC 9421 HTTP message signatures with an Ed25519 key (RFC 8037). The signature covers the request's method,
  target, Content-Digest (RFC 9530) and If-Match. A verifier checks the digest of the body it received, then the
  signature. So a body changed after signing is refused, and so is a signature from another key.

  Scenario Outline: A signature is accepted only for the body and key it was made with
    Given the library signs the body <signed>
    When the signature is checked against the body <checked> with the key of <key>
    Then the signature is <outcome>

    Examples:
      | signed         | checked        | key     | outcome  |
      | {"result":"a"} | {"result":"a"} | library | accepted |
      | {"result":"a"} | {"result":"b"} | library | refused  |
      | {"result":"a"} | {"result":"a"} | other   | refused  |
