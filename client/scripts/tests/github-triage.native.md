Use GitHub's REST API at https://api.github.com, through the http tool, to do this task in the repository
{owner}/{repo}.

Triage and fix the blank-bed-name crash, start to finish. The crash gets an issue, labelled and assigned, with a
sub-issue for its test; a second report of the same crash is closed as a duplicate of it. The fix and its test go
on a branch of their own, into one pull request, which is merged. Then every issue is closed the right way, and
the crash's conversation is locked.

- In {owner}/{repo}
  - Open an issue titled "Crash when a bed has no name ({stamp})", saying a bed with a blank name crashes the garden (the crash issue)
    - Label it {label}
    - Assign it to {owner}
    - Make sure it is open
  - Open an issue titled "Test blank bed names ({stamp})", saying it is part of #(the crash issue's number) (the test issue)
  - Open an issue titled "Garden crashes on an empty bed name ({stamp})", saying it was seen on the demo garden (the duplicate issue)
  - From the main branch, start a new branch named fix-blank-names-{stamp}
    - Commit a file named fix-{stamp}.md saying "Bed names may not be blank." with the message "Refuse a bed with no name"
    - Commit a file named test-{stamp}.md saying "A bed with a blank name is refused." with the message "Test that a blank bed name is refused"
    - Open a pull request into main titled "Refuse a bed with no name ({stamp})", saying it fixes #(the crash issue's number) (the pull request)
      - Change its description to say it fixes #(the crash issue's number) and is tested by #(the test issue's number)
      - Wait until the pull request can be merged
      - Merge it by squashing its commits, with the commit title "Refuse a bed with no name (#(the pull request's number))"
      - Make sure it merged
- On the crash issue
  - Add the test issue as a sub-issue, by the test issue's id
  - Comment "Fixed in #(the pull request's number), tested in #(the test issue's number)."
  - Close it the right way: the fix is merged
  - Lock its conversation as resolved
  - Make sure it is closed, its reason is completed, and it is locked
- On the test issue
  - Comment "The test went in with #(the pull request's number)."
  - Close it as completed
  - Make sure it is closed
- On the duplicate issue
  - Comment "Same crash as #(the crash issue's number)."
  - Close it as a duplicate of #(the crash issue's number), whose id is the crash issue's id
  - Make sure it is closed, and its reason is duplicate
