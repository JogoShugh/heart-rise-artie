# Standards work for HEART

Drafts and requests, ready for the author to submit or not. Nothing here has
been submitted anywhere.

- `identifiers.json`: every HEART identifier under `https://w3id.org/heart-rise/`
  and what it means. `scripts/build-identifier-pages.sh` builds `docs/` from it.
- `w3id/`: the `.htaccess` and README for a pull request to
  `perma-id/w3id.org`, and `PULL_REQUEST.md` with the steps before opening it.
- `draft-gough-hal-schema-forms-heart-00.md`: the HEART profile as an
  Internet-Draft (kramdown-rfc), with its IANA requests: the `forms` and
  `unavailable` link relations, the profile URIs, and the problem types.
  `.xml`, `.txt` and `.html` are rendered from it with kramdown-rfc 1.7.43 and
  xml2rfc 3.34.1.

To render again: `kramdown-rfc draft-….md > draft-….xml`, then
`xml2rfc --text --html draft-….xml`.
