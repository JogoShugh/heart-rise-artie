# Pull request to perma-id/w3id.org (draft, for Josh to open)

**Title:** Add /heart-rise for HEART identifiers

**Files:** copy `heart-rise/.htaccess` and `heart-rise/README.md` from this
folder into a fork of https://github.com/perma-id/w3id.org, at `heart-rise/`.

**Description:**

> This adds `https://w3id.org/heart-rise/` for HEART, a hypermedia profile of
> HAL for stateful APIs and the agents that use them. The identifiers name its
> media-type profiles, link relations (`forms`, `unavailable`), RFC 9457
> problem types and home-document variables. Each redirects to a page that
> defines it, on GitHub Pages, built from the project's own definitions file.
> Maintainer: @JogoShugh.

**Before opening it:**
1. Turn on GitHub Pages for `heart-rise-artie`, from the `docs/` folder on the
   default branch (Settings → Pages), and merge `docs/` to that branch.
2. Check that https://jogoshugh.github.io/heart-rise-artie/rels/forms/ answers.
