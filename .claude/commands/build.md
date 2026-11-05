---
description: Builds a spec from plan to release by coordinating the engineer, coder, tester, cleaner and devops agents.
argument-hint: "[spec, such as feat-launch-listing]"
---

# Build a spec

Spec requested: `$ARGUMENTS`

You are the orchestrator. Coordinate the agents below in this order, running one at a time and
waiting for each to finish. Do not write code, tests or documentation yourself, and do not change
what an agent reports. Each agent starts with no context, so give it the spec's path, its branch
and any report it needs.

## 1. Choose the spec

Read the specs in `project/specs/`. A dependency is met when its spec's status is `released`.

- **With an argument**: use that spec. If its dependencies are not met, stop and tell the user.
- **With no argument**: use the first spec in alphabetical order with status `draft` whose
  **Depends on** are all met. If there is none, stop and tell the user why.

Statuses change on the spec's branch and reach the default branch only with the release. So read
the dependencies on the default branch. For the chosen spec, if its branch exists, switch to it
and read the status there. Start from the step that this status calls for, so that a stopped build
can continue:

| Status                 | Start at               |
|------------------------|------------------------|
| `draft`                | Step 2                 |
| `planned`, `in-progress` | Step 3               |
| `verified`             | Step 4                 |
| `released`             | Stop: nothing to do.   |

If there are pending changes in the working tree, stop and tell the user.

## 2. Plan

Run the `engineer` agent. If it returns open questions, ask the user, then run it again with the
answers.

## 3. Implement and verify

Repeat at most 3 rounds:

1. Run the `coder` agent. From the second round on, pass it the tester's latest report in full.
   If `./mvnw test` does not pass when it finishes, stop and inform the user.
2. Check that the API responds, using the URL in `ASTROBOOKINGS_API_URL` or the default:

   ```bash
   curl -s -o /dev/null --max-time 5 "${ASTROBOOKINGS_API_URL:-http://localhost:3000}/activities"
   ```

   If it does not, stop and ask the user to start it. Do not start it yourself. This does not use
   up a round. To start it, the user runs `npm run dev` in `../activity-bookings-express`.
3. Run the `tester` agent and read the first line of its final message:

   - `RESULT: PASS`: go to step 4.
   - `RESULT: FAIL`: start the next round.
   - `RESULT: BLOCKED`: stop and inform the user.

If the tester still fails after the third round, stop and give the user its latest report. Do not
continue to the cleaner.

## 4. Clean

Check again that the API responds, as above, then run the `cleaner` agent. If it reports
`RESULT: FAIL`, stop and inform the user.

## 5. Confirm the release

**Stop here and ask the user with AskUserQuestion**, every time, with no exceptions. Show the
spec, the rounds it took, the criteria covered and how, and what the release will do: the version
and tag, if any, the merge into the default branch and the push of the default branch, the spec's
branch and the tag.

If the user does not confirm, stop and leave the branch as it is.

## 6. Release

Run the `devops` agent, and tell it that the user confirmed the release. Unless its first line is
`MERGED`, stop and inform the user.

## 7. Report

Tell the user the spec's final status, the version and tag, the requirements marked `done`, and
the specs that can now be built.
