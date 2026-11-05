---
name: merging-default
description: Merges the current branch into the repository's default branch with --no-ff, through a deterministic script with a closed set of results. Use when a finished branch must be merged into the default branch.
---

# Merging into the default branch

Run the script from the repository root, on the branch to merge. Always use it instead of running
the merge by hand.

```bash
bash .claude/skills/merging-default/merge.sh "<merge commit subject>" ["<merge commit body>"]
```

With no subject, it uses `chore: merge <branch> into <default-branch>`.

The script prints exactly one word on standard output, and its diagnostics on standard error:

| Result     | Exit code | Meaning                                        | Where it leaves you | What to do                     |
|------------|-----------|------------------------------------------------|---------------------|--------------------------------|
| `MERGED`   | 0         | The merge commit was created.                  | Default branch      | Continue.                      |
| `CONFLICT` | 1         | The merge conflicted and was aborted.          | Your branch         | Stop and report the files.     |
| `DIRTY`    | 2         | There are pending changes, so nothing was done. | Your branch         | Stop and report the changes.   |
| `ERROR`    | 3         | Anything else, explained on standard error.    | Your branch         | Stop and report the diagnostic. |

`ERROR` includes a failed fetch, a default branch that cannot be detected, being on the default
branch already, a local default branch behind `origin`, and a branch with nothing to merge.

Do not resolve conflicts, and do not retry with other options. The script does not push.
