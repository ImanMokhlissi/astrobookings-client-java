---
name: releasing-version
description: Releases a verified spec by updating CHANGELOG.md, the pom.xml version, the spec and PRD statuses, and by creating the version tag after the merge. Use when a verified spec is ready to be released.
---

# Releasing a version

Start on the spec's branch, with no pending changes and the spec in status `verified`. If not,
stop and report it.

1. Work out the new version from the spec's type, following [sem-ver.md](sem-ver.md). If its tag
   already exists, stop and report it.

2. Update `CHANGELOG.md` in the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format,
   creating the file if needed. Describe the change from the user's point of view, under `Added`,
   `Changed` or `Fixed`:

   - **feat, fix**: add a `## [X.Y.Z] - YYYY-MM-DD` section, and move anything under
     `## [Unreleased]` into it.
   - **chore**: add the entry under `## [Unreleased]`.

3. **feat, fix only**: set the project's `<version>` in `pom.xml` to the new version. Change no
   other version.

4. In the spec, change **Status** to `released`.

5. In `project/PRD.md`, for each FR and TR in the spec's **Related**, set the status to `done`
   only if every spec that lists that ID in **Related** is now `released`. Otherwise, leave it as
   it is. Change only the Status column.

6. Commit with the `committing-changes` skill.

7. Merge with the `merging-default` skill. Continue only if the result is `MERGED`. On any other
   result, stop and report it, with no tag created.

8. **feat, fix only**: on the merge commit, create the tag:

   ```bash
   git tag -a vX.Y.Z -m "vX.Y.Z"
   ```

9. Push the default branch, the spec's branch and the tag, if there is one:

   ```bash
   git push origin <default-branch> <spec-branch>
   git push origin vX.Y.Z
   ```

10. Report the version, the tag, the requirements marked `done`, and those left open because
    another spec still lists them.
