---
name: committing-changes
description: Groups pending changes into coherent commits with Conventional Commits messages. Use when the user asks to commit, save or record work in Git.
---

# Committing changes

1. Inspect the work:

   ```bash
   git status
   git diff
   git diff --staged
   ```

2. Group the changes so each group has one purpose and can be described in one
   subject line. Leave out generated files, secrets and unrelated changes, and
   tell the user what was left out.

3. Stage and commit one group at a time, naming its paths explicitly. Do not use
   `git add .` or `git add -A`.

   ```bash
   git add <paths-of-the-group>
   git diff --staged --stat
   git commit -m "<type>(<optional-scope>): <subject>"
   ```

4. Write each message following [conventional-commits.md](conventional-commits.md).

5. Run `git status` and `git log --oneline -n <commits-made>` to confirm that
   nothing was missed, then report the commits made.

Do not push unless the user asks. If a hook fails, fix the cause and create the
commit again. Do not bypass the hook with `--no-verify`.
